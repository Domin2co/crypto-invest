# 테스트 결과 및 다음 작업 피드백

## 2026-09-23 Phase 13 미검증 경로 추가 점검

| 구분 | 실제 실행 | 결과 | 범위 |
| --- | --- | --- | --- |
| Backend unit | `backend\\gradlew.bat clean test --rerun-tasks --console=plain` (JDK 21) | FAIL (54 tests, 1 failure) | 추가한 Upbit/Bithumb private account adapter 검사에서 `balance + locked` 기대값 1.5 대신 0 재현; 나머지 53개 통과 |
| Exchange order chance parser unit | 위 Backend unit 명령에 포함 | PASS (2 tests) | 정상 한도·잔고 파싱, 누락/음수 한도 거부 |
| PostgreSQL integration | `backend\\gradlew.bat integrationTest --rerun-tasks --console=plain` (JDK 21, `.env` DB 비밀번호는 프로세스 환경에만 전달) | PASS (8 tests) | Flyway V1~V9, 사용자 격리, PAPER 롤백, LIVE 제출 의도와 부분 체결 terminal 저장 |
| Frontend | `npm run lint`, `npm run typecheck`, `npm run test`, `npm run build` | PASS (2 tests) | lint·타입·Vitest·production build |
| Browser E2E | `e2e: npm run test:e2e` | PASS (3 tests) | 공개 시세/추천, 320px UI, 가입·암호화 저장·PAPER 매수/매도; 테스트용 임시 key는 프로세스 환경에서만 사용 |
| Backend runtime / DB | `backend\\gradlew.bat bootRun`, Playwright Browser E2E, `docker compose ps` | PASS | E2E에서 PAPER 화면/API 동작, Flyway V1~V9 up-to-date, PostgreSQL/Redis healthy; Backend는 검증 후 종료 |
| Dependency audit | `frontend: npm audit`; `e2e: npm audit` | FAIL / PASS | Frontend Vitest dependency chain에 moderate 취약점 2건, 수정안은 Vitest major 업그레이드; E2E 0건 |
| 실제 Private/LIVE 거래소 API 및 주문 | 미실행 | NOT EXECUTED | 테스트용 credential만 사용; 실제 exchange private endpoint·주문은 호출하지 않음 |

첫 E2E 시도는 `.env`에 `AUTH_TOKEN_SECRET`과 `CREDENTIAL_ENCRYPTION_KEY`가 없어 인증/암호화 동작이 실패했다. `.env`는 수정하지 않고 E2E 전용 임시 키를 프로세스에만 주입해 재실행했으며, 최종 3개 Browser 테스트가 통과했다. 검증 중 추가한 adapter 단위 테스트는 실제 거래소 응답의 숫자 문자열 처리 결함을 찾아냈다. 구현 수정은 승인 전 보류한다.

E2E가 생성한 사용자 및 가짜 자격증명의 암호문은 로컬 PostgreSQL에 남아 있다. 자격증명은 테스트용 문자열이며 암호화 임시 키는 프로세스 종료와 함께 폐기되어 해당 테스트 암호문은 다시 복호화할 수 없다. 기존 사용자 데이터 삭제는 하지 않았다.

## 2026-09-23 Phase 13 LIVE/PAPER 회귀 검증 이어서 수행

| 구분 | 실제 실행 | 결과 | 범위 |
| --- | --- | --- | --- |
| Backend unit | `backend\\gradlew.bat clean test --rerun-tasks --console=plain` (JDK 21) | PASS (50 tests) | LIVE 복구·부분 체결·사용자 재확인, PAPER 멱등성/롤백, 거래소 인증 adapter |
| PostgreSQL integration | `backend\\gradlew.bat integrationTest --rerun-tasks --console=plain` (JDK 21, `.env`의 비밀번호를 프로세스 환경으로 전달) | PASS (8 tests) | Flyway, 사용자별 PAPER 경계, LIVE 제출 의도 커밋 및 terminal 부분 체결 저장 |
| Docker runtime | `docker compose ps` | PASS | PostgreSQL/Redis healthy; 기존 서비스는 계속 실행 중 |
| Backend runtime / API | `backend\\gradlew.bat bootRun --console=plain`, `GET /api/health` | PASS | Flyway V1~V9 검증, `{"status":"UP","tradingMode":"PAPER"}`; 확인 후 Backend 종료 |
| Browser / 실제 LIVE 주문 | 미실행 | NOT EXECUTED | Browser 검증은 별도 수행하지 않음; 실제 거래소 주문은 호출하지 않음 |

