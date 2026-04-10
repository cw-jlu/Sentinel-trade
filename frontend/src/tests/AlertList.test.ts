/**
 * Unit tests for AlertList component.
 * Requirements: 3.4, 5.2
 */

import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import AlertList from '../components/AlertList.vue'

const makeAlert = (overrides = {}) => ({
  alertId: 'a1',
  alertType: 'LARGE_ORDER',
  symbol: 'BTCUSDT',
  timestamp: 1704067200000,
  severity: 'HIGH',
  amount: '67685.34',
  ...overrides,
})

describe('AlertList', () => {
  it('renders alerts', () => {
    const wrapper = mount(AlertList, {
      props: { alerts: [makeAlert()] },
    })
    expect(wrapper.text()).toContain('LARGE_ORDER')
    expect(wrapper.text()).toContain('BTCUSDT')
  })

  it('shows no-alerts message when empty', () => {
    const wrapper = mount(AlertList, { props: { alerts: [] } })
    expect(wrapper.text()).toContain('No alerts')
  })

  it('filters by alert type', async () => {
    const alerts = [
      makeAlert({ alertId: 'a1', alertType: 'LARGE_ORDER' }),
      makeAlert({ alertId: 'a2', alertType: 'FLASH_CRASH' }),
    ]
    const wrapper = mount(AlertList, { props: { alerts } })
    const select = wrapper.find('select')
    await select.setValue('LARGE_ORDER')
    const items = wrapper.findAll('.alert-type')
    expect(items.every(i => i.text() === 'LARGE_ORDER')).toBe(true)
  })

  it('sorts alerts by timestamp descending', () => {
    const alerts = [
      makeAlert({ alertId: 'a1', timestamp: 1000 }),
      makeAlert({ alertId: 'a2', timestamp: 3000 }),
      makeAlert({ alertId: 'a3', timestamp: 2000 }),
    ]
    const wrapper = mount(AlertList, { props: { alerts } })
    const items = wrapper.findAll('.alert-item')
    // First item should be the most recent (timestamp 3000)
    expect(items[0].text()).toContain('LARGE_ORDER')
    expect(items).toHaveLength(3)
  })
})
