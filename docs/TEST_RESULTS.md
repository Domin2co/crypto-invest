# 테스트 결과 및 다음 작업 피드백

## 2026-09-22 Phase 11 목표 비중·rebalancing gap

| 구분 | 실제 실행 | 결과 | 범위 |
| --- | --- | --- | --- |
| Backend unit | `backend\\gradlew.bat test integrationTest --rerun-tasks --console=plain` | PASS (39 unit / 5 integration) | 목표 비중·현재 비중 차이 계산, Flyway V1~V9, 목표 비중 저장·schema 한글 comment |
| Frontend lint/typecheck/test/build | `npm run lint; npm run typecheck; npm run test; npm run build` | PASS (2 tests) | 목표 비중 저장 요청·저장 후 포트폴리오 갱신·모바일 UI |
| Browser E2E | `e2e: npm run test:e2e` | PASS (3 tests) | 목표 비중 UI 포함, 가입·암호화 키 저장·PAPER 매수/매도·320px 회귀 |
| 실제 Private 잔고·목표 비중 Browser E2E | 미실행 | NOT EXECUTED | 유효한 사용자별 최소권한 거래소 API key가 아직 없음 |

통합 테스트는 PostgreSQL `NUMERIC(9,8)`의 scale(`0.35000000`)과 테스트 기대값(`0.35`)의 `BigDecimal.equals` 차이로 최초 FAIL했다. 금융값 비교를 scale을 무시하는 비교로 교정한 뒤 동일 전체 명령을 재실행해 PASS를 확인했다.

추가로 `backend\\gradlew.bat integrationTest --tests com.cryptoinvest.security.PrivacyControllerIT --rerun-tasks --console=plain`를 실행해 계정 삭제 시 사용자 목표 비중 정책도 함께 삭제되는 것을 PASS로 확인했다.

## 2026-09-22 Phase 11 사용자 연동 포트폴리오

| 구분 | 실제 실행 | 결과 | 범위 |
| --- | --- | --- | --- |
| Backend unit | `backend\\gradlew.bat test integrationTest --rerun-tasks --console=plain` | PASS (39 unit / 5 integration) | 읽기 전용 총 평가액·현금/종목 비중, 사용자 자격증명·PAPER/LIVE 회귀 |
| Frontend lint/typecheck/test/build | `npm run lint; npm run typecheck; npm run test; npm run build` | PASS (2 tests) | 키의 메모리 전송·입력 초기화, 명시적 포트폴리오 새로고침 UI |
| Browser E2E | `e2e: npm run test:e2e` | PASS (3 tests) | 가입→암호화 키 저장 HTTP 204→PAPER 매수/매도, 실제 거래소 주문 미호출 |
| 실제 Private 잔고 조회 | 미실행 | NOT EXECUTED | 유효한 사용자별 최소권한 거래소 API key가 아직 없음 |

첫 E2E는 로컬 `.env`에 `CREDENTIAL_ENCRYPTION_KEY`가 없어 자격증명 저장이 HTTP 409로 FAIL했다. `.env`를 수정하거나 키를 출력하지 않고, 테스트 Backend 프로세스에만 임시 256-bit 키를 주입해 동일 E2E를 재실행하여 PASS를 확인했다.
목표 비중 정책은 아직 사용자별로 저장되지 않으므로 rebalancing gap을 임의 계산하지 않았으며 TODO에 후속 항목으로 남긴다.

## 2026-09-22 Phase 11 공개 추천 화면 연동

