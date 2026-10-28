package com.venuity.server.web;

import com.venuity.server.api.dto.PageResponse;
import com.venuity.server.api.dto.StatsResponse;
import com.venuity.server.api.dto.TransactionResponse;
import com.venuity.server.service.TransactionQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionQueryService transactions;

    public TransactionController(TransactionQueryService transactions) {
        this.transactions = transactions;
    }

    @GetMapping("/stats")
    public StatsResponse stats() {
        return transactions.modernStats();
    }

    @GetMapping
    public PageResponse<TransactionResponse> list(
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return transactions.modernPage(customerId, type, from, to, page, size);
    }
}
