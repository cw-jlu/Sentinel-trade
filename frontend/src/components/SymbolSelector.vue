<template>
  <div class="symbol-selector">
    <label for="symbol-select">Trading Pair</label>
    <select id="symbol-select" class="premium-select" :value="modelValue" @change="onChange">
      <option v-for="s in symbols" :key="s" :value="s">{{ s }}</option>
    </select>
  </div>
</template>

<script setup lang="ts">
const props = defineProps<{ modelValue: string }>()
const emit = defineEmits<{ (e: 'update:modelValue', value: string): void }>()

const symbols = ['BTCUSDT', 'ETHUSDT', 'BNBUSDT', 'SOLUSDT']

function onChange(event: Event) {
  emit('update:modelValue', (event.target as HTMLSelectElement).value)
}
</script>

<style scoped>
.symbol-selector {
  display: flex;
  align-items: center;
  gap: 0.8rem;
}
.symbol-selector label {
  color: var(--text-secondary);
  font-size: 0.85rem;
  letter-spacing: 0.5px;
  text-transform: uppercase;
  font-weight: 500;
}
.premium-select {
  background: rgba(0, 0, 0, 0.3);
  color: var(--text-primary);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
  padding: 0.4rem 1rem;
  font-size: 0.9rem;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--transition-fast);
  appearance: none;
  background-image: url("data:image/svg+xml;charset=UTF-8,%3csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%2394a3b8' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3e%3cpolyline points='6 9 12 15 18 9'%3e%3c/polyline%3e%3c/svg%3e");
  background-repeat: no-repeat;
  background-position: right 0.7rem center;
  background-size: 1em;
  padding-right: 2.5rem;
}
.premium-select:hover {
  border-color: var(--accent-indigo);
  background-color: rgba(0, 0, 0, 0.5);
}
.premium-select:focus {
  border-color: var(--accent-indigo);
  box-shadow: 0 0 0 2px rgba(99, 102, 241, 0.2);
}
.premium-select option {
  background: var(--bg-dark);
  color: var(--text-primary);
}
</style>
