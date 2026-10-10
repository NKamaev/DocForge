package com.docforge.document;

import com.docforge.common.exception.ResourceNotFoundException;
import com.docforge.document.dto.DiffChunk;
import com.docforge.document.dto.DiffChunk.DiffChunkType;
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
 * Verifies full-context diff chunking (EQUAL/DELETE/INSERT) between revisions
 * and against the current document body.
 */
@ExtendWith(MockitoExtension.class)
class DocumentServiceDiffTest {

    private static final UUID DOC_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentRegistryRepository registryRepository;
    @Mock private DocumentRevisionRepository revisionRepository;
    @Mock private DocumentRevisionBodyRepository revisionBodyRepository;

    @InjectMocks
    private DocumentServiceImpl service;

    // ── diff between two revisions ────────────────────────────────────────────

    @Test
    @DisplayName("computeRevisionDiff() between two differing revisions returns EQUAL/DELETE/INSERT/EQUAL chunks")
    void computeRevisionDiff_twoRevisions_returnsContextualChunks() {
        // given — revision 1: A B C; revision 2: A X C (one line replaced)
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(registryRepository.findById(DOC_ID)).thenReturn(Optional.of(registry()));
        when(revisionRepository.findByDocumentIdAndRevisionNumber(DOC_ID, 2))
                .thenReturn(Optional.of(revision(2, id2)));
        when(revisionRepository.findByDocumentIdAndRevisionNumber(DOC_ID, 1))
                .thenReturn(Optional.of(revision(1, id1)));
        when(revisionBodyRepository.findById(id2))
                .thenReturn(Optional.of(revisionBody(2, id2, tiptapDoc("A", "B", "C"))));
        when(revisionBodyRepository.findById(id1))
                .thenReturn(Optional.of(revisionBody(1, id1, tiptapDoc("A", "X", "C"))));

        // when
        RevisionDiffResponse response = service.computeRevisionDiff(DOC_ID, 2, 1);

        // then — metadata
        assertThat(response.revisionId()).isEqualTo(id2);
        assertThat(response.revisionNumber()).isEqualTo(2);
        assertThat(response.compareToRevisionNumber()).isEqualTo(1);
        assertThat(response.compareToCurrent()).isFalse();

        // then — chunk types and order (context preserved on both sides)
        assertThat(response.diff()).extracting(DiffChunk::type)
                .containsExactly(DiffChunkType.EQUAL, DiffChunkType.DELETE,
                        DiffChunkType.INSERT, DiffChunkType.EQUAL);

        // then — each chunk carries the expected text
        List<DiffChunk> chunks = response.diff();
        assertThat(chunks.get(0).text()).contains("A");
        assertThat(chunks.get(1).text()).contains("X"); // deleted line (old value)
        assertThat(chunks.get(2).text()).contains("B"); // inserted line (new value)
        assertThat(chunks.get(3).text()).contains("C");
    }

    // ── diff against current body ─────────────────────────────────────────────

    @Test
    @DisplayName("computeRevisionDiff() with null compareTo diffs against document.currentBody")
    void computeRevisionDiff_nullCompareTo_usesCurrentBody() {
        // given — revision 1: A B; current body: A → line B was added after saving rev 1
        UUID id1 = UUID.randomUUID();
        when(registryRepository.findById(DOC_ID)).thenReturn(Optional.of(registry()));
        when(revisionRepository.findByDocumentIdAndRevisionNumber(DOC_ID, 1))
                .thenReturn(Optional.of(revision(1, id1)));
        when(revisionBodyRepository.findById(id1))
                .thenReturn(Optional.of(revisionBody(1, id1, tiptapDoc("A", "B"))));
        when(documentRepository.findById(DOC_ID))
                .thenReturn(Optional.of(documentWithCurrentBody(tiptapDoc("A"))));

        // when
        RevisionDiffResponse response = service.computeRevisionDiff(DOC_ID, 1, null);

        // then
        assertThat(response.compareToCurrent()).isTrue();
        assertThat(response.compareToRevisionNumber()).isNull();
        assertThat(response.diff()).extracting(DiffChunk::type)
                .containsExactly(DiffChunkType.EQUAL, DiffChunkType.INSERT);
        assertThat(response.diff().get(1).text()).contains("B");
    }

    @Test
    @DisplayName("computeRevisionDiff() falls back to generatedBody when currentBody is null")
    void computeRevisionDiff_nullCompareTo_currentBodyNull_fallsBackToGenerated() {
        // given — currentBody absent, generatedBody differs from the revision
        UUID id1 = UUID.randomUUID();
        when(registryRepository.findById(DOC_ID)).thenReturn(Optional.of(registry()));
        when(revisionRepository.findByDocumentIdAndRevisionNumber(DOC_ID, 1))
                .thenReturn(Optional.of(revision(1, id1)));
        when(revisionBodyRepository.findById(id1))
                .thenReturn(Optional.of(revisionBody(1, id1, tiptapDoc("A", "B"))));

        Document doc = new Document();
        doc.setId(DOC_ID);
        doc.setCurrentBody(null);
        doc.setGeneratedBody(tiptapDoc("A"));
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(doc));

