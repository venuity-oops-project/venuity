package com.venuity.server.security;

import com.venuity.common.ErrorCode;
import com.venuity.server.api.error.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

@Service
public class PasswordPolicyService {

    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "password", "password123", "123456", "12345678", "123456789",
            "admin123", "qwerty", "welcome1", "letmein", "venuity123"
    );

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SYMBOLS = "!@#$%^&*()-_=+";
    private static final String ALL_CHARS = UPPER + LOWER + DIGITS + SYMBOLS;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final int minLength;

    public PasswordPolicyService(@Value("${venuity.security.password-min-length:8}") int configuredMinLength,
                                 Environment env) {
        boolean isProd = Arrays.asList(env.getActiveProfiles()).contains("prod");
        this.minLength = isProd ? Math.max(configuredMinLength, 12) : configuredMinLength;
    }

    public void validatePassword(String password, String username) {
        if (password == null || password.isBlank()) {
            throw new ApiException(ErrorCode.PASSWORD_POLICY, HttpStatus.BAD_REQUEST, "Password cannot be empty.");
        }
        if (password.length() < minLength) {
            throw new ApiException(ErrorCode.PASSWORD_POLICY, HttpStatus.BAD_REQUEST,
                    "Password must be at least " + minLength + " characters long.");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new ApiException(ErrorCode.PASSWORD_POLICY, HttpStatus.BAD_REQUEST,
                    "Password must contain at least one letter and one number.");
        }
        if (username != null && !username.isBlank()) {
            if (password.equalsIgnoreCase(username.trim())) {
                throw new ApiException(ErrorCode.PASSWORD_POLICY, HttpStatus.BAD_REQUEST,
                        "Password cannot be identical to the username.");
            }
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (COMMON_PASSWORDS.contains(lower)) {
            throw new ApiException(ErrorCode.PASSWORD_POLICY, HttpStatus.BAD_REQUEST,
                    "Password is too common and easily guessed. Please choose a stronger password.");
        }
    }

    public String generateTemporaryPassword() {
        StringBuilder sb = new StringBuilder(16);
        sb.append(UPPER.charAt(RANDOM.nextInt(UPPER.length())));
        sb.append(LOWER.charAt(RANDOM.nextInt(LOWER.length())));
        sb.append(DIGITS.charAt(RANDOM.nextInt(DIGITS.length())));
        sb.append(SYMBOLS.charAt(RANDOM.nextInt(SYMBOLS.length())));
        for (int i = 4; i < 16; i++) {
            sb.append(ALL_CHARS.charAt(RANDOM.nextInt(ALL_CHARS.length())));
        }
        // Shuffle characters
        char[] chars = sb.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char temp = chars[i];
            chars[i] = chars[j];
            chars[j] = temp;
        }
        return new String(chars);
    }

    public int getMinLength() {
        return minLength;
    }
}
