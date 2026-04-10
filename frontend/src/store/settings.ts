import { ref } from 'vue'

/**
 * Simple reactive settings store for the frontend.
 * Defers to local storage for persistence.
 */

const STORAGE_KEY = 'sentinel_settings'

interface Settings {
  useLocalTime: boolean
}

// Load initial state
const saved = localStorage.getItem(STORAGE_KEY)
const initial: Settings = saved ? JSON.parse(saved) : { useLocalTime: true }

export const useLocalTime = ref(initial.useLocalTime)

/**
 * Toggle the timezone preference.
 */
export function toggleTimezone() {
  useLocalTime.value = !useLocalTime.value
  saveSettings()
}

function saveSettings() {
  localStorage.setItem(STORAGE_KEY, JSON.stringify({
    useLocalTime: useLocalTime.value
  }))
}
