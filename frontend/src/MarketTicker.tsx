import { useState } from 'react'
import type { CoinSymbol, Exchange } from './RecommendationPanel'
import MarketTradingPanel from './MarketTradingPanel'
import { useMarketStream } from './useMarketStream'

/** 공개 시장 데이터만 표시하며 사용자 계정이나 주문 기능에는 접근하지 않는다. */
export default function MarketTicker({ token }: { token: string | null }) {
  const [exchange, setExchange] = useState<Exchange>('UPBIT')
  const [symbol, setSymbol] = useState<CoinSymbol>('BTC')
  const stream = useMarketStream(symbol)
  return <MarketTradingPanel exchange={exchange} symbol={symbol} onExchangeChange={setExchange} onSymbolChange={setSymbol} stream={stream} token={token} />
}
