package com.docforge.template;

import com.docforge.AbstractIT;
import com.docforge.AuditTestSupport;
import com.docforge.TestAuth;
import com.docforge.security.JwtTokenProvider;
import com.docforge.template.dto.CreateTemplateRequest;
import com.docforge.template.dto.TemplateResponse;
import com.docforge.template.dto.UpdateTemplateRequest;
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
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test of {@code PUT /api/templates/{id}} over HTTP.
 *
 * <p>BUG-068: the template module requires the caller to be the owner of the template
 * (or an administrator) — otherwise the request is refused with
 * {@code 403 «Нет прав на изменение шаблона»}. The test therefore authenticates with a
 * real JWT of a real user who owns the template.</p>
 */
@AutoConfigureMockMvc
class TemplateControllerIT extends AbstractIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TemplateService templateService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Владелец шаблона теста. */
    private UUID ownerId;
    private String ownerToken;

    /** Шаблоны, созданные тестом — чистятся в {@code @AfterEach}. */
    private final List<UUID> templateIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String login = "tpl-ctrl-owner-" + suffix;
        User owner = new User();
        owner.setLogin(login);
        owner.setPasswordHash("hash");
        owner.setDisplayName("Template Controller Owner");
        ownerId = userRepository.save(owner).getId();
        ownerToken = jwtTokenProvider.generateToken(login);
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
        for (UUID templateId : templateIds) {
            if (templateId == null) continue;
            AuditTestSupport.awaitQuiescence(jdbcTemplate,
                    "SELECT COUNT(*) FROM audit_log WHERE user_id = ?", ownerId);
            mongoTemplate.remove(Query.query(Criteria.where("template_id").is(templateId)), "template_snapshots");
            mongoTemplate.remove(Query.query(Criteria.where("_id").is(templateId)), "templates");
            jdbcTemplate.update("DELETE FROM template_version WHERE template_id = ?", templateId);
            jdbcTemplate.update("DELETE FROM template_registry WHERE id = ?", templateId);
            jdbcTemplate.update("DELETE FROM audit_log WHERE entity_id = ?", templateId);
        }
        // Аудит асинхронный (@Async) — удаляем пользователя вместе с его строками
        // аудита детерминированно (retry при поздней строке аудита).
        AuditTestSupport.deleteUserWithAuditRows(jdbcTemplate, ownerId);
        templateIds.clear();
    }

    /** Создаёт шаблон от имени владельца (прямой вызов сервиса) и запоминает его. */
    private TemplateResponse createTemplate(String name) {
        TemplateResponse created = TestAuth.callAs(ownerTokenLogin(), () -> templateService.create(
                ownerId,
                new CreateTemplateRequest(name, null, null, null)));
        templateIds.add(created.id());
        return created;
    }

    /** Логин владельца — {@link #ownerToken} выдан ровно на него. */
    private String ownerTokenLogin() {
        return jwtTokenProvider.getLogin(ownerToken);
    }

    @Test
    @DisplayName("PUT /api/templates/{id} returns 409 for published template")
    void update_publishedTemplate_returns409() throws Exception {
        TemplateResponse created = createTemplate("HTTP Published Template");
        TestAuth.callAs(ownerTokenLogin(), () -> templateService.publish(created.id()));

        UpdateTemplateRequest body = new UpdateTemplateRequest("Attempt to Update", null, null, null, null, null, null, null, null, null);
        mockMvc.perform(put("/api/templates/" + created.id())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("PUT /api/templates/{id} returns 200 for draft template (regression)")
    void update_draftTemplate_returns200() throws Exception {
        TemplateResponse created = createTemplate("HTTP Draft Template");

        UpdateTemplateRequest body = new UpdateTemplateRequest("Updated Draft", null, null, null, null, null, null, null, null, null);
        mockMvc.perform(put("/api/templates/" + created.id())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }
}