        // when
        RevisionDiffResponse response = service.computeRevisionDiff(DOC_ID, 1, null);

        // then — diff computed against generated body → B is an insertion
        assertThat(response.compareToCurrent()).isTrue();
        assertThat(response.diff()).extracting(DiffChunk::type)
                .containsExactly(DiffChunkType.EQUAL, DiffChunkType.INSERT);
    }

    // ── identical bodies ──────────────────────────────────────────────────────

    @Test
    @DisplayName("computeRevisionDiff() on identical bodies returns a single EQUAL chunk")
    void computeRevisionDiff_identicalBodies_returnsSingleEqualChunk() {
        // given — both revisions contain the same text
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(registryRepository.findById(DOC_ID)).thenReturn(Optional.of(registry()));
        when(revisionRepository.findByDocumentIdAndRevisionNumber(DOC_ID, 1))
                .thenReturn(Optional.of(revision(1, id1)));
        when(revisionRepository.findByDocumentIdAndRevisionNumber(DOC_ID, 2))
                .thenReturn(Optional.of(revision(2, id2)));
        when(revisionBodyRepository.findById(id1))
                .thenReturn(Optional.of(revisionBody(1, id1, tiptapDoc("Same", "Text"))));
        when(revisionBodyRepository.findById(id2))
                .thenReturn(Optional.of(revisionBody(2, id2, tiptapDoc("Same", "Text"))));

        // when
        RevisionDiffResponse response = service.computeRevisionDiff(DOC_ID, 1, 2);

        // then — no INSERT/DELETE, exactly one EQUAL chunk with the full text
        assertThat(response.diff()).hasSize(1);
        assertThat(response.diff().get(0).type()).isEqualTo(DiffChunkType.EQUAL);
        assertThat(response.diff().get(0).text()).contains("Same").contains("Text");
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

    // ── empty revision bodies ─────────────────────────────────────────────────

    @Test
    @DisplayName("computeRevisionDiff() empty target revision vs non-empty compare lists the compare body as DELETE")
    void computeRevisionDiff_emptyTargetRevision_returnsDeleteOnly() {
        // given — revision 2 body is empty, revision 1 body has one paragraph
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(registryRepository.findById(DOC_ID)).thenReturn(Optional.of(registry()));
        when(revisionRepository.findByDocumentIdAndRevisionNumber(DOC_ID, 2))
                .thenReturn(Optional.of(revision(2, id2)));
        when(revisionRepository.findByDocumentIdAndRevisionNumber(DOC_ID, 1))
                .thenReturn(Optional.of(revision(1, id1)));
        when(revisionBodyRepository.findById(id2))
                .thenReturn(Optional.of(revisionBody(2, id2, tiptapDoc())));
        when(revisionBodyRepository.findById(id1))
                .thenReturn(Optional.of(revisionBody(1, id1, tiptapDoc("A"))));

        // when
        RevisionDiffResponse response = service.computeRevisionDiff(DOC_ID, 2, 1);

        // then — the removed line is the only chunk, no context to preserve
        assertThat(response.diff()).hasSize(1);
        assertThat(response.diff().get(0).type()).isEqualTo(DiffChunkType.DELETE);
        assertThat(response.diff().get(0).text()).contains("A");
    }

    @Test
    @DisplayName("computeRevisionDiff() empty vs empty bodies returns a single empty EQUAL chunk")
    void computeRevisionDiff_emptyVsEmpty_returnsSingleEqualChunk() {
        // given — both revisions contain no text
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(registryRepository.findById(DOC_ID)).thenReturn(Optional.of(registry()));
        when(revisionRepository.findByDocumentIdAndRevisionNumber(DOC_ID, 1))
                .thenReturn(Optional.of(revision(1, id1)));
        when(revisionRepository.findByDocumentIdAndRevisionNumber(DOC_ID, 2))
                .thenReturn(Optional.of(revision(2, id2)));
        when(revisionBodyRepository.findById(id1))
                .thenReturn(Optional.of(revisionBody(1, id1, tiptapDoc())));
        when(revisionBodyRepository.findById(id2))
                .thenReturn(Optional.of(revisionBody(2, id2, tiptapDoc())));

        // when
        RevisionDiffResponse response = service.computeRevisionDiff(DOC_ID, 2, 1);

        // then — no deltas, a single EQUAL chunk carrying the (empty) target text
        assertThat(response.diff()).hasSize(1);
        assertThat(response.diff().get(0).type()).isEqualTo(DiffChunkType.EQUAL);
        assertThat(response.diff().get(0).text()).isEmpty();
    }

    // ── leading / trailing changes ────────────────────────────────────────────

    @Test
    @DisplayName("computeRevisionDiff() revision with a removed leading line emits DELETE before EQUAL")
    void computeRevisionDiff_deletedLeadingLine_emitsDeleteThenEqual() {
        // given — revision 1: A B C; revision 2: B C (line A removed at the start)
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(registryRepository.findById(DOC_ID)).thenReturn(Optional.of(registry()));
        when(revisionRepository.findByDocumentIdAndRevisionNumber(DOC_ID, 2))
                .thenReturn(Optional.of(revision(2, id2)));
        when(revisionRepository.findByDocumentIdAndRevisionNumber(DOC_ID, 1))
                .thenReturn(Optional.of(revision(1, id1)));
        when(revisionBodyRepository.findById(id2))
                .thenReturn(Optional.of(revisionBody(2, id2, tiptapDoc("B", "C"))));
        when(revisionBodyRepository.findById(id1))
                .thenReturn(Optional.of(revisionBody(1, id1, tiptapDoc("A", "B", "C"))));

        // when
        RevisionDiffResponse response = service.computeRevisionDiff(DOC_ID, 2, 1);

        // then — no leading EQUAL since the change starts at position 0
        assertThat(response.diff()).extracting(DiffChunk::type)
                .containsExactly(DiffChunkType.DELETE, DiffChunkType.EQUAL);
        assertThat(response.diff().get(0).text()).contains("A");
        assertThat(response.diff().get(1).text()).contains("B").contains("C");
    }

    @Test
    @DisplayName("computeRevisionDiff() revision with an added leading line emits INSERT before EQUAL")
    void computeRevisionDiff_leadingInsert_emitsInsertThenEqual() {
        // given — revision 1: B C; revision 2: A B C (line A added at the start)
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(registryRepository.findById(DOC_ID)).thenReturn(Optional.of(registry()));
        when(revisionRepository.findByDocumentIdAndRevisionNumber(DOC_ID, 2))
                .thenReturn(Optional.of(revision(2, id2)));
        when(revisionRepository.findByDocumentIdAndRevisionNumber(DOC_ID, 1))
                .thenReturn(Optional.of(revision(1, id1)));
        when(revisionBodyRepository.findById(id2))
                .thenReturn(Optional.of(revisionBody(2, id2, tiptapDoc("A", "B", "C"))));
        when(revisionBodyRepository.findById(id1))
                .thenReturn(Optional.of(revisionBody(1, id1, tiptapDoc("B", "C"))));

        // when
        RevisionDiffResponse response = service.computeRevisionDiff(DOC_ID, 2, 1);

        // then — inserted line first, then unchanged context
        assertThat(response.diff()).extracting(DiffChunk::type)
                .containsExactly(DiffChunkType.INSERT, DiffChunkType.EQUAL);
        assertThat(response.diff().get(0).text()).contains("A");
        assertThat(response.diff().get(1).text()).contains("B").contains("C");
    }

    @Test
    @DisplayName("computeRevisionDiff() fully rewritten body emits a single DELETE then a single INSERT")
    void computeRevisionDiff_allLinesChanged_emitsDeleteThenInsert() {
        // given — revision 1: A B; revision 2: X Y (nothing in common)
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(registryRepository.findById(DOC_ID)).thenReturn(Optional.of(registry()));
        when(revisionRepository.findByDocumentIdAndRevisionNumber(DOC_ID, 2))
                .thenReturn(Optional.of(revision(2, id2)));
        when(revisionRepository.findByDocumentIdAndRevisionNumber(DOC_ID, 1))
                .thenReturn(Optional.of(revision(1, id1)));
        when(revisionBodyRepository.findById(id2))
                .thenReturn(Optional.of(revisionBody(2, id2, tiptapDoc("X", "Y"))));
        when(revisionBodyRepository.findById(id1))
                .thenReturn(Optional.of(revisionBody(1, id1, tiptapDoc("A", "B"))));

        // when
        RevisionDiffResponse response = service.computeRevisionDiff(DOC_ID, 2, 1);

        // then — full replacement is normalised to DELETE (old) then INSERT (new)
        assertThat(response.diff()).extracting(DiffChunk::type)
                .containsExactly(DiffChunkType.DELETE, DiffChunkType.INSERT);
        assertThat(response.diff().get(0).text()).contains("A").contains("B");
        assertThat(response.diff().get(1).text()).contains("X").contains("Y");
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
