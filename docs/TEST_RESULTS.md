# 테스트 결과 및 다음 작업 목록


## 2026-09-24 월간 PAPER 집계 내구성 보완

| 검증 | 실제 명령/환경 | 결과 | 범위 |
| --- | --- | --- | --- |
| Backend unit | `backend: .\gradlew.bat clean test --rerun-tasks --console=plain` (JDK 21) | PASS (60 tests) | 스케줄러·서비스 변경 포함 전체 단위 테스트 |
| PostgreSQL integration | `.env` DB 비밀번호를 프로세스 환경에만 설정 후 `backend: .\gradlew.bat integrationTest --rerun-tasks --console=plain` | PASS (11 tests) | 동월 중복 잠금 거부, open/close 잠금 독립, 트랜잭션 종료 시 잠금 해제, 기존 월별 랭킹·동의 통합 검사 |
| Frontend | `frontend: npm run lint`, `npm run typecheck`, `npm run test`, `npm run build` | PASS (3 tests) | 전체 정적 검사·단위 테스트·빌드 |
| Browser E2E | `e2e: npm run test:e2e` | PASS (3 tests) | 가입/로그인/닉네임 설정, 월간 공개 동의·다음 달 참가·동의 철회, PAPER 흐름, 320px 화면; LIVE 주문 미호출 |
| Runtime/API | Docker 상태, Backend bootRun, `/api/health`, `/api/paper-league`, Frontend `/` | PASS | PostgreSQL/Redis healthy, Flyway V12, PAPER/UP, 공개 랭킹 WAITING 200, Frontend 200 |
| PDF 안내서 | Playwright Chromium PDF 출력 및 4페이지 확인 | PASS (4 pages) | 경계 잠금·재시도와 장애 복구 한계를 반영; 전체 HTML 렌더 미리보기 검토 |

첫 Backend 테스트 컴파일에서 테스트 코드의 AssertJ boolean overload가 모호했으나 결과를 primitive 변수로 분리해 해결했고 전체 60개가 통과했다. 첫 E2E에서는 페이지에 서로 다른 두 상태 알림이 있어 단일 `role=status` locator가 모호했으며 대회 완료 문구 locator를 좁혀 전체 3개가 통과했다. Backend 기동은 Gradle 배포판 다운로드가 샌드박스 네트워크에서 거부되어 승인된 다운로드로 재시도 후 정상 기동했다.

월 경계 집계 시각 자체(매월 1일 00:00~00:04)는 현재 날짜 조건상 runtime에서 발생시키지 않았고, PostgreSQL 잠금 동작은 통합 테스트로 검증했다. 서비스가 해당 5분 전체 중단되거나 시세가 계속 실패할 때 지난 시점 평가액을 재구성하는 기능은 없다. 실제 LIVE 주문, 출금, 외부 거래소 private API는 호출하지 않았다 (NOT EXECUTED). 로컬 Backend/Frontend 및 PostgreSQL/Redis는 검증 뒤 계속 실행 중이다.
## 2026-09-24 월간 PAPER 대회 및 시스템 검증

| 검증 | 실제 명령/환경 | 결과 | 범위 |
| --- | --- | --- | --- |
| Backend unit | `backend: .\gradlew.bat clean test --rerun-tasks --console=plain` (JDK 21) | PASS (60 tests) | 기존 단위 테스트, 새 서비스·스케줄러 컴파일 |
| PostgreSQL integration | `.env`의 DB 비밀번호는 프로세스 환경에만 설정하고 `backend: .\gradlew.bat integrationTest --rerun-tasks --console=plain` | PASS (10 tests) | Flyway V12, PAPER 월간 손익 순위·4명 중 상위 3개 메달, 선택 공개 동의·철회 기록 삭제, 기존 격리 테스트 |
| Frontend | `frontend: npm run lint`, `npm run typecheck`, `npm run test`, `npm run build` | PASS (3 tests) | 월간 대회 패널 표시, 메뉴·계정 흐름, 프로덕션 빌드 |
| Browser E2E | `e2e: npm run test:e2e` | PASS (3 tests) | 320px 레이아웃, 대회 패널 및 개인정보 하단 링크, 가입/로그인. LIVE 주문 경로 미호출 |
| Runtime | `docker compose up -d`, JDK 21 `backend: .\gradlew.bat bootRun --console=plain`, HTTP 확인 | PASS | PostgreSQL/Redis healthy, Flyway V12 적용, `/api/health` PAPER/UP, `/api/paper-league` 대기 상태 200, 잘못된 월 형식 400 |
| Local user guide PDF | `python tmp/pdfs/build_guide.py`; `python tmp/pdfs/render.py` | PASS (4 pages) | 월간 PAPER 참가/동의 절차와 경계시각 한계를 반영, 페이지 렌더 확인 |

