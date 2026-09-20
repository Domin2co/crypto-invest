package com.cryptoinvest.trading;

import com.cryptoinvest.risk.RiskEngine;
import com.cryptoinvest.risk.RiskPolicy;
import java.util.List;

/** 자동투자 주문 후보를 기존 RiskEngine으로만 통과시킨다. adapter 실행은 이 service의 책임이 아니다. */
public final class AutoInvestmentService {
    private AutoInvestmentService() {}
    public static List<OrderPlan> eligible(List<OrderPlan> plans, AutoInvestmentMode mode, RiskPolicy policy) {
        return plans.stream().filter(p -> !"SELL".equals(p.side()) || AutoInvestmentPolicy.allowsSell(mode))
                .filter(p -> RiskEngine.rejectReason(p, policy) == null).toList();
    }
}
