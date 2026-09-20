# TABLERULE.md

## 원칙

- 스키마 변경은 `backend/src/main/resources/db/migration/V{n}__{description}.sql`에만 추가한다.
  적용한 migration은 수정하지 않는다.
- 식별자는 `UUID`, 시각은 `TIMESTAMP WITH TIME ZONE`, 금액·가격·수량·비중은
  `NUMERIC`을 사용한다. `float`, `double`, `real`은 사용하지 않는다.
- 테이블은 단수형이 아닌 `snake_case` 복수/집합 명사와 일관된 `id`, `created_at`,
  `updated_at`을 사용한다. 불변 이벤트(`audit_log`, snapshot)는 `updated_at`을 두지 않는다.
- 외부 식별자, symbol, enum은 필요한 최대 길이를 제한하고, 설명·근거·redacted audit
  payload처럼 크기가 본질적으로 가변적인 데이터만 `TEXT` 또는 `JSONB`를 사용한다.

## 사용자 및 자격증명

| 테이블.컬럼 | 타입 | 판단 |
| --- | --- | --- |
| `app_user.id` | `UUID` | DB extension 없이 application에서 생성한다. |
| `app_user.email` | `VARCHAR(254)` | RFC 이메일 최대 길이. 로그인 구현 전에도 유일 사용자 기준을 제공한다. |
| `exchange_account.exchange` | `VARCHAR(20)` | `UPBIT`, `BITHUMB` enum 값에 충분하며 check constraint로 제한한다. |
| `exchange_account.encrypted_access_key` | `VARCHAR(1024)` | base64url AES-GCM envelope와 향후 key 길이 증가를 수용한다. |
| `exchange_account.encrypted_secret_key` | `VARCHAR(2048)` | secret이 access key보다 길 수 있으므로 별도 여유를 둔다. |
| `exchange_account.key_version` | `SMALLINT` | master key rotation 시 복호화 key를 선택한다. |

자격증명 원문, 복호화 결과, Authorization header, 요청 서명은 저장·응답·로그에 포함하지
않는다. 암호문은 `v1.<nonce>.<ciphertext>` 형식이며 AES-256-GCM의 96-bit nonce와
exchange-account/필드별 AAD를 사용한다. master key는 `CREDENTIAL_ENCRYPTION_KEY`에
base64로 주입하며 DB에 저장하지 않는다.

## 거래 데이터

| 용도 | 핵심 길이/정밀도 |
| --- | --- |
| symbol, market | `VARCHAR(32)` — `KRW-BTC` 및 향후 거래소 symbol에 충분 |
| order id, idempotency key | `VARCHAR(128)` — 거래소 ID와 내부 key를 수용 |
| monetary value | `NUMERIC(38,18)` — KRW 금액과 소수 수량을 같은 규칙으로 처리 |
| target weight | `NUMERIC(9,8)` — 0~1 비중 |
| score | `NUMERIC(5,2)` — 0~100 점수 |
| order side/type/status/mode | `VARCHAR(20|32)`와 check constraint |

`asset_snapshot`, `market_candle`, `recommendation`, `order_plan`, `trade_order`,
`audit_log`는 future phase에서 사용하는 데이터 계약을 먼저 만든다. 실제 주문은
`trade_order.trading_mode = 'PAPER'`만 허용하는 Phase 9 전까지 생성하지 않는다.

## 조회와 제약

- 사용자별 거래소 계정은 `(user_id, exchange)` unique다.
- 시장 캔들은 `(exchange, market, candle_interval, opened_at)` unique다.
- 주문의 `idempotency_key`는 unique다.
- 모든 사용자 소유 데이터는 `user_id` foreign key를 가진다. API 구현 시 인증된
  user id로만 조회 조건을 구성하며, client가 임의 `user_id`를 선택하지 못하게 한다.
- audit payload는 secret을 제거한 JSON만 허용한다.