최초 PostgreSQL 통합 테스트 시 Docker 엔진이 내려가 있어 연결 단계에서 실패했다. Docker Desktop과 프로젝트 컨테이너를 시작한 후 10개 테스트를 다시 실행해 모두 통과했다. 최초 Browser E2E 시도는 대회 대기 상태 문구를 잘못 예상해 실패했으며, 실제 WAITING 상태 문구에 맞춘 assertion으로 고친 뒤 3개 모두 통과했다. 프런트 최초 새 화면 테스트에서도 테스트 대역 추천 응답이 잘못된 성공 형태였던 문제를 바로잡은 뒤 전체 3개 테스트가 통과했다.

실제 LIVE 주문, 출금 및 외부 거래소 개인 API는 호출하지 않았다 (NOT EXECUTED). 월 경계 집계는 로컬 단일 Backend의 한국 시간 첫 5분 실행에 의존하며 해당 시간대 서비스 중단·시세 장애를 운영 규모에서 복구하는 내구성은 검증하지 않았다.

## 2026-09-24 PAPER 실시간 거래소 시세와 로고/페이지 제목

| 구분 | 실제 실행 | 결과 | 범위 |
| --- | --- | --- | --- |
| Backend unit | backend/gradlew.bat clean test --rerun-tasks --console=plain (JDK 21) | PASS (60 tests) | PAPER, LIVE guard, 공개 시장 API 및 공통 service 단위 검사 |
| PostgreSQL integration | .env DB 비밀번호를 프로세스 환경에만 전달한 뒤 backend/gradlew.bat integrationTest --rerun-tasks --console=plain | PASS (8 tests) | Flyway V1~V10, 거래소별 PAPER 지갑, 실시간 시세 기반 주문 응답/체결 단가, 사용자 분리 |
| Frontend | npm run lint, npm run typecheck, npm run test, npm run build | PASS (3 tests) | 실시간 시세 컴포넌트, 경로별 document title, 로고 asset |
| Browser E2E | e2e: npm run test:e2e | PASS (3 tests) | 데스크톱/320px, 가입·로그인, Upbit/Bithumb WebSocket 시세, 거래소별 PAPER 매수·매도/잔고 |
| Runtime | Docker Compose 상태, Backend bootRun, GET /api/health | PASS | PostgreSQL/Redis healthy, API UP, 거래 모드 PAPER, V10 적용 |
| 로컬 기능 안내 PDF | tmp/pdfs/build_guide.py, tmp/pdfs/render.py | PASS (3 pages) | PAPER 사용 절차 반영, 전 페이지 시각 검토 |

초기 통합 테스트는 프로세스에 POSTGRES_PASSWORD가 전달되지 않아 DB 연결에서 실패했다. .env 값을 출력하거나 수정하지 않고 해당 프로세스에만 설정해 재실행했고 통과했다. 첫 Browser E2E는 PAPER 매수 수량의 소수 자릿수 18자리가 매도 입력 step 8자리와 맞지 않아 브라우저 입력 검증이 제출을 막았다. 입력 step을 저장 정밀도와 맞춘 뒤 전체 E2E가 통과했다. Browser E2E에서 두 거래소의 실시간 시세와 거래소 선택에 따른 PAPER 체결을 확인했다.

실제 거래소 주문, 출금, 비공개 API 및 LIVE 경로는 호출하지 않았다 (NOT EXECUTED). 공개 WebSocket 시세는 브라우저에서 직접 구독하며, 체결 시 서버가 선택 거래소의 공개 REST 현재가를 다시 조회한다. 외부 실시간 데이터 공급 및 시장 체결 빈도는 거래소 API 상태에 의존한다.

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

## 2026-09-23 UI·인증·프록시·로깅 재검증

| 구분 | 실제 실행 | 결과 | 범위 |
| --- | --- | --- | --- |
| Backend clean/unit | `backend\gradlew.bat clean test --rerun-tasks --console=plain` | PASS (59 tests) | 개인 잔고 문자열 숫자 파싱, 계정·거래·LIVE 컨트롤러 단위 검사 |
| Backend unit after auth fail-fast | `backend\gradlew.bat test --rerun-tasks --console=plain` | PASS (60 tests) | 빈 `AUTH_TOKEN_SECRET`의 생성자 초기화 실패 검사 포함 |
| PostgreSQL integration | `backend\gradlew.bat integrationTest --rerun-tasks --console=plain` | PASS (8 tests) | Flyway V1–V9, PAPER/LIVE repository, auth/privacy; 키가 필수인 context test에는 테스트 전용 키 설정 |
| Frontend static/test/build | `npm run lint`, `npm run typecheck`, `npm run test`, `npm run build` (Vitest 5.0.1) | PASS (3 tests) | 메뉴별 경로, footer 개인정보 링크, 가입/로그인 및 409 메시지, production build |
| Dependency audit | `npm audit` | PASS (0 vulnerabilities) | Vitest 3의 moderate advisory 2건 해결 위해 Vitest 5.0.1 적용 |
| Browser E2E | `e2e: npm run test:e2e` | PASS (3 tests) | 각 메뉴 route·정책 footer·320px, 실제 테스트 계정 가입/로그인, PAPER 매수/매도·이력; LIVE route 미호출 |
| Proxy/API runtime | Frontend `GET http://127.0.0.1:5173/api/health` | PASS (200) | Vite proxy를 거쳐 backend 8080 응답 `UP`, `PAPER` |
| Backend request logs | `bootRun` 중 E2E requests | PASS | `/api/auth/register`, `/api/auth/login` 200; PAPER 주문 201. 로그에는 method/path/status/duration만 있으며 Authorization/query/body 없음 |
| Docker/runtime | `docker compose ps`, Backend bootRun/Flyway | PASS | PostgreSQL·Redis healthy, Backend 8080 기동, Flyway V1–V9 확인 |
| User test guide PDF | ReportLab 생성 및 PyMuPDF 3페이지 렌더/이미지 검토 | PASS | 가입 409 원인/수정, 양 포트 연결, 메뉴/로그 절차 반영 |
| 실제 LIVE 주문 | 미실행 | NOT EXECUTED | PAPER 기본값과 global kill switch 유지; 실권한·운영·법무 승인은 미완료 |

