import { afterEach, beforeEach, describe, it, expect, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'

import RequestAbsenceDialog from '../RequestAbsenceDialog.vue'
import type { NewAbsenceRequest } from '@/api/client'
import { server } from '@/mocks/node'
import { startMockSession } from '@/mocks/handlers'
import { queryKeys } from '@/api/queryKeys'
import { queryPlugin, testQueryClient } from '@/test/query'

async function mountDialog(queryClient = testQueryClient()) {
  const wrapper = mount(RequestAbsenceDialog, {
    attachTo: document.body,
    global: { plugins: [queryPlugin(queryClient)] },
  })
  await flushPromises()
  return wrapper
}
type Wrapper = Awaited<ReturnType<typeof mountDialog>>

/** The input a <label> points at, like a user finds it */
function field(wrapper: Wrapper, label: string) {
  const el = wrapper.findAll('label').find((l) => l.text() === label)!
  return wrapper.find(`#${el.attributes('for')}`)
}
const radio = (wrapper: Wrapper, group: string, option: string) =>
  wrapper
    .findAll('fieldset')
    .find((f) => f.find('legend').text() === group)!
    .findAll('label')
    .find((l) => l.text() === option)!
    .find('input')
const preview = (wrapper: Wrapper) => wrapper.find('.preview').text().replace(/\s+/g, ' ')

describe('RequestAbsenceDialog', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(2026, 9, 1)) // Thursday 1 Oct 2026
    startMockSession()
  })
  afterEach(() => vi.useRealTimers())

  it('opens as a modal with vacation for today and previews one day', async () => {
    const wrapper = await mountDialog()

    expect(wrapper.find('dialog').attributes('open')).toBeDefined()
    expect(wrapper.find('h2').text()).toBe('Request absence')
    expect((field(wrapper, 'Type').element as HTMLSelectElement).value).toBe('VACATION')
    expect((field(wrapper, 'Start date').element as HTMLInputElement).value).toBe('2026-10-01')
    // 18.5 left in the mock balance
    expect(preview(wrapper)).toBe(
      '1 working day Weekends and public holidays excluded · 17.5 days left after approval',
    )
  })

  it('counts working days over weekends and holidays, and limits the day parts', async () => {
    const wrapper = await mountDialog()

    await field(wrapper, 'End date').setValue('2026-10-09')

    // 1, 2, 6, 7, 8, 9 Oct: the weekend and 5 Oct (Republic Day) don't count
    expect(preview(wrapper)).toContain('6 working days')
    expect(radio(wrapper, 'Start', 'Morning').attributes('disabled')).toBeDefined()
    expect(radio(wrapper, 'End', 'Afternoon').attributes('disabled')).toBeDefined()
    expect(radio(wrapper, 'End', 'Morning').attributes('disabled')).toBeUndefined()
  })

  it('makes a single day a half day, with the end following the start', async () => {
    const wrapper = await mountDialog()

    await radio(wrapper, 'Start', 'Afternoon').setValue(true)

    expect((radio(wrapper, 'End', 'Afternoon').element as HTMLInputElement).checked).toBe(true)
    expect(preview(wrapper)).toContain('0.5 working day')
  })

  it('warns and blocks the submit when the range has no working days', async () => {
    const wrapper = await mountDialog()

    await field(wrapper, 'Start date').setValue('2026-10-03')

    expect(preview(wrapper)).toContain('There are no working days in this range.')
    expect(wrapper.find('button[type="submit"]').attributes('disabled')).toBeDefined()
  })

  it('submits the request, refreshes and closes', async () => {
    let sent: NewAbsenceRequest | undefined
    server.use(
      http.post('*/api/me/absence-requests', async ({ request }) => {
        sent = (await request.json()) as NewAbsenceRequest
        return HttpResponse.json(
          { ...sent, id: 7, workingDays: 2, status: 'PENDING', createdAt: '' },
          { status: 201 },
        )
      }),
    )
    const wrapper = await mountDialog()

    await field(wrapper, 'End date').setValue('2026-10-02')
    await field(wrapper, 'Reason (optional)').setValue('  Long weekend  ')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(sent).toEqual({
      type: 'VACATION',
      startDate: '2026-10-01',
      endDate: '2026-10-02',
      startPart: 'FULL',
      endPart: 'FULL',
      reason: 'Long weekend',
    })
    expect(wrapper.emitted('created')?.[0]?.[0]).toMatchObject({ id: 7 })
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('explains an overlap with another absence and refreshes the absences', async () => {
    const queryClient = testQueryClient()
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries')
    const wrapper = await mountDialog(queryClient)

    await field(wrapper, 'Start date').setValue('2026-10-12') // the approved 12–16 Oct
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[role="alert"]').text()).toBe(
      'These days overlap another absence of yours that is pending or approved.',
    )
    expect(wrapper.emitted('close')).toBeUndefined()
    // The overlapping absence may come from another tab: the calendar behind should show it
    expect(invalidate).toHaveBeenCalledWith({ queryKey: queryKeys.absences.all })
  })

  it('warns early about a short balance and shows the backend’s numbers', async () => {
    const wrapper = await mountDialog()

    await field(wrapper, 'Start date').setValue('2026-11-02')
    await field(wrapper, 'End date').setValue('2026-12-31')
    expect(preview(wrapper)).toContain('more than you have left in 2026')

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[role="alert"]').text()).toContain('You don’t have enough days left')
    expect(wrapper.find('[role="alert"]').text()).toContain('Only 18.5 vacation days left in 2026')
  })

  it('shows field errors from the backend under the field', async () => {
    server.use(
      http.post('*/api/me/absence-requests', () =>
        HttpResponse.json(
          {
            type: 'about:blank',
            title: 'Bad Request',
            status: 400,
            errors: [{ field: 'reason', message: 'is too long' }],
          },
          { status: 400, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )
    const wrapper = await mountDialog()

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    const reason = field(wrapper, 'Reason (optional)')
    expect(reason.attributes('aria-invalid')).toBe('true')
    expect(wrapper.find(`#${reason.attributes('aria-describedby')}`).text()).toBe('is too long')
  })

  it('closes without a request on Cancel', async () => {
    const wrapper = await mountDialog()

    await wrapper
      .findAll('button')
      .find((b) => b.text() === 'Cancel')!
      .trigger('click')

    expect(wrapper.emitted('close')).toHaveLength(1)
    expect(wrapper.emitted('created')).toBeUndefined()
  })
})
