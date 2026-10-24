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

    /**
     * Чтение шаблона с проверкой прав (BUG-059).
     *
     * <p>Опубликованный шаблон доступен любому аутентифицированному
     * пользователю (поведение каталога). Черновик (неопубликованный шаблон)
     * доступен только владельцу и администратору, иначе — 403.
     *
     * @param id     шаблон
     * @param userId вызывающий пользователь
     * @param admin  является ли вызывающий администратором
     */
    TemplateResponse getReadable(UUID id, UUID userId, boolean admin);

    Page<TemplateListItemResponse> list(int page, int size, String category, Boolean isPublished,
                                        OffsetDateTime createdAfter);

    /**
     * Список шаблонов, видимых вызывающему (BUG-059).
     *
     * <p>Не-администратор видит опубликованные шаблоны и собственные
     * черновики; чужие черновики не показываются. Администратор видит всё.
     *
     * @param userId вызывающий пользователь
     * @param admin  является ли вызывающий администратором
     */
    Page<TemplateListItemResponse> listVisible(int page, int size, String category, Boolean isPublished,
                                                OffsetDateTime createdAfter, UUID userId, boolean admin);

    TemplateResponse update(UUID id, UpdateTemplateRequest request);

    void delete(UUID id);

    TemplateResponse publish(UUID id);

    TemplateResponse unpublish(UUID id);

    TemplateValidationResult validate(UUID id);

    ScriptTestResult testScript(UUID templateId, ScriptTestRequest request);

    // ── Versioning ────────────────────────────────────────────────────────────

    List<TemplateVersionDTO> listVersions(UUID templateId);

    /**
     * Список версий шаблона с проверкой прав (BUG-059).
     *
     * <p>Владелец и администратор видят все версии, остальные — только
     * опубликованные (черновики {@code saveVersion} в список не попадают).
     */
    List<TemplateVersionDTO> listVersionsVisible(UUID templateId, UUID userId, boolean admin);

    TemplateSnapshot getSnapshot(UUID templateId, int versionNumber);

    /**
     * Чтение снимка версии шаблона с проверкой прав (BUG-059).
     *
     * <p>Владелец и администратор видят любую версию, остальные — только
     * опубликованные. Исключение: версию, к которой привязан документ,
     * доступный вызывающему, прочитать можно (сотрудничество над документом).
     * Иначе — 403 «Нет доступа к версии шаблона».
     */
    TemplateSnapshot getSnapshotVisible(UUID templateId, int versionNumber, UUID userId, boolean admin);

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

    /**
     * Diff двух версий с проверкой прав (BUG-059).
     *
     * <p>Diff раскрывает содержимое снимков, поэтому обе версии должны быть
     * читаемы вызывающим: владелец и администратор — любые, остальные —
     * опубликованные (или версии, к которой привязан доступный им документ).
     */
    DiffResult getDiffVisible(UUID templateId, int fromVersion, int toVersion,
                              UUID userId, boolean admin);
}
