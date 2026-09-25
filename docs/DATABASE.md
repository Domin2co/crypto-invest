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


## 월간 PAPER 대회 (Flyway V12)

`paper_league_entry`는 사용자별 대회 월 참가 및 시작/종료 총 평가액, 수익률, 체결 수, 순위와 메달을 저장한다. 사용자/월 유일 제약으로 중복 참가를 막는다. `user_consent.consent_type`에는 별도 선택 동의인 `PAPER_LEADERBOARD`가 추가되었다. 동의 철회 시 공개 목적의 참가·결과 행은 삭제한다.
## 호가 주문 가격 이력 (Flyway V13)

`order_plan`과 `trade_order`에 `order_type` 및 `limit_price`를 저장한다. 모의거래의 호가 주문은 제출 시점의 공개 시세와 비교해 즉시 체결 가능한 경우에만 체결 이력으로 남긴다. 미체결 지정가 주문을 저장하거나 이후 자동 체결하는 주문 수명주기는 구현하지 않았다.
## LIVE 호가 가격 이력 설명 (Flyway V14)

`order_plan.limit_price`와 `trade_order.limit_price`의 PostgreSQL comment를 PAPER/LIVE 공통 지정가 가격으로 정정했다. V14는 설명 comment만 보완하며 스키마 데이터나 주문 상태를 변경하지 않는다.
## 종목 토론방 (Flyway V15, V16)

`market_discussion_post`는 종목 기호, 작성 사용자 ID, 1,000자 이하 본문과 생성 시각을 저장한다. `(symbol, created_at DESC, id DESC)` 인덱스로 종목별 최신 50개를 조회하며 계정 삭제 절차에서 작성 글을 삭제한다.
V16은 토론 게시글 테이블과 모든 열에 한글 COMMENT를 추가한다.

### V18 email verification and cooldown

Migration V18 adds `email_verified` and `email_changed_at` to `app_user`, preserving legacy accounts as verified and starting their change cooldown at account creation. `account_email_verification` stores signup/email-change challenges, attempt count, expiry and HMAC hashes only; authenticated user challenges are deleted with their account, and expired rows are purged daily.

V19 documents every email-challenge column. V20 restores Korean metadata for the rich discussion post fields introduced in V17. V21 documents the accepted discussion image MIME type.


## 종목 토론 댓글·투표 (Flyway V22, V23)

`market_discussion_comment`는 게시글별 최대 500자 댓글과 작성자, 생성 시각을 저장한다. `market_discussion_vote`는 사용자/게시글당 하나의 투표(-1 또는 1)만 저장하며 사용자가 방향을 변경하면 기존 표를 갱신한다. 양 테이블은 계정 삭제 시 계정 소유 데이터를 cascade 삭제하고, 각 열의 COMMENT는 V23에서 보완한다. 비추천 수 21 이상에서 본문과 첨부 이미지 조회를 모두 제한한다.

## 토론방 수정·신고·관리 권한 (Flyway V24)

V24는 app_user.role(USER/ADMIN), 게시글·댓글 updated_at, 게시글 관리자 숨김 메타데이터와 사용자당 게시글 한 건의 중복 신고를 막는 market_discussion_report를 추가한다. 댓글과 게시글은 페이지 단위로 조회되며 신고 처리 상태는 PENDING/HIDDEN/DISMISSED/RESTORED로 관리한다. 초기 ADMIN 부여는 운영자가 신원 확인 후 검토된 운영 절차로 수행하며 공개 가입 경로로는 권한을 부여할 수 없다. 이후 역할 변경은 감사 로그를 남기는 관리자 전용 도구로 처리한다.

## 관리자 역할 변경 감사 (Flyway V25)

`admin_role_change_audit`는 담당자/대상 사용자 ID, 이전·새 역할, 필수 사유, 변경 시각을 보존한다. ID는 계정 삭제 뒤에도 감사 추적에 남도록 사용자 외래 키를 두지 않는다. 역할 변경 트랜잭션은 PostgreSQL advisory lock으로 직렬화하고 최신 관리자 수를 확인한다.


## 2026-09-25 migrations

- V26 adds `PASSWORD_RESET` to the email verification purpose constraint.
- V27 records the market quote capture time for monthly PAPER opening and final valuation snapshots.

- V29 adds a per-user auth token version; password reset increments it to revoke every prior bearer session.

#



## Recommendation evaluation snapshots (Flyway V33)

`recommendation_evaluation_snapshot` stores a point-in-time, non-user-specific asset evaluation with exchange/market, rule version, regime and signed scores, confidence, rating, indicator/factor/data-quality JSON snapshots, and nullable 7/30-day forward outcomes. The evaluated inputs and scores describe a fixed point in time; only nullable forward-return outcome fields may be filled after their horizons elapse. It contains no portfolio recommendation or order command.

## TOTP 다중 인증 (Flyway V31)

`user_totp_mfa`는 사용자별 암호화 비밀 키, 활성화 상태, 재사용 차단 시간 구간을 저장한다. `user_totp_recovery_code`에는 1회용 복구 코드의 원문 대신 서버 HMAC 지문만 저장한다. 계정 삭제 시 FK cascade로 함께 삭제된다.

## TOTP 컬럼 설명 보완 (Flyway V32)

V31 TOTP 테이블의 식별자, 활성화 상태, 생성·활성화 시각 컬럼에 한국어 데이터베이스 설명을 추가한다. 기존 V31 migration은 변경하지 않는다.

## API 요청 제한 (Flyway V30)

인증 API의 이메일 계정별·IP별 고정 구간 카운터를 PostgreSQL에 원자적으로 기록한다. 제한 키는 서버 인증 비밀키 기반 HMAC 지문이며 이메일·IP 원문을 저장하지 않는다. 만료된 행은 시간별 스케줄로 정리한다.
