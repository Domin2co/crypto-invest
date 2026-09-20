import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import App from './App'

describe('App', () => {
  it('shows the paper-only dashboard without exposing an API key', () => {
    render(<App />)

    expect(screen.getByRole('heading', { name: '투자 현황' })).toBeInTheDocument()
    expect(screen.getByLabelText('거래 모드: PAPER')).toHaveTextContent('PAPER 모드')
    expect(screen.getByRole('heading', { name: '추천과 주문 계획' })).toBeInTheDocument()
    expect(screen.queryByText(/API Key:|Secret:/i)).not.toBeInTheDocument()
  })
})
