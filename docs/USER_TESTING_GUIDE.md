# Crypto Invest 로컬 기능 테스트 가이드

- 문서 버전: 1.1
- 기준일: 2026-09-23
- 갱신 기준: 화면, API, 실행 절차 또는 환경 설정이 바뀔 때 이 문서와 PDF를 함께 갱신합니다.

## 실행 구성

- Frontend: `http://127.0.0.1:5173`
- Backend: `http://127.0.0.1:8080`
- Frontend의 `/api/*` 요청은 Vite 개발 서버가 Backend 8080으로 전달합니다. 브라우저에서는 5173의 같은 출처 경로를 사용하므로 별도 API 주소를 입력할 필요가 없습니다.
- 기본 거래 모드는 `PAPER`입니다. PAPER 매수·매도만 테스트하며 실제 주문이나 출금은 실행하지 않습니다.

## 준비

프로젝트 루트는 `C:\dev\crypto-invest`입니다. Docker Desktop, JDK 21, Node.js 24 이상을 준비하고 루트 `.env`가 있어야 합니다.

```powershell
Copy-Item .env.example .env
```

`.env`에 PostgreSQL 비밀번호와 Base64 32-byte `AUTH_TOKEN_SECRET`, `CREDENTIAL_ENCRYPTION_KEY`를 설정합니다. 이 프로젝트 로컬 환경에는 두 키가 이미 생성되어 있습니다. 새 키가 필요하면 생성한 값을 `.env`에 저장하고 값을 공유하거나 로그에 남기지 마세요. DB에 거래소 credential을 저장한 뒤 암호화 키를 바꾸면 기존 credential을 복호화할 수 없습니다. `.env`는 Git에서 제외됩니다.

실제 거래소 API key는 회원가입·로그인·공개 시세·추천·PAPER 매매에 필요하지 않습니다. 실잔고를 직접 조회할 때만 읽기 권한만 있는 본인 key를 등록하고 출금 권한은 주지 마세요.

## 1. DB와 Backend 시작

첫 PowerShell 창에서 프로젝트 루트로 이동한 뒤 실행합니다.

```powershell
docker compose up -d
docker compose ps
```

PostgreSQL과 Redis가 `healthy`인지 확인합니다. 같은 창에서 `.env` 설정을 프로세스에 불러오고 백엔드를 시작합니다. 키 값은 출력되지 않습니다.

```powershell
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

시작 로그에서 `Started CryptoInvestApplication`, Flyway schema version 9를 확인합니다. 다른 창에서 `http://127.0.0.1:8080/api/health`를 열면 `status`가 `UP`, `tradingMode`가 `PAPER`여야 합니다. Backend 창에는 다음 형태로 API 요청 로그가 표시됩니다.

```text
api_request method=GET path=/api/health status=200 duration_ms=...
```

로그는 method, 경로, 상태 코드, 처리 시간만 기록합니다. Authorization 헤더, query string, 요청 본문은 기록하지 않습니다. 서버는 이 창에서 계속 실행하고 종료할 때 `Ctrl+C`를 누릅니다.

## 2. Frontend 시작

두 번째 PowerShell 창에서 실행합니다.

```powershell
Set-Location C:\dev\crypto-invest\frontend
npm run dev -- --host 127.0.0.1
```

브라우저에서 `http://127.0.0.1:5173`을 엽니다. 대시보드의 시스템 상태가 `정상`으로 보이면 프론트엔드에서 백엔드 health API까지 연결된 것입니다. Frontend 창도 종료는 `Ctrl+C`입니다.

## 3. 화면과 계정 확인

상단 메뉴는 별도 경로로 이동합니다: 대시보드 `/`, 포트폴리오 `/portfolio`, 시장 `/market`, 추천 `/recommendations`, 거래 이력 `/history`. 개인정보 처리방침은 모든 화면 하단 링크에서 열립니다. `/privacy-policy.html`로 접근해도 공통 디자인이 적용된 `/privacy`로 이동합니다.

1. `로그인 / 회원가입`을 눌러 `/account`로 이동합니다.
2. `회원가입` 탭에서 테스트용 이메일과 12자 이상 비밀번호를 입력합니다.
3. 개인정보 처리 동의를 체크합니다. 필수 동의이며 마케팅 동의는 선택입니다.
4. 가입 성공 후 계정 이메일을 확인합니다. 토큰은 현재 브라우저 메모리에만 보관되고 localStorage/sessionStorage에는 저장되지 않습니다.
5. `로그아웃`한 뒤 `로그인` 탭에서 같은 이메일과 비밀번호로 다시 로그인합니다.

