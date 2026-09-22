import { useState } from 'react'
import type { FormEvent } from 'react'

type PaperFill = { symbol: string; side: string; quantity: number; amount: number; fee: number; status: string }
type Props = { token: string | null; onFilled: () => void }

/** 모의 가격으로만 체결하며, 실제 거래소 주문이나 API key를 사용하지 않는다. */
export default function PaperTradingPanel({ token, onFilled }: Props) {
  const [side, setSide] = useState('BUY')
  const [message, setMessage] = useState('')
  const [fill, setFill] = useState<PaperFill | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!token) return
    setBusy(true); setMessage(''); setFill(null)
    const form = new FormData(event.currentTarget)
    try {
      const response = await fetch('/api/paper/orders', { method: 'POST', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, body: JSON.stringify({
        exchange: 'UPBIT', symbol: 'BTC', side, amount: Number(form.get('amount')), price: Number(form.get('price')),
        quantity: side === 'SELL' ? Number(form.get('quantity')) : null, idempotencyKey: crypto.randomUUID(),
      }) })
      if (!response.ok) throw new Error('paper')
      setFill(await response.json() as PaperFill); onFilled()
    } catch { setMessage('PAPER 주문을 처리하지 못했습니다. 주문 금액과 모의 가격을 확인해 주세요.') } finally { setBusy(false) }
  }

  return <section className="mt-6 rounded-xl border border-slate-800 bg-slate-900 p-5" aria-labelledby="paper-trading-heading">
    <h2 className="text-xl font-bold text-white" id="paper-trading-heading">PAPER 모의 거래</h2>
    <p className="mt-1 text-sm text-slate-400">모의 가격으로만 체결되며 실제 거래소 주문·API key를 사용하지 않습니다.</p>
    {!token ? <p className="mt-4 rounded-lg bg-slate-950 p-3 text-sm text-slate-400">회원가입 후 사용자별 PAPER 지갑으로 모의 거래를 실행할 수 있습니다.</p> : <form className="mt-5 grid gap-4 sm:max-w-xl" onSubmit={submit}>
      <label className="grid gap-1 text-sm font-medium">방향<select className="rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-white" value={side} onChange={(event) => setSide(event.target.value)}><option value="BUY">매수</option><option value="SELL">매도</option></select></label>
      <label className="grid gap-1 text-sm font-medium">주문 금액(KRW)<input className="rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-white" defaultValue="10000" min="5000" name="amount" required type="number" /></label>
      <label className="grid gap-1 text-sm font-medium">모의 체결 가격(KRW/BTC)<input className="rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-white" defaultValue="100000000" min="1" name="price" required type="number" /></label>
      {side === 'SELL' && <label className="grid gap-1 text-sm font-medium">매도 수량(BTC)<input className="rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-white" min="0.00000001" name="quantity" required step="0.00000001" type="number" /></label>}
      <button className="w-full rounded-lg bg-emerald-400 px-4 py-2.5 font-bold text-slate-950 disabled:opacity-50 sm:w-fit" disabled={busy} type="submit">PAPER 주문 실행</button>
    </form>}
    {fill && <p className="mt-4 rounded-lg bg-emerald-500/10 p-3 text-sm text-emerald-200" role="status">PAPER 체결 완료: {fill.side} {fill.symbol}, 체결 금액 {Math.round(fill.amount).toLocaleString('ko-KR')} KRW, 수수료 {fill.fee}</p>}
    {message && <p className="mt-4 text-sm text-rose-200" role="status">{message}</p>}
  </section>
}
