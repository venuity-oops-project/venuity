package com.venuity.server.service;

import com.venuity.common.ErrorCode;
import com.venuity.common.MoneyUtil;
import com.venuity.server.api.dto.CheckInRequest;
import com.venuity.server.api.dto.CheckInResponse;
import com.venuity.server.api.dto.LegacyCheckInRequest;
import com.venuity.server.api.dto.LegacyCheckInResponse;
import com.venuity.server.api.error.ApiException;
import com.venuity.server.domain.CheckIn;
import com.venuity.server.domain.Customer;
import com.venuity.server.domain.TxType;
import com.venuity.server.domain.WalletTransaction;
import com.venuity.server.repo.CheckInRepository;
import com.venuity.server.security.AuthPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class CheckInService {

    private static final String ENDPOINT = "POST /api/checkins";

    private final CheckInRepository checkIns;
    private final CustomerService customers;
    private final WalletService wallet;
    private final SettingsService settings;
    private final IdempotencyService idempotency;

    public CheckInService(CheckInRepository checkIns, CustomerService customers, WalletService wallet,
                          SettingsService settings, IdempotencyService idempotency) {
        this.checkIns = checkIns;
        this.customers = customers;
        this.wallet = wallet;
        this.settings = settings;
        this.idempotency = idempotency;
    }

    @Transactional
    public CheckInResponse checkIn(CheckInRequest request, String idemKey, AuthPrincipal principal) {
        return idempotency.execute(idemKey, ENDPOINT, principal.id(), request, CheckInResponse.class,
                () -> perform(customers.requireIdByCodeForCheckIn(request.code()), principal).response());
    }

    @Transactional
    public LegacyCheckInResponse legacyCheckIn(LegacyCheckInRequest request, AuthPrincipal principal) {
        if ((request.customerId() == null || request.customerId().isBlank())
                && (request.qrId() == null || request.qrId().isBlank())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Validation failed")
                    .withLegacyDetail(Map.of("non_field_errors",
                            List.of("Either customer_id or qr_id must be provided.")));
        }
        String identifier = request.qrId() != null && !request.qrId().isBlank()
                ? request.qrId().trim()
                : request.customerId().trim();
        long customerId = request.qrId() != null && !request.qrId().isBlank()
                ? customers.requireIdByCodeForCheckIn(identifier)
                : customers.requireIdByPublicIdForCheckIn(identifier);
        Outcome outcome = perform(customerId, principal);
        return new LegacyCheckInResponse(true, customers.legacy(outcome.customer(), settings.zone()),
                MoneyUtil.plain(outcome.response().feeCharged()));
    }

    private Outcome perform(long customerId, AuthPrincipal principal) {
        BigDecimal fee = MoneyUtil.normalize(settings.current().getCheckinFee());
        WalletTransaction transaction = wallet.debit(customerId, fee, TxType.CHECKIN,
                principal.id(), "");
        Customer customer = transaction.getCustomer();
        CheckIn checkIn = checkIns.save(CheckIn.create(customer, transaction, fee));
        return new Outcome(customer, new CheckInResponse(customer.getName(), fee,
                MoneyUtil.normalize(customer.getWalletBalance()), checkIn.getCheckedInAt(),
                customers.modern(customer)));
    }

    private record Outcome(Customer customer, CheckInResponse response) {
    }
}
