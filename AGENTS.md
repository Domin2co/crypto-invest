# AGENTS.md

## 기본 원칙

코드 작성만으로 작업이 완료되었다고 판단하지 않는다.

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

## 검증

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
