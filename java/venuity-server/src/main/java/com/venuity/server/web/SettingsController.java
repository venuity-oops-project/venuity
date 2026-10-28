package com.venuity.server.web;

import com.venuity.server.api.dto.BrandingResponse;
import com.venuity.server.api.dto.SettingsResponse;
import com.venuity.server.api.dto.SettingsUpdateRequest;
import com.venuity.server.service.SettingsService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService settings;

    public SettingsController(SettingsService settings) {
        this.settings = settings;
    }

    @GetMapping
    public SettingsResponse get() {
        return settings.modern();
    }

    @GetMapping("/branding")
    public BrandingResponse branding() {
        return settings.modernBranding();
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public SettingsResponse update(@Valid @RequestBody SettingsUpdateRequest request) {
        return settings.update(request);
    }
}
