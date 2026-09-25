import { afterEach, beforeEach, describe, it, expect, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'
import { h } from 'vue'
import { RouterView, createMemoryHistory, createRouter } from 'vue-router'

import { server } from '@/mocks/node'
import { startMockSession } from '@/mocks/handlers'
import { findMockUser } from '@/mocks/data/users'
import { routes } from '@/router'
import { queryPlugin } from '@/test/query'

// FE-7.1: the Timesheets tab of the Approvals page. The mock has Carla's week 37 and Diogo's
// week 38 waiting for Ana (mocks/data/timesheets.ts).
async function mountPage(path = '/approvals?tab=timesheets') {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push(path)
  const wrapper = mount(
    { render: () => h(RouterView) },
    { attachTo: document.body, global: { plugins: [router, queryPlugin()] } },
  )
  await vi.waitFor(() => expect(wrapper.find('[role="tablist"]').exists()).toBe(true))
  await flushPromises()
  return { wrapper, router }
}

type Wrapper = Awaited<ReturnType<typeof mountPage>>['wrapper']

const panel = (wrapper: Wrapper) => wrapper.get('#panel-timesheets')
const squash = (text: string) => text.replace(/\s+/g, ' ').trim()
const names = (wrapper: Wrapper) =>
  panel(wrapper)
    .findAll('tbody th.person-cell')
    .map((th) => squash(th.text()))
const button = (wrapper: Wrapper, label: string) => wrapper.find(`button[aria-label="${label}"]`)
const tab = (wrapper: Wrapper, name: string) => wrapper.get(`#tab-${name}`)

const CARLA = 'Carla Mendes’s week 37, 7–13 Sept'
const DIOGO = 'Diogo Pereira’s week 38, 14–20 Sept'

/** Collects the decisions the page POSTs, without replacing the mock's handler */
function recordDecisions() {
  const calls: { url: string; body: unknown }[] = []
  server.use(
    http.post('*/api/team/timesheets/:id/:decision', async ({ request }) => {
      calls.push({ url: new URL(request.url).pathname, body: await request.clone().json() })
    }),
  )
  return calls
}

const conflict = () =>
  HttpResponse.json(
    {
      type: '/problems/timesheet-not-submitted',
      title: 'Conflict',
      status: 409,
      detail: 'Only submitted timesheets can be decided; this one is approved',
    },
    { status: 409 },
  )

describe('ApprovalsView, Timesheets tab', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(2026, 8, 25))
    startMockSession()
  })
  afterEach(() => vi.useRealTimers())

  it('opens on ?tab=timesheets, with both counts', async () => {
    const { wrapper } = await mountPage()

    expect(tab(wrapper, 'timesheets').attributes('aria-selected')).toBe('true')
    expect(tab(wrapper, 'timesheets').attributes('tabindex')).toBe('0')
    expect(tab(wrapper, 'absences').attributes('aria-selected')).toBe('false')
    expect(tab(wrapper, 'absences').attributes('tabindex')).toBe('-1')
    expect(squash(tab(wrapper, 'absences').text())).toBe('Absences 3')
    expect(squash(tab(wrapper, 'timesheets').text())).toBe('Timesheets 2')
    expect(panel(wrapper).attributes('hidden')).toBeUndefined()
    expect(wrapper.get('#panel-absences').attributes('hidden')).toBeDefined()
  })

  it('switches tabs by click and arrow keys, keeping the tab in the URL', async () => {
    const { wrapper, router } = await mountPage('/approvals')
    expect(tab(wrapper, 'absences').attributes('aria-selected')).toBe('true')

    await tab(wrapper, 'timesheets').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.tab).toBe('timesheets')
    expect(tab(wrapper, 'timesheets').attributes('aria-selected')).toBe('true')

    await tab(wrapper, 'timesheets').trigger('keydown', { key: 'ArrowRight' })
    await flushPromises()
    // Wraps around to the first tab, which gets the focus; the URL loses ?tab=
    expect(router.currentRoute.value.query.tab).toBeUndefined()
    expect(tab(wrapper, 'absences').attributes('aria-selected')).toBe('true')
    expect(document.activeElement).toBe(tab(wrapper, 'absences').element)

    await tab(wrapper, 'absences').trigger('keydown', { key: 'End' })
    await flushPromises()
    expect(tab(wrapper, 'timesheets').attributes('aria-selected')).toBe('true')
  })

  it('lists the submitted weeks, oldest first, with totals and hours per project', async () => {
    const { wrapper } = await mountPage()

    expect(names(wrapper)).toEqual(['CM Carla Mendes', 'DP Diogo Pereira'])
    const diogo = panel(wrapper)
      .findAll('tbody')[1]!
      .find('tr')
      .findAll('td')
      .map((td) => squash(td.text()))
    expect(diogo).toEqual([
      '',
      'Week 38 · 14–20 Sept',
      '40 h',
      'DKB-CORE 32 · TRAINING 8',
      'Reject Approve',
    ])
    expect(button(wrapper, `Approve ${DIOGO}`).exists()).toBe(true)
    expect(button(wrapper, `Reject ${CARLA}`).exists()).toBe(true)
  })

  it('expands a row to the read-only week grid', async () => {
    const { wrapper } = await mountPage()
    const toggle = button(wrapper, `Hours of ${DIOGO}`)
    expect(toggle.attributes('aria-expanded')).toBe('false')
    expect(panel(wrapper).find('table.grid').exists()).toBe(false)

    await toggle.trigger('click')

    expect(toggle.attributes('aria-expanded')).toBe('true')
    const grid = panel(wrapper).get(`#${toggle.attributes('aria-controls')} table.grid`)
    // Read-only: hours as text, no inputs, no add or remove
    expect(grid.findAll('input')).toHaveLength(0)
    expect(grid.text()).not.toContain('Add project')
    // :scope, since the outer table's <tbody> is an ancestor of every cell of the grid too
    expect(grid.findAll(':scope > tbody th').map((th) => squash(th.text()))).toEqual([
      'DKB-CORE, DKB · DKB core banking',
      'TRAINING, Internal · Training',
    ])
    expect(grid.findAll(':scope > tfoot td').map((td) => td.text())).toEqual([
      '8',
      '8',
      '8',
      '8',
      '8',
      '–',
      '–',
      '40',
    ])

    await toggle.trigger('click')
    expect(panel(wrapper).find('table.grid').exists()).toBe(false)
  })

  it('shows a week’s descriptions from the grid', async () => {
    const { wrapper } = await mountPage()
    await button(wrapper, `Hours of ${DIOGO}`).trigger('click')

    await button(wrapper, 'Descriptions for TRAINING (1)').trigger('click')
    await flushPromises()

    expect(squash(wrapper.get('dialog dl').text())).toBe('Friday 18 Sept · 8 hAWS course')
  })

  it('approves a week and drops its row', async () => {
    const calls = recordDecisions()
    const { wrapper } = await mountPage()

    await button(wrapper, `Approve ${CARLA}`).trigger('click')
    await flushPromises()

    expect(calls).toEqual([{ url: '/api/team/timesheets/11/approve', body: {} }])
    expect(names(wrapper)).toEqual(['DP Diogo Pereira'])
    expect(squash(tab(wrapper, 'timesheets').text())).toBe('Timesheets 1')
    expect(panel(wrapper).find('.banner').exists()).toBe(false)
  })

  it('removes the row at once and puts it back when approving fails', async () => {
    let respond: () => void = () => {}
    server.use(
      http.post(
        '*/api/team/timesheets/:id/approve',
        () =>
          new Promise<Response>((resolve) => {
            respond = () => resolve(new HttpResponse(null, { status: 503 }))
          }),
        { once: true },
      ),
    )
    const { wrapper } = await mountPage()

    await button(wrapper, `Approve ${CARLA}`).trigger('click')
    await flushPromises()
    expect(names(wrapper)).toEqual(['DP Diogo Pereira'])

    respond()
    await flushPromises()

    expect(names(wrapper)).toEqual(['CM Carla Mendes', 'DP Diogo Pereira'])
    expect(panel(wrapper).get('.banner').text()).toBe(
      'Couldn’t approve Carla Mendes’s week 37. Something went wrong. Please try again.',
    )
  })

  it('says so when the week was already decided, and refreshes the list', async () => {
    let listCalls = 0
    server.use(
      http.get('*/api/team/timesheets', () => {
        listCalls++
      }),
      http.post('*/api/team/timesheets/:id/approve', conflict, { once: true }),
    )
    const { wrapper } = await mountPage()
    expect(listCalls).toBe(1)

    await button(wrapper, `Approve ${DIOGO}`).trigger('click')
    await flushPromises()

    expect(panel(wrapper).get('.banner').text()).toBe(
      'Couldn’t approve Diogo Pereira’s week 38. This week was already approved or rejected.',
    )
    expect(listCalls).toBe(2)
  })

  it('asks for a comment before rejecting', async () => {
    const calls = recordDecisions()
    const { wrapper } = await mountPage()

    await button(wrapper, `Reject ${DIOGO}`).trigger('click')
    await flushPromises()
    expect(wrapper.get('dialog h2').text()).toBe('Reject Diogo’s week 38?')

    await wrapper.get('dialog form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('dialog textarea').attributes('aria-invalid')).toBe('true')
    expect(wrapper.get('dialog .field__error').text()).toBe(
      'Please say why, so they know what to change.',
    )
    expect(calls).toEqual([])
  })

  it('rejects with the comment and closes the dialog', async () => {
    const calls = recordDecisions()
    const { wrapper } = await mountPage()

    await button(wrapper, `Reject ${DIOGO}`).trigger('click')
    await flushPromises()
    await wrapper.get('dialog textarea').setValue(' Please book Friday on DKB-CORE ')
    await wrapper.get('dialog form').trigger('submit')
    await flushPromises()

    expect(calls).toEqual([
      {
        url: '/api/team/timesheets/12/reject',
        body: { comment: 'Please book Friday on DKB-CORE' },
      },
    ])
    expect(wrapper.find('dialog').exists()).toBe(false)
    expect(names(wrapper)).toEqual(['CM Carla Mendes'])
  })

  it('shows a 409 inside the reject dialog and refreshes the list', async () => {
    let listCalls = 0
    server.use(
      http.get('*/api/team/timesheets', () => {
        listCalls++
      }),
      http.post('*/api/team/timesheets/:id/reject', conflict, { once: true }),
    )
    const { wrapper } = await mountPage()

    await button(wrapper, `Reject ${DIOGO}`).trigger('click')
    await flushPromises()
    await wrapper.get('dialog textarea').setValue('Missing Friday')
    await wrapper.get('dialog form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('dialog [role="alert"]').text()).toBe(
      'This week was already approved or rejected.',
    )
    expect(listCalls).toBe(2)
  })

  it('has nothing to approve for someone who approves nobody', async () => {
    startMockSession(findMockUser('carla.mendes@cofinpro.pt')!)
    const { wrapper } = await mountPage()

    expect(panel(wrapper).text()).toContain('No timesheets to approve right now.')
    expect(panel(wrapper).find('table').exists()).toBe(false)
    expect(squash(tab(wrapper, 'timesheets').text())).toBe('Timesheets 0')
  })

  it('offers a retry when the list fails to load', async () => {
    server.use(
      http.get('*/api/team/timesheets', () => new HttpResponse(null, { status: 503 }), {
        once: true,
      }),
    )
    const { wrapper } = await mountPage()

    expect(panel(wrapper).get('[role="alert"]').text()).toContain(
      "The timesheets couldn't be loaded.",
    )
    await panel(wrapper).get('[role="alert"] button').trigger('click')
    await flushPromises()

    expect(names(wrapper)).toHaveLength(2)
  })
})
