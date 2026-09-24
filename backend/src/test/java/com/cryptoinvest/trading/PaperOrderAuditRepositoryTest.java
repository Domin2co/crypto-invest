package com.cryptoinvest.trading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.cryptoinvest.exchange.Exchange;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class PaperOrderAuditRepositoryTest {
    @Test void recordsPaperOrderAndRedactedAuditEvent() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(any(String.class), any(Object[].class))).thenReturn(1);
        assertThat(new PaperOrderAuditRepository(jdbc).save(UUID.randomUUID(), UUID.randomUUID(), Exchange.UPBIT,
                new PaperTradingService.PaperFill("BTC", "BUY", BigDecimal.ONE, new BigDecimal("10000"), new BigDecimal("10"), "FILLED", "key"), new BigDecimal("1000"))).isTrue();
    }
}
