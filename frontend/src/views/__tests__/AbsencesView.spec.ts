import { afterEach, beforeEach, describe, it, expect, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'

import AbsencesView from '../AbsencesView.vue'
import { server } from '@/mocks/node'
import { startMockSession } from '@/mocks/handlers'
import { queryPlugin } from '@/test/query'

function mountView() {
  return mount(AbsencesView, { global: { plugins: [queryPlugin()] } })
}

describe('AbsencesView', () => {
  beforeEach(() => {
    // Only Date is faked, so MSW's timers keep working
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(2026, 9, 1))
    startMockSession()
  })
  afterEach(() => vi.useRealTimers())

  it('shows a card per balance and what is coming up', async () => {
    const wrapper = mountView()
    await flushPromises()

    const cards = wrapper.findAll('article').map((a) => a.find('h2').text())
    expect(cards).toEqual(['Vacation', 'Training'])
    expect(wrapper.find('[aria-label="Vacation balance"]').text()).toContain('18.5 days left')
    expect(wrapper.find('#upcoming-title').exists()).toBe(true)
  })

  it('shows the balance of another year', async () => {
    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('select').setValue('2025')
    await flushPromises()

    expect(wrapper.find('[aria-label="Vacation balance"]').text()).toContain('3 days left')
  })

  it('says so when a year has no balance', async () => {
    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('select').setValue('2027')
    await flushPromises()

    expect(wrapper.text()).toContain('No balance for 2027 yet.')
  })

  it('offers a retry when the balance fails to load', async () => {
    server.use(
      http.get('*/api/me/absence-balance', () => new HttpResponse(null, { status: 503 }), {
        once: true,
      }),
    )
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.find('[role="alert"]').text()).toContain("Your balance couldn't be loaded.")

    await wrapper.find('[role="alert"] button').trigger('click')
    await flushPromises()

    expect(wrapper.find('[aria-label="Vacation balance"]').exists()).toBe(true)
  })

  it('opens the request dialog from the header and closes it again', async () => {
    const wrapper = mount(AbsencesView, {
      attachTo: document.body,
      global: { plugins: [queryPlugin()] },
    })
    await flushPromises()

    await wrapper
      .findAll('button')
      .find((b) => b.text() === 'Request absence')!
      .trigger('click')
    await flushPromises()
    expect(wrapper.find('dialog').attributes('open')).toBeDefined()

    await wrapper.find('button[aria-label="Close"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('dialog').exists()).toBe(false)
  })
})
