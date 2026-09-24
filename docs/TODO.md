# TODO.md

## 2026-09-24 필수 닉네임 설정

- [x] 가입/로그인 뒤 닉네임이 없는 계정은 설정 dialog로 제한, 저장 전 중복확인 필수
- [x] 2~8자 한글·영문·숫자 규칙, 대소문자 무시 중복 제약 및 계정 닉네임 변경
- [x] 닉네임 미설정 계정의 인증 API 접근을 서버에서도 차단하고 migration V11로 기존 계정 적용

## 2026-09-24 월간 PAPER 대회

- [x] 별도 선택 공개 동의, 다음 달 참가 신청, 월별 평가액·체결 수·순위·상위 3개 메달 (Flyway V12)
- [x] 모의거래 화면에 현재 랭킹·최근 메달·참가 신청 연결; 동의 철회 시 기록 제거
- [x] 월별 open/close PostgreSQL transaction advisory lock으로 다중 인스턴스 중복 집계 방지; 첫 5분 매분 재시도 및 실패 로그 추가
- [ ] Backend가 한국 시간 월 1일 첫 5분 내내 중단되거나 시세가 끊긴 경우의 정확한 복구는 과거 잔고 스냅샷이 없어 미지원; 운영 전 이력 데이터/월 마감 정책 결정


## 2026-09-22 Phase 13 보완

- [x] 기존 `user_consent`에 `LIVE_TRADING` 재확인 유형 추가(Flyway V8, 모든 변경 컬럼 comment 유지)
- [x] 재확인 API, 15분 유효시간 검증, `LIVE_TRADING_CONFIRMED` 및 주문 상태 감사 이력
- [x] 재확인 누락 시 RiskEngine·복호화·거래소 호출 이전에 거절하는 단위 테스트
- [ ] 운영 승인, 실제 거래소 권한/주문 상태 조회 검증 및 법무 출시 게이트는 수동 처리 항목으로 유지

## 2026-09-22 Phase 9 PAPER 거래 E2E 보완

- [x] 인증된 사용자 PAPER 주문 API와 사용자별 주문 계획·지갑·감사 이력 연결
- [x] PAPER 매수·매도 UI (Upbit/Bithumb 실시간 공개 시세, 거래소별 가상 지갑, 체결 수량·단가·수수료 표시; 실제 주문/API key 미사용)
- [x] 가입 후 PAPER 매수·매도 체결(HTTP 201) Browser E2E 및 320px 접근성 회귀 검증

## 운영 원칙

- Phase는 아래 순서로만 진행하고, 완료된 Phase는 테스트·runtime 결과를 기록한다.
- 모의거래는 전용 API로 분리한다. 실주문은 기본 잠금 상태이며 자동 테스트에서 실행하지 않는다.
- 새 테이블 또는 컬럼을 추가할 때는 같은 Flyway migration에 한글 `COMMENT`를 추가한다.
- 기존 migration은 수정하지 않고 새 `V{n}__*.sql` migration을 추가한다.
- 개인정보·시큐어코딩·추천 투명성·접근성·운영 기준은 `AGENTS.md`와 신규 운영 문서를 상시 적용한다.

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

### Phase 4 — Private Read Wallet API

- [x] 사용자별 암호화된 거래소 자격증명 저장/조회 service
- [x] Private API용 HS512 JWT signer
- [x] Upbit 인증 balance adapter
- [x] Bithumb 인증 balance adapter
- [x] 잔고·평균 매수가 공통 model
- [x] 내부 service의 authenticated user ID 기반 계정 분리
- [x] Read-only unit test

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
- [x] Paper Trading E2E (가입 후 사용자별 PAPER 매수·매도 HTTP 201, 실제 거래소 주문 미호출)

### Phase 10 — Auto Investment

- [x] `REBALANCE_ALL`, `KEEP_EXISTING_ASSETS`, `CASH_ONLY` (단위 테스트 완료)
- [x] 기존 OrderPlanner → RiskEngine → TradingService 경로 재사용 (PAPER 체결 경로 단위 테스트 완료)

### Phase 11 — Frontend Dashboard

