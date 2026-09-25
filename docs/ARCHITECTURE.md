# ARCHITECTURE.md

## 1. 기본 원칙

초기 구조는 **Modular Monolith**를 우선한다.

서비스가 충분히 커지기 전까지 Microservice로 분리하지 않는다.

---

## 2. 전체 흐름

```text
Exchange APIs
     ↓
ExchangeClient
     ↓
MarketData / AccountData
     ↓
IndicatorEngine
     ↓
RecommendationEngine
     ↓
PortfolioEngine
     ↓
RiskEngine
     ↓
OrderPlanner
     ↓
TradingService
     ↓
PaperExchange / Real Exchange
```

---

## 3. 주요 Module

### exchange

거래소별 API 연동.

예:

```text
ExchangeClient
├── UpbitExchangeClient
├── BithumbExchangeClient
└── MockExchangeClient
```

거래소별 응답은 내부 공통 Model로 변환한다.

### market

- OHLCV
- 현재가
- 거래량
- 시장 데이터 Cache

### indicator

- 이동평균
- RSI
- Momentum
- Volatility
- 기타 기술지표

### recommendation

추천 계산은 유료 AI에 의존하지 않는 재현 가능한 규칙으로 구성한다. 공통 모델은 `MarketRegime`, signed asset score, 별도 confidence, factor contributions, metric availability/source/capture time을 표현하며 결측 지표는 추정하지 않는다. Coin-specific strategy와 Portfolio Recommendation은 별도 단계로 분리한다.

Asset score와 사용자 포트폴리오 액션은 서로 다른 결과다. 자산 평가를 규칙 버전·지표·요인·데이터 품질과 함께 시점 스냅샷으로 저장할 DB 구조를 준비한다. portfolio action은 user-scoped 상태를 읽어 별도로 계산한다. 주문 생성/실행은 포함하지 않는다.

### portfolio

- 현재 포트폴리오 계산
- 목표 비중
- Rebalancing Gap
- 자산 배분

### risk

주문 실행 가능 여부를 판단한다.

### trading

- OrderPlan 생성
- Paper / Live 주문 실행
- 주문 상태 추적
- 중복 주문 방지

---

## 4. Backend 권장 Package 예시

```text
com.example.cryptoinvest
├── common
├── auth
├── exchange
│   ├── common
│   ├── upbit
│   └── bithumb
├── market
├── indicator
├── recommendation
├── portfolio
├── risk
├── trading
├── audit
└── config
```

---

## 5. Frontend 권장 구조

```text
src/
├── app/
├── pages/
├── features/
│   ├── dashboard/
│   ├── portfolio/
│   ├── market/
│   ├── recommendation/
│   └── trading/
├── entities/
├── api/
├── components/
└── utils/
```

---

## 6. 의존 방향

권장 방향:

```text
Controller
  ↓
Application / Service
  ↓
Domain
  ↓
Port
  ↓
Infrastructure Adapter
```

거래소 SDK / HTTP Response가 Domain을 직접 침투하지 않도록 한다.

---

## 7. 데이터 흐름 원칙

- Raw Exchange Response는 Adapter 내부에서 처리
- 내부 Model은 거래소에 독립적이어야 함
- 추천 결과와 주문 요청은 별도 Model 사용
- Order Execution 전에 Risk 검증 필수

### market discussion

- 종목 기호별 게시글 API와 저장소를 기존 `market` 모듈에 둔다.
- 읽기는 공개하고 쓰기는 Bearer 인증 및 닉네임 설정 게이트를 적용한다.
- 공개 응답은 닉네임·본문·시각만 포함하며 작성자 이메일과 내부 계정 ID를 포함하지 않는다.
## Login reload and discussion moderation

The SPA restores its bearer token from tab-scoped `sessionStorage` on startup and hydrates account role/nickname through `/api/account/profile`. The backend uses stateless bearer authentication; the token expires after 30 minutes and can be explicitly renewed through an authenticated account endpoint while valid. A token format version change invalidates previously issued tokens during rollout. Logout clears the tab copy. Discussion comments have a dedicated ten-item page API. Post/comment changes are scoped to the authenticated author, while an ADMIN role check protects report listing and hide/dismiss/restore actions.

## Account recovery and PAPER settlement

Password reset extends the shared email-verification service, consumes its one-time token, updates the password, and increments the per-user bearer-token version in one transaction. Previously issued sessions are rejected immediately. Monthly PAPER boundary valuations persist their source quote timestamp on the league entry; the scheduler retries throughout day one in the Seoul timezone.
