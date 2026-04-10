<template>
  <div class="dashboard">
    <!-- Premium Frosted Glass Header -->
    <header class="dashboard-header glass-panel">
      <div class="header-brand">
        <div class="logo-icon">⬡</div>
        <h1>Sentinel-Trade <span class="badge">PRO</span></h1>
      </div>
      
      <div class="header-controls">
        <!-- Timezone Toggle -->
        <div class="timezone-toggle" @click="toggleTimezone">
          <span :class="{ active: !useLocalTime }">UTC</span>
          <div class="toggle-slider">
            <div class="toggle-knob" :style="{ transform: useLocalTime ? 'translateX(20px)' : 'translateX(0)' }"></div>
          </div>
          <span :class="{ active: useLocalTime }">Local</span>
        </div>

        <SymbolSelector v-model="selectedSymbol" @update:modelValue="onSymbolChange" />
        <div :class="['status-badge', wsConnected ? 'connected' : 'disconnected']">
          <span class="pulse-dot"></span>
          {{ wsConnected ? 'Live' : 'Disconnected' }}
        </div>
      </div>
    </header>

    <main class="dashboard-main">
      <section class="chart-section glass-panel fade-in-up" style="animation-delay: 0.1s">
        <KLineChart
          :symbol="selectedSymbol"
          :klines="klines"
          @intervalChange="onIntervalChange"
        />
      </section>

      <section class="history-section glass-panel fade-in-up" style="animation-delay: 0.2s">
        <div class="date-range">
          <div class="input-group">
            <span class="label-text">From</span>
            <input type="datetime-local" class="glass-input" v-model="startDate" />
          </div>
          <div class="input-group">
            <span class="label-text">To</span>
            <input type="datetime-local" class="glass-input" v-model="endDate" />
          </div>
          <button class="primary-btn" @click="loadHistory">
            <span>Query History</span>
          </button>
        </div>
      </section>

      <aside class="alert-section glass-panel fade-in-up" style="animation-delay: 0.3s">
        <div class="section-title">
          <h2>Market Anomalies</h2>
          <span class="alert-counter" v-if="alerts.length">{{ alerts.length }}</span>
        </div>
        <AlertList :alerts="alerts" />
      </aside>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import KLineChart from './components/KLineChart.vue'
import AlertList from './components/AlertList.vue'
import SymbolSelector from './components/SymbolSelector.vue'
import { WebSocketService } from './services/WebSocketService'
import { fetchKLines } from './services/api'
import { useLocalTime, toggleTimezone } from './store/settings'

const selectedSymbol = ref('BTCUSDT')
const displayInterval = ref('1m')
const intervalMinutes = ref(1)
const raw1mKlines = ref<any[]>([])     // Always collect 1m candles as raw material
const historyKlines = ref<any[]>([])   // Historical data loaded from backend
const alerts = ref<any[]>([])
const wsConnected = ref(false)
const startDate = ref('')
const endDate = ref('')

// Map display interval to the backend-native interval for fetching
const nativeIntervals: Record<string, string> = { '1m': '1m', '5m': '5m', '1h': '1h' }

// Aggregate 1m candles into N-minute candles on the client side
function aggregate1mCandles(candles1m: any[], minutes: number): any[] {
  if (minutes <= 1 || candles1m.length === 0) return candles1m

  const windowMs = minutes * 60 * 1000
  const groups: Record<number, any[]> = {}

  for (const c of candles1m) {
    const bucket = Math.floor(c.openTime / windowMs) * windowMs
    if (!groups[bucket]) groups[bucket] = []
    groups[bucket].push(c)
  }

  return Object.entries(groups)
    .map(([bucket, arr]) => {
      const openTime = Number(bucket)
      const o = parseFloat(String(arr[0].open ?? arr[0].openPrice ?? 0))
      const c = parseFloat(String(arr[arr.length - 1].close ?? arr[arr.length - 1].closePrice ?? 0))
      let h = -Infinity, l = Infinity, vol = 0, tc = 0
      for (const k of arr) {
        const hv = parseFloat(String(k.high ?? k.highPrice ?? 0))
        const lv = parseFloat(String(k.low ?? k.lowPrice ?? 0))
        if (hv > h) h = hv
        if (lv < l) l = lv
        vol += parseFloat(String(k.volume ?? 0))
        tc += (k.tradeCount ?? 0)
      }
      return { openTime, open: o, high: h, low: l, close: c, volume: vol, tradeCount: tc }
    })
    .sort((a, b) => a.openTime - b.openTime)
}

// Compute display K-lines: aggregate from raw 1m data OR use history
const klines = computed(() => {
  const mins = intervalMinutes.value
  // If we have history data, use it; otherwise aggregate from real-time 1m data
  const source = historyKlines.value.length > 0 ? historyKlines.value : raw1mKlines.value
  if (mins <= 1) return source
  return aggregate1mCandles(source, mins)
})

// WebSocket — always subscribe to ALL 1m K-line updates
const wsService = new WebSocketService(
  `ws://${window.location.host}/ws/market-data`
)

