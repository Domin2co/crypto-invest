package com.cryptoinvest.exchange.privateapi;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;

/** 거래소가 반환한 주문별 최소 금액·수수료·가용 잔고를 주문 위험 검증에 전달한다. */
public record ExchangeOrderChance(String market, String state, String quoteCurrency, String baseCurrency,
        BigDecimal availableQuote, BigDecimal availableBase, BigDecimal minimumBidAmount,
        BigDecimal minimumAskAmount, BigDecimal maximumOrderAmount, BigDecimal bidFee, BigDecimal askFee) {
    public static ExchangeOrderChance from(JsonNode response) {
        JsonNode market = response.path("market");
        JsonNode bidAccount = response.path("bid_account");
        JsonNode askAccount = response.path("ask_account");
        return new ExchangeOrderChance(requiredText(market, "id"), requiredText(market, "state"),
                requiredText(bidAccount, "currency"), requiredText(askAccount, "currency"),
                requiredAmount(bidAccount, "balance"), requiredAmount(askAccount, "balance"),
                requiredAmount(market.path("bid"), "min_total"), requiredAmount(market.path("ask"), "min_total"),
                requiredAmount(market, "max_total"), requiredAmount(response, "bid_fee"), requiredAmount(response, "ask_fee"));
    }

    private static String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        if (value == null || value.isBlank()) throw new IllegalStateException("Exchange order rules are unavailable");
        return value;
    }

    private static BigDecimal requiredAmount(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        try {
            if (value == null) throw new NumberFormatException();
            BigDecimal amount = new BigDecimal(value);
            if (amount.signum() < 0) throw new NumberFormatException();
            return amount;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Exchange order rules are unavailable");
        }
    }
}
