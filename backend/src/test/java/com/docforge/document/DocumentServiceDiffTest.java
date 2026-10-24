package com.docforge.document;

import com.docforge.common.exception.ResourceNotFoundException;
import com.docforge.document.dto.RevisionDiffResponse;
import com.docforge.document.model.Document;
import com.docforge.document.model.DocumentRegistry;
import com.docforge.document.model.DocumentRevision;
import com.docforge.document.model.DocumentRevisionBody;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DocumentServiceImpl#computeRevisionDiff}.
 *
 * <p>Variant B (TASK-064): the endpoint no longer computes diff chunks on the
 * server — it returns the raw TipTap bodies ({@code targetBody} / {@code compareBody})
 * so the client can render a structural rich diff. These tests verify the selected
 * bodies, the baseline metadata and error handling.</p>
 */
@ExtendWith(MockitoExtension.class)
class DocumentServiceDiffTest {

    private static final UUID DOC_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentRegistryRepository registryRepository;
    @Mock private DocumentRevisionRepository revisionRepository;
    @Mock private DocumentRevisionBodyRepository revisionBodyRepository;
    /** Новый конструктор-аргумент DocumentServiceImpl (BUG-063): без @Mock инжектится null. */
    @Mock private TipTapBodyValidator tipTapBodyValidator;

    @InjectMocks
    private DocumentServiceImpl service;

    // ── diff between two revisions ────────────────────────────────────────────

    @Test
    @DisplayName("computeRevisionDiff() between two revisions returns the raw target and compare bodies")
    void computeRevisionDiff_twoRevisions_returnsRawBodies() {
        // given — revision 2 body: A B C; revision 1 body: A X C
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Map<String, Object> targetBody = tiptapDoc("A", "B", "C");
        Map<String, Object> compareBody = tiptapDoc("A", "X", "C");
        when(registryRepository.findById(DOC_ID)).thenReturn(Optional.of(registry()));
        when(revisionRepository.findByDocumentIdAndRevisionNumber(DOC_ID, 2))
                .thenReturn(Optional.of(revision(2, id2)));
        when(revisionRepository.findByDocumentIdAndRevisionNumber(DOC_ID, 1))
                .thenReturn(Optional.of(revision(1, id1)));
        when(revisionBodyRepository.findById(id2))
                .thenReturn(Optional.of(revisionBody(2, id2, targetBody)));
        when(revisionBodyRepository.findById(id1))
                .thenReturn(Optional.of(revisionBody(1, id1, compareBody)));

        // when
        RevisionDiffResponse response = service.computeRevisionDiff(DOC_ID, 2, 1);

        // then — metadata
        assertThat(response.revisionId()).isEqualTo(id2);
        assertThat(response.revisionNumber()).isEqualTo(2);
        assertThat(response.compareToRevisionNumber()).isEqualTo(1);
        assertThat(response.compareToCurrent()).isFalse();

        // then — the requested revision is the target, the selected one the baseline
        assertThat(response.targetBody()).isEqualTo(targetBody);
        assertThat(response.compareBody()).isEqualTo(compareBody);
    }

    // ── diff against current body ─────────────────────────────────────────────

    @Test
    @DisplayName("computeRevisionDiff() with null compareTo uses document.currentBody as baseline")
    void computeRevisionDiff_nullCompareTo_usesCurrentBody() {
        // given — revision 1 body: A B; current body: A
        UUID id1 = UUID.randomUUID();
        Map<String, Object> revisionBody = tiptapDoc("A", "B");
        Map<String, Object> currentBody = tiptapDoc("A");
        when(registryRepository.findById(DOC_ID)).thenReturn(Optional.of(registry()));
        when(revisionRepository.findByDocumentIdAndRevisionNumber(DOC_ID, 1))
                .thenReturn(Optional.of(revision(1, id1)));
        when(revisionBodyRepository.findById(id1))
                .thenReturn(Optional.of(revisionBody(1, id1, revisionBody)));
        when(documentRepository.findById(DOC_ID))
                .thenReturn(Optional.of(documentWithCurrentBody(currentBody)));

        // when
        RevisionDiffResponse response = service.computeRevisionDiff(DOC_ID, 1, null);

        // then
        assertThat(response.compareToCurrent()).isTrue();
        assertThat(response.compareToRevisionNumber()).isNull();
        assertThat(response.targetBody()).isEqualTo(revisionBody);
        assertThat(response.compareBody()).isEqualTo(currentBody);
    }