- [x] Dashboard, 시장 시세, PAPER 거래·주문 내역 화면
- [x] 공개 일봉 기반 추천 API 연동 및 데이터 시각·출처·지표·한계 표시
- [x] 사용자 연동 계정의 실제 읽기 전용 포트폴리오(총 평가·현금/종목 비중) 표시
- [x] 사용자별 목표 비중 정책 저장 및 rebalancing gap 표시(Flyway V9, 모든 테이블·컬럼 한글 comment)
- [x] API key 미노출·PAPER mode 표시

### Phase 12 — E2E / Security Hardening

- [x] 인증·인가, 사용자별 credential/portfolio/order 격리 (Bearer 토큰·BCrypt·소유자 ID 저장 경로)
- [x] XSS, CSRF, SQL injection, CORS 검토 (React 기본 escaping·입력 검증·JDBC bind parameter·stateless bearer 및 허용/차단 CORS 통합 테스트)
- [x] secret/Authorization header logging 차단 (request detail·Tomcat access log 비활성화, Bearer filter 무로그 확인)
- [x] timeout recovery, HTTP 429/500, malformed response, insufficient balance (읽기 API 재시도 금지·timeout/오류 단위 테스트, PAPER 잔고 거절 테스트)
- [x] PAPER/LIVE 멱등성 조회의 사용자 소유자 범위 및 타 사용자 키 충돌 시 재사용 차단·PAPER 롤백 검증
- [x] Mock/PAPER E2E (PAPER UI·credential 미노출·공개 시세 E2E 완료)
- [x] PostgreSQL Flyway·repository 통합 테스트 (rollback 격리된 실제 DB 검증 완료)
- [x] backend/frontend API 통합 테스트 (Vite `/api` proxy·실제 공개 시세 runtime/E2E 완료)

## 진행 중

### Phase 13 — Live Trading

> Phase 12 검증 완료 뒤에만 착수한다.

- [x] LiveTradingGuard의 기본 kill switch·일일 한도·RiskEngine 검증 단위 테스트
- [x] Live order adapter 및 주문 상태 확인 (Upbit HS512 / Bithumb HS256+timestamp·SHA-512 query hash·36자 client ID 단위 테스트)
- [x] timeout/process recovery: 제출 의도 커밋, timeout/408/5xx/client ID 충돌 시 재전송 없이 조회, 종료된 부분 체결 수량·완료 시각 보존
- [x] 인증된 `/api/live-trading/orders`에서 사용자 소유 계정·실제 잔고·최신 시세로 OrderPlan을 구성하고 RiskEngine, 멱등성, 재확인, kill switch 경로에 연결 (기본 PAPER/kill switch로 LIVE 실행 차단)
- [ ] 사용자별 실거래 권한·운영 승인 및 실환경 거래소 권한/주문 상태 검증 (수동 API 권한 설정·운영/법무 출시 게이트 필요, 실제 주문 테스트 금지)

## 상시 준수 항목

- [x] 개인정보처리방침 초안·필수/선택 동의·동의 이력·본인 열람/마케팅 철회/계정 삭제 API
- [x] 시큐어코딩·추천 투명성·접근성/UX·운영/사고대응·테스트 피드백 문서 및 AGENTS 규칙
- [x] frontend 가입 동의/개인정보 권리 화면과 모바일/키보드 E2E (토큰 메모리 보관·320px 메뉴·실제 가입/마케팅 철회 E2E)
- [ ] 공개·유료·타인 자동매매 출시 전 법무·개인정보·보안·운영 게이트 서면 승인

## 보류

- 출금 기능
- 레버리지, 선물/옵션, margin trading
- 유료 AI API 기반 추천

## 수동 처리 (Phase와 별도, 완료 시 일괄 안내)

다음은 아직 사용자 계정 권한 또는 전문가 검토가 필요한 항목이다.
Phase 13 완료 보고 전까지는 작업을 막는 경우가 아니면 별도 요청하지 않는다.

