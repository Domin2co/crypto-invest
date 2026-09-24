import { useQuery } from '@tanstack/react-query'
type Summary = { wallets: { exchange: 'UPBIT' | 'BITHUMB'; currency: string; availableAmount: number }[]; orders: { exchange: string; symbol: string; side: string; quantity: number; amount: number; price: number; fee: number; status: string; createdAt: string }[] }
type Slice = { name: string; value: number }
const number = (n: number, digits = 8) => n.toLocaleString('ko-KR', { maximumFractionDigits: digits })
const krw = (n: number) => `₩${Math.round(n).toLocaleString('ko-KR')}`

export default function PaperAccountSummary({ token, revision }: { token: string | null; revision: number }) {
  const query = useQuery({ queryKey: ['paper-summary', token, revision], enabled: !!token, retry: false, queryFn: async () => {
    const response = await fetch('/api/paper/orders/summary', { headers: { Authorization: 'Bearer ' + token } })
    if (!response.ok) throw new Error('paper-summary')
    return response.json() as Promise<Summary>
  } })
  const wallets = query.data?.wallets ?? []
  const priceQuery = useQuery({
    queryKey: ['paper-wallet-prices', wallets.map((w) => `${w.exchange}-${w.currency}-${w.availableAmount}`).join('|')],
    enabled: !!token && wallets.some((wallet) => wallet.currency !== 'KRW' && wallet.availableAmount > 0),
    retry: false,
    refetchInterval: 15_000,
    queryFn: async () => {
      const positions = wallets.filter((wallet) => wallet.currency !== 'KRW' && wallet.availableAmount > 0)
      const prices = await Promise.all(positions.map(async (wallet) => {
        const response = await fetch(`/api/markets/${wallet.exchange}/ticker?market=KRW-${wallet.currency}`)
        if (!response.ok) throw new Error('paper-price')
        const quote = await response.json() as { price: number; capturedAt: string }
        return { key: wallet.exchange + '-' + wallet.currency, price: quote.price, capturedAt: quote.capturedAt }
      }))
      return Object.fromEntries(prices.map((quote) => [quote.key, quote])) as Record<string, { price: number; capturedAt: string }>
    },
  })
  if (!token) return null
  const values: Slice[] = wallets.map((wallet) => ({
    name: `${wallet.exchange === 'UPBIT' ? '업비트' : '빗썸'} ${wallet.currency}`,
    value: wallet.currency === 'KRW' ? wallet.availableAmount : wallet.availableAmount * (priceQuery.data?.[wallet.exchange + '-' + wallet.currency]?.price ?? 0),
  })).filter((slice) => slice.value > 0)
  const total = values.reduce((sum, slice) => sum + slice.value, 0)
  let accumulated = 0
  const stops = values.map((slice, index) => {
    const start = total ? accumulated / total * 100 : 0
    accumulated += slice.value
    const end = total ? accumulated / total * 100 : 0
    return `${['#2563eb', '#06b6d4', '#7c3aed', '#10b981', '#f59e0b', '#f43f5e'][index % 6]} ${start}% ${end}%`
  })
  const capturedAt = Object.values(priceQuery.data ?? {}).map((quote) => quote.capturedAt).sort().at(-1)
  if (!token) return null
  return <>
    <section className="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-3" aria-label="거래소별 모의 지갑 요약">
      {query.data?.wallets.map((wallet, i) => <article className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm" key={wallet.exchange + wallet.currency + i}>
        <p className="text-sm text-slate-600">{wallet.exchange === 'UPBIT' ? '업비트' : '빗썸'} 모의거래 · {wallet.currency}</p>
        <strong className="mt-3 block break-all text-2xl text-slate-950">{number(wallet.availableAmount, wallet.currency === 'KRW' ? 0 : 8)} <span className="text-base">{wallet.currency}</span></strong>
      </article>) ?? <p className="text-sm text-slate-600">모의 지갑을 불러오는 중입니다.</p>}
    </section>
    <section className="mt-6 rounded-xl border border-slate-200 bg-white p-5 shadow-sm" aria-labelledby="paper-portfolio-heading">
      <h2 className="text-xl font-bold text-slate-950" id="paper-portfolio-heading">모의 포트폴리오 비중</h2>
      {priceQuery.isError && <p className="mt-3 text-sm text-amber-800" role="status">일부 현재가를 불러오지 못해 비중을 표시하지 않습니다.</p>}
      {total > 0 && !priceQuery.isError ? <div className="mt-4 grid items-center gap-6 sm:grid-cols-[12rem_1fr]">
        <div className="mx-auto grid size-44 place-items-center rounded-full" style={{ background: `conic-gradient(${stops.join(', ')})` }} role="img" aria-label={`모의 포트폴리오 총 평가액 ${krw(total)}`}><div className="grid size-28 place-content-center rounded-full bg-white text-center"><span className="text-xs text-slate-500">총 평가액</span><strong className="text-sm">{krw(total)}</strong></div></div>
        <ul className="grid gap-2">{values.map((slice, index) => <li className="flex items-center justify-between gap-3 text-sm" key={slice.name}><span className="inline-flex items-center gap-2"><span className="size-3 rounded-sm" style={{ backgroundColor: ['#2563eb', '#06b6d4', '#7c3aed', '#10b981', '#f59e0b', '#f43f5e'][index % 6] }} aria-hidden="true" />{slice.name}</span><span className="text-right font-semibold">{krw(slice.value)} · {(slice.value / total * 100).toFixed(1)}%</span></li>)}</ul>
      </div> : !query.isError && <p className="mt-3 text-sm text-slate-600">잔고를 불러오면 종목별 평가 비중을 표시합니다.</p>}
      {capturedAt && <p className="mt-3 text-xs text-slate-500">공개 현재가 기준 · 갱신 15초 · 최근 시세 {new Date(capturedAt).toLocaleTimeString('ko-KR')}</p>}
    </section>
    <section className="mt-6 rounded-xl border border-slate-200 bg-white p-5 shadow-sm" aria-labelledby="orders-heading">
      <h2 className="text-xl font-bold text-slate-950" id="orders-heading">모의 주문 내역</h2>
      {query.data?.orders.length ? <ul className="mt-4 divide-y divide-slate-200">{query.data.orders.map((order, i) => <li className="py-3 text-sm" key={order.exchange + order.symbol + order.createdAt + i}>
        <strong>{order.exchange === 'UPBIT' ? '업비트' : '빗썸'} {order.side} {order.symbol}</strong>
        <p className="mt-1 text-slate-700">체결 수량 {number(order.quantity)} {order.symbol} · 체결가 {krw(order.price)} · 금액 {krw(order.amount)}</p>
        <p className="mt-1 text-slate-500">수수료 {krw(order.fee)} · {order.status} · {new Date(order.createdAt).toLocaleString('ko-KR')}</p>
      </li>)}</ul> : <p className="mt-3 text-sm text-slate-600">아직 모의거래 체결 내역이 없습니다.</p>}
    </section>
  </>
}