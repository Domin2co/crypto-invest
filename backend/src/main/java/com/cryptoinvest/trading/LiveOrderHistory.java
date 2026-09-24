package com.cryptoinvest.trading;

import java.math.BigDecimal;
import java.time.Instant;

public record LiveOrderHistory(String exchange, String market, String side, String orderType, BigDecimal limitPrice, BigDecimal requestedQuantity,
        BigDecimal requestedAmount, BigDecimal executedQuantity, BigDecimal executedAmount, BigDecimal fee,
        String status, Instant createdAt) {}
