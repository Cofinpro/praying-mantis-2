import { afterEach, beforeEach, describe, it, expect, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { focusManager, QueryClient } from '@tanstack/vue-query'
import { http, HttpResponse } from 'msw'

import NotificationBell from '../NotificationBell.vue'
import { routes } from '@/router'
import type { AppNotification } from '@/api/client'
import { server } from '@/mocks/node'
import { setMockNotifications, startMockSession } from '@/mocks/handlers'
import { mockNotifications } from '@/mocks/data/notifications'
import { queryPlugin, testQueryClient } from '@/test/query'

async function mountBell(queryClient: QueryClient = testQueryClient()) {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push('/absences')
  // Attached, so focus and clicks outside behave as in a page
  const wrapper = mount(NotificationBell, {
    attachTo: document.body,
    global: { plugins: [router, queryPlugin(queryClient)] },
  })
  await flushPromises()
  return { wrapper, router }
}

type Wrapper = Awaited<ReturnType<typeof mountBell>>['wrapper']

const toggle = (wrapper: Wrapper) => wrapper.find('button[aria-expanded]')
const panel = (wrapper: Wrapper) => wrapper.find('section')

async function openPanel(wrapper: Wrapper) {
  await toggle(wrapper).trigger('click')
  await flushPromises()
}

const unread = (id: number, extra: Partial<AppNotification> = {}): AppNotification => ({
  id,
  type: 'ABSENCE_REQUESTED',
  message: `Notification ${id}`,
  link: '/approvals',
  createdAt: new Date().toISOString(),
  ...extra,
})

describe('NotificationBell', () => {
  beforeEach(() => startMockSession())
  afterEach(() => vi.useRealTimers())

  it('shows the unread count in the badge and the label', async () => {
    const { wrapper } = await mountBell()

    expect(wrapper.find('.bell__badge').text()).toBe('3')
    expect(toggle(wrapper).attributes('aria-label')).toBe('Notifications, 3 unread')
    expect(toggle(wrapper).attributes('aria-expanded')).toBe('false')
    expect(panel(wrapper).isVisible()).toBe(false)
  })

  it('caps the badge at 9+ and hides it with nothing unread', async () => {
    setMockNotifications(Array.from({ length: 12 }, (_, i) => unread(i + 1)))
    const many = await mountBell()
    expect(many.wrapper.find('.bell__badge').text()).toBe('9+')
    expect(toggle(many.wrapper).attributes('aria-label')).toBe('Notifications, 12 unread')

    setMockNotifications([unread(1, { readAt: new Date().toISOString() })])
    const none = await mountBell()
    expect(none.wrapper.find('.bell__badge').exists()).toBe(false)
    expect(toggle(none.wrapper).attributes('aria-label')).toBe('Notifications')
  })

  it('polls the unread count every 30 seconds', async () => {
    // Only setInterval is faked: TanStack Query polls with it, MSW needs the real setTimeout
    vi.useFakeTimers({ toFake: ['setInterval'] })
    const { wrapper } = await mountBell()
    expect(wrapper.find('.bell__badge').text()).toBe('3')

    setMockNotifications([...mockNotifications(), unread(200)])
    vi.advanceTimersByTime(29_000)
    await flushPromises()
    expect(wrapper.find('.bell__badge').text()).toBe('3')

    vi.advanceTimersByTime(1_000)
    await flushPromises()
    expect(wrapper.find('.bell__badge').text()).toBe('4')
  })

  it('refetches the unread count when the tab regains focus', async () => {
    const { wrapper } = await mountBell()

    setMockNotifications([unread(1), unread(2), unread(3), unread(4), unread(5)])
    focusManager.setFocused(false)
    focusManager.setFocused(true)
    await flushPromises()
    focusManager.setFocused(undefined)

    expect(wrapper.find('.bell__badge').text()).toBe('5')
  })

  it('refreshes absences and approvals when a new notification arrives', async () => {
    const queryClient = testQueryClient()
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries')
    await mountBell(queryClient)
    // The first count is no news
    expect(invalidate).not.toHaveBeenCalled()

    setMockNotifications([...mockNotifications(), unread(200)])
    focusManager.setFocused(false)
    focusManager.setFocused(true)
    await flushPromises()
    focusManager.setFocused(undefined)

    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['team'] })
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['absences'] })
  })

  it('refreshes nothing when the count goes down', async () => {
    const queryClient = testQueryClient()
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries')
    await mountBell(queryClient)

    setMockNotifications([unread(1)])
    focusManager.setFocused(false)
    focusManager.setFocused(true)
    await flushPromises()
    focusManager.setFocused(undefined)

    expect(invalidate).not.toHaveBeenCalled()
  })

  it('lists the newest notifications when opened, unread ones highlighted', async () => {
    const { wrapper } = await mountBell()
    await openPanel(wrapper)

    expect(toggle(wrapper).attributes('aria-expanded')).toBe('true')
    expect(toggle(wrapper).attributes('aria-controls')).toBe(panel(wrapper).attributes('id'))
    expect(panel(wrapper).isVisible()).toBe(true)
    const items = wrapper.findAll('.item')
    expect(items).toHaveLength(6)
    expect(items[0]!.text()).toContain('Carla Mendes requested 5 days of vacation (2–6 Nov)')
    expect(items[0]!.find('.item__time').text()).toBe('2 min ago, unread')
    expect(items[0]!.find('.item__icon--primary').exists()).toBe(true)
    expect(items[2]!.find('.item__time').text()).toBe('Yesterday, unread')
    expect(items[3]!.find('.item__time').text()).toBe('3 days ago')
    expect(items.map((i) => i.classes('item--unread'))).toEqual([
      true,
      true,
      true,
      false,
      false,
      false,
    ])
    expect(items[3]!.find('.item__icon--danger').exists()).toBe(true)
  })

  it('closes with Escape and gives focus back to the bell', async () => {
    const { wrapper } = await mountBell()
    await openPanel(wrapper)
    ;(wrapper.find('.item').element as HTMLElement).focus()

    await wrapper.find('.item').trigger('keydown', { key: 'Escape' })

    expect(panel(wrapper).isVisible()).toBe(false)
    expect(toggle(wrapper).attributes('aria-expanded')).toBe('false')
    expect(document.activeElement).toBe(toggle(wrapper).element)
  })

  it('closes on a click outside, but not on a click inside', async () => {
    const { wrapper } = await mountBell()
    await openPanel(wrapper)

    await wrapper.find('.panel__title').trigger('pointerdown')
    expect(panel(wrapper).isVisible()).toBe(true)

    document.body.dispatchEvent(new Event('pointerdown', { bubbles: true }))
    await flushPromises()
    expect(panel(wrapper).isVisible()).toBe(false)
  })

  it('toggles with the bell', async () => {
    const { wrapper } = await mountBell()
    await openPanel(wrapper)
    await openPanel(wrapper)

    expect(panel(wrapper).isVisible()).toBe(false)
  })

  it('marks a notification read and goes to its link when clicked', async () => {
    let marked: string | undefined
    server.events.on('request:start', ({ request }) => {
      if (request.method === 'POST') {
        marked = new URL(request.url).pathname
      }
    })
    const { wrapper, router } = await mountBell()
    await openPanel(wrapper)

    await wrapper.find('.item').trigger('click')
    await flushPromises()
    server.events.removeAllListeners()

    expect(marked).toBe('/api/me/notifications/106/read')
    expect(panel(wrapper).isVisible()).toBe(false)
    await vi.waitFor(() => expect(router.currentRoute.value.fullPath).toBe('/approvals'))
    expect(wrapper.find('.bell__badge').text()).toBe('2')
  })

  it('goes to the link of a read notification without marking it again', async () => {
    const requests: string[] = []
    server.events.on('request:start', ({ request }) => {
      requests.push(`${request.method} ${new URL(request.url).pathname}`)
    })
    const { wrapper, router } = await mountBell()
    await openPanel(wrapper)

    await wrapper.findAll('.item')[3]!.trigger('click')
    await flushPromises()
    server.events.removeAllListeners()

    expect(requests.filter((r) => r.startsWith('POST'))).toEqual([])
    await vi.waitFor(() =>
      expect(router.currentRoute.value.fullPath).toBe('/timesheets?week=2026-10-05'),
    )
  })

  it('never follows a link that leaves the app', async () => {
    setMockNotifications([unread(1, { link: '//evil.example.com/phish' })])
    const { wrapper, router } = await mountBell()
    await openPanel(wrapper)

    await wrapper.find('.item').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/absences')
    // It's still marked read and the panel closes
    expect(wrapper.find('.bell__badge').exists()).toBe(false)
    expect(panel(wrapper).isVisible()).toBe(false)
  })

  it('marks all read', async () => {
    const { wrapper } = await mountBell()
    await openPanel(wrapper)

    await wrapper.find('.panel__mark-all').trigger('click')
    await flushPromises()

    expect(wrapper.find('.bell__badge').exists()).toBe(false)
    expect(wrapper.findAll('.item--unread')).toHaveLength(0)
    expect(wrapper.findAll('.item')).toHaveLength(6)
    expect(wrapper.find('.panel__mark-all').attributes('disabled')).toBeDefined()
  })

  it('says so when there are no notifications', async () => {
    setMockNotifications([])
    const { wrapper } = await mountBell()
    await openPanel(wrapper)

    expect(panel(wrapper).text()).toContain("You're all caught up.")
    expect(wrapper.find('.panel__mark-all').attributes('disabled')).toBeDefined()
  })

  it('offers a retry when the list fails to load', async () => {
    server.use(
      http.get('*/api/me/notifications', () => new HttpResponse(null, { status: 503 }), {
        once: true,
      }),
    )
    const { wrapper } = await mountBell()
    await openPanel(wrapper)

    expect(wrapper.find('[role="alert"]').text()).toContain("Couldn't load notifications.")

    await wrapper.find('.panel__retry').trigger('click')
    await flushPromises()

    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.findAll('.item')).toHaveLength(6)
  })
})
