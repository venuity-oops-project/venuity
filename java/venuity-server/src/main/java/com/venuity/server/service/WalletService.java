package com.venuity.server.service;

import com.venuity.common.ErrorCode;
import com.venuity.common.MoneyUtil;
import com.venuity.server.api.error.ApiException;
import com.venuity.server.domain.AppUser;
import com.venuity.server.domain.Customer;
import com.venuity.server.domain.TxType;
import com.venuity.server.domain.WalletTransaction;
import com.venuity.server.repo.AppUserRepository;
import com.venuity.server.repo.CustomerRepository;
import com.venuity.server.repo.WalletTransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class WalletService {

    private final CustomerRepository customers;
    private final WalletTransactionRepository ledger;
    private final AppUserRepository users;

    public WalletService(CustomerRepository customers, WalletTransactionRepository ledger,
                         AppUserRepository users) {
        this.customers = customers;
        this.ledger = ledger;
        this.users = users;
    }

    @Transactional
    public WalletTransaction topUp(long customerId, BigDecimal amount, Long userId, String note) {
        BigDecimal normalized = MoneyUtil.normalize(amount);
        if (normalized == null || normalized.compareTo(MoneyUtil.MIN_RECHARGE) < 0) {
            throw amountOutOfRange();
        }
        if (normalized.compareTo(MoneyUtil.MAX_AMOUNT) > 0) {
            throw amountOutOfRange();
        }
        Customer customer = lock(customerId);
        ensureActive(customer);
        BigDecimal newBalance = MoneyUtil.normalize(customer.getWalletBalance().add(normalized));
        customer.setWalletBalance(newBalance);
        customers.save(customer);
        return ledger.save(WalletTransaction.create(customer, TxType.TOPUP, normalized, newBalance,
                note, user(userId)));
    }

    @Transactional
    public WalletTransaction debit(long customerId, BigDecimal fee, TxType type, Long userId, String note) {
        BigDecimal normalized = MoneyUtil.normalize(fee);
        if (normalized == null || normalized.signum() < 0) {
            throw amountOutOfRange();
        }
        Customer customer = lock(customerId);
        ensureActive(customer);
        BigDecimal newBalance = MoneyUtil.normalize(customer.getWalletBalance().subtract(normalized));
        if (newBalance.signum() < 0) {
            throw insufficient(customer, normalized);
        }
        customer.setWalletBalance(newBalance);
        customers.save(customer);
        return ledger.save(WalletTransaction.create(customer, type, normalized.negate(), newBalance,
                note, user(userId)));
    }

    @Transactional(readOnly = true)
    public Customer lock(long customerId) {
        return customers.findByIdForUpdate(customerId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Customer not found")
                        .withLegacyError("Customer not found."));
    }

    private void ensureActive(Customer customer) {
        if (!customer.isActive()) {
            throw new ApiException(ErrorCode.CUSTOMER_INACTIVE, HttpStatus.CONFLICT,
                    "Customer is inactive");
        }
    }

    private ApiException insufficient(Customer customer, BigDecimal fee) {
        return new ApiException(ErrorCode.INSUFFICIENT_BALANCE, HttpStatus.PAYMENT_REQUIRED,
                "Insufficient balance")
                .withModernStatus(HttpStatus.CONFLICT)
                .withLegacyError("Insufficient balance. Required: " + MoneyUtil.plain(fee)
                        + ", Available: " + MoneyUtil.plain(customer.getWalletBalance()))
                .withoutLegacyCode();
    }

    private AppUser user(Long userId) {
        if (userId == null) {
            return null;
        }
        return users.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED,
                        "User not found"));
    }

    private ApiException amountOutOfRange() {
        return new ApiException(ErrorCode.AMOUNT_OUT_OF_RANGE, HttpStatus.BAD_REQUEST,
                "Amount out of range")
                .withLegacyError("Validation failed.")
                .withLegacyDetail(java.util.Map.of("amount",
                        java.util.List.of("Ensure this value is between 0.01 and 99999999.99.")));
    }
}
