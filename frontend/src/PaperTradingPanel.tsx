import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import type { FormEvent } from 'react'
import type { CoinSymbol, Exchange } from './RecommendationPanel'
import MarketTradingPanel from './MarketTradingPanel'
import { useMarketStream } from './useMarketStream'

type PaperOrder = { exchange: Exchange; symbol: string; side: string; orderType: string; limitPrice: number | null; quantity: number; amount: number; price: number; fee: number; status: string; createdAt: string }
type PaperFill = { symbol: string; side: string; quantity: number; amount: number; price: number; fee: number; status: string; exchange: Exchange; market: string; capturedAt: string }
const won = (n: number) => '₩' + Math.round(n).toLocaleString('ko-KR')
const qty = (n: number) => n.toLocaleString('ko-KR', { maximumFractionDigits: 8 })

/** 거래소 공개 시장 흐름을 함께 보여주되 주문은 전용 모의거래 API로만 보낸다. */
export default function PaperTradingPanel({ token, exchange, symbol, onExchangeChange, onSymbolChange, onFilled }: { token: string | null; exchange: Exchange; symbol: CoinSymbol; onExchangeChange: (exchange: Exchange) => void; onSymbolChange: (symbol: CoinSymbol) => void; onFilled: () => void }) {
  const [side, setSide] = useState<'BUY' | 'SELL'>('BUY')
  const [tab, setTab] = useState<'BUY' | 'SELL' | 'QUICK' | 'ORDERBOOK' | 'HISTORY'>('BUY')
  const history = useQuery({ queryKey: ['paper-history', token], enabled: !!token && tab === 'HISTORY', retry: false, queryFn: async () => { const response = await fetch('/api/paper/orders/summary', { headers: { Authorization: 'Bearer ' + token } }); if (!response.ok) throw new Error('history'); return response.json() as Promise<{ orders: PaperOrder[] }> } })
  const [amount, setAmount] = useState('10000')
  const [sellQuantity, setSellQuantity] = useState('')
  const [limitPrice, setLimitPrice] = useState('')
  const [limitError, setLimitError] = useState('')
  const [fill, setFill] = useState<PaperFill | null>(null)
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  const stream = useMarketStream(symbol)
  const quote = stream.tickers[exchange]

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!token || !quote) return
    const orderQuantity = tab === 'QUICK' && side === 'SELL' ? Number(amount) / quote.price : Number(sellQuantity)
    setBusy(true); setMessage(''); setLimitError(''); setFill(null)
    try {
      const response = await fetch('/api/paper/orders', { method: 'POST', headers: { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' }, body: JSON.stringify({ exchange, symbol, side, orderType: tab === 'ORDERBOOK' ? 'LIMIT' : 'MARKET', limitPrice: tab === 'ORDERBOOK' ? Number(limitPrice) : null, amount: side === 'BUY' ? Number(amount) : orderQuantity * quote.price, quantity: side === 'SELL' ? orderQuantity : null, idempotencyKey: crypto.randomUUID() }) })
      if (response.status === 409 && tab === 'ORDERBOOK') { setLimitError('지정 가격에 즉시 체결 가능한 반대 호가가 없습니다. 호가를 다시 선택해 주세요.'); return }
      if (!response.ok) throw new Error('paper')
      setFill(await response.json() as PaperFill); onFilled()
    } catch { setMessage('최신 시세 또는 모의 주문을 확인하지 못했습니다. 연결을 확인하고 다시 시도해 주세요.') } finally { setBusy(false) }
  }
  const orderPanel =     <section className="rounded-xl border border-slate-200 bg-white p-3 shadow-sm sm:p-4" aria-labelledby="paper-trading-heading">
      <h2 className="text-xl font-bold text-slate-950" id="paper-trading-heading">모의거래 주문</h2>
      <p className="mt-1 text-sm text-slate-600">실거래 계정과 분리된 가상 지갑을 사용합니다. 주문은 선택한 거래소의 최신 공개 시세로 가상 체결되며 실제 주문은 전송하지 않습니다.</p>
      <div className="mt-3 flex gap-1 overflow-x-auto rounded-lg bg-slate-100 p-1" role="tablist" aria-label="모의거래 주문 메뉴">
        {([['BUY', '매수'], ['SELL', '매도'], ['QUICK', '간편주문'], ['ORDERBOOK', '호가주문'], ['HISTORY', '거래내역']] as const).map(([key, label]) => <button key={key} type="button" role="tab" aria-selected={tab === key} className={`whitespace-nowrap rounded-md px-3 py-2 text-xs font-semibold transition-colors sm:text-sm ${tab === key ? 'bg-white text-slate-950 shadow-sm ring-1 ring-slate-200' : 'text-slate-600 hover:bg-white/70'}`} onClick={() => { setTab(key); if (key === 'BUY' || key === 'SELL') setSide(key) }}>{label}</button>)}
      </div>
      {tab === 'HISTORY' ? <div role="tabpanel" className="mt-3">{!token ? <p className="text-sm text-slate-600">로그인 후 내 모의거래 내역을 확인할 수 있습니다.</p> : history.isPending ? <p role="status">거래내역을 불러오는 중입니다.</p> : history.isError ? <p role="alert">거래내역을 불러오지 못했습니다.</p> : history.data?.orders.length ? <ol className="divide-y divide-slate-100">{history.data.orders.slice(0, 10).map((order, index) => <li className="grid grid-cols-[1fr_auto] gap-1 py-3 text-sm" key={`${order.createdAt}-${index}`}><strong>{order.exchange === 'UPBIT' ? '업비트' : '빗썸'} {order.side === 'BUY' ? '매수' : '매도'} {order.symbol} · {order.orderType === 'LIMIT' ? '호가' : '시장'}</strong><span>{won(order.amount)}</span><span className="text-xs text-slate-500">{qty(order.quantity)} {order.symbol} · {new Date(order.createdAt).toLocaleString('ko-KR')}</span><span className="text-right text-xs text-slate-500">{order.status}{order.limitPrice ? ` · 지정 ${won(order.limitPrice)}` : ''}</span></li>)}</ol> : <p className="text-sm text-slate-600">아직 모의거래 체결 내역이 없습니다.</p>}</div>
      : !token ? <p className="mt-4 rounded-lg bg-slate-50 p-3 text-sm text-slate-600">로그인 후 거래소별 모의 지갑으로 주문을 연습할 수 있습니다.</p> : <form role="tabpanel" className="mt-3 grid gap-3 sm:max-w-xl" onSubmit={submit}>
        <p className="rounded-lg bg-blue-50 p-3 text-sm text-blue-900">{exchange === 'UPBIT' ? '업비트' : '빗썸'} · KRW-{symbol} · {quote ? won(quote.price) : '실시간 시세 대기'}</p>
        {tab === 'QUICK' && <div><p className="mb-2 text-sm font-medium">간편 주문 금액</p><div className="flex flex-wrap gap-2">{[10000, 50000, 100000].map((value) => <button key={value} type="button" aria-pressed={Number(amount) === value} className="rounded-md border border-slate-300 px-3 py-2 text-sm" onClick={() => setAmount(String(value))}>{value.toLocaleString('ko-KR')}원</button>)}</div><div className="mt-3 flex gap-2"><button type="button" className={`rounded-md px-4 py-2 text-sm ${side === 'BUY' ? 'bg-rose-700 text-white' : 'bg-slate-100'}`} onClick={() => setSide('BUY')}>매수</button><button type="button" className={`rounded-md px-4 py-2 text-sm ${side === 'SELL' ? 'bg-blue-700 text-white' : 'bg-slate-100'}`} onClick={() => setSide('SELL')}>매도</button></div></div>}
        {tab !== 'QUICK' && <p className="text-sm font-semibold">{side === 'BUY' ? '매수 주문' : '매도 주문'}</p>}
        {tab === 'ORDERBOOK' && <label className="grid gap-1 text-sm font-medium">호가 지정 가격(KRW)<input className="rounded-lg border border-slate-300 bg-slate-50 px-3 py-2" type="number" min="1" step="1" required value={limitPrice} onChange={(e) => setLimitPrice(e.target.value)} /></label>}
        {side === 'BUY' ? <label className="grid gap-1 text-sm font-medium">주문 금액(KRW)<input className="rounded-lg border border-slate-300 bg-slate-50 px-3 py-2" type="number" min="5000" required value={amount} onChange={(e) => setAmount(e.target.value)} /></label> : <label className="grid gap-1 text-sm font-medium">매도 수량({symbol})<input className="rounded-lg border border-slate-300 bg-slate-50 px-3 py-2" type="number" min="0.000000000000000001" step="0.000000000000000001" required value={sellQuantity} onChange={(e) => setSellQuantity(e.target.value)} /></label>}
        {quote && <p className="text-sm text-slate-600" aria-live="polite">{side === 'BUY' ? '예상 매수량' : '예상 매도 금액'}: {side === 'BUY' ? qty(Number(amount) / quote.price) + ' ' + symbol : won((tab === 'QUICK' ? Number(amount) : Number(sellQuantity || 0) * quote.price))}</p>}
        <p className="text-xs text-slate-500">{tab === 'ORDERBOOK' ? '호가주문은 선택 가격이 현재 시세에 닿았을 때 즉시 가상 체결됩니다. 미체결 주문은 보관하지 않습니다.' : '주문은 제출 시점의 최신 공개 시세로 가상 체결됩니다.'}</p><button className={`w-full rounded-lg px-4 py-2.5 font-bold text-white disabled:opacity-50 sm:w-fit ${side === 'BUY' ? 'bg-rose-700' : 'bg-blue-700'}`} disabled={busy || !quote} type="submit">{tab === 'ORDERBOOK' ? '호가 주문 실행' : tab === 'QUICK' ? '간편 주문 실행' : `${side === 'BUY' ? '매수' : '매도'} 주문 실행`}</button>
      </form>}
      {fill && <p className="mt-4 rounded-lg bg-emerald-50 p-3 text-sm text-emerald-800" role="status">모의거래 체결: {fill.exchange === 'UPBIT' ? '업비트' : '빗썸'} {fill.side === 'BUY' ? '매수' : '매도'} {fill.symbol} · {qty(fill.quantity)} {fill.symbol} · 체결가 {won(fill.price)} · {won(fill.amount)} · 수수료 {won(fill.fee)}</p>}
      {limitError && <p className="mt-4 text-sm text-amber-800" role="status">{limitError}</p>}{message && <p className="mt-4 text-sm text-rose-800" role="status">{message}</p>}
    </section>
  return <MarketTradingPanel stream={stream} exchange={exchange} symbol={symbol} onExchangeChange={onExchangeChange} onSymbolChange={onSymbolChange} onPriceSelect={(price, selectedSide) => { setLimitPrice(String(price)); setSide(selectedSide); setTab('ORDERBOOK') }} orderPanel={orderPanel} token={token} />
}