- [x] Docker Desktop 설치 및 실행: PostgreSQL/Redis가 healthy인 것을 확인
- [x] JDK 21 설치 확인: 실행 시 PowerShell 세션 `JAVA_HOME`을 JDK 21 경로로 설정
- [x] Node.js 24+ 설치 확인: frontend/E2E 명령 실행
- [x] 프로젝트 루트 `.env` 생성 및 PostgreSQL 비밀번호 설정
- [x] `AUTH_TOKEN_SECRET`, `CREDENTIAL_ENCRYPTION_KEY` 로컬 키 생성 및 `.env` 설정: Base64 32-byte, Git/Frontend에 저장 금지
- [ ] Upbit API key 발급: 자산 조회·주문 조회·주문만 필요한 최소 권한, 출금 권한 금지
- [ ] Bithumb API key 발급: 자산 조회·주문 조회·주문만 필요한 최소 권한, 출금 권한 금지
- [ ] 사용자별 API key 등록: Phase 12 인증 UI/API가 준비된 뒤에만 수행, `.env` 공용 key로 대체 금지
- [ ] LIVE 전환 승인: Phase 13의 별도 안전 점검·kill switch·한도 검증 후에만 검토
- [ ] 개인정보처리방침 게시 정보 확정: 사업자/개인정보 보호책임자/연락처/보유기간/위탁·국외이전 여부를 법률 검토로 확정
- [ ] 공개·유료·타인 자동매매 법률 검토: 인허가·신고·약관·광고·환불·소비자보호 적용성을 전문가에게 서면 확인

## 2026-09-24 거래 화면 및 종목 범위

- [x] 시장·모의거래·실거래 화면의 거래소별 전체 KRW 마켓 목록과 심볼/한글명/영문명 검색, 거래소 변경 시 미지원 종목을 첫 유효 종목으로 전환
- [x] 분/시간/일/주/월 캔들, 캔들/선/영역 차트, SMA 표시 선택, RSI(14), 포인터·키보드 OHLCV 조회
- [x] Upbit/Bithumb 공개 WebSocket 시세·체결·호가, 일반/누적호가와 모의 호가주문 연결
- [x] 모의·실거래 호가주문을 지정가 IOC로 연결하고 실거래 기본 잠금·RiskEngine 경계를 유지; 미체결 잔량은 즉시 취소

## 2026-09-24 종목별 토론방 및 거래 레이아웃

- [x] 차트를 호가창 위에 전체 너비로 배치하고 아래에 호가·주문 패널 나란히 표시
- [x] 종목별 최신 게시글 조회, 닉네임 기반 작성, 인증·길이 검증 적용
- [x] 계정 삭제 시 작성 게시글 정리 및 개인정보/API/DB 문서 반영
- [ ] 공개 서비스 전 신고·삭제 대응과 유해 게시물 운영 절차 확정 및 법률·개인정보 검토
## 2026-09-24 시가 대비 등락률

- [x] 현재가와 당일 시가 비교 등락률을 요약 정보와 각 호가 가격에 표시
- [x] 시가 미수신 시 계산 대기 상태 표시

## 2026-09-24 계정 보안

- [x] 가입 이메일 코드 인증과 만료·재시도·재전송 제한
- [x] 현재 비밀번호 확인을 거친 비밀번호 변경 및 신규 비밀번호 규칙 적용
- [x] 이메일 재인증과 가입/직전 변경 이후 90일 변경 제한
- [x] 로그인 비밀번호의 가입 형식검사 제거 및 전체 비밀번호 입력 표시 토글
- [ ] 운영 이메일 발송용 SMTP 공급자와 발신 도메인 설정


## 2026-09-24 토론방·계정 수정·호가 UI 개선

- [x] 종목 토론 목록을 글번호·제목·작성자·작성일·추천수로 제한하고 상세 보기, 서식/이미지, 거래화면·목록 이동 추가
- [x] 댓글, 사용자당 단일 추천/비추천 투표, 비추천 21개부터 본문·이미지 블라인드 및 직접 열람 추가
- [x] 마이 페이지 정보 요약과 별도 내 정보 수정 화면 연결, 공통 비밀번호 입력/보기 버튼 크기 보정
- [x] 고양이가 코인을 가지고 노는 프로젝트 로고/아이콘 SVG 적용
- [x] 호가 행 구분선 색상 보정, 유휴 시 숨기고 스크롤 중 얇게 보이는 스크롤바 적용

## 2026-09-24 세션 복원 및 토론방 운영 기능

- [x] F5 새로고침 뒤 같은 탭에서 로그인 상태 복원, 로그아웃 시 탭 토큰 제거
- [x] 댓글 페이지당 10개 조회와 작성자 본인 글/댓글 수정·삭제
- [x] 게시글 신고 중복 방지, ADMIN 신고 목록과 숨김·기각·복원 기능
- [ ] 운영자가 검토된 계정에 ADMIN 역할을 부여하고 공개 서비스 신고 대응 절차를 확정