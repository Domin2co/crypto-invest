package com.cryptoinvest.security;

import com.cryptoinvest.exchange.credential.CredentialCipher;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TotpMfaService {
    private static final long STEP_SECONDS = 30;
    private final JdbcTemplate jdbc;
    private final CredentialCipher cipher;
    private final AppTokenService tokens;
    private final SecureRandom random = new SecureRandom();

    public TotpMfaService(JdbcTemplate jdbc, CredentialCipher cipher, AppTokenService tokens) {
        this.jdbc = jdbc;
        this.cipher = cipher;
        this.tokens = tokens;
    }

    @Transactional
    public Enrollment begin(UUID userId, String email) {
        String secret = base32(randomBytes(20));
        String encrypted = cipher.encrypt(secret, context(userId));
        int changed = jdbc.update("""
                INSERT INTO user_totp_mfa (user_id, encrypted_secret, enabled, created_at)
                VALUES (?, ?, FALSE, CURRENT_TIMESTAMP)
                ON CONFLICT (user_id) DO UPDATE SET encrypted_secret = EXCLUDED.encrypted_secret, created_at = CURRENT_TIMESTAMP
                WHERE user_totp_mfa.enabled = FALSE
                """, userId, encrypted);
        if (changed != 1) throw new IllegalStateException("Multi-factor authentication is already enabled");
        String label = "Crypto Invest:" + email;
        String uri = "otpauth://totp/" + URLEncoder.encode(label, StandardCharsets.UTF_8).replace("+", "%20")
                + "?secret=" + secret + "&issuer=" + URLEncoder.encode("Crypto Invest", StandardCharsets.UTF_8).replace("+", "%20") + "&algorithm=SHA1&digits=6&period=30";
        return new Enrollment(secret, uri);
    }

    @Transactional
    public List<String> confirm(UUID userId, String code) {
        String secret = pendingSecret(userId);
        Long step = matchingStep(secret, code, Instant.now().getEpochSecond());
        if (step == null) throw new IllegalArgumentException("Invalid or expired authenticator code");
        if (jdbc.update("UPDATE user_totp_mfa SET enabled = TRUE, enabled_at = CURRENT_TIMESTAMP, last_used_step = ? WHERE user_id = ? AND enabled = FALSE AND created_at > CURRENT_TIMESTAMP - INTERVAL '10 minutes'", step, userId) != 1) {
            throw new IllegalArgumentException("Authenticator setup expired");
        }
        List<String> recoveryCodes = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String recoveryCode = HexFormat.of().formatHex(randomBytes(16));
            recoveryCodes.add(recoveryCode);
            jdbc.update("INSERT INTO user_totp_recovery_code (user_id, code_hash) VALUES (?, ?)", userId, tokens.fingerprint("mfa-recovery|" + userId + "|" + recoveryCode));
        }
        return List.copyOf(recoveryCodes);
    }

    public boolean isEnabled(UUID userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM user_totp_mfa WHERE user_id = ? AND enabled = TRUE)", Boolean.class, userId));
    }

    @Transactional
    public boolean verify(UUID userId, String code) {
        if (code == null || code.isBlank()) return false;
        if (code.matches("(?i)^[0-9a-f]{32}$")) {
            return jdbc.update("DELETE FROM user_totp_recovery_code WHERE user_id = ? AND code_hash = ?",
                    userId, tokens.fingerprint("mfa-recovery|" + userId + "|" + code.toLowerCase(java.util.Locale.ROOT))) == 1;
        }
        String encrypted = jdbc.query("SELECT encrypted_secret FROM user_totp_mfa WHERE user_id = ? AND enabled = TRUE", rs -> rs.next() ? rs.getString(1) : null, userId);
        if (encrypted == null) return false;
        Long step = matchingStep(cipher.decrypt(encrypted, context(userId)), code, Instant.now().getEpochSecond());
        return step != null && jdbc.update("UPDATE user_totp_mfa SET last_used_step = ? WHERE user_id = ? AND enabled = TRUE AND (last_used_step IS NULL OR last_used_step < ?)", step, userId, step) == 1;
    }

    @Transactional
    public void disable(UUID userId, String code) {
        if (!verify(userId, code)) throw new IllegalArgumentException("Invalid authenticator code");
        jdbc.update("DELETE FROM user_totp_mfa WHERE user_id = ?", userId);
    }

    private String pendingSecret(UUID userId) {
        String encrypted = jdbc.query("SELECT encrypted_secret FROM user_totp_mfa WHERE user_id = ? AND enabled = FALSE AND created_at > CURRENT_TIMESTAMP - INTERVAL '10 minutes'", rs -> rs.next() ? rs.getString(1) : null, userId);
        if (encrypted == null) throw new IllegalArgumentException("Authenticator setup expired");
        return cipher.decrypt(encrypted, context(userId));
    }

    static Long matchingStep(String secret, String code, long nowSeconds) {
        if (code == null || !code.matches("^[0-9]{6}$")) return null;
        long current = nowSeconds / STEP_SECONDS;
        for (long step : new long[] { current, current - 1, current + 1 }) {
            if (step >= 0 && MessageDigest.isEqual(totp(secret, step).getBytes(StandardCharsets.US_ASCII), code.getBytes(StandardCharsets.US_ASCII))) return step;
        }
        return null;
    }

    static String totp(String secret, long step) {
        try {
            byte[] key = decodeBase32(secret);
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16) | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            return String.format(java.util.Locale.ROOT, "%06d", binary % 1_000_000);
        } catch (Exception exception) {
            throw new IllegalStateException("Authenticator verification failed", exception);
        }
    }

    private byte[] randomBytes(int count) { byte[] bytes = new byte[count]; random.nextBytes(bytes); return bytes; }
    private static String context(UUID userId) { return "user:" + userId + ":totp"; }
    private static String base32(byte[] bytes) {
        final char[] alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
        StringBuilder result = new StringBuilder(); int buffer = 0, bits = 0;
        for (byte value : bytes) { buffer = (buffer << 8) | (value & 0xff); bits += 8; while (bits >= 5) { result.append(alphabet[(buffer >> (bits -= 5)) & 31]); } }
        if (bits > 0) result.append(alphabet[(buffer << (5 - bits)) & 31]);
        return result.toString();
    }
    private static byte[] decodeBase32(String value) {
        String normalized = value.replace("=", "").toUpperCase(java.util.Locale.ROOT);
        byte[] output = new byte[normalized.length() * 5 / 8]; int buffer = 0, bits = 0, index = 0;
        for (char character : normalized.toCharArray()) {
            int digit = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(character);
            if (digit < 0) throw new IllegalArgumentException("Invalid authenticator secret");
            buffer = (buffer << 5) | digit; bits += 5;
            if (bits >= 8) output[index++] = (byte) (buffer >> (bits -= 8));
        }
        return output;
    }

    public record Enrollment(String secret, String otpauthUri) {}
}
