package com.cryptoinvest.exchange.credential;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** Private API 요청 직전에만 HS512 JWT를 만든다. 생성 토큰과 key는 로그에 남기지 않는다. */
@Component
public class JwtSigner {
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    public String bearerToken(ExchangeCredentials credentials) {
        return bearerToken(credentials, "HS512", false);
    }

    public String bearerToken(ExchangeCredentials credentials, String algorithm, boolean includeTimestamp) {
        try {
            String header = encode("{\"alg\":\"" + algorithm + "\",\"typ\":\"JWT\"}");
            String timestamp = includeTimestamp ? ",\"timestamp\":" + System.currentTimeMillis() : "";
            String payload = encode("{\"access_key\":\"" + json(credentials.accessKey()) + "\",\"nonce\":\"" + UUID.randomUUID() + "\"" + timestamp + "}");
            String macAlgorithm = "HS256".equals(algorithm) ? "HmacSHA256" : "HmacSHA512";
            Mac mac = Mac.getInstance(macAlgorithm);
            mac.init(new SecretKeySpec(credentials.secretKey().getBytes(StandardCharsets.UTF_8), macAlgorithm));
            String signed = header + "." + payload;
            return "Bearer " + signed + "." + ENCODER.encodeToString(mac.doFinal(signed.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("JWT signing failed", exception);
        }
    }

    private static String encode(String value) { return ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
    private static String json(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
}
