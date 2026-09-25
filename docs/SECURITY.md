# SECURITY.md

## 실거래 사용자 재확인

새 LIVE 주문은 최근 사용자 재확인이 없으면 거절한다. 재확인 API는 주문을 생성하지 않고 동의 이력과
`LIVE_TRADING_CONFIRMED` 감사 이벤트만 기록한다. 기본 유효시간은 15분이며 `LIVE_TRADING_CONFIRMATION_MINUTES`로 조정한다.

## 주문 멱등성 및 사용자 격리

PAPER 체결과 LIVE 주문의 기존 결과 조회는 인증된 사용자 ID와 멱등 키를 함께 조건으로 사용한다.
DB의 전역 멱등 키 제약에서 다른 사용자의 키와 충돌하면 기존 계획·체결을 반환하지 않고 새 주문도 진행하지 않는다.
PAPER 체결 중 이 충돌이 발생하면 지갑 변경과 주문 기록을 같은 트랜잭션에서 롤백한다.

## 닉네임 설정 접근 게이트

로그인 후 닉네임이 없으면 `/api/account/profile`, 닉네임 중복확인·저장 API만 허용한다. 다른 인증 API는 서버 Bearer filter에서 `NICKNAME_REQUIRED`로 차단하며, UI 경로 제한을 우회해도 동일하게 적용된다. 중복확인은 저장 직전에도 확인하고 DB case-insensitive unique index가 동시 요청 경합을 막는다.

## TOTP 다중 인증

계정 설정에서 인증 앱 기반 TOTP를 선택적으로 켤 수 있다. 비밀 키는 사용자별 추가 인증 정보를 사용해 AES-GCM 암호화하며, 인증 시간 구간 재사용을 막고 1회용 복구 코드는 원문을 저장하지 않는다. 복구 코드는 설정 완료 시 한 번만 표시한다. 로그인 및 설정 요청에는 DB 기반 계정/IP 제한이 적용된다. TOTP는 피싱 방지 인증이 아니므로 공개 출시 전 운영 HTTPS 도메인에 맞춘 WebAuthn/passkey를 별도 검토한다.

## 신뢰 프록시 IP

`X-Forwarded-For`는 즉시 연결된 프록시가 `TRUSTED_PROXY_CIDRS`에 포함될 때만 오른쪽에서 신뢰 체인을 해석한다. 미설정 또는 비신뢰 직접 연결은 헤더를 무시하고 socket peer IP를 사용한다. 배포 프록시는 클라이언트 제공 forwarding 헤더를 덮어써야 한다.

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
LIVE_TRADING_ENABLED=false
```

실거래는 `LIVE_TRADING_ENABLED=true`, `LIVE_TRADING_KILL_SWITCH=false`, 양수 일일 한도와 RiskEngine·최근 사용자 재확인이 모두 통과된 경우에만 허용한다.

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


## 월간 PAPER 공개 랭킹

- 공개 `GET /api/paper-league`는 현재/완료 월의 동의 활성 참가자만 최소 응답(닉네임, 수익률, 체결 수, 순위, 메달)으로 반환한다. 이메일·계정 ID·잔고는 반환하지 않는다.
- 참가 조회·신청은 인증과 닉네임 설정이 필요하다. 공개 랭킹 선택 동의가 없으면 참가 신청을 거절한다.
- 동의 철회는 공개 응답에서 즉시 제외하고 사용자 참가·결과 기록을 삭제한다. 월 경계 집계는 PAPER 지갑만 읽고 LIVE 주문을 만들지 않는다.
## 종목 토론 게시글

종목 토론방 읽기는 공개 API이고 작성은 인증된 계정만 가능하다. 게시글 작성에도 기존 닉네임 게이트를 적용한다. 본문은 1,000자 이하 plain text로 제한하고 SQL parameter binding과 React text rendering을 사용한다. 응답에 이메일·계정 ID를 노출하지 않으며 계정 삭제 요청 시 해당 사용자의 게시글을 함께 삭제한다.

## Password and email verification

Registration cannot complete without a verified email challenge. Codes are generated with `SecureRandom`, stored as keyed HMAC-SHA256 hashes, expire after 10 minutes, allow at most five attempts and have a 60-second resend interval. Confirmation returns a random one-time token; only its keyed hash is stored and it expires after 15 minutes. Expired challenge rows are purged daily after an additional 24-hour window. Delivery failures return a generic service-unavailable response without logging the code or address.

Password changes verify the current BCrypt password and apply the same 10–20 ASCII character composition policy as registration. Login only requires a non-empty password and does not apply that policy. Email changes verify the new address and enforce the 90-day interval server-side using `email_changed_at`; UI checks are supplemental. Local Mailpit ports are bound to loopback and must not be exposed in production.

## Browser login persistence

Bearer access tokens are kept in tab-scoped `sessionStorage` so a reload in the same tab restores the login. Logout removes the token. The backend remains stateless and validates token expiry after 30 minutes; a token format version change invalidates previously issued longer-lived tokens on rollout, so existing users must sign in again. The UI shows the remaining time and can request a replacement 30-minute token while the current token is valid. Ordinary browsing does not extend it; the browser logs out at expiry. `sessionStorage` is readable by page JavaScript, so XSS prevention remains required. Do not move the token to persistent `localStorage` without a security review.

## Discussion ownership and moderation

Discussion update/delete SQL includes the authenticated user ID; missing ownership is returned as not found. Reports are unique per reporter and post. ADMIN routes check the persisted `app_user.role` server-side on every request. Administrators grant or revoke ADMIN through the authenticated user-management API with a required reason. Each change records the acting and target user IDs, prior and new roles, reason, and timestamp. Initial ADMIN provisioning remains a reviewed manual operation. The API rechecks the persisted actor role after acquiring the role-change lock and prevents self-demotion or removal of the last active administrator. Hiding removes the post from public lists and suppresses body/image access; restoration is recorded against moderation reports.


## Password recovery and mail transport

Password reset uses the existing HMAC-hashed email challenge, five-attempt limit, ten-minute code expiry, and fifteen-minute single-use verification token. The request endpoint responds identically for known and unknown addresses. Production SMTP requires authenticated STARTTLS and certificate hostname verification; credentials belong in deployment secrets.

Password reset increments the per-user token version. Previously issued bearer tokens are rejected immediately, and the new password plus revocation are committed together.

#



## 인증 요청 제한

로그인은 이메일 계정 기준 15분당 10회, IP 기준 60회로 제한한다. 가입 인증 코드와 비밀번호 재설정 메일은 이메일 계정 기준 1시간당 3회, IP 기준 20회로 제한하고 기존 이메일별 재전송 간격과 코드 시도 제한도 유지한다. 제한은 PostgreSQL에서 원자적으로 공유하며 저장 키는 인증 비밀키 기반 HMAC 지문이다. 제한 초과는 HTTP 429와 일반 코드 `RATE_LIMITED`를 반환한다. 애플리케이션은 `HttpServletRequest.getRemoteAddr()`를 사용하며, reverse proxy 배포에서는 신뢰 프록시/전달 헤더 구성을 운영자가 검토하고 프록시가 외부 제공 forwarded 헤더를 제거해야 한다.
