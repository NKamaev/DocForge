package com.docforge.document;

import com.docforge.audit.AuditService;
import com.docforge.common.exception.ResourceNotFoundException;
import com.docforge.document.dto.*;
import com.docforge.document.generator.ConditionEvaluator;
import com.docforge.document.generator.DocumentGenerator;
import com.docforge.document.generator.PythonConditionEvaluator;
import com.docforge.document.generator.ScriptExecutionException;
import com.docforge.document.model.Document;
import com.docforge.document.model.DocumentRegistry;
import com.docforge.document.model.DocumentRole;
import com.docforge.document.model.DocumentRevision;
import com.docforge.document.model.DocumentRevisionBody;
import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;
import com.docforge.template.TemplateService;
import com.docforge.template.dto.TemplateResponse;
import com.docforge.template.model.*;
import com.docforge.user.User;
import com.docforge.user.UserRepository;
import com.docforge.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
    private final DocumentRevisionRepository revisionRepository;
    private final DocumentRevisionBodyRepository revisionBodyRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final TemplateService templateService;
    private final DocumentGenerator documentGenerator;
    private final ConditionEvaluator conditionEvaluator;
    private final PythonConditionEvaluator pythonEvaluator;
    private final AuditService auditService;

    // ── create ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public DocumentResponse create(UUID templateId, String name, UUID ownerId, UUID folderId,
                                   Integer templateVersionNumber) {
        // Verify template exists (throws 404 if not)
        TemplateResponse template = templateService.getById(templateId);

        // Resolve snapshot — use specified version or latest published
        com.docforge.template.model.TemplateSnapshot snapshot = null;
        if (templateVersionNumber != null) {
            snapshot = templateService.getSnapshot(templateId, templateVersionNumber);
        } else {
            snapshot = templateService.findLatestSnapshot(templateId).orElse(null);
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
        doc.setTemplateVersion(template.version());
        if (snapshot != null) {
            doc.setTemplateSnapshotId(snapshot.getId());
        }
        doc.setAnswers(new HashMap<>());
        doc.setCreatedAt(now);
        doc.setUpdatedAt(now);
        doc = documentRepository.save(doc);

        log.info("Создан документ id={} name='{}' templateId={}", id, name, templateId);
        auditService.log(currentLogin(), "DOCUMENT", id, "CREATE", null);

        QuestionnaireResponse questionnaire =
                buildQuestionnaireResponse(template, doc.getAnswers());
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

    // ── getQuestionnaire ──────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public QuestionnaireResponse getQuestionnaire(UUID documentId) {
        com.docforge.document.model.Document doc = findDocOrThrow(documentId);
        DocumentRegistry registry = findRegistryOrThrow(documentId);
        TemplateResponse template = templateService.getById(registry.getTemplateId());
        return buildQuestionnaireResponse(template, doc.getAnswers());
    }

    // ── saveAnswers ───────────────────────────────────────────────────────────

    @Override
    @Transactional
    public SaveAnswersResponse saveAnswers(UUID documentId, Map<String, Object> newAnswers) {
        com.docforge.document.model.Document doc = findDocOrThrow(documentId);
        DocumentRegistry registry = findRegistryOrThrow(documentId);
        TemplateResponse template = templateService.getById(registry.getTemplateId());

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
        Template template;
        if (doc.getTemplateSnapshotId() != null) {
            com.docforge.template.model.TemplateSnapshot snapshot =
                    templateService.getSnapshotById(doc.getTemplateSnapshotId());
            template = snapshotToTemplate(snapshot);
        } else {
            TemplateResponse templateResponse = templateService.getById(registry.getTemplateId());
            template = toTemplate(templateResponse);
        }

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
            TemplateResponse template, Map<String, Object> answers) {

        Questionnaire questionnaire = template.questionnaire();
        if (questionnaire == null) {
            return new QuestionnaireResponse(null, List.of());
        }

        // Index parameters by code for quick lookups
        Map<String, Parameter> paramIndex = (template.parameters() != null
                ? template.parameters() : List.<Parameter>of()).stream()
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
                    User user = userRepository.findById(m.getUserId()).orElse(null);
                    return new DocumentParticipantDTO(
                            m.getUserId(),
                            user != null ? user.getLogin() : null,
                            user != null ? user.getDisplayName() : null,
                            m.getRole(),
                            m.getGrantedAt() != null ? m.getGrantedAt().atOffset(java.time.ZoneOffset.UTC) : null
                    );
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public DocumentParticipantDTO addParticipant(UUID documentId, UUID userId, String role) {
        findRegistryOrThrow(documentId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден: " + userId));

        DocumentRole documentRole = DocumentRole.valueOf(role != null ? role.toUpperCase() : "EDITOR");
        documentPermissionService.grantRole(documentId, userId, documentRole, null);

        return documentMemberRepository.findByDocumentIdAndUserId(documentId, userId)
                .map(m -> new DocumentParticipantDTO(
                        userId, user.getLogin(), user.getDisplayName(),
                        m.getRole(),
                        m.getGrantedAt() != null ? m.getGrantedAt().atOffset(java.time.ZoneOffset.UTC) : null
                ))
                .orElse(new DocumentParticipantDTO(userId, user.getLogin(), user.getDisplayName(), documentRole.name(), null));
    }

    @Override
    @Transactional
    public void removeParticipant(UUID documentId, UUID userId) {
        findRegistryOrThrow(documentId);
        documentPermissionService.revokeRole(documentId, userId, null);
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
            try {
                authorName = userService.getById(r.getSavedBy()).displayName();
            } catch (Exception e) {
                log.warn("Не удалось загрузить автора редакции savedBy={}: {}", r.getSavedBy(), e.getMessage());
            }
        }
        return new DocumentRevisionDTO(
                r.getId(), r.getDocumentId(), r.getRevisionNumber(),
                r.getSavedBy(), authorName, r.getSavedAt(), r.getComment()
        );
    }

    private UUID currentUserId() {
        // Best-effort: try to get user ID from security context via login
        String login = currentLogin();
        if (login == null) return null;
        try {
            return userRepository.findByLogin(login).map(User::getId).orElse(null);
        } catch (Exception e) {
            return null;
        }
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

        Object compareBodyObj;
        boolean compareToCurrent = compareToRevisionNumber == null;
        Integer actualCompareToNumber = null;

        if (compareToCurrent) {
            Document doc = findDocOrThrow(documentId);
            compareBodyObj = doc.getCurrentBody() != null ? doc.getCurrentBody() : doc.getGeneratedBody();
        } else {
            DocumentRevisionBody compareBody = getRevisionBody(documentId, compareToRevisionNumber);
            compareBodyObj = compareBody.getBody();
            actualCompareToNumber = compareToRevisionNumber;
        }

        String targetText = extractPlainText(targetBody.getBody());
        String compareText = extractPlainText(compareBodyObj);

        List<String> targetLines = targetText.lines().toList();
        List<String> compareLines = compareText.lines().toList();

        Patch<String> patch = DiffUtils.diff(compareLines, targetLines);

        List<DiffChunk> chunks = new ArrayList<>();
        if (patch.getDeltas().isEmpty()) {
            chunks.add(new DiffChunk(DiffChunk.DiffChunkType.EQUAL, targetText));
        } else {
            int sourceCursor = 0;
            for (AbstractDelta<String> delta : patch.getDeltas()) {
                int sourceStart = delta.getSource().getPosition();
                if (sourceStart > sourceCursor) {
                    appendChunk(chunks, DiffChunk.DiffChunkType.EQUAL,
                            compareLines.subList(sourceCursor, sourceStart));
                }
                switch (delta.getType()) {
                    case INSERT -> appendChunk(chunks, DiffChunk.DiffChunkType.INSERT,
                            delta.getTarget().getLines());
                    case DELETE -> appendChunk(chunks, DiffChunk.DiffChunkType.DELETE,
                            delta.getSource().getLines());
                    case CHANGE -> {
                        appendChunk(chunks, DiffChunk.DiffChunkType.DELETE,
                                delta.getSource().getLines());
                        appendChunk(chunks, DiffChunk.DiffChunkType.INSERT,
                                delta.getTarget().getLines());
                    }
                }
                sourceCursor = sourceStart + delta.getSource().size();
            }
            if (sourceCursor < compareLines.size()) {
                appendChunk(chunks, DiffChunk.DiffChunkType.EQUAL,
                        compareLines.subList(sourceCursor, compareLines.size()));
            }
        }

        return new RevisionDiffResponse(
                targetBody.getId(),
                revisionNumber,
                actualCompareToNumber,
                compareToCurrent,
                chunks
        );
    }

    private void appendChunk(List<DiffChunk> chunks, DiffChunk.DiffChunkType type, List<String> lines) {
        if (lines.isEmpty()) return;
        String text = String.join("\n", lines) + "\n";
        if (!chunks.isEmpty() && chunks.get(chunks.size() - 1).type() == type) {
            DiffChunk last = chunks.get(chunks.size() - 1);
            chunks.set(chunks.size() - 1, new DiffChunk(type, last.text() + text));
        } else {
            chunks.add(new DiffChunk(type, text));
        }
    }

    @SuppressWarnings("unchecked")
    private String extractPlainText(Object body) {
        if (body == null) return "";
        if (!(body instanceof Map<?, ?>)) return body.toString();

        Map<String, Object> bodyMap = (Map<String, Object>) body;
        StringBuilder sb = new StringBuilder();
        extractTextRecursive(bodyMap, sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private void extractTextRecursive(Map<String, Object> node, StringBuilder sb) {
        if (node == null) return;

        String type = (String) node.get("type");
        if ("text".equals(type)) {
            Object text = node.get("text");
            if (text != null) {
                sb.append(text);
            }
            return;
        }

        Object attrs = node.get("attrs");
        if (attrs instanceof Map<?, ?> attrMap) {
            Object display = attrMap.get("display");
            if ("parameterField".equals(type) && display != null) {
                sb.append("[").append(display).append("]");
            }
        }

        Object content = node.get("content");
        if (content instanceof List<?> contentList) {
            for (Object item : contentList) {
                if (item instanceof Map<?, ?>) {
                    extractTextRecursive((Map<String, Object>) item, sb);
                }
            }
        }

        if ("paragraph".equals(type) || "heading".equals(type) || "listItem".equals(type)) {
            sb.append("\n");
        }
    }
}
