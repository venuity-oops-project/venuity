package com.venuity.server.service;

import com.venuity.server.domain.AuditLog;
import com.venuity.server.repo.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogs;

    public AuditService(AuditLogRepository auditLogs) {
        this.auditLogs = auditLogs;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long actorUserId, String actorUsername, String action,
                       Long targetUserId, String targetUsername, String ip,
                       String details, boolean success) {
        try {
            AuditLog entry = AuditLog.record(actorUserId, actorUsername, action,
                    targetUserId, targetUsername, ip, details, success);
            auditLogs.save(entry);
            log.info("[AUDIT] action={} actor={} target={} success={} ip={}",
                    action, actorUsername, targetUsername, success, ip);
        } catch (Exception e) {
            log.error("[AUDIT ERROR] Failed to record audit log for action {}: {}", action, e.getMessage());
        }
    }
}
