package com.cryptoinvest.trading;

import com.cryptoinvest.exchange.Exchange;
import com.cryptoinvest.exchange.credential.ExchangeCredentials;
import com.cryptoinvest.exchange.credential.JwtSigner;
import com.cryptoinvest.exchange.publicapi.ExchangeApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Upbit·Bithumb의 주문/주문조회 차이만 변환한다. 호출 전 LiveTradingGuard가 반드시 실행된다. */
@Component
public class LiveOrderClient {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private final ObjectMapper objectMapper;
    private final JwtSigner signer;
    private final HttpClient httpClient;
    private final String upbitBaseUrl;
    private final String bithumbBaseUrl;

    @Autowired
    public LiveOrderClient(ObjectMapper objectMapper, JwtSigner signer,
            @Value("${app.upbit-api-base-url:https://api.upbit.com}") String upbitBaseUrl,
            @Value("${app.bithumb-api-base-url:https://api.bithumb.com}") String bithumbBaseUrl) {
        this(objectMapper, signer, HttpClient.newBuilder().connectTimeout(TIMEOUT).build(), upbitBaseUrl, bithumbBaseUrl);
    }

    LiveOrderClient(ObjectMapper objectMapper, JwtSigner signer, HttpClient httpClient, String upbitBaseUrl, String bithumbBaseUrl) {
        this.objectMapper = objectMapper; this.signer = signer; this.httpClient = httpClient;
        this.upbitBaseUrl = stripTrailingSlash(upbitBaseUrl); this.bithumbBaseUrl = stripTrailingSlash(bithumbBaseUrl);
    }

    public LiveOrder submit(OrderPlan plan, BigDecimal sellQuantity, String clientOrderId, ExchangeCredentials credentials) {
        LinkedHashMap<String, String> body = orderBody(plan, sellQuantity, clientOrderId);
        String query = queryString(body);
        JsonNode response = request(plan.exchange(), "POST", baseUrl(plan.exchange()) + "/" + orderPath(plan.exchange()), query,
                json(body), credentials);
        return result(plan.exchange(), clientOrderId, response);
    }

    public LiveOrder findByClientOrderId(Exchange exchange, String clientOrderId, ExchangeCredentials credentials) {
        String field = exchange == Exchange.UPBIT ? "identifier" : "client_order_id";
        String query = field + "=" + clientOrderId;
        JsonNode response = request(exchange, "GET", baseUrl(exchange) + "/v1/order?" + query, query, null, credentials);
        return result(exchange, clientOrderId, response);
    }

    private JsonNode request(Exchange exchange, String method, String url, String query, String body, ExchangeCredentials credentials) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT)
                    .header("Accept", "application/json").header("Authorization", signer.bearerTokenForQuery(credentials, query,
                            exchange == Exchange.UPBIT ? "HS512" : "HS256", exchange == Exchange.BITHUMB));
            if ("POST".equals(method)) request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
            else request.GET();
            HttpResponse<String> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                // A gateway/timeout or duplicate client ID can happen after acceptance; query the saved ID instead of marking failed.
                if (response.statusCode() == 408 || response.statusCode() >= 500
                        || ("POST".equals(method) && response.statusCode() == 409)) {
                    throw new LiveOrderUnknownResultException("Exchange order API result is unknown (HTTP " + response.statusCode() + ")");
                }
                throw new ExchangeApiException("Exchange order API returned HTTP " + response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new LiveOrderUnknownResultException("Live order request timed out", exception);
        } catch (IOException exception) {
            throw new LiveOrderUnknownResultException("Live order request result is unknown", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new LiveOrderUnknownResultException("Live order request interrupted", exception);
        }
    }

    private LinkedHashMap<String, String> orderBody(OrderPlan plan, BigDecimal sellQuantity, String clientOrderId) {
        if (!"BUY".equals(plan.side()) && !"SELL".equals(plan.side())) throw new IllegalArgumentException("Unsupported order side");
        LinkedHashMap<String, String> body = new LinkedHashMap<>();
        body.put("market", plan.symbol());
        body.put("side", "BUY".equals(plan.side()) ? "bid" : "ask");
        if ("BUY".equals(plan.side())) {
            body.put(plan.exchange() == Exchange.UPBIT ? "ord_type" : "order_type", "price");
            body.put("price", plan.amount().toPlainString());
        } else {
            if (sellQuantity == null || sellQuantity.signum() <= 0) throw new IllegalArgumentException("Live sell quantity is required");
            body.put(plan.exchange() == Exchange.UPBIT ? "ord_type" : "order_type", "market");
            body.put("volume", sellQuantity.toPlainString());
        }
        body.put(plan.exchange() == Exchange.UPBIT ? "identifier" : "client_order_id", clientOrderId);
        return body;
    }

    private LiveOrder result(Exchange exchange, String clientOrderId, JsonNode node) {
        String exchangeOrderId = text(node, "uuid", "order_id", "id");
        String state = text(node, "state", "status");
        BigDecimal executedQuantity = decimal(node, "executed_volume");
        BigDecimal volume = decimal(node, "volume");
        boolean terminal = "done".equals(state) || "filled".equals(state) || "cancel".equals(state) || "cancelled".equals(state);
        String status = status(state);
        if (terminal && ("done".equals(state) || "filled".equals(state)) && volume.signum() > 0 && executedQuantity.compareTo(volume) < 0) {
            status = executedQuantity.signum() > 0 ? "PARTIALLY_FILLED" : "UNKNOWN";
        }
        return new LiveOrder(null, exchange, clientOrderId, exchangeOrderId, status,
                executedQuantity, decimal(node, "executed_funds", "executed_amount"), decimal(node, "paid_fee", "fee"), terminal);
    }

    private static String status(String state) {
        if (state == null) return "UNKNOWN";
        return switch (state) {
            case "wait", "watch", "pending" -> "SUBMITTED";
            case "trade" -> "PARTIALLY_FILLED";
            case "done", "filled" -> "FILLED";
            case "cancel", "cancelled" -> "CANCELLED";
            default -> "UNKNOWN";
        };
    }
    private String json(Map<String, String> values) {
        try { return objectMapper.writeValueAsString(values); }
        catch (com.fasterxml.jackson.core.JsonProcessingException exception) { throw new IllegalStateException("Order JSON serialization failed", exception); }
    }
    private static String queryString(Map<String, String> values) { return values.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue()).collect(java.util.stream.Collectors.joining("&")); }
    private String baseUrl(Exchange exchange) { return exchange == Exchange.UPBIT ? upbitBaseUrl : bithumbBaseUrl; }
    private static String orderPath(Exchange exchange) { return exchange == Exchange.UPBIT ? "v1/orders" : "v2/orders"; }
    private static String stripTrailingSlash(String baseUrl) { return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl; }
    private static String text(JsonNode node, String... names) { for (String name : names) if (!node.path(name).isMissingNode() && !node.path(name).isNull()) return node.path(name).asText(); return null; }
    private static BigDecimal decimal(JsonNode node, String... names) {
        for (String name : names) if (node.hasNonNull(name)) return new BigDecimal(node.path(name).asText());
        return BigDecimal.ZERO;
    }
}
