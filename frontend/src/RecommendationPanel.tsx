import { useQuery } from '@tanstack/react-query'

type RecommendationView = {
  recommendation: { symbol: string; score: number; signal: string; targetWeight: number; reasons: string[] }
  generatedAt: string
  dataCapturedAt: string
  dataSource: string
  candleCount: number
  indicators: { rsi: number; momentum: number; volatilityPercent: number }
  limitations: string[]
  evaluation: {
    ruleVersion: string
    score: number
    confidence: number
    rating: string
    marketRegime: { regime: string }
    factors: { code: string; explanation: string; points: number; dataStatus: string }[]
    metrics: Record<string, { status: string; value: number | null; source: string | null; capturedAt: string | null; note: string | null }>
  }
}
type BacktestView = { days: number; groups: { rating: string; regime: string; samples: number; averageReturnPercent: number; winRatePercent: number }[]; limitations: string }
export type Exchange = 'UPBIT' | 'BITHUMB'
export type CoinSymbol = string
type Props = { exchange?: Exchange; symbol?: CoinSymbol }

export default function RecommendationPanel({ exchange = 'UPBIT', symbol = 'BTC' }: Props) {
  const market = `KRW-${symbol}`
  const query = useQuery({
    queryKey: ['recommendation', exchange, market],
    queryFn: async () => {
      const response = await fetch(`/api/recommendations/${exchange}?market=${market}`)
      if (!response.ok) throw new Error('recommendation')
      return response.json() as Promise<RecommendationView>
    },
    retry: false,
    staleTime: 60_000,
  })
  const result = query.data
  const backtest = useQuery({ queryKey: ['recommendation-backtest', 7], queryFn: async () => { const response = await fetch('/api/recommendations/backtest?days=7'); if (!response.ok) throw new Error('backtest'); return response.json() as Promise<BacktestView> }, retry: false })
  return <article className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm" aria-labelledby="recommendations-heading">
    <h2 className="text-xl font-bold text-slate-950" id="recommendations-heading">종목 시장 평가</h2>
    <p className="mt-1 text-sm text-slate-600">규칙 기반 점수와 신뢰도입니다. 개인화된 매수 지시나 주문이 아닙니다.</p>
    {query.isPending && <p className="mt-5 text-sm text-slate-600" role="status">시장 평가를 불러오는 중입니다.</p>}
    {query.isError && <p className="mt-5 text-sm text-amber-900" role="status">시장 평가 데이터를 불러오지 못했습니다. 데이터가 없으면 등급을 표시하지 않습니다.</p>}
    {backtest.data && <section className="mt-4 rounded-lg bg-slate-50 p-4" aria-label="Historical recommendation performance"><h3 className="font-semibold">Observed 7-day outcomes</h3>{backtest.data.groups.length ? <ul className="mt-2 space-y-1 text-sm">{backtest.data.groups.map((group) => <li key={`${group.rating}-${group.regime}`}>{group.rating} / {group.regime}: {group.samples} samples, average {group.averageReturnPercent.toFixed(2)}%, wins {group.winRatePercent.toFixed(1)}%</li>)}</ul> : <p className="mt-2 text-sm text-slate-600">No matured recommendation snapshots yet.</p>}<p className="mt-2 text-xs text-slate-500">{backtest.data.limitations}</p></section>}
    {result && <div className="mt-5 divide-y divide-slate-200">
      <div className="py-4"><div className="flex flex-wrap items-baseline justify-between gap-2"><h3 className="font-bold">{exchange} · {result.recommendation.symbol}</h3><span className="rounded-full bg-slate-100 px-3 py-1 text-sm font-bold text-slate-900" aria-label={`시장 평가 ${result.evaluation.rating}`}>{result.evaluation.rating} · {result.evaluation.score > 0 ? '+' : ''}{result.evaluation.score}점 · 신뢰도 {result.evaluation.confidence}%</span></div>
        <p className="mt-2 text-sm text-slate-700">시장 상태: {result.evaluation.marketRegime.regime} · RSI {result.indicators.rsi.toFixed(1)} · 하루 모멘텀 {result.indicators.momentum.toFixed(2)} · 변동성 {result.indicators.volatilityPercent.toFixed(2)}%</p>
        <ul className="mt-3 list-disc space-y-1 pl-5 text-sm text-slate-600">{result.evaluation.factors.map((factor) => <li key={factor.code}>{factor.explanation} ({factor.points > 0 ? '+' : ''}{factor.points}, {factor.dataStatus})</li>)}</ul>
      </div>
      <div className="py-4 text-xs text-slate-500"><p>데이터 출처: {result.dataSource} · 마지막 일봉 {new Date(result.dataCapturedAt).toLocaleString('ko-KR')} · 일봉 {result.candleCount}개</p><p>계산 시각: {new Date(result.generatedAt).toLocaleString('ko-KR')} · 규칙 {result.evaluation.ruleVersion}</p>
        <ul className="mt-2 list-disc space-y-1 pl-5">{result.limitations.map((limitation) => <li key={limitation}>{limitation}</li>)}</ul>
        <p className="mt-2">온체인, 수급, ETF 지표는 연결된 공개 출처가 없어 미확보이며 점수에 포함하지 않았습니다.</p>
      </div>
    </div>}
  </article>
}