첫 통합 테스트 시도는 프로세스에 `POSTGRES_PASSWORD`가 전달되지 않아 DB 인증에서 8개가 실패했다. 비밀값을 출력하지 않고 `.env`에서 해당 프로세스 환경변수로만 전달해 통합 테스트를 재실행했고 모두 통과했다.

## 2026-09-23 Phase 13 멱등성 사용자 격리 및 PAPER 롤백

| 구분 | 실제 실행 | 결과 | 범위 |
| --- | --- | --- | --- |
| Backend unit | `backend\\gradlew.bat test --rerun-tasks --console=plain` (JDK 21) | PASS (50 tests) | PAPER 멱등 키 충돌 시 트랜잭션 실패 처리 및 기존 거래 회귀 |
| PostgreSQL integration | `backend\\gradlew.bat integrationTest --rerun-tasks --console=plain` (PostgreSQL 16.15) | PASS (8 tests) | 사용자별 계획/체결 조회 격리, 타 사용자 키 충돌 차단, PAPER 지갑 롤백, Flyway comment |
| Docker runtime | `docker compose ps` | PASS | PostgreSQL/Redis healthy |
| Backend HTTP runtime / 실제 LIVE 거래소 주문 | 미실행 | NOT EXECUTED | 앱을 별도 기동하지 않음; 실제 주문 금지 원칙 준수 |

초기 두 차례 통합 실행은 저장소 계층에서 충돌 예외가 Spring `@Repository` 예외 변환에 걸려 기대한 상태 충돌이 아닌 데이터 접근 예외로 노출되는 결함으로 FAIL했다. 계획 조회가 충돌을 `Optional.empty()`로 반환하고 API 계층에서 거절하도록 수정했으며, 단위·통합 명령을 재실행해 모두 PASS를 확인했다. 별도 통합 시나리오로 PAPER 지갑이 멱등성 충돌 뒤 남지 않는 것도 확인했다.

## 2026-09-23 Phase 13 DB 한글 comment 회귀 검증 강화

| 구분 | 실제 실행 | 결과 | 범위 |
| --- | --- | --- | --- |
| PostgreSQL integration | `backend\\gradlew.bat integrationTest --rerun-tasks --console=plain` | PASS (6 tests) | 애플리케이션 테이블·컬럼 comment 존재 및 한글 포함 검증 |
| 실제 거래소 주문 | 미실행 | NOT EXECUTED | 스키마 검증만 수행, 실제 주문 미호출 |

기존 통합 테스트가 comment의 null 여부만 확인하던 것을 한글 포함 여부도 확인하도록 강화했다. 스키마/migration 변경은 없다.

## 2026-09-23 Phase 13 LIVE 주문 HTTP 결과 분류

| 구분 | 실제 실행 | 결과 | 범위 |
| --- | --- | --- | --- |
| Backend unit | `backend\\gradlew.bat test --rerun-tasks --console=plain` | PASS (49 tests) | HTTP 500/408/POST 409는 UNKNOWN 복구 경로, 429/일반 4xx는 명시적 거절, POST 자동 재전송 없음 |
| PostgreSQL integration | `backend\\gradlew.bat integrationTest --rerun-tasks --console=plain` | PASS (6 tests) | PostgreSQL/Flyway 및 LIVE 주문 저장소 제출·복구 경계 회귀 |
| 실제 거래소 주문 | 미실행 | NOT EXECUTED | 로컬 mock HTTP만 사용 |

HTTP 5xx/408을 `FAILED`로 저장하면 거래소가 주문을 접수한 경우 실제 체결을 누락할 수 있었다. 이제 UNKNOWN으로 분류해 기존 client ID 조회로 회복하며, 429는 명시적 거절로 유지한다.

## 2026-09-23 Phase 13 거래소 부분 체결 terminal 상태

