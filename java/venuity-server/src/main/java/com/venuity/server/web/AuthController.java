package com.venuity.server.web;

import com.venuity.server.api.dto.LoginRequest;
import com.venuity.server.api.dto.LoginResponse;
import com.venuity.server.api.dto.RefreshRequest;
import com.venuity.server.api.dto.TokenPairResponse;
import com.venuity.server.api.dto.UserResponse;
import com.venuity.server.security.AuthPrincipal;
import com.venuity.server.security.SecurityUtils;
import com.venuity.server.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        String ip = servletRequest.getRemoteAddr();
        String userAgent = servletRequest.getHeader("User-Agent");
        AuthService.LoginResult result = auth.login(request.username(), request.password(), ip, userAgent);
        return new LoginResponse(result.tokens().access(), result.tokens().refresh(),
                result.tokens().accessExpiresAt(), auth.modernUser(result.user()));
    }

    @GetMapping("/me")
    public UserResponse me() {
        return auth.modernUser(auth.requireUser(SecurityUtils.principal().id()));
    }

    @PostMapping("/refresh")
    public TokenPairResponse refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest servletRequest) {
        String ip = servletRequest.getRemoteAddr();
        String userAgent = servletRequest.getHeader("User-Agent");
        var pair = auth.refresh(request.refresh(), ip, userAgent);
        return new TokenPairResponse(pair.access(), pair.refresh());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request, HttpServletRequest servletRequest) {
        AuthPrincipal principal = null;
        try {
            principal = SecurityUtils.principal();
        } catch (Exception ignored) {
        }
        Long userId = principal != null ? principal.id() : null;
        String username = principal != null ? principal.username() : null;
        auth.logout(request.refresh(), userId, username, servletRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }
}
