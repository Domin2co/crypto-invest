# DATABASE.md

## 실거래 재확인 이력

`user_consent.consent_type`은 `PRIVACY`, `MARKETING`, `LIVE_TRADING`을 허용한다. 실거래 재확인은
기존 동의 이력과 `audit_log`를 함께 사용하므로 별도 테이블을 만들지 않는다.

## 1. DB

기본 DB:

```text
PostgreSQL
```

Schema 변경은 Flyway로 관리한다.

---

## 2. 기본 원칙

- `ddl-auto=create` 사용 금지
- 운영에서는 Schema 자동 변경 금지
- Migration 파일은 순차 추가
- 이미 배포한 Migration 파일은 수정하지 않는다

권장:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

---

## 3. 주요 Entity 후보

### user

다중 사용자 도입 시 사용.

### exchange_account

- user_id
- exchange
- encrypted_access_key
- encrypted_secret_key
- enabled

### asset_snapshot

- exchange
- symbol
- quantity
- average_buy_price
- current_price
- evaluated_amount
- captured_at

### market_candle

필요 시 Cache / 분석용으로 저장.

### recommendation

- symbol
- score
- signal
- target_weight
- reason
- generated_at

### order_plan

실제 주문 전에 계산된 주문 계획.

### trade_order

- exchange
- symbol
- side
- order_type
- requested_quantity
- requested_amount
- executed_quantity
- executed_amount
- fee
- status
- idempotency_key
- created_at
- completed_at

### audit_log

- event_type
- exchange
- symbol
- details
- created_at

### user_consent

- consent_type: 개인정보·마케팅·실거래 재확인
- policy_version
- granted_at
- withdrawn_at

### portfolio_target

- user_id
- exchange
- currency
- target_weight
- updated_at

사용자별 목표 비중 정책이다. 동일 사용자·거래소·통화는 하나만 저장하며, 계정 삭제 시 함께 삭제한다.

---

## 4. Precision

금액 / 수량 Column은 `numeric` / `decimal` 계열을 사용한다.

`float`, `real`, `double precision`은 금융값 저장에 사용하지 않는다.

실제 precision / scale은 거래소 요구사항에 맞춰 결정한다.

---

## 5. Index 후보

- `trade_order.idempotency_key` UNIQUE
- `trade_order.created_at`
- `trade_order.exchange, symbol`
- `recommendation.generated_at`
- `asset_snapshot.captured_at`

실제 Query Pattern 확인 후 확정한다.

---

## 6. Migration Naming

예:

```text
V1__create_base_tables.sql
V2__create_exchange_account.sql
V3__create_trade_order.sql
```

---

## 7. 개인정보 / Secret

거래소 Secret은 평문 저장하지 않는다.

DB Backup에도 암호화된 값만 포함되도록 한다.
