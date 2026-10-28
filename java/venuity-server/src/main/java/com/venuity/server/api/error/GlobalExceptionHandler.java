package com.venuity.server.api.error;

import com.venuity.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ErrorResponseFactory responses;

    public GlobalExceptionHandler(ErrorResponseFactory responses) {
        this.responses = responses;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Object> handleApi(ApiException exception, HttpServletRequest request) {
        boolean legacy = responses.isLegacy(request.getRequestURI());
        Object body = legacy ? responses.legacy(exception) : responses.modern(exception);
        return ResponseEntity.status(legacy ? exception.getStatus() : exception.getModernStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidation(MethodArgumentNotValidException exception,
                                                   HttpServletRequest request) {
        Map<String, List<String>> fields = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            fields.computeIfAbsent(error.getField(), key -> new java.util.ArrayList<>())
                    .add(error.getDefaultMessage());
        }
        ApiException api = new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST,
                "Validation failed").withLegacyDetail(fields);
        return handleApi(api, request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Object> handleBadRequest(Exception exception, HttpServletRequest request) {
        ApiException api = new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST,
                "Invalid request");
        return handleApi(api, request);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Object> handleMissingHeader(MissingRequestHeaderException exception,
                                                      HttpServletRequest request) {
        Map<String, Object> details = Map.of(exception.getHeaderName(), List.of("This header is required."));
        ApiException api = new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST,
                "Missing required header " + exception.getHeaderName())
                .withLegacyDetail(details);
        return handleApi(api, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(AccessDeniedException exception, HttpServletRequest request) {
        return handleApi(responses.forbidden(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleConflict(DataIntegrityViolationException exception,
                                                 HttpServletRequest request) {
        ApiException api = new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST,
                "The request conflicts with existing data");
        return handleApi(api, request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Object> handleNoResource(NoResourceFoundException exception, HttpServletRequest request) {
        ApiException api = new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found");
        return handleApi(api, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled request failure", exception);
        ApiException api = new ApiException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR,
                "An internal server error occurred.");
        return handleApi(api, request);
    }
}
