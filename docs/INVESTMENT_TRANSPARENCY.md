# 투자추천 투명성 기준

- 추천은 투자 권유·수익 보장·자동 주문 지시가 아니며, 주문 실행 전 RiskEngine과 사용자 설정을
  별도로 통과해야 한다.
- 각 추천에는 대상 종목, 평가 시각, 규칙 버전, 시장 레짐, -100~100 자산 점수, 0~100 신뢰도,
  데이터 출처·수집 시각·상태, 요인별 가감점, 사용 지표, 위험 조정, 데이터 지연·결측·모델 한계를 표시한다.
- unavailable/stale/invalid/outlier 지표는 해당 상태를 노출하고 신뢰도를 낮춘다. 값을 임의 추정하거나 누락된 값을 중립 점수로 간주하지 않는다.
- 자산 평가와 사용자 포트폴리오 액션은 구분한다. 어떤 추천도 주문을 직접 생성하거나 실행하지 않는다.
- 과거 성과는 산출 조건·수수료·슬리피지·기간·생존자 편향을 함께 밝히고 미래 수익을 보장하는
  표현을 사용하지 않는다.
- 사용자별 추천/주문은 타 사용자에게 노출하지 않으며, 변경된 정책/버전은 추천 결과와 함께 저장한다.


## 월간 PAPER 랭킹

PAPER 랭킹은 과거 가상 거래의 월간 평가 결과이며 실거래 추천이나 수익 보장이 아니다. 수익률은 월 시작과 종료 시점의 Upbit·Bithumb 가상 지갑 합산 평가액을 비교하며 코인은 해당 거래소의 공개 KRW 시세로 평가한다. 시세 누락/오래된 값은 완료 결과로 확정하지 않는다. 한 달에 PAPER 체결이 없는 참가자는 순위에 표시하지 않는다.
## 2026-09-26 Recommendation Engine stages 2-8 work in progress

- Stage 2: added BigDecimal EMA, MACD and OHLC true-range ATR to the existing indicator engine.
- Stage 3/4 core: added reusable market-regime aggregation and signed weighted scoring (30/20/20/15/10/5), score bands, data-coverage/freshness/agreement/outlier confidence. Empty factor input fails closed.
- Stage 5: added BTC/ETH/SOL/XRP/default metric profiles; unsupported observations remain unavailable and do not receive fabricated values.
- Stage 6 core: added order-free portfolio sizing helper with configurable caps and cash floor. Portfolio account integration, target loading, correlation and volatility input wiring remain TODO.
- Stage 7: Phase 1 domain models already cover factor/status/source/timestamp explainability; full recommendation API and frontend wiring remain TODO.
- Stage 8 core: added forward-return calculation that only accepts observations after the horizon. Snapshot repository, outcome scheduler/API and walk-forward evaluation remain TODO; V33 schema is present.

Free-source review: Alternative.me documents a public Fear & Greed endpoint and requires attribution. DefiLlama documents chain TVL, DEX volume and stablecoin metrics with varying update intervals. Binance documents public derivatives funding/open-interest endpoints but represents Binance derivatives, not Korean spot exchange flows. These sources were reviewed but are not yet wired into runtime; ETF flows and several chain-specific metrics stay unavailable until a supported source is integrated.
### Public source coverage

Reviewed free/public source documentation: [Alternative.me Fear & Greed API](https://alternative.me/crypto/fear-and-greed-index/), [DefiLlama metrics documentation](https://docs.llama.fi/), and [Binance Coin-M futures market-data API](https://developers.binance.com/docs/derivatives/coin-margined-futures/market-data/rest-api/Get-Funding-Info). These providers are not yet wired into runtime; source-specific observations remain explicitly unavailable. Binance derivatives data must not be represented as Upbit/Bithumb spot flow.
## 2026-09-26 Recommendation Engine final implementation pass

- [x] Technical indicators: EMA, MACD, ATR, SMA and volume trend use public daily OHLCV; 30-day median/MAD flags extreme daily returns as OUTLIER and applies a risk penalty.
- [x] Market regime combines BTC 20/60/120-day trend with available Fear & Greed, global market-cap change and Binance funding; unavailable inputs remain visible and are omitted from scoring.
- [x] Signed weighted asset scoring, coverage/freshness/agreement/outlier confidence, per-asset metric profiles and source/timestamp explainability are returned by the recommendation API.
- [x] Free public sources are connected at runtime: Alternative.me Fear & Greed, Binance USD-M funding/open interest, DefiLlama ETH/SOL chain TVL, DEX volume and stablecoin history. CoinGecko global stats require optional COINGECKO_DEMO_API_KEY.
- [x] Authenticated read-only portfolio proposal endpoint: `/api/portfolio/{exchange}/recommendations?market=...`; uses the caller's exchange balance, target weights, cash floor, individual/aggregate alt caps, configurable environment limits, unrealized PnL and available 30-day return correlations. Correlation >=0.85 halves a positive suggested amount. No order call is made.
- [x] V33 point-in-time evaluations are persisted at most once per market/rule/hour. A scheduled job fills 7/30-day observed returns only after each horizon. `/api/recommendations/backtest?days=7|30` exposes return/win-rate groups and the UI displays 7-day results.
- [ ] Data still unavailable without an appropriate free source/key: BTC/ETH ETF flow, MVRV/SOPR/exchange balances, BTC dominance change history, XRP-specific on-chain/RWA/RLUSD/institutional metrics, OI history/change and market-wide volume history. These remain unavailable; no values are estimated.
- [ ] Backtest outcomes and the 30-day UI comparison need matured snapshots; the current database has only new snapshots, so result groups are currently empty. Fees, slippage, survivorship and walk-forward weight calibration are not modeled.
- [ ] Major/high-risk asset classification is a conservative fixed symbol list; correlated holdings without 20 aligned observations are omitted. Review this classification before production use.