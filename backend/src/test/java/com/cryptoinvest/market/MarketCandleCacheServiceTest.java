package com.cryptoinvest.market;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import com.cryptoinvest.exchange.Exchange;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class MarketCandleCacheServiceTest {
    @Test void upsertsNormalizedDailyCandleAndRejectsUnsafeReadLimit() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        MarketCandleCacheService service = new MarketCandleCacheService(jdbc);
        service.saveDaily(List.of(new MarketCandle(Exchange.UPBIT, "KRW-BTC", Instant.EPOCH, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE)));
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("ON CONFLICT"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertThatThrownBy(() -> service.findDaily(Exchange.UPBIT, "KRW-BTC", 201)).isInstanceOf(IllegalArgumentException.class);
    }
}
