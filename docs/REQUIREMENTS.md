# REQUIREMENTS.md

## 1. 목적

업비트와 빗썸의 계정 및 시장 데이터를 통합해 다음을 제공하는 시스템을 만든다.

- 자산 조회
- 시장 분석
- 투자 추천
- 포트폴리오 분석
- Paper Trading
- 자동투자
- 리밸런싱

유료 AI API 없이 동작하는 것을 기본 목표로 한다.

---

## 2. 사용자 범위

초기 버전은 개인 사용을 우선한다.

향후 다중 사용자 구조를 고려하되, 초기 MVP에서 불필요한 복잡도를 추가하지 않는다.

---

## 3. 핵심 기능

### 3.0 사용자 닉네임

- 로그인 후 고유 닉네임 지정이 필수이며, 지정 전에는 계정·시장·거래 기능 API 접근을 차단한다.
- 닉네임은 2~8자의 한글·영문·숫자만 허용하고, 대소문자를 구분하지 않고 중복을 막는다.
- 중복확인은 닉네임 등록·변경마다 필수다.

### 3.1 거래소 계정 연동

지원 대상:

- Upbit
- Bithumb

조회 대상:

- KRW 잔액
- 코인 보유량
- 평균 매수가
- 현재가
- 평가금액
- 평가손익
- 수익률

### 3.2 시장 데이터

- 현재가
- 거래량
- OHLCV
- 기간별 차트
- 주요 기술지표

### 3.3 투자 추천

추천 결과는 최소 다음 정보를 포함한다.

- 종목
- Score
- Signal
- Target Allocation
- 추천 근거
- Risk Adjustment

### 3.4 포트폴리오 분석

- 총 자산
- 현금 비중
- 종목별 비중
- 평가손익
- 목표 비중과 현재 비중 차이
- Concentration Risk

### 3.5 Paper Trading

실제 거래소 주문을 실행하지 않고 가상 주문을 처리한다.

최소 기능:

- 가상 잔액
- 매수
- 매도
- 체결 기록
- 수수료 반영
- 평가손익

### 3.6 자동투자

지원 모드:

- `REBALANCE_ALL`
- `KEEP_EXISTING_ASSETS`
- `CASH_ONLY`

### 3.7 주문 이력

최소 저장 항목:

- 거래소
- 종목
- 주문 방향
- 주문 유형
- 요청 수량
- 요청 금액
- 체결 수량
- 체결 금액
- 수수료
- 주문 상태
- 생성 시각
- 완료 시각

---

## 4. 비기능 요구사항

### 안전성

- 모의거래는 전용 API와 가상 지갑으로 제공하고 전역 거래 모드는 두지 않는다. 실주문은 기본 잠금 상태다.
- 자동 테스트에서 실거래 금지
- 중복 주문 방지
- 주문 Retry 안전성 확보

### 보안

- Secret Git 저장 금지
- Secret Logging 금지
- API Key Frontend 전달 금지
- 거래소 API 권한 최소화

### 정확성

- 금융 계산은 `BigDecimal`
- 거래소별 Precision / Tick Size 고려

### 재현성

동일 입력과 동일 설정은 가능한 한 동일 추천 결과를 반환해야 한다.

---

## 5. MVP 범위

1차 MVP:

- Upbit 연동
- Bithumb 연동
- 자산 조회
- 시장 데이터
- 기술지표
- 규칙 기반 추천
- Paper Trading
- 포트폴리오 분석

2차:

- 자동투자
- 리밸런싱
- 고급 Risk Rule
- 알림

3차:

- 실거래
- 다중 사용자
- 운영 모니터링

---

## 6. 제외 범위

명시적 요구가 생기기 전까지 제외한다.

- 가상자산 출금
- 레버리지
- 선물 / 옵션
- Margin Trading
- 유료 AI API 기반 추천


## 월간 PAPER 대회

- 참가 신청은 별도의 선택 `PAPER_LEADERBOARD` 공개 동의를 받은 계정만 할 수 있으며, 신청한 다음 달부터 참가한다. 가입/로그인만으로 공개하지 않는다.
- 한국 시간 기준 매월 1일 시작 시점과 다음 달 1일 종료 시점의 Upbit·Bithumb PAPER 지갑 총 평가액(KRW)을 사용한다. 코인 보유분은 해당 거래소의 공개 KRW 시세로 평가한다.
- 월간 PAPER 체결이 한 건 이상인 참가자만 순위에 포함하며 수익률은 `(종료 평가액 - 시작 평가액) / 시작 평가액 × 100`으로 계산한다. 수익률이 같으면 닉네임을 대소문자 구분 없이 오름차순 정렬한다.
- 1~3위에 Gold/Silver/Bronze 표시를 제공한다. 현금성 상금이나 실제 투자 성과를 약속하지 않는다.
- 공개 항목은 닉네임, 월간 수익률, 체결 수, 순위, 메달로 제한한다. 이메일과 잔고 금액은 공개하지 않는다. 공개 동의 철회 시 참가·성과·메달 기록을 삭제한다.
## 종목별 토론방

- 종목별 게시글 목록은 종목 화면에서 최신 50개를 조회한다. 로그인하지 않은 방문자는 읽을 수 있다.
- 게시글 작성은 로그인과 고유 닉네임 설정 후 허용한다. 공개 작성자 표시는 닉네임이며 이메일·계정 ID는 노출하지 않는다.
- 글은 최대 1,000자 일반 텍스트로 저장하고 HTML로 실행하지 않는다. 계정 삭제 요청 시 작성 글도 삭제한다.

## Account security and email lifecycle

- Registration requires a one-time code sent to the address being registered.
- Password changes require the current password. Registration and new passwords use 10–20 printable ASCII characters with uppercase, lowercase, number and special character; login applies no registration-format validation.
- All password inputs provide an accessible show/hide toggle.
- Email changes require verification of the new address and are allowed only 90 days after registration or the previous email change.
- Verification codes expire after 10 minutes; confirmation tokens expire after 15 minutes. Sending is limited to one request per address and purpose per 60 seconds; five incorrect codes invalidate a challenge.


## 종목 토론방 상호작용

- 종목별 게시글은 페이지당 10개를 제공하며 목록에는 글번호, 제목, 작성자, 작성일, 추천수만 노출한다. 제목을 눌러 상세 페이지에서 서식과 이미지를 포함한 글을 확인한다.
- 로그인 사용자는 댓글과 추천/비추천을 할 수 있고 글 하나에 한 표만 유지한다. 비추천이 21개 이상이면 본문과 첨부 이미지를 기본 블라인드하고 사용자가 명시적으로 내용 보기를 선택하면 공개한다.
- 상세 글 아래에서 해당 종목 거래 화면과 토론방 목록으로 돌아갈 수 있고, 현재 글은 목록에서 링크가 아닌 현재 항목으로 표시한다.

### 토론방 수정·신고 관리

- 댓글은 최신순으로 페이지당 10개씩 조회한다.
- 작성자는 본인 게시글과 댓글만 수정·삭제할 수 있으며 서버가 소유권을 확인한다.
- 로그인 사용자는 게시글을 신고할 수 있고 동일 사용자의 같은 게시글 중복 신고는 막는다.
- ADMIN만 신고 목록을 열고 게시글 숨김·신고 기각·숨김 복원을 처리한다. 숨김 글은 공개 목록에서 제외하고 상세 본문/이미지를 제공하지 않는다.