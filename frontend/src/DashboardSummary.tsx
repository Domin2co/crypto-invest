import { useQuery } from '@tanstack/react-query'

type Props = { token: string | null; revision: number }
type Health = { status: string; tradingMode: string }
type PaperSummary = { wallets: { currency: string; availableAmount: number }[]; orders: { status: string }[] }

export default function DashboardSummary({ token, revision }: Props) {
  const health = useQuery({ queryKey: ['health'], retry: false, queryFn: async () => {
    const response = await fetch('/api/health')
    if (!response.ok) throw new Error('health')
    return response.json() as Promise<Health>
  } })
  const paper = useQuery({ queryKey: ['paper-dashboard', token, revision], enabled: !!token, retry: false, queryFn: async () => {
    const response = await fetch('/api/paper/orders/summary', { headers: { Authorization: `Bearer ${token}` } })
    if (!response.ok) throw new Error('paper-dashboard')
    return response.json() as Promise<PaperSummary>
  } })
  const krw = paper.data?.wallets.find((wallet) => wallet.currency === 'KRW')?.availableAmount
  return <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3" aria-label="오늘의 요약">
    <article className="panel-card"><p className="text-sm font-medium text-slate-600">시스템 상태</p><strong className="mt-3 block text-xl text-slate-950">{health.isError ? '연결 확인 필요' : health.data?.status === 'UP' ? '정상' : '확인 중'}</strong><p className="mt-2 text-sm text-slate-500">시장 데이터와 계정 기능 상태</p></article>
    <article className="panel-card"><p className="text-sm font-medium text-slate-600">현재 거래 모드</p><strong className="mt-3 block text-xl text-emerald-800">{health.data?.tradingMode ?? '확인 중'}</strong><p className="mt-2 text-sm text-slate-500">실제 주문 여부는 서버 설정이 결정합니다.</p></article>
    <article className="panel-card"><p className="text-sm font-medium text-slate-600">내 PAPER 지갑</p><strong className="mt-3 block text-xl text-slate-950">{!token ? '로그인 필요' : krw === undefined ? paper.isError ? '불러오지 못함' : '불러오는 중' : `${Math.round(krw).toLocaleString('ko-KR')} KRW`}</strong><p className="mt-2 text-sm text-slate-500">{token ? `누적 모의 주문 ${paper.data?.orders.length ?? 0}건` : '로그인하면 가상 잔액을 확인할 수 있습니다.'}</p></article>
  </section>
}