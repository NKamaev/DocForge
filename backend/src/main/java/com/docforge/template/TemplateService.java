package com.docforge.template;

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
import com.docforge.template.model.TemplateSnapshot;
import org.springframework.data.domain.Page;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TemplateService {

    TemplateResponse create(UUID ownerId, CreateTemplateRequest request);

    TemplateResponse getById(UUID id);

    Page<TemplateListItemResponse> list(int page, int size, String category, Boolean isPublished,
                                        OffsetDateTime createdAfter);

    TemplateResponse update(UUID id, UpdateTemplateRequest request);

    void delete(UUID id);

    TemplateResponse publish(UUID id);

    TemplateResponse unpublish(UUID id);

    TemplateValidationResult validate(UUID id);

    ScriptTestResult testScript(UUID templateId, ScriptTestRequest request);

    // ── Versioning ────────────────────────────────────────────────────────────

    List<TemplateVersionDTO> listVersions(UUID templateId);

    TemplateSnapshot getSnapshot(UUID templateId, int versionNumber);

    Optional<TemplateSnapshot> findLatestSnapshot(UUID templateId);

    /**
     * Последняя версия, которую можно инстанцировать не-владельцу шаблона:
     * черновые версии ({@code MAJOR}/{@code MINOR}) исключены.
     */
    Optional<TemplateSnapshot> findLatestPublishedSnapshot(UUID templateId);

    /**
     * Результат проверки доступа к версии шаблона при инстанцировании документа.
     *
     * @param exists    существует ли версия
     * @param published опубликована ли версия (черновик {@code saveVersion} — нет)
     * @param snapshot  снимок версии (может отсутствовать, если снимок удалён)
     */
    record VersionAccess(boolean exists, boolean published, TemplateSnapshot snapshot) {}

    /**
     * Проверка доступа к конкретной версии шаблона: существование, признак
     * опубликованности и снимок версии.
     *
     * <p>Опубликованной считается версия с {@code bump_type} {@code PUBLISH},
     * легаси-запись без {@code bump_type} (создана до появления saveVersion) и
     * {@code ROLLBACK}, сделанный с опубликованной версии.
     */
    VersionAccess resolveVersionAccess(UUID templateId, int versionNumber);

    /**
     * Есть ли у шаблона записи версий. Пусто — значит шаблон опубликован до
     * появления версионирования, и его снимка в базе нет.
     */
    boolean hasVersions(UUID templateId);

    TemplateSnapshot getSnapshotById(UUID snapshotId);

    /** Save a draft version (creates version + snapshot without publishing). */
    SaveVersionResponse saveVersion(UUID templateId, SaveVersionRequest request);

    /** Rollback template to a previous version (creates a new version). */
    RollbackResponse rollback(UUID templateId, int versionNumber);

    /** Generate diff between two snapshots. */
    DiffResult getDiff(UUID templateId, int fromVersion, int toVersion);
}
