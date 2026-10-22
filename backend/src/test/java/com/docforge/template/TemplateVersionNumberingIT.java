package com.docforge.template;

import com.docforge.AbstractIT;
import com.docforge.template.dto.CreateTemplateRequest;
import com.docforge.template.dto.RollbackResponse;
import com.docforge.template.dto.SaveVersionRequest;
import com.docforge.template.dto.SaveVersionResponse;
import com.docforge.template.dto.TemplateResponse;
import com.docforge.template.dto.TemplateVersionDTO;
import com.docforge.template.model.Template;
import com.docforge.template.model.TemplateRegistry;
import com.docforge.template.model.TemplateSnapshot;
import com.docforge.template.model.TemplateVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * BUG-054 regression guard: {@code publish()} and {@code saveVersion()} must share one
 * semantic counter.
 *
 * <p>Before the fix {@code publish()} took {@code max(version_number) + 1}, while
 * {@code saveVersion()} used {@code major * 10000 + minor}. Sequence
 * {@code saveVersion(MINOR) = 10001 → publish = 10002 → saveVersion(MINOR) = 10002}
 * violated {@code UNIQUE (template_id, version_number)} and surfaced as a 500
 * {@code DataIntegrityViolationException}.
 *
 * <p>These tests use a real PostgreSQL + MongoDB (see {@link AbstractIT}). They are
 * isolated by creating a fresh template per test and deleting its rows afterwards.
 */
class TemplateVersionNumberingIT extends AbstractIT {

    private static final int MAJOR_SCALE = 10_000;

    @Autowired
    private TemplateService templateService;

    @Autowired
    private TemplateVersionRepository versionRepository;

    @Autowired
    private TemplateSnapshotRepository snapshotRepository;

    @Autowired
    private TemplateRegistryRepository registryRepository;