| 구분 | 실제 실행 | 결과 | 범위 |
| --- | --- | --- | --- |
| Backend unit | `backend\\gradlew.bat test integrationTest --rerun-tasks --console=plain` | PASS (38 unit / 5 integration) | 시간순 일봉 15개, RSI·Momentum·가격 대비 변동성, 추천 메타데이터·기존 보안 회귀 |
| Frontend lint/typecheck/test/build | `npm run lint; npm run typecheck; npm run test; npm run build` | PASS (2 tests) | 공개 추천 UI·투명성 메타데이터·프로덕션 번들 |
| Runtime | `backend\\gradlew.bat bootRun`, `GET /api/health`, `GET /api/recommendations/UPBIT?market=KRW-BTC` | PASS | `PAPER`/`UP`, Upbit 공개 일봉 15개와 출처 반환 |
| Browser E2E | `e2e: npm run test:e2e` | PASS (3 tests) | 공개 추천 출처, 320px 메뉴, PAPER 매수·매도 및 주문 내역 |

첫 Frontend 실행에서 테스트 mock의 선택 인자 타입 때문에 `typecheck`와 `build`가 FAIL했다. mock 타입을 실제 `fetch` 호출 시그니처로 교정한 뒤 동일 전체 명령을 재실행해 PASS를 확인했다.
추천은 공개 시장 데이터만 사용하며, 주문 API·사용자 자격증명·LIVE 주문을 호출하지 않는다. 사용자 연동 계정의 읽기 전용 포트폴리오 표시는 TODO의 Phase 11 보완 항목으로 남긴다.

## 2026-09-22 거래소 자격증명 입력 경계 보완

| 구분 | 실제 실행 | 결과 | 범위 |
| --- | --- | --- | --- |
| Backend unit | `backend\\gradlew.bat test --rerun-tasks --console=plain` | PASS (36 tests) | 기존 보안·거래·LIVE 차단 회귀 |
| Backend integration | `backend\\gradlew.bat integrationTest --rerun-tasks --console=plain` | PASS (5 tests) | 인증된 자격증명 암호화 저장, 누락 거래소·512자 초과 키 거절, 저장 없음 |

`POST /api/exchange-accounts`는 거래소와 접근/비밀 키의 필수·길이를 검증하고, 검증 오류도 세부값 없이 `INVALID_REQUEST`만 반환한다.

## 2026-09-22 Phase 13 실거래 재확인

| 구분 | 실제 실행 | 결과 | 범위 |
| --- | --- | --- | --- |
| Backend unit | `backend\\gradlew.bat test --rerun-tasks --console=plain` | PASS (36 tests) | 재확인 기록·만료 거절, 재확인 누락 시 RiskEngine/복호화/거래소 호출 차단 |
| Backend integration | `backend\\gradlew.bat integrationTest --rerun-tasks --console=plain` | PASS | PostgreSQL/Flyway V1~V8, `LIVE_TRADING` 동의·확인/주문 상태 감사 이벤트·schema comment |
| Runtime | `docker compose up -d`, `docker compose ps` | PASS | PostgreSQL 및 Redis healthy |
| 실제 LIVE 주문 | 미실행 | NOT EXECUTED | 명시적 승인과 법무·운영 출시 게이트 전까지 금지 |

첫 통합 테스트는 PostgreSQL 컨테이너가 내려가 있어 연결 실패했으며, 컨테이너를 기동한 뒤 동일 명령을 재실행해 PASS를 확인했다.
감사 로그 통합 테스트 중 `client_order_id` 36자 제한을 초과한 테스트 데이터가 발견되어 UUID 길이 값으로 교정했고, 재실행 결과 PASS를 확인했다.

## 2026-09-22 Phase 9 PAPER 거래 E2E

| 구분 | 실제 실행 | 결과 | 범위 |
| --- | --- | --- | --- |
| Backend unit | `backend\\gradlew.bat test --rerun-tasks --console=plain` | PASS (36 tests) | PAPER 주문 API 컴파일 및 기존 거래·RiskEngine 회귀 |
| Backend integration | `backend\\gradlew.bat integrationTest --rerun-tasks --console=plain` | PASS (4 tests) | 가입한 사용자 PAPER 매수, 주문 계획·지갑·감사 이력 |
| Frontend | `npm run lint; npm run typecheck; npm run test; npm run build` | PASS (2 tests) | 메모리 토큰 전달, PAPER 주문 UI, production build |
| Browser E2E | `e2e: npm run test:e2e` | PASS (3 tests) | 가입→PAPER 매수·매도 HTTP 201, 체결 후 지갑·`FILLED` 주문 내역 표시, 실제 거래소 주문 미호출, 320px 메뉴 |

