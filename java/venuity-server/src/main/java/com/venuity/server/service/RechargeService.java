package com.venuity.server.service;

import com.venuity.common.ErrorCode;
import com.venuity.common.MoneyUtil;
import com.venuity.server.api.dto.LegacyRechargeRequest;
import com.venuity.server.api.dto.LegacyRechargeResponse;
import com.venuity.server.api.dto.RechargeRequest;
import com.venuity.server.api.dto.RechargeResponse;
import com.venuity.server.api.error.ApiException;
import com.venuity.server.domain.Customer;
import com.venuity.server.domain.WalletTransaction;
import com.venuity.server.repo.WalletTransactionRepository;
import com.venuity.server.security.AuthPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RechargeService {

    private static final String ENDPOINT = "POST /api/customers/{id}/recharge";

    private final CustomerService customers;
    private final WalletService wallet;
    private final SettingsService settings;
    private final IdempotencyService idempotency;
    private final WalletTransactionRepository ledger;

    public RechargeService(CustomerService customers, WalletService wallet, SettingsService settings,
                           IdempotencyService idempotency, WalletTransactionRepository ledger) {
        this.customers = customers;
        this.wallet = wallet;
        this.settings = settings;
        this.idempotency = idempotency;
        this.ledger = ledger;
    }

    @Transactional
    public LegacyRechargeResponse legacyRecharge(LegacyRechargeRequest request, AuthPrincipal principal) {
        String publicId = request.customerId() == null ? "" : request.customerId().trim();
        long customerId = customers.requireIdByPublicId(publicId);
        BigDecimal amount = parseAmount(request.amount());
        WalletTransaction transaction = wallet.topUp(customerId, amount, principal.id(), "");
        return new LegacyRechargeResponse(true,
                customers.legacy(transaction.getCustomer(), settings.zone()));
    }

    @Transactional
    public RechargeResponse recharge(String publicId, RechargeRequest request, String idemKey,
                                     AuthPrincipal principal) {
        Map<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("customer", publicId);
        fingerprint.put("amount", MoneyUtil.plain(request.amount()));
        fingerprint.put("note", request.note());
        return idempotency.execute(idemKey, ENDPOINT, principal.id(), fingerprint, RechargeResponse.class,
                () -> perform(publicId, request, principal));
    }

    private RechargeResponse perform(String publicId, RechargeRequest request, AuthPrincipal principal) {
        long customerId = customers.requireIdByPublicId(publicId);
        WalletTransaction transaction = wallet.topUp(customerId, request.amount(), principal.id(),
                request.note() == null ? "" : request.note());
        Customer customer = transaction.getCustomer();
        return new RechargeResponse(customers.modern(customer), MoneyUtil.normalize(transaction.getAmount()),
                MoneyUtil.normalize(customer.getWalletBalance()), transaction.getId());
    }

    private BigDecimal parseAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            throw amountValidation("This field is required.");
        }
        BigDecimal amount;
        try {
            amount = MoneyUtil.normalize(new BigDecimal(raw.trim()));
        } catch (NumberFormatException exception) {
            throw amountValidation("A valid number is required.");
        }
        if (amount.compareTo(MoneyUtil.MIN_RECHARGE) < 0) {
            throw amountValidation("Ensure this value is greater than or equal to 0.01.");
        }
        if (amount.compareTo(MoneyUtil.MAX_AMOUNT) > 0) {
            throw amountValidation("Ensure this value is less than or equal to 99999999.99.");
        }
        return amount;
    }

    private ApiException amountValidation(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Validation failed")
                .withLegacyDetail(Map.of("amount", List.of(message)));
    }
}
