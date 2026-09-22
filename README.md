# Crypto Invest

업비트·빗썸 시장/계정 데이터를 바탕으로 투자 추천, 포트폴리오 분석, PAPER 거래와 자동투자 계획을 제공하는 Java·React 기반 Modular Monolith입니다.

기본 거래 모드는 `PAPER`입니다. 실제 주문은 기본 차단되며, 테스트·E2E·Browser Automation은 실제 주문 또는 출금을 실행하지 않습니다.

## 현재 구현 범위

- Upbit/Bithumb 공개 시세·캔들, 인증된 읽기 전용 잔고 및 공개 현재가 기반 포트폴리오 평가
- 시장 데이터 정규화, 이동평균·RSI·모멘텀·변동성 지표, 공개 추천 조회·포트폴리오·RiskEngine
- 사용자별 AES-256-GCM 암호화 거래소 API 키와 BCrypt 비밀번호
- PAPER 지갑·주문·감사 이력, 인증된 사용자 모의 매수·매도 UI, 멱등성 및 자동투자 모드
- 개인정보 필수/선택 동의, 마케팅 철회, 본인 정보 열람 및 계정 익명화
- LIVE 안전 경계: PAPER 기본값, LIVE 이중 스위치, 일일 한도, RiskEngine, client order ID, timeout 후 상태 조회, 최근 사용자 재확인
- 반응형 React Dashboard와 320px 모바일 내비게이션, Playwright E2E

LIVE 주문 controller는 외부에 노출하지 않습니다. 재확인 API는 주문을 생성하지 않고 `user_consent` 이력 및 `audit_log`만 기록합니다. 공개·유료·타인 자동매매는 [준법 출시 게이트](docs/COMPLIANCE.md)의 서면 승인이 있기 전에는 활성화할 수 없습니다.

## 기술 스택

- Backend: Java 21, Spring Boot, Spring Security, Spring Data JPA, Gradle, PostgreSQL, Flyway, JDK `HttpClient`
- Frontend: React, TypeScript, Vite, Tailwind CSS, TanStack Query
- Infrastructure: Docker Compose, PostgreSQL 16, Redis 7
- Test: JUnit 5, Mockito, AssertJ, Spring Boot Test, Playwright

## 프로젝트 구조

```text
crypto-invest/
├── AGENTS.md                         # 작업·보안·검증 규칙
├── README.md
├── .env.example
├── .codex/config.toml                # 프로젝트 전용 도구 설정
├── docker-compose.yml
├── docs/                             # 요구사항, 아키텍처, DB, 보안, 준법, 운영, 테스트 결과, TODO
├── backend/
│   └── src/
│       ├── main/java/com/cryptoinvest/
│       │   ├── common/               # Health 및 공통 웹 오류
│       │   ├── exchange/             # 거래소 public/private API, 암호화 credential
│       │   ├── market/ indicator/ recommendation/ portfolio/ risk/
│       │   ├── security/             # 인증, 동의, 개인정보 권리
│       │   └── trading/              # PAPER 및 LIVE 안전 경계
│       ├── main/resources/db/migration/ # V1~V9 Flyway, 한글 COMMENT
│       └── test/java/                # 단위 및 PostgreSQL 통합 테스트
├── frontend/
│   ├── public/privacy-policy.html
│   └── src/                          # Dashboard, Account/Exchange/Portfolio/Market/Recommendation/PAPER UI, 테스트
└── e2e/
    └── tests/dashboard.spec.ts
```

최상위 구조, `backend`/`frontend`/`e2e`, 모듈, Flyway migration, 운영 문서를 추가·이동·삭제하면 같은 변경에서 이 구조와 관련 설명을 즉시 갱신합니다. 변경 후 실제 파일 목록과 README를 대조하고 `git diff`로 검토합니다.

## 환경 설정

```powershell
Copy-Item .env.example .env
```

`.env`에는 실제 DB 비밀번호, `CREDENTIAL_ENCRYPTION_KEY`, `AUTH_TOKEN_SECRET`을 입력합니다. 두 키는 base64 인코딩된 32-byte 값이어야 하며 Git·로그·Frontend에 노출하면 안 됩니다.

```env
TRADING_MODE=PAPER
LIVE_TRADING_ENABLED=false
LIVE_TRADING_CONFIRMATION_MINUTES=15
```

## 실행

```powershell
docker compose up -d
docker compose ps

cd backend
.\gradlew.bat bootRun
```

```powershell
cd frontend
npm install
npm run dev
```

## 검증

```powershell
cd backend
.\gradlew.bat clean test

cd frontend
npm run lint
npm run typecheck
npm run test
npm run build

cd ..\e2e
npm run test:e2e
```

최근 실행 결과와 미해결 위험은 [테스트 결과](docs/TEST_RESULTS.md)를 확인합니다.

## 문서

- [요구사항](docs/REQUIREMENTS.md), [아키텍처](docs/ARCHITECTURE.md), [설계 결정](docs/DECISIONS.md)
- [DB](docs/DATABASE.md), [테이블 규칙](docs/TABLERULE.md), [거래 규칙](docs/TRADING_RULES.md), [거래소 API 규칙](docs/API_RULES.md)
- [보안](docs/SECURITY.md), [보안 코딩](docs/SECURE_CODING.md), [개인정보처리방침 초안](docs/PRIVACY_POLICY.md), [준법 출시 게이트](docs/COMPLIANCE.md)
- [투자추천 투명성](docs/INVESTMENT_TRANSPARENCY.md), [접근성·UX](docs/ACCESSIBILITY_UX.md), [운영·사고 대응](docs/OPERATIONS.md)
- [TODO](docs/TODO.md), [테스트 결과](docs/TEST_RESULTS.md)
