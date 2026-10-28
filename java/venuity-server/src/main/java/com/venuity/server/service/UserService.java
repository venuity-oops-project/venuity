package com.venuity.server.service;

import com.venuity.common.ErrorCode;
import com.venuity.server.api.dto.UserCreateRequest;
import com.venuity.server.api.dto.UserResponse;
import com.venuity.server.api.error.ApiException;
import com.venuity.server.domain.AppUser;
import com.venuity.server.domain.Role;
import com.venuity.server.repo.AppUserRepository;
import com.venuity.server.security.PasswordService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final AppUserRepository users;
    private final PasswordService passwords;
    private final AuthService authService;

    public UserService(AppUserRepository users, PasswordService passwords, AuthService authService) {
        this.users = users;
        this.passwords = passwords;
        this.authService = authService;
    }

    @Transactional
    public UserResponse create(UserCreateRequest request) {
        String username = request.username().trim();
        if (users.findByUsername(username).isPresent()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST,
                    "Username '" + username + "' is already taken");
        }

        Role role = request.role() != null ? request.role() : Role.STAFF;
        AppUser user = AppUser.create(username, passwords.encode(request.password()), role);
        if (request.email() != null && !request.email().isBlank()) {
            user.setEmail(request.email().trim());
        }
        if (request.firstName() != null && !request.firstName().isBlank()) {
            user.setFirstName(request.firstName().trim());
        }
        if (request.lastName() != null && !request.lastName().isBlank()) {
            user.setLastName(request.lastName().trim());
        }

        users.save(user);
        return authService.modernUser(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return users.findAll().stream()
                .map(authService::modernUser)
                .toList();
    }
}

