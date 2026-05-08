package com.docforge.audit;

import com.docforge.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    /**
     * Writes audit event asynchronously in a new transaction so that
     * a failure here never rolls back the calling business transaction.
     */
    @Async
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String login, String entityType, UUID entityId, String action, String details) {
        try {
            AuditLog entry = new AuditLog();
            entry.setEntityType(entityType);
            entry.setEntityId(entityId);
            entry.setAction(action);
            entry.setDetails(details);

            if (login != null) {
                userRepository.findByLogin(login)
                        .ifPresent(u -> entry.setUserId(u.getId()));
            }

            auditLogRepository.save(entry);
        } catch (Exception e) {
            // Audit must never break business operations
            log.error("Failed to write audit log [{} {} {}]: {}", action, entityType, entityId, e.getMessage());
        }
    }
}
