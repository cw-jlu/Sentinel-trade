<template>
  <div class="alert-list" role="region" aria-label="Alerts">
    <div class="alert-filters">
      <label for="alert-filter">Filter:</label>
      <select id="alert-filter" class="filter-select" v-model="filterType">
        <option value="">All Anomalies</option>
        <option value="LARGE_ORDER">Large Order</option>
        <option value="FLASH_CRASH">Flash Crash</option>
      </select>
    </div>
    
    <TransitionGroup name="list" tag="ul" class="alerts-container">
      <li v-for="alert in filteredAlerts" :key="alert.alertId" :class="['alert-card', alert.severity.toLowerCase()]">
        <div class="alert-header">
          <span class="alert-type">{{ alert.alertType }}</span>
          <span class="alert-time">{{ timeAgo(alert.timestamp) }}</span>
        </div>
        <div class="alert-body">
          <span class="alert-symbol">{{ alert.symbol }}</span>
          <span class="alert-amount">{{ formatAmount(alert.amount) }} USDT</span>
        </div>
      </li>
      <li v-if="filteredAlerts.length === 0" class="no-alerts" key="empty">
        <div class="empty-state">No anomalies detected</div>
      </li>
    </TransitionGroup>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useLocalTime } from '../store/settings'

interface Alert { 
  alertId: string; 
  alertType: string; 
  symbol: string; 
  timestamp: number; 
  severity: string; 
  amount: string 
}

const props = defineProps<{ alerts: Alert[] }>()
const filterType = ref('')

const filteredAlerts = computed(() => {
  const sorted = [...props.alerts].sort((a, b) => b.timestamp - a.timestamp)
  return filterType.value ? sorted.filter(a => a.alertType === filterType.value) : sorted
})

function formatAmount(amt: string): string {
  const val = parseFloat(amt);
  if (isNaN(val)) return amt;
  if (val > 1000000) return (val / 1000000).toFixed(2) + 'M';
  if (val > 1000) return (val / 1000).toFixed(2) + 'K';
  return val.toFixed(2);
}

function timeAgo(ts: number): string {
  const diff = Date.now() - ts;
  const seconds = Math.floor(diff / 1000);
  if (seconds < 60) return `${seconds}s ago`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  
  return new Date(ts).toLocaleTimeString('en-GB', {
    hour: '2-digit', 
    minute:'2-digit',
    hour12: false,
    timeZone: useLocalTime.value ? undefined : 'UTC'
  }) + (useLocalTime.value ? ' (L)' : ' (U)');
}
</script>

<style scoped>
.alert-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  height: 100%;
}
.alert-filters {
  display: flex;
  align-items: center;
  gap: 0.8rem;
  padding-bottom: 0.5rem;
}
.alert-filters label {
  color: var(--text-muted);
  font-size: 0.8rem;
  text-transform: uppercase;
}
.filter-select {
  background: rgba(0,0,0,0.2);
  border: 1px solid var(--glass-border);
  color: var(--text-primary);
  padding: 0.3rem 0.6rem;
  border-radius: var(--radius-md);
  font-size: 0.85rem;
  cursor: pointer;
  outline: none;
}
.filter-select:focus {
  border-color: var(--accent-indigo);
}
.alerts-container {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  overflow-y: auto;
  padding-right: 0.5rem;
  flex: 1;
}

/* Alert Item Card */
.alert-card {
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
  padding: 0.8rem 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  transition: all var(--transition-fast);
  position: relative;
  overflow: hidden;
}
.alert-card:hover {
  transform: translateX(4px);
  background: rgba(255, 255, 255, 0.05);
}

/* Neon Borders overlay */
.alert-card::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
}
.alert-card.high::before, .alert-card.medium::before {
  background: var(--accent-amber);
  box-shadow: 0 0 10px var(--accent-amber);
}
.alert-card.critical::before {
  background: var(--accent-rose);
  box-shadow: 0 0 10px var(--accent-rose);
}

.alert-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.alert-type {
  font-size: 0.75rem;
  font-weight: 700;
  letter-spacing: 0.5px;
  background: rgba(255,255,255,0.1);
  padding: 0.15rem 0.4rem;
  border-radius: 4px;
}
.alert-card.critical .alert-type {
  color: var(--accent-rose);
  background: var(--accent-rose-glow);
}
.alert-card.high .alert-type, .alert-card.medium .alert-type {
  color: var(--accent-amber);
  background: rgba(245, 158, 11, 0.2);
}

.alert-time {
  font-size: 0.75rem;
  color: var(--text-muted);
}
.alert-body {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-family: var(--font-mono);
  font-size: 0.9rem;
}
.alert-symbol {
  color: var(--text-secondary);
}
.alert-amount {
  color: var(--text-primary);
  font-weight: 600;
}

.no-alerts {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100px;
}
.empty-state {
  color: var(--text-muted);
  font-style: italic;
  font-size: 0.9rem;
}

/* Transition Group Animations */
.list-enter-active,
.list-leave-active {
  transition: all 0.4s ease;
}
.list-enter-from {
  opacity: 0;
  transform: translateX(-30px);
}
.list-leave-to {
  opacity: 0;
  transform: translateX(30px);
}
</style>