package com.docforge.document;

import com.docforge.audit.AuditService;
import com.docforge.common.exception.ResourceNotFoundException;
import com.docforge.document.dto.DocumentResponse;
import com.docforge.document.dto.QuestionnaireResponse;
import com.docforge.document.dto.SaveAnswersResponse;
import com.docforge.document.generator.ConditionEvaluator;
import com.docforge.document.generator.PythonConditionEvaluator;
import com.docforge.document.model.Document;
import com.docforge.document.model.DocumentRegistry;
import com.docforge.template.TemplateService;
import com.docforge.template.dto.TemplateResponse;
import com.docforge.template.model.Condition;
import com.docforge.template.model.ConditionType;
import com.docforge.template.model.Parameter;
import com.docforge.template.model.ParameterType;
import com.docforge.template.model.Question;
import com.docforge.template.model.Questionnaire;
import com.docforge.template.model.QuestionnairePage;
import com.docforge.template.model.TemplateSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression tests for BUG-047 — "Опросный лист пуст при наличии вопросов в шаблоне".
 *
 * <p>Root cause: {@code getQuestionnaire()} and {@code saveAnswers()} used to read the
 * questionnaire from the <b>live</b> template while {@code generate()} read it from the
 * document's immutable <b>snapshot</b>. Once the template was edited after the document
 * was created (e.g. questionnaire nulled/cleared), the document's questionnaire collapsed
 * to empty even though the snapshot still held the questions.</p>
 *
 * <p>These tests pin the fixed behaviour: the questionnaire is resolved from the document's
 * snapshot when one is linked (even when the snapshot is <em>less</em> populated than the
 * live template — "inverse skew"), falling back to the live template only when the snapshot
 * is missing (deleted) — any other failure propagates.</p>
 */
@ExtendWith(MockitoExtension.class)
class DocumentServiceQuestionnaireTest {

    private static final UUID DOC_ID = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
    private static final UUID TEMPLATE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000cc");
    private static final UUID SNAPSHOT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000dd");
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000ee");

    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentRegistryRepository registryRepository;
    @Mock private TemplateService templateService;
    @Mock private AuditService auditService;
    @Mock private ConditionEvaluator conditionEvaluator;
    @Mock private PythonConditionEvaluator pythonEvaluator;

    @InjectMocks
    private DocumentServiceImpl service;

    // ── 1. core BUG-047 regression ────────────────────────────────────────────

    @Test
    @DisplayName("getQuestionnaire() reads from snapshot when live template questionnaire is gone (BUG-047)")
    void getQuestionnaire_snapshotHasQuestionnaire_liveEmpty_returnsSnapshotQuestionnaire() {
        // given — document pinned to a snapshot; snapshot keeps the questions, live template lost them
        Document doc = documentWithSnapshot();
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(doc));
        when(registryRepository.findById(DOC_ID)).thenReturn(Optional.of(registry()));
        when(templateService.getSnapshotById(SNAPSHOT_ID)).thenReturn(snapshotWithQuestionnaire());

        // when
        QuestionnaireResponse response = service.getQuestionnaire(DOC_ID);

        // then — the snapshot's questionnaire wins, not the (now empty) live one
        assertThat(response.title()).isEqualTo("Параметры договора");
        assertThat(response.pages()).hasSize(1);
        assertThat(response.pages().get(0).title()).isEqualTo("Стороны договора");
        assertThat(response.pages().get(0).questions())
                .extracting(QuestionnaireResponse.QuestionItem::id)
                .containsExactly("q1", "q2");

