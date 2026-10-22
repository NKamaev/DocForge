package com.docforge.template;

import com.docforge.audit.AuditService;
import com.docforge.common.exception.BusinessException;
import com.docforge.common.exception.ResourceNotFoundException;
import com.docforge.common.exception.TemplatePublishedException;
import com.docforge.document.DocumentService;
import com.docforge.document.generator.PythonConditionEvaluator;
import com.docforge.document.generator.ScriptExecutionException;
import com.docforge.user.UserService;
import com.docforge.template.dto.CreateTemplateRequest;
import com.docforge.template.dto.DiffResult;
import com.docforge.template.dto.RollbackResponse;
import com.docforge.template.dto.SaveVersionRequest;
import com.docforge.template.dto.SaveVersionResponse;
import com.docforge.template.dto.ScriptTestRequest;
import com.docforge.template.dto.ScriptTestResult;
import com.docforge.template.dto.TemplateListItemResponse;
import com.docforge.template.dto.TemplateResponse;
import com.docforge.template.dto.TemplateVersionDTO;
import com.docforge.template.dto.UpdateTemplateRequest;
import com.docforge.template.model.Script;
import com.docforge.template.model.Template;
import com.docforge.template.model.TemplateRegistry;
import com.docforge.template.model.TemplateSnapshot;
import com.docforge.template.model.TemplateVersion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateServiceImpl implements TemplateService {

    private final TemplateRegistryRepository registryRepository;
    private final TemplateRepository templateRepository;
    private final TemplateVersionRepository versionRepository;
    private final TemplateSnapshotRepository snapshotRepository;
    private final TemplateDiffService templateDiffService;
    private final AuditService auditService;
    private final TemplateValidator templateValidator;
    private final PythonConditionEvaluator pythonConditionEvaluator;

    // @Lazy разрывает цикл: DocumentServiceImpl → TemplateService → TemplateServiceImpl
    @Lazy
    @Autowired
    private DocumentService documentService;

    @Autowired
    private UserService userService;

    @Override
    @Transactional
    public TemplateResponse create(UUID ownerId, CreateTemplateRequest request) {
        UUID id = UUID.randomUUID();

        TemplateRegistry registry = new TemplateRegistry();
        registry.setId(id);
        registry.setName(request.name());
        registry.setDescription(request.description());
        registry.setCategory(request.category());
        registry.setOwnerId(ownerId);
        registry.setDefaultFolderId(request.defaultFolderId());
        registry.setMajorVersion(1);
        registry.setMinorVersion(0);
        registry = registryRepository.save(registry);

        Template template = Template.builder()
                .id(id)
                .version(1)
                .build();
        template = templateRepository.save(template);

        log.info("Создан шаблон id={} name='{}'", id, request.name());
        auditService.log(currentLogin(), "TEMPLATE", id, "CREATE", null);
        return toResponse(registry, template);
    }

    @Override
    @Transactional(readOnly = true)
    public TemplateResponse getById(UUID id) {
        TemplateRegistry registry = findRegistryOrThrow(id);
        Template template = findTemplateOrThrow(id);
        return toResponse(registry, template);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TemplateListItemResponse> list(int page, int size, String category, Boolean isPublished) {
        Specification<TemplateRegistry> spec = Specification.where(null);

        if (category != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("category"), category));
        }
        if (isPublished != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("published"), isPublished));
        }

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return registryRepository.findAll(spec, pageRequest).map(this::toListItem);
    }

    @Override
    @Transactional
    public TemplateResponse update(UUID id, UpdateTemplateRequest request) {
        TemplateRegistry registry = findRegistryOrThrow(id);
        if (registry.isPublished()) {
            throw new TemplatePublishedException(id);
        }
        registry.setName(request.name());
        registry.setDescription(request.description());
        registry.setCategory(request.category());
        registry.setDefaultFolderId(request.defaultFolderId());
        registry = registryRepository.save(registry);

        Template template = findTemplateOrThrow(id);
        template.setParameters(request.parameters());
        template.setQuestionnaire(request.questionnaire());
        template.setBody(request.body());
        template.setScripts(request.scripts());
        template.setFormatting(request.formatting());
        template.setPhrasings(request.phrasings());
        template = templateRepository.save(template);

        log.info("Обновлён шаблон id={}", id);
        auditService.log(currentLogin(), "TEMPLATE", id, "UPDATE", null);
        return toResponse(registry, template);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        TemplateRegistry registry = findRegistryOrThrow(id);
        if (registry.isPublished()) {
            throw new TemplatePublishedException(id);
        }
        if (documentService.existsByTemplateId(id)) {
            throw new BusinessException(
                    "Нельзя удалить шаблон: на него ссылаются существующие документы");
        }
        registryRepository.deleteById(id);
        try {
            templateRepository.deleteById(id);
        } catch (Exception e) {
            log.error("Шаблон удалён из PostgreSQL, но не удалён из MongoDB id={}: {}", id, e.getMessage());
        }
        log.info("Удалён шаблон id={}", id);
        auditService.log(currentLogin(), "TEMPLATE", id, "DELETE", null);
    }

    @Override
    @Transactional
    public TemplateResponse publish(UUID id) {
        return setPublished(id, true);
    }

    @Override
    @Transactional
    public TemplateResponse unpublish(UUID id) {
        return setPublished(id, false);
    }

    // ── Versioning ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public SaveVersionResponse saveVersion(UUID templateId, SaveVersionRequest request) {
        TemplateRegistry registry = findRegistryOrThrow(templateId);
        Template template = findTemplateOrThrow(templateId);

        // Ensure current content is persisted before snapshot
        template = templateRepository.save(template);

        // Determine new version numbers
        int newMajor = registry.getMajorVersion();
        int newMinor = registry.getMinorVersion();
        if ("MAJOR".equals(request.bumpType())) {
            newMajor++;
            newMinor = 0;
        } else {
            newMinor++;
        }
        int newVersionNumber = newMajor * 10000 + newMinor;

        // Build auto-diff description
        String autoDescription = buildAutoDescription(templateId, template, newMajor, newMinor);

        // Update registry
        registry.setMajorVersion(newMajor);
        registry.setMinorVersion(newMinor);
        registry.setVersion(newVersionNumber);
        registry = registryRepository.save(registry);

        // Create version metadata
        TemplateVersion version = new TemplateVersion();
        version.setTemplateId(templateId);
        version.setVersionNumber(newVersionNumber);
        version.setCreatedBy(currentUserId());
        version.setCreatedAt(OffsetDateTime.now());
        version.setComment(request.comment());
        version.setBumpType(request.bumpType());
        version.setAutoDescription(autoDescription);
        version.setMajorVersion(newMajor);
        version.setMinorVersion(newMinor);
        versionRepository.save(version);

        // Create snapshot in MongoDB
        TemplateSnapshot snapshot = TemplateSnapshot.builder()
                .id(version.getId())
                .templateId(templateId)
                .versionNumber(newVersionNumber)
                .majorVersion(newMajor)
                .minorVersion(newMinor)
                .parameters(template.getParameters())
                .questionnaire(template.getQuestionnaire())
                .body(template.getBody())
                .formatting(template.getFormatting())
                .scripts(template.getScripts())
                .phrasings(template.getPhrasings())
                .createdAt(version.getCreatedAt().toInstant())
                .build();
        snapshotRepository.save(snapshot);

        log.info("Создана версия шаблона id={} v{}.{} ({})", templateId, newMajor, newMinor, request.bumpType());
        auditService.log(currentLogin(), "TEMPLATE", templateId, "SAVE_VERSION",
                "version=" + newMajor + "." + newMinor + ",bumpType=" + request.bumpType());

        TemplateVersionDTO dto = toVersionDTO(version);
        return new SaveVersionResponse(dto, toResponse(registry, template));
    }

    @Override
    @Transactional
    public RollbackResponse rollback(UUID templateId, int versionNumber) {
        TemplateRegistry registry = findRegistryOrThrow(templateId);
        Template template = findTemplateOrThrow(templateId);
        TemplateSnapshot snapshot = getSnapshot(templateId, versionNumber);

        // Restore template content from snapshot
        template.setParameters(snapshot.getParameters());
        template.setQuestionnaire(snapshot.getQuestionnaire());
        template.setBody(snapshot.getBody());
        template.setFormatting(snapshot.getFormatting());
        template.setScripts(snapshot.getScripts());
        template.setPhrasings(snapshot.getPhrasings());
        template = templateRepository.save(template);

        // Create a new version as rollback
        int newMajor = registry.getMajorVersion();
        int newMinor = registry.getMinorVersion() + 1;
        int newVersionNumber = newMajor * 10000 + newMinor;

        registry.setMinorVersion(newMinor);
        registry.setVersion(newVersionNumber);
        registry = registryRepository.save(registry);

        String autoDescription = "Откат к версии v" + snapshot.getMajorVersion() + "." + snapshot.getMinorVersion();

        TemplateVersion version = new TemplateVersion();
        version.setTemplateId(templateId);
        version.setVersionNumber(newVersionNumber);
        version.setCreatedBy(currentUserId());
        version.setCreatedAt(OffsetDateTime.now());
        version.setBumpType("ROLLBACK");
        version.setAutoDescription(autoDescription);
        version.setRollbackFrom(versionNumber);
        version.setMajorVersion(newMajor);
        version.setMinorVersion(newMinor);
        versionRepository.save(version);

        // Create snapshot
        TemplateSnapshot newSnapshot = TemplateSnapshot.builder()
                .id(version.getId())
                .templateId(templateId)
                .versionNumber(newVersionNumber)
                .majorVersion(newMajor)
                .minorVersion(newMinor)
                .parameters(template.getParameters())
                .questionnaire(template.getQuestionnaire())
                .body(template.getBody())
                .formatting(template.getFormatting())
                .scripts(template.getScripts())
                .phrasings(template.getPhrasings())
                .createdAt(version.getCreatedAt().toInstant())
                .build();
        snapshotRepository.save(newSnapshot);

        log.info("Откат шаблона id={} к версии {} -> новая v{}.{}", templateId, versionNumber, newMajor, newMinor);
        auditService.log(currentLogin(), "TEMPLATE", templateId, "ROLLBACK",
                "fromVersion=" + versionNumber + ",newVersion=" + newMajor + "." + newMinor);

        TemplateVersionDTO dto = toVersionDTO(version);
        return new RollbackResponse(dto, toResponse(registry, template));
    }

    @Override
    public DiffResult getDiff(UUID templateId, int fromVersion, int toVersion) {
        findRegistryOrThrow(templateId);
        TemplateSnapshot from = getSnapshot(templateId, fromVersion);
        TemplateSnapshot to = getSnapshot(templateId, toVersion);
        return templateDiffService.diff(from, to);
    }

    @Override
    public List<TemplateVersionDTO> listVersions(UUID templateId) {
        findRegistryOrThrow(templateId);
        return versionRepository.findByTemplateIdOrderByVersionNumberDesc(templateId)
                .stream()
                .map(this::toVersionDTO)
                .toList();
    }

    @Override
    public TemplateSnapshot getSnapshot(UUID templateId, int versionNumber) {
        findRegistryOrThrow(templateId);
        return snapshotRepository.findFirstByTemplateIdAndVersionNumberOrderByCreatedAtDesc(templateId, versionNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Снимок шаблона не найден: templateId=" + templateId + " version=" + versionNumber));
    }

    @Override
    public Optional<TemplateSnapshot> findLatestSnapshot(UUID templateId) {
        return versionRepository.findByTemplateIdOrderByVersionNumberDesc(templateId)
                .stream()
                .findFirst()
                .flatMap(v -> snapshotRepository.findById(v.getId()));
    }

    @Override
    public TemplateSnapshot getSnapshotById(UUID snapshotId) {
        return snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new ResourceNotFoundException("Снимок шаблона не найден: " + snapshotId));
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private TemplateResponse setPublished(UUID id, boolean published) {
        TemplateRegistry registry = findRegistryOrThrow(id);
        Template template = findTemplateOrThrow(id);

        if (published) {
            int pgMax = versionRepository.findMaxVersionNumber(id);
            int mongoMax = snapshotRepository.findFirstByTemplateIdOrderByVersionNumberDesc(id)
                    .map(TemplateSnapshot::getVersionNumber)
                    .orElse(0);
            int nextVersion = Math.max(pgMax, mongoMax) + 1;
            registry.setVersion(nextVersion);

            TemplateVersion version = new TemplateVersion();
            version.setTemplateId(id);
            version.setVersionNumber(nextVersion);
            version.setCreatedBy(currentUserId());
            version.setCreatedAt(OffsetDateTime.now());
            version.setBumpType("PUBLISH");
            version.setMajorVersion(registry.getMajorVersion());
            version.setMinorVersion(registry.getMinorVersion());
            versionRepository.save(version);

            TemplateSnapshot snapshot = TemplateSnapshot.builder()
                    .id(version.getId())
                    .templateId(id)
                    .versionNumber(nextVersion)
                    .majorVersion(registry.getMajorVersion())
                    .minorVersion(registry.getMinorVersion())
                    .parameters(template.getParameters())
                    .questionnaire(template.getQuestionnaire())
                    .body(template.getBody())
                    .formatting(template.getFormatting())
                    .scripts(template.getScripts())
                    .phrasings(template.getPhrasings())
                    .createdAt(version.getCreatedAt().toInstant())
                    .build();
            snapshotRepository.save(snapshot);

            log.info("Шаблон id={} опубликован, создан снимок version={} snapshotId={}", id, nextVersion, version.getId());
        }

        registry.setPublished(published);
        registry = registryRepository.save(registry);
        auditService.log(currentLogin(), "TEMPLATE", id, published ? "PUBLISH" : "UNPUBLISH", null);
        return toResponse(registry, template);
    }

    private String buildAutoDescription(UUID templateId, Template current, int newMajor, int newMinor) {
        // Diff with previous snapshot if exists
        int currentVersionNumber = newMajor * 10000 + newMinor;
        int prevVersionNumber = versionRepository.findMaxVersionNumber(templateId);
        if (prevVersionNumber > 0 && prevVersionNumber < currentVersionNumber) {
            try {
                TemplateSnapshot prev = getSnapshot(templateId, prevVersionNumber);
                DiffResult diff = templateDiffService.diff(prev, new TemplateSnapshotSupplier(current));
                return diff.summary();
            } catch (Exception e) {
                log.warn("Не удалось сформировать авто-описание для шаблона {}: {}", templateId, e.getMessage());
            }
        }
        return "Первая версия";
    }

    private TemplateRegistry findRegistryOrThrow(UUID id) {
        return registryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Шаблон не найден: " + id));
    }

    private Template findTemplateOrThrow(UUID id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Тело шаблона не найдено в MongoDB: " + id));
    }

    private TemplateResponse toResponse(TemplateRegistry r, Template t) {
        return new TemplateResponse(
                r.getId(), r.getCode(), r.getName(), r.getDescription(), r.getCategory(),
                r.getVersion(), r.getMajorVersion(), r.getMinorVersion(),
                r.isPublished(), r.getOwnerId(), r.getCreatedAt(), r.getUpdatedAt(),
                r.getDefaultFolderId(),
                t.getParameters(), t.getQuestionnaire(), t.getBody(), t.getFormatting(), t.getScripts(),
                t.getPhrasings()
        );
    }

    private TemplateListItemResponse toListItem(TemplateRegistry r) {
        return new TemplateListItemResponse(
                r.getId(), r.getCode(), r.getName(), r.getDescription(),
                r.getCategory(), r.isPublished(), r.getMajorVersion(), r.getMinorVersion(),
                r.getCreatedAt()
        );
    }

    private TemplateVersionDTO toVersionDTO(TemplateVersion v) {
        int major = v.getMajorVersion() != null ? v.getMajorVersion()
                : (v.getVersionNumber() / 10000 > 0 ? v.getVersionNumber() / 10000 : v.getVersionNumber());
        int minor = v.getMinorVersion() != null ? v.getMinorVersion()
                : (v.getVersionNumber() % 10000);
        return new TemplateVersionDTO(
                v.getId(), v.getTemplateId(), v.getVersionNumber(),
                major, minor,
                v.getCreatedAt(), v.getCreatedBy(),
                v.getComment(), v.getBumpType(), v.getAutoDescription(), v.getRollbackFrom()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public TemplateValidationResult validate(UUID id) {
        Template template = findTemplateOrThrow(id);
        return templateValidator.validate(template);
    }

    @Override
    @Transactional(readOnly = true)
    public ScriptTestResult testScript(UUID templateId, ScriptTestRequest request) {
        Template template = findTemplateOrThrow(templateId);

        List<Script> scripts = template.getScripts();
        if (scripts == null || scripts.isEmpty()) {
            throw new ResourceNotFoundException("Скрипты не найдены в шаблоне: " + templateId);
        }

        Script script = scripts.stream()
                .filter(s -> request.scriptId().equals(s.getId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Скрипт не найден: " + request.scriptId()));

        Map<String, Object> answers = request.mockAnswers() != null
                ? request.mockAnswers() : Map.of();

        long start = System.currentTimeMillis();
        try {
            boolean result = pythonConditionEvaluator.evaluate(script.getCode(), answers);
            long ms = System.currentTimeMillis() - start;
            return new ScriptTestResult(true, result, null, ms);
        } catch (ScriptExecutionException e) {
            long ms = System.currentTimeMillis() - start;
            return new ScriptTestResult(false, null, e.getMessage(), ms);
        }
    }

    private String currentLogin() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    private UUID currentUserId() {
        String login = currentLogin();
        if (login == null) return null;
        try {
            return userService.getCurrentUser(login).id();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Adapter wrapping a live Template to implement TemplateDiffService.ParameterTypeSupplier.
     */
    private record TemplateSnapshotSupplier(Template template) implements ParameterTypeSupplier {
        @Override
        public List<com.docforge.template.model.Parameter> getParameters() { return template.getParameters(); }
        @Override
        public Map<String, Object> getBody() { return template.getBody(); }
        @Override
        public com.docforge.template.model.Questionnaire getQuestionnaire() { return template.getQuestionnaire(); }
        @Override
        public List<Script> getScripts() { return template.getScripts(); }
        @Override
        public List<com.docforge.template.model.Phrasing> getPhrasings() { return template.getPhrasings(); }
    }
}
