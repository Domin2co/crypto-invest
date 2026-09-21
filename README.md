# Crypto Invest

업비트와 빗썸의 시장·계정 데이터를 바탕으로 투자 추천, 포트폴리오 분석, PAPER 거래와
자동투자 계획을 제공하는 Modular Monolith 프로젝트입니다.

> 기본 원칙: 실제 거래보다 **분석 가능성, 재현 가능성, 안전한 테스트**를 우선합니다.

---

## 현재 구현 범위

- Upbit/Bithumb 공개 시세·일봉 candle 및 읽기 전용 잔고 조회
- 시장 데이터 정규화·기술지표·규칙 기반 추천·포트폴리오·RiskEngine
- 사용자별 암호화 거래소 API key, BCrypt 로그인, PAPER wallet/주문 감사 이력
- 개인정보 필수/선택 동의, 본인 정보 열람, 마케팅 철회, 계정 익명화
- Live 주문 안전 경계: 기본 차단, 이중 스위치, 일일 한도, 멱등 client order ID,
  timeout 뒤 상태조회. 실제 LIVE 주문 endpoint는 아직 노출하지 않는다.
- 반응형 React dashboard, 320px navigation, 가입/동의·개인정보 권리 UI

---

## 기술 스택

### Backend

- Java 21
- Spring Boot 3.x
- Spring Security
- Spring Data JPA
- Gradle
- PostgreSQL
- Flyway
- JDK `HttpClient`

### Frontend

- React
- TypeScript
- Vite
- Tailwind CSS
- TanStack Query

### Infrastructure

- Windows 10 / 11
- Docker Desktop
- Docker Compose
- PostgreSQL Container
- Redis Container — 필요 시

### Test

- JUnit 5
- Mockito
- AssertJ
- Spring Boot Test
- Playwright

### AI Development

- Orca
- Codex CLI
- `AGENTS.md`
- Playwright MCP — 선택

---

## 프로젝트 구조

```text
crypto-invest/
├─ AGENTS.md
├─ README.md
├─ .env.example
├─ .gitignore
├─ .codex/
│  └─ config.toml
├─ docker-compose.yml                 # PostgreSQL 16, Redis 7
├─ docs/
│  ├─ REQUIREMENTS.md / ARCHITECTURE.md / DECISIONS.md
│  ├─ DATABASE.md / TABLERULE.md
│  ├─ SECURITY.md / SECURE_CODING.md / COMPLIANCE.md
│  ├─ PRIVACY_POLICY.md / INVESTMENT_TRANSPARENCY.md
│  ├─ ACCESSIBILITY_UX.md / OPERATIONS.md
│  ├─ TESTING.md / TEST_RESULTS.md
│  └─ TODO.md / TRADING_RULES.md / API_RULES.md
├─ backend/
│  ├─ src/main/java/com/cryptoinvest/
│  │  ├─ common/          # health, 공통 오류 응답
│  │  ├─ exchange/        # public/private API, credential 암호화
│  │  ├─ market/ indicator/ recommendation/ portfolio/ risk/
│  │  ├─ security/        # 인증, 사용자 동의·개인정보 권리
│  │  └─ trading/         # PAPER 및 기본 차단된 LIVE 안전 경계
│  ├─ src/main/resources/db/migration/  # V1–V7 Flyway + 한글 COMMENT
│  └─ src/test/java/                   # 단위·PostgreSQL 통합 테스트
├─ frontend/
│  ├─ public/privacy-policy.html
│  └─ src/                # App, AccountAccess, MarketTicker, styles, tests
└─ e2e/
   ├─ playwright.config.ts
   └─ tests/dashboard.spec.ts
```

구조를 새로 만들거나 이동·삭제할 때는 같은 변경에서 이 구조도 즉시 갱신한다. 상세 의존
방향은 [아키텍처 문서](docs/ARCHITECTURE.md)를 따른다.

---

## 기본 환경변수

`.env.example`을 복사해 `.env`를 만든다.

```powershell
Copy-Item .env.example .env
```

실제 API Key, DB Password, JWT Secret 등은 `.env`에 입력한다.

실제 `.env`는 Git에 Commit하지 않는다.

기본 거래 모드:

```env
TRADING_MODE=PAPER
LIVE_TRADING_ENABLED=false
```

`CREDENTIAL_ENCRYPTION_KEY`와 `AUTH_TOKEN_SECRET`은 base64 32-byte 값으로 별도 설정한다.
실제 LIVE 전환과 공개·유료·타인 자동매매는 [준법 게이트](docs/COMPLIANCE.md)의 서면 승인
전까지 금지한다.

---

## 실행

Infrastructure:

```powershell
docker compose up -d
docker compose ps
```

Backend:

```powershell
cd backend
.\gradlew.bat bootRun
```

Frontend:

```powershell
cd frontend
npm install
npm run dev
```

---

## 테스트

Backend:

```powershell
cd backend
.\gradlew.bat clean test
```

Frontend:

```powershell
cd frontend
npm run lint
npm run typecheck
npm run test
npm run build
```

E2E:

```powershell
cd e2e
npm run test:e2e
```

최근 실제 검증 결과와 미해결 위험은 [TEST_RESULTS.md](docs/TEST_RESULTS.md)를 확인한다.

---

## 문서

- [요구사항](docs/REQUIREMENTS.md)
- [아키텍처](docs/ARCHITECTURE.md)
- [보안](docs/SECURITY.md)
- [테스트](docs/TESTING.md)
- [거래 규칙](docs/TRADING_RULES.md)
- [거래소 API 규칙](docs/API_RULES.md)
- [Database](docs/DATABASE.md)
- [설계 결정](docs/DECISIONS.md)
- [현재 작업](docs/TODO.md)
- [개인정보처리방침 초안](docs/PRIVACY_POLICY.md)
- [준법 출시 게이트](docs/COMPLIANCE.md)
- [시큐어 코딩 기준](docs/SECURE_CODING.md)
- [접근성·UX 기준](docs/ACCESSIBILITY_UX.md)
- [운영·사고 대응](docs/OPERATIONS.md)
- [테스트 결과](docs/TEST_RESULTS.md)

---

## 주의

본 프로젝트의 추천 결과는 규칙 기반 분석 결과이며 수익을 보장하지 않는다.

실거래 기능은 기본 비활성화이며, 자동테스트·E2E·Browser Automation은 실제 주문을 실행하지 않는다.
