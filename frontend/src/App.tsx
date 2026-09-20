/**
 * Phase 11 대시보드의 읽기 전용 뼈대다.
 * 인증과 사용자별 API가 준비되기 전에는 예시 수치를 실제 잔고처럼 보이지 않게 명확히 표기한다.
 */
const recommendations = [
  { market: 'KRW-BTC', signal: '대기', score: '—', reason: '계정과 시장 데이터를 연결하면 추천을 계산합니다.' },
  { market: 'KRW-ETH', signal: '대기', score: '—', reason: 'PAPER 모드에서만 주문 계획을 검토할 수 있습니다.' },
]
const navigation = ['대시보드', '포트폴리오', '시장', '추천', '거래 내역']

function App() {
  return (
    <main className="min-h-screen bg-slate-950 text-slate-100">
      <div className="mx-auto max-w-7xl px-5 py-6 sm:px-8 lg:px-10">
        <header className="flex flex-col gap-6 border-b border-slate-800 pb-6 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <p className="text-sm font-semibold tracking-wide text-cyan-300">CRYPTO INVEST</p>
            <h1 className="mt-2 text-3xl font-bold tracking-tight text-white">투자 현황</h1>
            <p className="mt-2 text-sm text-slate-400">연동 전 화면 · 계정과 API Key는 브라우저에 저장하지 않습니다.</p>
          </div>
          <div className="flex items-center gap-3">
            <span className="rounded-full border border-emerald-500/50 bg-emerald-500/10 px-3 py-1.5 text-sm font-bold text-emerald-300" aria-label="거래 모드: PAPER">PAPER 모드</span>
            <button className="rounded-lg bg-cyan-400 px-4 py-2.5 text-sm font-bold text-slate-950 disabled:cursor-not-allowed disabled:opacity-50" disabled>거래소 계정 연결 (인증 준비 중)</button>
          </div>
        </header>

        <nav className="mt-5 flex gap-2 overflow-x-auto pb-1" aria-label="주요 메뉴">
          {navigation.map((item, index) => <button className={`whitespace-nowrap rounded-lg px-3 py-2 text-sm ${index === 0 ? 'bg-slate-800 font-bold text-white' : 'text-slate-400'}`} key={item} type="button">{item}</button>)}
        </nav>

        <section className="mt-8 grid gap-4 md:grid-cols-3" aria-label="포트폴리오 요약">
          <SummaryCard title="총 평가 자산" value="연동 전" description="잔고 조회 권한이 있는 계정을 연결하세요." />
          <SummaryCard title="현금 비중" value="—" description="사용 가능한 KRW와 보유 자산을 분리해 표시합니다." />
          <SummaryCard title="리스크 상태" value="PAPER 안전" description="실거래는 별도의 최종 승인 전까지 활성화되지 않습니다." positive />
        </section>

        <section className="mt-8 grid gap-6 lg:grid-cols-[1.35fr_0.65fr]">
          <article className="rounded-xl border border-slate-800 bg-slate-900 p-5" aria-labelledby="recommendations-heading">
            <div className="flex items-center justify-between gap-4"><div><h2 className="text-xl font-bold text-white" id="recommendations-heading">추천과 주문 계획</h2><p className="mt-1 text-sm text-slate-400">추천은 주문이 아닙니다. 항상 RiskEngine 검증 후 PAPER로 실행됩니다.</p></div><span className="rounded bg-slate-800 px-2 py-1 text-xs font-semibold text-slate-300">규칙 기반</span></div>
            <div className="mt-5 divide-y divide-slate-800">
              {recommendations.map((recommendation) => <div className="grid gap-2 py-4 sm:grid-cols-[1fr_auto]" key={recommendation.market}><div><h3 className="font-bold text-slate-100">{recommendation.market}</h3><p className="mt-1 text-sm leading-6 text-slate-400">{recommendation.reason}</p></div><div className="flex items-center gap-2 sm:justify-end"><span className="rounded bg-slate-800 px-2 py-1 text-sm text-slate-300">점수 {recommendation.score}</span><span className="rounded bg-amber-400/10 px-2 py-1 text-sm font-semibold text-amber-300">{recommendation.signal}</span></div></div>)}
            </div>
          </article>
          <aside className="rounded-xl border border-slate-800 bg-slate-900 p-5" aria-labelledby="market-heading"><h2 className="text-xl font-bold text-white" id="market-heading">시장 데이터</h2><p className="mt-1 text-sm text-slate-400">Upbit·Bithumb 공개 시세를 조회할 수 있습니다.</p><div className="mt-6 rounded-lg border border-dashed border-slate-700 p-4"><p className="font-semibold text-slate-200">아직 선택된 마켓이 없습니다.</p><p className="mt-2 text-sm leading-6 text-slate-400">마켓을 선택하면 현재가, 일봉, 지표와 변동성 경고를 이 영역에 표시합니다.</p></div></aside>
        </section>

        <section className="mt-6 rounded-xl border border-slate-800 bg-slate-900 p-5" aria-labelledby="orders-heading"><div className="flex flex-col justify-between gap-2 sm:flex-row sm:items-center"><div><h2 className="text-xl font-bold text-white" id="orders-heading">PAPER 주문 내역</h2><p className="mt-1 text-sm text-slate-400">실제 거래소 주문은 이 화면에서 실행되지 않습니다.</p></div><span className="text-sm font-semibold text-slate-400">0건</span></div><p className="mt-8 border-t border-slate-800 pt-5 text-center text-sm text-slate-500">인증 후 사용자별 모의 체결과 감사 이력이 표시됩니다.</p></section>
      </div>
    </main>
  )
}

function SummaryCard({ title, value, description, positive = false }: { title: string; value: string; description: string; positive?: boolean }) {
  return <article className="rounded-xl border border-slate-800 bg-slate-900 p-5"><p className="text-sm text-slate-400">{title}</p><strong className={`mt-3 block text-3xl ${positive ? 'text-emerald-300' : 'text-white'}`}>{value}</strong><p className="mt-3 text-sm text-slate-500">{description}</p></article>
}

export default App
