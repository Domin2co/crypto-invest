package com.cryptoinvest.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 서버만 가진 HMAC 키로 로그인 토큰의 사용자 ID와 만료 시각을 검증한다. */
@Component
public class AppTokenService {
    private final byte[] signingKey;
    private final Clock clock;
    private final UserAuthRepository users;

    @Autowired
    public AppTokenService(@Value("${app.auth-token-secret:}") String secret, UserAuthRepository users) { this(secret, Clock.systemUTC(), users); }
    AppTokenService(String secret, Clock clock) { this(secret, clock, null); }
    AppTokenService(String secret, Clock clock, UserAuthRepository users) { this.signingKey = decodeKey(secret); this.clock = clock; this.users = users; }

    public String issue(UUID userId) {
        long expiresAt = clock.instant().plusSeconds(60 * 30).getEpochSecond();
        int version = users == null ? 0 : users.authTokenVersion(userId);
        String payload = userId + "." + expiresAt + ".v3." + version;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + signature(payload);
    }

    public UUID verify(String token) {
        String[] parts = token == null ? new String[0] : token.split("\\.");
        if (parts.length != 2) throw new IllegalArgumentException("Invalid access token");
        String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(signature(payload).getBytes(StandardCharsets.US_ASCII), parts[1].getBytes(StandardCharsets.US_ASCII))) throw new IllegalArgumentException("Invalid access token");
        String[] values = payload.split("\\.");
        if (values.length != 4 || !"v3".equals(values[2])) throw new IllegalArgumentException("Invalid access token");
        if (Long.parseLong(values[1]) <= clock.instant().getEpochSecond()) throw new IllegalArgumentException("Expired access token");
        UUID userId = UUID.fromString(values[0]);
        if (users != null && Integer.parseInt(values[3]) != users.authTokenVersion(userId)) throw new IllegalArgumentException("Revoked access token");
        return userId;
    }

    private static byte[] decodeKey(String secret) {
        if (secret == null || secret.isBlank()) throw new IllegalStateException("AUTH_TOKEN_SECRET must be a base64 32-byte key");
        try {
            byte[] key = Base64.getDecoder().decode(secret);
            if (key.length < 32) throw new IllegalStateException("AUTH_TOKEN_SECRET must be a base64 32-byte key");
            return key;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("AUTH_TOKEN_SECRET must be a base64 32-byte key", exception);
        }
    }

    public String fingerprint(String value) {
        return signature("rate-limit:" + value);
    }

    private String signature(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Authentication token signing failed", exception);
        }
    }
}
