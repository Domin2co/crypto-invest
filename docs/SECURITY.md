# SECURITY.md

## 실거래 사용자 재확인

새 LIVE 주문은 최근 사용자 재확인이 없으면 거절한다. 재확인 API는 주문을 생성하지 않고 동의 이력과
`LIVE_TRADING_CONFIRMED` 감사 이벤트만 기록한다. 기본 유효시간은 15분이며 `LIVE_TRADING_CONFIRMATION_MINUTES`로 조정한다.

## 주문 멱등성 및 사용자 격리

PAPER 체결과 LIVE 주문의 기존 결과 조회는 인증된 사용자 ID와 멱등 키를 함께 조건으로 사용한다.
DB의 전역 멱등 키 제약에서 다른 사용자의 키와 충돌하면 기존 계획·체결을 반환하지 않고 새 주문도 진행하지 않는다.
PAPER 체결 중 이 충돌이 발생하면 지갑 변경과 주문 기록을 같은 트랜잭션에서 롤백한다.

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

실거래는 PAPER 외 모드, `LIVE_TRADING_ENABLED=true`, `LIVE_TRADING_KILL_SWITCH=false`, 양수 일일 한도와 RiskEngine·최근 사용자 재확인이 모두 통과된 경우에만 허용한다.

전역 `LIVE_TRADING_KILL_SWITCH`는 기본 `true`이며, 켜져 있는 동안 다른 설정이나 사용자 위험 정책과 관계없이 신규 LIVE 주문을 차단한다. 운영 승인과 출시 게이트를 통과하기 전에는 이 값을 해제하지 않는다.

추가 보호 후보:

- LiveTradingGuard
- 사용자 재확인
- 일일 한도
- 종목별 한도
- 전역 Kill Switch (`LIVE_TRADING_KILL_SWITCH`, 기본 차단)

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
