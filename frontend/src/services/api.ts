/**
 * API service for historical data queries.
 * Requirements: 5.3
 */

import axios from 'axios'

const api = axios.create({ baseURL: '/api' })

export interface KLine {
  symbol: string
  interval: string
  openTime: number
  openPrice: string
  highPrice: string
  lowPrice: string
  closePrice: string
  volume: string
  tradeCount: number
}

export interface TickData {
  symbol: string
  price: string
  quantity: string
  timestamp: number
  trade_id: string
  is_buyer_maker: boolean
}

export async function fetchKLines(
  symbol: string,
  interval: string,
  startTime: number,
  endTime: number
): Promise<KLine[]> {
  const { data } = await api.get<KLine[]>('/history/klines', {
    params: { symbol, interval, startTime, endTime },
  })
  return data
}

export async function fetchTicks(
  symbol: string,
  startTime: number,
  endTime: number
): Promise<TickData[]> {
  const { data } = await api.get<TickData[]>('/history/ticks', {
    params: { symbol, startTime, endTime },
  })
  return data
}
