import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import type { FormEvent } from 'react'

type Portfolio = {
  exchange: string
  totalEvaluatedAmount: number
  cashWeight: number
  capturedAt: string
  positions: { currency: string; quantity: number; averageBuyPrice: number; currentPrice: number; evaluatedAmount: number; weight: number; targetWeight: number | null; rebalancingGap: number | null }[]
}
type Props = { token: string | null }
const krw = (value: number) => `${Math.round(value).toLocaleString('ko-KR')} KRW`

/** 명시적으로 새로고침할 때만 본인 거래소의 읽기 전용 잔고를 요청한다. */
export default function PortfolioPanel({ token }: Props) {
  const [targetMessage, setTargetMessage] = useState('')
  const query = useQuery({
    queryKey: ['portfolio', 'UPBIT', token],
    enabled: false,
    retry: false,
    queryFn: async () => {
      const response = await fetch('/api/portfolio/UPBIT', { headers: { Authorization: `Bearer ${token}` } })
      if (!response.ok) throw new Error('portfolio')
      return response.json() as Promise<Portfolio>
    },
  })
  const portfolio = query.data

  async function saveTargets(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!token || !portfolio) return
    setTargetMessage('')
    const form = new FormData(event.currentTarget)
    try {
      const response = await fetch('/api/portfolio/UPBIT/targets', { method: 'PUT', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, body: JSON.stringify({
        targets: portfolio.positions.map((position) => ({ currency: position.currency, weight: Number(form.get(`target-${position.currency}`)) })),
      }) })
      if (!response.ok) throw new Error('targets')
      await query.refetch(); setTargetMessage('목표 비중을 저장했습니다.')
    } catch { setTargetMessage('목표 비중을 저장하지 못했습니다. 합계가 100% 이하인지 확인해 주세요.') }
  }

  return <section className="mt-8" aria-labelledby="portfolio-heading">
    <div className="flex flex-wrap items-center justify-between gap-3"><div><h2 className="text-xl font-bold text-white" id="portfolio-heading">연동 포트폴리오</h2><p className="mt-1 text-sm text-slate-400">본인 계정의 읽기 전용 잔고와 공개 시세로 계산합니다.</p></div>
      {token && <button className="rounded-lg border border-cyan-400 px-4 py-2 text-sm font-bold text-cyan-200 disabled:opacity-50" disabled={query.isFetching} onClick={() => query.refetch()} type="button">포트폴리오 새로고침</button>}</div>
    {!token && <p className="mt-4 rounded-lg bg-slate-900 p-4 text-sm text-slate-400">로그인하고 거래소 읽기 전용 연동을 저장한 뒤 조회할 수 있습니다.</p>}
    {query.isFetching && <p className="mt-4 text-sm text-slate-400" role="status">포트폴리오를 불러오는 중입니다.</p>}
    {query.isError && <p className="mt-4 rounded-lg bg-amber-500/10 p-4 text-sm text-amber-100" role="status">포트폴리오를 불러오지 못했습니다. 거래소 연동과 자산 조회 권한을 확인해 주세요.</p>}
    {portfolio && <><div className="mt-4 grid gap-4 md:grid-cols-3" aria-label="포트폴리오 요약"><Summary title="총 평가 자산" value={krw(portfolio.totalEvaluatedAmount)} /><Summary title="현금 비중" value={`${(portfolio.cashWeight * 100).toFixed(1)}%`} /><Summary title="조회 거래소" value={portfolio.exchange} /></div>
      <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">{portfolio.positions.map((position) => <article className="rounded-xl border border-slate-800 bg-slate-900 p-4" key={position.currency}><h3 className="font-bold text-white">{position.currency}</h3><p className="mt-2 text-sm text-slate-300">평가액 {krw(position.evaluatedAmount)} · 현재 비중 {(position.weight * 100).toFixed(1)}%</p><p className="mt-1 text-xs text-slate-500">수량 {position.quantity} · 현재가 {krw(position.currentPrice)}{position.rebalancingGap !== null && ` · 조정 차이 ${(position.rebalancingGap * 100).toFixed(1)}%`}</p></article>)}</div>
      <form className="mt-5 rounded-xl border border-slate-800 bg-slate-900 p-4" onSubmit={saveTargets}><h3 className="font-bold text-white">목표 비중</h3><p className="mt-1 text-sm text-slate-400">합계는 100% 이하여야 하며, 저장해도 주문은 실행되지 않습니다.</p><div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">{portfolio.positions.map((position) => <label className="grid gap-1 text-sm" key={position.currency}>{position.currency} 목표 비중 (0~1)<input className="rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-white" defaultValue={position.targetWeight ?? 0} max="1" min="0" name={`target-${position.currency}`} required step="0.01" type="number" /></label>)}</div><button className="mt-4 rounded-lg border border-cyan-400 px-4 py-2 text-sm font-bold text-cyan-200" type="submit">목표 비중 저장</button>{targetMessage && <p className="mt-3 text-sm text-cyan-200" role="status">{targetMessage}</p>}</form>
      <p className="mt-3 text-xs text-slate-500">조회 시각: {new Date(portfolio.capturedAt).toLocaleString('ko-KR')}</p>
    </>}
  </section>
}

function Summary({ title, value }: { title: string; value: string }) {
  return <article className="rounded-xl border border-slate-800 bg-slate-900 p-5"><p className="text-sm text-slate-400">{title}</p><strong className="mt-3 block text-2xl text-white">{value}</strong></article>
}