### 조사 및 수정

- `POST /api/auth/register`는 `AuthController`의 `@RequestMapping("/api/auth")`와 `@PostMapping("/register")`로 등록돼 있고 `SecurityConfig`에서 비인증 공개 경로다. 가입 버튼은 해당 API를 호출하며 사용자는 저장·동의 기록 뒤 토큰을 받는다.
- 기존 로컬 `.env`에서 `AUTH_TOKEN_SECRET`이 비어 있었다. 이전 코드가 서버 기동을 허용한 뒤 토큰 서명에서 `IllegalStateException`을 냈고, `ApiExceptionHandler`가 모든 `IllegalStateException`을 409 `INVALID_STATE`로 변환했다. 가입은 트랜잭션 rollback되어 계정이 저장되지 않았다. 로컬 `.env`에 랜덤 32-byte Base64 키를 설정했고, `AppTokenService`가 앞으로는 빈/잘못된 키를 서버 시작 시 거부하도록 변경했다. 키 값은 출력하지 않았다.
- 프론트 409 안내를 서버 설정/상태 오류로 분리했다. 400은 입력, 개인정보 필수 동의, 기존 가입 여부 확인 안내를 표시한다.
- 초기 통합 재검증에서는 fail-fast 때문에 인증 키를 지정하지 않은 두 context test가 실패했다. 두 테스트에만 고정 테스트 키를 지정한 후 통합 8건 PASS를 확인했다.
- E2E에서 고유 테스트 계정과 PAPER 주문 데이터가 로컬 DB에 생성되었다. 테스트가 만든 레코드는 정리하지 않았다. 사용자가 진행하는 계정·키·법무 설정에 영향을 주지 않도록 유지한다.

## 2026-09-24 프론트엔드 도구 호환성 재검증

| 구분 | 실제 실행 | 결과 | 범위 |
| --- | --- | --- | --- |
| Frontend lint/typecheck/test/build | `npm run lint`, `npm run typecheck`, `npm run test`, `npm run build` | PASS (Vitest 5.0.1, 3 tests) | Vite 6.4.3 / Node 24 환경 |
| Dependency audit | `npm audit` | PASS (0 vulnerabilities) | Vite 최소 범위 `^6.4.0`, Vitest `^5.0.1` |
| Browser E2E | `e2e: npm run test:e2e` | PASS (3 tests) | 최신 설치본으로 메뉴/계정/PAPER 흐름 재실행 |
| API proxy runtime | `GET http://127.0.0.1:5173/api/health` | PASS (200) | `/api` Vite proxy → Backend 8080, `UP`/`PAPER` |

Vitest 5의 공식 요구사항(Vite 6.4 이상, Node 22.12 이상)에 맞춰 Vite 의존성 범위를 고정했다.

## 2026-09-24 Backend clean 회귀 확인

| 구분 | 실제 실행 | 결과 | 범위 |
| --- | --- | --- | --- |
| Backend clean/unit | `backend\gradlew.bat clean test --rerun-tasks --console=plain` (JDK 21) | PASS (60 tests) | 최신 auth-key fail-fast, credential parsing, PAPER/LIVE 단위 경로 |
| Backend runtime after clean | `GET http://127.0.0.1:8080/api/health` | PASS (200) | 실행 중 서비스 health 응답 지속 확인 |

## 2026-09-24 필수 닉네임·PAPER 화면 회귀 검증

