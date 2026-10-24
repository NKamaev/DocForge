package com.docforge.document;

import com.docforge.audit.AuditService;
import com.docforge.common.exception.ResourceNotFoundException;
import com.docforge.document.dto.*;
import com.docforge.document.generator.ConditionEvaluator;
import com.docforge.document.generator.DocumentGenerator;
import com.docforge.document.generator.PythonConditionEvaluator;
import com.docforge.document.generator.ScriptExecutionException;
import com.docforge.document.model.Document;
import com.docforge.document.model.DocumentPermission;
import com.docforge.document.model.DocumentRegistry;
import com.docforge.document.model.DocumentRole;
import com.docforge.document.model.DocumentRevision;
import com.docforge.document.model.DocumentRevisionBody;
import com.docforge.document.model.FolderPermission;
import com.docforge.template.TemplateService;
import com.docforge.template.dto.TemplateResponse;
import com.docforge.template.model.*;
import com.docforge.user.UserService;
import com.docforge.user.dto.UserDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentRegistryRepository registryRepository;
    private final DocumentMemberRepository documentMemberRepository;
    private final DocumentPermissionService documentPermissionService;
    private final FolderPermissionService folderPermissionService;
    private final DocumentRevisionRepository revisionRepository;
    private final DocumentRevisionBodyRepository revisionBodyRepository;
    private final UserService userService;
    private final TemplateService templateService;
    private final DocumentGenerator documentGenerator;
    private final ConditionEvaluator conditionEvaluator;
    private final PythonConditionEvaluator pythonEvaluator;
    private final AuditService auditService;
    private final TipTapBodyValidator tipTapBodyValidator;

    // ── create ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public DocumentResponse create(UUID templateId, String name, UUID ownerId, UUID folderId,
                                   Integer templateVersionNumber) {
        // Verify template exists (throws 404 if not)
        TemplateResponse template = templateService.getById(templateId);

        // Шаблон доступен, если он опубликован или принадлежит пользователю.
        // Иначе можно инстанцировать чужой неопубликованный (черновой) шаблон.
        boolean owner = ownerId != null && ownerId.equals(template.ownerId());
        if (!template.published() && !owner) {
            throw new AccessDeniedException("Нет доступа к шаблону");
        }

        // Целевая папка должна быть доступна на запись.
        if (folderId != null
                && !folderPermissionService.hasPermission(folderId, ownerId, FolderPermission.WRITE)) {
            throw new AccessDeniedException("Нет прав на папку-цель");
        }

        // Resolve snapshot — use specified version or latest published.
        // Черновые версии (saveVersion) доступны только владельцу: иначе любой
        // пользователь мог бы инстанцировать неопубликованное содержимое.
        com.docforge.template.model.TemplateSnapshot snapshot = null;
        if (templateVersionNumber != null) {
            TemplateService.VersionAccess access =
                    templateService.resolveVersionAccess(templateId, templateVersionNumber);
            if (!access.exists()) {
                throw new ResourceNotFoundException(
                        "Снимок шаблона не найден: templateId=" + templateId
                                + " version=" + templateVersionNumber);
            }
            if (!owner && !access.published()) {
                log.warn("Попытка инстанцировать черновую версию {} шаблона {} (userId={})",
                        templateVersionNumber, templateId, ownerId);
                throw new AccessDeniedException("Нет доступа к черновой версии шаблона");
            }
            snapshot = access.snapshot();
            if (snapshot == null) {
                throw new ResourceNotFoundException(
                        "Снимок версии шаблона не найден: templateId=" + templateId
                                + " version=" + templateVersionNumber);
            }
        } else if (owner) {
            // Владелец работает с последней версией, включая собственный черновик.
            snapshot = templateService.findLatestSnapshot(templateId).orElse(null);
        } else {
            // Гостю — только последняя опубликованная версия, а не последний
            // сохранённый черновик.
            snapshot = templateService.findLatestPublishedSnapshot(templateId).orElse(null);
            if (snapshot == null && templateService.hasVersions(templateId)) {
                // Версии есть, но ни одна не опубликована — отдавать live-шаблон
                // нельзя: после отката он может содержать черновой контент.
                throw new AccessDeniedException("Нет опубликованной версии шаблона");
            }
            // У шаблона нет записей версий (опубликован до появления
            // версионирования) — работаем с live-шаблоном.
        }

        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        // PostgreSQL first (inside @Transactional)
        DocumentRegistry registry = new DocumentRegistry();
        registry.setId(id);
        registry.setTemplateId(templateId);
        registry.setName(name);
        registry.setOwnerId(ownerId);
        registry.setStatus("DRAFT");
        registry.setFolderId(folderId);
        if (snapshot != null) {
            registry.setTemplateSnapshotId(snapshot.getId());
            registry.setTemplateVersionNumber(snapshot.getVersionNumber());
        }
        registry = registryRepository.save(registry);

        // MongoDB second
        com.docforge.document.model.Document doc = new com.docforge.document.model.Document();
        doc.setId(id);
        doc.setTemplateId(templateId);
        if (snapshot != null) {
            // Верера документа — это версия закреплённого снимка (в Mongo и в
            // PostgreSQL одно и то же число).
            doc.setTemplateVersion(snapshot.getVersionNumber());
            doc.setTemplateSnapshotId(snapshot.getId());
        }
        doc.setAnswers(new HashMap<>());
        doc.setCreatedAt(now);
        doc.setUpdatedAt(now);
        doc = documentRepository.save(doc);

        log.info("Создан документ id={} name='{}' templateId={}", id, name, templateId);
        auditService.log(currentLogin(), "DOCUMENT", id, "CREATE", null);

        QuestionnaireResponse questionnaire = buildQuestionnaireResponse(
                snapshot != null ? snapshotToTemplate(snapshot) : toTemplate(template),
                doc.getAnswers());
        return toResponse(registry, doc, questionnaire);
    }

    // ── getById ───────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public DocumentResponse getById(UUID id) {
        DocumentRegistry registry = findRegistryOrThrow(id);
        com.docforge.document.model.Document doc = findDocOrThrow(id);
        return toResponse(registry, doc, null);
    }

    // ── list ──────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<DocumentListItemResponse> list(UUID ownerId, int page, int size) {
        return registryRepository
                .findAccessibleByUser(ownerId, PageRequest.of(page, size))
                .map(this::toListItem);
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void delete(UUID id) {
        findRegistryOrThrow(id);
        registryRepository.deleteById(id);
        try {
            documentRepository.deleteById(id);
        } catch (Exception e) {
            log.error("Документ удалён из PostgreSQL, но не удалён из MongoDB id={}: {}", id, e.getMessage());
        }
        log.info("Удалён документ id={}", id);
        auditService.log(currentLogin(), "DOCUMENT", id, "DELETE", null);
    }

    // ── existsByTemplateId ────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public boolean existsByTemplateId(UUID templateId) {
        return registryRepository.existsByTemplateId(templateId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasAccessibleDocumentForTemplateVersion(UUID templateId, int versionNumber, UUID userId) {
        if (userId == null) {
            return false;
        }
        List<DocumentRegistry> documents = registryRepository
                .findByTemplateIdAndTemplateVersionNumber(templateId, versionNumber);
        for (DocumentRegistry document : documents) {
            // Проверяем именно право чтения: так правило останется верным,
            // если появится роль без доступа к содержимому.
            if (documentPermissionService.hasPermission(
                    document.getId(), userId, DocumentPermission.READ)) {
                return true;
            }
        }
        return false;
    }

    // ── getQuestionnaire ──────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public QuestionnaireResponse getQuestionnaire(UUID documentId) {
        com.docforge.document.model.Document doc = findDocOrThrow(documentId);
        DocumentRegistry registry = findRegistryOrThrow(documentId);
        Template template = resolveDocumentTemplate(doc, registry.getTemplateId());
        return buildQuestionnaireResponse(template, doc.getAnswers());
    }

    // ── saveAnswers ───────────────────────────────────────────────────────────

    @Override
    @Transactional
    public SaveAnswersResponse saveAnswers(UUID documentId, Map<String, Object> newAnswers) {
        com.docforge.document.model.Document doc = findDocOrThrow(documentId);
        DocumentRegistry registry = findRegistryOrThrow(documentId);
        Template template = resolveDocumentTemplate(doc, registry.getTemplateId());

        // Merge: existing answers ← new answers (new overwrite existing)
        Map<String, Object> merged = new HashMap<>(
                doc.getAnswers() != null ? doc.getAnswers() : Map.of());
        merged.putAll(newAnswers);

        doc.setAnswers(merged);
        doc.setUpdatedAt(Instant.now());
        documentRepository.save(doc);

        log.debug("Сохранены ответы для документа id={}, keys={}", documentId, merged.keySet());

        QuestionnaireResponse questionnaire = buildQuestionnaireResponse(template, merged);
        return new SaveAnswersResponse(Collections.unmodifiableMap(merged), questionnaire);
    }

    // ── generate ─────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public GenerateResponse generate(UUID documentId) {
        com.docforge.document.model.Document doc = findDocOrThrow(documentId);
        DocumentRegistry registry = findRegistryOrThrow(documentId);

        // Prefer snapshot (immutable) over live template to avoid post-publish changes
        Template template = resolveDocumentTemplate(doc, registry.getTemplateId());

        Map<String, Object> answers = doc.getAnswers() != null ? doc.getAnswers() : Map.of();

        Object generatedBody = documentGenerator.generate(template, answers);

        doc.setGeneratedBody(generatedBody);
        doc.setCurrentBody(generatedBody); // initial editable copy = generated result
        doc.setUpdatedAt(Instant.now());
        documentRepository.save(doc);

        registry.setStatus("GENERATED");
        registryRepository.save(registry);

        log.info("Сгенерирован документ id={}", documentId);
        auditService.log(currentLogin(), "DOCUMENT", documentId, "GENERATE", null);
        return new GenerateResponse(documentId, generatedBody);
    }

    // ── updateBody ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void updateBody(UUID documentId, Object currentBody) {
        com.docforge.document.model.Document doc = findDocOrThrow(documentId);
        // Валидируем структуру на границе записи: некорректное тело должно
        // давать 422 здесь, а не 500 при экспорте (BUG-063).
        tipTapBodyValidator.validate(currentBody);
        doc.setCurrentBody(currentBody);
        doc.setUpdatedAt(Instant.now());
        documentRepository.save(doc);
        log.debug("Обновлено currentBody документа id={}", documentId);
    }

    // ── Collaboration: Yjs state ──────────────────────────────────────────────

    @Override
    public byte[] getYjsState(UUID documentId) {
        return documentRepository.findById(documentId)
                .map(com.docforge.document.model.Document::getYjsState)
                .orElse(null);
    }

    @Override
    @Transactional
    public void moveToFolder(UUID documentId, UUID folderId) {
        DocumentRegistry registry = findRegistryOrThrow(documentId);
        registry.setFolderId(folderId);
        registryRepository.save(registry);
        log.info("Документ id={} перемещён в папку folderId={}", documentId, folderId);
    }

    @Override
    public void saveYjsState(UUID documentId, byte[] state) {
        documentRepository.findById(documentId).ifPresent(doc -> {
            doc.setYjsState(state);
            doc.setUpdatedAt(Instant.now());
            documentRepository.save(doc);
            log.debug("Сохранено YjsState документа id={}, {} байт", documentId, state.length);
        });
    }

    // ── Questionnaire builder ─────────────────────────────────────────────────

    private QuestionnaireResponse buildQuestionnaireResponse(
            Template template, Map<String, Object> answers) {

        Questionnaire questionnaire = template.getQuestionnaire();
        if (questionnaire == null) {
            return new QuestionnaireResponse(null, List.of());
        }

        // Index parameters by code for quick lookups
        Map<String, Parameter> paramIndex = (template.getParameters() != null
                ? template.getParameters() : List.<Parameter>of()).stream()
                .collect(Collectors.toMap(Parameter::getCode, p -> p));

        Map<String, Object> safeAnswers = answers != null ? answers : Map.of();

        List<QuestionnaireResponse.PageItem> pages = (questionnaire.getPages() != null
                ? questionnaire.getPages() : List.<QuestionnairePage>of()).stream()
                .map(page -> {
                    List<QuestionnaireResponse.QuestionItem> questions =
                            (page.getQuestions() != null ? page.getQuestions() : List.<Question>of())
                                    .stream()
                                    .sorted(Comparator.comparingInt(Question::getOrder))
                                    .map(q -> buildQuestionItem(q, paramIndex, safeAnswers))
                                    .collect(Collectors.toList());

                    // Page id is stored in a field not present in current model;
                    // use title as stable key for now (questionnaire pages have no id in model)
                    return new QuestionnaireResponse.PageItem(null, page.getTitle(), questions);
                })
                .collect(Collectors.toList());

        return new QuestionnaireResponse(questionnaire.getTitle(), pages);
    }

    private QuestionnaireResponse.QuestionItem buildQuestionItem(
            Question question,
            Map<String, Parameter> paramIndex,
            Map<String, Object> answers) {

        Parameter param = paramIndex.get(question.getParameterCode());

        // Evaluate visibility condition
        boolean visible = true;
        Condition cond = question.getVisibilityCondition();
        if (cond != null && cond.getExpression() != null && !cond.getExpression().isBlank()) {
            visible = evalCondition(cond, answers);
        }

        return new QuestionnaireResponse.QuestionItem(
                question.getId(),
                question.getParameterCode(),
                question.getLabel(),
                question.getHint(),
                question.getOrder(),
                param != null ? param.getType().name().toLowerCase() : "text",
                param != null ? param.getOptions() : null,
                visible,
                param != null && param.isRequired(),
                answers.get(question.getParameterCode()),
                param != null ? param.getDirectoryId() : null,
                param != null ? param.getDirectoryFields() : null
        );
    }

    // ── Condition evaluation ──────────────────────────────────────────────────

    private boolean evalCondition(Condition condition, Map<String, Object> answers) {
        if (condition == null || condition.getExpression() == null) return true;
        try {
            if (condition.getType() == ConditionType.PYTHON) {
                return pythonEvaluator.evaluate(condition.getExpression(), answers);
            }
            return conditionEvaluator.evaluate(condition.getExpression(), answers);
        } catch (ScriptExecutionException e) {
            log.warn("Ошибка вычисления условия видимости (treating as true): {}", e.getMessage());
            return true; // show question on error to avoid data loss
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private DocumentRegistry findRegistryOrThrow(UUID id) {
        return registryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Документ не найден: " + id));
    }

    private com.docforge.document.model.Document findDocOrThrow(UUID id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Тело документа не найдено в MongoDB: " + id));
    }

    private Template toTemplate(TemplateResponse r) {
        return Template.builder()
                .id(r.id())
                .version(r.version())
                .parameters(r.parameters())
                .questionnaire(r.questionnaire())
                .body(r.body())
                .formatting(r.formatting())
                .scripts(r.scripts())
                .build();
    }

    private Template snapshotToTemplate(com.docforge.template.model.TemplateSnapshot s) {
        return Template.builder()
                .id(s.getTemplateId())
                .version(s.getVersionNumber())
                .parameters(s.getParameters())
                .questionnaire(s.getQuestionnaire())
                .body(s.getBody())
                .formatting(s.getFormatting())
                .scripts(s.getScripts())
                .phrasings(s.getPhrasings())
                .build();
    }

    /**
     * Resolve the template content a document is pinned to. Prefers the immutable
     * snapshot (the version the document was created from) so the questionnaire,
     * parameters and body stay consistent with the generated document. Falls back
     * to the live template only when the snapshot is missing (deleted) — any other
     * failure while loading the snapshot is propagated, never silently masked.
     */
    private Template resolveDocumentTemplate(
            com.docforge.document.model.Document doc, UUID templateId) {
        if (doc.getTemplateSnapshotId() != null) {
            try {
                return snapshotToTemplate(
                        templateService.getSnapshotById(doc.getTemplateSnapshotId()));
            } catch (ResourceNotFoundException e) {
                log.warn("Снимок шаблона {} не найден для документа {}, используем live-шаблон: {}",
                        doc.getTemplateSnapshotId(), doc.getId(), e.getMessage());
            }
        }
        return toTemplate(templateService.getById(templateId));
    }

    private DocumentResponse toResponse(
            DocumentRegistry registry,
            com.docforge.document.model.Document doc,
            QuestionnaireResponse questionnaire) {
        return new DocumentResponse(
                registry.getId(),
                registry.getName(),
                registry.getStatus(),
                registry.getTemplateId(),
                registry.getTemplateSnapshotId(),
                registry.getTemplateVersionNumber(),
                registry.getOwnerId(),
                registry.getFolderId(),
                registry.getCreatedAt(),
                registry.getUpdatedAt(),
                doc.getAnswers(),
                doc.getGeneratedBody(),
                doc.getCurrentBody(),
                questionnaire
        );
    }

    private DocumentListItemResponse toListItem(DocumentRegistry r) {
        return new DocumentListItemResponse(
                r.getId(), r.getName(), r.getStatus(), r.getTemplateId(), r.getFolderId(), r.getCreatedAt()
        );
    }

    // ── Participants ──────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<DocumentParticipantDTO> getParticipants(UUID documentId) {
        findRegistryOrThrow(documentId);
        return documentMemberRepository.findByDocumentId(documentId).stream()
                .map(m -> {
                    UserDTO user = userService.findUserById(m.getUserId()).orElse(null);
                    return new DocumentParticipantDTO(
                            m.getUserId(),
                            user != null ? user.login() : null,
                            user != null ? user.displayName() : null,
                            m.getRole(),
                            m.getGrantedAt() != null ? m.getGrantedAt().atOffset(java.time.ZoneOffset.UTC) : null
                    );
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public DocumentParticipantDTO addParticipant(UUID documentId, UUID userId, String role, UUID grantedBy) {
        findRegistryOrThrow(documentId);
        UserDTO user = userService.findUserById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден: " + userId));

        DocumentRole documentRole = DocumentRole.valueOf(role != null ? role.toUpperCase() : "EDITOR");
        documentPermissionService.grantRole(documentId, userId, documentRole, grantedBy);

        return documentMemberRepository.findByDocumentIdAndUserId(documentId, userId)
                .map(m -> new DocumentParticipantDTO(
                        userId, user.login(), user.displayName(),
                        m.getRole(),
                        m.getGrantedAt() != null ? m.getGrantedAt().atOffset(java.time.ZoneOffset.UTC) : null
                ))
                .orElse(new DocumentParticipantDTO(userId, user.login(), user.displayName(), documentRole.name(), null));
    }

    @Override
    @Transactional
    public void removeParticipant(UUID documentId, UUID userId, UUID revokedBy) {
        findRegistryOrThrow(documentId);
        documentPermissionService.revokeRole(documentId, userId, revokedBy);
    }

    // ── Editions ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public DocumentRevisionDTO saveRevision(UUID documentId, String comment) {
        com.docforge.document.model.Document doc = findDocOrThrow(documentId);
        findRegistryOrThrow(documentId);

        int nextNumber = revisionRepository.findMaxRevisionNumber(documentId) + 1;
        OffsetDateTime now = OffsetDateTime.now();

        DocumentRevision revision = new DocumentRevision();
        revision.setDocumentId(documentId);
        revision.setRevisionNumber(nextNumber);
        revision.setSavedBy(currentUserId());
        revision.setSavedAt(now);
        revision.setComment(comment);
        revision = revisionRepository.save(revision);

        DocumentRevisionBody body = DocumentRevisionBody.builder()
                .id(revision.getId())
                .documentId(documentId)
                .revisionNumber(nextNumber)
                .body(doc.getCurrentBody())
                .answers(doc.getAnswers())
                .savedBy(revision.getSavedBy())
                .savedAt(now.toInstant())
                .comment(comment)
                .build();
        revisionBodyRepository.save(body);

        log.info("Редакция документа id={} edition={}", documentId, nextNumber);
        return toRevisionDTO(revision);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentRevisionDTO> listRevisions(UUID documentId) {
        findRegistryOrThrow(documentId);
        return revisionRepository.findByDocumentIdOrderByRevisionNumberDesc(documentId)
                .stream().map(this::toRevisionDTO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentRevisionBody getRevisionBody(UUID documentId, int revisionNumber) {
        findRegistryOrThrow(documentId);
        DocumentRevision rev = revisionRepository.findByDocumentIdAndRevisionNumber(documentId, revisionNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Редакция не найдена: documentId=" + documentId + " edition=" + revisionNumber));
        return revisionBodyRepository.findById(rev.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Тело редакции не найдено: " + rev.getId()));
    }

    @Override
    @Transactional
    public DocumentRevisionDTO restoreRevision(UUID documentId, int revisionNumber) {
        DocumentRevisionBody revBody = getRevisionBody(documentId, revisionNumber);
        com.docforge.document.model.Document doc = findDocOrThrow(documentId);

        // Восстановленное тело должно быть валидным деревом: иначе старый мусор
        // снова попал бы в current_body и уронил бы экспорт (BUG-063).
        tipTapBodyValidator.validate(revBody.getBody());
        doc.setCurrentBody(revBody.getBody());
        if (revBody.getAnswers() != null) {
            doc.setAnswers(revBody.getAnswers());
        }
        doc.setYjsState(null);
        doc.setUpdatedAt(Instant.now());
        documentRepository.save(doc);

        String comment = "Откат к редакции " + revisionNumber;
        return saveRevision(documentId, comment);
    }

    private DocumentRevisionDTO toRevisionDTO(DocumentRevision r) {
        String authorName = null;
        if (r.getSavedBy() != null) {
            // findUserById() не бросает исключений — иначе исключение внутри
            // общей транзакции пометило бы её rollback-only (BUG-061).
            Optional<UserDTO> author = userService.findUserById(r.getSavedBy());
            if (author.isPresent()) {
                authorName = author.get().displayName();
            } else {
                log.debug("Автор редакции не найден (удалён): savedBy={}", r.getSavedBy());
            }
        }
        return new DocumentRevisionDTO(
                r.getId(), r.getDocumentId(), r.getRevisionNumber(),
                r.getSavedBy(), authorName, r.getSavedAt(), r.getComment()
        );
    }

    private UUID currentUserId() {
        // Best-effort: try to get user ID from security context via login.
        // findUser() не бросает исключений — иначе исключение внутри общей
        // транзакции пометило бы её rollback-only (BUG-061).
        String login = currentLogin();
        if (login == null) return null;
        return userService.findUser(login).map(UserDTO::id).orElse(null);
    }

    private String currentLogin() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    // ── Revision Diff ───────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public RevisionDiffResponse computeRevisionDiff(
            UUID documentId,
            int revisionNumber,
            Integer compareToRevisionNumber) {

        DocumentRevisionBody targetBody = getRevisionBody(documentId, revisionNumber);

        Object compareBody;
        boolean compareToCurrent = compareToRevisionNumber == null;
        Integer actualCompareToNumber = null;

        if (compareToCurrent) {
            Document doc = findDocOrThrow(documentId);
            compareBody = doc.getCurrentBody() != null ? doc.getCurrentBody() : doc.getGeneratedBody();
        } else {
            DocumentRevisionBody compareRevision = getRevisionBody(documentId, compareToRevisionNumber);
            compareBody = compareRevision.getBody();
            actualCompareToNumber = compareToRevisionNumber;
        }

        return new RevisionDiffResponse(
                targetBody.getId(),
                revisionNumber,
                actualCompareToNumber,
                compareToCurrent,
                targetBody.getBody(),
                compareBody
        );
    }
}
