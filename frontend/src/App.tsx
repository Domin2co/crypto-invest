import MarketTicker from './MarketTicker'
import AccountAccess from './AccountAccess'

const recommendations = [
  ['KRW-BTC', '계정과 시장 데이터를 연결하면 추천을 계산합니다.'],
  ['KRW-ETH', 'PAPER 모드에서만 주문 계획을 검토할 수 있습니다.'],
]

/** 인증 전에는 실제 잔고처럼 보이는 수치를 표시하지 않는 PAPER 대시보드다. */
function App() {
  return <main className="min-h-screen bg-slate-950 text-slate-100"><div className="mx-auto max-w-7xl px-5 py-6 sm:px-8 lg:px-10">
    <header className="flex flex-col gap-6 border-b border-slate-800 pb-6 lg:flex-row lg:items-center lg:justify-between">
      <div><p className="text-sm font-semibold tracking-wide text-cyan-300">CRYPTO INVEST</p><h1 className="mt-2 text-3xl font-bold text-white">투자 현황</h1><p className="mt-2 text-sm text-slate-400">계정과 API Key는 브라우저에 저장하지 않습니다.</p></div>
      <div className="flex flex-col items-start gap-3 sm:flex-row sm:items-center"><span className="rounded-full border border-emerald-500/50 bg-emerald-500/10 px-3 py-1.5 text-sm font-bold text-emerald-300" aria-label="거래 모드: PAPER">PAPER 모드</span><a className="rounded-lg bg-cyan-400 px-4 py-2.5 text-sm font-bold text-slate-950" href="#account">회원가입 / 내 정보</a></div>
    </header>
    <nav className="mt-5 flex gap-2 overflow-x-auto pb-1" aria-label="주요 메뉴">{['대시보드', '포트폴리오', '시장', '추천', '거래 내역'].map((item, index) => <button className={`shrink-0 rounded-lg px-3 py-2 text-sm ${index === 0 ? 'bg-slate-800 font-bold' : 'text-slate-400'}`} key={item} type="button">{item}</button>)}</nav>
    <section className="mt-8 grid gap-4 md:grid-cols-3" aria-label="포트폴리오 요약"><Summary title="총 평가 자산" value="연동 전" text="잔고 조회 권한이 있는 계정을 연결하세요." /><Summary title="현금 비중" value="—" text="KRW와 보유 자산을 분리해 표시합니다." /><Summary title="리스크 상태" value="PAPER 안전" text="실거래는 최종 승인 전까지 활성화되지 않습니다." safe /></section>
    <section className="mt-8 grid gap-6 lg:grid-cols-[1.35fr_0.65fr]">
      <article className="rounded-xl border border-slate-800 bg-slate-900 p-5" aria-labelledby="recommendations-heading"><h2 className="text-xl font-bold text-white" id="recommendations-heading">추천과 주문 계획</h2><p className="mt-1 text-sm text-slate-400">추천은 주문이 아닙니다. 항상 RiskEngine 검증 후 PAPER로 실행됩니다.</p><div className="mt-5 divide-y divide-slate-800">{recommendations.map(([market, reason]) => <div className="py-4" key={market}><h3 className="font-bold">{market}</h3><p className="mt-1 text-sm text-slate-400">{reason}</p></div>)}</div></article>
      <aside className="rounded-xl border border-slate-800 bg-slate-900 p-5"><h2 className="text-xl font-bold text-white">시장 데이터</h2><p className="mt-1 text-sm text-slate-400">Upbit·Bithumb 공개 시세를 조회할 수 있습니다.</p><p className="mt-6 rounded-lg border border-dashed border-slate-700 p-4 text-sm text-slate-400">선택한 마켓의 캔들·지표·변동성 경고가 표시됩니다.</p></aside>
    </section>
    <MarketTicker />
    <AccountAccess />
    <section className="mt-6 rounded-xl border border-slate-800 bg-slate-900 p-5" aria-labelledby="orders-heading"><h2 className="text-xl font-bold text-white" id="orders-heading">PAPER 주문 내역</h2><p className="mt-1 text-sm text-slate-400">실제 거래소 주문은 이 화면에서 실행되지 않습니다.</p><p className="mt-8 border-t border-slate-800 pt-5 text-center text-sm text-slate-500">인증 후 사용자별 모의 체결과 감사 이력이 표시됩니다.</p></section>
  </div></main>
}

function Summary({ title, value, text, safe = false }: { title: string; value: string; text: string; safe?: boolean }) {
  return <article className="rounded-xl border border-slate-800 bg-slate-900 p-5"><p className="text-sm text-slate-400">{title}</p><strong className={`mt-3 block text-3xl ${safe ? 'text-emerald-300' : 'text-white'}`}>{value}</strong><p className="mt-3 text-sm text-slate-500">{text}</p></article>
}

export default App
