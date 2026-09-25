import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'

import BalanceCard from '../BalanceCard.vue'
import type { AbsenceBalance } from '@/api/client'

const vacation: AbsenceBalance = {
  type: 'VACATION',
  year: 2026,
  entitledDays: 25,
  carriedOverDays: 3,
  usedDays: 9.5,
  pendingDays: 2,
  remainingDays: 18.5,
}

describe('BalanceCard', () => {
  it('shows the days left, pending days and the breakdown', () => {
    const wrapper = mount(BalanceCard, { props: { balance: vacation, name: 'Vacation' } })

    expect(wrapper.find('h2').text()).toBe('Vacation')
    expect(wrapper.find('.balance__big').text()).toBe('18.5 days left')
    expect(wrapper.find('.badge').text()).toBe('2 pending')
    expect(wrapper.find('.balance__stats').text()).toBe('25 entitled · 3 carried over · 9.5 used')
  })

  it('draws used days as a share of entitled plus carried over', () => {
    const wrapper = mount(BalanceCard, { props: { balance: vacation, name: 'Vacation' } })

    const bar = wrapper.find('[role="progressbar"]')
    expect(bar.attributes('aria-valuenow')).toBe('9.5')
    expect(bar.attributes('aria-valuemax')).toBe('28')
    expect(bar.attributes('aria-label')).toBe('9.5 days of 28 days used')
  })

  it('leaves out the pending badge and carried over when there are none', () => {
    const balance = { ...vacation, carriedOverDays: 0, pendingDays: 0 }
    const wrapper = mount(BalanceCard, { props: { balance, name: 'Vacation' } })

    expect(wrapper.find('.badge').exists()).toBe(false)
    expect(wrapper.find('.balance__stats').text()).toBe('25 entitled · 9.5 used')
  })

  it('shows used days for a type without a remaining balance', () => {
    const parental: AbsenceBalance = { ...vacation, type: 'PARENTAL', usedDays: 1 }
    delete parental.remainingDays
    const wrapper = mount(BalanceCard, { props: { balance: parental, name: 'Parental leave' } })

    expect(wrapper.find('.balance__big').text()).toBe('1 day used')
  })
})
