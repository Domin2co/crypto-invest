import { useState } from 'react'
import AccountAccess from './AccountAccess'
import MarketTicker from './MarketTicker'
import PaperTradingPanel from './PaperTradingPanel'
import PaperAccountSummary from './PaperAccountSummary'
import RecommendationPanel from './RecommendationPanel'
import ExchangeAccountPanel from './ExchangeAccountPanel'
import PortfolioPanel from './PortfolioPanel'

/** 실제 잔고처럼 보이는 수치를 인증 전에는 표시하지 않는 PAPER 대시보드다. */
function App() {
  const [token, setToken] = useState<string | null>(null)
  const [paperRevision, setPaperRevision] = useState(0)
  return <main className="min-h-screen bg-slate-950 text-slate-100"><div className="mx-auto max-w-7xl px-5 py-6 sm:px-8 lg:px-10">
    <header className="flex flex-col gap-6 border-b border-slate-800 pb-6 lg:flex-row lg:items-center lg:justify-between">
      <div><p className="text-sm font-semibold tracking-wide text-cyan-300">CRYPTO INVEST</p><h1 className="mt-2 text-3xl font-bold text-white">투자 현황</h1><p className="mt-2 text-sm text-slate-400">계정과 API Key는 브라우저에 저장하지 않습니다.</p></div>
      <div className="flex flex-col items-start gap-3 sm:flex-row sm:items-center"><span className="rounded-full border border-emerald-500/50 bg-emerald-500/10 px-3 py-1.5 text-sm font-bold text-emerald-300" aria-label="거래 모드: PAPER">PAPER 모드</span><a className="rounded-lg bg-cyan-400 px-4 py-2.5 text-sm font-bold text-slate-950" href="#account">회원가입 / 내 정보</a></div>
    </header>
    <nav className="mt-5 flex gap-2 overflow-x-auto pb-1" aria-label="주요 메뉴">{['대시보드', '포트폴리오', '시장', '추천', '거래 이력'].map((item, index) => <button className={`shrink-0 rounded-lg px-3 py-2 text-sm ${index === 0 ? 'bg-slate-800 font-bold' : 'text-slate-400'}`} key={item} type="button">{item}</button>)}</nav>
    <PortfolioPanel token={token} />
    <section className="mt-8 grid gap-6 lg:grid-cols-[1.35fr_0.65fr]">
      <RecommendationPanel />
      <aside className="rounded-xl border border-slate-800 bg-slate-900 p-5"><h2 className="text-xl font-bold text-white">시장 데이터</h2><p className="mt-1 text-sm text-slate-400">Upbit·Bithumb 공개 시세를 조회할 수 있습니다.</p><p className="mt-6 rounded-lg border border-dashed border-slate-700 p-4 text-sm text-slate-400">선택한 마켓의 캔들·지표·변동성 경고가 표시됩니다.</p></aside>
    </section>
    <MarketTicker />
    <AccountAccess onTokenChange={setToken} />
    <ExchangeAccountPanel token={token} />
    <PaperTradingPanel token={token} onFilled={() => setPaperRevision((value) => value + 1)} />
    <PaperAccountSummary token={token} revision={paperRevision} />
  </div></main>
}

export default App
