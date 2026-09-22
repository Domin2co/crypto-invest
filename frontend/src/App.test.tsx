import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import App from './App'

const ticker = { market: 'KRW-BTC', price: 123456, capturedAt: '2026-09-21T00:00:00Z' }
const recommendation = { recommendation: { symbol: 'KRW-BTC', score: 90, signal: 'ACCUMULATE', targetWeight: 0.35, reasons: ['RSI 과매도'] }, generatedAt: '2026-09-21T00:00:00Z', dataCapturedAt: '2026-09-20T00:00:00Z', dataSource: 'UPBIT public daily candles', candleCount: 15, indicators: { rsi: 25, momentum: 1, volatilityPercent: 0.5 }, limitations: ['추천은 주문 지시가 아닙니다.'] }
const publicFetch = (input: RequestInfo | URL) => Promise.resolve({ ok: true, json: async () => String(input).includes('/api/recommendations/') ? recommendation : ticker })

describe('App', () => {
  afterEach(() => { cleanup(); vi.unstubAllGlobals() })

  it('shows the paper-only dashboard and public ticker without exposing an API key', async () => {
    vi.stubGlobal('fetch', vi.fn(publicFetch))
    render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><App /></QueryClientProvider>)

    expect(screen.getByRole('heading', { name: '투자 현황' })).toBeInTheDocument()
    expect(screen.getByLabelText('거래 모드: PAPER')).toHaveTextContent('PAPER 모드')
    expect(screen.getByRole('heading', { name: '추천과 주문 계획' })).toBeInTheDocument()
    expect(screen.queryByText(/API Key:|Secret:/i)).not.toBeInTheDocument()
    expect(await screen.findByText('₩123,456')).toBeInTheDocument()
    expect(await screen.findByText(/ACCUMULATE · 점수 90/)).toBeInTheDocument()
  })

  it('keeps the token in memory and makes PAPER trading available after required consent', async () => {
    const fetchMock = vi.fn((input: RequestInfo | URL, _init?: RequestInit) => {
      void _init
      const url = String(input)
      if (url.includes('/api/auth/register')) return Promise.resolve({ ok: true, json: async () => ({ accessToken: 'memory-only-token' }) })
      if (url.includes('/api/privacy/me')) return Promise.resolve({ ok: true, json: async () => ({ email: 'user@example.com', consents: [{ type: 'PRIVACY', policyVersion: '2026-09-21', grantedAt: '2026-09-21T00:00:00Z', withdrawnAt: null }] }) })
      if (url.includes('/api/paper/orders/summary')) return Promise.resolve({ ok: true, json: async () => ({ wallets: [], orders: [] }) })
      if (url.includes('/api/portfolio/UPBIT/targets')) return Promise.resolve({ ok: true })
      if (url.includes('/api/portfolio/')) return Promise.resolve({ ok: true, json: async () => ({ exchange: 'UPBIT', totalEvaluatedAmount: 100000, cashWeight: 0.4, capturedAt: '2026-09-21T00:00:00Z', positions: [{ currency: 'KRW', quantity: 40000, averageBuyPrice: 0, currentPrice: 1, evaluatedAmount: 40000, weight: 0.4, targetWeight: null, rebalancingGap: null }] }) })
      return publicFetch(input)
    })
    vi.stubGlobal('fetch', fetchMock)
    render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><App /></QueryClientProvider>)

    fireEvent.change(screen.getByLabelText('이메일'), { target: { value: 'user@example.com' } })
    fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'long-enough-password' } })
    fireEvent.click(screen.getByLabelText(/개인정보 처리에 동의합니다/))
    fireEvent.click(screen.getByRole('button', { name: '회원가입' }))

    expect(await screen.findByText('로그인 계정: user@example.com')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'PAPER 주문 실행' })).toBeInTheDocument()
    const registerCall = fetchMock.mock.calls.find(([url]) => String(url).includes('/api/auth/register'))
    expect(registerCall?.[1]).toMatchObject({ method: 'POST' })
    expect(JSON.parse((registerCall?.[1] as RequestInit).body as string)).toMatchObject({ privacyAccepted: true, marketingAccepted: false })

    fireEvent.change(screen.getByLabelText('접근 키'), { target: { value: 'test-access' } })
    fireEvent.change(screen.getByLabelText('비밀 키'), { target: { value: 'test-secret' } })
    fireEvent.click(screen.getByRole('button', { name: '암호화 저장' }))
    expect(await screen.findByText(/암호화해 저장했습니다/)).toBeInTheDocument()
    const accountCall = fetchMock.mock.calls.find(([url]) => String(url).includes('/api/exchange-accounts'))
    expect(accountCall?.[1]).toMatchObject({ method: 'POST', headers: expect.objectContaining({ Authorization: 'Bearer memory-only-token' }) })

    fireEvent.click(screen.getByRole('button', { name: '포트폴리오 새로고침' }))
    expect(await screen.findByText('100,000 KRW')).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('KRW 목표 비중 (0~1)'), { target: { value: '0.4' } })
    fireEvent.click(screen.getByRole('button', { name: '목표 비중 저장' }))
    expect(await screen.findByText('목표 비중을 저장했습니다.')).toBeInTheDocument()
    const targetCall = fetchMock.mock.calls.find(([url]) => String(url).includes('/api/portfolio/UPBIT/targets'))
    expect(JSON.parse((targetCall?.[1] as RequestInit).body as string)).toEqual({ targets: [{ currency: 'KRW', weight: 0.4 }] })
  })
})
