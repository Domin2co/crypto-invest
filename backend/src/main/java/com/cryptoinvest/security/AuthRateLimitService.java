package com.cryptoinvest.security;

import java.time.Duration;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class AuthRateLimitService {
    private final JdbcTemplate jdbc;
    private final AppTokenService tokens;

    public AuthRateLimitService(JdbcTemplate jdbc, AppTokenService tokens) {
        this.jdbc = jdbc;
        this.tokens = tokens;
    }

    public void check(String action, String email, String ip, int emailLimit, int ipLimit, Duration window) {
        int seconds = Math.toIntExact(window.toSeconds());
        int emailCount = increment(tokens.fingerprint(action + "|email|" + email.trim().toLowerCase(Locale.ROOT)), seconds);
        int ipCount = increment(tokens.fingerprint(action + "|ip|" + ip), seconds);
        if (emailCount > emailLimit || ipCount > ipLimit) {
            throw new RateLimitExceededException();
        }
    }

    private int increment(String key, int windowSeconds) {
        return jdbc.queryForObject("""
                INSERT INTO api_rate_limit_bucket (bucket_key, window_started_at, request_count)
                VALUES (?, CURRENT_TIMESTAMP, 1)
                ON CONFLICT (bucket_key) DO UPDATE SET
                  window_started_at = CASE WHEN api_rate_limit_bucket.window_started_at <= CURRENT_TIMESTAMP - (? * INTERVAL '1 second') THEN CURRENT_TIMESTAMP ELSE api_rate_limit_bucket.window_started_at END,
                  request_count = CASE WHEN api_rate_limit_bucket.window_started_at <= CURRENT_TIMESTAMP - (? * INTERVAL '1 second') THEN 1 ELSE api_rate_limit_bucket.request_count + 1 END
                RETURNING request_count
                """, Integer.class, key, windowSeconds, windowSeconds);
    }

    public static class RateLimitExceededException extends RuntimeException {}

    @Scheduled(cron = "0 17 * * * *")
    public void removeExpiredBuckets() {
        jdbc.update("DELETE FROM api_rate_limit_bucket WHERE window_started_at < CURRENT_TIMESTAMP - INTERVAL '1 day'");
    }
}