| 구분 | 실제 실행 | 결과 | 범위 |
| --- | --- | --- | --- |
| Backend unit | `backend\\gradlew.bat test --rerun-tasks --console=plain` | PASS (45 tests) | Bithumb `done`+부분 체결 수량을 `PARTIALLY_FILLED`/terminal로 매핑, 재조회·재주문 방지 |
| PostgreSQL integration | `backend\\gradlew.bat integrationTest --rerun-tasks --console=plain` | PASS (6 tests) | 기존 `completed_at`으로 terminal 여부 저장·복원, 전체 schema 한글 comment 검증, 새 DB column/migration 없음 |
| 실제 거래소 주문 | 미실행 | NOT EXECUTED | 부분 체결 응답은 로컬 모의 HTTP만 사용 |

Bithumb 공식 규격상 `done`은 IOC/FOK의 잔량 취소로 발생할 수 있다. 전체 체결과 구분하기 위해 기존 `completed_at`을 terminal source로 사용하며, `PARTIALLY_FILLED` 상태/실행 수량은 그대로 보존한다.

## 2026-09-23 Phase 13 거래소 LIVE 인증 규격 대조

| 구분 | 실제 실행 | 결과 | 범위 |
| --- | --- | --- | --- |
| Backend unit | `backend\\gradlew.bat test --rerun-tasks --console=plain` | PASS (43 tests) | Upbit HS512/no timestamp, Bithumb HS256/timestamp, 실제 HMAC·SHA-512 query hash, Bithumb 36자 제한, 모의 주문/조회 HTTP |
| PostgreSQL integration | 미실행 | NOT EXECUTED | 이번 변경은 거래소 인증/HTTP adapter 전용이며 DB·migration 변경 없음 |
| 실제 거래소 주문 | 미실행 | NOT EXECUTED | 실제 주문 없이 로컬 mock HTTP 서버만 사용 |

공식 문서 대조 결과 Bithumb은 Upbit과 JWT 규칙이 달랐고 기존 구현은 주문 요청을 인증 실패시킬 수 있었다. 서명 알고리즘·timestamp와 Bithumb client ID 길이를 수정하고, 실제 HMAC 바이트를 테스트에서 검증했다. 참고 규격은 `docs/API_RULES.md`에 기록했다.

## 2026-09-23 Phase 13 제출 의도 커밋 및 재기동 복구

| 구분 | 실제 실행 | 결과 | 범위 |
| --- | --- | --- | --- |
| Backend unit | `backend\\gradlew.bat test --rerun-tasks --console=plain` | PASS (41 tests) | 저장된 SUBMITTED 재호출은 주문 재전송 없이 거래소 상태를 조회 |
| PostgreSQL integration | `backend\\gradlew.bat integrationTest --rerun-tasks --console=plain` | PASS (6 tests) | LIVE 제출 의도와 시작 감사 이력이 API 반환 전에 커밋됨; 전체 테이블/컬럼 한글 comment 검증 포함 |
| Runtime/API/Browser | 미실행 | NOT EXECUTED | 통합 테스트에서 실제 Spring/PostgreSQL context 기동 확인; HTTP/Browser 경로 변경 없음 |
| 실제 LIVE 주문 | 미실행 | NOT EXECUTED | 실제 거래소 주문은 금지하고 모의/저장소 검증만 수행 |

LIVE 서비스의 외부 HTTP 호출을 서비스 전체 DB 트랜잭션 밖으로 이동했다. 제출 의도 저장과 감사 이력은 별도 원자적 트랜잭션으로 커밋하고, 결과 갱신도 별도 원자적 트랜잭션으로 기록한다. 응답 전 프로세스가 종료돼도 SUBMITTED 상태가 남아 재호출은 기존 client order ID로 조회하며 새 주문을 보내지 않는다.

## 2026-09-23 Phase 13 전역 LIVE kill switch

| 구분 | 실제 실행 | 결과 | 범위 |
| --- | --- | --- | --- |
| Backend unit | `backend\\gradlew.bat test --rerun-tasks --console=plain` | PASS (40 tests) | 전역 kill switch 우선 차단 및 기존 LIVE Guard 회귀 |
| Docker 상태 | `docker compose ps` (권한 승인 후 재실행) | PASS | PostgreSQL/Redis healthy |
| Runtime | `backend\\gradlew.bat bootRun --console=plain`, `GET /api/health` | PASS | Flyway V1~V9 확인, `{"status":"UP","tradingMode":"PAPER"}`; 검증 후 Backend 종료 |
| Frontend / E2E | 미실행 | NOT EXECUTED | Backend LIVE guard 설정 변경만 수행 |
| 실제 LIVE 주문 | 미실행 | NOT EXECUTED | 명시적 승인·운영/법무 출시 게이트·실환경 검증 전 금지 |

