# 테스트 결과 및 다음 작업 피드백

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
