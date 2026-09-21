package com.cryptoinvest.exchange.publicapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** HTTP timeout·상태 코드·JSON 처리만 공통화한다. 주문이나 인증 정보는 취급하지 않는다. */
public abstract class AbstractPublicClient {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    protected final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Duration timeout;

    protected AbstractPublicClient(ObjectMapper objectMapper) { this(objectMapper, HttpClient.newBuilder().connectTimeout(TIMEOUT).build(), TIMEOUT); }
    protected AbstractPublicClient(ObjectMapper objectMapper, HttpClient httpClient, Duration timeout) {
        this.objectMapper = objectMapper; this.httpClient = httpClient; this.timeout = timeout;
    }

    /** 공개 API는 자동 재시도하지 않아 rate limit과 장애 중 요청 증폭을 피한다. */
    protected JsonNode get(String url) {
        return get(url, null);
    }

    /** 인증 header 값은 호출 직후 폐기하며 exception이나 log에 포함하지 않는다. */
    protected JsonNode get(String url, String bearerToken) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).timeout(timeout).header("Accept", "application/json").GET();
            if (bearerToken != null) request.header("Authorization", bearerToken);
            HttpResponse<String> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ExchangeApiException("Exchange public API returned HTTP " + response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (IOException exception) {
            throw new ExchangeApiException("Exchange public API request failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ExchangeApiException("Exchange public API request interrupted", exception);
        }
    }

    protected static void validate(String market, int count) {
        if (market == null || !market.matches("[A-Z]{2,10}-[A-Z0-9]{2,20}")) throw new IllegalArgumentException("Invalid market");
        if (count < 1 || count > 200) throw new IllegalArgumentException("Candle count must be between 1 and 200");
    }
}
