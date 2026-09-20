# TODO.md

## 운영 원칙

- Phase는 아래 순서로만 진행하고, 완료된 Phase는 테스트·runtime 결과를 기록한다.
- 기본 거래 모드는 `PAPER`이며 Phase 13 전에는 실거래 주문을 구현·실행하지 않는다.
- 새 테이블 또는 컬럼을 추가할 때는 같은 Flyway migration에 한글 `COMMENT`를 추가한다.
- 기존 migration은 수정하지 않고 새 `V{n}__*.sql` migration을 추가한다.

## 완료

### Phase 1 — 프로젝트 Skeleton / Health Check

- [x] Backend / Frontend skeleton
- [x] `/api/health`, 기본 `PAPER` 모드
- [x] Gradle Wrapper, frontend lint/typecheck/test/build

### Phase 2 — PostgreSQL + Flyway

- [x] PostgreSQL Container, Flyway, `ddl-auto=validate`
- [x] 사용자·거래소 계정·시장·추천·주문·감사 테이블
- [x] 금액/수량 `NUMERIC`, 멱등성/조회 index
- [x] 사용자 거래소 자격증명 AES-256-GCM 암호화 기반
- [x] 모든 현 테이블과 컬럼 한글 comment

### Phase 3 — 거래소 Public Market API

- [x] `ExchangePublicClient` 공통 interface
- [x] Upbit / Bithumb public ticker 및 일봉 candle
- [x] 공통 market model, 입력 검증, timeout·HTTP 오류 처리
- [x] 실제 두 거래소 ticker runtime 확인

## 진행 중

### Phase 4 — Private Read Wallet API

- [x] 사용자별 암호화된 거래소 자격증명 저장/조회 service
- [x] Private API용 HS512 JWT signer
- [x] Upbit 인증 balance adapter
- [x] Bithumb 인증 balance adapter
- [x] 잔고·평균 매수가 공통 model
- [x] 내부 service의 authenticated user ID 기반 계정 분리
- [x] Read-only unit test

## 예정

### Phase 5 — Market Data Normalization

- [x] 시장 데이터 cache 저장
- [x] 거래소 symbol/시간/캔들 정규화
- [x] cache 조회: 최신 200개 제한, refresh 시 upsert로 갱신
- [x] cache 저장·조회 단위 테스트

### Phase 6 — Indicator Engine

- [x] 이동평균, RSI, Momentum, Volatility
- [x] BigDecimal 계산 및 indicator test dataset

### Phase 7 — Recommendation Engine

- [x] Score model, Signal, Recommendation reasons
- [x] Target allocation
- [x] deterministic regression test

### Phase 8 — Portfolio / Risk Engine

- [x] 포트폴리오 평가·비중·rebalancing gap
- [x] OrderPlan, 최소 주문·precision·fee
- [x] RiskEngine, daily limit, asset allocation limit, kill switch

### Phase 9 — Paper Trading

- [x] Virtual wallet, virtual buy/sell order, fee, fill
- [x] In-memory idempotency
- [x] DB order history 및 audit log (사용자별 가상 지갑 transaction 연동, 단위·런타임 검증 완료)
- [x] paper order/audit repository 단위 테스트
- [ ] Paper Trading E2E

### Phase 10 — Auto Investment

- [x] `REBALANCE_ALL`, `KEEP_EXISTING_ASSETS`, `CASH_ONLY` (단위 테스트 완료)
- [x] 기존 OrderPlanner → RiskEngine → TradingService 경로 재사용 (PAPER 체결 경로 단위 테스트 완료)

### Phase 11 — Frontend Dashboard

- [x] Dashboard, portfolio, market, recommendation, trading 화면 (연동 전 읽기 전용 UX)
- [x] API key 미노출·PAPER mode 표시

### Phase 12 — E2E / Security Hardening

- [x] 인증·인가, 사용자별 credential/portfolio/order 격리 (Bearer 토큰·BCrypt·소유자 ID 저장 경로)
- [ ] XSS, CSRF, SQL injection, CORS 검토
- [ ] secret/Authorization header logging 차단
- [ ] timeout recovery, HTTP 429/500, malformed response, insufficient balance
- [ ] Mock/PAPER E2E
- [ ] PostgreSQL Flyway·repository 통합 테스트
- [ ] backend/frontend API 통합 테스트

### Phase 13 — Live Trading

> Phase 12 검증 완료 뒤에만 착수한다.

- [ ] LiveTradingGuard
- [ ] Live order adapter 및 주문 상태 확인
- [ ] timeout recovery: 기존 주문 조회 후 안전할 때만 후속 처리
- [ ] 실거래 권한·일일/종목 제한·kill switch·audit log

## 보류

- 출금 기능
- 레버리지, 선물/옵션, margin trading
- 유료 AI API 기반 추천

## 수동 처리 (Phase와 별도, 완료 시 일괄 안내)

다음은 코드로 안전하게 대신 처리할 수 없거나 사용자 계정 권한이 필요한 항목이다.
Phase 13 완료 보고 전까지는 작업을 막는 경우가 아니면 별도 요청하지 않는다.

- [ ] Docker Desktop 설치 및 실행: PostgreSQL/Redis runtime 검증에 필요
- [ ] JDK 21 설치 및 `JAVA_HOME`을 JDK 21 경로로 설정
- [ ] Node.js 24+ 설치: frontend/E2E 실행에 필요
- [ ] 프로젝트 루트 `.env` 생성: `.env.example`을 복사하고 PostgreSQL 비밀번호 설정
- [ ] `CREDENTIAL_ENCRYPTION_KEY` 생성 및 `.env` 설정: base64 32-byte AES key, DB/Git/Frontend에 저장 금지
- [ ] Upbit API key 발급: 자산 조회·주문 조회·주문만 필요한 최소 권한, 출금 권한 금지
- [ ] Bithumb API key 발급: 자산 조회·주문 조회·주문만 필요한 최소 권한, 출금 권한 금지
- [ ] 사용자별 API key 등록: Phase 12 인증 UI/API가 준비된 뒤에만 수행, `.env` 공용 key로 대체 금지
- [ ] LIVE 전환 승인: Phase 13의 별도 안전 점검·kill switch·한도 검증 후에만 검토
