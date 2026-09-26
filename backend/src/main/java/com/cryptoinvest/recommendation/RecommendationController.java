package com.cryptoinvest.recommendation;

import com.cryptoinvest.exchange.Exchange;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 추천 조회는 공개 시장 데이터만 사용하며, 결과를 실제 주문으로 연결하지 않는다. */
@RestController
@Validated
@RequestMapping("/api/recommendations")
public class RecommendationController {
    private final RecommendationService recommendationService;
    private final RecommendationSnapshotRepository snapshots;

    public RecommendationController(RecommendationService recommendationService, RecommendationSnapshotRepository snapshots) { this.recommendationService = recommendationService; this.snapshots = snapshots; }

    @GetMapping("/backtest")
    public BacktestView backtest(@RequestParam @Pattern(regexp = "7|30") String days) {
        return new BacktestView(Integer.parseInt(days), snapshots.backtest(Integer.parseInt(days)), "Historical observed returns only; excludes fees, slippage and survivorship bias.");
    }
    public record BacktestView(int days, java.util.List<RecommendationSnapshotRepository.BacktestGroup> groups, String limitations) {}

    @GetMapping("/{exchange}")
    public RecommendationService.RecommendationView recommend(@PathVariable Exchange exchange,
            @RequestParam @NotBlank @Pattern(regexp = "[A-Z]{2,10}-[A-Z0-9]{2,20}") String market) {
        return recommendationService.recommend(exchange, market);
    }
}
