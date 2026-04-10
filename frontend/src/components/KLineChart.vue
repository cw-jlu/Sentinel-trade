<template>
  <div class="kline-chart">
    <div class="chart-header">
      <h2 class="chart-title">{{ symbol }} <span class="chart-subtitle">Real-time K-Line</span></h2>
      <div class="chart-controls">
        <!-- Preset interval buttons -->
        <div class="interval-group">
          <button
            v-for="opt in presetIntervals"
            :key="opt.value"
            :class="['interval-btn', { active: displayInterval === opt.value }]"
            @click="selectInterval(opt.value)"
          >{{ opt.label }}</button>
          <!-- Custom interval input -->
          <div class="custom-interval">
            <input
              type="number"
              class="custom-input"
              v-model.number="customMinutes"
              min="1"
              max="1440"
              placeholder="N"
              @keyup.enter="applyCustomInterval"
            />
            <span class="custom-unit">min</span>
            <button class="interval-btn apply-btn" @click="applyCustomInterval">OK</button>
          </div>
        </div>
      </div>
    </div>
    <v-chart
      ref="chartRef"
      :option="chartOption"
      :update-options="{ notMerge: false, lazyUpdate: true }"
      autoresize
      style="height: 480px; width: 100%;"
      @datazoom="onDataZoom"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CandlestickChart, BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, DataZoomComponent, TitleComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { useLocalTime } from '../store/settings'

use([CandlestickChart, BarChart, GridComponent, TooltipComponent, DataZoomComponent, TitleComponent, CanvasRenderer])

interface KLineData {
  openTime: number
  open: number | string
  high: number | string
  low: number | string
  close: number | string
  volume?: number | string
  tradeCount?: number
  openPrice?: number | string
  highPrice?: number | string
  lowPrice?: number | string
  closePrice?: number | string
}

const props = defineProps<{
  symbol: string
  klines: KLineData[]
}>()

const emit = defineEmits<{
  (e: 'intervalChange', interval: string, minutes: number): void
}>()

// Preset interval options
const presetIntervals = [
  { label: '1m', value: '1m', minutes: 1 },
  { label: '3m', value: '3m', minutes: 3 },
  { label: '5m', value: '5m', minutes: 5 },
  { label: '15m', value: '15m', minutes: 15 },
  { label: '30m', value: '30m', minutes: 30 },
  { label: '1h', value: '1h', minutes: 60 },
  { label: '4h', value: '4h', minutes: 240 },
]

const displayInterval = ref('1m')
const customMinutes = ref<number | null>(null)

// Persist dataZoom position
const zoomStart = ref(0)
const zoomEnd = ref(100)
const chartRef = ref<InstanceType<typeof VChart> | null>(null)

function onDataZoom(params: any) {
  if (params.batch && params.batch.length > 0) {
    zoomStart.value = params.batch[0].start ?? zoomStart.value
    zoomEnd.value = params.batch[0].end ?? zoomEnd.value
  } else if (params.start !== undefined) {
    zoomStart.value = params.start
    zoomEnd.value = params.end
  }
}

function selectInterval(value: string) {
  displayInterval.value = value
  const preset = presetIntervals.find(p => p.value === value)
  const minutes = preset ? preset.minutes : 1
  // Reset zoom when switching intervals
  zoomStart.value = 0
  zoomEnd.value = 100
  emit('intervalChange', value, minutes)
}

function applyCustomInterval() {
  const mins = customMinutes.value
  if (!mins || mins < 1) return
  const label = mins >= 60 ? `${mins / 60}h` : `${mins}m`
  displayInterval.value = label
  zoomStart.value = 0
  zoomEnd.value = 100
  emit('intervalChange', label, mins)
}

// Helpers
function getO(k: KLineData): number { return parseFloat(String(k.open ?? k.openPrice ?? 0)) }
function getH(k: KLineData): number { return parseFloat(String(k.high ?? k.highPrice ?? 0)) }
function getL(k: KLineData): number { return parseFloat(String(k.low ?? k.lowPrice ?? 0)) }
function getC(k: KLineData): number { return parseFloat(String(k.close ?? k.closePrice ?? 0)) }

function formatTime(ts: number): string {
  const d = new Date(ts)
  const options: Intl.DateTimeFormatOptions = {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
    timeZone: useLocalTime.value ? undefined : 'UTC'
  }
  
  // If the data spans more than 24 hours or we are on > 1h interval, add date
  const isLargeInterval = props.klines.length > 0 && 
    (props.klines[props.klines.length-1].openTime - props.klines[0].openTime > 24 * 3600 * 1000)
    
  if (isLargeInterval || displayInterval.value.includes('h') || displayInterval.value.includes('d')) {
    options.month = '2-digit'
    options.day = '2-digit'
  }

  return new Intl.DateTimeFormat('en-GB', options).format(d)
}

function formatFullTime(ts: number): string {
  return new Intl.DateTimeFormat('en-GB', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
    timeZone: useLocalTime.value ? undefined : 'UTC'
  }).format(new Date(ts)) + (useLocalTime.value ? ' (Local)' : ' (UTC)')
}

const upColor = '#10b981'
const downColor = '#f43f5e'

