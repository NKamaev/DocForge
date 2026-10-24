package com.docforge;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Helpers for the asynchronous audit writer.
 *
 * <p>{@code AuditServiceImpl.log} is annotated {@code @Async}: the audit row is written
 * in a separate thread and in a separate transaction ({@code REQUIRES_NEW}), so it lands
 * in the database <em>after</em> the business call has returned. A test cleanup that
 * deletes the users right after the test would race with that write and fail with an
 * {@code audit_log → users} FK violation.</p>
 *
 * <p><b>Why a plain “wait until the count stabilises” is not enough.</b>
 * {@link #awaitQuiescence(JdbcTemplate, String, Object...)} can only observe what is
 * <em>already</em> in the database: if the {@code @Async} task has not been scheduled
 * yet, the count is a stable {@code 0} and the helper returns immediately — the row
 * lands a moment later and the FK violation is still possible. The count heuristic is
 * therefore used only as a best-effort warm-up.</p>
 *
 * <p><b>The deterministic way to clean up a user</b> is
 * {@link #deleteUserWithAuditRows(JdbcTemplate, UUID)}: it deletes the audit rows and
 * the user in a small retry loop and tolerates (and finally reports) an FK violation
 * that happens because a late audit row references the user. Every cleanup that removes
 * a test user goes through this helper, so all of them benefit from the fix.</p>
 */
public final class AuditTestSupport {

    /** How long a row count must stay unchanged to be considered stable. */
    private static final int REQUIRED_STABLE_READS = 4;

    /** Pause between two readings. */
    private static final long POLL_INTERVAL_MS = 50L;

    /** Maximum wait for the asynchronous writer. */
    private static final long TIMEOUT_MS = 20_000L;

    /** How many times the deletion of a user is retried after an FK violation. */
    private static final int MAX_DELETE_ATTEMPTS = 20;

    /** Pause between two deletion attempts. */
    private static final long DELETE_RETRY_INTERVAL_MS = 100L;

    private AuditTestSupport() {
    }

    /**
     * Waits until the number of rows returned by {@code countSql} stops changing.
     *
     * <p><b>Heuristic only.</b> The wait ends as soon as the count repeats
     * {@link #REQUIRED_STABLE_READS} times — which is also the case when the
     * asynchronous writer has not been scheduled yet (the count is a stable {@code 0}).
     * Use {@link #deleteUserWithAuditRows(JdbcTemplate, UUID)} to remove a user
     * deterministically.</p>
     *
     * @param jdbcTemplate template to query with
     * @param countSql     query returning a single numeric count
     * @param args         arguments of the query
     * @throws AssertionError if the count does not stabilise within the timeout
     */
    public static void awaitQuiescence(JdbcTemplate jdbcTemplate, String countSql, Object... args) {
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        Long previous = null;
        int stableReads = 0;
        while (System.currentTimeMillis() < deadline) {
            Long current = jdbcTemplate.queryForObject(countSql, Long.class, args);
            if (current != null && current.equals(previous)) {
                stableReads++;
                if (stableReads >= REQUIRED_STABLE_READS) {
                    return;
                }
            } else {
                stableReads = 0;
            }
            previous = current;
            sleep(POLL_INTERVAL_MS);
        }
        throw new AssertionError(
                "Асинхронная запись аудита не stabilизировалась за " + TIMEOUT_MS + " мс: " + countSql);
    }

    /**
     * Polls until the audit row for the given entity/action appears and returns its
     * {@code details} column as text ({@code details::text}).
     *
     * <p>Unlike {@link #awaitQuiescence(JdbcTemplate, String, Object...)} (a heuristic
     * that only observes an already stable count) this helper waits for the row itself,
     * so it can be used to assert <em>what</em> the asynchronous writer has written
     * (see BUG-071: {@code details} must be valid JSON in the {@code jsonb} column).</p>
     *
     * <p>The rows are ordered by {@code id DESC}: when several rows match (e.g. a test
     * that writes the same entity/action twice) the newest one is returned deterministically
     * instead of relying on unspecified row order.</p>
     *
     * @param jdbcTemplate template to query with
     * @param entityType   {@code audit_log.entity_type}, e.g. {@code "TEMPLATE"}
     * @param entityId     {@code audit_log.entity_id}
     * @param action       {@code audit_log.action}, e.g. {@code "SAVE_VERSION"}
     * @return the {@code details} column rendered as text
     * @throws AssertionError if the row does not appear within the timeout
     */
    public static String awaitAuditDetails(JdbcTemplate jdbcTemplate, String entityType, UUID entityId, String action) {
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            List<String> details = jdbcTemplate.queryForList(
                    "SELECT details::text FROM audit_log "
                            + "WHERE entity_type = ? AND entity_id = ? AND action = ? "
                            + "ORDER BY id DESC",
                    String.class, entityType, entityId, action);
            if (!details.isEmpty()) {
                return details.get(0);
            }
            sleep(POLL_INTERVAL_MS);
        }
        throw new AssertionError(
                "Запись аудита " + entityType + "/" + action + " для " + entityId
                        + " не появилась за " + TIMEOUT_MS + " мс");
    }

    /**
     * Deletes a test user together with its audit rows — deterministically.
     *
     * <p>The audit writer is asynchronous, so a row referencing the user can land
     * <em>between</em> the deletion of the audit rows and the deletion of the user. The
     * FK {@code audit_log → users} then rejects the deletion of the user. This helper
     * retries the whole pair of statements a few times: the late row is removed by the
     * next round and the user is deleted successfully.</p>
     *
     * <p>A late row that lands <em>after</em> the user is gone is harmless: the insert
     * fails with an FK violation inside {@code AuditServiceImpl}, which swallows it by
     * design (“audit must never break business operations”).</p>
     *
     * @param jdbcTemplate template to clean up with
     * @param userId       id of the user to remove
     * @throws AssertionError if the user cannot be removed within the retry budget
     */
    public static void deleteUserWithAuditRows(JdbcTemplate jdbcTemplate, UUID userId) {
        if (userId == null) {
            return;
        }
        DataIntegrityViolationException lastViolation = null;
        for (int attempt = 1; attempt <= MAX_DELETE_ATTEMPTS; attempt++) {
            // Сначала аудит (он ссылается на пользователя), потом сам пользователь.
            jdbcTemplate.update("DELETE FROM audit_log WHERE user_id = ?", userId);
            try {
                jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
                return;
            } catch (DataIntegrityViolationException e) {
                // Поздняя строка аудита успела проскочить между удалениями — повторяем.
                lastViolation = e;
                sleep(DELETE_RETRY_INTERVAL_MS);
            }
        }
        long auditRows = countAuditRows(jdbcTemplate, userId);
        throw new AssertionError(
                "Не удалось удалить пользователя " + userId + " за " + MAX_DELETE_ATTEMPTS + " попыток: "
                        + "осталось строк аудита = " + auditRows, lastViolation);
    }

    /** Number of audit rows that reference the given user. */
    private static long countAuditRows(JdbcTemplate jdbcTemplate, UUID userId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE user_id = ?", Long.class, userId);
        return count == null ? 0L : count;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
