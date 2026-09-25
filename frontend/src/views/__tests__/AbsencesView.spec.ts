import { afterEach, beforeEach, describe, it, expect, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'
import { createMemoryHistory, createRouter } from 'vue-router'

import AbsencesView from '../AbsencesView.vue'
import { server } from '@/mocks/node'
import { startMockSession } from '@/mocks/handlers'
import { routes } from '@/router'
import { queryPlugin } from '@/test/query'

// The view reads `?request=` (links from notifications), so it needs a router
async function mountView({ path = '/absences', attach = false } = {}) {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push(path)
  const wrapper = mount(AbsencesView, {
    attachTo: attach ? document.body : undefined,
    global: { plugins: [router, queryPlugin()] },
  })
  return { wrapper, router }
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
    const { wrapper } = await mountView()
    await flushPromises()

    const cards = wrapper.findAll('article').map((a) => a.find('h2').text())
    expect(cards).toEqual(['Vacation', 'Training'])
    expect(wrapper.find('[aria-label="Vacation balance"]').text()).toContain('18.5 days left')
    expect(wrapper.find('#upcoming-title').exists()).toBe(true)
  })

  it('shows the balance of another year', async () => {
    const { wrapper } = await mountView()
    await flushPromises()

    await wrapper.find('select').setValue('2025')
    await flushPromises()

    expect(wrapper.find('[aria-label="Vacation balance"]').text()).toContain('3 days left')
  })

  it('says so when a year has no balance', async () => {
    const { wrapper } = await mountView()
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
    const { wrapper } = await mountView()
    await flushPromises()

    expect(wrapper.find('[role="alert"]').text()).toContain("Your balance couldn't be loaded.")

    await wrapper.find('[role="alert"] button').trigger('click')
    await flushPromises()

    expect(wrapper.find('[aria-label="Vacation balance"]').exists()).toBe(true)
  })

  it('opens the request dialog from the header and closes it again', async () => {
    const { wrapper } = await mountView({ attach: true })
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

  it('opens the details from "Coming up"', async () => {
    const { wrapper } = await mountView({ attach: true })
    await flushPromises()

    const item = wrapper.findAll('.upcoming__item').find((b) => b.text().startsWith('12–16 Oct'))!
    // A real button, so it's in the tab order and Enter/Space activate it
    expect(item.element.tagName).toBe('BUTTON')
    ;(item.element as HTMLButtonElement).focus()
    expect(document.activeElement).toBe(item.element)
    await item.trigger('click')
    await flushPromises()

    expect(wrapper.find('dialog').attributes('open')).toBeDefined()
    expect(wrapper.find('.details__range').text()).toBe('12–16 Oct 2026')
  })

  it('cancels a pending absence from its calendar chip', async () => {
    const { wrapper } = await mountView({ attach: true })
    await flushPromises()
    const chip = () => wrapper.find('td[data-date="2026-10-26"] .chip')

    await chip().trigger('click')
    await flushPromises()
    expect(wrapper.find('.details__range').text()).toBe('26–27 Oct 2026')

    const cancel = () =>
      wrapper
        .findAll('dialog button')
        .filter((b) => b.text() === 'Cancel request')
        .pop()!
    await cancel().trigger('click')
    await flushPromises()
    expect(wrapper.findAll('dialog')).toHaveLength(2)

    await cancel().trigger('click')
    await flushPromises()

    expect(wrapper.find('dialog').exists()).toBe(false)
    // Refetched: cancelled absences are hidden, and it's no longer coming up
    expect(chip().exists()).toBe(false)
    expect(wrapper.find('#upcoming-title').element.parentElement!.textContent).not.toContain(
      '26–27 Oct',
    )
  })

  describe('a link from a notification', () => {
    it('opens the details of the linked request and drops the query', async () => {
      const { wrapper, router } = await mountView({ path: '/absences?request=5', attach: true })
      await flushPromises()

      expect(wrapper.find('dialog').attributes('open')).toBeDefined()
      expect(wrapper.find('.details__range').text()).toBe('12–16 Oct 2026')
      expect(router.currentRoute.value.fullPath).toBe('/absences')
    })

    it('opens it when the link is followed while already on the page', async () => {
      const { wrapper, router } = await mountView({ attach: true })
      await flushPromises()
      expect(wrapper.find('dialog').exists()).toBe(false)

      await router.push('/absences?request=5')
      await flushPromises()

      expect(wrapper.find('.details__range').text()).toBe('12–16 Oct 2026')
    })

    it('just shows the page when the request is not loaded', async () => {
      // Request 3 is in September, before "Coming up" and the calendar's October
      const { wrapper, router } = await mountView({ path: '/absences?request=3', attach: true })
      await flushPromises()

      expect(wrapper.find('dialog').exists()).toBe(false)
      expect(router.currentRoute.value.fullPath).toBe('/absences')
    })
  })
})