| 구분 | 실제 실행 | 결과 | 범위 |
| --- | --- | --- | --- |
| Backend clean/unit | `backend: .\gradlew.bat clean test --rerun-tasks --console=plain` (JDK 21) | PASS (60 tests) | 닉네임 API/계정 접근 필터 및 PAPER/LIVE 단위 회귀 |
| PostgreSQL integration | `backend: .\gradlew.bat integrationTest --rerun-tasks --console=plain` (JDK 21, DB 암호만 프로세스 환경 변수로 전달) | PASS (8 tests) | V11 migration, 미설정 계정 403, 형식 검증, 대소문자 무시 중복, 충돌 409 |
| Frontend lint/typecheck/test/build | `frontend: npm run lint; npm run typecheck; npm run test; npm run build` | PASS (3 frontend tests) | 닉네임 필수 설정 흐름, 메뉴와 production build |
| Browser E2E | `e2e: npm run test:e2e` | PASS (3 tests) | 가입 직후 설정 차단, 중복확인 후 저장, 계정 닉네임 변경 시 재확인, PAPER 거래 및 실거래 메뉴의 PAPER 차단; LIVE 주문 API 미호출 |
| Runtime / API proxy | Node `fetch` to `http://127.0.0.1:8080/api/health` and `http://127.0.0.1:5173/api/health` | PASS (200 / 200) | Backend and Vite proxy both report `UP`, `PAPER` |
| Docker services | `docker compose ps` | PASS | PostgreSQL 16 and Redis 7 healthy |
| User guide PDF | `python tmp/pdfs/build_guide.py`; `python tmp/pdfs/render.py output/pdf/crypto-invest-local-testing-guide.pdf` | PASS | v1.2; 3 rendered pages visually inspected; nickname setup/edit and menu paths included |
| Diff whitespace | `git diff --check` | PASS | final review |

E2E가 테스트용 계정과 PAPER 거래 데이터를 로컬 DB에 만들었다. 실제 LIVE 주문은 실행하지 않았다. PDF 재생성은 로컬 pip 접근 제한으로 첫 시도가 실패해, 승인된 네트워크 권한으로 `tmp/pdfs/lib`에 필요한 PDF 도구만 설치한 뒤 완료했다.
## 2026-09-24 전체 종목·차트·IOC 호가 주문 검증

| 구분 | 실제 실행 | 결과 | 범위 |
| --- | --- | --- | --- |
| Backend 단위 테스트 | `backend: .\gradlew.bat clean test --rerun-tasks --console=plain` (JDK 21) | PASS (62 tests) | 모의·실거래 주문 경계, Upbit/Bithumb IOC 페이로드, timeout 복구 |
| PostgreSQL 통합 테스트 | `.env`의 `POSTGRES_PASSWORD`를 프로세스 환경에 전달 후 `backend: .\gradlew.bat integrationTest --rerun-tasks --console=plain` | PASS (12 tests) | Flyway V14, LIVE 지정가 이력, 모의 지정가 가격, 기존 계정/대회 격리 |
| Frontend 정적·단위·빌드 | `frontend: npm run lint; npm run typecheck; npm run test; npm run build` | PASS (3 tests) | 거래 화면과 시장 메뉴, 전체 KRW 마켓 선택, 메뉴·계정 UI |
| Browser E2E | `e2e: npm run test:e2e` | PASS (3 tests) | 시장 메뉴 및 거래 화면 전체 종목, 320px, Upbit/Bithumb 시세·호가, 모의 주문·이력; LIVE 잠금 중 주문 미호출 |
| Runtime/API | JDK 21 `backend: .\gradlew.bat bootRun --console=plain`, HTTP API 조회 | PASS | PostgreSQL/Redis healthy, Flyway V14, health UP, LIVE false, Upbit KRW 289종목·Bithumb KRW 480종목, 각 거래소 10분 캔들 5건 |
| 실제 LIVE 주문 | 실행하지 않음 | NOT EXECUTED | 기본 LIVE 잠금 유지; adapter 검증은 loopback HTTP mock만 사용 |
| PDF 기능 가이드 | `node output/pdf/build_guide.mjs`; `python tmp/pdfs/render.py` | PASS (4 pages) | v1.7; PDF 4페이지 렌더링·육안 검사, 실행 절차·전체 KRW 종목·차트·IOC 호가주문 반영 |

시장 목록/10분 캔들 런타임 검증은 실제 공개 API를 조회했다. E2E는 로컬 DB에 고유 테스트 계정과 모의 체결 데이터를 추가했다. 실거래 API/주문은 호출하지 않았다.

## 2026-09-24 Vite 프론트엔드 모듈 변환 오류 수정

| 검증 | 실제 실행 | 결과 | 범위 |
| --- | --- | --- | --- |
| Frontend 정적·단위·빌드 | `npm run lint`, `npm run typecheck`, `npm run test`, `npm run build` | PASS (3 tests) | TSX 문법 복구 및 프로덕션 번들 |
| Browser E2E | `e2e: npm run test:e2e` | PASS (3 tests) | 메뉴 렌더링, 시장 종목 선택, 가입/닉네임/모의거래 흐름 |
| Runtime/browser | Playwright Chromium, `/market`, Vite 5173 및 API proxy | PASS | 문서 파싱 500 복구, 시장 메뉴 렌더링, 업비트 289 종목 선택 확인 |
| 외부 WebSocket | Chromium에서 Upbit/Bithumb 공개 WebSocket 접속 | NOT EXECUTED | 이 실행 환경의 네트워크 정책이 외부 연결을 차단해 `ERR_NETWORK_ACCESS_DENIED`; 브라우저 JS 예외는 없음 |

