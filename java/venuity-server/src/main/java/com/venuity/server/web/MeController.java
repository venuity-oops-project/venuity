package com.venuity.server.web;

import com.venuity.server.api.dto.ChangePasswordRequest;
import com.venuity.server.api.dto.ProfileUpdateRequest;
import com.venuity.server.api.dto.UserResponse;
import com.venuity.server.api.dto.UserSessionResponse;
import com.venuity.server.domain.UserSession;
import com.venuity.server.security.AuthPrincipal;
import com.venuity.server.security.SecurityUtils;
import com.venuity.server.service.UserService;
import com.venuity.server.service.UserSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final UserService userService;
    private final UserSessionService sessionService;

    public MeController(UserService userService, UserSessionService sessionService) {
        this.userService = userService;
        this.sessionService = sessionService;
    }

    @GetMapping
    public UserResponse getProfile() {
        AuthPrincipal principal = SecurityUtils.principal();
        return userService.get(principal.id());
    }

    @PutMapping
    public UserResponse updateProfile(@Valid @RequestBody ProfileUpdateRequest request, HttpServletRequest servletRequest) {
        AuthPrincipal principal = SecurityUtils.principal();
        return userService.updateProfile(principal.id(), request, servletRequest.getRemoteAddr());
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request, HttpServletRequest servletRequest) {
        AuthPrincipal principal = SecurityUtils.principal();
        userService.changePassword(principal.id(), request, servletRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sessions")
    public List<UserSessionResponse> getSessions() {
        AuthPrincipal principal = SecurityUtils.principal();
        return sessionService.getActiveSessions(principal.id()).stream()
                .map(s -> new UserSessionResponse(s.getId(), s.getIp(), s.getUserAgent(), s.getCreatedAt(),
                        s.getLastSeenAt(), s.getExpiresAt(), s.getRevokedAt(), false))
                .toList();
    }

    @DeleteMapping("/sessions/{sid}")
    public ResponseEntity<Void> revokeSession(@PathVariable Long sid) {
        AuthPrincipal principal = SecurityUtils.principal();
        sessionService.revokeSessionById(principal.id(), sid);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-others")
    public ResponseEntity<Void> logoutOthers() {
        AuthPrincipal principal = SecurityUtils.principal();
        sessionService.revokeAllSessions(principal.id());
        return ResponseEntity.noContent().build();
    }
}
