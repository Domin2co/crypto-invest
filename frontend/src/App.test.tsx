import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import App from './App'

function mount() {
  return render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><App /></QueryClientProvider>)
}

describe('App navigation and account flow', () => {
  afterEach(() => { cleanup(); vi.unstubAllGlobals(); window.history.replaceState(null, '', '/') })

  it('routes each menu to a distinct page and keeps privacy policy in the global footer', async () => {
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => ({ ok: true, json: async () => String(input).includes('/api/health') ? { status: 'UP', tradingMode: 'PAPER' } : {} })))
    mount()
    expect(await screen.findByText('정상')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '개인정보 처리방침' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '포트폴리오 보기' })).not.toBeInTheDocument()
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

  it('registers and logs in through backend APIs while keeping tokens out of browser storage', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input)
      if (url.includes('/api/auth/register') || url.includes('/api/auth/login')) return { ok: true, json: async () => ({ accessToken: 'memory-only-token' }) }
      if (url.includes('/api/privacy/me')) return { ok: true, json: async () => ({ email: 'user@example.com', consents: [] }) }
      if (url.includes('/api/health')) return { ok: true, json: async () => ({ status: 'UP', tradingMode: 'PAPER' }) }
      if (url.includes('/api/paper/orders/summary')) return { ok: true, json: async () => ({ wallets: [], orders: [] }) }
      return { ok: true, json: async () => ({}) }
    })
    vi.stubGlobal('fetch', fetchMock)
    mount()
    fireEvent.click(screen.getByRole('link', { name: '로그인 / 회원가입' }))
    fireEvent.click(screen.getByRole('tab', { name: '회원가입' }))
    fireEvent.change(screen.getByLabelText('이메일'), { target: { value: 'user@example.com' } })
    fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'long-enough-password' } })
    fireEvent.click(screen.getByLabelText(/개인정보 처리에 동의합니다/))
    fireEvent.click(screen.getByRole('button', { name: '회원가입' }))
    expect(await screen.findByText('로그인 계정: user@example.com')).toBeInTheDocument()
    expect(localStorage.length).toBe(0)
    fireEvent.click(screen.getByRole('button', { name: '로그아웃' }))
    fireEvent.click(screen.getByRole('tab', { name: '로그인' }))
    fireEvent.change(screen.getByLabelText('이메일'), { target: { value: 'user@example.com' } })
    fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'long-enough-password' } })
    fireEvent.click(screen.getByRole('button', { name: '로그인' }))
    expect(await screen.findByText('로그인 계정: user@example.com')).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/api/auth/register'))).toBe(true)
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/api/auth/login'))).toBe(true)
  })
  it('explains a backend configuration conflict during signup', async () => {
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => String(input).includes('/api/auth/register')
      ? { ok: false, status: 409, json: async () => ({ code: 'INVALID_STATE' }) }
      : { ok: true, json: async () => ({ status: 'UP', tradingMode: 'PAPER' }) }))
    mount()
    fireEvent.click(screen.getByRole('link', { name: '로그인 / 회원가입' }))
    fireEvent.click(screen.getByRole('tab', { name: '회원가입' }))
    fireEvent.change(screen.getByLabelText('이메일'), { target: { value: 'new@example.com' } })
    fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'long-enough-password' } })
    fireEvent.click(screen.getByLabelText(/개인정보 처리에 동의합니다/))
    fireEvent.click(screen.getByRole('button', { name: '회원가입' }))
    expect(await screen.findByText(/서버 설정 또는 서비스 상태 문제/)).toBeInTheDocument()
  })
})