wsService.onMessage((data: any) => {
  if (data.type === 'kline' && data.symbol === selectedSymbol.value
      && data.interval === '1m') {
    const incoming = data.data
    const idx = raw1mKlines.value.findIndex((k: any) => k.openTime === incoming.openTime)
    if (idx >= 0) {
      raw1mKlines.value[idx] = incoming
      raw1mKlines.value = [...raw1mKlines.value]
    } else {
      raw1mKlines.value = [...raw1mKlines.value.slice(-500), incoming]
    }
  }
  // Also collect native 5m/1h if backend sends them (for direct display)
  if (data.type === 'kline' && data.symbol === selectedSymbol.value
      && data.interval === displayInterval.value && data.interval !== '1m') {
    const incoming = data.data
    const idx = raw1mKlines.value.findIndex((k: any) => k.openTime === incoming.openTime)
    if (idx < 0) {
      // Store as extra data point, but don't duplicate
    }
  }
  if (data.type === 'alert' && data.symbol === selectedSymbol.value) {
    alerts.value = [data.data, ...alerts.value.slice(0, 99)]
  }
})

function onSymbolChange() {
  raw1mKlines.value = []
  historyKlines.value = []
  alerts.value = []
  autoLoadRecent()
}

async function onIntervalChange(interval: string, minutes: number) {
  displayInterval.value = interval
  intervalMinutes.value = minutes
  historyKlines.value = []
  // Auto-load recent history for the selected interval
  await autoLoadRecent()
}

// Auto-load the most recent N hours of data from backend
async function autoLoadRecent() {
  try {
    const now = Date.now()
    const mins = intervalMinutes.value
    // Load enough history to fill the chart: at least 200 candles worth
    const lookbackMs = Math.max(mins * 200 * 60 * 1000, 2 * 60 * 60 * 1000) // at least 2h
    const nativeInterval = nativeIntervals[displayInterval.value] || '1m'
    const data = await fetchKLines(selectedSymbol.value, nativeInterval, now - lookbackMs, now)
    if (data && data.length > 0) {
      // If user picked a native interval, use directly; otherwise we'll aggregate from 1m
      if (nativeInterval === displayInterval.value || intervalMinutes.value <= 1) {
        historyKlines.value = data
      } else {
        // For custom intervals, fetch 1m data and let the computed aggregation handle it
        const data1m = await fetchKLines(selectedSymbol.value, '1m', now - lookbackMs, now)
        historyKlines.value = data1m || []
      }
    }
  } catch (e) {
    // Silently fail — real-time data will still populate
    console.warn('Failed to load history:', e)
  }
}

async function loadHistory() {
  if (!startDate.value || !endDate.value) return
  
  // If useLocalTime is false, we interpret the string as UTC
  let start: number, end: number
  if (useLocalTime.value) {
    start = new Date(startDate.value).getTime()
    end = new Date(endDate.value).getTime()
  } else {
    // Treat as UTC
    start = new Date(startDate.value + ':00Z').getTime()
    end = new Date(endDate.value + ':00Z').getTime()
  }
  
  const nativeInterval = nativeIntervals[displayInterval.value] || '1m'
  const data = await fetchKLines(selectedSymbol.value, nativeInterval, start, end)
  historyKlines.value = data || []
}

// Watch for timezone changes to update input strings (best effort)
watch(useLocalTime, (isLocal) => {
  if (!startDate.value || !endDate.value) return
  
  const sTs = isLocal 
    ? new Date(startDate.value + ':00Z').getTime() // was UTC, now local
    : new Date(startDate.value).getTime()          // was local, now UTC
  
  const eTs = isLocal 
    ? new Date(endDate.value + ':00Z').getTime() 
    : new Date(endDate.value).getTime()
    
  startDate.value = formatForPicker(sTs, isLocal)
  endDate.value = formatForPicker(eTs, isLocal)
})

function formatForPicker(ts: number, local: boolean): string {
  const d = new Date(ts)
  if (local) {
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
  } else {
    return d.toISOString().slice(0, 16)
  }
}

onMounted(() => {
  wsService.connect()
  watch(() => wsService.connected.value, (newVal) => {
    wsConnected.value = newVal
  }, { immediate: true })
  // Auto-load recent history on startup
  autoLoadRecent()
})

onUnmounted(() => {
  wsService.disconnect()
})
</script>

<style scoped>
.dashboard { 
  display: flex; 
  flex-direction: column; 
  height: 100vh; 
  padding: 1.5rem;
  gap: 1.5rem;
  max-width: 1800px;
  margin: 0 auto;
}

/* Header */
.dashboard-header {
  display: flex; 
  justify-content: space-between;
  align-items: center; 
  padding: 1rem 2rem; 
  background: rgba(15, 23, 42, 0.4);
}

