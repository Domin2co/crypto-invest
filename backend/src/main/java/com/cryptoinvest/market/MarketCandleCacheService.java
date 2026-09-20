package com.cryptoinvest.market;

import com.cryptoinvest.exchange.Exchange;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 정규화된 일봉만 market_candle에 저장한다. 거래소 원본 JSON은 DB에 보관하지 않는다. */
@Service
public class MarketCandleCacheService {
    private final JdbcTemplate jdbcTemplate;
    public MarketCandleCacheService(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public void saveDaily(List<MarketCandle> candles) {
        for (MarketCandle candle : candles) {
            jdbcTemplate.update("""
                    INSERT INTO market_candle (id, exchange, market, candle_interval, opened_at, open_price, high_price, low_price, close_price, volume)
                    VALUES (?, ?, ?, 'DAY', ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (exchange, market, candle_interval, opened_at) DO UPDATE SET
                    open_price = EXCLUDED.open_price, high_price = EXCLUDED.high_price, low_price = EXCLUDED.low_price,
                    close_price = EXCLUDED.close_price, volume = EXCLUDED.volume
                    """, UUID.randomUUID(), candle.exchange().name(), candle.market(), candle.openedAt(), candle.open(), candle.high(), candle.low(), candle.close(), candle.volume());
        }
    }

    /** 최신 저장 순서가 아닌 candle 시작 시각 기준으로 반환한다. */
    public List<MarketCandle> findDaily(Exchange exchange, String market, int count) {
        if (count < 1 || count > 200) throw new IllegalArgumentException("Candle count must be between 1 and 200");
        return jdbcTemplate.query("""
                SELECT opened_at, open_price, high_price, low_price, close_price, volume FROM market_candle
                WHERE exchange = ? AND market = ? AND candle_interval = 'DAY' ORDER BY opened_at DESC LIMIT ?
                """, (rs, row) -> candle(exchange, market, rs), exchange.name(), market, count);
    }

    private static MarketCandle candle(Exchange exchange, String market, ResultSet rs) throws java.sql.SQLException {
        return new MarketCandle(exchange, market, rs.getTimestamp("opened_at").toInstant(), rs.getBigDecimal("open_price"),
                rs.getBigDecimal("high_price"), rs.getBigDecimal("low_price"), rs.getBigDecimal("close_price"), rs.getBigDecimal("volume"));
    }
}
