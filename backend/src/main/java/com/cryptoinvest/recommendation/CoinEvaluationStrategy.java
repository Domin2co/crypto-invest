package com.cryptoinvest.recommendation;

import java.util.Set;

/** Coin profiles name the metrics a strategy can use; absent observations remain unavailable. */
public interface CoinEvaluationStrategy {
    Set<String> metrics();
    static CoinEvaluationStrategy forMarket(String market) {
        return switch (market.substring(market.indexOf('-') + 1).toUpperCase(java.util.Locale.ROOT)) {
            case "BTC" -> new BitcoinEvaluationStrategy();
            case "ETH" -> new EthereumEvaluationStrategy();
            case "SOL" -> new SolanaEvaluationStrategy();
            case "XRP" -> new XrpEvaluationStrategy();
            default -> new DefaultAltcoinEvaluationStrategy();
        };
    }
}
