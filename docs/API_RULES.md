# API_RULES.md

## 1. 목적

Upbit / Bithumb API 차이를 `ExchangeClient` 뒤로 숨기고 내부 서비스는 거래소별 구현에 최소한으로 의존하게 한다.

---

## 2. 공통 Interface 예시

```java
public interface ExchangeClient {

    List<ExchangeBalance> getBalances();

    MarketPrice getPrice(String symbol);

    List<Candle> getCandles(String symbol, CandleInterval interval);

    OrderResult placeOrder(OrderRequest request);

    OrderStatus getOrderStatus(String orderId);
}
```

실제 Interface는 구현 과정에서 조정한다.

---

## 3. Adapter

```text
ExchangeClient
├─ UpbitExchangeClient
├─ BithumbExchangeClient
└─ MockExchangeClient
```

외부 응답은 내부 공통 DTO / Domain Model로 변환한다.

---

## 4. HTTP Client

Spring `WebClient` 사용을 우선한다.

반드시 고려:

- Connect Timeout
- Read Timeout
- Rate Limit
- HTTP Error
- Invalid JSON
- Partial Response

---

## 5. Retry 정책

GET / 조회성 요청:

- 제한적 Retry 가능
- Exponential Backoff 고려

주문 요청:

- 자동 Retry를 기본 금지
- 주문 상태 조회 후 안전할 때만 후속 처리

---

## 6. Rate Limit

거래소 응답 Header 또는 공식 제한 정책을 반영한다.

HTTP `429` 발생 시 무한 재시도하지 않는다.

---

## 7. 인증정보

거래소 인증 Header와 Secret은 Logging하지 않는다.

---

## 8. Public / Private 분리

Public API:

- 현재가
- 호가
- Candle
- 거래량

Private API:

- Balance
- Order
- Order Status

가능하면 Client 내부에서도 책임을 분리한다.

---

## 9. Mock

자동 테스트에서는 `MockExchangeClient`를 사용할 수 있어야 한다.

Mock은 최소한 다음 Scenario를 지원한다.

- 정상 주문
- 잔액 부족
- Timeout
- HTTP 429
- HTTP 500
- 중복 주문
- Partial Fill
