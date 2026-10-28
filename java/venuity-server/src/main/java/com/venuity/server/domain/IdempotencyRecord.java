package com.venuity.server.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "idempotency_record")
public class IdempotencyRecord {

    @EmbeddedId
    private IdempotencyKey id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "response_code")
    private Integer responseCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body")
    private JsonNode responseBody;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    protected IdempotencyRecord() {
    }

    public static IdempotencyRecord create(IdempotencyKey id, String requestHash) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.id = id;
        record.requestHash = requestHash;
        return record;
    }

    @PrePersist
    void timestamp() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public IdempotencyKey getId() {
        return id;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public Integer getResponseCode() {
        return responseCode;
    }

    public JsonNode getResponseBody() {
        return responseBody;
    }

    public void complete(int responseCode, JsonNode responseBody) {
        this.responseCode = responseCode;
        this.responseBody = responseBody;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}