        // then — snapshot path is used, live template is never consulted
        verify(templateService).getSnapshotById(SNAPSHOT_ID);
        verify(templateService, never()).getById(any());
    }

    // ── 2. no snapshot → live fallback ────────────────────────────────────────

    @Test
    @DisplayName("getQuestionnaire() without snapshot falls back to the live template questionnaire")
    void getQuestionnaire_noSnapshot_fallsBackToLiveTemplate() {
        // given — legacy document without a pinned snapshot
        Document doc = new Document();
        doc.setId(DOC_ID);
        doc.setTemplateId(TEMPLATE_ID);
        doc.setTemplateSnapshotId(null);
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(doc));
        when(registryRepository.findById(DOC_ID)).thenReturn(Optional.of(registry()));
        when(templateService.getById(TEMPLATE_ID)).thenReturn(liveTemplate(questionnaire()));

        // when
        QuestionnaireResponse response = service.getQuestionnaire(DOC_ID);

        // then
        assertThat(response.title()).isEqualTo("Параметры договора");
        assertThat(response.pages().get(0).questions())
                .extracting(QuestionnaireResponse.QuestionItem::id)
                .containsExactly("q1", "q2");
        verify(templateService).getById(TEMPLATE_ID);
        verify(templateService, never()).getSnapshotById(any());
    }

    // ── 3. neither source has a questionnaire ─────────────────────────────────

    @Test
    @DisplayName("getQuestionnaire() returns empty pages when no questionnaire exists anywhere")
    void getQuestionnaire_noQuestionnaireAnywhere_returnsEmptyPages() {
        // given — live template has no questionnaire at all
        Document doc = new Document();
        doc.setId(DOC_ID);
        doc.setTemplateId(TEMPLATE_ID);
        doc.setTemplateSnapshotId(null);
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(doc));
        when(registryRepository.findById(DOC_ID)).thenReturn(Optional.of(registry()));
        when(templateService.getById(TEMPLATE_ID)).thenReturn(liveTemplate(null));

        // when
        QuestionnaireResponse response = service.getQuestionnaire(DOC_ID);

        // then — explicit empty state (not an error), title null
        assertThat(response.title()).isNull();
        assertThat(response.pages()).isEmpty();
    }

    // ── 4. deleted snapshot → graceful fallback ───────────────────────────────

    @Test
    @DisplayName("getQuestionnaire() falls back to the live template when the snapshot is not found")
    void getQuestionnaire_snapshotNotFound_fallsBackToLiveTemplate() {
        // given — snapshot was deleted (`ResourceNotFoundException` is the only swallowed failure)
        Document doc = documentWithSnapshot();
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(doc));
        when(registryRepository.findById(DOC_ID)).thenReturn(Optional.of(registry()));
        when(templateService.getSnapshotById(SNAPSHOT_ID))
                .thenThrow(new ResourceNotFoundException("snapshot deleted"));
        when(templateService.getById(TEMPLATE_ID)).thenReturn(liveTemplate(questionnaire()));

        // when
        QuestionnaireResponse response = service.getQuestionnaire(DOC_ID);

        // then — no exception propagates; live template data is returned
        assertThat(response.title()).isEqualTo("Параметры договора");
        assertThat(response.pages().get(0).questions()).hasSize(2);
        verify(templateService).getSnapshotById(SNAPSHOT_ID);
        verify(templateService).getById(TEMPLATE_ID);
    }

    // ── 5. non-404 snapshot failure propagates ────────────────────────────────

    @Test
    @DisplayName("getQuestionnaire() propagates a non-404 error raised while loading the snapshot")
    void getQuestionnaire_snapshotLoadFailsUnexpectedly_propagates() {
        // given — snapshot lookup fails with an unexpected (non-404) error
        Document doc = documentWithSnapshot();
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(doc));
        when(registryRepository.findById(DOC_ID)).thenReturn(Optional.of(registry()));
        when(templateService.getSnapshotById(SNAPSHOT_ID))
                .thenThrow(new IllegalStateException("database unavailable"));

        // when / then — the failure is not silently masked by a live-template fallback
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> service.getQuestionnaire(DOC_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("database unavailable");
        verify(templateService, never()).getById(any());
    }

    // ── 6. inverse skew: pin to snapshot even when it is emptier ──────────────

    @Test
    @DisplayName("getQuestionnaire() returns the snapshot's empty questionnaire, not richer live questions")
    void getQuestionnaire_snapshotQuestionnaireEmpty_liveHasQuestions_returnsEmptyFromSnapshot() {
        // given — inverse skew: the snapshot has NO questionnaire, the live template HAS one
        Document doc = documentWithSnapshot();
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(doc));
        when(registryRepository.findById(DOC_ID)).thenReturn(Optional.of(registry()));
        when(templateService.getSnapshotById(SNAPSHOT_ID)).thenReturn(snapshotWithoutQuestionnaire());
        // live template deliberately has questions — must NOT be used
        lenient().when(templateService.getById(TEMPLATE_ID)).thenReturn(liveTemplate(questionnaire()));

        // when
        QuestionnaireResponse response = service.getQuestionnaire(DOC_ID);

        // then — pinned to the snapshot: empty pages, not the live questions
        assertThat(response.pages()).isEmpty();
        assertThat(response.title()).isNull();
        verify(templateService).getSnapshotById(SNAPSHOT_ID);
        verify(templateService, never()).getById(any());
    }

    // ── 7. saveAnswers also resolves from the snapshot ────────────────────────

    @Test
    @DisplayName("saveAnswers() merges answers and returns the snapshot's questionnaire")
    void saveAnswers_mergesAnswers_returnsSnapshotQuestionnaire() {
        // given — document already has one answer, snapshot questionnaire is intact
        Document doc = documentWithSnapshot();
        Map<String, Object> existing = new HashMap<>();
        existing.put("client_name", "Иванов Иван");
        doc.setAnswers(existing);
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(doc));
        when(registryRepository.findById(DOC_ID)).thenReturn(Optional.of(registry()));
        when(templateService.getSnapshotById(SNAPSHOT_ID)).thenReturn(snapshotWithQuestionnaire());

        Map<String, Object> newAnswers = new HashMap<>();
        newAnswers.put("has_penalty", true);

        // when
        SaveAnswersResponse response = service.saveAnswers(DOC_ID, newAnswers);

        // then — answers merged
        assertThat(response.answers())
                .containsEntry("client_name", "Иванов Иван")
                .containsEntry("has_penalty", true);

        // then — questionnaire comes from the snapshot, not the live template
        assertThat(response.questionnaire().title()).isEqualTo("Параметры договора");
        assertThat(response.questionnaire().pages().get(0).questions()).hasSize(2);
        verify(templateService).getSnapshotById(SNAPSHOT_ID);
        verify(templateService, never()).getById(any());
    }

    // ── 8. create() builds the initial questionnaire from the snapshot ────────

    @Test
    @DisplayName("create() builds the initial questionnaire from the resolved snapshot, not the live template")
    void create_resolvesSnapshot_buildsQuestionnaireFromSnapshot() {
        // given — live template has NO questionnaire, the latest snapshot HAS one
        when(templateService.getById(TEMPLATE_ID)).thenReturn(liveTemplate(null));
        when(templateService.findLatestSnapshot(TEMPLATE_ID))
                .thenReturn(Optional.of(snapshotWithQuestionnaire()));
        when(registryRepository.save(any(DocumentRegistry.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(documentRepository.save(any(Document.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // when
        DocumentResponse response = service.create(TEMPLATE_ID, "Новый документ", OWNER_ID, null, null);

        // then — the created document is pinned to the snapshot …
        assertThat(response.templateSnapshotId()).isEqualTo(SNAPSHOT_ID);
        assertThat(response.templateVersionNumber()).isEqualTo(3);

        // … and its initial questionnaire is the snapshot's one, not the (empty) live one
        assertThat(response.questionnaire()).isNotNull();
        assertThat(response.questionnaire().title()).isEqualTo("Параметры договора");
        assertThat(response.questionnaire().pages().get(0).questions())
                .extracting(QuestionnaireResponse.QuestionItem::id)
                .containsExactly("q1", "q2");

        verify(templateService).findLatestSnapshot(TEMPLATE_ID);
        // create() pins the snapshot object returned by findLatestSnapshot; it never re-loads it by id
        verify(templateService, never()).getSnapshotById(any());
    }

    // ── 9. hidden questions are returned, not filtered out ────────────────────

    @Test
    @DisplayName("getQuestionnaire() keeps questions hidden by their condition, flagging visible=false")
    void getQuestionnaire_hiddenQuestion_stillReturnedWithVisibleFalse() {
        // given — q2 is gated by "has_penalty == true" and no answer is set,
        //         so the evaluator returns false (mock default)
        Document doc = documentWithSnapshot();
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(doc));
        when(registryRepository.findById(DOC_ID)).thenReturn(Optional.of(registry()));
        when(templateService.getSnapshotById(SNAPSHOT_ID)).thenReturn(snapshotWithQuestionnaire());

        // when
        QuestionnaireResponse response = service.getQuestionnaire(DOC_ID);

        // then — the question is present on the backend, only its visibility flag is false
        List<QuestionnaireResponse.QuestionItem> questions = response.pages().get(0).questions();
        assertThat(questions).hasSize(2);

        QuestionnaireResponse.QuestionItem hidden = questions.stream()
                .filter(q -> "q2".equals(q.id()))
                .findFirst()
                .orElseThrow();
        assertThat(hidden.visible()).isFalse();
        assertThat(hidden.parameterCode()).isEqualTo("penalty_percent");

        QuestionnaireResponse.QuestionItem alwaysVisible = questions.stream()
                .filter(q -> "q1".equals(q.id()))
                .findFirst()
                .orElseThrow();
        assertThat(alwaysVisible.visible()).isTrue();
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

    private static Document documentWithSnapshot() {
        Document doc = new Document();
        doc.setId(DOC_ID);
        doc.setTemplateId(TEMPLATE_ID);
        doc.setTemplateSnapshotId(SNAPSHOT_ID);
        return doc;
    }

    private static DocumentRegistry registry() {
        DocumentRegistry r = new DocumentRegistry();
        r.setId(DOC_ID);
        r.setTemplateId(TEMPLATE_ID);
        r.setName("Questionnaire Test Doc");
        return r;
    }

    /** Snapshot (immutable) that still holds the questionnaire. */
    private static TemplateSnapshot snapshotWithQuestionnaire() {
        return TemplateSnapshot.builder()
                .id(SNAPSHOT_ID)
                .templateId(TEMPLATE_ID)
                .versionNumber(3)
                .parameters(parameters())
                .questionnaire(questionnaire())
                .build();
    }

    /** Snapshot (immutable) whose questionnaire is absent. */
    private static TemplateSnapshot snapshotWithoutQuestionnaire() {
        return TemplateSnapshot.builder()
                .id(SNAPSHOT_ID)
                .templateId(TEMPLATE_ID)
                .versionNumber(3)
                .parameters(parameters())
                .questionnaire(null)
                .build();
    }

    /** Live {@link TemplateResponse} with the supplied questionnaire (may be null). */
    private static TemplateResponse liveTemplate(Questionnaire questionnaire) {
        return new TemplateResponse(
                TEMPLATE_ID, "contract", "Договор", null, null,
                1, 1, 0, true, null, null, null, null,
                parameters(), questionnaire, Map.of(), null, null, null);
    }

    private static List<Parameter> parameters() {
        return List.of(
                Parameter.builder()
                        .code("client_name").label("ФИО клиента")
                        .type(ParameterType.FIO).required(true)
                        .build(),
                Parameter.builder()
                        .code("penalty_percent").label("Процент неустойки")
                        .type(ParameterType.NUMBER).required(false)
                        .build());
    }

    /** Two-question page: q1 always visible, q2 gated by {@code has_penalty == true}. */
    private static Questionnaire questionnaire() {
        Question q1 = Question.builder()
                .id("q1").parameterCode("client_name")
                .label("Укажите ФИО клиента").order(1)
                .visibilityCondition(null)
                .build();
        Question q2 = Question.builder()
                .id("q2").parameterCode("penalty_percent")
                .label("Укажите процент неустойки").order(2)
                .visibilityCondition(Condition.builder()
                        .type(ConditionType.SIMPLE)
                        .expression("has_penalty == true")
                        .build())
                .build();
        return Questionnaire.builder()
                .title("Параметры договора")
                .pages(List.of(QuestionnairePage.builder()
                        .title("Стороны договора")
                        .questions(List.of(q1, q2))
                        .build()))
                .build();
    }
}
