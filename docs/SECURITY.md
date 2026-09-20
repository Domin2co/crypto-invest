# SECURITY.md

## 1. 기본 원칙

이 프로젝트는 거래소 API Key와 실제 자산을 다룰 수 있으므로 일반 웹 프로젝트보다 보수적인 보안 정책을 적용한다.

---

## 2. Secret 관리

Secret 예:

- Upbit Access Key
- Upbit Secret Key
- Bithumb API Key
- Bithumb Secret Key
- DB Password
- JWT Secret
- Encryption Key

금지:

- Git Commit
- Source Hardcoding
- Frontend 저장
- Browser Local Storage 저장
- API Response 노출
- Log 출력

개발 초기 Secret은 `.env`로 관리한다.

---

## 3. `.env`

Git 제외:

```gitignore
.env
.env.*
!.env.example
```

`.env.example`에는 Key 이름만 포함하고 실제 값은 넣지 않는다.

---

## 4. 사용자별 거래소 Key

다중 사용자 기능 도입 후에는 사용자별 거래소 Key를 `.env`에 저장하지 않는다.

권장 구조:

```text
.env
└─ Master Encryption Key

Database
└─ Encrypted User Exchange Credentials
```

---

## 5. 거래소 Key 권한

최소 권한 원칙을 적용한다.

가능하면:

- 자산 조회
- 주문 조회
- 주문

만 활성화한다.

출금 권한은 사용하지 않는다.

---

## 6. 실거래 보호

기본:

```env
TRADING_MODE=PAPER
LIVE_TRADING_ENABLED=false
```

실거래는 두 조건이 모두 명시적으로 활성화된 경우에만 허용한다.

추가 보호 후보:

- LiveTradingGuard
- 사용자 재확인
- 일일 한도
- 종목별 한도
- Kill Switch

---

## 7. 인증 / 인가

다중 사용자 도입 시:

- Spring Security 사용
- 사용자별 Exchange Credential 격리
- 타 사용자 Portfolio / Order 접근 차단
- 관리자 기능 별도 권한

---

## 8. Web Security

최소 검토 대상:

- XSS
- CSRF
- SQL Injection
- CORS
- Authentication Bypass
- Authorization Bypass
- Session / Token 노출

---

## 9. Logging

Logging 가능:

- 주문 ID
- 종목
- 주문 방향
- 금액
- 상태
- 오류 코드

Logging 금지:

- API Secret
- JWT Secret
- DB Password
- Authorization Header
- 전체 Private API Request Header

---

## 10. 사고 대응

Secret 노출이 의심되면:

1. 해당 Key 비활성화
2. 새 Key 발급
3. Git History / Log 확인
4. 영향 범위 확인
5. 재발 방지 Rule 추가
