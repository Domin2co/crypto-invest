package com.cryptoinvest.trading;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/** 외부 거래소를 호출하지 않는 가상 체결. 동시 사용자 확장 전까지 단일 process paper wallet이다. */
public class PaperTradingService {
    private final Map<String, BigDecimal> balances = new HashMap<>();
    private final Map<String, PaperFill> fills = new HashMap<>();
    public PaperTradingService(BigDecimal initialKrw) { balances.put("KRW", initialKrw); }

    public synchronized PaperFill buy(String symbol, BigDecimal amount, BigDecimal price, BigDecimal feeRate, String idempotencyKey) {
        if (fills.containsKey(idempotencyKey)) return fills.get(idempotencyKey);
        BigDecimal fee = amount.multiply(feeRate), total = amount.add(fee);
        if (balances.get("KRW").compareTo(total) < 0) throw new IllegalStateException("Insufficient paper balance");
        BigDecimal quantity = amount.divide(price, 18, java.math.RoundingMode.DOWN);
        balances.put("KRW", balances.get("KRW").subtract(total)); balances.merge(symbol, quantity, BigDecimal::add);
        PaperFill fill = new PaperFill(symbol, "BUY", quantity, amount, fee, "FILLED", idempotencyKey); fills.put(idempotencyKey, fill); return fill;
    }
    public synchronized PaperFill sell(String symbol, BigDecimal quantity, BigDecimal price, BigDecimal feeRate, String idempotencyKey) {
        if (fills.containsKey(idempotencyKey)) return fills.get(idempotencyKey);
        if (balances.getOrDefault(symbol, BigDecimal.ZERO).compareTo(quantity) < 0) throw new IllegalStateException("Insufficient paper asset");
        BigDecimal amount = quantity.multiply(price), fee = amount.multiply(feeRate);
        balances.put(symbol, balances.get(symbol).subtract(quantity)); balances.merge("KRW", amount.subtract(fee), BigDecimal::add);
        PaperFill fill = new PaperFill(symbol, "SELL", quantity, amount, fee, "FILLED", idempotencyKey); fills.put(idempotencyKey, fill); return fill;
    }
    public BigDecimal balance(String currency) { return balances.getOrDefault(currency, BigDecimal.ZERO); }
    /** ponytail: global lock, per-account locks if paper trading throughput matters. */
    public record PaperFill(String symbol, String side, BigDecimal quantity, BigDecimal amount, BigDecimal fee, String status, String idempotencyKey) {}
}