원인: TSX 파일 끝에 실제 줄바꿈 대신 문자 `\n`이 남아 Vite React Babel 파서가 `App.tsx`와 `MarketTicker.tsx` 요청에 500을 반환했다. 해당 문자를 제거하고 파일 끝 줄바꿈을 정리했다. 외부 WebSocket 실시간 연결은 이 환경에서 확인하지 못했다.
## 2026-09-24 거래 화면 정보 배치 및 선택 상태 개선

| 검증 | 실제 실행 | 결과 | 범위 |
| --- | --- | --- | --- |
| Frontend lint/typecheck/unit/build | `npm run lint`, `npm run typecheck`, `npm run test`, `npm run build` | PASS (3 tests) | 세 열 거래 화면, 메뉴 선택 스타일, frontend 번들 |
| Browser E2E | `e2e: npm run test:e2e` | PASS (3 tests) | 데스크톱 차트·호가·주문 열 순서, 일반/누적 선택 강조, 모의 주문 흐름, 320px 메뉴 |
| Diff check | `git diff --check` | PASS | whitespace 검사 |
| PDF 가이드 | `node output/pdf/build_guide.mjs`; `python tmp/pdfs/render.py` | PASS (4 pages) | v1.8 거래 화면 배치와 메뉴 선택 표시 반영 |

E2E에서는 외부 WebSocket 대신 Playwright가 공개 시세/체결/호가 메시지를 모의 주입하고, 가격은 로컬 Backend의 공개 ticker API에서 가져와 모의 IOC 주문 조건과 일치시켰다. 실제 거래소 주문 경로는 호출하지 않았다.
## 2026-09-24 거래 화면 차트 폭 및 동시 조회 레이아웃

| 검증 | 실행 | 결과 | 범위 |
| --- | --- | --- | --- |
| Frontend lint/typecheck/unit/build | `npm run lint`, `npm run typecheck`, `npm run test`, `npm run build` | PASS (3 tests) | 차트/호가/주문 레이아웃과 타입 검사 |
| Browser E2E | `e2e: npm run test:e2e` | PASS (3 tests, 최종 실행) | 데스크톱에서 차트가 오른쪽 호가·주문 영역보다 넓은지, 모바일 320px, 회원가입/모의주문 흐름 |
| PDF 가이드 | `node output/pdf/build_guide.mjs`; `python tmp/pdfs/render.py output/pdf/crypto-invest-local-testing-guide.pdf` | PASS (4 pages) | v1.9, 거래 화면 배치 설명 갱신 및 렌더링 확인 |
| LIVE 주문 | 실행하지 않음 | NOT EXECUTED | 화면 레이아웃 변경이며 실제 주문 테스트 없음 |

초기 E2E 실행에서는 이전 3열 배치를 확인하던 검증 조건이 남아 실패했고, 이를 새 2열 구조에 맞춰 갱신했습니다. 이후 별도 실행에서 기존 E2E 테스트 계정의 주문이 HTTP 409를 반환했으나, 최종 재실행에서는 3개 테스트 모두 통과했습니다.
로컬 Runtime 확인: http://127.0.0.1:5173/market, Backend /api/health, Vite proxy /api/health가 모두 HTTP 200이었고 LIVE 제출은 false였다. docker compose ps는 Docker named pipe 권한 거부로 NOT EXECUTED; E2E 브라우저 흐름은 통과했다.

## 2026-09-24 차트 상단 배치·시가 대비 등락·종목 토론방

| 검증 | 실제 명령/환경 | 결과 | 범위 |
| --- | --- | --- | --- |
| Backend unit | `backend: .\gradlew.bat clean test --rerun-tasks --console=plain` (JDK 21) | PASS (62 tests) | 애플리케이션 전체 컴파일 및 단위 테스트 |
| PostgreSQL integration | `.env`의 DB 비밀번호는 프로세스 환경에만 설정 후 `backend: .\gradlew.bat integrationTest --rerun-tasks --console=plain` | PASS (13 tests) | Flyway V16, 공개 목록·인증 작성·닉네임 게이트·종목별 분리·본문 경계·계정 삭제 정리 |
| Frontend lint/typecheck/test/build | `frontend: npm run lint`, `npm run typecheck`, `npm run test`, `npm run build` | PASS (3 tests) | 거래 레이아웃, 시가 대비 표시, 토론방 입력/표시 UI 포함 |
| Browser E2E | `e2e: npm run test:e2e` | PASS (3 tests) | 차트가 호가 위 전체 너비인지, 각 호가의 시가 대비 `+1.01%`, 토론 게시/비인증 공개 조회, 모바일 320px; LIVE 주문 미호출 |
| Runtime/API | Backend/Vite HTTP 및 `GET /api/discussions/BTC`; `docker compose ps` | PASS | health와 Vite proxy 200/LIVE false, 공개 토론 목록 200, PostgreSQL·Redis healthy, V16 기동 적용 |
| 사용자 가이드 PDF | `node output/pdf/build_guide.mjs`; `python tmp/pdfs/render.py output/pdf/crypto-invest-local-testing-guide.pdf` | PASS (4 pages) | v2.1, 상단 차트·호가 시가 등락·종목토론방 안내 렌더 확인 |
| 실제 거래소 주문 | 실행하지 않음 | NOT EXECUTED | 테스트와 브라우저 확인은 모의 흐름만 사용 |

