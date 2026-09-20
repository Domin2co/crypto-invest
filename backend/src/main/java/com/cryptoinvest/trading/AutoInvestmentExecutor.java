package com.cryptoinvest.trading;

import com.cryptoinvest.risk.RiskPolicy;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/** 자동투자 mode를 적용한 뒤 기존 Paper 체결 경로에만 주문을 전달한다. */
@Service
public class AutoInvestmentExecutor {
    private final PersistentPaperTradingService paperTradingService;

    public AutoInvestmentExecutor(PersistentPaperTradingService paperTradingService) {
        this.paperTradingService = paperTradingService;
    }

    public List<PaperTradingService.PaperFill> execute(List<AutoInvestmentRequest> requests,
            AutoInvestmentMode mode, RiskPolicy policy) {
        List<PaperTradingService.PaperFill> fills = new ArrayList<>();
        for (AutoInvestmentRequest request : requests) {
            if (!AutoInvestmentService.eligible(List.of(request.plan()), mode, policy).isEmpty()) {
                fills.add(paperTradingService.execute(request.orderPlanId(), request.plan(), request.price(),
                        request.sellQuantity(), request.feeRate(), policy));
            }
        }
        return fills;
    }
}
