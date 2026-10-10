package com.docforge.template;

import com.docforge.audit.AuditService;
import com.docforge.common.exception.ResourceNotFoundException;
import com.docforge.document.DocumentService;
import com.docforge.document.generator.PythonConditionEvaluator;
import com.docforge.template.dto.DiffResult;
import com.docforge.template.dto.RollbackResponse;
import com.docforge.template.dto.SaveVersionRequest;
import com.docforge.template.dto.SaveVersionResponse;
import com.docforge.template.dto.TemplateVersionDTO;
import com.docforge.template.model.Template;
import com.docforge.template.model.TemplateRegistry;
import com.docforge.template.model.TemplateSnapshot;
import com.docforge.template.model.TemplateVersion;
import com.docforge.user.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TemplateServiceImplTest {

    @Mock private TemplateRegistryRepository registryRepository;
    @Mock private TemplateRepository templateRepository;
    @Mock private TemplateVersionRepository versionRepository;
    @Mock private TemplateSnapshotRepository snapshotRepository;
    @Mock private TemplateDiffService templateDiffService;
    @Mock private AuditService auditService;
    @Mock private TemplateValidator templateValidator;
    @Mock private PythonConditionEvaluator pythonConditionEvaluator;
    @Mock private DocumentService documentService;
    @Mock private UserService userService;

    @InjectMocks
    private TemplateServiceImpl templateService;

    private final UUID templateId = UUID.randomUUID();

    // ── Helper factories ───────────────────────────────────────────────────────

    private static TemplateRegistry registry(UUID id, int major, int minor) {
        TemplateRegistry r = new TemplateRegistry();
        r.setId(id);
        r.setName("Test Template");
        r.setMajorVersion(major);
        r.setMinorVersion(minor);
        r.setVersion(major * 10000 + minor);
        return r;
    }

    private static Template template(UUID id) {
        return Template.builder()
                .id(id)
                .version(1)
                .body(Map.of("type", "doc", "content", List.of()))
                .build();
    }

    private static TemplateSnapshot snapshot(UUID id, UUID templateId, int versionNumber, int major, int minor) {
        return TemplateSnapshot.builder()
                .id(id)
                .templateId(templateId)
                .versionNumber(versionNumber)
                .majorVersion(major)
                .minorVersion(minor)
                .createdAt(java.time.Instant.now())
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  saveVersion
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("saveVersion() with MINOR bump creates version 1.1 and persists snapshot")
    void saveVersion_minorBump_incrementsMinor() {
        // given
        TemplateRegistry registry = registry(templateId, 1, 0);
        Template template = template(templateId);

        when(registryRepository.findById(templateId)).thenReturn(Optional.of(registry));
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));
        when(templateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(registryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(versionRepository.save(any())).thenAnswer(inv -> {
            TemplateVersion v = inv.getArgument(0);
            if (v.getId() == null) v.setId(UUID.randomUUID());
            return v;
        });
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SaveVersionRequest request = new SaveVersionRequest("MINOR", "Added new parameter");

        // when
        SaveVersionResponse response = templateService.saveVersion(templateId, request);

        // then — version metadata
        ArgumentCaptor<TemplateVersion> versionCaptor = ArgumentCaptor.forClass(TemplateVersion.class);
        verify(versionRepository).save(versionCaptor.capture());
        TemplateVersion savedVersion = versionCaptor.getValue();

        assertThat(savedVersion.getTemplateId()).isEqualTo(templateId);
        assertThat(savedVersion.getVersionNumber()).isEqualTo(10001);          // 1*10000 + 1
        assertThat(savedVersion.getMajorVersion()).isEqualTo(1);
        assertThat(savedVersion.getMinorVersion()).isEqualTo(1);
        assertThat(savedVersion.getBumpType()).isEqualTo("MINOR");
        assertThat(savedVersion.getComment()).isEqualTo("Added new parameter");
        assertThat(savedVersion.getAutoDescription()).isEqualTo("Первая версия");
        assertThat(savedVersion.getRollbackFrom()).isNull();
        assertThat(savedVersion.getCreatedBy()).isNull();                     // no security context

        // then — snapshot
        ArgumentCaptor<TemplateSnapshot> snapshotCaptor = ArgumentCaptor.forClass(TemplateSnapshot.class);
        verify(snapshotRepository).save(snapshotCaptor.capture());
        TemplateSnapshot savedSnapshot = snapshotCaptor.getValue();

        assertThat(savedSnapshot.getId()).isEqualTo(savedVersion.getId());
        assertThat(savedSnapshot.getTemplateId()).isEqualTo(templateId);
        assertThat(savedSnapshot.getVersionNumber()).isEqualTo(10001);
        assertThat(savedSnapshot.getMajorVersion()).isEqualTo(1);
        assertThat(savedSnapshot.getMinorVersion()).isEqualTo(1);
        assertThat(savedSnapshot.getBody()).isEqualTo(template.getBody());
        assertThat(savedSnapshot.getCreatedAt()).isNotNull();

        // then — registry was bumped
        ArgumentCaptor<TemplateRegistry> registryCaptor = ArgumentCaptor.forClass(TemplateRegistry.class);
        verify(registryRepository).save(registryCaptor.capture());
        TemplateRegistry savedRegistry = registryCaptor.getValue();

        assertThat(savedRegistry.getMajorVersion()).isEqualTo(1);
        assertThat(savedRegistry.getMinorVersion()).isEqualTo(1);
        assertThat(savedRegistry.getVersion()).isEqualTo(10001);

        // then — response
        assertThat(response.version().majorVersion()).isEqualTo(1);
        assertThat(response.version().minorVersion()).isEqualTo(1);
        assertThat(response.version().versionNumber()).isEqualTo(10001);
        assertThat(response.version().bumpType()).isEqualTo("MINOR");
        assertThat(response.template().majorVersion()).isEqualTo(1);
        assertThat(response.template().minorVersion()).isEqualTo(1);

        verify(auditService).log(any(), eq("TEMPLATE"), eq(templateId), eq("SAVE_VERSION"), contains("MINOR"));
    }

    @Test
    @DisplayName("saveVersion() with MAJOR bump increments major and resets minor to 0")
    void saveVersion_majorBump_resetsMinor() {
        // given
        TemplateRegistry registry = registry(templateId, 2, 5);              // v2.5 → bump MAJOR → v3.0
        Template template = template(templateId);

        when(registryRepository.findById(templateId)).thenReturn(Optional.of(registry));
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));
        when(templateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(registryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(versionRepository.save(any())).thenAnswer(inv -> {
            TemplateVersion v = inv.getArgument(0);
            if (v.getId() == null) v.setId(UUID.randomUUID());
            return v;
        });
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SaveVersionRequest request = new SaveVersionRequest("MAJOR", "Breaking API change");

        // when
        SaveVersionResponse response = templateService.saveVersion(templateId, request);

        // then — version metadata
        ArgumentCaptor<TemplateVersion> versionCaptor = ArgumentCaptor.forClass(TemplateVersion.class);
        verify(versionRepository).save(versionCaptor.capture());
        TemplateVersion savedVersion = versionCaptor.getValue();

        assertThat(savedVersion.getVersionNumber()).isEqualTo(30000);          // 3*10000 + 0
        assertThat(savedVersion.getMajorVersion()).isEqualTo(3);
        assertThat(savedVersion.getMinorVersion()).isEqualTo(0);
        assertThat(savedVersion.getBumpType()).isEqualTo("MAJOR");
        assertThat(savedVersion.getComment()).isEqualTo("Breaking API change");

        // then — snapshot
        ArgumentCaptor<TemplateSnapshot> snapshotCaptor = ArgumentCaptor.forClass(TemplateSnapshot.class);
        verify(snapshotRepository).save(snapshotCaptor.capture());
        TemplateSnapshot savedSnapshot = snapshotCaptor.getValue();

        assertThat(savedSnapshot.getMajorVersion()).isEqualTo(3);
        assertThat(savedSnapshot.getMinorVersion()).isEqualTo(0);

        // then — response
        assertThat(response.version().majorVersion()).isEqualTo(3);
        assertThat(response.version().minorVersion()).isEqualTo(0);
        assertThat(response.template().majorVersion()).isEqualTo(3);
        assertThat(response.template().minorVersion()).isEqualTo(0);
        assertThat(response.template().version()).isEqualTo(30000);
    }

    @Test
    @DisplayName("saveVersion() throws ResourceNotFoundException when template registry missing")
    void saveVersion_throwsResourceNotFoundException_whenTemplateNotFound() {
        when(registryRepository.findById(templateId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                templateService.saveVersion(templateId, new SaveVersionRequest("MINOR", null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(templateId.toString());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  rollback
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("rollback() restores template from snapshot and creates ROLLBACK version")
    void rollback_restoresTemplateContentFromSnapshot() {
        // given
        TemplateRegistry registry = registry(templateId, 1, 2);               // current v1.2
        Template template = template(templateId);
        UUID versionId = UUID.randomUUID();

        Map<String, Object> originalBody = Map.of(
                "type", "doc",
                "content", List.of(Map.of("type", "paragraph"))
        );
        TemplateSnapshot oldSnapshot = TemplateSnapshot.builder()
                .id(versionId)
                .templateId(templateId)
                .versionNumber(10001)
                .majorVersion(1)
                .minorVersion(1)
                .body(originalBody)
                .build();

        when(registryRepository.findById(templateId)).thenReturn(Optional.of(registry));
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));
        when(templateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(snapshotRepository.findByTemplateIdAndVersionNumber(templateId, 10001))
                .thenReturn(Optional.of(oldSnapshot));
        when(registryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(versionRepository.save(any())).thenAnswer(inv -> {
            TemplateVersion v = inv.getArgument(0);
            if (v.getId() == null) v.setId(UUID.randomUUID());
            return v;
        });
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        RollbackResponse response = templateService.rollback(templateId, 10001);

        // then — template body was restored
        assertThat(template.getBody()).isEqualTo(originalBody);
        verify(templateRepository).save(template);

        // then — new version created with ROLLBACK bump
        ArgumentCaptor<TemplateVersion> versionCaptor = ArgumentCaptor.forClass(TemplateVersion.class);
        verify(versionRepository).save(versionCaptor.capture());
        TemplateVersion newVersion = versionCaptor.getValue();

        assertThat(newVersion.getBumpType()).isEqualTo("ROLLBACK");
        assertThat(newVersion.getRollbackFrom()).isEqualTo(10001);
        assertThat(newVersion.getVersionNumber()).isEqualTo(10003);            // 1*10000 + (2+1)
        assertThat(newVersion.getMajorVersion()).isEqualTo(1);
        assertThat(newVersion.getMinorVersion()).isEqualTo(3);
        assertThat(newVersion.getAutoDescription()).contains("Откат к версии v1.1");

        // then — new snapshot reflects restored content
        ArgumentCaptor<TemplateSnapshot> snapshotCaptor = ArgumentCaptor.forClass(TemplateSnapshot.class);
        verify(snapshotRepository).save(snapshotCaptor.capture());
        TemplateSnapshot newSnapshot = snapshotCaptor.getValue();

        assertThat(newSnapshot.getTemplateId()).isEqualTo(templateId);
        assertThat(newSnapshot.getVersionNumber()).isEqualTo(10003);
        assertThat(newSnapshot.getMajorVersion()).isEqualTo(1);
        assertThat(newSnapshot.getMinorVersion()).isEqualTo(3);
        assertThat(newSnapshot.getBody()).isEqualTo(originalBody);

        // then — registry minor bumped
        assertThat(registry.getMinorVersion()).isEqualTo(3);
        assertThat(registry.getVersion()).isEqualTo(10003);

        // then — response DTO
        assertThat(response.version().majorVersion()).isEqualTo(1);
        assertThat(response.version().minorVersion()).isEqualTo(3);
        assertThat(response.version().bumpType()).isEqualTo("ROLLBACK");
        assertThat(response.version().rollbackFrom()).isEqualTo(10001);

        verify(auditService).log(any(), eq("TEMPLATE"), eq(templateId), eq("ROLLBACK"),
                contains("fromVersion=10001"));
    }

    @Test
    @DisplayName("rollback() throws ResourceNotFoundException when snapshot not found")
    void rollback_throwsResourceNotFoundException_whenSnapshotNotFound() {
        when(registryRepository.findById(templateId)).thenReturn(Optional.of(registry(templateId, 1, 2)));
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template(templateId)));
        when(snapshotRepository.findByTemplateIdAndVersionNumber(templateId, 999))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> templateService.rollback(templateId, 999))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Снимок шаблона не найден");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  getDiff
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getDiff() returns diff computed by TemplateDiffService")
    void getDiff_returnsDiffResultFromSnapshots() {
        // given
        TemplateRegistry registry = registry(templateId, 1, 2);
        TemplateSnapshot fromSnapshot = snapshot(UUID.randomUUID(), templateId, 10001, 1, 1);
        TemplateSnapshot toSnapshot = snapshot(UUID.randomUUID(), templateId, 10002, 1, 2);

        DiffResult expectedDiff = new DiffResult(
                "Добавлен параметр: test",
                List.of("Добавлен параметр: test"),
                true
        );

        when(registryRepository.findById(templateId)).thenReturn(Optional.of(registry));
        when(snapshotRepository.findByTemplateIdAndVersionNumber(templateId, 10001))
                .thenReturn(Optional.of(fromSnapshot));
        when(snapshotRepository.findByTemplateIdAndVersionNumber(templateId, 10002))
                .thenReturn(Optional.of(toSnapshot));
        when(templateDiffService.diff(fromSnapshot, toSnapshot)).thenReturn(expectedDiff);

        // when
        DiffResult result = templateService.getDiff(templateId, 10001, 10002);

        // then
        assertThat(result).isEqualTo(expectedDiff);
        assertThat(result.hasChanges()).isTrue();
        assertThat(result.summary()).isEqualTo("Добавлен параметр: test");
        assertThat(result.changes()).containsExactly("Добавлен параметр: test");

        verify(templateDiffService).diff(fromSnapshot, toSnapshot);
    }

    @Test
    @DisplayName("getDiff() throws ResourceNotFoundException when a snapshot is missing")
    void getDiff_throwsResourceNotFoundException_whenSnapshotNotFound() {
        when(registryRepository.findById(templateId)).thenReturn(Optional.of(registry(templateId, 1, 2)));
        when(snapshotRepository.findByTemplateIdAndVersionNumber(templateId, 10001))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> templateService.getDiff(templateId, 10001, 10002))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Снимок шаблона не найден");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  listVersions
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("listVersions() returns version DTOs sorted descending by version number")
    void listVersions_returnsVersionDTOsInDescendingOrder() {
        // given
        TemplateRegistry registry = registry(templateId, 1, 3);
        OffsetDateTime now = OffsetDateTime.now();

        TemplateVersion v2 = createVersion(templateId, UUID.randomUUID(), 10002, 1, 2,
                "MINOR", "Second version", null);
        v2.setCreatedAt(now);

        TemplateVersion v1 = createVersion(templateId, UUID.randomUUID(), 10001, 1, 1,
                "MINOR", "First version", null);
        v1.setCreatedAt(now.minusHours(1));

        when(registryRepository.findById(templateId)).thenReturn(Optional.of(registry));
        when(versionRepository.findByTemplateIdOrderByVersionNumberDesc(templateId))
                .thenReturn(List.of(v2, v1));

        // when
        List<TemplateVersionDTO> result = templateService.listVersions(templateId);

        // then
        assertThat(result).hasSize(2);

        assertThat(result.get(0).templateId()).isEqualTo(templateId);
        assertThat(result.get(0).versionNumber()).isEqualTo(10002);
        assertThat(result.get(0).majorVersion()).isEqualTo(1);
        assertThat(result.get(0).minorVersion()).isEqualTo(2);
        assertThat(result.get(0).bumpType()).isEqualTo("MINOR");
        assertThat(result.get(0).comment()).isEqualTo("Second version");

        assertThat(result.get(1).versionNumber()).isEqualTo(10001);
        assertThat(result.get(1).majorVersion()).isEqualTo(1);
        assertThat(result.get(1).minorVersion()).isEqualTo(1);
        assertThat(result.get(1).bumpType()).isEqualTo("MINOR");
        assertThat(result.get(1).comment()).isEqualTo("First version");
    }

    @Test
    @DisplayName("listVersions() maps all fields including rollbackFrom and autoDescription")
    void listVersions_mapsAllFields() {
        // given
        TemplateRegistry registry = registry(templateId, 1, 4);
        OffsetDateTime now = OffsetDateTime.now();

        TemplateVersion version = createVersion(templateId, UUID.randomUUID(), 10003, 1, 3,
                "ROLLBACK", null, 10001);
        version.setAutoDescription("Откат к версии v1.1");

        when(registryRepository.findById(templateId)).thenReturn(Optional.of(registry));
        when(versionRepository.findByTemplateIdOrderByVersionNumberDesc(templateId))
                .thenReturn(List.of(version));

        // when
        List<TemplateVersionDTO> result = templateService.listVersions(templateId);

        // then
        assertThat(result).hasSize(1);
        TemplateVersionDTO dto = result.get(0);

        assertThat(dto.id()).isEqualTo(version.getId());
        assertThat(dto.versionNumber()).isEqualTo(10003);
        assertThat(dto.majorVersion()).isEqualTo(1);
        assertThat(dto.minorVersion()).isEqualTo(3);
        assertThat(dto.bumpType()).isEqualTo("ROLLBACK");
        assertThat(dto.rollbackFrom()).isEqualTo(10001);
        assertThat(dto.autoDescription()).isEqualTo("Откат к версии v1.1");
        assertThat(dto.createdAt()).isNotNull();
        assertThat(dto.createdBy()).isNull();
    }

    @Test
    @DisplayName("listVersions() throws ResourceNotFoundException when template not found")
    void listVersions_throwsResourceNotFoundException_whenTemplateNotFound() {
        when(registryRepository.findById(templateId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> templateService.listVersions(templateId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(templateId.toString());
    }

    // ── Helper ─────────────────────────────────────────────────────────────────

    private static TemplateVersion createVersion(UUID templateId, UUID id, int versionNumber,
                                                  int major, int minor, String bumpType,
                                                  String comment, Integer rollbackFrom) {
        TemplateVersion v = new TemplateVersion();
        v.setId(id);
        v.setTemplateId(templateId);
        v.setVersionNumber(versionNumber);
        v.setMajorVersion(major);
        v.setMinorVersion(minor);
        v.setBumpType(bumpType);
        v.setComment(comment);
        v.setRollbackFrom(rollbackFrom);
        v.setCreatedAt(OffsetDateTime.now());
        return v;
    }
}
