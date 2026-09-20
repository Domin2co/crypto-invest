package com.cryptoinvest.trading;

import java.math.BigDecimal;
import java.util.UUID;

/** OrderPlanner가 만든 계획을 Paper 체결에 전달할 때 필요한 시세·매도 수량 묶음이다. */
public record AutoInvestmentRequest(UUID orderPlanId, OrderPlan plan, BigDecimal price, BigDecimal sellQuantity, BigDecimal feeRate) {}
