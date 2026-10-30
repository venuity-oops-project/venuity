package com.venuity.server.config;

import com.venuity.server.security.JwtAuthenticationFilter;
import com.venuity.server.security.RestAccessDeniedHandler;
import com.venuity.server.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            JwtAuthenticationFilter jwtFilter,
                                            RestAuthenticationEntryPoint entryPoint,
                                            RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .headers(headers -> headers
                        .permissionsPolicy(permissions -> permissions.policy("camera=(self)")))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/api/v1/settings/branding", "/api/v1/settings/branding/",
                                "/api/settings/branding", "/api/settings/branding/")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/", "/index.html", "/favicon.ico", "/favicon.svg",
                                "/favicon.png", "/logo.png", "/css/**", "/js/**", "/vendor/**", "/assets/**")
                        .permitAll()
                        .requestMatchers("/api/auth/login", "/api/auth/refresh",
                                "/api/v1/auth/token", "/api/v1/auth/token/",
                                "/api/v1/auth/token/refresh", "/api/v1/auth/token/refresh/",
                                "/api/health", "/actuator/health", "/actuator/info",
                                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/error")
                        .permitAll()
                        .requestMatchers("/api/users/**", "/api/audit-log/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