첫 PostgreSQL 통합 실행에서 스키마 COMMENT 점검이 새 토론 테이블의 미주석 열을 발견했다. V16에 한글 테이블·열 COMMENT를 추가한 뒤 13개 통합 테스트가 통과했다. E2E 초기 실행에서는 토론 글 입력 폼이 거래 주문 폼의 넓은 locator와 충돌했고, 거래 폼을 tabpanel로 한정한 뒤 최종 3개가 통과했다. Backend는 `.env` 값을 출력하지 않고 프로세스에 로드해 기동했다.

토론방은 최신 50개 조회와 게시글 작성만 제공한다. 신고, 개별 편집/삭제와 운영자 moderation 도구는 구현하지 않았으며 공개 서비스 출시 전 운영 규칙과 처리 절차를 확정해야 한다.
E2E는 종료 시 생성한 테스트 계정을 개인정보 삭제 API로 정리한다. 디버깅 과정에서 남은 이전 테스트 게시글 3개도 ID를 확인한 뒤 삭제했고, 최종 공개 BTC 목록은 빈 상태를 반환한다.

## 2026-09-24 일반·누적 호가 화면 보정

| 검증 | 실제 명령/환경 | 결과 | 범위 |
| --- | --- | --- | --- |
| Frontend lint/typecheck/unit/build | `frontend: npm run lint`, `npm run typecheck`, `npm run test`, `npm run build` | PASS (3 unit tests) | 독립 매도·매수 호가 렌더링, 누적 수량 계산 |
| Browser E2E | `e2e: npm run test:e2e` | PASS (3 tests) | 양방향 각 15단, 누적 수량 합산, 현재가 분리, 320px 및 회원/모의주문 흐름 |
| Runtime/API | `Invoke-WebRequest -UseBasicParsing` to Vite `/` and Backend `/api/health` | PASS (HTTP 200/200) | 기존 로컬 서버 응답 확인 |
| 사용자 가이드 PDF | `node output/pdf/build_guide.mjs`; `python tmp/pdfs/render.py output/pdf/crypto-invest-local-testing-guide.pdf` | PASS (4 pages) | v2.2 호가 표시 설명 갱신 및 전체 페이지 렌더 확인 |
| Backend unit/integration | 실행하지 않음 | NOT EXECUTED | Backend 변경 없음 |
| 실제 거래소 주문 | 실행하지 않음 | NOT EXECUTED | 공개 시세 WebSocket mock만 사용 |

첫 E2E 실행에서 호가 선택 버튼을 찾지 못했다. 수정 입력 과정의 한국어 접근성 이름이 손상된 것이 원인이었으며 문자열을 복구한 뒤 프론트엔드 전체 검증과 E2E 3개가 통과했다. 이전 UI는 거래소 응답의 같은 배열 인덱스에 든 매도·매수 호가를 한 줄로 짝지어 서로 다른 가격 사다리를 혼합했고, 누적 수량을 개별 잔량 최대치와 비교해 막대가 포화될 수 있었다. 가격 방향으로 각 호가를 따로 정렬하고, 매도·현재가·매수를 위·가운데·아래로 분리했으며 양쪽 15단을 표시한다. 누적 수량은 각 방향 최우선 호가부터 합산하고 그 누적 최대치로 막대를 정규화한다.

Browser runtime은 기존 로컬 Vite/Backend 환경에서 통과했다. E2E에서 실제 거래소 주문 API는 호출하지 않았다.
## 2026-09-24 BTC 호가창 스크롤 안정성

| 검증 | 명령/환경 | 결과 | 범위 |
| --- | --- | --- | --- |
| Frontend lint/typecheck/unit/build | `frontend: npm run lint`, `npm run typecheck`, `npm run test`, `npm run build` | PASS (5 unit tests) | 구형 배열 및 신규 페이지형 토론방 응답 회귀 검증, 호가 스크롤 보정 |
| Browser E2E | `e2e: npm run test:e2e` | PASS (3 tests) | mocked BTC WebSocket 메시지, 초기 ±8단, 스크롤 후 행 추가/제거, 메뉴·회원/토론방 흐름 |
| Browser runtime | Playwright Chromium, 로컬 Vite/Backend, mocked Upbit/Bithumb WebSocket | PASS | 연속 ticker·체결·호가 메시지 중 화면 유지, 콘솔 오류 0건, 수동 scrollTop 440 유지 |
| Runtime/API | Node fetch, `http://127.0.0.1:5173/`, `http://127.0.0.1:8080/api/health` | PASS (HTTP 200/200) | Frontend 제공 및 Backend UP, LIVE 제출 false |
| 사용자 가이드 PDF | `node output/pdf/build_guide.mjs`; `python tmp/pdfs/render.py output/pdf/crypto-invest-local-testing-guide.pdf` | PASS (4 pages) | v2.3, 호가 스크롤 동작 설명 렌더 확인 |
| Public exchange BTC stream | 거래소 외부 WebSocket | NOT EXECUTED | 실제 외부 BTC 스트림 대신 결정적인 모의 메시지로 변동 동작을 재현 |

