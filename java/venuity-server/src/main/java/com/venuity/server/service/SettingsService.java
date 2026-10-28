package com.venuity.server.service;

import com.venuity.common.ErrorCode;
import com.venuity.common.MoneyUtil;
import com.venuity.server.api.dto.BrandingResponse;
import com.venuity.server.api.dto.LegacyBrandingResponse;
import com.venuity.server.api.dto.LegacySettingsResponse;
import com.venuity.server.api.dto.LegacySettingsUpdateRequest;
import com.venuity.server.api.dto.SettingsResponse;
import com.venuity.server.api.dto.SettingsUpdateRequest;
import com.venuity.server.api.error.ApiException;
import com.venuity.server.domain.BusinessSettings;
import com.venuity.server.repo.BusinessSettingsRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Service
public class SettingsService {

    private final BusinessSettingsRepository settings;

    public SettingsService(BusinessSettingsRepository settings) {
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public BusinessSettings current() {
        return settings.findById((byte) 1)
                .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR,
                        "Business settings are not configured"));
    }

    @Transactional(readOnly = true)
    public ZoneId zone() {
        try {
            return ZoneId.of(current().getBusinessTimezone());
        } catch (RuntimeException exception) {
            return ZoneId.of("Asia/Kolkata");
        }
    }

    @Transactional(readOnly = true)
    public LegacySettingsResponse legacy() {
        BusinessSettings value = current();
        return new LegacySettingsResponse(value.getBusinessName(), value.getCurrencySymbol(),
                MoneyUtil.plain(value.getCheckinFee()), value.getPrimaryColor(),
                value.getPrimaryHoverColor(), value.getLogoUrl(), value.getAppTitle());
    }

    @Transactional(readOnly = true)
    public SettingsResponse modern() {
        BusinessSettings value = current();
        return new SettingsResponse(value.getBusinessName(), value.getCurrencySymbol(),
                MoneyUtil.normalize(value.getCheckinFee()), value.getPrimaryColor(),
                value.getPrimaryHoverColor(), value.getLogoUrl(), value.getAppTitle(),
                value.getBusinessTimezone());
    }

    @Transactional(readOnly = true)
    public LegacyBrandingResponse branding() {
        BusinessSettings value = current();
        return new LegacyBrandingResponse(value.getBusinessName(), value.getPrimaryColor(),
                value.getPrimaryHoverColor(), value.getLogoUrl(), value.getAppTitle());
    }

    @Transactional(readOnly = true)
    public BrandingResponse modernBranding() {
        BusinessSettings value = current();
        return new BrandingResponse(value.getBusinessName(), value.getPrimaryColor(),
                value.getPrimaryHoverColor(), value.getLogoUrl(), value.getAppTitle());
    }

    @Transactional
    public LegacySettingsResponse updateLegacy(LegacySettingsUpdateRequest request) {
        BigDecimal fee = null;
        if (request.checkinFee() != null) {
            try {
                fee = new BigDecimal(request.checkinFee().trim());
            } catch (NumberFormatException exception) {
                throw validation("checkin_fee", "A valid number is required.");
            }
        }
        apply(request.businessName(), request.currencySymbol(), fee, request.primaryColor(),
                request.primaryHoverColor(), request.logoUrl(), request.appTitle(), null);
        return legacy();
    }

    @Transactional
    public SettingsResponse update(SettingsUpdateRequest request) {
        apply(request.businessName(), request.currencySymbol(), request.checkinFee(),
                request.primaryColor(), request.primaryHoverColor(), request.logoUrl(),
                request.appTitle(), request.businessTimezone());
        return modern();
    }

    private void apply(String businessName, String currencySymbol, BigDecimal checkinFee,
                       String primaryColor, String primaryHoverColor, String logoUrl,
                       String appTitle, String businessTimezone) {
        BusinessSettings value = current();
        if (businessName != null) {
            value.setBusinessName(requireText(businessName, "business_name"));
        }
        if (currencySymbol != null) {
            value.setCurrencySymbol(requireText(currencySymbol, "currency_symbol"));
        }
        if (checkinFee != null) {
            BigDecimal normalized = MoneyUtil.normalize(checkinFee);
            if (normalized.signum() < 0) {
                throw validation("checkin_fee", "Ensure this value is greater than or equal to 0.");
            }
            if (normalized.compareTo(MoneyUtil.MAX_AMOUNT) > 0) {
                throw validation("checkin_fee", "Ensure this value is at most 99999999.99.");
            }
            value.setCheckinFee(normalized);
        }
        if (primaryColor != null) {
            value.setPrimaryColor(requireColor(primaryColor, "primary_color"));
        }
        if (primaryHoverColor != null) {
            value.setPrimaryHoverColor(requireColor(primaryHoverColor, "primary_hover_color"));
        }
        if (logoUrl != null) {
            value.setLogoUrl(requireText(logoUrl, "logo_url"));
        }
        if (appTitle != null) {
            value.setAppTitle(requireText(appTitle, "app_title"));
        }
        if (businessTimezone != null) {
            try {
                ZoneId.of(businessTimezone);
            } catch (RuntimeException exception) {
                throw validation("business_timezone", "Unknown time zone.");
            }
            value.setBusinessTimezone(businessTimezone);
        }
        settings.save(value);
    }

    private String requireText(String value, String field) {
        if (value.isBlank()) {
            throw validation(field, "This field may not be blank.");
        }
        return value;
    }

    private String requireColor(String value, String field) {
        if (!value.startsWith("#")) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST,
                    "Invalid color format for " + field + ": must start with #")
                    .withLegacyError("Invalid color format for " + field + ": must start with #")
                    .withoutLegacyCode();
        }
        return value;
    }

    private ApiException validation(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Validation failed")
                .withLegacyDetail(Map.of(field, List.of(message)));
    }
}
