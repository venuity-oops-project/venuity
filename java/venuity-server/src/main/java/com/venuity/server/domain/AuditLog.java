package com.venuity.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false, insertable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_username", nullable = false, length = 150)
    private String actorUsername;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(name = "target_user_id")
    private Long targetUserId;

    @Column(name = "target_username", length = 150)
    private String targetUsername;

    @Column(nullable = false, length = 45)
    private String ip;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(nullable = false)
    private boolean success;

    protected AuditLog() {
    }

    public static AuditLog record(Long actorUserId, String actorUsername, String action,
                                  Long targetUserId, String targetUsername, String ip,
                                  String details, boolean success) {
        AuditLog log = new AuditLog();
        log.actorUserId = actorUserId;
        log.actorUsername = actorUsername != null ? actorUsername : "SYSTEM";
        log.action = action;
        log.targetUserId = targetUserId;
        log.targetUsername = targetUsername;
        log.ip = ip != null ? ip : "";
        log.details = details;
        log.success = success;
        return log;
    }

    public Long getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public String getAction() {
        return action;
    }

    public Long getTargetUserId() {
        return targetUserId;
    }

    public String getTargetUsername() {
        return targetUsername;
    }

    public String getIp() {
        return ip;
    }

    public String getDetails() {
        return details;
    }

    public boolean isSuccess() {
        return success;
    }
}