호가 목록 상단에서 행이 삽입/제거될 때 브라우저의 native scroll anchoring이 `scrollTop`을 자동 보정할 수 있어, 호가 뷰포트에 `overflow-anchor: none`을 적용했다. 사용자가 수동 스크롤을 움직이지 않는 한 ticker 업데이트마다 중심 위치를 재설정하지 않으며, 처음 데이터가 들어온 시점에 거래소/종목별로 한 번만 현재가 기준 ±8행을 맞춘다. Playwright가 만든 거래소별 모의 스트림에서 연속 시세·호가 갱신과 스크롤 유지가 확인됐다. 외부 거래소 실시간 BTC 스트림 확인은 별도 미실행이다.


## 2026-09-24 실시간 체결 정렬·차트 입력·계정 메뉴 개선

| 검증 | 실행 | 결과 | 범위 |
| --- | --- | --- | --- |
| Frontend lint/typecheck/unit/build | frontend: npm run lint, npm run typecheck, npm run test, npm run build | PASS (5 tests) | 고정 폭 체결 열, 차트 안내 닫기, 로그인 후 이동·닉네임 비활성화 |
| Browser E2E | e2e: npm run test:e2e | PASS (3 tests) | 차트 휠 확대 시 페이지 스크롤 유지, 안내 닫기, 매수·매도 체결색/고정열, 계정 흐름 |
| Backend unit/integration | 미실행 | NOT EXECUTED | Backend 동작 코드 변경 없음 |
| Runtime/API | 로컬 Vite / 및 Backend /api/health HTTP 확인 | PASS (200/200) | Backend UP, LIVE 제출 false |
| 비밀번호 변경·이메일 인증 | 이전 결과(대체됨) | SUPERSEDED | 2026-09-24 계정 보안 검증 결과는 아래 최신 섹션 참조 |

체결 행은 시각·가격·수량에 고정 폭 열과 tabular numerals를 사용한다. 차트 휠은 passive 해제한 native listener에서 preventDefault하여 페이지 스크롤을 막는다. 차트 안내는 브라우저 localStorage로 닫힘을 기억한다. 닉네임이 있는 사용자의 로그인은 대시보드로 이동하고, 같은 닉네임의 중복확인/저장을 비활성화한다. 상단 메뉴명은 마이 페이지이며 로그아웃을 상단에 배치했다.


## 2026-09-24 계정 보안 기능 검증

| 검증 | 실제 명령/환경 | 결과 | 범위 |
| --- | --- | --- | --- |
| Backend 단위 테스트 | backend: .\\gradlew.bat clean test --rerun-tasks --console=plain, Java 21 | PASS | 회원가입 검증, 로그인 기존 비밀번호 허용, 비밀번호 변경 |
| Backend PostgreSQL 통합 테스트 | backend: .\\gradlew.bat integrationTest --rerun-tasks --console=plain, .env DB 암호 환경 주입 | PASS (14 tests) | 이메일 인증 코드·해시 저장·시도 제한·1회 토큰, Flyway 및 기존 API |
| Frontend lint/typecheck/unit/build | frontend: npm run lint; npm run typecheck; npm run test -- --reporter=dot; npm run build | PASS (5 tests) | 가입 이메일 인증, 로그인 비밀번호 형식 미검사, 공통 비밀번호 표시, 계정 보안 화면 |
| Browser E2E | e2e: npm run test:e2e, 로컬 Vite/Backend + Mailpit | PASS (3 tests) | 이메일 수신·인증·회원가입·로그인·닉네임 보호 흐름, 실제 주문 API 미호출 |
| Runtime/API | GET /api/health, POST /api/auth/email-verification | PASS (200/202) | PostgreSQL 16 연결, Flyway V21, Mailpit 전달 확인 |
| 실제 거래 주문 | 실행 안 함 | NOT EXECUTED | 요청 범위 밖이며 테스트에서 주문을 실행하지 않음 |

최초 E2E 실행에서 공통 비밀번호 토글로 인한 모호한 라벨 선택과 테스트가 가입 비밀번호와 다른 문자열로 로그인하던 결함을 발견해 테스트 선택자와 입력값을 수정했다. Mailpit 메일은 비동기 전달될 수 있어 수신 목록을 최대 10초 폴링한다. 최종 E2E 3건이 통과했고 LIVE 주문 경로 호출은 없었다. 백엔드는 최신 코드로 재기동했으며 PostgreSQL 연결과 Flyway V21 상태를 확인했다.


