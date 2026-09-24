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

## 공개 시장 데이터

- 시장 목록은 각 거래소 공개 KRW 마켓에서 조회한다.
- 캔들 간격은 1/3/5/15/60/240분 및 일/주/월을 공통 OHLCV 모델로 변환한다.
- 현재가, 체결, 호가는 거래소 공개 WebSocket을 사용하고 개인 주문 흐름과 분리한다.

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

## 12. 거래소 전체 KRW 종목 및 차트

- `GET /api/markets/{exchange}/markets`는 Upbit 또는 Bithumb 공개 마켓 목록 중 `KRW-*` 전체를 반환한다. 응답은 `market`, `koreanName`, `englishName`이다.
- `GET /api/markets/{exchange}/candles?market=KRW-BTC&interval=1m&count=100`은 OHLCV 캔들을 최신 거래소 데이터로 조회한다. `interval`은 1/3/5/10/15/30/60/240분, 일(`1d`), 주(`1w`), 월(`1M`)을 허용하고, `count`는 1~200이다.
- 브라우저는 선택된 종목의 ticker·trade·orderbook 공개 WebSocket을 구독한다. Upbit 호가 식별자의 `.15` 접미사는 마켓 코드와 분리하며, Bithumb의 마이크로초 timestamp는 밀리초로 변환한다.
- 목록 조회에는 사용자 인증이나 거래소 API key가 필요하지 않다. 공개 캔들 조회는 주문을 제출하지 않는다.
## 13. 실거래 호가 IOC 주문

인증된 `POST /api/live-trading/orders`는 기존 요청에서 `orderType`을 생략하면 시장가를 유지한다. `orderType: "LIMIT"`이면 양수 `limitPrice`가 필요하다. 매수는 기존 `amount`를 지정가로 나누어 수량을 계산하고, 매도는 `quantity`와 지정가를 사용한다. 거래소로 보내는 지정가 주문은 `time_in_force: "ioc"`를 설정해 미체결 잔량이 대기 주문으로 남지 않게 한다. 이 경로도 최근 사용자 재확인, 전역 실거래 스위치, 일일 한도, 거래소 주문 가능 잔액, RiskEngine 검증을 모두 통과해야 한다.

- [Upbit 주문하기](https://docs.upbit.com/kr/reference/orders), [Bithumb 주문 요청](https://apidocs.bithumb.com/reference/주문-요청): `limit` 지정가와 IOC 잔량 취소 규격을 참고한다.
## 종목별 토론방

- `GET /api/discussions/{symbol}`: 종목별 최신 글 50개를 반환한다. 읽기는 인증 없이 가능하다.
- `POST /api/discussions/{symbol}`: 인증 및 닉네임 설정이 필요하다. JSON body는 `{ "content": "..." }`이며 1~1,000자 일반 텍스트만 허용한다. 성공 시 201과 저장된 게시글을 반환한다.
- `symbol`은 대문자 영문·숫자 2~20자다. 응답에는 작성 닉네임·본문·생성 시각만 공개한다.

## Account security endpoints

- `POST /api/auth/email-verification` `{email}` starts signup verification and returns `202 {challengeId}`; a 60-second resend cooldown applies.
- `POST /api/auth/email-verification/confirm` `{challengeId,email,code}` returns a one-time `verificationToken` after a valid 6-digit code.
- `POST /api/auth/register` now requires `verificationToken` in addition to the existing account fields.
- `PATCH /api/auth/password` requires Bearer authentication and `{currentPassword,newPassword}`.
- `GET /api/account/email-status` returns only the authenticated user's email, verification state and next change time.
- `POST /api/account/email-verification` and `/confirm` verify a new email; `PATCH /api/account/email` consumes the one-time token and changes the email. Changes are blocked until 90 days after signup or the last change (`409 EMAIL_CHANGE_COOLDOWN`).
