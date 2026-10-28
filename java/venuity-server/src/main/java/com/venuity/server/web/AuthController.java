package com.venuity.server.web;

import com.venuity.server.api.dto.LoginRequest;
import com.venuity.server.api.dto.LoginResponse;
import com.venuity.server.api.dto.RefreshRequest;
import com.venuity.server.api.dto.TokenPairResponse;
import com.venuity.server.api.dto.UserResponse;
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
        AuthService.LoginResult result = auth.login(request.username(), request.password(),
                servletRequest.getRemoteAddr());
        return new LoginResponse(result.tokens().access(), result.tokens().refresh(),
                result.tokens().accessExpiresAt(), auth.modernUser(result.user()));
    }

    @GetMapping("/me")
    public UserResponse me() {
        return auth.modernUser(auth.requireUser(SecurityUtils.principal().id()));
    }

    @PostMapping("/refresh")
    public TokenPairResponse refresh(@Valid @RequestBody RefreshRequest request) {
        var pair = auth.refresh(request.refresh());
        return new TokenPairResponse(pair.access(), pair.refresh());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        auth.blacklist(request.refresh());
        return ResponseEntity.noContent().build();
    }
}
