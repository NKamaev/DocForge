package com.docforge.audit;

import com.docforge.AbstractIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test: AuditLog.details persists as jsonb without error.
 * Covers BUG-001 fix: @JdbcTypeCode(SqlTypes.JSON) added to AuditLog.details.
 */
@Transactional
class AuditServiceImplIT extends AbstractIT {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    @DisplayName("AuditLog.details saves and loads JSON string via jsonb column without error")
    void details_savesAndLoadsJsonb() {
        // Arrange
        AuditLog log = new AuditLog();
        log.setEntityType("TEMPLATE");
        log.setEntityId(UUID.randomUUID());
        log.setAction("PUBLISH");
        log.setDetails("{\"key\":\"value\",\"version\":1}");

        // Act — was failing with "column details is of type jsonb but expression is of type varchar"
        AuditLog saved = auditLogRepository.saveAndFlush(log);

        // Assert
        assertThat(saved.getId()).isNotNull();

        AuditLog loaded = auditLogRepository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getDetails()).isNotNull();
        assertThat(loaded.getDetails()).contains("\"key\"");
        assertThat(loaded.getDetails()).contains("\"value\"");
    }

    @Test
    @DisplayName("AuditLog.details can be null (optional field)")
    void details_acceptsNull() {
        AuditLog log = new AuditLog();
        log.setEntityType("DOCUMENT");
        log.setEntityId(UUID.randomUUID());
        log.setAction("CREATE");
        log.setDetails(null);

        AuditLog saved = auditLogRepository.saveAndFlush(log);
        assertThat(saved.getId()).isNotNull();

        AuditLog loaded = auditLogRepository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getDetails()).isNull();
    }
}
