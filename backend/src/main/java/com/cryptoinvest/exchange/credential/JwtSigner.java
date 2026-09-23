package com.cryptoinvest.exchange.credential;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** 거래소 규격 JWT를 요청 직전에 만들며 토큰과 key는 로그에 남기지 않는다. */
@Component
public class JwtSigner {
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    public String bearerToken(ExchangeCredentials credentials) {
        return bearerToken(credentials, "HS512", false);
    }

    public String bearerToken(ExchangeCredentials credentials, String algorithm, boolean includeTimestamp) {
        return bearerToken(credentials, algorithm, includeTimestamp, null);
    }

    /** 주문 본문·조회 query와 같은 순서의 문자열을 서명해 거래소가 요청을 검증할 수 있게 한다. */
    public String bearerTokenForQuery(ExchangeCredentials credentials, String queryString) {
        return bearerTokenForQuery(credentials, queryString, "HS512", false);
    }

    /** 거래소별 JWT 서명 알고리즘·timestamp 규칙과 동일한 query hash를 적용한다. */
    public String bearerTokenForQuery(ExchangeCredentials credentials, String queryString, String algorithm, boolean includeTimestamp) {
        return bearerToken(credentials, algorithm, includeTimestamp, queryString);
    }

    private String bearerToken(ExchangeCredentials credentials, String algorithm, boolean includeTimestamp, String queryString) {
        try {
            String header = encode("{\"alg\":\"" + algorithm + "\",\"typ\":\"JWT\"}");
            String timestamp = includeTimestamp ? ",\"timestamp\":" + System.currentTimeMillis() : "";
            String queryHash = queryString == null || queryString.isBlank() ? "" : ",\"query_hash\":\"" + sha512(queryString) + "\",\"query_hash_alg\":\"SHA512\"";
            String payload = encode("{\"access_key\":\"" + json(credentials.accessKey()) + "\",\"nonce\":\"" + UUID.randomUUID() + "\"" + timestamp + queryHash + "}");
            String macAlgorithm = "HS256".equals(algorithm) ? "HmacSHA256" : "HmacSHA512";
            Mac mac = Mac.getInstance(macAlgorithm);
            mac.init(new SecretKeySpec(credentials.secretKey().getBytes(StandardCharsets.UTF_8), macAlgorithm));
            String signed = header + "." + payload;
            return "Bearer " + signed + "." + ENCODER.encodeToString(mac.doFinal(signed.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("JWT signing failed", exception);
        }
    }

    private static String sha512(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-512").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte part : digest) hex.append(String.format("%02x", part));
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-512 is unavailable", exception);
        }
    }

    private static String encode(String value) { return ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
    private static String json(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
}
