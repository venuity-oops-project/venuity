package com.venuity.server.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.venuity.server.api.error.ApiException;
import com.venuity.server.api.error.ErrorResponseFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ErrorResponseFactory responses;
    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ErrorResponseFactory responses, ObjectMapper objectMapper) {
        this.responses = responses;
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ApiException exception = responses.unauthenticated();
        Object body = responses.isLegacy(request.getRequestURI())
                ? responses.legacy(exception)
                : responses.modern(exception);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
