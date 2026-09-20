package com.cryptoinvest.trading;

import com.cryptoinvest.risk.RiskEngine;
import com.cryptoinvest.risk.RiskPolicy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 사용자별 모의 체결·가상 잔고·주문 이력을 하나의 DB transaction으로 처리한다. */
@Service
public class PersistentPaperTradingService {
    private final PaperWalletRepository walletRepository;
    private final PaperOrderAuditRepository orderRepository;
    private final BigDecimal initialKrw;

    public PersistentPaperTradingService(PaperWalletRepository walletRepository, PaperOrderAuditRepository orderRepository,
            @Value("${app.paper-initial-krw:1000000}") BigDecimal initialKrw) {
        this.walletRepository = walletRepository;
        this.orderRepository = orderRepository;
        this.initialKrw = initialKrw;
    }

    /** plan은 RiskEngine을 통과해야 하며, SELL에는 매도 수량이 필요하다. */
    @Transactional
    public synchronized PaperTradingService.PaperFill execute(UUID orderPlanId, OrderPlan plan, BigDecimal price,
            BigDecimal sellQuantity, BigDecimal feeRate, RiskPolicy policy) {
        var existing = orderRepository.findByIdempotencyKey(plan.idempotencyKey());
        if (existing.isPresent()) return existing.get();
        String reason = RiskEngine.rejectReason(plan, policy);
        if (reason != null) throw new IllegalStateException("Paper order rejected: " + reason);
        if (price == null || price.signum() <= 0 || feeRate == null || feeRate.signum() < 0) throw new IllegalArgumentException("Invalid paper price or fee");

        walletRepository.initializeKrw(plan.userId(), initialKrw);
        PaperTradingService.PaperFill fill = "BUY".equals(plan.side())
                ? buy(plan, price, feeRate) : sell(plan, price, sellQuantity, feeRate);
        orderRepository.save(plan.userId(), orderPlanId, plan.exchange(), fill);
        return fill;
    }

    private PaperTradingService.PaperFill buy(OrderPlan plan, BigDecimal price, BigDecimal feeRate) {
        BigDecimal fee = plan.amount().multiply(feeRate);
        walletRepository.subtract(plan.userId(), "KRW", plan.amount().add(fee));
        BigDecimal quantity = plan.amount().divide(price, 18, RoundingMode.DOWN);
        walletRepository.add(plan.userId(), plan.symbol(), quantity);
        return new PaperTradingService.PaperFill(plan.symbol(), "BUY", quantity, plan.amount(), fee, "FILLED", plan.idempotencyKey());
    }

    private PaperTradingService.PaperFill sell(OrderPlan plan, BigDecimal price, BigDecimal quantity, BigDecimal feeRate) {
        if (quantity == null || quantity.signum() <= 0) throw new IllegalArgumentException("Sell quantity is required");
        BigDecimal amount = quantity.multiply(price);
        BigDecimal fee = amount.multiply(feeRate);
        walletRepository.balanceForUpdate(plan.userId(), plan.symbol());
        walletRepository.subtract(plan.userId(), plan.symbol(), quantity);
        walletRepository.add(plan.userId(), "KRW", amount.subtract(fee));
        return new PaperTradingService.PaperFill(plan.symbol(), "SELL", quantity, amount, fee, "FILLED", plan.idempotencyKey());
    }
}
