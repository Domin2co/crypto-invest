import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import App from './App'

describe('App', () => {
  afterEach(() => { cleanup(); vi.unstubAllGlobals() })

  it('shows the paper-only dashboard and public ticker without exposing an API key', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => ({ market: 'KRW-BTC', price: 123456, capturedAt: '2026-09-21T00:00:00Z' }) }))
    render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><App /></QueryClientProvider>)

    expect(screen.getByRole('heading', { name: '투자 현황' })).toBeInTheDocument()
    expect(screen.getByLabelText('거래 모드: PAPER')).toHaveTextContent('PAPER 모드')
    expect(screen.getByRole('heading', { name: '추천과 주문 계획' })).toBeInTheDocument()
    expect(screen.queryByText(/API Key:|Secret:/i)).not.toBeInTheDocument()
    expect(await screen.findByText('₩123,456')).toBeInTheDocument()
  })

  it('requires privacy consent while keeping marketing optional and in-memory', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => ({ market: 'KRW-BTC', price: 123456, capturedAt: '2026-09-21T00:00:00Z' }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ accessToken: 'memory-only-token' }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ email: 'user@example.com', consents: [{ type: 'PRIVACY', policyVersion: '2026-09-21', grantedAt: '2026-09-21T00:00:00Z', withdrawnAt: null }] }) })
    vi.stubGlobal('fetch', fetchMock)
    render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><App /></QueryClientProvider>)

    fireEvent.change(screen.getByLabelText('이메일'), { target: { value: 'user@example.com' } })
    fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'long-enough-password' } })
    fireEvent.click(screen.getByLabelText(/개인정보 처리에 동의합니다/))
    fireEvent.click(screen.getByRole('button', { name: '회원가입' }))

    expect(await screen.findByText('로그인 계정: user@example.com')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/auth/register', expect.objectContaining({ method: 'POST' }))
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toMatchObject({ privacyAccepted: true, marketingAccepted: false })
  })
})
