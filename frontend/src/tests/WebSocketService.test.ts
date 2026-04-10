/**
 * Unit tests for WebSocketService.
 * Requirements: 5.1
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { WebSocketService } from '../services/WebSocketService'

// Mock WebSocket
class MockWebSocket {
  static instances: MockWebSocket[] = []
  url: string
  onopen: (() => void) | null = null
  onmessage: ((e: { data: string }) => void) | null = null
  onclose: (() => void) | null = null
  onerror: (() => void) | null = null
  readyState = 0

  constructor(url: string) {
    this.url = url
    MockWebSocket.instances.push(this)
  }

  close() { this.onclose?.() }
  triggerOpen() { this.readyState = 1; this.onopen?.() }
  triggerMessage(data: string) { this.onmessage?.({ data }) }
  triggerClose() { this.readyState = 3; this.onclose?.() }
}

beforeEach(() => {
  MockWebSocket.instances = []
  vi.stubGlobal('WebSocket', MockWebSocket)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('WebSocketService', () => {
  it('connects and sets connected to true on open', () => {
    const svc = new WebSocketService('ws://localhost/ws')
    svc.connect()
    MockWebSocket.instances[0].triggerOpen()
    expect(svc.connected.value).toBe(true)
  })

  it('sets connected to false on close', () => {
    const svc = new WebSocketService('ws://localhost/ws')
    svc.connect()
    const ws = MockWebSocket.instances[0]
    ws.triggerOpen()
    ws.triggerClose()
    expect(svc.connected.value).toBe(false)
  })

  it('calls message handlers with parsed JSON', () => {
    const svc = new WebSocketService('ws://localhost/ws')
    const received: unknown[] = []
    svc.onMessage(data => received.push(data))
    svc.connect()
    MockWebSocket.instances[0].triggerMessage('{"type":"kline","symbol":"BTCUSDT"}')
    expect(received).toHaveLength(1)
    expect((received[0] as any).type).toBe('kline')
  })

  it('ignores malformed JSON messages', () => {
    const svc = new WebSocketService('ws://localhost/ws')
    const received: unknown[] = []
    svc.onMessage(data => received.push(data))
    svc.connect()
    MockWebSocket.instances[0].triggerMessage('not-json')
    expect(received).toHaveLength(0)
  })

  it('disconnect stops reconnection', () => {
    vi.useFakeTimers()
    const svc = new WebSocketService('ws://localhost/ws')
    svc.connect()
    svc.disconnect()
    const countBefore = MockWebSocket.instances.length
    vi.advanceTimersByTime(5000)
    expect(MockWebSocket.instances.length).toBe(countBefore)
    vi.useRealTimers()
  })

  it('reconnects after connection drop', () => {
    vi.useFakeTimers()
    const svc = new WebSocketService('ws://localhost/ws')
    svc.connect()
    MockWebSocket.instances[0].triggerOpen()
    MockWebSocket.instances[0].triggerClose()
    vi.advanceTimersByTime(1500)
    expect(MockWebSocket.instances.length).toBe(2)
    vi.useRealTimers()
  })
})
