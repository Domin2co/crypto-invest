# TESTING.md

## 1. 목표

테스트의 목적은 단순 Build 성공이 아니라 실제 금융 / 거래 로직의 오류와 중복 주문 가능성을 줄이는 것이다.

---

## 2. 테스트 계층

### Unit Test

대상:

- Indicator
- Recommendation
- Portfolio
- Risk
- Fee
- Rounding
- OrderPlan

### Integration Test

대상:

- Repository
- PostgreSQL
- Flyway
- Transaction
- Exchange Adapter

가능하면 Testcontainers를 사용한다.

### E2E Test

대상:

- Login
- Dashboard
- Portfolio
- Recommendation
- Paper Trading
- Auto Invest Setting

---

## 3. Backend 실행

Windows PowerShell:

```powershell
.\gradlew.bat clean test
```

---

## 4. Frontend 실행

```bash
npm run lint
npm run typecheck
npm run test
npm run build
```

---

## 5. E2E

```bash
npm run test:e2e
```

E2E에서는 `PAPER` 또는 Mock Exchange만 사용한다.

---

## 6. 필수 테스트 케이스

관련되는 경우 다음을 확인한다.

- 정상
- Null
- Empty
- Invalid Input
- 최소값
- 최대값
- 경계값
- Timeout
- Network Error
- HTTP 429
- HTTP 500
- Malformed Response
- 잔액 부족

---

## 7. 거래 로직 추가 케이스

- 최소 주문금액
- 수수료
- 가격 Precision
- 수량 Precision
- Rounding
- 최대 주문금액
- 일일 투자한도
- 종목별 최대 비중
- 중복 주문
- Timeout 후 주문 상태 조회

---

## 8. Regression Test

버그 수정 시 가능하면 버그를 먼저 재현하는 테스트를 만든다.

절차:

```text
Bug Reproduction
→ Failing Test
→ Fix
→ Test Pass
→ Related Regression Test
```

---

## 9. Runtime Verification

가능하면 테스트 이후 실제 시스템을 실행한다.

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

확인:

- DB
- Migration
- API
- Browser
- Logs

---

## 10. 완료 기준

검증 결과는 항상 다음 중 하나로 기록한다.

- `PASS`
- `FAIL`
- `NOT EXECUTED`

실행하지 않은 테스트를 통과했다고 기록하지 않는다.
