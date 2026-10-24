package com.docforge.template;

import com.docforge.AbstractIT;
import com.docforge.AuditTestSupport;
import com.docforge.TestAuth;
import com.docforge.security.JwtTokenProvider;
import com.docforge.template.dto.CreateTemplateRequest;
import com.docforge.template.dto.SaveVersionRequest;
import com.docforge.template.dto.ScriptTestRequest;
import com.docforge.template.dto.TemplateResponse;
import com.docforge.template.dto.UpdateTemplateRequest;
import com.docforge.template.model.Script;
import com.docforge.user.User;
import com.docforge.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test of the write permissions of the {@code template} module (BUG-068).
 *
 * <p>Since BUG-068 every mutating operation of the module requires the caller to be the
 * owner of the template ({@code template_registry.owner_id}) or an administrator; everybody
 * else gets {@code 403 «Нет прав на изменение шаблона»}. The check lives in the service
 * ({@code TemplateServiceImpl.assertCanModify}) and covers:</p>
 * <ul>
 *   <li>{@code PUT /api/templates/{id}} — update;</li>
 *   <li>{@code DELETE /api/templates/{id}} — delete;</li>
 *   <li>{@code POST /api/templates/{id}/publish} и {@code .../unpublish} — публикация;</li>
 *   <li>{@code POST /api/templates/{id}/versions} — сохранение версии;</li>
 *   <li>{@code POST /api/templates/{id}/rollback/{versionNumber}} — откат;</li>
 *   <li>{@code POST /api/templates/{id}/validate} — валидация содержимого;</li>
 *   <li>{@code POST /api/templates/{id}/scripts/test} — тест Python-скрипта.</li>
 * </ul>
 *
 * <p>The test covers three identities and the failure modes:</p>
 * <ol>
 *   <li><strong>userB</strong> — посторонний: все изменяющие запросы отклоняются с
 *       {@code 403}, и <em>ничего</em> в шаблоне не меняется;</li>
 *   <li><strong>userA</strong> — владелец: запросы проходят (с учётом собственных
 *       предусловий эндпоинтов, например update опубликованного шаблона → {@code 409});</li>
 *   <li><strong>admin</strong> — может изменять чужой шаблон;</li>
 *   <li>неизвестный логин и отсутствие аутентификации → отказ (fail closed).</li>
 * </ol>
 *
 * <p>Additionally the list of templates must expose {@code ownerId} — the frontend uses it
 * to hide the publish/delete buttons for чужие шаблоны.</p>
 */
@AutoConfigureMockMvc
class TemplateWritePermissionIT extends AbstractIT {

    /** {@code TemplateServiceImpl.assertCanModify} — отказ по правам на изменение. */
    private static final String MODIFY_DENIED = "Нет прав на изменение шаблона";

    /** {@code TemplatePublishedException} — опубликованный шаблон нельзя изменить. */
    private static final String PUBLISHED_DENIED = "Шаблон опубликован и не может быть изменён";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private TemplateService templateService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MongoTemplate mongoTemplate;

    private String suffix;

    /** Владелец шаблонов теста. */
    private UUID userAId;
    private String userALogin;
    private String userAToken;

    /** Посторонний — не владеет ничем. */
    private UUID userBId;
    private String userBLogin;
    private String userBToken;

    /** Засеянный администратор ({@code V014__default_admin_user.sql}). */
    private String adminToken;

    /** Черновик (draft), принадлежащий {@code userA}. */
    private UUID draftTemplateId;

    /** Опубликованный шаблон, принадлежащий {@code userA}. */
    private UUID publishedTemplateId;

    /** Номер опубликованной версии {@link #publishedTemplateId}. */
    private int publishedVersionNumber;

    /** Ещё один черновик — его удаляет владелец (и пытается удалить посторонний). */
    private UUID deletableTemplateId;

