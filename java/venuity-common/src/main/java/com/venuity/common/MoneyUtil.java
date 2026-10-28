package com.venuity.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyUtil {

    public static final int SCALE = 2;
    public static final BigDecimal ZERO = new BigDecimal("0.00");
    public static final BigDecimal MIN_RECHARGE = new BigDecimal("0.01");
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999.99");

    private MoneyUtil() {
    }

    public static BigDecimal normalize(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal nonNegative(BigDecimal value) {
        BigDecimal normalized = normalize(value);
        return normalized == null ? ZERO : normalized;
    }

    public static String plain(BigDecimal value) {
        return nonNegative(value).toPlainString();
    }
}
