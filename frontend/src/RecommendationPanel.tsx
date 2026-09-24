import { useQuery } from '@tanstack/react-query'

type RecommendationView = {
  recommendation: { symbol: string; score: number; signal: string; targetWeight: number; reasons: string[] }
  generatedAt: string
  dataCapturedAt: string
  dataSource: string
  candleCount: number
  indicators: { rsi: number; momentum: number; volatilityPercent: number }
  limitations: string[]
}
export type Exchange = 'UPBIT' | 'BITHUMB'
export type CoinSymbol = string
type Props = { exchange?: Exchange; symbol?: CoinSymbol }

function marketGrade(score: number) {
  if (score >= 80) return { label: 'Excellent', style: 'bg-emerald-100 text-emerald-900' }
  if (score >= 60) return { label: 'Great', style: 'bg-lime-100 text-lime-900' }
  if (score >= 31) return { label: 'Good', style: 'bg-amber-100 text-amber-900' }
  return { label: 'Dangerous', style: 'bg-rose-100 text-rose-900' }
}

/** 점수 등급은 기존 공개 일봉 지표의 요약이며 매수 지시나 주문 요청이 아니다. */
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
  const grade = result ? marketGrade(result.recommendation.score) : null

  return <article className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm" aria-labelledby="recommendations-heading">
    <h2 className="text-xl font-bold text-slate-950" id="recommendations-heading">종목 시장 평가</h2>
    <p className="mt-1 text-sm text-slate-600">선택한 종목의 최근 공개 일봉 지표를 요약합니다. 이 등급은 개인화된 매수 지시가 아닙니다.</p>
    {query.isPending && <p className="mt-5 text-sm text-slate-600" role="status">시장 평가를 불러오는 중입니다.</p>}
    {query.isError && <p className="mt-5 text-sm text-amber-900" role="status">시장 평가 데이터를 불러오지 못했습니다. 데이터가 없으면 등급을 표시하지 않습니다.</p>}
    {result && grade && <div className="mt-5 divide-y divide-slate-200">
      <div className="py-4"><div className="flex flex-wrap items-baseline justify-between gap-2"><h3 className="font-bold">{exchange} · {result.recommendation.symbol}</h3><span className={`rounded-full px-3 py-1 text-sm font-bold ${grade.style}`} aria-label={`시장 평가 ${grade.label}`}>{grade.label} · {result.recommendation.signal} · {result.recommendation.score}점</span></div>
        <p className="mt-2 text-sm text-slate-700">계산상 참고 비중 {(result.recommendation.targetWeight * 100).toFixed(0)}% · RSI {result.indicators.rsi.toFixed(1)} · 하루 모멘텀 {result.indicators.momentum.toFixed(2)} · 변동성 {result.indicators.volatilityPercent.toFixed(2)}%</p>
        <ul className="mt-3 list-disc space-y-1 pl-5 text-sm text-slate-600">{result.recommendation.reasons.map((reason) => <li key={reason}>{reason}</li>)}</ul>
      </div>
      <div className="py-4 text-xs text-slate-500"><p>데이터 출처: {result.dataSource} · 마지막 일봉 {new Date(result.dataCapturedAt).toLocaleString('ko-KR')} · 일봉 {result.candleCount}개</p><p>계산 시각: {new Date(result.generatedAt).toLocaleString('ko-KR')}</p>
        <ul className="mt-2 list-disc space-y-1 pl-5">{result.limitations.map((limitation) => <li key={limitation}>{limitation}</li>)}</ul>
        <p className="mt-2">등급 구간: Excellent 80~100, Great 60~79, Good 31~59, Dangerous 0~30. 거래 성과 점수가 아닙니다.</p>
      </div>
    </div>}
  </article>
}