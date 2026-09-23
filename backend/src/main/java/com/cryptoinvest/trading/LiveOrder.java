package com.cryptoinvest.trading;

import com.cryptoinvest.exchange.Exchange;
import java.math.BigDecimal;
import java.util.UUID;

/** 실주문 저장 상태의 최소 표현. terminal은 부분 체결 포함, 추가 상태 조회가 필요 없는 종료 여부다. */
public record LiveOrder(UUID id, Exchange exchange, String clientOrderId, String exchangeOrderId,
        String status, BigDecimal executedQuantity, BigDecimal executedAmount, BigDecimal fee, boolean terminal) {}
