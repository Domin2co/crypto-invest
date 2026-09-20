package com.cryptoinvest.exchange.credential;

import com.cryptoinvest.exchange.Exchange;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** exchange_account의 암호문을 관리한다. controller가 아닌 인증된 application service만 호출해야 한다. */
@Service
public class ExchangeAccountCredentialService {
    private final JdbcTemplate jdbcTemplate;
    private final CredentialCipher cipher;

    public ExchangeAccountCredentialService(JdbcTemplate jdbcTemplate, CredentialCipher cipher) {
        this.jdbcTemplate = jdbcTemplate;
        this.cipher = cipher;
    }

    public void save(UUID userId, Exchange exchange, ExchangeCredentials credentials) {
        UUID accountId = jdbcTemplate.query("SELECT id FROM exchange_account WHERE user_id = ? AND exchange = ?",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : UUID.randomUUID(), userId, exchange.name());
        String access = cipher.encrypt(credentials.accessKey(), context(accountId, "access"));
        String secret = cipher.encrypt(credentials.secretKey(), context(accountId, "secret"));
        jdbcTemplate.update("""
                INSERT INTO exchange_account (id, user_id, exchange, encrypted_access_key, encrypted_secret_key)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (user_id, exchange) DO UPDATE SET encrypted_access_key = EXCLUDED.encrypted_access_key,
                encrypted_secret_key = EXCLUDED.encrypted_secret_key, updated_at = CURRENT_TIMESTAMP, enabled = TRUE
                """, accountId, userId, exchange.name(), access, secret);
    }

    public ExchangeCredentials getEnabled(UUID userId, Exchange exchange) {
        return jdbcTemplate.query("SELECT id, encrypted_access_key, encrypted_secret_key FROM exchange_account WHERE user_id = ? AND exchange = ? AND enabled = TRUE",
                rs -> rs.next() ? new ExchangeCredentials(cipher.decrypt(rs.getString(2), context(rs.getObject(1, UUID.class), "access")),
                        cipher.decrypt(rs.getString(3), context(rs.getObject(1, UUID.class), "secret"))) : null,
                userId, exchange.name());
    }

    private static String context(UUID accountId, String field) { return accountId + ":" + field; }
}
