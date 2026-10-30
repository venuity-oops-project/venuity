package com.venuity.server.security;

import com.venuity.server.domain.Role;

public record AuthPrincipal(long id, String publicId, String username, Role role, boolean mustChangePassword) {

    public AuthPrincipal(long id, String publicId, String username, Role role) {
        this(id, publicId, username, role, false);
    }
}
