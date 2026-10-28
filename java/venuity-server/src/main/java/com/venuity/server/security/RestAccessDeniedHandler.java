package com.venuity.server.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.venuity.server.api.error.ApiException;
import com.venuity.server.api.error.ErrorResponseFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ErrorResponseFactory responses;
    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ErrorResponseFactory responses, ObjectMapper objectMapper) {
        this.responses = responses;
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        ApiException exception = responses.forbidden();
        Object body = responses.isLegacy(request.getRequestURI())
                ? responses.legacy(exception)
                : responses.modern(exception);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
