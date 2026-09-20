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

시장 데이터와 Indicator를 이용해 Score와 Signal을 계산한다.

실제 주문 기능은 포함하지 않는다.

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
