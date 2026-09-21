package com.cryptoinvest.trading;

import com.cryptoinvest.exchange.Exchange;
import java.math.BigDecimal;
import java.util.UUID;

/** 실주문 저장 상태의 최소 표현. API key·서명·원문 응답은 이 객체에 넣지 않는다. */
public record LiveOrder(UUID id, Exchange exchange, String clientOrderId, String exchangeOrderId,
        String status, BigDecimal executedQuantity, BigDecimal executedAmount, BigDecimal fee) {}
