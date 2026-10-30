package com.venuity.server.security;

import com.venuity.server.domain.AppUser;
import com.venuity.server.repo.AppUserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AppUserRepository users;
    private final RestAuthenticationEntryPoint entryPoint;

    public JwtAuthenticationFilter(JwtService jwtService, AppUserRepository users,
                                   RestAuthenticationEntryPoint entryPoint) {
        this.jwtService = jwtService;
        this.users = users;
        this.entryPoint = entryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            Claims claims = jwtService.parse(header.substring(7));
            if (!"access".equals(claims.get("type", String.class))) {
                throw new BadCredentialsException("Wrong token type");
            }
            long userId = Long.parseLong(claims.getSubject());
            AppUser user = users.findById(userId)
                    .filter(AppUser::isEnabled)
                    .orElseThrow(() -> new BadCredentialsException("Unknown or disabled user"));

            if (user.isArchived() || user.isLocked() || user.isExpired()) {
                throw new BadCredentialsException("User account is inactive, locked, or expired");
            }
            Integer ver = claims.get("ver", Integer.class);
            if (ver != null && ver != user.getTokenVersion()) {
                throw new BadCredentialsException("Token has been revoked");
            }

            if (user.isMustChangePassword()) {
                String uri = request.getRequestURI();
                boolean allowed = uri.equals("/api/auth/me")
                        || uri.equals("/api/me")
                        || uri.equals("/api/me/change-password")
                        || uri.equals("/api/auth/logout")
                        || uri.startsWith("/api/settings/branding");
                if (!allowed) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"code\":\"PASSWORD_CHANGE_REQUIRED\",\"message\":\"Password change required before accessing other resources.\"}");
                    return;
                }
            }

            AuthPrincipal principal = new AuthPrincipal(user.getId(), user.getPublicId(),
                    user.getUsername(), user.getRole(), user.isMustChangePassword());
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException | BadCredentialsException exception) {
            SecurityContextHolder.clearContext();
            entryPoint.commence(request, response,
                    new BadCredentialsException("Invalid access token", exception));
        }
    }
}