    @Test
    @DisplayName("computeRevisionDiff() falls back to generatedBody when currentBody is null")
    void computeRevisionDiff_nullCompareTo_currentBodyNull_fallsBackToGenerated() {
        // given — currentBody absent, generatedBody is the only baseline
        UUID id1 = UUID.randomUUID();
        Map<String, Object> revisionBody = tiptapDoc("A", "B");
        Map<String, Object> generatedBody = tiptapDoc("A");
        when(registryRepository.findById(DOC_ID)).thenReturn(Optional.of(registry()));
        when(revisionRepository.findByDocumentIdAndRevisionNumber(DOC_ID, 1))
                .thenReturn(Optional.of(revision(1, id1)));
        when(revisionBodyRepository.findById(id1))
                .thenReturn(Optional.of(revisionBody(1, id1, revisionBody)));

        Document doc = new Document();
        doc.setId(DOC_ID);
        doc.setCurrentBody(null);
        doc.setGeneratedBody(generatedBody);
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(doc));

        // when
        RevisionDiffResponse response = service.computeRevisionDiff(DOC_ID, 1, null);

        // then
        assertThat(response.compareToCurrent()).isTrue();
        assertThat(response.compareBody()).isEqualTo(generatedBody);
    }

    @Test
    @DisplayName("computeRevisionDiff() falls back to null compareBody when both currentBody and generatedBody are null")
    void computeRevisionDiff_nullCompareTo_currentAndGeneratedNull_returnsNullCompareBody() {
        // given — review #13: document has neither currentBody nor generatedBody
        UUID id1 = UUID.randomUUID();
        Map<String, Object> revisionBody = tiptapDoc("A");
        when(registryRepository.findById(DOC_ID)).thenReturn(Optional.of(registry()));
        when(revisionRepository.findByDocumentIdAndRevisionNumber(DOC_ID, 1))
                .thenReturn(Optional.of(revision(1, id1)));
        when(revisionBodyRepository.findById(id1))
                .thenReturn(Optional.of(revisionBody(1, id1, revisionBody)));

        Document doc = new Document();
        doc.setId(DOC_ID);
        doc.setCurrentBody(null);
        doc.setGeneratedBody(null);
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(doc));

        // when
        RevisionDiffResponse response = service.computeRevisionDiff(DOC_ID, 1, null);

        // then — the baseline is null, but the target body is still returned intact
        assertThat(response.compareToCurrent()).isTrue();
        assertThat(response.compareToRevisionNumber()).isNull();
        assertThat(response.targetBody()).isEqualTo(revisionBody);
        assertThat(response.compareBody()).isNull();
    }

    // ── identical bodies ──────────────────────────────────────────────────────

    @Test
    @DisplayName("computeRevisionDiff() on identical bodies still echoes both raw bodies")
    void computeRevisionDiff_identicalBodies_returnsBothBodies() {
        // given — both revisions contain the same text
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Map<String, Object> body = tiptapDoc("Same", "Text");
        when(registryRepository.findById(DOC_ID)).thenReturn(Optional.of(registry()));
        when(revisionRepository.findByDocumentIdAndRevisionNumber(DOC_ID, 1))
                .thenReturn(Optional.of(revision(1, id1)));
        when(revisionRepository.findByDocumentIdAndRevisionNumber(DOC_ID, 2))
                .thenReturn(Optional.of(revision(2, id2)));
        when(revisionBodyRepository.findById(id1))
                .thenReturn(Optional.of(revisionBody(1, id1, body)));
        when(revisionBodyRepository.findById(id2))
                .thenReturn(Optional.of(revisionBody(2, id2, body)));

        // when
        RevisionDiffResponse response = service.computeRevisionDiff(DOC_ID, 1, 2);

        // then
        assertThat(response.compareToCurrent()).isFalse();
        assertThat(response.compareToRevisionNumber()).isEqualTo(2);
        assertThat(response.targetBody()).isEqualTo(body);
        assertThat(response.compareBody()).isEqualTo(body);
    }

    // ── missing revision ──────────────────────────────────────────────────────

    @Test
    @DisplayName("computeRevisionDiff() throws ResourceNotFoundException for unknown revision number")
    void computeRevisionDiff_unknownRevision_throwsNotFound() {
        // given — document exists but has no revision 99
        when(registryRepository.findById(DOC_ID)).thenReturn(Optional.of(registry()));
        when(revisionRepository.findByDocumentIdAndRevisionNumber(DOC_ID, 99))
                .thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.computeRevisionDiff(DOC_ID, 99, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("computeRevisionDiff() throws ResourceNotFoundException for unknown compareTo revision")
    void computeRevisionDiff_unknownCompareTo_throwsNotFound() {
        // given — target revision exists, baseline revision does not
        UUID id2 = UUID.randomUUID();
        when(registryRepository.findById(DOC_ID)).thenReturn(Optional.of(registry()));
        when(revisionRepository.findByDocumentIdAndRevisionNumber(DOC_ID, 2))
                .thenReturn(Optional.of(revision(2, id2)));
        when(revisionBodyRepository.findById(id2))
                .thenReturn(Optional.of(revisionBody(2, id2, tiptapDoc("A"))));
        when(revisionRepository.findByDocumentIdAndRevisionNumber(DOC_ID, 41))
                .thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.computeRevisionDiff(DOC_ID, 2, 41))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── empty revision bodies ─────────────────────────────────────────────────

    @Test
    @DisplayName("computeRevisionDiff() empty target revision keeps the compare body intact")
    void computeRevisionDiff_emptyTargetRevision_returnsEmptyTarget() {
        // given — revision 2 body is empty, revision 1 body has one paragraph
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Map<String, Object> emptyBody = tiptapDoc();
        Map<String, Object> compareBody = tiptapDoc("A");
        when(registryRepository.findById(DOC_ID)).thenReturn(Optional.of(registry()));
        when(revisionRepository.findByDocumentIdAndRevisionNumber(DOC_ID, 2))
                .thenReturn(Optional.of(revision(2, id2)));
        when(revisionRepository.findByDocumentIdAndRevisionNumber(DOC_ID, 1))
                .thenReturn(Optional.of(revision(1, id1)));
        when(revisionBodyRepository.findById(id2))
                .thenReturn(Optional.of(revisionBody(2, id2, emptyBody)));
        when(revisionBodyRepository.findById(id1))
                .thenReturn(Optional.of(revisionBody(1, id1, compareBody)));

        // when
        RevisionDiffResponse response = service.computeRevisionDiff(DOC_ID, 2, 1);

        // then — client receives both raw bodies untouched; the empty target stays empty
        assertThat(response.targetBody()).isEqualTo(emptyBody);
        assertThat(((Map<?, ?>) response.targetBody()).get("content")).isEqualTo(List.of());
        assertThat(response.compareBody()).isEqualTo(compareBody);
    }

    @Test
    @DisplayName("computeRevisionDiff() empty vs empty bodies returns two empty docs")
    void computeRevisionDiff_emptyVsEmpty_returnsEmptyBodies() {
        // given — both revisions contain no content
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Map<String, Object> emptyBody = tiptapDoc();
        when(registryRepository.findById(DOC_ID)).thenReturn(Optional.of(registry()));
        when(revisionRepository.findByDocumentIdAndRevisionNumber(DOC_ID, 1))
                .thenReturn(Optional.of(revision(1, id1)));
        when(revisionRepository.findByDocumentIdAndRevisionNumber(DOC_ID, 2))
                .thenReturn(Optional.of(revision(2, id2)));
        when(revisionBodyRepository.findById(id1))
                .thenReturn(Optional.of(revisionBody(1, id1, emptyBody)));
        when(revisionBodyRepository.findById(id2))
                .thenReturn(Optional.of(revisionBody(2, id2, emptyBody)));

        // when
        RevisionDiffResponse response = service.computeRevisionDiff(DOC_ID, 2, 1);

        // then
        assertThat(response.targetBody()).isEqualTo(emptyBody);
        assertThat(response.compareBody()).isEqualTo(emptyBody);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private static DocumentRegistry registry() {
        DocumentRegistry r = new DocumentRegistry();
        r.setId(DOC_ID);
        r.setName("Diff Test Doc");
        return r;
    }

    private static DocumentRevision revision(int number, UUID id) {
        DocumentRevision r = new DocumentRevision();
        r.setId(id);
        r.setDocumentId(DOC_ID);
        r.setRevisionNumber(number);
        return r;
    }

    private static DocumentRevisionBody revisionBody(int number, UUID id, Object body) {
        return DocumentRevisionBody.builder()
                .id(id)
                .documentId(DOC_ID)
                .revisionNumber(number)
                .body(body)
                .build();
    }

    private static Document documentWithCurrentBody(Object currentBody) {
        Document doc = new Document();
        doc.setId(DOC_ID);
        doc.setCurrentBody(currentBody);
        return doc;
    }

    /** Builds a minimal TipTap document with one paragraph per provided line. */
    private static Map<String, Object> tiptapDoc(String... paragraphs) {
        List<Map<String, Object>> content = new ArrayList<>();
        for (String paragraph : paragraphs) {
            content.add(Map.of(
                    "type", "paragraph",
                    "content", List.of(Map.of("type", "text", "text", paragraph))
            ));
        }
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("type", "doc");
        doc.put("content", content);
        return doc;
    }
}
