package com.docforge.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Отдельный компонент записи в {@code audit_log}.
 *
 * <p>Транзакция открывается <b>здесь</b>, а не в {@link AuditServiceImpl}:
 * если бы {@code try/catch} жил внутри {@code @Transactional}-метода, то
 * упавшая вставка пометила бы транзакцию rollback-only, и {@code commit}
 * всё равно бросил бы {@code UnexpectedRollbackException} — запись потерялась
 * бы, а наверх ушла бы неверная ошибка (BUG-071).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogWriter {

    private final AuditLogRepository auditLogRepository;

    /**
     * Сохраняет запись аудита в отдельной транзакции
     * ({@code REQUIRES_NEW}): сбой аудита не влияет на бизнес-транзакцию.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(AuditLog entry) {
        auditLogRepository.save(entry);
    }
}
