package com.venuity.server.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Component
public class PasswordService {

    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(12);

    public String encode(String rawPassword) {
        return bcrypt.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String storedHash) {
        if (storedHash == null) {
            return false;
        }
        if (storedHash.startsWith("$2")) {
            return bcrypt.matches(rawPassword, storedHash);
        }
        if (storedHash.startsWith("pbkdf2_sha256$")) {
            return matchesDjango(rawPassword, storedHash);
        }
        return false;
    }

    public boolean needsRehash(String storedHash) {
        return storedHash == null || !storedHash.startsWith("$2");
    }

    private boolean matchesDjango(String rawPassword, String storedHash) {
        String[] parts = storedHash.split("\\$", 4);
        if (parts.length != 4) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = parts[2].getBytes(StandardCharsets.UTF_8);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            PBEKeySpec spec = new PBEKeySpec(rawPassword.toCharArray(), salt, iterations, 256);
            byte[] actual = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();
            return MessageDigest.isEqual(expected, actual);
        } catch (RuntimeException | java.security.GeneralSecurityException exception) {
            return false;
        }
    }
}
