package com.venuity.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "check_in")
public class CheckIn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, unique = true)
    private WalletTransaction transaction;

    @Column(name = "fee_charged", nullable = false, precision = 10, scale = 2)
    private BigDecimal feeCharged;

    @Column(name = "checked_in_at", updatable = false)
    private Instant checkedInAt;

    protected CheckIn() {
    }

    public static CheckIn create(Customer customer, WalletTransaction transaction, BigDecimal feeCharged) {
        CheckIn checkIn = new CheckIn();
        checkIn.customer = customer;
        checkIn.transaction = transaction;
        checkIn.feeCharged = feeCharged;
        return checkIn;
    }

    @PrePersist
    void timestamp() {
        if (checkedInAt == null) {
            checkedInAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public WalletTransaction getTransaction() {
        return transaction;
    }

    public BigDecimal getFeeCharged() {
        return feeCharged;
    }

    public Instant getCheckedInAt() {
        return checkedInAt;
    }
}



