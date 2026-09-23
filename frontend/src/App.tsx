import { useEffect, useState } from 'react'
import type { AnchorHTMLAttributes, MouseEvent, ReactNode } from 'react'
import AccountAccess from './AccountAccess'
import MarketTicker from './MarketTicker'
import PaperTradingPanel from './PaperTradingPanel'
import PaperAccountSummary from './PaperAccountSummary'
import RecommendationPanel from './RecommendationPanel'
import ExchangeAccountPanel from './ExchangeAccountPanel'
import PortfolioPanel from './PortfolioPanel'
import PrivacyPolicyPage from './PrivacyPolicyPage'
import DashboardSummary from './DashboardSummary'

type Page = { path: string; label: string; title: string }
const pages: Page[] = [
  { path: '/', label: '대시보드', title: '대시보드' },
  { path: '/portfolio', label: '포트폴리오', title: '포트폴리오' },
  { path: '/market', label: '시장', title: '시장 데이터' },
  { path: '/recommendations', label: '추천', title: '투자 추천' },
  { path: '/history', label: '거래 이력', title: '거래 이력' },
]

function InternalLink({ to, onNavigate, children, ...attributes }: { to: string; onNavigate: (path: string) => void; children: ReactNode } & Omit<AnchorHTMLAttributes<HTMLAnchorElement>, 'href' | 'onClick'>) {
  function follow(event: MouseEvent<HTMLAnchorElement>) {
    if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return
    event.preventDefault(); onNavigate(to)
  }
  return <a {...attributes} href={to} onClick={follow}>{children}</a>
}

/** 각 메뉴는 독립 경로로 표시하고 인증 토큰은 앱 메모리에만 유지한다. */
function App() {
  const [path, setPath] = useState(() => window.location.pathname)
  const [token, setToken] = useState<string | null>(null)
  const [paperRevision, setPaperRevision] = useState(0)
  const navigate = (nextPath: string) => { window.history.pushState(null, '', nextPath); setPath(nextPath) }
  useEffect(() => {
    const syncPath = () => setPath(window.location.pathname)
    window.addEventListener('popstate', syncPath)
    return () => window.removeEventListener('popstate', syncPath)
  }, [])

  const current = pages.find((page) => page.path === path)
  const title = current?.title ?? (path === '/account' ? '로그인과 회원가입' : path === '/privacy' ? '개인정보 처리방침' : '페이지를 찾을 수 없습니다')
  return <div className="min-h-screen bg-slate-50 text-slate-900">
    <div className="mx-auto flex min-h-screen max-w-6xl flex-col px-4 sm:px-6 lg:px-8">
      <header className="border-b border-slate-200 py-5 sm:py-6">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <InternalLink className="group" to="/" onNavigate={navigate}><span className="text-sm font-extrabold tracking-[0.16em] text-blue-700">CRYPTO INVEST</span><span className="mt-1 block text-2xl font-bold tracking-tight text-slate-950">{title}</span></InternalLink>
          <div className="flex items-center gap-3"><span className="inline-flex items-center gap-2 rounded-full bg-slate-100 px-3 py-1.5 text-xs font-bold text-slate-700" aria-label="현재 거래 모드 상태: PAPER"><span className="size-2 rounded-full bg-emerald-600" aria-hidden="true"></span>현재 모드 <strong>PAPER</strong></span><InternalLink className="primary-button" to="/account" onNavigate={navigate}>{token ? '내 계정' : '로그인 / 회원가입'}</InternalLink></div>
        </div>
        <p className="mt-3 text-sm text-slate-600">계정·자산 정보는 보호하고, 거래는 PAPER 모드에서 안전하게 확인합니다.</p>
      </header>
      <nav className="-mx-1 mt-4 flex gap-1 overflow-x-auto pb-2" aria-label="주요 메뉴">
        {pages.map((page) => <InternalLink className={`shrink-0 rounded-lg px-3 py-2.5 text-sm font-semibold transition-colors ${path === page.path ? 'bg-blue-50 text-blue-800' : 'text-slate-600 hover:bg-slate-100 hover:text-slate-950'}`} key={page.path} to={page.path} onNavigate={navigate} aria-current={path === page.path ? 'page' : undefined}>{page.label}</InternalLink>)}
      </nav>
      <main className="flex-1 py-6 sm:py-8" id="main-content">
        {path === '/' && <section className="space-y-6"><div className="panel-card"><p className="eyebrow">안전한 가상자산 관리</p><h2 className="mt-2 text-2xl font-bold tracking-tight text-slate-950">내 자산과 시장을 차분히 살펴보세요</h2><p className="panel-description">추천은 참고 정보이며 주문과 분리됩니다. 이 대시보드에서는 시스템 상태, 가상 잔액, PAPER 주문을 확인할 수 있습니다.</p></div><DashboardSummary token={token} revision={paperRevision} /><PaperTradingPanel token={token} onFilled={() => setPaperRevision((value) => value + 1)} /><PaperAccountSummary token={token} revision={paperRevision} /></section>}
        {path === '/portfolio' && <PortfolioPanel token={token} />}
        {path === '/market' && <section className="space-y-4"><div><h2 className="page-heading">주요 시세</h2><p className="panel-description">거래소 공개 데이터만 조회합니다. 로그인이나 API key가 필요하지 않습니다.</p></div><MarketTicker /><div className="panel-card"><h3 className="section-title">데이터 출처</h3><p className="mt-2 text-sm text-slate-600">Upbit 공개 시세 API · KRW-BTC · 조회 시각은 시세 카드에 표시됩니다.</p></div></section>}
        {path === '/recommendations' && <section className="space-y-4"><div><h2 className="page-heading">시장 데이터에 따른 참고 정보</h2><p className="panel-description">계산 근거와 데이터 시각을 함께 확인하세요. 추천은 주문 지시나 수익 보장이 아닙니다.</p></div><RecommendationPanel /></section>}
        {path === '/history' && (token ? <PaperAccountSummary token={token} revision={paperRevision} /> : <section className="panel-card"><h2 className="panel-title">로그인이 필요합니다</h2><p className="panel-description">내 PAPER 거래와 체결 이력은 로그인 후 확인할 수 있습니다.</p><InternalLink className="primary-button mt-5" to="/account" onNavigate={navigate}>로그인 / 회원가입</InternalLink></section>)}
        {path === '/account' && <div className="space-y-5"><AccountAccess token={token} onTokenChange={setToken} /><ExchangeAccountPanel token={token} /></div>}
        {path === '/privacy' && <PrivacyPolicyPage />}
        {!current && path !== '/account' && path !== '/privacy' && <section className="panel-card"><h2 className="panel-title">페이지를 찾을 수 없습니다</h2><InternalLink className="primary-button mt-5" to="/" onNavigate={navigate}>대시보드로 이동</InternalLink></section>}
      </main>
      <footer className="mt-auto border-t border-slate-200 py-6 text-sm text-slate-600">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between"><p>Crypto Invest · 기본 거래 모드 PAPER</p><InternalLink className="font-semibold text-blue-700 underline underline-offset-4" to="/privacy" onNavigate={navigate}>개인정보 처리방침</InternalLink></div>
        <p className="mt-2 text-xs leading-5 text-slate-500">개인정보 처리방침은 개발용 초안입니다. 실제 서비스 공개 전 법률 검토와 사업자 정보를 확정해야 합니다.</p>
      </footer>
    </div>
  </div>
}

export default App