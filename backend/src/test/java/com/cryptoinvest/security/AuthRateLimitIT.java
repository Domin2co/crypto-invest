package com.cryptoinvest.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AuthRateLimitIT {
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.auth-token-secret", () -> Base64.getEncoder().encodeToString(new byte[32]));
        registry.add("app.credential-encryption-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
        registry.add("management.health.mail.enabled", () -> "false");
    }

    @Autowired AuthRateLimitService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired AppTokenService tokens;

    @Test
    void limitsRequestsWithoutPersistingEmailOrIpAndResetsExpiredWindow() {
        String email = "limit-" + java.util.UUID.randomUUID() + "@example.com";
        String ip = "192.0.2.77";
        service.check("login", email, ip, 1, 10, Duration.ofMinutes(15));
        assertThatThrownBy(() -> service.check("login", email, "192.0.2.78", 1, 10, Duration.ofMinutes(15)))
                .isInstanceOf(AuthRateLimitService.RateLimitExceededException.class);
        String emailKey = tokens.fingerprint("login|email|" + email.toLowerCase(java.util.Locale.ROOT));
        String ipKey = tokens.fingerprint("login|ip|" + ip);
        assertThat(emailKey).doesNotContain(email, ip);
        assertThat(ipKey).doesNotContain(email, ip);
        assertThat(jdbc.queryForObject("SELECT request_count FROM api_rate_limit_bucket WHERE bucket_key = ?", Integer.class, emailKey)).isEqualTo(2);
        jdbc.update("UPDATE api_rate_limit_bucket SET window_started_at = CURRENT_TIMESTAMP - INTERVAL '16 minutes' WHERE bucket_key IN (?, ?)", emailKey, ipKey);
        service.check("login", email, ip, 1, 10, Duration.ofMinutes(15));
        assertThat(jdbc.queryForObject("SELECT request_count FROM api_rate_limit_bucket WHERE bucket_key = ?", Integer.class, emailKey)).isEqualTo(1);

        service.check("ip-limit", "first@example.com", ip, 10, 1, Duration.ofMinutes(15));
        assertThatThrownBy(() -> service.check("ip-limit", "second@example.com", ip, 10, 1, Duration.ofMinutes(15)))
                .isInstanceOf(AuthRateLimitService.RateLimitExceededException.class);
    }
}
