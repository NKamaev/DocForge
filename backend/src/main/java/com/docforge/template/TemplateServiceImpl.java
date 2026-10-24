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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateServiceImpl implements TemplateService {

    /** Сколько версий шаблона читать за раз при поиске последней опубликованной. */
    private static final int VERSION_PAGE_SIZE = 100;
    /** Глубина разбора цепочки откатов (ROLLBACK → источник). */
    private static final int ROLLBACK_PROVENANCE_DEPTH = 5;

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
    public TemplateResponse getReadable(UUID id, UUID userId, boolean admin) {
        TemplateRegistry registry = findRegistryOrThrow(id);
        if (!registry.isPublished() && !admin && !isOwner(registry, userId)) {
            log.warn("Попытка чтения чужого черновика шаблона id={} (userId={})", id, userId);
            throw new AccessDeniedException("Нет доступа к шаблону");
        }
        Template template = findTemplateOrThrow(id);
        return toResponse(registry, template);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TemplateListItemResponse> list(int page, int size, String category, Boolean isPublished,
                                               OffsetDateTime createdAfter) {
        Specification<TemplateRegistry> spec = buildSpec(category, isPublished, createdAfter, null, true);
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return registryRepository.findAll(spec, pageRequest).map(this::toListItem);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TemplateListItemResponse> listVisible(int page, int size, String category, Boolean isPublished,
                                                       OffsetDateTime createdAfter, UUID userId, boolean admin) {
        Specification<TemplateRegistry> spec =
                buildSpec(category, isPublished, createdAfter, userId, admin);
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return registryRepository.findAll(spec, pageRequest).map(this::toListItem);
    }

    /**
     * Спецификация выборки шаблонов.
     *
     * <p>Для не-администратора добавляется ограничение видимости (BUG-059):
     * опубликованные шаблоны + собственные черновики. Чужие черновики
     * отфильтровываются на уровне запроса, поэтому в пагинацию не попадают.
     */
    private Specification<TemplateRegistry> buildSpec(String category, Boolean isPublished,
                                                      OffsetDateTime createdAfter, UUID userId, boolean admin) {
        Specification<TemplateRegistry> spec = Specification.where(null);

        if (category != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("category"), category));
        }
        if (isPublished != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("published"), isPublished));
        }
        if (createdAfter != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), createdAfter));
        }
        if (!admin) {
            if (userId == null) {
                spec = spec.and((root, query, cb) -> cb.isTrue(root.get("published")));
            } else {
                spec = spec.and((root, query, cb) -> cb.or(
                        cb.isTrue(root.get("published")),
                        cb.equal(root.get("ownerId"), userId)));
            }
        }

        return spec;
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

        // Determine new version numbers (единый счётчик со всеми писателями)
        NextVersion next = allocateVersion(registry, templateId, request.bumpType());
        int newMajor = next.major();
        int newMinor = next.minor();
        int newVersionNumber = next.versionNumber();

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

        // Откат опубликованного шаблона на черновик сделал бы живой шаблон
        // (который виден всем, пока published=true) черновым содержимым.
        if (registry.isPublished() && !isPublishedVersion(templateId, versionNumber)) {
            throw new BusinessException(
                    "Нельзя откатить опубликованный шаблон к черновой версии: version=" + versionNumber);
        }

        // Restore template content from snapshot
        template.setParameters(snapshot.getParameters());
        template.setQuestionnaire(snapshot.getQuestionnaire());
        template.setBody(snapshot.getBody());
        template.setFormatting(snapshot.getFormatting());
        template.setScripts(snapshot.getScripts());
        template.setPhrasings(snapshot.getPhrasings());
        template = templateRepository.save(template);

        // Create a new version as rollback
        NextVersion next = allocateVersion(registry, templateId, "ROLLBACK");
        int newMajor = next.major();
        int newMinor = next.minor();
        int newVersionNumber = next.versionNumber();

        registry.setMajorVersion(newMajor);
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
    @Transactional(readOnly = true)
    public List<TemplateVersionDTO> listVersionsVisible(UUID templateId, UUID userId, boolean admin) {
        TemplateRegistry registry = findRegistryOrThrow(templateId);
        List<TemplateVersion> versions = versionRepository.findByTemplateIdOrderByVersionNumberDesc(templateId);
        if (admin || isOwner(registry, userId)) {
            return versions.stream().map(this::toVersionDTO).toList();
        }
        // Не-владелец видит только опубликованные версии: черновики
        // saveVersion в список не попадают вообще (BUG-059).
        // Происхождение откатов считается по уже загруженной истории версий,
        // без дополнительных запросов на каждый ROLLBACK.
        Map<Integer, TemplateVersion> byNumber = new HashMap<>();
        for (TemplateVersion version : versions) {
            byNumber.putIfAbsent(version.getVersionNumber(), version);
        }
        Map<Integer, Boolean> publishedMemo = new HashMap<>();
        return versions.stream()
                .filter(version -> isPublished(version, templateId, 0, byNumber, publishedMemo))
                .map(this::toVersionDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TemplateSnapshot getSnapshotVisible(UUID templateId, int versionNumber, UUID userId, boolean admin) {
        TemplateRegistry registry = findRegistryOrThrow(templateId);
        if (admin || isOwner(registry, userId)) {
            return getSnapshot(templateId, versionNumber);
        }

        if (!versionExists(templateId, versionNumber)) {
            throw new ResourceNotFoundException(
                    "Снимок шаблона не найден: templateId=" + templateId + " version=" + versionNumber);
        }

        requireVersionVisible(templateId, versionNumber, userId);
        return getSnapshot(templateId, versionNumber);
    }

    @Override
    @Transactional(readOnly = true)
    public DiffResult getDiffVisible(UUID templateId, int fromVersion, int toVersion,
                                     UUID userId, boolean admin) {
        TemplateRegistry registry = findRegistryOrThrow(templateId);
        if (admin || isOwner(registry, userId)) {
            return getDiff(templateId, fromVersion, toVersion);
        }
        // Diff раскрывает содержимое снимков — обе версии должны быть
        // читаемы вызывающим (BUG-059).
        if (!versionExists(templateId, fromVersion) || !versionExists(templateId, toVersion)) {
            throw new ResourceNotFoundException("Снимок шаблона не найден: templateId=" + templateId);
        }
        requireVersionVisible(templateId, fromVersion, userId);
        requireVersionVisible(templateId, toVersion, userId);
        return getDiff(templateId, fromVersion, toVersion);
    }

    /**
     * Существует ли версия шаблона (запись в {@code template_version} или
     * легаси-снимок в MongoDB).
     */
    private boolean versionExists(UUID templateId, int versionNumber) {
        if (versionRepository.findByTemplateIdAndVersionNumber(templateId, versionNumber).isPresent()) {
            return true;
        }
        return snapshotRepository
                .findFirstByTemplateIdAndVersionNumberOrderByCreatedAtDesc(templateId, versionNumber)
                .isPresent();
    }

    /**
     * Опубликована ли версия для не-владельца.
     *
     * <p>Легаси-снимок без записи в {@code template_version} создан публикацией
     * (см. V008) — считаем его опубликованным.
     */
    private boolean isPublishedVersion(UUID templateId, int versionNumber) {
        Optional<TemplateVersion> version =
                versionRepository.findByTemplateIdAndVersionNumber(templateId, versionNumber);
        if (version.isPresent()) {
            return isPublished(version.get(), templateId, 0);
        }
        return snapshotRepository
                .findFirstByTemplateIdAndVersionNumberOrderByCreatedAtDesc(templateId, versionNumber)
                .isPresent();
    }

    /**
     * Проверка доступа к версии для не-владельца: опубликованная версия
     * доступна всем, черновик — только соавтору документа, привязанного к этой
     * версии (BUG-059). Иначе — 403.
     */
    private void requireVersionVisible(UUID templateId, int versionNumber, UUID userId) {
        if (isPublishedVersion(templateId, versionNumber)) {
            return;
        }
        if (documentService.hasAccessibleDocumentForTemplateVersion(templateId, versionNumber, userId)) {
            return;
        }
        log.warn("Попытка чтения черновой версии {} шаблона {} (userId={})",
                versionNumber, templateId, userId);
        throw new AccessDeniedException("Нет доступа к версии шаблона");
    }

    @Override
    public TemplateSnapshot getSnapshot(UUID templateId, int versionNumber) {
        findRegistryOrThrow(templateId);
        // Резолвим так же, как при инстанцировании документа (по id версии с
        // откатом на поиск по номеру), чтобы «пин» документа и чтение снимка
        // версии всегда указывали на один и тот же снимок.
        Optional<TemplateVersion> version =
                versionRepository.findByTemplateIdAndVersionNumber(templateId, versionNumber);
        if (version.isPresent()) {
            return findSnapshotOf(version.get(), templateId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Снимок шаблона не найден: templateId=" + templateId
                                    + " version=" + versionNumber));
        }
        return snapshotRepository.findFirstByTemplateIdAndVersionNumberOrderByCreatedAtDesc(
                        templateId, versionNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Снимок шаблона не найден: templateId=" + templateId
                                + " version=" + versionNumber));
    }

    @Override
    public Optional<TemplateSnapshot> findLatestSnapshot(UUID templateId) {
        return versionRepository.findByTemplateIdOrderByVersionNumberDesc(templateId)
                .stream()
                .findFirst()
                .flatMap(v -> findSnapshotOf(v, templateId));
    }

    @Override
    public Optional<TemplateSnapshot> findLatestPublishedSnapshot(UUID templateId) {
        int page = 0;
        while (true) {
            List<TemplateVersion> versions = versionRepository.findByTemplateIdOrderByVersionNumberDesc(
                    templateId, PageRequest.of(page, VERSION_PAGE_SIZE));
            if (versions.isEmpty()) {
                return Optional.empty();
            }
            for (TemplateVersion version : versions) {
                if (!isPublished(version, templateId, 0)) {
                    continue;
                }
                Optional<TemplateSnapshot> snapshot = findSnapshotOf(version, templateId);
                if (snapshot.isPresent()) {
                    return snapshot;
                }
                // Снимок версии удалён — продолжаем искать более старую
                // опубликованную версию со снимком.
                log.warn("Снимок опубликованной версии {} шаблона {} не найден, ищем более старый",
                        version.getVersionNumber(), templateId);
            }
            if (versions.size() < VERSION_PAGE_SIZE) {
                return Optional.empty();
            }
            page++;
        }
    }

    @Override
    public VersionAccess resolveVersionAccess(UUID templateId, int versionNumber) {
        Optional<TemplateVersion> version =
                versionRepository.findByTemplateIdAndVersionNumber(templateId, versionNumber);
        if (version.isEmpty()) {
            // Легаси-снимок без записи в template_version создан публикацией
            // (см. V008) — он существует и опубликован. Семантика совпадает
            // с isPublishedVersion(...), чтобы правила не расходились.
            Optional<TemplateSnapshot> legacy = snapshotRepository
                    .findFirstByTemplateIdAndVersionNumberOrderByCreatedAtDesc(templateId, versionNumber);
            return legacy
                    .map(snapshot -> new VersionAccess(true, true, snapshot))
                    .orElseGet(() -> new VersionAccess(false, false, null));
        }
        TemplateVersion v = version.get();
        boolean published = isPublished(v, templateId, 0);
        TemplateSnapshot snapshot = findSnapshotOf(v, templateId).orElse(null);
        return new VersionAccess(true, published, snapshot);
    }

    @Override
    public boolean hasVersions(UUID templateId) {
        return versionRepository.findMaxVersionNumber(templateId) > 0;
    }

    /**
     * Снимок версии: по id версии из PostgreSQL
     * ({@code template_snapshots._id == template_version.id}), с откатом на
     * легаси-поиск по номеру версии — исторические снимки могли сохраниться
     * с другим {@code _id} (см. BUG-053).
     */
    private Optional<TemplateSnapshot> findSnapshotOf(TemplateVersion version, UUID templateId) {
        Optional<TemplateSnapshot> byId = snapshotRepository.findById(version.getId());
        if (byId.isPresent()) {
            return byId;
        }
        Optional<TemplateSnapshot> legacy = snapshotRepository
                .findFirstByTemplateIdAndVersionNumberOrderByCreatedAtDesc(
                        templateId, version.getVersionNumber());
        legacy.ifPresent(snapshot -> log.warn(
                "Снимок версии {} шаблона {} найден только по номеру версии (id={} не совпал)",
                version.getVersionNumber(), templateId, version.getId()));
        return legacy;
    }

    /**
     * Опубликована ли версия (её можно инстанцировать не-владельцу).
     *
     * <ul>
     *   <li>{@code PUBLISH} — опубликована;</li>
     *   <li>{@code null} (легаси до {@code bump_type}) — опубликована: до
     *       V015 единственным писателем {@code template_version} была
     *       публикация (см. комментарий к V008);</li>
     *   <li>{@code ROLLBACK} — опубликована только если откат сделан с
     *       опубликованной версии ({@code rollback_from});</li>
     *   <li>{@code MAJOR}/{@code MINOR} — черновик {@code saveVersion},
     *       недоступен не-владельцу.</li>
     * </ul>
     */
    private boolean isPublished(TemplateVersion version, UUID templateId, int depth) {
        return isPublished(version, templateId, depth, Map.of(), new HashMap<>());
    }

    /**
     * @param byNumber   все версии шаблона по номеру (чтобы не ходить в БД за
     *                   каждым источником отката)
     * @param publishedMemo кэш вычисленных публикаций по номеру версии
     */
    private boolean isPublished(TemplateVersion version, UUID templateId, int depth,
                                Map<Integer, TemplateVersion> byNumber,
                                Map<Integer, Boolean> publishedMemo) {
        String bumpType = version.getBumpType();
        if (bumpType == null || "PUBLISH".equals(bumpType)) {
            return true;
        }
        if ("ROLLBACK".equals(bumpType)) {
            Integer source = version.getRollbackFrom();
            if (source == null || depth >= ROLLBACK_PROVENANCE_DEPTH) {
                return false;
            }
            Boolean cached = publishedMemo.get(source);
            if (cached != null) {
                return cached;
            }
            TemplateVersion src = byNumber.get(source);
            if (src == null) {
                src = versionRepository.findByTemplateIdAndVersionNumber(templateId, source)
                        .orElse(null);
            }
            boolean result = src != null && isPublished(src, templateId, depth + 1, byNumber, publishedMemo);
            publishedMemo.put(source, result);
            return result;
        }
        return false;
    }

    @Override
    public TemplateSnapshot getSnapshotById(UUID snapshotId) {
        return snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new ResourceNotFoundException("Снимок шаблона не найден: " + snapshotId));
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private record NextVersion(int major, int minor, int versionNumber) {}

    /**
     * Единый аллокатор семантических версий ({@code major*10000 + minor}).
     *
     * <p>Все версии (PUBLISH/MINOR/MAJOR/ROLLBACK) пишут в один счётчик
     * {@code template_version.version_number}, поэтому публикация и откат
     * расходуют minor так же, как {@link #saveVersion}. Значения
     * {@code max(pgMax, mongoMax)} используются только как floor монотонности:
     * если в базе остались «осиротевшие» снимки с большим номером, следующий
     * номер поднимается выше них.
     *
     * @param bumpType {@code MAJOR} поднимает major и сбрасывает minor в 0,
     *                 остальные значения (MINOR/PUBLISH/ROLLBACK) растят minor
     */
    private NextVersion allocateVersion(TemplateRegistry registry, UUID templateId, String bumpType) {
        int pgMax = versionRepository.findMaxVersionNumber(templateId);
        int mongoMax = snapshotRepository.findFirstByTemplateIdOrderByVersionNumberDesc(templateId)
                .map(TemplateSnapshot::getVersionNumber)
                .orElse(0);
        int maxExisting = Math.max(pgMax, mongoMax);
        boolean majorBump = "MAJOR".equals(bumpType);

        int major = Math.max(registry.getMajorVersion(), 1);
        int minor;
        if (majorBump) {
            major++;
            minor = 0;
        } else {
            minor = registry.getMinorVersion() + 1;
        }
        if (minor > 9999) {
            major++;
            minor = 0;
        }
        int versionNumber = major * 10000 + minor;

        if (versionNumber <= maxExisting) {
            if (majorBump) {
                major = Math.max(maxExisting / 10000, 1) + 1;
                minor = 0;
            } else {
                major = Math.max(maxExisting / 10000, 1);
                minor = maxExisting % 10000 + 1;
                if (minor > 9999) {
                    major++;
                    minor = 0;
                }
            }
            versionNumber = major * 10000 + minor;
        }
        return new NextVersion(major, minor, versionNumber);
    }

    private TemplateResponse setPublished(UUID id, boolean published) {
        TemplateRegistry registry = findRegistryOrThrow(id);
        Template template = findTemplateOrThrow(id);

        if (published) {
            NextVersion next = allocateVersion(registry, id, "PUBLISH");
            int newMajor = next.major();
            int newMinor = next.minor();
            int nextVersion = next.versionNumber();
            registry.setVersion(nextVersion);
            registry.setMajorVersion(newMajor);
            registry.setMinorVersion(newMinor);

            TemplateVersion version = new TemplateVersion();
            version.setTemplateId(id);
            version.setVersionNumber(nextVersion);
            version.setCreatedBy(currentUserId());
            version.setCreatedAt(OffsetDateTime.now());
            version.setBumpType("PUBLISH");
            version.setMajorVersion(newMajor);
            version.setMinorVersion(newMinor);
            versionRepository.save(version);

            TemplateSnapshot snapshot = TemplateSnapshot.builder()
                    .id(version.getId())
                    .templateId(id)
                    .versionNumber(nextVersion)
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

    /** Владелец шаблона (владельцем может быть и сам пользователь). */
    private boolean isOwner(TemplateRegistry registry, UUID userId) {
        return userId != null && userId.equals(registry.getOwnerId());
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
        // Легаси-счётчики (< 10000) не семантические: V015 трактует их как vN.0.
        boolean semver = v.getVersionNumber() >= 10000;
        int major = v.getMajorVersion() != null ? v.getMajorVersion()
                : (semver ? v.getVersionNumber() / 10000 : v.getVersionNumber());
        int minor = v.getMinorVersion() != null ? v.getMinorVersion()
                : (semver ? v.getVersionNumber() % 10000 : 0);
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