가입/로그인 요청은 브라우저에서 `http://127.0.0.1:5173/api/auth/register`, `/api/auth/login`으로 보이고, Vite가 이를 Backend 8080으로 전달합니다. 서버에서는 같은 경로를 처리하고 200과 access token을 응답합니다.

### 회원가입 409 오류

빈 `AUTH_TOKEN_SECRET` 때문에 토큰 서명 단계에서 `IllegalStateException`이 발생했고, 공통 예외 처리기가 이를 409 `INVALID_STATE`로 응답했습니다. 가입 서비스가 트랜잭션이어서 가입 데이터는 롤백됩니다. 로컬 `.env`에 유효한 32-byte Base64 키를 저장하고 그 파일의 변수를 Backend를 실행하는 같은 창에 불러오면 해결됩니다. 현재 로컬 설정은 조치되어 있으며 실제 가입·로그인 브라우저 테스트가 통과했습니다. 가입 화면은 409가 발생하면 서버 상태/설정 확인 메시지를 보여줍니다.

## 4. 기능 점검

- **시장:** 공개 KRW-BTC 시세와 데이터 출처·시각을 확인합니다. 로그인/API key가 필요하지 않습니다.
- **추천:** 추천 점수, 계산 근거, 데이터 시각, 위험·한계를 확인합니다. 추천은 주문 지시가 아닙니다.
- **PAPER 매수·매도:** 로그인 후 대시보드에서 주문 금액 `10000 KRW`, 모의 가격 `100000000 KRW/BTC`로 매수합니다. 이어서 `SELL`과 `0.0001 BTC` 수량으로 매도하고, 거래 이력 메뉴에서 두 건을 확인합니다. 이는 가상 지갑 체결입니다.
- **포트폴리오:** 거래소 계정을 연동하고 나서 새로고침해야 실잔고를 읽습니다. API key는 암호화 저장되고 다시 화면에 표시되지 않습니다.
- **개인정보:** 계정 화면에서 동의 이력을 확인하고 선택 마케팅 동의를 철회하거나 계정을 삭제할 수 있습니다.

## 5. 검증 실행

프로젝트 루트에서 프론트 정적 검증을 실행합니다.

```powershell
Set-Location frontend
npm run lint
npm run typecheck
npm run test
npm run build
```

브라우저 E2E는 Backend와 Docker 서비스를 실행한 상태에서 별도 창으로 실행합니다.

```powershell
Set-Location C:\dev\crypto-invest\e2e
npm run test:e2e
```

E2E는 테스트 이메일로 가입·로그인하고 PAPER 주문을 검증합니다. 거래소 LIVE 주문 경로는 호출하지 않습니다.

## 종료와 문제 해결

Backend와 Frontend 창에서 각각 `Ctrl+C`를 누릅니다. DB와 Redis도 중지하려면 루트에서 `docker compose stop`을 사용합니다. `docker compose down -v`는 DB 데이터를 지울 수 있으므로 사용하지 마세요.

| 증상 | 확인할 점 |
| --- | --- |
| 회원가입이 409로 실패 | Backend를 시작한 PowerShell 창에 `.env`의 `AUTH_TOKEN_SECRET`이 불러와졌는지 확인합니다. 키 값은 로그나 채팅에 붙이지 않습니다. |
| 회원가입이 400으로 실패 | 이메일 형식, 12자 이상 비밀번호, 개인정보 필수 동의, 기존 가입 여부를 확인합니다. |
| 로그인 실패 | 가입 시 사용한 이메일과 비밀번호인지 확인합니다. |
| 대시보드 상태가 연결 확인 필요 | Backend가 8080에서 실행 중인지 확인하고 `http://127.0.0.1:8080/api/health`에 접속합니다. |
| 거래소 credential 저장 실패 | `CREDENTIAL_ENCRYPTION_KEY`가 Backend 프로세스에 설정됐는지 확인합니다. |
| DB 연결 실패 | `docker compose ps`의 DB 상태와 `.env`의 DB 설정을 확인합니다. |
| 공개 시세·추천 실패 | 인터넷 연결 및 Backend 창의 API 상태 로그를 확인합니다. |
| 8080 포트 사용 중 | 기존 Backend 프로세스를 종료한 뒤 다시 실행합니다. |

실제 LIVE 주문은 이 가이드의 확인 범위가 아니며 기본 모드는 계속 PAPER입니다. 공개 서비스 전 개인정보 처리방침, 법률 검토 및 운영 출시 승인 절차는 별도로 완료해야 합니다.

