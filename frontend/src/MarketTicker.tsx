import { useQuery } from '@tanstack/react-query'

type MarketPrice = { market: string; price: number; capturedAt: string }

/** 공개 시세 API만 호출한다. 사용자 API Key·지갑 정보는 브라우저에 전달하지 않는다. */
async function loadTicker(): Promise<MarketPrice> {
  const response = await fetch('/api/markets/UPBIT/ticker?market=KRW-BTC')
  if (!response.ok) throw new Error('시세를 불러오지 못했습니다.')
  return response.json() as Promise<MarketPrice>
}

export default function MarketTicker() {
  const ticker = useQuery({ queryKey: ['ticker', 'UPBIT', 'KRW-BTC'], queryFn: loadTicker, retry: false })
  const value = ticker.data ? `₩${Math.round(ticker.data.price).toLocaleString('ko-KR')}` : ticker.isLoading ? '불러오는 중' : '연동 대기'

  return <section className="mt-6 rounded-xl border border-slate-800 bg-slate-900 p-5" aria-label="KRW-BTC 공개 시세">
    <p className="text-sm text-slate-400">Upbit 공개 시세 · KRW-BTC</p>
    <strong className="mt-2 block text-2xl text-white">{value}</strong>
    <p className="mt-2 text-sm text-slate-500">공개 API만 사용하며 계정 정보는 요청하지 않습니다.</p>
  </section>
}
