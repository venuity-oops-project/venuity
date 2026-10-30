package com.venuity.server.service;

import com.venuity.server.repo.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class ScheduledCleanupService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledCleanupService.class);

    private final UserSessionService sessionService;
    private final AuditLogRepository auditLogRepo;
    private final int sessionRetentionDays;
    private final int auditRetentionDays;

    public ScheduledCleanupService(UserSessionService sessionService,
                                  AuditLogRepository auditLogRepo,
                                  @Value("${venuity.cleanup.session-retention-days:30}") int sessionRetentionDays,
                                  @Value("${venuity.cleanup.audit-retention-days:90}") int auditRetentionDays) {
        this.sessionService = sessionService;
        this.auditLogRepo = auditLogRepo;
        this.sessionRetentionDays = sessionRetentionDays;
        this.auditRetentionDays = auditRetentionDays;
    }

    @Scheduled(cron = "${venuity.cleanup.cron:0 0 3 * * *}")
    @Transactional
    public void runCleanup() {
        log.info("[CLEANUP] Starting scheduled maintenance task...");
        try {
            int cleanedSessions = sessionService.cleanupExpiredSessions(sessionRetentionDays);
            log.info("[CLEANUP] Removed {} expired/revoked session records older than {} days.",
                    cleanedSessions, sessionRetentionDays);
        } catch (Exception e) {
            log.error("[CLEANUP ERROR] Failed to clean up user sessions: {}", e.getMessage());
        }

        try {
            Instant auditCutoff = Instant.now().minus(auditRetentionDays, ChronoUnit.DAYS);
            int cleanedAudits = auditLogRepo.deleteOlderThan(auditCutoff);
            log.info("[CLEANUP] Purged {} audit log records older than {} days.",
                    cleanedAudits, auditRetentionDays);
        } catch (Exception e) {
            log.error("[CLEANUP ERROR] Failed to clean up audit logs: {}", e.getMessage());
        }
    }
}
