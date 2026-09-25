# Crypto Invest

업비트·빗썸 시장/계정 데이터를 바탕으로 투자 추천, 포트폴리오 분석, 모의거래와 자동투자 계획을 제공하는 Java·React 기반 Modular Monolith입니다.

모의거래는 별도 API와 가상 지갑으로 제공합니다. 전역 거래 모드 설정은 없습니다. 실주문은 운영 스위치, kill switch, 일일 한도와 RiskEngine 검증으로 기본 차단됩니다.

## 현재 구현 범위

- Upbit/Bithumb 전체 KRW 종목 목록, 공개 시세·체결·호가와 분/시간/일/주/월 캔들, 인증된 읽기 전용 잔고 및 공개 현재가 기반 포트폴리오 평가
- 시장 데이터 정규화, 이동평균·RSI·모멘텀·변동성 지표, 공개 추천 조회·포트폴리오·RiskEngine
- 사용자별 AES-256-GCM 암호화 거래소 API 키와 BCrypt 비밀번호
- 모의거래: 거래소별 전체 KRW 종목, 실시간 공개 WebSocket 시세·체결·호가, 매수·매도·간편·호가 주문, 가상 지갑 및 체결 이력
- 개인정보 필수/선택 동의, 마케팅 철회, 본인 정보 열람 및 계정 익명화
- 암호화된 TOTP 다중 인증, 일회용 복구 코드, 로그인·인증 설정의 DB 기반 이메일/IP 요청 제한
- LIVE 안전 경계: 기본 잠금, 이중 스위치, 일일 한도, RiskEngine, 사용자 재확인, idempotent 상태 복구, 호가 IOC 지정가 지원
- 회원가입 후 필수 고유 닉네임 설정·중복확인과 계정에서 닉네임 변경, 미설정 사용자 API 차단
- 메뉴별 독립 경로, 모든 화면의 개인정보 footer, 320px 모바일 내비게이션과 Playwright E2E

인증된 `POST /api/live-trading/orders` controller는 구현되어 있지만 사용자 재확인, LIVE enable 및 기본 활성 kill switch를 모두 통과해야 실행됩니다. 기본 설정에서는 주문이 차단되며 자동 테스트는 LIVE 주문을 호출하지 않습니다. 공개·유료·타인 자동매매는 [준법 출시 게이트](docs/COMPLIANCE.md)의 서면 승인이 있기 전에는 활성화할 수 없습니다.

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
├── docker-compose.yml                 # PostgreSQL, Redis, 로컬 전용 Mailpit
├── scripts/backup/verify-postgres-backup.ps1 # Creates a PostgreSQL backup and verifies isolated restore
├── docs/                             # 요구사항, 설계, 보안, 운영 및 로컬 기능 테스트 가이드
├── output/pdf/crypto-invest-local-testing-guide.pdf # 로컬 기능 테스트 가이드
├── backend/
│   └── src/
│       ├── main/java/com/cryptoinvest/
│       │   ├── common/               # Health 및 공통 웹 오류
│       │   ├── exchange/             # 거래소 public/private API, 암호화 credential
│       │   ├── market/                 # 시세 및 종목토론방
│       │   ├── indicator/ recommendation/ portfolio/ risk/
│       │   ├── security/             # 인증, 동의, 개인정보 권리
│       │   └── trading/              # 모의거래 및 LIVE 안전 경계
│       ├── main/resources/db/migration/ # V1~V33 Flyway, COMMENT와 제약으로 DB 구조·토론방 게시 형식·댓글·평가 관리
│       └── test/java/                # 단위 및 PostgreSQL 통합 테스트
├── frontend/
│   ├── public/coin-mascot.svg          # 프로젝트 시그니처 로고 및 favicon
│   ├── public/privacy-policy.html
│   └── src/                          # Dashboard, Account/Exchange/Portfolio/Market/Discussion/Recommendation/simulation UI·월간 랭킹, 테스트
└── e2e/
    └── tests/dashboard.spec.ts
```

최상위 구조, `backend`/`frontend`/`e2e`, 모듈, Flyway migration, 운영 문서를 추가·이동·삭제하면 같은 변경에서 이 구조와 관련 설명을 즉시 갱신합니다. 변경 후 실제 파일 목록과 README를 대조하고 `git diff`로 검토합니다.

## 환경 설정

```powershell
Copy-Item .env.example .env
```

`.env`에는 실제 DB 비밀번호와 Base64 32-byte `CREDENTIAL_ENCRYPTION_KEY`, `AUTH_TOKEN_SECRET`을 저장합니다. 두 키는 Git·로그·Frontend에 노출하면 안 됩니다. Spring Boot는 `.env`를 자동으로 읽지 않으므로 Backend를 시작하는 PowerShell 프로세스에 값을 불러와야 합니다. 거래소 credential을 저장한 뒤 암호화 키를 바꾸면 기존 키를 복호화할 수 없습니다.

```env
LIVE_TRADING_ENABLED=false
LIVE_TRADING_CONFIRMATION_MINUTES=15
```

## 실행

첫 PowerShell 창에서 프로젝트 루트에서 DB를 시작하고 `.env`를 출력 없이 현재 프로세스에 불러온 뒤 Backend를 실행합니다.

```powershell
docker compose up -d
docker compose ps
Get-Content .env | ForEach-Object {
  if ($_ -match '^\s*([^#=]+)=(.*)$') {
    $envName = $matches[1]
    $envValue = $matches[2]
    Set-Item -Path "Env:$envName" -Value $envValue
  }
}
Set-Location backend
$env:JAVA_HOME = 'C:\dev\jdk-21.0.12.1'
.\gradlew.bat bootRun --console=plain
```

두 번째 PowerShell 창에서는 Frontend를 실행합니다.

```powershell
Set-Location C:\dev\crypto-invest\frontend
npm install
npm run dev -- --host 127.0.0.1
```

브라우저 주소는 `http://127.0.0.1:5173`이며 `/api/*` 요청은 Backend `127.0.0.1:8080`으로 프록시됩니다. 전체 화면 점검 절차는 [로컬 기능 테스트 가이드](docs/USER_TESTING_GUIDE.md)를 참고합니다.

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
- [TODO](docs/TODO.md), [테스트 결과](docs/TEST_RESULTS.md), [로컬 기능 테스트 가이드](docs/USER_TESTING_GUIDE.md)


## Account verification and SMTP

New accounts require a one-time email code before registration. The code and its confirmation token are stored only as HMAC hashes, expire after 10 and 15 minutes respectively, and are rate-limited. Password changes require the current password; new passwords require 10–20 printable ASCII characters with uppercase, lowercase, digit and symbol. Login does not apply the registration format rule. Email changes require a new-address code and are blocked until 90 days after account creation or the previous email change. Local development uses Mailpit at `http://127.0.0.1:8025`; production deployments must supply an authenticated SMTP provider.


종목별 토론방은 10개씩 페이지를 나누고 상세 글, 첨부 이미지, 댓글, 추천/비추천 및 21개 초과 비추천 블라인드를 제공합니다. 계정 보안 정보는 마이 페이지의 별도 수정 화면에서 관리합니다. 고양이와 코인 SVG 시그니처는 브라우저 아이콘과 상단 로고에 공통 사용합니다.


관리자 역할 변경 감사는 Flyway `V25__admin_role_change_audit.sql`에서 생성하며, 초기 관리자 프로비저닝은 검토된 운영 절차가 필요합니다.


Password reset verifies the mailbox with a one-time email code, applies the same password policy as signup, and revokes all previously issued bearer sessions. Monthly PAPER valuations store the quote capture time and pause participant fills until the opening snapshot is recorded.
