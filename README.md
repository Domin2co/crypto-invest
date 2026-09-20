# Crypto Invest

업비트와 빗썸을 연동해 자산을 조회하고, 시장 데이터와 기술지표를 바탕으로 투자 추천·포트폴리오 분석·Paper Trading·자동투자를 제공하는 개인 프로젝트입니다.

> 기본 원칙: 실제 거래보다 **분석 가능성, 재현 가능성, 안전한 테스트**를 우선합니다.

---

## 주요 기능

- 업비트 / 빗썸 계정 연동
- 보유 KRW 및 가상자산 조회
- 현재가 / 평가금액 / 수익률 조회
- 미보유 종목 시장 데이터 조회
- 기술지표 계산
- 규칙 기반 투자 추천
- 포트폴리오 분석
- Paper Trading
- 자동투자
- 리밸런싱
- 투자 / 주문 / 추천 Audit Log

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
- WebClient

### Frontend

- React
- TypeScript
- Vite
- Tailwind CSS
- TanStack Query
- Lightweight Charts 또는 ECharts

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
- Testcontainers
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
├─ docs/
│  ├─ REQUIREMENTS.md
│  ├─ ARCHITECTURE.md
│  ├─ SECURITY.md
│  ├─ TESTING.md
│  ├─ TRADING_RULES.md
│  ├─ API_RULES.md
│  ├─ DATABASE.md
│  ├─ DECISIONS.md
│  └─ TODO.md
├─ backend/
├─ frontend/
├─ e2e/
├─ infra/
└─ scripts/
```

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

실제 프로젝트 구성에 따라 경로 및 Script는 변경될 수 있다.

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

---

## 주의

본 프로젝트의 추천 결과는 규칙 기반 분석 결과이며 수익을 보장하지 않는다.

실거래 기능은 개발 초기에는 비활성화한다.
