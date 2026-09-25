import { afterEach, beforeEach, describe, it, expect, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { http, HttpResponse } from 'msw'

import AppHeader from '../AppHeader.vue'
import { routes } from '@/router'
import { queryKeys } from '@/api/queryKeys'
import type { CurrentUser } from '@/api/client'
import { server } from '@/mocks/node'
import { mockUser } from '@/mocks/handlers'
import { queryPlugin, testQueryClient } from '@/test/query'

async function mountHeader(user: Partial<CurrentUser> = {}) {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push('/absences')
  const queryClient = testQueryClient()
  queryClient.setQueryData(queryKeys.me, { ...mockUser, ...user })
  const wrapper = mount(AppHeader, { global: { plugins: [router, queryPlugin(queryClient)] } })
  return { wrapper, router, queryClient }
}

const navLabels = (wrapper: Awaited<ReturnType<typeof mountHeader>>['wrapper']) =>
  wrapper.findAll('nav a').map((a) => a.text().replace('(opens in a new tab)', '').trim())

describe('AppHeader', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_TRAININGS_URL', 'https://trainings.example.com')
    vi.stubEnv('VITE_SEATS_URL', 'https://seats.example.com')
  })
  afterEach(() => vi.unstubAllEnvs())

  it('shows the user with initials, level and client', async () => {
    const { wrapper } = await mountHeader({ name: 'Ana Silva', level: 'EXPERT', client: 'DEKA' })

    expect(wrapper.find('.user__avatar').text()).toBe('AS')
    expect(wrapper.find('.user__name').text()).toBe('Ana Silva')
    expect(wrapper.find('.user__role').text()).toBe('Expert · Deka')
  })

  it('shows Approvals to team leads and Admin to admins only', async () => {
    const employee = await mountHeader({ isTeamLead: false, isAdmin: false })
    expect(navLabels(employee.wrapper)).toEqual(['Absences', 'Timesheets', 'Trainings', 'Seats'])

    const lead = await mountHeader({ isTeamLead: true, isAdmin: false })
    expect(navLabels(lead.wrapper)).toContain('Approvals')
    expect(navLabels(lead.wrapper)).not.toContain('Admin')

    const admin = await mountHeader({ isTeamLead: false, isAdmin: true })
    expect(navLabels(admin.wrapper)).toContain('Admin')
    expect(navLabels(admin.wrapper)).not.toContain('Approvals')
  })

  it('marks the current page and opens external apps in a new tab', async () => {
    const { wrapper } = await mountHeader()

    // RouterLink marks the current page for screen readers too
    expect(wrapper.find('nav [aria-current="page"]').text()).toBe('Absences')
    const trainings = wrapper.findAll('nav a').find((a) => a.text().startsWith('Trainings'))!
    expect(trainings.attributes('target')).toBe('_blank')
    expect(trainings.attributes('rel')).toContain('noopener')
  })

  it('leaves out an external link without a configured URL', async () => {
    vi.stubEnv('VITE_SEATS_URL', '')
    const { wrapper } = await mountHeader()

    expect(navLabels(wrapper)).not.toContain('Seats')
    expect(navLabels(wrapper)).toContain('Trainings')
  })

  it('says so when logging out fails, and stays', async () => {
    server.use(http.post('*/api/auth/logout', () => new HttpResponse(null, { status: 503 })))
    const { wrapper, router } = await mountHeader()

    await wrapper.find('button[aria-label="Log out"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[role="alert"]').text()).toBe("Couldn't log out. Try again.")
    expect(router.currentRoute.value.name).toBe('absences')
  })

  it('logs out, clears the cache and goes to the login page', async () => {
    let loggedOut = false
    server.use(
      http.post('*/api/auth/logout', () => {
        loggedOut = true
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const { wrapper, router, queryClient } = await mountHeader()

    await wrapper.find('button[aria-label="Log out"]').trigger('click')
    await flushPromises()

    expect(loggedOut).toBe(true)
    expect(queryClient.getQueryData(queryKeys.me)).toBeUndefined()
    // The login page is lazy-loaded, which flushPromises() doesn't wait for
    await vi.waitFor(() => expect(router.currentRoute.value.name).toBe('login'))
  })
})
