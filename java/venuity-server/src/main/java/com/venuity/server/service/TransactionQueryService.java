package com.venuity.server.service;

import com.venuity.common.MoneyUtil;
import com.venuity.server.api.dto.LegacyPageResponse;
import com.venuity.server.api.dto.LegacyStatsResponse;
import com.venuity.server.api.dto.LegacyTransactionResponse;
import com.venuity.server.api.dto.PageResponse;
import com.venuity.server.api.dto.StatsResponse;
import com.venuity.server.api.dto.TransactionResponse;
import com.venuity.server.domain.TxType;
import com.venuity.server.domain.WalletTransaction;
import com.venuity.server.repo.CustomerRepository;
import com.venuity.server.repo.WalletTransactionRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
public class TransactionQueryService {

    private final WalletTransactionRepository ledger;
    private final CustomerRepository customers;
    private final SettingsService settings;

    public TransactionQueryService(WalletTransactionRepository ledger, CustomerRepository customers,
                                   SettingsService settings) {
        this.ledger = ledger;
        this.customers = customers;
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public LegacyPageResponse<LegacyTransactionResponse> legacyPage(String type, int page, int pageSize,
                                                                    HttpServletRequest request) {
        int safeSize = Math.max(1, Math.min(pageSize, 100));
        int safePage = Math.max(page - 1, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(
                Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<WalletTransaction> result = ledger.findAll(specification(type, null, null, null), pageable);
        ZoneId zone = settings.zone();
        List<LegacyTransactionResponse> rows = result.getContent().stream()
                .map(transaction -> legacy(transaction, zone))
                .toList();
        return legacyPage(result, rows, request);
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> modernPage(String customerPublicId, String type,
                                                        Instant from, Instant to, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, 100));
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(
                Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<WalletTransaction> result = ledger.findAll(
                specification(type, customerPublicId, from, to), pageable);
        return PageResponse.of(result.map(this::modern));
    }

    @Transactional(readOnly = true)
    public LegacyStatsResponse legacyStats() {
        ZoneId zone = settings.zone();
        BigDecimal revenue = MoneyUtil.normalize(ledger.sumByType(TxType.TOPUP)
                .add(ledger.sumByType(TxType.OPENING_BALANCE)));
        List<LegacyTransactionResponse> recent = ledger.findTop10ByOrderByCreatedAtDescIdDesc().stream()
                .map(transaction -> legacy(transaction, zone))
                .toList();
        return new LegacyStatsResponse(customers.countByActiveTrue(), revenue, recent);
    }

    @Transactional(readOnly = true)
    public StatsResponse modernStats() {
        BigDecimal revenue = MoneyUtil.normalize(ledger.sumByType(TxType.TOPUP)
                .add(ledger.sumByType(TxType.OPENING_BALANCE)));
        List<TransactionResponse> recent = ledger.findTop10ByOrderByCreatedAtDescIdDesc().stream()
                .map(this::modern)
                .toList();
        return new StatsResponse(customers.countByActiveTrue(), revenue, recent);
    }

    public LegacyTransactionResponse legacy(WalletTransaction transaction, ZoneId zone) {
        return new LegacyTransactionResponse(transaction.getId(), transaction.getCustomer().getPublicId(),
                transaction.getCustomer().getName(), legacyType(transaction.getType()),
                MoneyUtil.plain(transaction.getAmount().abs()),
                transaction.getNote() == null ? "" : transaction.getNote(),
                TimeUtil.legacy(transaction.getCreatedAt(), zone));
    }

    public TransactionResponse modern(WalletTransaction transaction) {
        return new TransactionResponse(transaction.getId(), transaction.getCustomer().getPublicId(),
                transaction.getCustomer().getName(), transaction.getType().name(),
                MoneyUtil.normalize(transaction.getAmount()),
                MoneyUtil.normalize(transaction.getBalanceAfter()),
                transaction.getNote(), transaction.getCreatedAt());
    }

    private Specification<WalletTransaction> specification(String type, String customerPublicId,
                                                            Instant from, Instant to) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (type != null && !type.isBlank()) {
                String normalized = type.trim().toLowerCase();
                if (normalized.equals("recharge")) {
                    predicates.add(root.get("type").in(TxType.TOPUP, TxType.OPENING_BALANCE));
                } else if (normalized.equals("checkin")) {
                    predicates.add(builder.equal(root.get("type"), TxType.CHECKIN));
                } else {
                    try {
                        predicates.add(builder.equal(root.get("type"), TxType.valueOf(normalized.toUpperCase())));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            if (customerPublicId != null && !customerPublicId.isBlank()) {
                predicates.add(builder.equal(root.get("customer").get("publicId"), customerPublicId));
            }
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(builder.lessThan(root.get("createdAt"), to));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private String legacyType(TxType type) {
        return switch (type) {
            case TOPUP, OPENING_BALANCE -> "recharge";
            case CHECKIN -> "checkin";
            case ADJUSTMENT -> "adjustment";
            case REFUND -> "refund";
        };
    }

    private LegacyPageResponse<LegacyTransactionResponse> legacyPage(Page<WalletTransaction> page,
                                                                     List<LegacyTransactionResponse> rows,
                                                                     HttpServletRequest request) {
        int totalPages = Math.max(1, (int) Math.ceil(page.getTotalElements() / (double) page.getSize()));
        int number = page.getNumber();
        String next = (number + 1) < totalPages
                ? ServletUriComponentsBuilder.fromRequest(request)
                .replaceQueryParam("page", number + 2)
                .build()
                .toUriString()
                : null;
        String previous = number > 0
                ? ServletUriComponentsBuilder.fromRequest(request)
                .replaceQueryParam("page", number)
                .build()
                .toUriString()
                : null;
        return new LegacyPageResponse<>(page.getTotalElements(), totalPages, next, previous, rows);
    }
}
