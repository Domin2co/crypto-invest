package com.cryptoinvest.risk;

import com.cryptoinvest.trading.OrderPlan;

/** 모든 실행 경로가 호출해야 하는 주문 허용 판정. 거절 사유는 감사 로그에 기록할 수 있다. */
public final class RiskEngine {
    private RiskEngine() {}
    public static String rejectReason(OrderPlan plan, RiskPolicy policy) {
        if (policy.killSwitch()) return "KILL_SWITCH";
        if (plan.amount() == null || plan.amount().compareTo(policy.minOrderAmount()) < 0) return "MIN_ORDER_AMOUNT";
        if (plan.amount().compareTo(policy.maxOrderAmount()) > 0) return "MAX_ORDER_AMOUNT";
        if ("BUY".equals(plan.side()) && plan.projectedWeight().compareTo(policy.maxAssetWeight()) > 0) return "MAX_ASSET_WEIGHT";
        return null;
    }
}
