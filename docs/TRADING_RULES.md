# TRADING_RULES.md

## 1. 기본 원칙

추천과 실제 주문은 분리한다.

```text
RecommendationEngine
        ↓
PortfolioEngine
        ↓
RiskEngine
        ↓
OrderPlanner
        ↓
TradingService
```

추천 결과가 곧 주문을 의미하지 않는다.

---

## 2. 거래 모드

### PAPER

가상 주문.

실제 거래소 주문 API를 호출하지 않는다.

### READ_ONLY

실제 계정 및 시장 데이터는 읽을 수 있지만 주문하지 않는다.

### LIVE

실제 주문 허용.

실거래는 기본 비활성화한다.

---

## 3. 자동투자 모드

### REBALANCE_ALL

현재 보유 자산을 목표 비율에 맞게 재배치할 수 있다.

### KEEP_EXISTING_ASSETS

기존 보유 자산은 가능한 한 유지하고 신규 현금을 이용해 목표 비중에 접근한다.

### CASH_ONLY

현재 사용 가능한 현금만 투자한다.

기존 보유 코인을 자동 매도하지 않는다.

---

## 4. 추천 결과

예:

```json
{
  "symbol": "BTC",
  "score": 73,
  "signal": "ACCUMULATE",
  "targetWeight": 0.35,
  "reasons": [
    "장기 추세 양호",
    "RSI 중립",
    "Momentum 양호",
    "변동성 Risk Penalty 적용"
  ]
}
```

---

## 5. Risk 검증

주문 전 확인:

- Trading Mode
- Live Trading 활성화
- 잔액
- 최소 주문금액
- 최대 주문금액
- 일일 투자 한도
- 종목별 최대 비중
- 가격 Staleness
- 중복 주문
- 거래소 API 상태
- 사용자 자동투자 설정

---

## 6. Idempotency

동일 주문이 여러 번 실행되지 않도록 한다.

예:

```text
AUTO-20260920-BTC-000001
```

DB Unique Constraint 또는 동등한 보호 장치를 사용한다.

---

## 7. Retry

조회 API는 제한적인 Retry 가능.

주문 API는 무조건 Retry 금지.

Timeout 시:

```text
Order Request
→ Unknown Result
→ Query Order Status
→ Determine Existing Execution
→ Retry only if safe
```

---

## 8. 금융 계산

모든 금액과 수량은 `BigDecimal`.

거래소별:

- 최소 주문 금액
- 호가 단위
- 수량 Precision
- 수수료

를 반영한다.

---

## 9. 향후 결정할 값

아래 값은 코드에 Hard Coding하지 않고 설정 또는 정책으로 관리하는 것을 우선한다.

- 종목별 최대 투자 비율
- 일일 투자 한도
- 최소 현금 비중
- Score Threshold
- Stop / Pause Rule

실제 수치는 구현 단계에서 별도로 결정한다.
