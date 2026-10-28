package com.venuity.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class IdempotencyKey implements Serializable {

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "idem_key", nullable = false, length = 36)
    private String idemKey;

    @Column(name = "endpoint", nullable = false, length = 100)
    private String endpoint;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    protected IdempotencyKey() {
    }

    public IdempotencyKey(String idemKey, String endpoint, Long userId) {
        this.idemKey = idemKey;
        this.endpoint = endpoint;
        this.userId = userId;
    }

    public String getIdemKey() {
        return idemKey;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public Long getUserId() {
        return userId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof IdempotencyKey key)) {
            return false;
        }
        return Objects.equals(idemKey, key.idemKey)
                && Objects.equals(endpoint, key.endpoint)
                && Objects.equals(userId, key.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idemKey, endpoint, userId);
    }
}