    private final List<UUID> templateIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);

        userALogin = "tpl-write-a-" + suffix;
        userBLogin = "tpl-write-b-" + suffix;
        userAId = createUser(userALogin, "Template Write A");
        userBId = createUser(userBLogin, "Template Write B");
        userAToken = jwtTokenProvider.generateToken(userALogin);
        userBToken = jwtTokenProvider.generateToken(userBLogin);
        adminToken = jwtTokenProvider.generateToken("admin");

        // Черновик владельца: с телом и скриптом (иначе scripts/test нечего тестировать).
        TemplateResponse draft = TestAuth.callAs(userALogin, () -> templateService.create(
                userAId,
                new CreateTemplateRequest("BUG-068 Draft " + suffix, null, category(), null)));
        draftTemplateId = draft.id();
        templateIds.add(draftTemplateId);
        TestAuth.callAs(userALogin, () -> templateService.update(draftTemplateId, updateRequest(
                "BUG-068 Draft " + suffix, body("DRAFT BODY"))));

        // Опубликованный шаблон владельца — на нём проверяем откат и 409 при update.
        TemplateResponse published = TestAuth.callAs(userALogin, () -> templateService.create(
                userAId,
                new CreateTemplateRequest("BUG-068 Published " + suffix, null, category(), null)));
        publishedTemplateId = published.id();
        templateIds.add(publishedTemplateId);
        TestAuth.callAs(userALogin, () -> templateService.update(publishedTemplateId, updateRequest(
                "BUG-068 Published " + suffix, body("PUBLISHED BODY"))));
        TestAuth.callAs(userALogin, () -> templateService.publish(publishedTemplateId));
        publishedVersionNumber = templateService.listVersions(publishedTemplateId).stream()
                .filter(v -> "PUBLISH".equals(v.bumpType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("PUBLISH version was not created"))
                .versionNumber();

        // Черновик, который владелец удалит в сценарии «владелец может изменять».
        TemplateResponse deletable = TestAuth.callAs(userALogin, () -> templateService.create(
                userAId,
                new CreateTemplateRequest("BUG-068 Deletable " + suffix, null, category(), null)));
        deletableTemplateId = deletable.id();
        templateIds.add(deletableTemplateId);
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
        for (UUID templateId : templateIds) {
            if (templateId == null) continue;
            // Аудит асинхронный (@Async) — best-effort пауза, чтобы удалить и строки
            // аудита по сущности (поздние строки добьёт deleteUserWithAuditRows).
            AuditTestSupport.awaitQuiescence(jdbcTemplate,
                    "SELECT COUNT(*) FROM audit_log WHERE user_id = ?", userAId);
            mongoTemplate.remove(Query.query(Criteria.where("template_id").is(templateId)), "template_snapshots");
            mongoTemplate.remove(Query.query(Criteria.where("_id").is(templateId)), "templates");
            jdbcTemplate.update("DELETE FROM template_version WHERE template_id = ?", templateId);
            jdbcTemplate.update("DELETE FROM template_registry WHERE id = ?", templateId);
            jdbcTemplate.update("DELETE FROM audit_log WHERE entity_id = ?", templateId);
        }
        for (UUID userId : List.of(userAId, userBId)) {
            if (userId == null) continue;
            // Аудит асинхронный (@Async) — удаляем пользователя вместе с его строками
            // аудита детерминированно (retry при поздней строке аудита).
            AuditTestSupport.deleteUserWithAuditRows(jdbcTemplate, userId);
        }
        templateIds.clear();
    }

    // ── 1. посторонний (userB) ───────────────────────────────────────────────

    @Test
    @DisplayName("stranger: every mutating endpoint of a чужой шаблон → 403 «Нет прав на изменение шаблона»")
    void stranger_mutatingEndpoints_allForbidden() throws Exception {
        // given — состояние шаблонов до попыток постороннего
        var draftBefore = templateService.getById(draftTemplateId);
        int draftVersionsBefore = templateService.listVersions(draftTemplateId).size();
        int publishedVersionsBefore = templateService.listVersions(publishedTemplateId).size();
        assertThat(draftBefore.published()).isFalse();
        assertThat(draftVersionsBefore).isZero();
        assertThat(bodyText(draftBefore.body())).isEqualTo("DRAFT BODY");

        // when — посторонний пытается изменить чужой черновик
        mockMvc.perform(put("/api/templates/{id}", draftTemplateId)
                        .header("Authorization", bearer(userBToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest("Взлом", body("HACKED")))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value(MODIFY_DENIED));

        mockMvc.perform(delete("/api/templates/{id}", draftTemplateId)
                        .header("Authorization", bearer(userBToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value(MODIFY_DENIED));

        mockMvc.perform(post("/api/templates/{id}/publish", draftTemplateId)
                        .header("Authorization", bearer(userBToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(MODIFY_DENIED));

        mockMvc.perform(post("/api/templates/{id}/unpublish", draftTemplateId)
                        .header("Authorization", bearer(userBToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(MODIFY_DENIED));

        mockMvc.perform(post("/api/templates/{id}/versions", draftTemplateId)
                        .header("Authorization", bearer(userBToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SaveVersionRequest("MINOR", "чужеродная версия"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(MODIFY_DENIED));

        mockMvc.perform(post("/api/templates/{id}/validate", draftTemplateId)
                        .header("Authorization", bearer(userBToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(MODIFY_DENIED));

        mockMvc.perform(post("/api/templates/{id}/scripts/test", draftTemplateId)
                        .header("Authorization", bearer(userBToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ScriptTestRequest("bug068-script", Map.of()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(MODIFY_DENIED));

        // and — откат чужого (уже опубликованного) шаблона
        mockMvc.perform(post("/api/templates/{id}/rollback/{v}", publishedTemplateId, publishedVersionNumber)
                        .header("Authorization", bearer(userBToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(MODIFY_DENIED));

        // and — отказ не изменил ничего: тело, флаг публикации, версии и реестр.
        assertTemplateUnchanged(draftTemplateId, "DRAFT BODY");
        assertThat(templateService.listVersions(publishedTemplateId))
                .as("отказ в откате не должен создавать версию")
                .hasSize(publishedVersionsBefore);
    }

    @Test
    @DisplayName("stranger: after the refused requests nothing changed — тело, флаг публикации и версии")
    void stranger_refusedRequests_changeNothing() {
        // given — состояние шаблона до попыток постороннего
        var before = templateService.getById(draftTemplateId);
        int versionsBefore = templateService.listVersions(draftTemplateId).size();
        assertThat(before.published()).isFalse();
        assertThat(versionsBefore).isZero();

        // when — посторонний пытается изменить шаблон (все вызовы отклоняются)
        TestAuth.runAs(userBLogin, () -> {
            assertThatThrownBy(() -> templateService.update(draftTemplateId,
                    updateRequest("Взлом", body("HACKED"))))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining(MODIFY_DENIED);
            assertThatThrownBy(() -> templateService.delete(draftTemplateId))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining(MODIFY_DENIED);
            assertThatThrownBy(() -> templateService.publish(draftTemplateId))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining(MODIFY_DENIED);
            assertThatThrownBy(() -> templateService.unpublish(draftTemplateId))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining(MODIFY_DENIED);
            assertThatThrownBy(() -> templateService.saveVersion(draftTemplateId,
                    new SaveVersionRequest("MINOR", "чужая")))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining(MODIFY_DENIED);
            assertThatThrownBy(() -> templateService.rollback(publishedTemplateId, publishedVersionNumber))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining(MODIFY_DENIED);
            assertThatThrownBy(() -> templateService.validate(draftTemplateId))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining(MODIFY_DENIED);
            assertThatThrownBy(() -> templateService.testScript(
                    draftTemplateId, new ScriptTestRequest("bug068-script", Map.of())))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining(MODIFY_DENIED);
        });

        // then — шаблон не изменился
        var after = templateService.getById(draftTemplateId);
        assertThat(after.name()).isEqualTo(before.name());
        assertThat(after.published()).isFalse();
        assertThat(after.body()).isEqualTo(before.body());
        assertThat(after.version()).isEqualTo(before.version());
        assertThat(templateService.listVersions(draftTemplateId)).hasSize(versionsBefore);

        // then — шаблон не удалён
        assertThat(registryRowCount(draftTemplateId)).isEqualTo(1);
    }

    @Test
    @DisplayName("stranger: refused rollback of a чужой published template creates no version")
    void stranger_refusedRollback_createsNoVersion() {
        int versionsBefore = templateService.listVersions(publishedTemplateId).size();

        assertThatThrownBy(() -> templateService.rollback(publishedTemplateId, publishedVersionNumber))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining(MODIFY_DENIED);

        assertThat(templateService.listVersions(publishedTemplateId)).hasSize(versionsBefore);
    }

    // ── 2. владелец (userA) ───────────────────────────────────────────────────

    @Test
    @DisplayName("owner: update, publish, unpublish, versions, validate и scripts/test — успешно")
    void owner_mutatingEndpoints_allowed() throws Exception {
        // update черновика
        mockMvc.perform(put("/api/templates/{id}", draftTemplateId)
                        .header("Authorization", bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest(
                                "BUG-068 Draft Updated " + suffix, body("UPDATED BODY")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("BUG-068 Draft Updated " + suffix));

        // сохранение версии
        mockMvc.perform(post("/api/templates/{id}/versions", draftTemplateId)
                        .header("Authorization", bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SaveVersionRequest("MINOR", "моя версия"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version.bumpType").value("MINOR"));

        // публикация и снятие с публикации
        mockMvc.perform(post("/api/templates/{id}/publish", draftTemplateId)
                        .header("Authorization", bearer(userAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(true));

        mockMvc.perform(post("/api/templates/{id}/unpublish", draftTemplateId)
                        .header("Authorization", bearer(userAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(false));

        // валидация содержимого
        mockMvc.perform(post("/api/templates/{id}/validate", draftTemplateId)
                        .header("Authorization", bearer(userAToken)))
                .andExpect(status().isOk());

        // тест python-скрипта
        mockMvc.perform(post("/api/templates/{id}/scripts/test", draftTemplateId)
                        .header("Authorization", bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ScriptTestRequest("bug068-script", Map.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // откат собственной версии
        int versionNumber = templateService.listVersions(draftTemplateId).stream()
                .filter(v -> "MINOR".equals(v.bumpType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("MINOR version was not created"))
                .versionNumber();
        mockMvc.perform(post("/api/templates/{id}/rollback/{v}", draftTemplateId, versionNumber)
                        .header("Authorization", bearer(userAToken)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("owner: delete of a собственный черновик → 204, шаблон удалён")
    void owner_delete_ownDraft_returns204() throws Exception {
        mockMvc.perform(delete("/api/templates/{id}", deletableTemplateId)
                        .header("Authorization", bearer(userAToken)))
                .andExpect(status().isNoContent());

        assertThat(registryRowCount(deletableTemplateId)).isZero();
    }

    @Test
    @DisplayName("owner: update of a published template → 409 «Шаблон опубликован…» (собственное предусловие эндпоинта)")
    void owner_updatePublishedTemplate_returns409() throws Exception {
        mockMvc.perform(put("/api/templates/{id}", publishedTemplateId)
                        .header("Authorization", bearer(userAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest(
                                "Новое имя " + suffix, body("BODY")))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value(containsString(PUBLISHED_DENIED)));
    }

    // ── 3. администратор ─────────────────────────────────────────────────────

    @Test
    @DisplayName("admin: может изменить чужой шаблон — publish/unpublish работают")
    void admin_canModifyForeignTemplate() throws Exception {
        // publish чужого черновика
        mockMvc.perform(post("/api/templates/{id}/publish", draftTemplateId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(true));

        // unpublish
        mockMvc.perform(post("/api/templates/{id}/unpublish", draftTemplateId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(false));

        // update чужого черновика
        mockMvc.perform(put("/api/templates/{id}", draftTemplateId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest(
                                "BUG-068 Admin Updated " + suffix, body("ADMIN BODY")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("BUG-068 Admin Updated " + suffix));
    }

    // ── 4. неизвестный логин и отсутствие аутентификации (fail closed) ───────

    @Test
    @DisplayName("unknown login: the guard fails closed → 403 «Нет прав на изменение шаблона»")
    void unknownLogin_isDenied() {
        String unknownLogin = "bug068-unknown-template-user-" + suffix;
        assertThat(countUsersByLogin(unknownLogin)).isZero();

        TestAuth.authenticateAs(unknownLogin);
        try {
            assertThatThrownBy(() -> templateService.publish(draftTemplateId))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining(MODIFY_DENIED);
            assertThatThrownBy(() -> templateService.update(draftTemplateId, updateRequest("Взлом", body("HACKED"))))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining(MODIFY_DENIED);
        } finally {
            TestAuth.clear();
        }

        // nothing changed
        assertThat(templateService.getById(draftTemplateId).published()).isFalse();
        assertThat(templateService.listVersions(draftTemplateId)).isEmpty();
    }

    @Test
    @DisplayName("no authentication: the guard fails closed → 403, а HTTP-запрос без токена тоже отклоняется (403)")
    void noAuthentication_isDenied() throws Exception {
        // сервис без контекста безопасности → отказ
        TestAuth.clear();
        assertThatThrownBy(() -> templateService.publish(draftTemplateId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining(MODIFY_DENIED);

        // HTTP без токена → запрос не доходит до сервиса: цепочка безопасности
        // отклоняет его раньше (SecurityConfig: anyRequest().authenticated()).
        mockMvc.perform(post("/api/templates/{id}/publish", draftTemplateId))
                .andExpect(status().isForbidden());

        assertThat(templateService.getById(draftTemplateId).published()).isFalse();
    }

    // ── 5. ownerId в списке шаблонов ─────────────────────────────────────────

    @Test
    @DisplayName("GET /api/templates: the list item contains ownerId of the template owner")
    void list_containsOwnerId() throws Exception {
        mockMvc.perform(get("/api/templates")
                        .param("page", "0")
                        .param("size", "50")
                        .param("category", category())
                        .header("Authorization", bearer(userAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem(draftTemplateId.toString())))
                .andExpect(jsonPath("$.content[*].id", hasItem(publishedTemplateId.toString())))
                .andExpect(jsonPath("$.content[?(@.id == '" + draftTemplateId + "')].ownerId")
                        .value(hasItem(userAId.toString())))
                .andExpect(jsonPath("$.content[?(@.id == '" + publishedTemplateId + "')].ownerId")
                        .value(hasItem(userAId.toString())));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Уникальная категория шаблонов теста — изолирует их от dev-базы. */
    private String category() {
        return "bug068-write-" + suffix;
    }

    /** TipTap-тело с одним абзацем. */
    private static Map<String, Object> body(String text) {
        return Map.of(
                "type", "doc",
                "content", List.of(Map.of(
                        "type", "paragraph",
                        "content", List.of(Map.of(
                                "type", "text",
                                "text", text)))));
    }

    /** Запрос обновления с телом и скриптом (нужен для {@code scripts/test}). */
    private UpdateTemplateRequest updateRequest(String name, Map<String, Object> body) {
        return new UpdateTemplateRequest(
                name, null, category(), null, null, body,
                List.of(Script.builder()
                        .id("bug068-script")
                        .name("Скрипт BUG-068")
                        .code("result = True")
                        .description("Всегда истина")
                        .build()),
                null, null, null);
    }

    private UUID createUser(String login, String displayName) {
        User user = new User();
        user.setLogin(login);
        user.setPasswordHash("hash");
        user.setDisplayName(displayName);
        return userRepository.save(user).getId();
    }

    /** Количество строк реестра шаблонов с данным id. */
    private int registryRowCount(UUID templateId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM template_registry WHERE id = ?", Integer.class, templateId);
        return count == null ? 0 : count;
    }

    /**
     * Проверяет, что отказ в изменении шаблона ничего не изменил: флаг публикации,
     * тело, список версий и строку реестра.
     *
     * @param templateId        шаблон
     * @param expectedBodyText  ожидаемый текст первого абзаца тела
     */
    private void assertTemplateUnchanged(UUID templateId, String expectedBodyText) {
        var after = templateService.getById(templateId);
        assertThat(after.published())
                .as("is_published должен остаться false")
                .isFalse();
        assertThat(bodyText(after.body()))
                .as("текст тела шаблона не должен измениться")
                .isEqualTo(expectedBodyText);
        assertThat(after.body())
                .as("тело шаблона не должно измениться")
                .isEqualTo(body(expectedBodyText));
        assertThat(templateService.listVersions(templateId))
                .as("список версий шаблона должен остаться пустым")
                .isEmpty();
        assertThat(registryRowCount(templateId))
                .as("строка реестра шаблона должна сохраниться")
                .isEqualTo(1);
    }

    /** Текст первого абзаца тела шаблона (TipTap JSON). */
    private static String bodyText(Map<String, Object> body) {
        if (body == null) {
            return null;
        }
        List<?> paragraphs = body.get("content") instanceof List<?> list ? list : List.of();
        if (paragraphs.isEmpty() || !(paragraphs.get(0) instanceof Map<?, ?> paragraph)) {
            return null;
        }
        List<?> runs = paragraph.get("content") instanceof List<?> list ? list : List.of();
        if (runs.isEmpty() || !(runs.get(0) instanceof Map<?, ?> run)) {
            return null;
        }
        Object text = run.get("text");
        return text == null ? null : text.toString();
    }

    private long countUsersByLogin(String login) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM users WHERE login = ?", Long.class, login);
        return count == null ? 0L : count;
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