    @Autowired
    private TemplateRepository templateRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<UUID> createdTemplateIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (UUID templateId : createdTemplateIds) {
            try {
                mongoTemplate.remove(
                        Query.query(Criteria.where("template_id").is(templateId)),
                        TemplateSnapshot.class);
                templateRepository.deleteById(templateId);
                // template_version has ON DELETE CASCADE
                registryRepository.deleteById(templateId);
            } catch (Exception ignored) {
                // best-effort cleanup of the shared dev database
            }
        }
        createdTemplateIds.clear();
    }

    @Test
    @WithMockUser(username = "admin")
    @DisplayName("BUG-054: publish() after saveVersion(MINOR) yields consecutive semver; next saveVersion(MINOR) does not collide")
    void publishAfterSaveVersionMinor_thenSaveVersionMinor_doesNotCollide() {
        // given — fresh template; create() leaves the registry on the legacy draft scale (version=1)
        TemplateResponse created = newTemplate("publish-after-minor");
        // Draft scale (version_number=1) is a separate follow-up; the first versioned write
        // promotes the registry into semver space (10001).
        assertThat(created.version()).isEqualTo(1);

        // when — saveVersion(MINOR) → 1.1 (10001)
        SaveVersionResponse minor = templateService.saveVersion(
                created.id(), new SaveVersionRequest("MINOR", "first minor"));
        assertThat(minor.version().versionNumber()).isEqualTo(10_001);
        assertThat(minor.version().majorVersion()).isEqualTo(1);
        assertThat(minor.version().minorVersion()).isEqualTo(1);

        // when — publish must consume the semantic counter → 1.2 (10002), not max+1
        TemplateResponse published = templateService.publish(created.id());
        assertThat(published.version()).isEqualTo(10_002);
        assertThat(published.majorVersion()).isEqualTo(1);
        assertThat(published.minorVersion()).isEqualTo(2);

        // then — the regression: this used to throw DataIntegrityViolationException (500)
        SaveVersionResponse nextMinor = assertDoesNotThrow(() ->
                templateService.saveVersion(created.id(), new SaveVersionRequest("MINOR", "second minor")));

        assertThat(nextMinor.version().versionNumber()).isEqualTo(10_003);
        assertThat(nextMinor.version().majorVersion()).isEqualTo(1);
        assertThat(nextMinor.version().minorVersion()).isEqualTo(3);

        assertThat(versionNumbers(created.id())).containsExactly(10_003, 10_002, 10_001);
        assertNoDuplicateVersionNumbers(created.id());
    }

    @Test
    @WithMockUser(username = "admin")
    @DisplayName("BUG-054: publish() keeps registry version, majorVersion and minorVersion consistent")
    void publish_keepsRegistryVersionFieldsConsistent() {
        TemplateResponse created = newTemplate("registry-consistency");

        // historical failure state: saveVersion(MINOR) leaves registry at 1.1, then publish
        templateService.saveVersion(created.id(), new SaveVersionRequest("MINOR", "before publish"));

        TemplateResponse published = templateService.publish(created.id());

        // returned view
        assertThat(published.version()).isEqualTo(10_002);
        assertThat(published.majorVersion()).isEqualTo(1);
        assertThat(published.minorVersion()).isEqualTo(2);

        // reloaded from PostgreSQL — previously version=10002 while major/minor stayed 1/1
        TemplateResponse reloaded = templateService.getById(created.id());
        assertThat(reloaded.version()).isEqualTo(10_002);
        assertThat(reloaded.majorVersion()).isEqualTo(1);
        assertThat(reloaded.minorVersion()).isEqualTo(2);
        assertThat(reloaded.version())
                .isEqualTo(reloaded.majorVersion() * MAJOR_SCALE + reloaded.minorVersion());

        // template_version row carries the same semver components
        TemplateVersion row = versionRepository
                .findByTemplateIdAndVersionNumber(created.id(), 10_002)
                .orElseThrow();
        assertThat(row.getBumpType()).isEqualTo("PUBLISH");
        assertThat(row.getMajorVersion()).isEqualTo(1);
        assertThat(row.getMinorVersion()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = "admin")
    @DisplayName("BUG-054: two consecutive publishes increment minor (1.1, 1.2) and version rows are unique")
    void twoConsecutivePublishes_incrementMinor_andAreUnique() {
        TemplateResponse created = newTemplate("two-publishes");

        TemplateResponse first = templateService.publish(created.id());
        TemplateResponse second = templateService.publish(created.id());

        assertThat(first.version()).isEqualTo(10_001);
        assertThat(first.majorVersion()).isEqualTo(1);
        assertThat(first.minorVersion()).isEqualTo(1);

        assertThat(second.version()).isEqualTo(10_002);
        assertThat(second.majorVersion()).isEqualTo(1);
        assertThat(second.minorVersion()).isEqualTo(2);

        assertThat(versionNumbers(created.id())).containsExactly(10_002, 10_001);
        assertNoDuplicateVersionNumbers(created.id());

        // snapshots share the same version numbers and are distinct documents
        TemplateSnapshot latest = snapshotRepository
                .findFirstByTemplateIdOrderByVersionNumberDesc(created.id())
                .orElseThrow();
        assertThat(latest.getVersionNumber()).isEqualTo(10_002);
        assertThat(latest.getMajorVersion()).isEqualTo(1);
        assertThat(latest.getMinorVersion()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = "admin")
    @DisplayName("BUG-054: rollback() → saveVersion(MINOR) does not collide with the rollback version")
    void rollback_thenSaveVersion_doesNotCollide() {
        TemplateResponse created = newTemplate("rollback-then-save");

        int firstVersion = templateService
                .saveVersion(created.id(), new SaveVersionRequest("MINOR", "first minor"))
                .version().versionNumber();
        assertThat(firstVersion).isEqualTo(10_001);

        TemplateResponse published = templateService.publish(created.id());
        assertThat(published.version()).isEqualTo(10_002);

        // rollback to 10001 creates the next monotonic version 1.3 (10003)
        RollbackResponse rollback = templateService.rollback(created.id(), 10_001);
        assertThat(rollback.version().versionNumber()).isEqualTo(10_003);
        assertThat(rollback.version().majorVersion()).isEqualTo(1);
        assertThat(rollback.version().minorVersion()).isEqualTo(3);
        assertThat(rollback.version().bumpType()).isEqualTo("ROLLBACK");
        assertThat(rollback.version().rollbackFrom()).isEqualTo(10_001);

        // then — saveVersion must continue from the rollback, not reuse 10003
        SaveVersionResponse next = assertDoesNotThrow(() ->
                templateService.saveVersion(created.id(), new SaveVersionRequest("MINOR", "after rollback")));

        assertThat(next.version().versionNumber()).isEqualTo(10_004);
        assertThat(next.version().majorVersion()).isEqualTo(1);
        assertThat(next.version().minorVersion()).isEqualTo(4);

        assertThat(versionNumbers(created.id())).containsExactly(10_004, 10_003, 10_002, 10_001);
        assertNoDuplicateVersionNumbers(created.id());
    }

    @Test
    @WithMockUser(username = "admin")
    @DisplayName("BUG-054: MAJOR bump resets minor to 0 (2.0) and the following publish gives 2.1")
    void majorBump_resetsMinor_andPublishContinues() {
        TemplateResponse created = newTemplate("major-bump");

        SaveVersionResponse major = templateService.saveVersion(
                created.id(), new SaveVersionRequest("MAJOR", "breaking change"));

        assertThat(major.version().versionNumber()).isEqualTo(20_000);
        assertThat(major.version().majorVersion()).isEqualTo(2);
        assertThat(major.version().minorVersion()).isZero();
        assertThat(major.template().version()).isEqualTo(20_000);
        assertThat(major.template().majorVersion()).isEqualTo(2);
        assertThat(major.template().minorVersion()).isZero();

        TemplateResponse published = templateService.publish(created.id());
        assertThat(published.version()).isEqualTo(20_001);
        assertThat(published.majorVersion()).isEqualTo(2);
        assertThat(published.minorVersion()).isEqualTo(1);

        SaveVersionResponse afterPublish = assertDoesNotThrow(() ->
                templateService.saveVersion(created.id(), new SaveVersionRequest("MINOR", "post-major minor")));
        assertThat(afterPublish.version().versionNumber()).isEqualTo(20_002);
        assertThat(afterPublish.version().majorVersion()).isEqualTo(2);
        assertThat(afterPublish.version().minorVersion()).isEqualTo(2);

        assertThat(versionNumbers(created.id())).containsExactly(20_002, 20_001, 20_000);
        assertNoDuplicateVersionNumbers(created.id());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  BUG-054 W1 regression: stale pre-fix components must not poison allocation
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(username = "admin")
    @DisplayName("BUG-054 W1: stale registry components (10002 / 1.1) with matching version row → saveVersion and rollback stay collision-free")
    void stalePreFixComponents_saveVersionAndRollback_doNotCollide() {
        // given — a template whose registry still holds the pre-fix broken state:
        //   version=10002 but major=1, minor=1 (publish() wrote 10002 while components lagged).
        // Plus a template_version row with version_number=10002, simulating pre-fix data,
        // and a snapshot for 10001 so rollback(10001) has a restore target.
        UUID templateId = seedStaleRegistryAndVersionRow("w1-stale");
        snapshotRepository.save(staleSnapshot(templateId, 10_001, 1, 1));

        // when — saveVersion(MINOR) must recover, not explode on UNIQUE(template_id, version_number)
        SaveVersionResponse next = assertDoesNotThrow(() ->
                templateService.saveVersion(templateId, new SaveVersionRequest("MINOR", "after repair")));

        // then — 10002 is already taken, so the allocator advances to 1.3 (10003)
        assertThat(next.version().versionNumber()).isEqualTo(10_003);
        assertThat(next.version().majorVersion()).isEqualTo(1);
        assertThat(next.version().minorVersion()).isEqualTo(3);

        // and rollback(10001) must allocate the next free number (1.4 / 10004), not collide
        RollbackResponse rollback = assertDoesNotThrow(() -> templateService.rollback(templateId, 10_001));
        assertThat(rollback.version().versionNumber()).isEqualTo(10_004);
        assertThat(rollback.version().majorVersion()).isEqualTo(1);
        assertThat(rollback.version().minorVersion()).isEqualTo(4);
        assertThat(rollback.version().bumpType()).isEqualTo("ROLLBACK");
        assertThat(rollback.version().rollbackFrom()).isEqualTo(10_001);

        assertThat(versionNumbers(templateId)).containsExactly(10_004, 10_003, 10_002);
        assertNoDuplicateVersionNumbers(templateId);
        assertRegistryConsistentWithSemver(templateId);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  BUG-054: unpublish → publish cycles keep consuming the minor counter
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(username = "admin")
    @DisplayName("BUG-054: unpublish → publish increments minor and creates a new PUBLISH snapshot every cycle")
    void unpublishPublishCycles_createNewPublishSnapshotEachTime() {
        TemplateResponse created = newTemplate("unpublish-publish-cycles");

        // first publish → 1.1
        templateService.unpublish(created.id());
        TemplateResponse first = templateService.publish(created.id());
        assertThat(first.version()).isEqualTo(10_001);
        assertThat(first.majorVersion()).isEqualTo(1);
        assertThat(first.minorVersion()).isEqualTo(1);

        // second publish → 1.2
        templateService.unpublish(created.id());
        TemplateResponse second = templateService.publish(created.id());
        assertThat(second.version()).isEqualTo(10_002);
        assertThat(second.majorVersion()).isEqualTo(1);
        assertThat(second.minorVersion()).isEqualTo(2);

        // third publish → 1.3
        templateService.unpublish(created.id());
        TemplateResponse third = templateService.publish(created.id());
        assertThat(third.version()).isEqualTo(10_003);
        assertThat(third.majorVersion()).isEqualTo(1);
        assertThat(third.minorVersion()).isEqualTo(3);

        // every cycle created its own PUBLISH row + snapshot, all distinct
        List<TemplateVersion> publishRows = versionRepository
                .findByTemplateIdOrderByVersionNumberDesc(created.id())
                .stream()
                .filter(v -> "PUBLISH".equals(v.getBumpType()))
                .toList();
        assertThat(publishRows).hasSize(3);
        assertThat(publishRows).extracting(TemplateVersion::getVersionNumber)
                .containsExactly(10_003, 10_002, 10_001);
        assertThat(publishRows).extracting(TemplateVersion::getId).doesNotHaveDuplicates();

        for (TemplateVersion row : publishRows) {
            TemplateSnapshot snapshot = snapshotRepository
                    .findFirstByTemplateIdAndVersionNumberOrderByCreatedAtDesc(created.id(), row.getVersionNumber())
                    .orElseThrow();
            assertThat(snapshot.getId()).isEqualTo(row.getId());
            assertThat(snapshot.getMajorVersion()).isEqualTo(row.getMajorVersion());
            assertThat(snapshot.getMinorVersion()).isEqualTo(row.getMinorVersion());
        }

        assertNoDuplicateVersionNumbers(created.id());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  BUG-054: legacy label-only rows render as vN.0, never vN.N
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(username = "admin")
    @DisplayName("BUG-054: legacy version_number=3 with null components is labelled 3.0 in listVersions()")
    void legacyVersionWithNullComponents_isLabelledMajorZero() {
        // given — fresh template, then a hand-written legacy row (version_number < 10000, null components)
        TemplateResponse created = newTemplate("legacy-label");

        TemplateVersion legacy = new TemplateVersion();
        legacy.setTemplateId(created.id());
        legacy.setVersionNumber(3);
        legacy.setBumpType("LEGACY");
        legacy.setMajorVersion(null);
        legacy.setMinorVersion(null);
        legacy.setCreatedAt(OffsetDateTime.now());
        legacy = versionRepository.save(legacy);

        // when
        List<TemplateVersionDTO> versions = templateService.listVersions(created.id());

        // then — labelled v3.0, NOT v3.3
        TemplateVersionDTO dto = versions.stream()
                .filter(v -> v.versionNumber() == 3)
                .findFirst()
                .orElseThrow();
        assertThat(dto.id()).isEqualTo(legacy.getId());
        assertThat(dto.majorVersion()).isEqualTo(3);
        assertThat(dto.minorVersion()).isZero();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private TemplateResponse newTemplate(String label) {
        TemplateResponse template = templateService.create(
                null,
                new CreateTemplateRequest(
                        "BUG-054 " + label + " " + UUID.randomUUID(),
                        "version numbering integration test",
                        "test",
                        null));
        createdTemplateIds.add(template.id());
        return template;
    }

    /** Version numbers in descending order (repository ordering). */
    private List<Integer> versionNumbers(UUID templateId) {
        return versionRepository.findByTemplateIdOrderByVersionNumberDesc(templateId)
                .stream()
                .map(TemplateVersion::getVersionNumber)
                .toList();
    }

    /**
     * Seeds a template whose registry holds the pre-fix broken state
     * ({@code version=10002, major=1, minor=1}) plus a matching pre-fix
     * {@code template_version} row with {@code version_number=10002}.
     */
    private UUID seedStaleRegistryAndVersionRow(String label) {
        UUID templateId = seedStaleRegistry(label);

        TemplateVersion stale = new TemplateVersion();
        stale.setTemplateId(templateId);
        stale.setVersionNumber(10_002);
        stale.setMajorVersion(1);
        stale.setMinorVersion(1);
        stale.setBumpType("PUBLISH");
        stale.setCreatedAt(OffsetDateTime.now());
        versionRepository.save(stale);

        return templateId;
    }

    /** Seeds a template whose registry carries stale pre-fix components (10002 / 1.1). */
    private UUID seedStaleRegistry(String label) {
        UUID id = UUID.randomUUID();

        TemplateRegistry registry = new TemplateRegistry();
        registry.setId(id);
        registry.setName("BUG-054 stale " + label + " " + UUID.randomUUID());
        registry.setDescription("pre-fix inconsistent version components");
        registry.setCategory("test");
        registry.setVersion(10_002);
        registry.setMajorVersion(1);
        registry.setMinorVersion(1);
        registryRepository.save(registry);

        Template template = Template.builder()
                .id(id)
                .version(10_002)
                .build();
        templateRepository.save(template);

        createdTemplateIds.add(id);
        return id;
    }

    private void assertRegistryConsistentWithSemver(UUID templateId) {
        TemplateResponse reloaded = templateService.getById(templateId);
        assertThat(reloaded.version())
                .as("registry version must equal major*10000 + minor")
                .isEqualTo(reloaded.majorVersion() * MAJOR_SCALE + reloaded.minorVersion());
    }

    /** Minimal snapshot used as a rollback target (body is irrelevant for numbering). */
    private TemplateSnapshot staleSnapshot(UUID templateId, int versionNumber, int major, int minor) {
        return TemplateSnapshot.builder()
                .id(UUID.randomUUID())
                .templateId(templateId)
                .versionNumber(versionNumber)
                .majorVersion(major)
                .minorVersion(minor)
                .body(java.util.Map.of("type", "doc", "content", List.of()))
                .createdAt(java.time.Instant.now())
                .build();
    }

    /** DB-level guarantee: no {@code UNIQUE (template_id, version_number)} duplicates. */
    private void assertNoDuplicateVersionNumbers(UUID templateId) {
        Integer duplicateGroups = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM (
                            SELECT version_number
                            FROM template_version
                            WHERE template_id = ?
                            GROUP BY version_number
                            HAVING COUNT(*) > 1
                        ) duplicates
                        """,
                Integer.class, templateId);

        assertThat(duplicateGroups)
                .as("duplicate version_number rows for template %s", templateId)
                .isZero();
        assertThat(versionNumbers(templateId)).doesNotHaveDuplicates();
    }
}
