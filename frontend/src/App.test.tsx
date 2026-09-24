import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import App from './App'

function mount() {
  return render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><App /></QueryClientProvider>)
}

describe('App navigation and account flow', () => {
  afterEach(() => { cleanup(); vi.unstubAllGlobals(); window.sessionStorage.clear(); window.history.replaceState(null, '', '/') })

  it('routes each menu to a distinct page and keeps privacy policy in the global footer', async () => {
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input)
      const data = url.includes('/api/health') ? { status: 'UP', liveOrderSubmissionEnabled: false }
        : url.includes('/api/markets/') && url.endsWith('/markets') ? [{ market: 'KRW-BTC', koreanName: '비트코인', englishName: 'Bitcoin' }, { market: 'KRW-ETH', koreanName: '이더리움', englishName: 'Ethereum' }]
          : url.includes('/api/markets/') && url.includes('/candles?') ? []
        : url === '/api/paper-league' ? { month: '2026-09', status: 'WAITING', marketDataAt: null, standings: [], latestCompletedMonth: null, latestAwards: [] }
          : url.includes('/api/paper-league/entry') ? { publicConsent: false, nextMonth: '2026-10', enrolled: false } : {}
      return { ok: !url.startsWith('/api/recommendations'), json: async () => data }
    }))
    mount()
    expect(await screen.findByRole('heading', { name: '내 자산과 시장을 차분히 살펴보세요' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '개인정보 처리방침' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '포트폴리오 보기' })).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('link', { name: '모의거래' }))
    expect(await screen.findByRole('heading', { name: '2026년 9월' })).toBeInTheDocument()
    expect(screen.getByText('대회 시작 또는 첫 체결을 기다리는 중입니다.')).toBeInTheDocument()
    expect(await screen.findByRole('option', { name: /ETH.*이더리움/ })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('link', { name: '포트폴리오' }))
    expect(await screen.findByRole('heading', { name: '연동 포트폴리오' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '개인정보 처리방침' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('link', { name: '시장' }))
    expect(await screen.findByRole('heading', { name: '주요 시세' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('link', { name: '추천' }))
    expect(await screen.findByRole('heading', { name: '시장 데이터에 따른 참고 정보' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('link', { name: '거래 이력' }))
    expect(await screen.findByRole('heading', { name: '로그인이 필요합니다' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('link', { name: '개인정보 처리방침' }))
    expect(await screen.findByRole('heading', { name: '개인정보 처리방침' })).toBeInTheDocument()
  })
  it('registers and logs in and restores the tab session after reload', async () => {
    let nickname: string | null = null
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url.includes('/api/auth/email-verification')) return url.endsWith('/confirm') ? { ok: true, json: async () => ({ verificationToken: 'verified-token' }) } : { ok: true, json: async () => ({ challengeId: 'challenge-1' }) }
      if (url.includes('/api/auth/register') || url.includes('/api/auth/login')) return { ok: true, json: async () => ({ accessToken: 'memory-only-token' }) }
      if (url.includes('/api/account/email-status')) return { ok: true, json: async () => ({ email: 'user@example.com', verified: true, changeAvailableAt: new Date(Date.now() + 86400000).toISOString() }) }
      if (url.includes('/api/account/profile')) return { ok: true, json: async () => ({ nickname, nicknameRequired: !nickname }) }
      if (url.includes('/api/account/nickname/availability')) return { ok: true, json: async () => ({ valid: true, available: true }) }
      if (url === '/api/account/nickname') { nickname = JSON.parse(String(init?.body)).nickname; return { ok: true, status: 204, json: async () => ({}) } }
      if (url.includes('/api/privacy/me')) return { ok: true, json: async () => ({ email: 'user@example.com', consents: [] }) }
      if (url.includes('/api/health')) return { ok: true, json: async () => ({ status: 'UP', liveOrderSubmissionEnabled: false }) }
      if (url.includes('/api/paper/orders/summary')) return { ok: true, json: async () => ({ wallets: [], orders: [] }) }
      return { ok: true, json: async () => ({}) }
    })
    vi.stubGlobal('fetch', fetchMock)
    mount()
    fireEvent.click(screen.getByRole('link', { name: '로그인 / 회원가입' }))
    fireEvent.click(screen.getByRole('tab', { name: '회원가입' }))
    fireEvent.change(screen.getByLabelText('이메일'), { target: { value: 'user@example.com' } })
    fireEvent.click(screen.getByRole('button', { name: '인증 코드 받기' }))
    fireEvent.change(await screen.findByLabelText('이메일 인증 코드'), { target: { value: '123456' } })
    fireEvent.click(screen.getByRole('button', { name: '인증 확인' }))
    expect(await screen.findByText('이메일 인증 완료')).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'Good-pass1!' } })
    fireEvent.click(screen.getByLabelText(/개인정보 처리에 동의합니다/))
    fireEvent.click(screen.getByRole('button', { name: '회원가입' }))
    expect(await screen.findByRole('dialog', { name: '사용할 닉네임을 정해 주세요' })).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('닉네임'), { target: { value: 'Paper42' } })
    fireEvent.click(screen.getByRole('button', { name: '중복확인' }))
    expect(await screen.findByText('사용할 수 있는 닉네임입니다.')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '저장' }))
    expect(await screen.findByText('로그인 계정: user@example.com')).toBeInTheDocument()
    expect(localStorage.length).toBe(0)
    expect(sessionStorage.getItem('crypto-invest-token')).toBe('memory-only-token')
    fireEvent.click(screen.getByRole('button', { name: '로그아웃' }))
    expect(sessionStorage.getItem('crypto-invest-token')).toBeNull()
    fireEvent.click(screen.getByRole('tab', { name: '로그인' }))
    expect(screen.getByLabelText('비밀번호')).not.toHaveAttribute('minlength')
    expect(screen.getByLabelText('비밀번호')).not.toHaveAttribute('pattern')
    fireEvent.click(screen.getByRole('button', { name: '비밀번호 표시' }))
    expect(screen.getByLabelText('비밀번호')).toHaveAttribute('type', 'text')
    fireEvent.click(screen.getByRole('button', { name: '비밀번호 숨기기' }))
    fireEvent.change(screen.getByLabelText('이메일'), { target: { value: 'user@example.com' } })
    fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'legacy' } })
    fireEvent.click(screen.getByRole('button', { name: '로그인' }))
    expect(await screen.findByRole('heading', { name: '내 자산과 시장을 차분히 살펴보세요' })).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/api/auth/register'))).toBe(true)
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/api/auth/login'))).toBe(true)
  })
  it('explains a backend configuration conflict during signup', async () => {
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => String(input).includes('/api/auth/register')
      ? { ok: false, status: 409, json: async () => ({ code: 'INVALID_STATE' }) }
      : String(input).includes('/api/auth/email-verification') ? (String(input).endsWith('/confirm') ? { ok: true, json: async () => ({ verificationToken: 'verified-token' }) } : { ok: true, json: async () => ({ challengeId: 'challenge-1' }) })
      : { ok: true, json: async () => ({ status: 'UP', liveOrderSubmissionEnabled: false }) }))
    mount()
    fireEvent.click(screen.getByRole('link', { name: '로그인 / 회원가입' }))
    fireEvent.click(screen.getByRole('tab', { name: '회원가입' }))
    fireEvent.change(screen.getByLabelText('이메일'), { target: { value: 'new@example.com' } })
    fireEvent.click(screen.getByRole('button', { name: '인증 코드 받기' }))
    fireEvent.change(await screen.findByLabelText('이메일 인증 코드'), { target: { value: '123456' } })
    fireEvent.click(screen.getByRole('button', { name: '인증 확인' }))
    expect(await screen.findByText('이메일 인증 완료')).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'Good-pass1!' } })
    fireEvent.click(screen.getByLabelText(/개인정보 처리에 동의합니다/))
    fireEvent.click(screen.getByRole('button', { name: '회원가입' }))
    expect(await screen.findByText(/서버 설정 또는 서비스 상태 문제/)).toBeInTheDocument()
  })
})
