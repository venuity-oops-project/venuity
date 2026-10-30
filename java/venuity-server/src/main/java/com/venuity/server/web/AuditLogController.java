package com.venuity.server.web;

import com.venuity.server.api.dto.AuditLogResponse;
import com.venuity.server.domain.AuditLog;
import com.venuity.server.repo.AuditLogRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.StringWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/audit-log")
@PreAuthorize("hasRole('ADMIN')")
public class AuditLogController {

    private final AuditLogRepository auditLogRepo;

    public AuditLogController(AuditLogRepository auditLogRepo) {
        this.auditLogRepo = auditLogRepo;
    }

    @GetMapping
    public Page<AuditLogResponse> list(
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String target,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Specification<AuditLog> spec = buildSpec(actor, target, action, success, from, to);
        return auditLogRepo.findAll(spec, PageRequest.of(page, Math.min(size, 100), Sort.by("occurredAt").descending()))
                .map(this::toResponse);
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<String> exportCsv(
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String target,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        Specification<AuditLog> spec = buildSpec(actor, target, action, success, from, to);
        List<AuditLog> logs = auditLogRepo.findAll(spec, Sort.by("occurredAt").descending());

        StringWriter writer = new StringWriter();
        writer.write("ID,Timestamp,Actor,Action,Target,IP,Success,Details\n");
        for (AuditLog a : logs) {
            writer.write(String.format("\"%d\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                    a.getId(),
                    a.getOccurredAt(),
                    escapeCsv(a.getActorUsername()),
                    escapeCsv(a.getAction()),
                    escapeCsv(a.getTargetUsername()),
                    escapeCsv(a.getIp()),
                    a.isSuccess(),
                    escapeCsv(a.getDetails())
            ));
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit_log.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(writer.toString());
    }

    private Specification<AuditLog> buildSpec(String actor, String target, String action,
                                              Boolean success, Instant from, Instant to) {
        return (root, q, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (actor != null && !actor.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("actorUsername")), "%" + actor.trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (target != null && !target.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("targetUsername")), "%" + target.trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (action != null && !action.isBlank()) {
                predicates.add(cb.equal(root.get("action"), action.trim()));
            }
            if (success != null) {
                predicates.add(cb.equal(root.get("success"), success));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("occurredAt"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private AuditLogResponse toResponse(AuditLog a) {
        return new AuditLogResponse(a.getId(), a.getOccurredAt(), a.getActorUserId(),
                a.getActorUsername(), a.getAction(), a.getTargetUserId(),
                a.getTargetUsername(), a.getIp(), a.getDetails(), a.isSuccess());
    }

    private String escapeCsv(String val) {
        if (val == null) return "";
        return val.replace("\"", "\"\"");
    }
}
