package com.example.officebooking.audit;

import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private final AuditLogRepository repo;
    private final Clock clock;

    public AuditService(AuditLogRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    /** Пишет запись в той же транзакции, что и бизнес-операция: действие и аудит атомарны. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void log(Long actorUserId, String action, String entityType, Object entityId, String details) {
        AuditLog a = new AuditLog();
        a.setActorUserId(actorUserId);
        a.setAction(action);
        a.setEntityType(entityType);
        a.setEntityId(entityId == null ? null : String.valueOf(entityId));
        a.setDetails(details);
        a.setCreatedAt(Instant.now(clock));
        repo.save(a);
    }
}
