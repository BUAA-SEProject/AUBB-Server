package com.aubb.server.modules.identityaccess.application.auth;

import com.aubb.server.common.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class OpaqueRefreshTokenCodec {

    private static final int REFRESH_TOKEN_RANDOM_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    public String issue(String sessionId) {
        return sessionId + "." + randomSecret();
    }

    public RefreshTokenPayload parse(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw invalidRefreshToken();
        }
        int separatorIndex = refreshToken.indexOf('.');
        if (separatorIndex <= 0 || separatorIndex == refreshToken.length() - 1) {
            throw invalidRefreshToken();
        }
        String sessionId = refreshToken.substring(0, separatorIndex);
        String secret = refreshToken.substring(separatorIndex + 1);
        if (sessionId.isBlank() || secret.isBlank()) {
            throw invalidRefreshToken();
        }
        return new RefreshTokenPayload(sessionId, secret);
    }

    public String hash(String rawToken) {
        return HexFormat.of().formatHex(sha256(rawToken));
    }

    public boolean matches(String rawToken, String expectedHash) {
        if (rawToken == null || expectedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                hash(rawToken).getBytes(StandardCharsets.UTF_8), expectedHash.getBytes(StandardCharsets.UTF_8));
    }

    private String randomSecret() {
        byte[] bytes = new byte[REFRESH_TOKEN_RANDOM_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private byte[] sha256(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest algorithm is unavailable", exception);
        }
    }

    private BusinessException invalidRefreshToken() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "refresh token 无效");
    }

    public record RefreshTokenPayload(String sessionId, String secret) {}
}