## 2026-09-24 토론방·계정 수정·호가 UI 개선

| 검증 | 실제 명령/환경 | 결과 | 범위 |
| --- | --- | --- | --- |
| Backend 단위 | backend: .\\gradlew.bat clean test --rerun-tasks --console=plain, Java 21 | PASS | 토론방 및 기존 단위 테스트 |
| Backend PostgreSQL 통합 | backend: .\\gradlew.bat integrationTest --rerun-tasks --console=plain, PostgreSQL 16 | PASS | 댓글/투표/블라인드, 이미지 URL 블라인드, migration |
| Frontend lint/typecheck/unit/build | frontend: npm run lint; npm run typecheck; npm run test -- --reporter=dot; npm run build | PASS (5 unit tests) | 토론방, 내 정보 수정 화면, 공통 비밀번호 입력, 호가 UI |
| Browser E2E | e2e: npm run test:e2e | PASS (3 tests) | 회원 흐름·내 정보 수정, 토론 글꼴/크기/정렬·이미지 표시·댓글·추천, 320px 모바일, 실거래 호출 없음 |
| Runtime/API | 로컬 Vite 및 최신 Backend, GET /api/health | PASS (HTTP 200) | PostgreSQL 16 연결, Flyway V23 적용 확인 |
| 로컬 기능 테스트 가이드 PDF | node output/pdf/build_guide.mjs; python tmp/pdfs/render.py output/pdf/crypto-invest-local-testing-guide.pdf | PASS (5 pages) | 버전 2.5, V23, 종목 토론방/내 정보 수정 안내, 렌더링 전 페이지 육안 확인 |
| 실제 거래소 LIVE 주문 | 실행 안 함 | NOT EXECUTED | 자동 검증에서 실주문 금지 |

초기 브라우저 검증은 별도 계정 수정 경로 도입 후 기존 계정 화면의 닉네임 위치를 가정해 실패했다. 테스트를 새 경로에 맞춰 수정했다. E2E는 글꼴·크기·정렬 저장 및 PNG 첨부 후 브라우저 표시까지 확인한다. 지정가 E2E는 BTC 시세보다 높은 모의 가격을 사용해 외부 공개 시세 변동에도 즉시 체결 조건을 결정적으로 확인한다. 최종 전체 E2E 3건은 통과했다.

## 2026-09-24 새로고침 로그인 복원 및 토론방 운영 기능

| 검증 | 실제 명령/환경 | 결과 | 범위 |
| --- | --- | --- | --- |
| Backend 단위 | `backend: .\gradlew.bat clean test --rerun-tasks --console=plain`, Java 21 | PASS | 전체 backend unit tests |
| Backend PostgreSQL 통합 | `backend: .\gradlew.bat clean integrationTest --rerun-tasks --console=plain`, `.env`의 POSTGRES_PASSWORD를 프로세스 변수로만 전달, PostgreSQL 16 | PASS (17 tests) | Flyway V24, 댓글 10개 페이징, 작성자 소유권 수정/삭제, 신고/관리자 숨김·복원, 기존 전체 통합 테스트 |
| Frontend 정적 검사·단위·빌드 | `frontend: npm run lint; npm run typecheck; npm run test -- --reporter=dot; npm run build` | PASS (6 unit tests) | 탭 세션 저장/로그아웃 제거, 토론 글 목록·댓글 페이지·댓글 수정, 전체 TypeScript build |
| Browser E2E | `e2e: npm run test:e2e`, Chromium, 로컬 최신 backend | PASS (3 tests) | 가입·로그인 후 F5 대시보드 복귀, 토론 글/댓글/추천, 모바일 320px, LIVE 주문 경로 미호출 |
| Runtime/API | Docker PostgreSQL/Redis/Mailpit healthy, Backend 8080, Frontend 5173, `GET /api/health` | PASS (HTTP 200) | PostgreSQL 연결, Flyway schema 24, LIVE 제출 false |
| 로컬 기능 테스트 가이드 | `node output/pdf/build_guide.mjs`; Poppler 렌더와 전체 5페이지 육안 확인 | PASS (5 pages, v2.6) | 세션 복원, 토론 댓글 페이징·수정·신고 관리 안내 추가 |
| 실제 거래소 LIVE 주문 | 실행 안 함 | NOT EXECUTED | 자동/수동 검증에서 실주문 금지 |

E2E 첫 실행은 8080에서 실행 중이던 이전 Backend가 댓글 페이지 API를 제공하지 않아 실패했다. 기존 개발 서버를 종료하고 `.env`를 프로세스에만 전달해 최신 Backend를 재기동한 후 3건 모두 통과했다. 토큰은 탭의 `sessionStorage`에 보관하며 서버 토큰 만료는 8시간이다. ADMIN 계정 부여는 운영자가 권한 검토 후 수동 수행해야 한다.