최초 Runtime은 프로세스 환경에 PostgreSQL 비밀번호가 없어 인증에 실패했다. `.env` 값을 출력하지 않고 Backend 프로세스에 전달해 재실행 후 정상 기동했다. 전역 kill switch는 기본 활성화 상태이며 주문을 실행하지 않았다.

## 2026-09-23 전체 회귀 검증

| 구분 | 실제 실행 | 결과 | 범위 |
| --- | --- | --- | --- |
| Backend unit | `backend\\gradlew.bat test --rerun-tasks --console=plain` | PASS (39 tests) | 시장·추천·포트폴리오·PAPER·LIVE 안전 경계 회귀 |
| Backend integration | `backend\\gradlew.bat integrationTest --rerun-tasks --console=plain` | PASS (5 tests) | PostgreSQL/Flyway V1~V9, 사용자 격리·암호화·개인정보 삭제·PAPER 거래 |
| Frontend lint/typecheck/test/build | `npm run lint; npm run typecheck; npm run test; npm run build` | PASS (2 tests) | Dashboard·추천·포트폴리오·자격증명 입력 UI |
| Browser E2E | `e2e: npm run test:e2e` | PASS (3 tests) | 공개 추천 출처, 320px 메뉴, 가입·암호화 저장·PAPER 매수/매도 |
| Runtime | `docker compose ps`, `backend\\gradlew.bat bootRun`, `GET /api/health` | PASS | PostgreSQL/Redis healthy, Backend `PAPER`/`UP`; 검증 후 Backend 종료 |
| 실제 Private/LIVE 거래소 주문 | 미실행 | NOT EXECUTED | 유효한 사용자 API key, 명시적 LIVE 승인 및 출시 게이트 전까지 금지 |

첫 통합 테스트는 PostgreSQL 컨테이너가 재기동 직후 `health: starting` 상태여서 연결 거부로 FAIL했다. 컨테이너가 `healthy`가 된 뒤 동일 통합 테스트를 재실행하여 PASS를 확인했다.

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

## 2026-09-23 재개 작업 및 사용자 테스트 PDF

| 구분 | 실제 실행 | 결과 | 범위 |
| --- | --- | --- | --- |
| Backend unit | `backend\\gradlew.bat clean test --rerun-tasks --console=plain` (JDK 21) | FAIL (54 tests, 1 failure) | `PrivateAccountClientTest.balancesIncludeFundsLockedByOpenOrders`: 거래소 숫자 문자열 응답을 수량으로 변환하지 못함; `balance + locked` 1.5 기대, 0 반환 |
| PostgreSQL integration | `backend\\gradlew.bat integrationTest --rerun-tasks --console=plain` (JDK 21, `.env`의 DB 비밀번호를 프로세스에만 전달) | PASS (8 tests) | 마이그레이션과 저장소 통합 경로 |
| Frontend static/test/build | `npm run lint`, `npm run typecheck`, `npm run test`, `npm run build` | PASS (2 tests) | lint, TypeScript, Vitest, production build |
| Local services | `docker compose ps` | PASS | PostgreSQL과 Redis 모두 healthy |
| PDF guide | ReportLab 생성, PyMuPDF 4페이지 렌더 및 이미지 검토, pypdf 텍스트 검사 | PASS | 한글, 로그인 API 절차, 실행 명령, 페이지 번호 확인 |
| API/Browser runtime | 이번 실행에서 미기동/미실행 | NOT EXECUTED | Browser E2E는 앞선 2026-09-23 기록의 3 tests PASS 참고 |
| Diff validation | `git diff --check` | PASS | 공백 오류 없음; Git의 LF/CRLF 변환 경고만 출력 |

실패한 테스트의 fixture는 JSON 문자열 필드(`balance`, `locked`, `avg_buy_price`)를 사용하며 Upbit/Bithumb adapter가 모두 같은 공통 잔고 경로를 통과한다. 실패 원인 수정과 관련 회귀 테스트 보완은 승인 전 보류한다. LIVE 주문 실행 경로는 아직 controller에 연결되지 않았으며, 실제 거래소 private API와 실거래 주문은 호출하지 않았다.
