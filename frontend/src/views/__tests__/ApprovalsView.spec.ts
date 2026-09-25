import { afterEach, beforeEach, describe, it, expect, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'
import { h } from 'vue'
import { RouterView, createMemoryHistory, createRouter } from 'vue-router'

import { server } from '@/mocks/node'
import { startMockSession } from '@/mocks/handlers'
import { findMockUser } from '@/mocks/data/users'
import { teamAbsenceRequests } from '@/mocks/data/team'
import { routes } from '@/router'
import { queryPlugin } from '@/test/query'

// Through a <RouterView>, since the tab comes from the route's `?tab=`. Attached, so the native
// <dialog> and focus behave as in the browser. The route is lazy, so wait for the page.
async function mountPage(path = '/approvals') {
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

async function mountView() {
  return (await mountPage()).wrapper
}

type Wrapper = Awaited<ReturnType<typeof mountView>>

/** The Absences tab's panel: the Timesheets panel is in the DOM too, just hidden */
const absences = (wrapper: Wrapper) => wrapper.get('#panel-absences')
const names = (wrapper: Wrapper) =>
  absences(wrapper)
    .findAll('tbody th')
    .map((th) => th.text())
const button = (wrapper: Wrapper, label: string) => wrapper.find(`button[aria-label="${label}"]`)

/** Collects the decisions the page POSTs, without replacing the mock's handler */
function recordDecisions() {
  const calls: { url: string; body: unknown }[] = []
  server.use(
    http.post('*/api/team/absence-requests/:id/:decision', async ({ request }) => {
      calls.push({ url: new URL(request.url).pathname, body: await request.clone().json() })
      // Returning nothing falls through to the mock handler
    }),
  )
  return calls
}

describe('ApprovalsView', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(2026, 9, 1))
    startMockSession()
  })
  afterEach(() => vi.useRealTimers())

  it('lists the pending requests, soonest first, with the balance after approval', async () => {
    const wrapper = await mountView()
    await flushPromises()

    expect(names(wrapper)).toEqual(['DP Diogo Pereira', 'CM Carla Mendes', 'BC Bruno Costa'])
    expect(wrapper.find('[role="tab"][aria-selected="true"]').text()).toBe('Absences 3')

    const carla = wrapper
      .findAll('tbody tr')[1]!
      .findAll('td')
      .map((td) => td.text().replace(/\s+/g, ' '))
    expect(carla).toEqual([
      'Vacation',
      '16–18 Nov 2026',
      '3',
      '19 → 16',
      'Visiting family in Madeira',
      'Reject Approve',
    ])
    // Training has no balance in the contract
    const diogo = absences(wrapper).findAll('tbody tr')[0]!.findAll('td')[3]!
    expect(diogo.find('.visually-hidden').text()).toBe('No balance for this type')
    // Bruno's doesn't fit any more
    const bruno = absences(wrapper).findAll('tbody tr')[2]!.findAll('td')[3]!
    expect(bruno.classes()).toContain('overdrawn')
    expect(bruno.text()).toBe('5 → -1 (not enough days)')
  })

  it('treats a null balance like a missing one, as the backend sends it', async () => {
    server.use(
      http.get('*/api/team/absence-requests', () =>
        HttpResponse.json([{ ...teamAbsenceRequests[0]!, remainingDays: null }]),
      ),
    )
    const wrapper = await mountView()
    await flushPromises()

    const balance = absences(wrapper).find('tbody td:nth-of-type(4)')
    expect(balance.find('.visually-hidden').text()).toBe('No balance for this type')
    expect(balance.classes()).not.toContain('overdrawn')
  })

  it('names each row button after the request', async () => {
    const wrapper = await mountView()
    await flushPromises()

    expect(button(wrapper, 'Approve Carla Mendes’s vacation, 16–18 Nov').exists()).toBe(true)
    expect(button(wrapper, 'Reject Diogo Pereira’s training, 10 Nov').exists()).toBe(true)
  })

  it('approves a request and drops its row', async () => {
    const calls = recordDecisions()
    const wrapper = await mountView()
    await flushPromises()

    await button(wrapper, 'Approve Carla Mendes’s vacation, 16–18 Nov').trigger('click')
    await flushPromises()

    expect(calls).toEqual([{ url: '/api/team/absence-requests/502/approve', body: {} }])
    expect(names(wrapper)).toEqual(['DP Diogo Pereira', 'BC Bruno Costa'])
    expect(wrapper.find('[role="tab"][aria-selected="true"]').text()).toBe('Absences 2')
    expect(absences(wrapper).find('.banner').exists()).toBe(false)
  })

  it('removes the row at once and puts it back when approving fails', async () => {
    let respond: () => void = () => {}
    server.use(
      http.post(
        '*/api/team/absence-requests/:id/approve',
        () =>
          new Promise<Response>((resolve) => {
            respond = () => resolve(new HttpResponse(null, { status: 503 }))
          }),
        { once: true },
      ),
    )
    const wrapper = await mountView()
    await flushPromises()

    await button(wrapper, 'Approve Carla Mendes’s vacation, 16–18 Nov').trigger('click')
    await flushPromises()
    // Optimistic: gone before the server answered
    expect(names(wrapper)).toEqual(['DP Diogo Pereira', 'BC Bruno Costa'])

    respond()
    await flushPromises()

    // Back at its old place, with the reason
    expect(names(wrapper)).toEqual(['DP Diogo Pereira', 'CM Carla Mendes', 'BC Bruno Costa'])
    expect(absences(wrapper).find('.banner').text()).toBe(
      'Couldn’t approve Carla Mendes’s request. Something went wrong. Please try again.',
    )
  })

  it('explains a request that no longer fits the balance', async () => {
    const wrapper = await mountView()
    await flushPromises()

    await button(wrapper, 'Approve Bruno Costa’s vacation, 23 Dec – 1 Jan').trigger('click')
    await flushPromises()

    expect(absences(wrapper).find('.banner').text()).toBe(
      'Couldn’t approve Bruno Costa’s request. This request doesn’t fit the balance any more, so it can’t be approved. You can still reject it. Only 5 vacation days left in 2026, but the request needs 6.',
    )
    expect(names(wrapper)).toContain('BC Bruno Costa')
  })

  it('asks for a comment before rejecting', async () => {
    const calls = recordDecisions()
    const wrapper = await mountView()
    await flushPromises()

    await button(wrapper, 'Reject Diogo Pereira’s training, 10 Nov').trigger('click')
    await flushPromises()
    expect(wrapper.find('dialog h2').text()).toBe('Reject Diogo’s training request?')

    await wrapper.find('dialog textarea').setValue('   ')
    await wrapper.find('dialog form').trigger('submit')
    await flushPromises()

    expect(wrapper.find('dialog textarea').attributes('aria-invalid')).toBe('true')
    expect(wrapper.find('dialog .field__error').text()).toBe(
      'Please say why, so they know what to change.',
    )
    expect(calls).toEqual([])
  })

  it('rejects with the comment and closes the dialog', async () => {
    const calls = recordDecisions()
    const wrapper = await mountView()
    await flushPromises()

    await button(wrapper, 'Reject Diogo Pereira’s training, 10 Nov').trigger('click')
    await flushPromises()
    await wrapper.find('dialog textarea').setValue(' Please book it after the release. ')
    await wrapper.find('dialog form').trigger('submit')
    await flushPromises()

    expect(calls).toEqual([
      {
        url: '/api/team/absence-requests/501/reject',
        body: { comment: 'Please book it after the release.' },
      },
    ])
    expect(wrapper.find('dialog').exists()).toBe(false)
    expect(names(wrapper)).toEqual(['CM Carla Mendes', 'BC Bruno Costa'])
  })

  it('shows the server’s comment error under the field', async () => {
    server.use(
      http.post(
        '*/api/team/absence-requests/:id/reject',
        () =>
          HttpResponse.json(
            {
              type: 'about:blank',
              title: 'Bad Request',
              status: 400,
              errors: [{ field: 'comment', message: 'size must be between 1 and 500' }],
            },
            { status: 400 },
          ),
        { once: true },
      ),
    )
    const wrapper = await mountView()
    await flushPromises()

    await button(wrapper, 'Reject Diogo Pereira’s training, 10 Nov').trigger('click')
    await flushPromises()
    await wrapper.find('dialog textarea').setValue('Too long, pretend')
    await wrapper.find('dialog form').trigger('submit')
    await flushPromises()

    expect(wrapper.find('dialog .field__error').text()).toBe('size must be between 1 and 500')
    expect(wrapper.find('dialog [role="alert"]').exists()).toBe(false)
    // Rolled back behind the dialog
    expect(names(wrapper)).toContain('DP Diogo Pereira')
  })

  it('says so when the request was already decided, and refreshes the list', async () => {
    let listCalls = 0
    server.use(
      http.get('*/api/team/absence-requests', () => {
        listCalls++
      }),
      http.post(
        '*/api/team/absence-requests/:id/reject',
        () =>
          HttpResponse.json(
            {
              type: '/problems/absence-not-pending',
              title: 'Conflict',
              status: 409,
              detail: 'The absence request is no longer pending',
            },
            { status: 409 },
          ),
        { once: true },
      ),
    )
    const wrapper = await mountView()
    await flushPromises()
    expect(listCalls).toBe(1)

    await button(wrapper, 'Reject Diogo Pereira’s training, 10 Nov').trigger('click')
    await flushPromises()
    await wrapper.find('dialog textarea').setValue('Release week')
    await wrapper.find('dialog form').trigger('submit')
    await flushPromises()

    expect(wrapper.find('dialog [role="alert"]').text()).toBe(
      'This request was already decided or cancelled.',
    )
    expect(listCalls).toBe(2)
  })

  it('has nothing to approve for someone who approves nobody', async () => {
    startMockSession(findMockUser('carla.mendes@cofinpro.pt')!)
    const wrapper = await mountView()
    await flushPromises()

    expect(absences(wrapper).text()).toContain('Nothing to approve right now.')
    expect(absences(wrapper).find('table').exists()).toBe(false)
    expect(wrapper.find('[role="tab"][aria-selected="true"]').text()).toBe('Absences 0')
  })

  it('offers a retry when the list fails to load', async () => {
    server.use(
      http.get('*/api/team/absence-requests', () => new HttpResponse(null, { status: 503 }), {
        once: true,
      }),
    )
    const wrapper = await mountView()
    await flushPromises()

    expect(absences(wrapper).find('[role="alert"]').text()).toContain(
      "The requests couldn't be loaded.",
    )

    await absences(wrapper).find('[role="alert"] button').trigger('click')
    await flushPromises()

    expect(names(wrapper)).toHaveLength(3)
  })
})
