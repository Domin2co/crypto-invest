package com.cryptoinvest.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Sends one-time codes and stores only keyed hashes of codes and confirmation tokens. */
@Service
public class EmailVerificationService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final ObjectProvider<JavaMailSender> mailSenders;
    private final byte[] key;
    private final String from;

    public EmailVerificationService(JdbcTemplate jdbc, ObjectProvider<JavaMailSender> mailSenders,
            @Value("${app.auth-token-secret}") String secret, @Value("${app.email-from}") String from) {
        this.jdbc = jdbc; this.mailSenders = mailSenders; this.key = Base64.getDecoder().decode(secret); this.from = from;
    }

    @Transactional
    public UUID start(String email, UUID userId, String purpose) {
        String normalized = email.trim().toLowerCase(java.util.Locale.ROOT);
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM account_email_verification WHERE LOWER(email) = ? AND purpose = ? AND created_at > CURRENT_TIMESTAMP - INTERVAL '60 seconds' AND consumed_at IS NULL)", Boolean.class, normalized, purpose)))
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Please wait before requesting another code");
        jdbc.update("UPDATE account_email_verification SET consumed_at = CURRENT_TIMESTAMP WHERE LOWER(email) = ? AND purpose = ? AND user_id IS NOT DISTINCT FROM ? AND consumed_at IS NULL", normalized, purpose, userId);
        String code = String.format(java.util.Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO account_email_verification (challenge_id, user_id, email, purpose, code_hash, created_at, expires_at) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '10 minutes')",
                id, userId, normalized, purpose, hash(code));
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from); message.setTo(normalized); message.setSubject("Crypto Invest 이메일 인증");
        message.setText("인증 코드: " + code + "\n유효 시간은 10분입니다. 요청하지 않았다면 이 메일을 무시하세요.");
        try {
            JavaMailSender sender = mailSenders.getIfAvailable();
            if (sender == null) throw new IllegalStateException("SMTP is not configured");
            sender.send(message);
        } catch (RuntimeException exception) {
            jdbc.update("DELETE FROM account_email_verification WHERE challenge_id = ?", id);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Email delivery is unavailable");
        }
        return id;
    }

    public String confirm(UUID challengeId, String email, String code, String purpose, UUID userId) {
        String normalized = email.trim().toLowerCase(java.util.Locale.ROOT);
        Challenge challenge = jdbc.query("SELECT code_hash, attempts FROM account_email_verification WHERE challenge_id = ? AND LOWER(email) = ? AND purpose = ? AND user_id IS NOT DISTINCT FROM ? AND expires_at > CURRENT_TIMESTAMP AND consumed_at IS NULL AND verified_token_hash IS NULL",
                rs -> rs.next() ? new Challenge(rs.getString(1), rs.getInt(2)) : null, challengeId, normalized, purpose, userId);
        if (challenge == null || challenge.attempts() >= 5) throw new IllegalArgumentException("Invalid or expired verification code");
        if (!java.security.MessageDigest.isEqual(challenge.codeHash().getBytes(StandardCharsets.US_ASCII), hash(code).getBytes(StandardCharsets.US_ASCII))) {
            jdbc.update("UPDATE account_email_verification SET attempts = attempts + 1 WHERE challenge_id = ? AND attempts < 5", challengeId);
            throw new IllegalArgumentException("Invalid or expired verification code");
        }
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        int updated = jdbc.update("UPDATE account_email_verification SET verified_token_hash = ?, token_expires_at = CURRENT_TIMESTAMP + INTERVAL '15 minutes' WHERE challenge_id = ? AND verified_token_hash IS NULL AND attempts < 5 AND expires_at > CURRENT_TIMESTAMP",
                hash(token), challengeId);
        if (updated != 1) throw new IllegalArgumentException("Invalid or expired verification code");
        return token;
    }

    public void consumeSignup(String email, String token) { consume(email, token, "SIGNUP", null); }
    public void consumeEmailChange(UUID userId, String email, String token) { consume(email, token, "EMAIL_CHANGE", userId); }

    private void consume(String email, String token, String purpose, UUID userId) {
        int updated = jdbc.update("UPDATE account_email_verification SET consumed_at = CURRENT_TIMESTAMP WHERE LOWER(email) = ? AND purpose = ? AND user_id IS NOT DISTINCT FROM ? AND verified_token_hash = ? AND token_expires_at > CURRENT_TIMESTAMP AND consumed_at IS NULL",
                email.trim().toLowerCase(java.util.Locale.ROOT), purpose, userId, hash(token == null ? "" : token));
        if (updated != 1) throw new IllegalArgumentException("Email verification is required");
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    public void removeExpiredChallenges() { jdbc.update("DELETE FROM account_email_verification WHERE expires_at < CURRENT_TIMESTAMP - INTERVAL '1 day'"); }

    private String hash(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) { throw new IllegalStateException(exception); }
    }
    private record Challenge(String codeHash, int attempts) {}
}
