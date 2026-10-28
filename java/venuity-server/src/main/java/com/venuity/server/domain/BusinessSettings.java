package com.venuity.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "business_settings")
public class BusinessSettings {

    @Id
    private Byte id;

    @Column(name = "business_name", nullable = false, length = 150)
    private String businessName;

    @Column(name = "currency_symbol", nullable = false, length = 8)
    private String currencySymbol;

    @Column(name = "checkin_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal checkinFee;

    @Column(name = "primary_color", nullable = false, length = 7)
    private String primaryColor;

    @Column(name = "primary_hover_color", nullable = false, length = 7)
    private String primaryHoverColor;

    @Column(name = "logo_url", nullable = false, length = 255)
    private String logoUrl;

    @Column(name = "app_title", nullable = false, length = 150)
    private String appTitle;

    @Column(name = "business_timezone", nullable = false, length = 64)
    private String businessTimezone;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected BusinessSettings() {
    }

    public Byte getId() {
        return id;
    }

    public String getBusinessName() {
        return businessName;
    }

    public void setBusinessName(String businessName) {
        this.businessName = businessName;
    }

    public String getCurrencySymbol() {
        return currencySymbol;
    }

    public void setCurrencySymbol(String currencySymbol) {
        this.currencySymbol = currencySymbol;
    }

    public BigDecimal getCheckinFee() {
        return checkinFee;
    }

    public void setCheckinFee(BigDecimal checkinFee) {
        this.checkinFee = checkinFee;
    }

    public String getPrimaryColor() {
        return primaryColor;
    }

    public void setPrimaryColor(String primaryColor) {
        this.primaryColor = primaryColor;
    }

    public String getPrimaryHoverColor() {
        return primaryHoverColor;
    }

    public void setPrimaryHoverColor(String primaryHoverColor) {
        this.primaryHoverColor = primaryHoverColor;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public String getAppTitle() {
        return appTitle;
    }

    public void setAppTitle(String appTitle) {
        this.appTitle = appTitle;
    }

    public String getBusinessTimezone() {
        return businessTimezone;
    }

    public void setBusinessTimezone(String businessTimezone) {
        this.businessTimezone = businessTimezone;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
