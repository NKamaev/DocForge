package com.docforge.document;

import com.docforge.document.dto.*;
import com.docforge.document.model.DocumentRevisionBody;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface DocumentService {

    /** Создать документ из шаблона. Возвращает пустой документ с опросным листом. */
    DocumentResponse create(UUID templateId, String name, UUID ownerId, UUID folderId, Integer templateVersionNumber);

    DocumentResponse getById(UUID id);

    Page<DocumentListItemResponse> list(UUID ownerId, int page, int size);

    void delete(UUID id);

    boolean existsByTemplateId(UUID templateId);

    /** Опросный лист с текущими ответами и вычисленной видимостью вопросов. */
    QuestionnaireResponse getQuestionnaire(UUID documentId);

    /**
     * Сохраняет ответы (merge с существующими), пересчитывает видимость.
     * Возвращает обновлённый опросный лист.
     */
    SaveAnswersResponse saveAnswers(UUID documentId, Map<String, Object> answers);

    /** Запускает pipeline генерации. Сохраняет generatedBody и currentBody. */
    GenerateResponse generate(UUID documentId);

    /** Сохраняет отредактированное тело документа (currentBody). */
    void updateBody(UUID documentId, Object currentBody);

    /** Перемещает документ в папку. folderId = null — убрать из папки. */
    void moveToFolder(UUID documentId, UUID folderId);

    // ── Collaboration ─────────────────────────────────────────────────────────

    /** Загружает сохранённое Yjs-состояние из MongoDB. Null если не инициализировано. */
    byte[] getYjsState(UUID documentId);

    /** Сохраняет Yjs-состояние в MongoDB (вызывается из YjsDocumentStore). */
    void saveYjsState(UUID documentId, byte[] state);

    // ── Participants ──────────────────────────────────────────────────────────

    List<DocumentParticipantDTO> getParticipants(UUID documentId);

    DocumentParticipantDTO addParticipant(UUID documentId, UUID userId, String role, UUID grantedBy);

    void removeParticipant(UUID documentId, UUID userId, UUID revokedBy);

    // ── Editions ──────────────────────────────────────────────────────────────

    /** Создаёт редакцию из текущего состояния документа. */
    DocumentRevisionDTO saveRevision(UUID documentId, String comment);

    List<DocumentRevisionDTO> listRevisions(UUID documentId);

    DocumentRevisionBody getRevisionBody(UUID documentId, int revisionNumber);

    /** Откатывает документ к указанной редакции (создаёт новую редакцию). */
    DocumentRevisionDTO restoreRevision(UUID documentId, int revisionNumber);

    /** Вычисляет diff между редакцией и предыдущей/текущей версией. */
    RevisionDiffResponse computeRevisionDiff(UUID documentId, int revisionNumber, Integer compareToRevisionNumber);
}
