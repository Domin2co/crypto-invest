package com.cryptoinvest.portfolio;

import com.cryptoinvest.exchange.Exchange;
import com.cryptoinvest.exchange.publicapi.ExchangePublicClient;
import com.cryptoinvest.market.MarketPrice;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;

/** 인증된 사용자의 읽기 전용 잔고와 공개 시세를 결합하며, 주문·자격증명 응답은 만들지 않는다. */
@Service
public class PortfolioReadService {
    private final AccountReadService accounts;
    private final Map<Exchange, ExchangePublicClient> publicClients;
    private final PortfolioTargetRepository targets;

    public PortfolioReadService(AccountReadService accounts, List<ExchangePublicClient> publicClients, PortfolioTargetRepository targets) {
        this.accounts = accounts;
        this.publicClients = publicClients.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(ExchangePublicClient::exchange, Function.identity()));
        this.targets = targets;
    }

    public PortfolioView read(UUID userId, Exchange exchange) {
        ExchangePublicClient publicClient = publicClients.get(exchange);
        if (publicClient == null) throw new IllegalArgumentException("Unsupported exchange");
        List<ValuedPosition> valued = accounts.getBalances(userId, exchange).stream()
                .filter(balance -> balance.quantity().signum() > 0)
                .map(balance -> value(balance, publicClient)).toList();
        List<BigDecimal> amounts = valued.stream().map(ValuedPosition::evaluatedAmount).toList();
        BigDecimal total = amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, BigDecimal> targetWeights = targets.findByUserAndExchange(userId, exchange);
        List<Position> positions = valued.stream().map(position -> new Position(position.currency(), position.quantity(), position.averageBuyPrice(),
                        position.currentPrice(), position.evaluatedAmount(), PortfolioEngine.weight(position.evaluatedAmount(), amounts),
                        targetWeights.get(position.currency()), targetWeights.containsKey(position.currency())
                                ? PortfolioEngine.rebalancingGap(PortfolioEngine.weight(position.evaluatedAmount(), amounts), targetWeights.get(position.currency())) : null))
                .sorted(Comparator.comparing(Position::evaluatedAmount).reversed()).toList();
        BigDecimal cashWeight = positions.stream().filter(position -> "KRW".equals(position.currency())).map(Position::weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PortfolioView(exchange, total, cashWeight, positions, Instant.now());
    }

    private static ValuedPosition value(ExchangeBalance balance, ExchangePublicClient publicClient) {
        if (balance.currency() == null || !balance.currency().matches("[A-Z0-9]{2,20}")) throw new IllegalStateException("Invalid balance currency");
        BigDecimal price = "KRW".equals(balance.currency()) ? BigDecimal.ONE
                : publicClient.getPrice("KRW-" + balance.currency()).price();
        return new ValuedPosition(balance.currency(), balance.quantity(), balance.averageBuyPrice(), price,
                PortfolioEngine.evaluatedAmount(balance, price));
    }

    private record ValuedPosition(String currency, BigDecimal quantity, BigDecimal averageBuyPrice, BigDecimal currentPrice,
            BigDecimal evaluatedAmount) {}
    public record PortfolioView(Exchange exchange, BigDecimal totalEvaluatedAmount, BigDecimal cashWeight, List<Position> positions,
            Instant capturedAt) {}
    public record Position(String currency, BigDecimal quantity, BigDecimal averageBuyPrice, BigDecimal currentPrice,
            BigDecimal evaluatedAmount, BigDecimal weight, BigDecimal targetWeight, BigDecimal rebalancingGap) {}
}
