import { useEffect, useState } from 'react'
import type { Exchange } from './RecommendationPanel'

export type CoinSymbol = string
export type LiveTicker = { price: number; open: number | null; high: number | null; low: number | null; changeRate: number | null; volume24h: number | null; capturedAt: string }
export type PublicTrade = { id: string; price: number; quantity: number; side: 'ASK' | 'BID'; timestamp: number }
export type OrderLevel = { askPrice: number; askSize: number; bidPrice: number; bidSize: number }
export type MarketStream = { tickers: Partial<Record<Exchange, LiveTicker>>; connected: Partial<Record<Exchange, boolean>>; trades: Partial<Record<Exchange, PublicTrade[]>>; orderbooks: Partial<Record<Exchange, OrderLevel[]>> }
const exchanges: Exchange[] = ['UPBIT', 'BITHUMB']
const venues: Record<Exchange, string> = { UPBIT: '업비트', BITHUMB: '빗썸' }
type Wire = Record<string, unknown>
const milliseconds = (value: number) => value > 100_000_000_000_000 ? Math.floor(value / 1000) : value
const millisecondsFor = (row: Wire, ...keys: string[]) => { const value = numeric(row, ...keys); return value === null ? null : milliseconds(value) }
const numeric = (row: Wire, ...keys: string[]) => { const value = keys.map((key) => row[key]).find((item) => item !== undefined && item !== null); const number = Number(value); return Number.isFinite(number) ? number : null }

/** 공개 WebSocket만 구독하며 개인 계정 주문이나 API Key는 읽지 않는다. */
export function useMarketStream(symbol: CoinSymbol): MarketStream {
  const [stream, setStream] = useState<MarketStream>({ tickers: {}, connected: {}, trades: {}, orderbooks: {} })
  useEffect(() => {
    let active = true
    const sockets: WebSocket[] = []
    const timers: number[] = []
    setStream({ tickers: {}, connected: {}, trades: {}, orderbooks: {} })
    for (const exchange of exchanges) {
      const url = exchange === 'UPBIT' ? 'wss://api.upbit.com/websocket/v1' : 'wss://ws-api.bithumb.com/websocket/v1'
      const market = `KRW-${symbol}`
      const connect = () => {
        if (!active || typeof WebSocket === 'undefined') return
        const socket = new WebSocket(url)
        socket.binaryType = 'arraybuffer'
        sockets.push(socket)
        socket.onopen = () => {
          if (!active) return
          setStream((old) => ({ ...old, connected: { ...old.connected, [exchange]: true } }))
          socket.send(JSON.stringify([{ ticket: 'crypto-invest-market' }, { type: 'ticker', codes: [market] }, { type: 'trade', codes: [market] }, { type: 'orderbook', codes: [exchange === 'UPBIT' ? market + '.15' : market] }]))
        }
        socket.onmessage = (event) => {
          try {
            const raw = event.data instanceof ArrayBuffer ? new TextDecoder().decode(event.data) : String(event.data)
            const decoded = JSON.parse(raw) as Wire | Wire[]
            for (const row of Array.isArray(decoded) ? decoded : [decoded]) {
              if (String(row.code ?? row.market ?? row.cd ?? row.mk ?? '').split('.')[0] !== market) continue
              const type = row.type ?? row.ty
              const price = numeric(row, 'trade_price', 'tp')
              const capturedAt = millisecondsFor(row, 'trade_timestamp', 'timestamp', 'ttms', 'tms') ?? Date.now()
              if (type === 'ticker' && price !== null && price > 0) {
                const changeRate = numeric(row, 'signed_change_rate', 'change_rate')
                setStream((old) => ({ ...old, tickers: { ...old.tickers, [exchange]: { price, open: numeric(row, 'opening_price', 'op'), high: numeric(row, 'high_price', 'hp'), low: numeric(row, 'low_price', 'lp'), changeRate: changeRate === null ? null : changeRate * 100, volume24h: numeric(row, 'acc_trade_volume_24h', 'acc_trade_volume'), capturedAt: new Date(capturedAt).toISOString() } } }))
              }
              if (type === 'trade' && price !== null && price > 0) {
                const quantity = numeric(row, 'trade_volume', 'tv')
                if (quantity === null || quantity <= 0) continue
                const id = String(row.sequential_id ?? row.sid ?? `${capturedAt}-${price}-${quantity}`)
                const trade: PublicTrade = { id, price, quantity, side: (row.ask_bid ?? row.ab) === 'ASK' ? 'ASK' : 'BID', timestamp: capturedAt }
                setStream((old) => { const previous = old.trades[exchange] ?? []; if (previous.some((item) => item.id === id)) return old; return { ...old, trades: { ...old.trades, [exchange]: [trade, ...previous].slice(0, 30) } } })
              }
              if (type === 'orderbook' && Array.isArray(row.orderbook_units ?? row.obu)) {
                const units = (row.orderbook_units ?? row.obu) as Wire[]
                const orderbook = units.flatMap((unit) => {
                  const askPrice = numeric(unit, 'ask_price', 'ap'), askSize = numeric(unit, 'ask_size', 'as')
                  const bidPrice = numeric(unit, 'bid_price', 'bp'), bidSize = numeric(unit, 'bid_size', 'bs')
                  return askPrice !== null && askSize !== null && bidPrice !== null && bidSize !== null ? [{ askPrice, askSize, bidPrice, bidSize }] : []
                })
                setStream((old) => ({ ...old, orderbooks: { ...old.orderbooks, [exchange]: orderbook } }))
              }
            }
          } catch { /* Ignore heartbeat and malformed frames; the socket reconnects when closed. */ }
        }
        socket.onclose = () => { if (active) { setStream((old) => ({ ...old, connected: { ...old.connected, [exchange]: false } })); timers.push(window.setTimeout(connect, 2000)) } }
        socket.onerror = () => socket.close()
      }
      connect()
    }
    return () => { active = false; timers.forEach(window.clearTimeout); sockets.forEach((socket) => socket.close()) }
  }, [symbol])
  return stream
}
export { venues }
