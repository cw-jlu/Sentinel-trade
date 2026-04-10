/**
 * WebSocketService: manages connection to the backend market data WebSocket.
 * Implements reconnect logic with exponential backoff.
 *
 * Requirements: 5.1
 */

import { ref, type Ref } from 'vue'

export type MessageHandler = (data: unknown) => void

export class WebSocketService {
  private ws: WebSocket | null = null
  private url: string
  private handlers: MessageHandler[] = []
  private reconnectDelay = 1000
  private maxDelay = 30000
  private shouldReconnect = false

  readonly connected: Ref<boolean> = ref(false)

  constructor(url: string) {
    this.url = url
  }

  connect(): void {
    this.shouldReconnect = true
    this._open()
  }

  disconnect(): void {
    this.shouldReconnect = false
    this.ws?.close()
    this.ws = null
    this.connected.value = false
  }

  onMessage(handler: MessageHandler): void {
    this.handlers.push(handler)
  }

  private _open(): void {
    this.ws = new WebSocket(this.url)

    this.ws.onopen = () => {
      this.connected.value = true
      this.reconnectDelay = 1000
    }

    this.ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data)
        this.handlers.forEach(h => h(data))
      } catch {
        // ignore malformed messages
      }
    }

    this.ws.onclose = () => {
      this.connected.value = false
      if (this.shouldReconnect) {
        setTimeout(() => this._open(), this.reconnectDelay)
        this.reconnectDelay = Math.min(this.reconnectDelay * 2, this.maxDelay)
      }
    }

    this.ws.onerror = () => {
      this.ws?.close()
    }
  }
}
