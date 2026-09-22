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

/** 공개 일봉 기반 추천만 보여 주며, 추천 결과를 주문 요청으로 변환하지 않는다. */
export default function RecommendationPanel() {
  const query = useQuery({
    queryKey: ['recommendation', 'UPBIT', 'KRW-BTC'],
    queryFn: async () => {
      const response = await fetch('/api/recommendations/UPBIT?market=KRW-BTC')
      if (!response.ok) throw new Error('recommendation')
      return response.json() as Promise<RecommendationView>
    },
    retry: false,
    staleTime: 60_000,
  })
  const result = query.data

  return <article className="rounded-xl border border-slate-800 bg-slate-900 p-5" aria-labelledby="recommendations-heading">
    <h2 className="text-xl font-bold text-white" id="recommendations-heading">추천과 주문 계획</h2>
    <p className="mt-1 text-sm text-slate-400">추천은 주문과 분리됩니다. 주문 전에는 RiskEngine 검증과 PAPER 모드가 적용됩니다.</p>
    {query.isPending && <p className="mt-5 text-sm text-slate-400" role="status">추천 데이터를 불러오는 중입니다.</p>}
    {query.isError && <p className="mt-5 text-sm text-amber-200" role="status">추천 데이터를 불러오지 못했습니다. 시장 데이터 연결 상태를 확인해 주세요.</p>}
    {result && <div className="mt-5 divide-y divide-slate-800">
      <div className="py-4"><div className="flex flex-wrap items-baseline justify-between gap-2"><h3 className="font-bold">{result.recommendation.symbol}</h3><span className="text-sm font-bold text-cyan-200">{result.recommendation.signal} · 점수 {result.recommendation.score}</span></div>
        <p className="mt-2 text-sm text-slate-300">목표 비중 {(result.recommendation.targetWeight * 100).toFixed(0)}% · RSI {result.indicators.rsi.toFixed(1)} · 변동성 {result.indicators.volatilityPercent.toFixed(2)}%</p>
        <ul className="mt-3 list-disc space-y-1 pl-5 text-sm text-slate-400">{result.recommendation.reasons.map((reason) => <li key={reason}>{reason}</li>)}</ul>
      </div>
      <div className="py-4 text-xs text-slate-500"><p>데이터: {result.dataSource} · {new Date(result.dataCapturedAt).toLocaleString('ko-KR')} · 일봉 {result.candleCount}개</p><p>계산 시각: {new Date(result.generatedAt).toLocaleString('ko-KR')}</p>
        <ul className="mt-2 list-disc space-y-1 pl-5">{result.limitations.map((limitation) => <li key={limitation}>{limitation}</li>)}</ul>
      </div>
    </div>}
  </article>
}