.header-brand {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.logo-icon {
  font-size: 1.5rem;
  color: var(--accent-emerald);
  filter: drop-shadow(0 0 8px var(--accent-emerald-glow));
}

.dashboard-header h1 { 
  font-size: 1.25rem; 
  font-weight: 600;
  letter-spacing: 0.5px;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.badge {
  font-size: 0.6rem;
  padding: 0.15rem 0.4rem;
  background: linear-gradient(135deg, var(--accent-indigo), #9333ea);
  border-radius: 4px;
  font-weight: 700;
  letter-spacing: 1px;
}

.header-controls {
  display: flex;
  align-items: center;
  gap: 1.5rem;
}

/* Timezone Toggle Styles */
.timezone-toggle {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  background: rgba(0, 0, 0, 0.2);
  padding: 0.3rem 0.6rem;
  border-radius: 20px;
  border: 1px solid var(--glass-border);
  cursor: pointer;
  user-select: none;
  transition: all var(--transition-fast);
}

.timezone-toggle:hover {
  background: rgba(255, 255, 255, 0.05);
  border-color: rgba(255, 255, 255, 0.2);
}

.timezone-toggle span {
  font-size: 0.7rem;
  font-weight: 600;
  color: var(--text-muted);
  transition: color 0.3s;
}

.timezone-toggle span.active {
  color: var(--accent-indigo);
}

.toggle-slider {
  width: 36px;
  height: 16px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 10px;
  position: relative;
  transition: background 0.3s;
}

.toggle-knob {
  width: 12px;
  height: 12px;
  background: white;
  border-radius: 50%;
  position: absolute;
  top: 2px;
  left: 2px;
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
}

/* Custom Status Badge */
.status-badge { 
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.4rem 1rem; 
  border-radius: 20px; 
  font-size: 0.85rem; 
  font-weight: 500;
  background: rgba(0,0,0,0.2);
  border: 1px solid rgba(255,255,255,0.05);
  transition: all var(--transition-fast);
}

.pulse-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.connected .pulse-dot { 
  background: var(--accent-emerald); 
  animation: pulse-emerald 2s infinite;
}
.connected { color: var(--accent-emerald); border-color: rgba(16, 185, 129, 0.2); }

.disconnected .pulse-dot { 
  background: var(--accent-rose); 
  animation: pulse-rose 2s infinite;
}
.disconnected { color: var(--accent-rose); border-color: rgba(244, 63, 94, 0.2); }

/* Main Grid Layout */
.dashboard-main {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 350px;
  grid-template-rows: minmax(500px, 1fr) auto;
  gap: 1.5rem; 
  flex: 1;
  min-height: 0; /* Important for flex child scrolling */
}

.chart-section { 
  grid-column: 1; 
  grid-row: 1; 
  padding: 1.5rem;
  display: flex;
  flex-direction: column;
}

.history-section { 
  grid-column: 1; 
  grid-row: 2; 
  padding: 1.25rem 1.5rem; 
}

.alert-section { 
  grid-column: 2; 
  grid-row: 1 / 3; 
  padding: 1.5rem; 
  overflow-y: hidden;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

/* History Range Controls */
.date-range { 
  display: flex; 
  gap: 1.5rem; 
  align-items: flex-end; 
  flex-wrap: wrap; 
}

.input-group {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.label-text {
  font-size: 0.75rem;
  color: var(--text-secondary);
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.glass-input { 
  background: rgba(0, 0, 0, 0.2); 
  color: var(--text-primary); 
  border: 1px solid rgba(255,255,255,0.1); 
  border-radius: var(--radius-md); 
  padding: 0.6rem 0.8rem; 
  font-size: 0.9rem;
  transition: all var(--transition-fast);
  color-scheme: dark;
}

.glass-input:focus {
  border-color: var(--accent-indigo);
  box-shadow: 0 0 0 2px rgba(99, 102, 241, 0.2);
}

.primary-btn { 
  padding: 0.6rem 1.5rem; 
  background: linear-gradient(135deg, var(--accent-indigo), #4f46e5); 
  border: 1px solid rgba(255,255,255,0.1); 
  border-radius: var(--radius-md); 
  color: #fff; 
  font-weight: 500;
  cursor: pointer; 
  transition: all var(--transition-fast);
  height: 41px;
}

.primary-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
  background: linear-gradient(135deg, #4f46e5, #4338ca); 
}

.primary-btn:active {
  transform: translateY(0);
}

/* Typography & Titles */
.section-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 0.5rem;
  border-bottom: 1px solid var(--glass-border);
}

.section-title h2 {
  font-size: 1.1rem;
  font-weight: 500;
  color: var(--text-primary);
}

.alert-counter {
  background: var(--accent-rose);
  color: white;
  font-size: 0.7rem;
  font-weight: 700;
  padding: 0.1rem 0.5rem;
  border-radius: 10px;
}

.fade-in-up {
  animation: fadeInSlideUp 0.6s cubic-bezier(0.16, 1, 0.3, 1) both;
}

@media (max-width: 1200px) {
  .dashboard-main {
    grid-template-columns: 1fr;
    grid-template-rows: auto auto auto;
  }
  .alert-section {
    grid-column: 1;
    grid-row: 3;
    max-height: 400px;
  }
}
</style>
