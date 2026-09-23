import { useQuery } from '@tanstack/react-query'

type Summary = { wallets: { currency: string; availableAmount: number }[]; orders: { symbol: string; side: string; amount: number; fee: number; status: string }[] }

/** 인증된 사용자의 PAPER 데이터만 표시하며 실제 거래소 잔고는 조회하지 않는다. */
export default function PaperAccountSummary({ token, revision }: { token: string | null; revision: number }) {
  const query = useQuery({ queryKey: ['paper-summary', token, revision], enabled: !!token, retry: false, queryFn: async () => {
    const response = await fetch('/api/paper/orders/summary', { headers: { Authorization: `Bearer ${token}` } })
    if (!response.ok) throw new Error('paper-summary')
    return response.json() as Promise<Summary>
  } })
  if (!token) return null
  return <><section className="mt-6 grid gap-4 md:grid-cols-3" aria-label="PAPER 지갑 요약">{query.data?.wallets.map((wallet) => <article className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm" key={wallet.currency}><p className="text-sm text-slate-600">PAPER {wallet.currency}</p><strong className="mt-3 block text-2xl text-slate-950">{wallet.availableAmount.toLocaleString('ko-KR')}</strong></article>) ?? <p className="text-sm text-slate-600">PAPER 지갑을 불러오는 중입니다.</p>}</section><section className="mt-6 rounded-xl border border-slate-200 bg-white p-5 shadow-sm" aria-labelledby="orders-heading"><h2 className="text-xl font-bold text-slate-950" id="orders-heading">PAPER 주문 내역</h2>{query.data?.orders.length ? <ul className="mt-4 divide-y divide-slate-200">{query.data.orders.map((order, index) => <li className="py-3 text-sm" key={`${order.symbol}-${index}`}>{order.side} {order.symbol} · {Math.round(order.amount).toLocaleString('ko-KR')} KRW · {order.status}</li>)}</ul> : <p className="mt-3 text-sm text-slate-600">아직 PAPER 체결 내역이 없습니다.</p>}</section></>
}
