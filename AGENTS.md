# AGENTS.md

## 기본 원칙

코드 작성만으로 작업이 완료되었다고 판단하지 않는다.

## 개발 도구 운영 원칙

- 코드 변경 전 Serena로 기존 코드 구조와 관련 심볼·호출 지점을 조사한다.
- 설계와 구현은 Ponytail `full` 기준을 따른다. 기존 구현·표준 기능을 우선 재사용하고, 검증 가능한 최소 구조만 추가한다.
- 조사 결과·도구 출력이 길어져 컨텍스트 부담이 커지면 Headroom으로 오래된 조사 내용을 압축한 뒤 작업을 이어간다.
- Headroom MCP는 이 프로젝트의 `.codex/config.toml`에만 등록한다. 전역 Codex 설정에는 등록하지 않는다.

항상 다음 순서를 따른다.

1. 관련 코드와 문서 확인
2. 영향 범위 확인
3. 간단한 구현 계획 작성
4. 필요한 범위만 구현
5. 정적 분석
6. 변경 범위에 맞는 테스트 실행
7. 가능한 경우 Runtime Verification
8. API / Browser Verification
9. `git diff` 검토
10. 실제 검증 결과와 남은 위험요소 보고

현재 작업과 관계없는 파일은 수정하지 않는다.
사용자의 기존 변경사항을 임의로 되돌리지 않는다.
불필요한 대규모 리팩터링은 피한다.

---

## 작업 전 문서 확인

작업 내용에 따라 다음 문서를 먼저 확인한다.

- 요구사항: `docs/REQUIREMENTS.md`
- 아키텍처: `docs/ARCHITECTURE.md`
- 보안: `docs/SECURITY.md`
- 테스트: `docs/TESTING.md`
- 거래 규칙: `docs/TRADING_RULES.md`
- 거래소 API: `docs/API_RULES.md`
- DB: `docs/DATABASE.md`
- 설계 결정: `docs/DECISIONS.md`
- 현재 작업: `docs/TODO.md`
- 개인정보·이용자 권리: `docs/PRIVACY_POLICY.md`, `docs/COMPLIANCE.md`
- 보안 구현 기준: `docs/SECURE_CODING.md`
- 투자추천 투명성: `docs/INVESTMENT_TRANSPARENCY.md`
- 접근성·UX: `docs/ACCESSIBILITY_UX.md`
- 운영·사고 대응: `docs/OPERATIONS.md`
- 이전 테스트 결과·피드백: `docs/TEST_RESULTS.md`

---

## 필수 안전 규칙

- 기본 거래 모드는 `PAPER`이다.
- 자동 테스트, E2E, Browser Automation 중 실제 주문을 실행하지 않는다.
- 사용자의 명시적인 요청 없이 실거래를 활성화하지 않는다.
- 출금 기능은 명시적인 요청 없이 구현하지 않는다.
- API Key, Secret, DB Password 등 민감정보를 Git, 로그, Frontend에 노출하지 않는다.
- 금융 계산에는 `BigDecimal`을 사용한다.
- 실제 주문 전 `RiskEngine` 검증을 우회하지 않는다.
- 주문 요청 Timeout 후 무조건 재시도하지 않는다. 기존 주문 상태를 먼저 확인한다.

상세 규칙은 `docs/SECURITY.md`와 `docs/TRADING_RULES.md`를 따른다.

---

## 상시 준법·품질 기준

- 개인정보 수집은 목적별 최소 항목과 필수/선택 동의를 구분한다. 마케팅 동의를 서비스 이용 조건으로 만들지 않으며, 동의 버전·시각·철회를 기록한다.
- 개인정보 열람·정정·삭제·처리정지 요청은 본인 인증과 사용자 소유권 검증 뒤에만 처리한다. 거래소 API key·비밀번호·서명은 열람/내보내기 대상에서 제외한다.
- 공개·유료·타인 자동매매 기능은 `docs/COMPLIANCE.md`의 법무·사업·보안 출시 게이트를 모두 통과하기 전에는 활성화하지 않는다. 법률 해석이나 법적 적합성 완료를 임의로 선언하지 않는다.
- 모든 신규 trust boundary에는 입력 검증, 권한 검증, parameterized query, 오류 정보 최소화, secret 무로그를 적용하고 해당 보안 테스트를 추가한다.
- 자동매매는 PAPER 기본값, 명시적 LIVE 이중 스위치, RiskEngine, 한도·kill switch, 멱등성, timeout 후 상태조회만 허용한다. 수익·손실 보장 표현은 금지한다.
- 추천 화면/API는 데이터 시각·출처, 계산 규칙·주요 위험·한계, 추천과 주문의 분리를 표시한다.
- UI 변경은 키보드 조작, 이름/역할/상태, 색상 외 상태표시, 모바일 320px 폭, 핵심 흐름 E2E를 검토한다.
- 테스트 실행 후 실제 명령·환경·PASS/FAIL/NOT EXECUTED·결함·후속 조치를 `docs/TEST_RESULTS.md`에 기록하고, 다음 작업 전 해당 문서의 미해결 항목을 확인한다.

---

## 검증

### 테스트 의존성 다운로드

- 단위 테스트·통합 테스트 실행에 필요한 경량 npm package 설치(`npm install`, `npm ci`)는 사용자에게 테스트 여부나 설치 여부를 다시 묻지 않고 진행한다.
- 전역 package 설치, 브라우저 바이너리 같은 대용량 다운로드, 운영 의존성 변경은 이 규칙에 포함하지 않는다.

### Backend 변경

Windows PowerShell:

```powershell
.\gradlew.bat clean test
```

### Frontend 변경

```bash
npm run lint
npm run typecheck
npm run test
npm run build
```

### 사용자 Flow / Frontend-Backend 연동 변경

```bash
npm run test:e2e
```

현재 변경과 관련 있는 검증만 수행한다.

실행하지 못한 검증은 반드시 `NOT EXECUTED`로 보고한다.

---

## Runtime Verification

실행 가능한 변경은 가능하면 실제 시스템을 실행하여 확인한다.

예:

```powershell
docker compose up -d
docker compose ps
```

Backend:

```powershell
.\gradlew.bat bootRun
```

Frontend:

```bash
npm run dev
```

확인 대상:

- Container 상태
- DB 연결
- Flyway Migration
- Backend 기동
- Frontend 기동
- 변경 API
- Browser 동작
- Console / Network Error
- Server Log

---

## Git

작업 전:

```bash
git status
```

작업 후:

```bash
git diff
```

다음 작업은 명시적인 요청 없이 수행하지 않는다.

- `git commit`
- `git push`
- `git merge`
- `git rebase`
- `git reset`
- `git reset --hard`
- `git push --force`

---

## 완료 보고

작업 완료 시 다음을 보고한다.

- 변경 내용
- 변경 파일
- 실제 실행한 명령
- 각 검증의 `PASS` / `FAIL` / `NOT EXECUTED`
- Runtime Verification 결과
- 미검증 항목
- 남은 위험요소

실제로 실행하지 않은 테스트나 검증을 성공했다고 말하지 않는다.
