package com.venuity.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.venuity.common.ErrorCode;
import com.venuity.server.api.error.ApiException;
import com.venuity.server.domain.IdempotencyKey;
import com.venuity.server.domain.IdempotencyRecord;
import com.venuity.server.repo.IdempotencyRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class IdempotencyService {

    private final IdempotencyRepository records;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyRepository records, ObjectMapper objectMapper) {
        this.records = records;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public <T> T execute(String idemKey, String endpoint, long userId, Object request,
                         Class<T> responseType, Supplier<T> action) {
        UUID.fromString(idemKey);
        String hash = hash(request);
        IdempotencyKey key = new IdempotencyKey(idemKey, endpoint, userId);
        Optional<IdempotencyRecord> existing = records.findById(key);
        if (existing.isPresent()) {
            return replay(existing.get(), hash, responseType);
        }
        IdempotencyRecord record = IdempotencyRecord.create(key, hash);
        try {
            record = records.saveAndFlush(record);
        } catch (DataIntegrityViolationException exception) {
            return replay(records.findById(key).orElseThrow(() -> inFlight()), hash, responseType);
        }
        T response = action.get();
        record.complete(200, objectMapper.valueToTree(response));
        records.saveAndFlush(record);
        return response;
    }

    @Scheduled(fixedDelay = 3_600_000L, initialDelay = 60_000L)
    @Transactional
    public void purgeExpired() {
        records.deleteByCreatedAtBefore(Instant.now().minus(48, ChronoUnit.HOURS));
    }

    private <T> T replay(IdempotencyRecord record, String hash, Class<T> responseType) {
        if (!record.getRequestHash().equals(hash)) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT, HttpStatus.CONFLICT,
                    "Idempotency key was already used with a different request");
        }
        JsonNode body = record.getResponseBody();
        if (body == null) {
            throw inFlight();
        }
        try {
            return objectMapper.treeToValue(body, responseType);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not replay stored response");
        }
    }

    private ApiException inFlight() {
        return new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT, HttpStatus.CONFLICT,
                "A request with this idempotency key is already in progress");
    }

    private String hash(Object request) {
        try {
            byte[] bytes = objectMapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not hash request");
        }
    }
}
