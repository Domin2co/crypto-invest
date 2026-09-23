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

---

## 10. 공개 추천 조회

`GET /api/recommendations/{exchange}?market=KRW-BTC`는 공개 일봉 15개를 시간순으로 정렬해 RSI,
1일 Momentum, 가격 대비 변동성을 계산한다. 응답은 추천 결과와 생성/데이터 시각, 출처, 표본 수,
지표, 한계를 함께 반환하며 주문 API나 사용자 API key를 사용하지 않는다.

`GET /api/portfolio/{exchange}`는 인증된 사용자 본인의 읽기 전용 잔고와 공개 현재가를 사용해 총 평가액,
현금 비중, 종목별 평가액·비중을 반환한다. 자격증명·주문 기능은 응답에 포함하거나 호출하지 않는다.

`PUT /api/portfolio/{exchange}/targets`는 인증된 본인의 통화별 목표 비중만 저장한다. 각 비중은 0~1,
전체 합계는 1 이하여야 하며 저장 후 조회 결과의 rebalancing gap은 `목표 비중 - 현재 비중`으로 계산한다.

## 11. LIVE 인증·멱등 식별자

- Upbit JWT는 `HS512`를 사용하고 query/body 서명에는 순서가 동일한 query string의 SHA-512 hash를 넣는다.
- Bithumb JWT는 `HS256`으로 서명하며 millisecond `timestamp`가 필수다. query/body hash는 SHA-512를 쓴다.
- Bithumb `client_order_id`는 영문·숫자·`-`·`_`만 허용되고 1~36자다. 내부 멱등성 키로 만든 client ID는 이 제한 안에 있어야 한다.
- Bithumb 응답의 `done`은 IOC/FOK 잔량 취소를 포함할 수 있다. `executed_volume < volume`이면 체결수량을 보존한 종료된 `PARTIALLY_FILLED`로 기록하고 재조회/재주문하지 않는다.
- 관련 공식 규격: [Upbit 인증](https://docs.upbit.com/kr/reference/auth), [Upbit 주문 조회](https://docs.upbit.com/kr/reference/get-order), [Bithumb 인증 토큰](https://apidocs.bithumb.com/docs/인증-토큰-생성하기), [Bithumb 주문 요청](https://apidocs.bithumb.com/reference/주문-요청), [Bithumb 개별 주문 조회](https://apidocs.bithumb.com/v2.1.0/reference/개별-주문-조회).