E2E 최초 401은 이전 Backend 프로세스가 8080 포트를 점유한 환경 문제였고, 해당 프로세스를 종료한 뒤 최신 PAPER Backend로 재기동해 PASS를 확인했다.
PAPER 잔고 부족은 HTTP 409과 `INVALID_STATE`로 응답하며, 주문 계획·체결 이력이 함께 롤백되는 것을 PostgreSQL 통합 테스트로 확인했다.
인증된 `GET /api/paper/orders/summary`는 사용자별 PAPER 지갑과 최근 체결만 반환하며, PostgreSQL 통합 테스트에서 다른 사용자 데이터 없이 KRW/BTC 지갑과 주문 이력을 확인했다.

## 기록 규칙

모든 작업은 실행한 명령, 환경, 결과, 결함, 미실행 항목과 다음 작업의 보완점을 이 문서에
추가한다. 이전 기록의 미해결 위험은 다음 개발·테스트 전에 확인한다.

## 2026-09-21 — Phase 12/13·개인정보 권리 경계

| 구분 | 실제 실행 | 결과 | 범위 |
| --- | --- | --- | --- |
| Backend unit | `backend\\gradlew.bat test --rerun-tasks --console=plain` | PASS (33 tests) | 공개 API 오류, 암호화, 인증, PAPER, Upbit/Bithumb Live guard·timeout recovery·거래소 서명 |
| Backend integration | `backend\\gradlew.bat integrationTest --rerun-tasks --console=plain` | PASS (4 tests) | PostgreSQL/Flyway V1–V7, schema 한글 comment, credential 격리, CORS, 개인정보 동의·열람·철회·익명화 |
| Runtime | `docker compose ps`, `backend\\gradlew.bat bootRun` | PASS | PostgreSQL/Redis healthy, Flyway V1–V7 validated, `/api/health` is PAPER, unauthenticated privacy API is 401 |
| Frontend lint/typecheck/test/build | `frontend: npm run lint; npm run typecheck; npm run test; npm run build` | PASS (2 tests) | 가입 필수/선택 동의·메모리 토큰·PAPER dashboard |
| Desktop/mobile E2E | `e2e: npm run test:e2e` | PASS (3 tests) | PAPER UI, 개인정보처리방침 링크, 320px 메뉴, 실제 가입·마케팅 철회 |
| 실제 LIVE 거래소 주문 | 미실행 | NOT EXECUTED | 명시적 승인·법무/운영 게이트 전 금지 |

### 발견·수정

- Live order client가 테스트 보조 생성자와 운영 생성자를 함께 가져 Spring이 생성자를 선택하지
  못했다. `@Autowired`로 운영 생성자를 지정하고 통합 테스트로 재확인했다.
- 개인정보 동의와 권리 API의 모든 DB 변경은 V7 Flyway migration 및 한글 comment로 적용했다.
- E2E의 `127.0.0.1:5173` 개발 origin이 CORS allow-list에 없었다. loopback 두 origin과
  개인정보 API가 사용하는 PATCH/DELETE만 명시 허용하고, 미등록 origin 차단 테스트를 유지했다.

### 다음 작업 피드백

- LIVE 승인 전 Upbit/Bithumb sandbox 또는 읽기 전용 환경에서 주문 조회 응답 매핑, rate limit,
  time-zone 일일 한도, 장애 복구 훈련을 재검증한다.
- 공개·유료·타인 자동매매 전 `COMPLIANCE.md`의 법률/사업/보안 게이트는 사람의 서면 승인으로만
  완료 처리한다.