const chartOption = computed(() => ({
  backgroundColor: 'transparent',
  tooltip: {
    trigger: 'axis',
    axisPointer: { type: 'cross', lineStyle: { color: 'rgba(255,255,255,0.2)' } },
    backgroundColor: 'rgba(15, 23, 42, 0.8)',
    borderColor: 'rgba(255,255,255,0.1)',
    textStyle: { color: '#f8fafc' },
    padding: 12,
    borderRadius: 8,
    formatter: (params: any) => {
      const p = params[0]
      const k = props.klines[p.dataIndex]
      if (!k) return ''
      const timeStr = formatFullTime(k.openTime)
      return `
        <div style="font-weight: 600; margin-bottom: 4px; color: #94a3b8; font-size: 11px;">${timeStr}</div>
        <div style="display: grid; grid-template-columns: auto auto; gap: 8px; font-family: monospace;">
          <span style="color: #64748b">Open:</span> <span style="text-align: right">${getO(k)}</span>
          <span style="color: #64748b">High:</span> <span style="text-align: right; color: #10b981">${getH(k)}</span>
          <span style="color: #64748b">Low:</span> <span style="text-align: right; color: #f43f5e">${getL(k)}</span>
          <span style="color: #64748b">Close:</span> <span style="text-align: right">${getC(k)}</span>
          ${k.volume ? `<span style="color: #64748b">Vol:</span> <span style="text-align: right">${parseFloat(String(k.volume)).toFixed(2)}</span>` : ''}
        </div>
      `
    }
  },
  grid: {
    left: '3%', right: '3%', bottom: '10%', top: '5%', containLabel: true
  },
  xAxis: {
    type: 'category',
    data: props.klines.map(k => formatTime(k.openTime)),
    axisLine: { lineStyle: { color: 'rgba(255,255,255,0.1)' } },
    axisLabel: { color: '#64748b' }
  },
  yAxis: {
    type: 'value',
    scale: true,
    splitLine: { lineStyle: { color: 'rgba(255,255,255,0.05)' } },
    axisLabel: { color: '#64748b' }
  },
  dataZoom: [
    { type: 'inside', start: zoomStart.value, end: zoomEnd.value },
    {
      type: 'slider', show: true,
      start: zoomStart.value, end: zoomEnd.value,
      backgroundColor: 'rgba(0,0,0,0)',
      fillerColor: 'rgba(99, 102, 241, 0.2)',
      borderColor: 'rgba(255,255,255,0.05)',
      handleStyle: { color: '#6366f1', borderColor: '#4f46e5' },
      textStyle: { color: '#64748b' }
    }
  ],
  series: [{
    name: 'Price',
    type: 'candlestick',
    data: props.klines.map(k => [getO(k), getC(k), getL(k), getH(k)]),
    itemStyle: {
      color: upColor,
      color0: downColor,
      borderColor: upColor,
      borderColor0: downColor,
    },
  }],
}))
</script>

<style scoped>
.kline-chart {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  width: 100%;
  height: 100%;
}
.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid var(--glass-border);
  padding-bottom: 0.8rem;
  flex-wrap: wrap;
  gap: 0.5rem;
}
.chart-title {
  font-size: 1.2rem;
  color: var(--text-primary);
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
.chart-subtitle {
  font-size: 0.8rem;
  padding: 0.2rem 0.5rem;
  background: rgba(99, 102, 241, 0.2);
  color: var(--accent-indigo);
  border-radius: 4px;
  font-weight: 600;
}
.chart-controls {
  display: flex;
  gap: 0.5rem;
}
.interval-group {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  background: rgba(0, 0, 0, 0.2);
  border-radius: var(--radius-md);
  padding: 0.2rem;
  border: 1px solid var(--glass-border);
}
.interval-btn {
  background: transparent;
  border: none;
  color: var(--text-secondary);
  padding: 0.35rem 0.7rem;
  border-radius: 6px;
  font-size: 0.8rem;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--transition-fast);
  font-family: inherit;
}
.interval-btn:hover {
  color: var(--text-primary);
  background: rgba(255,255,255,0.05);
}
.interval-btn.active {
  background: var(--accent-indigo);
  color: white;
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.3);
}
.custom-interval {
  display: flex;
  align-items: center;
  gap: 0.2rem;
  margin-left: 0.25rem;
  padding-left: 0.5rem;
  border-left: 1px solid var(--glass-border);
}
.custom-input {
  width: 40px;
  background: rgba(0,0,0,0.3);
  border: 1px solid var(--glass-border);
  color: var(--text-primary);
  padding: 0.3rem 0.4rem;
  border-radius: 4px;
  font-size: 0.8rem;
  text-align: center;
  font-family: inherit;
}
.custom-input:focus {
  border-color: var(--accent-indigo);
  outline: none;
}
.custom-unit {
  font-size: 0.75rem;
  color: var(--text-muted);
}
.apply-btn {
  font-size: 0.75rem;
  padding: 0.3rem 0.5rem;
  background: rgba(99, 102, 241, 0.2);
  color: var(--accent-indigo);
}
.apply-btn:hover {
  background: var(--accent-indigo);
  color: white;
}
</style>
