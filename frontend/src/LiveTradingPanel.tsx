import { useState } from 'react'
import type { FormEvent } from 'react'
import { useQuery } from '@tanstack/react-query'
import type { CoinSymbol, Exchange } from './RecommendationPanel'
import MarketTradingPanel from './MarketTradingPanel'
import { useMarketStream } from './useMarketStream'

type LiveHistory = { exchange: Exchange; market: string; side: 'BUY' | 'SELL'; orderType: string; limitPrice: number | null; requestedQuantity: number | null; requestedAmount: number; executedQuantity: number; executedAmount: number; fee: number; status: string; createdAt: string }
const won = (n: number) => Math.round(n).toLocaleString('ko-KR')
const tabs = [['BUY', '매수'], ['SELL', '매도'], ['QUICK', '간편주문'], ['ORDERBOOK', '호가주문'], ['HISTORY', '거래내역']] as const

/** 공개 시장정보와 실거래 설정을 분리하고, 서버 위험 검증을 거친 주문만 제출한다. */
export default function LiveTradingPanel({ token, liveOrderSubmissionEnabled, onNavigate }: { token: string | null; liveOrderSubmissionEnabled: boolean; onNavigate: (path: string) => void }) {
  const [exchange, setExchange] = useState<Exchange>('UPBIT')
  const [symbol, setSymbol] = useState<CoinSymbol>('BTC')
  const stream = useMarketStream(symbol)
  const quote = stream.tickers[exchange]
  const [tab, setTab] = useState<'BUY' | 'SELL' | 'QUICK' | 'ORDERBOOK' | 'HISTORY'>('BUY')
  const [side, setSide] = useState<'BUY' | 'SELL'>('BUY')
  const [amount, setAmount] = useState('10000')
  const [quantity, setQuantity] = useState('')
  const [limitPrice, setLimitPrice] = useState('')
  const [idempotencyKey, setIdempotencyKey] = useState(() => crypto.randomUUID())
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const history = useQuery({ queryKey: ['live-order-history', token], enabled: !!token && tab === 'HISTORY', retry: false, queryFn: async () => { const response = await fetch('/api/live-trading/orders', { headers: { Authorization: `Bearer ${token}` } }); if (!response.ok) throw new Error('history'); return response.json() as Promise<LiveHistory[]> } })

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!token || !liveOrderSubmissionEnabled || busy || !quote) return
    const orderQuantity = tab === 'QUICK' && side === 'SELL' ? Number(amount) / quote.price : tab === 'ORDERBOOK' && side === 'BUY' ? Number(amount) / Number(limitPrice) : Number(quantity)
    const description = `실제 ${exchange} ${side === 'BUY' ? '매수' : '매도'} ${tab === 'ORDERBOOK' ? 'IOC 지정가' : '시장가'} 주문을 제출합니다. ${symbol} ${side === 'BUY' ? `${Number(amount).toLocaleString('ko-KR')} KRW` : `${orderQuantity} ${symbol}`}${tab === 'ORDERBOOK' ? ` · 지정가 ${limitPrice} KRW` : ''}\n계속하려면 확인을 누르세요.`
    if (!window.confirm(description)) return
    setBusy(true); setMessage('')
    try {
      const confirmation = await fetch('/api/live-trading/confirm', { method: 'POST', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, body: JSON.stringify({ accepted: true }) })
      if (!confirmation.ok) throw new Error('confirmation')
      const response = await fetch('/api/live-trading/orders', { method: 'POST', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, body: JSON.stringify({ exchange, market: `KRW-${symbol}`, side, amount: side === 'BUY' ? Number(amount) : null, quantity: side === 'SELL' ? orderQuantity : null, orderType: tab === 'ORDERBOOK' ? 'LIMIT' : 'MARKET', limitPrice: tab === 'ORDERBOOK' ? Number(limitPrice) : null, idempotencyKey }) })
      if (!response.ok) throw new Error('order')
      const result = await response.json() as { order: { status: string; executedQuantity: number; executedAmount: number } }
      setMessage(`실주문 상태: ${result.order.status} · 체결 수량 ${result.order.executedQuantity} · 체결 금액 ${won(result.order.executedAmount)} KRW`)
      setIdempotencyKey(crypto.randomUUID())
      history.refetch()
    } catch { setMessage('주문 결과를 확인하지 못했습니다. 재시도 전 주문 이력에서 상태를 확인해 주세요. 같은 주문 키를 유지했습니다.') } finally { setBusy(false) }
  }

  const orderPanel =     <section className="rounded-xl border border-slate-200 bg-white p-3 shadow-sm sm:p-4" aria-labelledby="live-trading-heading">
      <p className="eyebrow text-rose-800">실거래 · 실제 자산이 변합니다</p><h2 className="text-xl font-bold text-slate-950" id="live-trading-heading">주문</h2>
      <div className="mt-3 flex gap-1 overflow-x-auto rounded-lg bg-slate-100 p-1" role="tablist" aria-label="실거래 주문 메뉴">{tabs.map(([key, label]) => <button key={key} type="button" role="tab" aria-selected={tab === key} className={`whitespace-nowrap rounded-md px-3 py-2 text-xs font-semibold transition-colors sm:text-sm ${tab === key ? 'bg-white text-slate-950 shadow-sm ring-1 ring-slate-200' : 'text-slate-600 hover:bg-white/70'}`} onClick={() => { setTab(key); if (key === 'BUY' || key === 'SELL') setSide(key) }}>{label}</button>)}</div>
      {tab === 'HISTORY' ? <div role="tabpanel" className="mt-4">{!token ? <p>로그인 후 본인 실주문 기록을 확인할 수 있습니다.</p> : history.isPending ? <p role="status">실거래 내역을 불러오는 중입니다.</p> : history.isError ? <p role="alert">실거래 내역을 불러오지 못했습니다.</p> : history.data?.length ? <ol className="divide-y divide-slate-100">{history.data.map((order, index) => <li className="grid grid-cols-[1fr_auto] gap-1 py-3 text-sm" key={`${order.createdAt}-${index}`}><strong>{order.exchange === 'UPBIT' ? '업비트' : '빗썸'} {order.side === 'BUY' ? '매수' : '매도'} {order.market} · {order.orderType === 'LIMIT' ? `지정가 ${won(order.limitPrice ?? 0)}원` : '시장가'}</strong><span>{won(order.executedAmount)}원</span><span className="text-xs text-slate-500">요청 {won(order.requestedAmount)}원 · 요청 수량 {order.requestedQuantity ?? '-'} · 체결 {order.executedQuantity}개</span><span className="text-right text-xs text-slate-500">{order.status} · {new Date(order.createdAt).toLocaleString('ko-KR')}</span></li>)}</ol> : <p className="text-sm text-slate-600">저장된 실주문 내역이 없습니다.</p>}</div>
      : !token ? <div role="tabpanel" className="panel-card mt-4"><p className="panel-title">로그인이 필요합니다</p><p className="panel-description">시장 시세는 공개 정보입니다. 실주문과 계정 내역은 로그인 후 본인 계정에서 확인합니다.</p><button className="primary-button mt-4" type="button" onClick={() => onNavigate('/account')}>로그인 / 회원가입</button></div>
      : !liveOrderSubmissionEnabled ? <div role="tabpanel" className="mt-4 rounded-lg bg-amber-50 p-4"><p className="font-semibold text-amber-950">실거래 주문이 비활성화되어 있습니다</p><p className="mt-1 text-sm text-amber-900">시세와 호가는 계속 조회할 수 있습니다. 주문은 운영 스위치와 위험 한도, 사용자 재확인 및 거래소 잔고 검증이 모두 허용될 때만 가능합니다.</p></div>
      : <form role="tabpanel" className="mt-3 grid gap-3 sm:max-w-xl" onSubmit={submit}>
        <p className="rounded-lg bg-rose-50 p-3 text-sm font-semibold text-rose-900">{exchange === 'UPBIT' ? '업비트' : '빗썸'} · KRW-{symbol} · {quote ? `${won(quote.price)}원` : '실시간 시세 대기'}</p>
        {tab === 'QUICK' && <div><p className="mb-2 text-sm font-medium">간편 주문 금액</p><div className="flex flex-wrap gap-2">{[10000, 50000, 100000].map((value) => <button key={value} type="button" aria-pressed={Number(amount) === value} className="rounded-md border border-slate-300 px-3 py-2 text-sm" onClick={() => setAmount(String(value))}>{value.toLocaleString('ko-KR')}원</button>)}</div><div className="mt-3 flex gap-2"><button type="button" className={`rounded-md px-4 py-2 text-sm ${side === 'BUY' ? 'bg-rose-700 text-white' : 'bg-slate-100'}`} onClick={() => setSide('BUY')}>매수</button><button type="button" className={`rounded-md px-4 py-2 text-sm ${side === 'SELL' ? 'bg-blue-700 text-white' : 'bg-slate-100'}`} onClick={() => setSide('SELL')}>매도</button></div></div>}
        {tab !== 'QUICK' && <p className="font-semibold">{side === 'BUY' ? '매수 주문' : '매도 주문'}</p>}
        {tab === 'ORDERBOOK' && <label className="grid gap-1 text-sm font-medium">호가 지정 가격(KRW)<input className="field-input" type="number" min="0.00000001" step="any" value={limitPrice} onChange={(event) => setLimitPrice(event.target.value)} required /></label>}
        {side === 'BUY' ? <label className="grid gap-1 text-sm font-medium">주문 금액(KRW)<input className="field-input" type="number" min="5000" step="1" value={amount} onChange={(event) => setAmount(event.target.value)} required /></label> : tab === 'QUICK' ? <p className="text-sm text-slate-700">예상 매도 수량 {quote ? Number(amount).toLocaleString('ko-KR') + '원 ÷ ' + won(quote.price) + '원' : '시세 대기'}</p> : <label className="grid gap-1 text-sm font-medium">매도 수량({symbol})<input className="field-input" type="number" min="0.00000001" step="0.00000001" value={quantity} onChange={(event) => setQuantity(event.target.value)} required /></label>}
        <p className="text-xs text-rose-800">주문 전 거래소 잔고·최소 금액·일일 한도·RiskEngine 검증과 최종 확인이 필요합니다. 지정가 주문은 IOC 방식으로 남은 미체결 수량을 즉시 취소합니다.</p>
        <button className="w-full rounded-lg bg-rose-700 px-4 py-2.5 font-bold text-white disabled:opacity-50 sm:w-fit" type="submit" disabled={busy || !quote}>{busy ? '주문 확인 중…' : tab === 'QUICK' ? '간편 주문 검토' : tab === 'ORDERBOOK' ? '호가 주문 검토' : '주문 검토'}</button>
      </form>}
      {message && <p className="mt-4 rounded-lg bg-amber-50 p-3 text-sm text-amber-900" role="status">{message}</p>}
    </section>
  return <MarketTradingPanel stream={stream} exchange={exchange} symbol={symbol} onExchangeChange={setExchange} onSymbolChange={setSymbol} onPriceSelect={(price, selectedSide) => { setLimitPrice(String(price)); setSide(selectedSide); setTab('ORDERBOOK') }} orderPanel={orderPanel} token={token} />
}