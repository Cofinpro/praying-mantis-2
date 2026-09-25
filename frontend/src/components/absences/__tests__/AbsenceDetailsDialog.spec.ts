import { afterEach, beforeEach, describe, it, expect, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'

import AbsenceDetailsDialog from '../AbsenceDetailsDialog.vue'
import type { AbsenceRequest, Problem } from '@/api/client'
import { queryKeys } from '@/api/queryKeys'
import { server } from '@/mocks/node'
import { startMockSession } from '@/mocks/handlers'
import { absenceRequests, absenceTypes } from '@/mocks/data/absences'
import { queryPlugin, testQueryClient } from '@/test/query'

const byDate = (startDate: string) => absenceRequests.find((r) => r.startDate === startDate)!
const pending = byDate('2026-10-26') // Vacation 26–27 Oct, pending
const approved = byDate('2026-10-12') // Vacation 12–16 Oct, approved
const sick = byDate('2026-10-08') // Sick 8 Oct, approved, no approver
const rejected = byDate('2026-09-14') // Vacation 14–15 Sep, rejected with a comment

async function mountDetails(request: AbsenceRequest, queryClient = testQueryClient()) {
  const wrapper = mount(AbsenceDetailsDialog, {
    props: { request, types: absenceTypes },
    attachTo: document.body,
    global: { plugins: [queryPlugin(queryClient)] },
  })
  await flushPromises()
  return wrapper
}
type Wrapper = Awaited<ReturnType<typeof mountDetails>>

const rows = (wrapper: Wrapper) =>
  Object.fromEntries(
    wrapper.findAll('.details__row').map((row) => [row.find('dt').text(), row.find('dd').text()]),
  )
const button = (wrapper: Wrapper, text: string, scope = 'dialog') =>
  wrapper
    .findAll(`${scope} button`)
    .filter((b) => b.text() === text)
    .pop()
const confirmation = (wrapper: Wrapper) => wrapper.findAll('dialog')[1]

describe('AbsenceDetailsDialog', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(2026, 9, 1)) // Thursday 1 Oct 2026
    startMockSession()
  })
  afterEach(() => vi.useRealTimers())

  it('shows the request with its dates, status and details', async () => {
    const wrapper = await mountDetails(pending)

    expect(wrapper.find('dialog').attributes('open')).toBeDefined()
    expect(wrapper.find('h2').text()).toBe('Absence details')
    expect(wrapper.find('.details__type').text()).toBe('Vacation Pending')
    expect(wrapper.find('.details__range').text()).toBe('26–27 Oct 2026')
    expect(wrapper.find('.details__subtitle').text()).toBe('Mon – Tue · 2 working days')
    expect(rows(wrapper)).toEqual({
      Start: '26 Oct 2026 · Full day',
      End: '27 Oct 2026 · Full day',
      Approver: 'Alex Admin',
      // en-GB abbreviates September as "Sept"
      Requested: '1 Sept 2026',
      Reason: 'Long weekend in Lisbon',
    })
    expect(wrapper.find('.details__note').exists()).toBe(true)
  })

  it('leaves out rows without a value and shows the approver’s comment', async () => {
    const withNulls = { ...sick, approver: null, reason: null } as unknown as AbsenceRequest
    expect(Object.keys(rows(await mountDetails(withNulls)))).toEqual(['Start', 'End', 'Requested'])

    const wrapper = await mountDetails(rejected)
    expect(rows(wrapper).Comment).toBe('Release week')
    expect(wrapper.find('.details__type').text()).toBe('Vacation Rejected')
    // The cancel rule doesn't apply to a closed request
    expect(wrapper.find('.details__note').exists()).toBe(false)
  })

  it('offers "Cancel request" only while the request can be cancelled', async () => {
    expect(button(await mountDetails(pending), 'Cancel request')).toBeDefined()
    expect(button(await mountDetails(approved), 'Cancel request')).toBeDefined()
    expect(button(await mountDetails(rejected), 'Cancel request')).toBeUndefined()

    // An approved absence that has started
    vi.setSystemTime(new Date(2026, 9, 12))
    expect(button(await mountDetails(approved), 'Cancel request')).toBeUndefined()
    // A pending one still can be
    expect(button(await mountDetails(pending), 'Cancel request')).toBeDefined()
  })

  it('asks for confirmation and "Keep request" closes only the confirmation', async () => {
    const wrapper = await mountDetails(pending)

    await button(wrapper, 'Cancel request')!.trigger('click')
    await flushPromises()

    const confirm = confirmation(wrapper)!
    expect(confirm.attributes('open')).toBeDefined()
    expect(confirm.find('h2').text()).toBe('Cancel this request?')
    expect(document.getElementById(confirm.attributes('aria-labelledby')!)?.textContent).toBe(
      'Cancel this request?',
    )
    expect(confirm.find('p').text().replace(/\s+/g, ' ')).toBe(
      'Vacation · 26–27 Oct 2026 · 2 days. The days go back to your balance. If the request was already approved, your team lead is notified.',
    )

    await button(wrapper, 'Keep request')!.trigger('click')
    await flushPromises()

    expect(wrapper.findAll('dialog')).toHaveLength(1)
    expect(wrapper.find('dialog').attributes('open')).toBeDefined()
    expect(wrapper.emitted('close')).toBeUndefined()
  })

  it('cancels the request, refreshes the absences and closes both dialogs', async () => {
    const queryClient = testQueryClient()
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries')
    let cancelledId: string | undefined
    server.events.on('request:start', ({ request }) => {
      const match = request.url.match(/absence-requests\/(\d+)\/cancel$/)
      if (match && request.method === 'POST') cancelledId = match[1]
    })
    const wrapper = await mountDetails(pending, queryClient)

    await button(wrapper, 'Cancel request')!.trigger('click')
    await flushPromises()
    await button(wrapper, 'Cancel request', 'dialog + dialog')!.trigger('click')
    await flushPromises()

    server.events.removeAllListeners()
    expect(cancelledId).toBe(String(pending.id))
    expect(invalidate).toHaveBeenCalledWith({ queryKey: queryKeys.absences.all })
    expect(wrapper.emitted('close')).toHaveLength(1)
    expect(wrapper.findAll('dialog')).toHaveLength(1)
    expect(wrapper.find('dialog').attributes('open')).toBeUndefined()
  })

  it('explains a 409 in the confirmation and keeps it open', async () => {
    server.use(
      http.post(
        '*/api/me/absence-requests/:id/cancel',
        () =>
          HttpResponse.json<Problem>(
            {
              type: '/problems/absence-not-cancellable',
              title: 'Conflict',
              status: 409,
              detail: 'The absence can no longer be cancelled',
            },
            { status: 409, headers: { 'Content-Type': 'application/problem+json' } },
          ),
        { once: true },
      ),
    )
    const wrapper = await mountDetails(approved)

    await button(wrapper, 'Cancel request')!.trigger('click')
    await flushPromises()
    await button(wrapper, 'Cancel request', 'dialog + dialog')!.trigger('click')
    await flushPromises()

    const confirm = confirmation(wrapper)!
    expect(confirm.find('[role="alert"]').text()).toBe('This absence can’t be cancelled any more.')
    expect(confirm.attributes('open')).toBeDefined()
    expect(wrapper.emitted('close')).toBeUndefined()
  })

  it('shows a generic message when the request no longer exists', async () => {
    const gone = { ...pending, id: 999 }
    const wrapper = await mountDetails(gone)

    await button(wrapper, 'Cancel request')!.trigger('click')
    await flushPromises()
    await button(wrapper, 'Cancel request', 'dialog + dialog')!.trigger('click')
    await flushPromises()

    expect(confirmation(wrapper)!.find('[role="alert"]').text()).toBe(
      'Something went wrong. Please try again.',
    )
  })
})
