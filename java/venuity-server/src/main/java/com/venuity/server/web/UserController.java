package com.venuity.server.web;

import com.venuity.server.api.dto.BulkActionResult;
import com.venuity.server.api.dto.BulkUserActionRequest;
import com.venuity.server.api.dto.ResetPasswordRequest;
import com.venuity.server.api.dto.UserActivityResponse;
import com.venuity.server.api.dto.UserCreateRequest;
import com.venuity.server.api.dto.UserCreateResponse;
import com.venuity.server.api.dto.UserDetailResponse;
import com.venuity.server.api.dto.UserImportPreviewResponse;
import com.venuity.server.api.dto.UserImportResultResponse;
import com.venuity.server.api.dto.UserResponse;
import com.venuity.server.api.dto.UserSessionResponse;
import com.venuity.server.api.dto.UserUpdateRequest;
import com.venuity.server.domain.Role;
import com.venuity.server.domain.UserSession;
import com.venuity.server.security.AuthPrincipal;
import com.venuity.server.security.SecurityUtils;
import com.venuity.server.service.UserService;
import com.venuity.server.service.UserSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;
    private final UserSessionService sessionService;

    public UserController(UserService userService, UserSessionService sessionService) {
        this.userService = userService;
        this.sessionService = sessionService;
    }

    @GetMapping
    public Page<UserResponse> listUsers(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sort,
            @RequestParam(defaultValue = "asc") String direction
    ) {
        Sort s = "desc".equalsIgnoreCase(direction) ? Sort.by(sort).descending() : Sort.by(sort).ascending();
        return userService.listUsers(query, role, status, PageRequest.of(page, Math.min(size, 100), s));
    }

    @PostMapping
    public ResponseEntity<UserCreateResponse> create(
            @Valid @RequestBody UserCreateRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthPrincipal principal = SecurityUtils.principal();
        UserCreateResponse response = userService.create(request, principal, servletRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public UserDetailResponse get(@PathVariable Long id) {
        return userService.getUserDetail(id);
    }

    @PutMapping("/{id}")
    public UserResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UserUpdateRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthPrincipal principal = SecurityUtils.principal();
        return userService.update(id, request, principal, servletRequest.getRemoteAddr());
    }

    @PostMapping("/{id}/disable")
    public UserResponse disable(@PathVariable Long id, HttpServletRequest servletRequest) {
        AuthPrincipal principal = SecurityUtils.principal();
        return userService.disable(id, principal, servletRequest.getRemoteAddr());
    }

    @PostMapping("/{id}/enable")
    public UserResponse enable(@PathVariable Long id, HttpServletRequest servletRequest) {
        AuthPrincipal principal = SecurityUtils.principal();
        return userService.enable(id, principal, servletRequest.getRemoteAddr());
    }

    @PostMapping("/{id}/unlock")
    public UserResponse unlock(@PathVariable Long id, HttpServletRequest servletRequest) {
        AuthPrincipal principal = SecurityUtils.principal();
        return userService.unlock(id, principal, servletRequest.getRemoteAddr());
    }

    @PostMapping("/{id}/reset-password")
    public Map<String, String> resetPassword(
            @PathVariable Long id,
            @RequestBody(required = false) ResetPasswordRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthPrincipal principal = SecurityUtils.principal();
        String tempPass = userService.resetPassword(id, request, principal, servletRequest.getRemoteAddr());
        return Map.of("temporaryPassword", tempPass);
    }

    @PostMapping("/{id}/force-logout")
    public ResponseEntity<Void> forceLogout(@PathVariable Long id, HttpServletRequest servletRequest) {
        AuthPrincipal principal = SecurityUtils.principal();
        userService.forceLogout(id, principal, servletRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> archive(@PathVariable Long id, HttpServletRequest servletRequest) {
        AuthPrincipal principal = SecurityUtils.principal();
        userService.archive(id, principal, servletRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restore")
    public UserResponse restore(@PathVariable Long id, HttpServletRequest servletRequest) {
        AuthPrincipal principal = SecurityUtils.principal();
        return userService.restore(id, principal, servletRequest.getRemoteAddr());
    }

    @GetMapping("/{id}/sessions")
    public List<UserSessionResponse> getSessions(@PathVariable Long id) {
        return sessionService.getActiveSessions(id).stream()
                .map(s -> toSessionResponse(s, false))
                .toList();
    }

    @DeleteMapping("/{id}/sessions/{sid}")
    public ResponseEntity<Void> revokeSession(@PathVariable Long id, @PathVariable Long sid) {
        sessionService.revokeSessionById(id, sid);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/activity")
    public UserActivityResponse getActivity(@PathVariable Long id) {
        return userService.getActivity(id);
    }

    @PostMapping("/bulk")
    public BulkActionResult bulkAction(
            @Valid @RequestBody BulkUserActionRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthPrincipal principal = SecurityUtils.principal();
        return userService.bulkAction(request, principal, servletRequest.getRemoteAddr());
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<String> exportCsv(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) String status
    ) {
        String csv = userService.exportCsv(query, role, status);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"users_export.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserImportPreviewResponse previewImport(@RequestParam("file") MultipartFile file) throws IOException {
        return userService.previewImportCsv(file.getInputStream());
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserImportResultResponse commitImport(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest servletRequest
    ) throws IOException {
        AuthPrincipal principal = SecurityUtils.principal();
        return userService.commitImportCsv(file.getInputStream(), principal, servletRequest.getRemoteAddr());
    }

    private UserSessionResponse toSessionResponse(UserSession s, boolean current) {
        return new UserSessionResponse(s.getId(), s.getIp(), s.getUserAgent(), s.getCreatedAt(),
                s.getLastSeenAt(), s.getExpiresAt(), s.getRevokedAt(), current);
    }
}
