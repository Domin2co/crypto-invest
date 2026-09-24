import { useEffect, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import type { AnchorHTMLAttributes, MouseEvent, ReactNode } from 'react'
import AccountAccess from './AccountAccess'
import AccountSecuritySettings from './AccountSecuritySettings'
import MarketTicker from './MarketTicker'
import PaperTradingPanel from './PaperTradingPanel'
import type { CoinSymbol, Exchange } from './RecommendationPanel'
import PaperAccountSummary from './PaperAccountSummary'
import RecommendationPanel from './RecommendationPanel'
import ExchangeAccountPanel from './ExchangeAccountPanel'
import PortfolioPanel from './PortfolioPanel'
import PrivacyPolicyPage from './PrivacyPolicyPage'
import NicknameEditor from './NicknameEditor'
import LiveTradingPanel from './LiveTradingPanel'
import PaperLeaguePanel from './PaperLeaguePanel'
import DiscussionModeration from './DiscussionModeration'

type Page = { path: string; label: string; title: string }
const pages: Page[] = [
  { path: '/', label: '대시보드', title: '대시보드' },
  { path: '/trading', label: '실거래', title: '실거래' },
  { path: '/paper-trading', label: '모의거래', title: '모의거래' },
  { path: '/portfolio', label: '포트폴리오', title: '포트폴리오' },
  { path: '/market', label: '시장', title: '시장' },
  { path: '/recommendations', label: '추천', title: '추천' },
  { path: '/history', label: '거래 이력', title: '거래 이력' },
]

function InternalLink({ to, onNavigate, children, ...attributes }: { to: string; onNavigate: (path: string) => void; children: ReactNode } & Omit<AnchorHTMLAttributes<HTMLAnchorElement>, 'href' | 'onClick'>) {
  function follow(event: MouseEvent<HTMLAnchorElement>) {
    if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return
    event.preventDefault(); onNavigate(to)
  }
  return <a {...attributes} href={to} onClick={follow}>{children}</a>
}

/** 인증 토큰은 현재 브라우저 탭의 sessionStorage에 보관해 새로고침 후 복원한다. */
function App() {
  const health = useQuery({ queryKey: ['health'], retry: false, queryFn: async () => {
    const response = await fetch('/api/health')
    if (!response.ok) throw new Error('health')
    return response.json() as Promise<{ status: string; liveOrderSubmissionEnabled: boolean }>
  } })
  const [path, setPath] = useState(() => window.location.pathname)
  const [token, setToken] = useState<string | null>(() => window.sessionStorage.getItem('crypto-invest-token'))
  const [paperRevision, setPaperRevision] = useState(0)
  const [paperExchange, setPaperExchange] = useState<Exchange>('UPBIT')
  const [paperSymbol, setPaperSymbol] = useState<CoinSymbol>('BTC')
  const [profile, setProfile] = useState<{ nickname: string | null; nicknameRequired: boolean; role: string } | null>(null)
  const [profileLoading, setProfileLoading] = useState(false)
  const [profileError, setProfileError] = useState(false)
  const pendingLoginRedirect = useRef(false)
  const nicknameGateActive = !!token && (profileLoading || profileError || !profile || profile.nicknameRequired)
  const updateToken = (next: string | null) => {
    if (next) window.sessionStorage.setItem('crypto-invest-token', next)
    else window.sessionStorage.removeItem('crypto-invest-token')
    pendingLoginRedirect.current = !!next
    setToken(next); setProfile(null); setProfileError(false); setProfileLoading(!!next)
  }
  const navigate = (nextPath: string) => { window.history.pushState(null, '', nextPath); setPath(nextPath) }
  useEffect(() => {
    const syncPath = () => setPath(window.location.pathname)
    window.addEventListener('popstate', syncPath)
    return () => window.removeEventListener('popstate', syncPath)
  }, [])

  useEffect(() => {
    if (!token) return
    let active = true
    fetch('/api/account/profile', { headers: { Authorization: `Bearer ${token}` } })
      .then((response) => response.ok ? response.json() as Promise<{ nickname: string | null; nicknameRequired: boolean; role: string }> : Promise.reject(new Error('profile')))
      .then((value) => { if (active) { setProfile(value); if (value.nickname && pendingLoginRedirect.current) { pendingLoginRedirect.current = false; navigate('/') } } })
      .catch(() => { if (active) setProfileError(true) })
      .finally(() => { if (active) setProfileLoading(false) })
    return () => { active = false }
  }, [token])
  const current = pages.find((page) => page.path === path)
  const title = path === '/' ? 'Crypto Invest' : current?.title ?? (path === '/account' ? '로그인과 회원가입' : path === '/account/edit' ? '내 정보 수정' : path === '/admin/discussions' ? '토론방 신고 관리' : path === '/privacy' ? '개인정보 처리방침' : '페이지를 찾을 수 없습니다')
  useEffect(() => { document.title = path === '/' ? 'Crypto Invest' : title + ' | Crypto Invest' }, [path, title])
  return <div className="min-h-screen bg-slate-50 text-slate-900">
    <div className="mx-auto flex min-h-screen max-w-6xl flex-col px-4 sm:px-6 lg:px-8">
      <header className="border-b border-slate-200 py-5 sm:py-6">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <InternalLink className="group inline-flex items-center gap-3" to="/" onNavigate={navigate}><img src="/coin-mascot.svg" alt="Crypto Invest 로고" className="size-10 shrink-0" /><span className="text-2xl font-bold tracking-tight text-slate-950">{path === '/' ? 'CRYPTO INVEST' : title}</span></InternalLink>
          <div className="flex items-center gap-3"><InternalLink className="primary-button" to="/account" onNavigate={navigate}>{token ? '마이 페이지' : '로그인 / 회원가입'}</InternalLink>{token && <button className="secondary-button" type="button" onClick={() => { updateToken(null); navigate('/account') }}>로그아웃</button>}</div>
        </div>
        <p className="mt-3 text-sm text-slate-600">시장과 투자 판단 정보를 한곳에서 확인합니다.</p>
      </header>
      {!nicknameGateActive && <nav className="-mx-1 mt-4 flex gap-1 overflow-x-auto pb-2" aria-label="주요 메뉴">
        {pages.map((page) => <InternalLink className={`shrink-0 rounded-lg px-3 py-2.5 text-sm font-semibold transition-colors ${path === page.path ? 'bg-blue-50 text-blue-800' : 'text-slate-600 hover:bg-slate-100 hover:text-slate-950'}`} key={page.path} to={page.path} onNavigate={navigate} aria-current={path === page.path ? 'page' : undefined}>{page.label}</InternalLink>)}
        {profile?.role === 'ADMIN' && <InternalLink className="shrink-0 rounded-lg px-3 py-2.5 text-sm font-semibold text-slate-600 hover:bg-slate-100" to="/admin/discussions" onNavigate={navigate}>신고 관리</InternalLink>}
      </nav>}
      <main className="flex-1 py-6 sm:py-8" id="main-content">
        {nicknameGateActive ? profileError ? <section className="panel-card mx-auto max-w-lg" role="alert"><h2 className="panel-title">계정 확인이 필요합니다</h2><p className="panel-description">닉네임 상태를 확인하지 못해 다른 기능을 열지 않았습니다. 연결을 확인하거나 다시 로그인해 주세요.</p></section> : profile?.nicknameRequired ? <NicknameEditor token={token!} current={null} required onSaved={(nickname) => setProfile((current) => ({ nickname, nicknameRequired: false, role: current?.role ?? 'USER' }))} /> : <p className="panel-card" role="status">계정 설정을 확인하고 있습니다.</p> : <>
        {path === '/' && <section className="space-y-6"><div className="panel-card"><p className="eyebrow">안전한 가상자산 관리</p><h2 className="mt-2 text-2xl font-bold tracking-tight text-slate-950">내 자산과 시장을 차분히 살펴보세요</h2><p className="panel-description">시세와 투자 판단에 필요한 핵심 정보를 모아 오늘의 흐름을 확인하세요. 각 기능 메뉴에서 상세 정보를 확인할 수 있습니다.</p></div></section>}
        {path === '/trading' && <LiveTradingPanel token={token} liveOrderSubmissionEnabled={health.data?.liveOrderSubmissionEnabled ?? false} onNavigate={navigate} />}
        {path === '/paper-trading' && <section className="space-y-6"><div><h2 className="page-heading">모의거래</h2><p className="panel-description">실거래 계정과 분리된 모의 지갑을 사용합니다. 실시간 공개 시세를 반영하며 실제 주문은 보내지 않습니다.</p></div><PaperTradingPanel token={token} exchange={paperExchange} symbol={paperSymbol} onExchangeChange={setPaperExchange} onSymbolChange={setPaperSymbol} onFilled={() => setPaperRevision((value) => value + 1)} /><RecommendationPanel exchange={paperExchange} symbol={paperSymbol} /><PaperAccountSummary token={token} revision={paperRevision} /><PaperLeaguePanel token={token} onNavigate={navigate} /></section>}
        {path === '/portfolio' && <PortfolioPanel token={token} />}
        {path === '/market' && <section className="space-y-4"><div><h2 className="page-heading">주요 시세</h2><p className="panel-description">업비트와 빗썸의 전체 KRW 마켓을 검색하고 공개 실시간 시세·차트·호가를 확인합니다. 로그인이나 API key가 필요하지 않습니다.</p></div><MarketTicker token={token} /><div className="panel-card"><h3 className="section-title">데이터 출처</h3><p className="mt-2 text-sm text-slate-600">선택한 거래소의 공개 API와 WebSocket을 사용하며 개인 계정 정보는 요청하지 않습니다.</p></div></section>}
        {path === '/recommendations' && <section className="space-y-4"><div><h2 className="page-heading">시장 데이터에 따른 참고 정보</h2><p className="panel-description">계산 근거와 데이터 시각을 함께 확인하세요. 추천은 주문 지시나 수익 보장이 아닙니다.</p></div><RecommendationPanel /></section>}
        {path === '/history' && (token ? <PaperAccountSummary token={token} revision={paperRevision} /> : <section className="panel-card"><h2 className="panel-title">로그인이 필요합니다</h2><p className="panel-description">내 모의거래 체결 이력은 로그인 후 확인할 수 있습니다.</p><InternalLink className="primary-button mt-5" to="/account" onNavigate={navigate}>로그인 / 회원가입</InternalLink></section>)}
        {path === '/account' && <div className="space-y-5"><AccountAccess token={token} onTokenChange={updateToken} nickname={profile?.nickname ?? null} onNavigate={navigate} /><ExchangeAccountPanel token={token} /></div>}
        {path === '/account/edit' && (token ? <section className="grid gap-4" aria-labelledby="account-edit-heading"><div className="flex flex-wrap items-center justify-between gap-3"><div><p className="eyebrow">마이 페이지</p><h2 className="page-heading" id="account-edit-heading">내 정보 수정</h2></div><button className="secondary-button" type="button" onClick={() => navigate('/account')}>마이 페이지로 돌아가기</button></div><NicknameEditor token={token} current={profile?.nickname ?? null} required={false} onSaved={(nickname) => setProfile((current) => ({ nickname, nicknameRequired: false, role: current?.role ?? 'USER' }))} /><AccountSecuritySettings token={token} /></section> : <section className="panel-card"><h2 className="panel-title">로그인이 필요합니다</h2><InternalLink className="primary-button mt-4" to="/account" onNavigate={navigate}>로그인 / 회원가입</InternalLink></section>)}
        {path === '/admin/discussions' && profile?.role === 'ADMIN' && <DiscussionModeration token={token!} />}
        {path === '/privacy' && <PrivacyPolicyPage />}
        {!current && path !== '/account' && path !== '/account/edit' && path !== '/admin/discussions' && path !== '/privacy' && <section className="panel-card"><h2 className="panel-title">페이지를 찾을 수 없습니다</h2><InternalLink className="primary-button mt-5" to="/" onNavigate={navigate}>대시보드로 이동</InternalLink></section>}
        </>}
      </main>
      {!nicknameGateActive && <footer className="mt-auto border-t border-slate-200 py-6 text-sm text-slate-600">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between"><p>Crypto Invest · 디지털 자산 관리</p><InternalLink className="font-semibold text-blue-700 underline underline-offset-4" to="/privacy" onNavigate={navigate}>개인정보 처리방침</InternalLink></div>
        <p className="mt-2 text-xs leading-5 text-slate-500">개인정보 처리방침은 개발용 초안입니다. 실제 서비스 공개 전 법률 검토와 사업자 정보를 확정해야 합니다.</p>
      </footer>}
    </div>
  </div>
}

export default App
