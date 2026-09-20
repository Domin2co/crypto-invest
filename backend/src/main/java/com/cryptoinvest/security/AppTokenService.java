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
    private final String secret;
    private final Clock clock;

    @Autowired
    public AppTokenService(@Value("${app.auth-token-secret:}") String secret) { this(secret, Clock.systemUTC()); }
    AppTokenService(String secret, Clock clock) { this.secret = secret; this.clock = clock; }

    public String issue(UUID userId) {
        long expiresAt = clock.instant().plusSeconds(60 * 60 * 8).getEpochSecond();
        String payload = userId + "." + expiresAt;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + signature(payload);
    }

    public UUID verify(String token) {
        String[] parts = token == null ? new String[0] : token.split("\\.");
        if (parts.length != 2) throw new IllegalArgumentException("Invalid access token");
        String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(signature(payload).getBytes(StandardCharsets.US_ASCII), parts[1].getBytes(StandardCharsets.US_ASCII))) throw new IllegalArgumentException("Invalid access token");
        String[] values = payload.split("\\.");
        if (values.length != 2 || Long.parseLong(values[1]) <= clock.instant().getEpochSecond()) throw new IllegalArgumentException("Expired access token");
        return UUID.fromString(values[0]);
    }

    private String signature(String payload) {
        try {
            byte[] key = Base64.getDecoder().decode(secret);
            if (key.length < 32) throw new IllegalStateException("AUTH_TOKEN_SECRET must be a base64 32-byte key");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Authentication token key is unavailable", exception);
        }
    }
}
