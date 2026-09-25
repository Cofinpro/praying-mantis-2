import { afterEach, beforeEach, describe, it, expect, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'

import AbsenceCalendar from '../AbsenceCalendar.vue'
import type { AbsenceRequest } from '@/api/client'
import { server } from '@/mocks/node'
import { startMockSession } from '@/mocks/handlers'
import { absenceTypes } from '@/mocks/data/absences'
import { queryPlugin } from '@/test/query'

async function mountCalendar() {
  const wrapper = mount(AbsenceCalendar, {
    props: { types: absenceTypes },
    global: { plugins: [queryPlugin()] },
  })
  await flushPromises()
  return wrapper
}

const cell = (wrapper: Awaited<ReturnType<typeof mountCalendar>>, date: string) =>
  wrapper.find(`td[data-date="${date}"]`)

describe('AbsenceCalendar', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(2026, 9, 1))
    startMockSession()
  })
  afterEach(() => vi.useRealTimers())

  it('shows the current month as whole weeks from Monday', async () => {
    const wrapper = await mountCalendar()

    expect(wrapper.find('h2').text()).toBe('October 2026')
    expect(wrapper.findAll('th').map((th) => th.text())).toEqual([
      'Mon',
      'Tue',
      'Wed',
      'Thu',
      'Fri',
      'Sat',
      'Sun',
    ])
    const dates = wrapper.findAll('td').map((td) => td.attributes('data-date'))
    expect(dates[0]).toBe('2026-09-28')
    expect(dates[dates.length - 1]).toBe('2026-11-01')
    expect(cell(wrapper, '2026-09-28').classes()).toContain('day--outside')
    expect(cell(wrapper, '2026-10-01').classes()).toContain('day--today')
  })

  it('marks weekends and public holidays', async () => {
    const wrapper = await mountCalendar()

    expect(cell(wrapper, '2026-10-10').classes()).toContain('day--weekend')
    expect(cell(wrapper, '2026-10-05').classes()).toContain('day--holiday')
    expect(cell(wrapper, '2026-10-05').text()).toContain('Republic Day')
  })

  it('shows absences by type, with the label on the first day only', async () => {
    const wrapper = await mountCalendar()

    const first = cell(wrapper, '2026-10-12').find('.chip')
    expect(first.find('[aria-hidden="true"]').text()).toBe('Vacation')
    expect(first.find('.visually-hidden').text()).toBe('Vacation, approved, 12–16 Oct, 5 days')
    expect(cell(wrapper, '2026-10-13').find('.chip [aria-hidden="true"]').exists()).toBe(false)
    expect(cell(wrapper, '2026-10-21').find('.chip').text()).toContain('Training')
  })

  it('shows pending requests as pending and half days as half chips', async () => {
    const wrapper = await mountCalendar()

    const pending = cell(wrapper, '2026-10-26').find('.chip')
    expect(pending.classes()).toContain('chip--pending')
    expect(pending.find('[aria-hidden="true"]').text()).toBe('Pending')

    const half = cell(wrapper, '2026-10-30').find('.chip')
    expect(half.classes()).toContain('chip--afternoon')
    expect(half.find('[aria-hidden="true"]').text()).toBe('PM')
  })

  it('hides rejected and cancelled requests until asked', async () => {
    const rejected: AbsenceRequest = {
      id: 99,
      type: 'VACATION',
      startDate: '2026-10-19',
      endDate: '2026-10-19',
      startPart: 'FULL',
      endPart: 'FULL',
      workingDays: 1,
      status: 'REJECTED',
      createdAt: '2026-09-01T09:00:00Z',
    }
    server.use(http.get('*/api/me/absence-requests', () => HttpResponse.json([rejected])))
    const wrapper = await mountCalendar()

    expect(cell(wrapper, '2026-10-19').find('.chip').exists()).toBe(false)

    await wrapper.find('input[type="checkbox"]').setValue(true)

    const chip = cell(wrapper, '2026-10-19').find('.chip')
    expect(chip.classes()).toContain('chip--rejected')
    expect(chip.find('[aria-hidden="true"]').text()).toBe('Rejected')
  })

  it('moves between months and back to today, fetching the new range', async () => {
    const ranges: string[] = []
    server.use(
      http.get('*/api/me/absence-requests', ({ request }) => {
        const params = new URL(request.url).searchParams
        ranges.push(`${params.get('from')}..${params.get('to')}`)
        return HttpResponse.json([])
      }),
    )
    const wrapper = await mountCalendar()

    await wrapper.find('button[aria-label="Next month"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('h2').text()).toBe('November 2026')

    await wrapper.find('button[aria-label="Previous month"]').trigger('click')
    await wrapper.find('button[aria-label="Previous month"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('h2').text()).toBe('September 2026')

    await wrapper
      .findAll('button')
      .find((b) => b.text() === 'Today')!
      .trigger('click')
    expect(wrapper.find('h2').text()).toBe('October 2026')

    // Which ranges, not how often: the test client has staleTime 0, so revisiting October refetches
    expect(new Set(ranges)).toEqual(
      new Set(['2026-09-28..2026-11-01', '2026-10-26..2026-12-06', '2026-08-31..2026-10-04']),
    )
  })

  it('offers a retry when the absences fail to load', async () => {
    server.use(
      http.get('*/api/me/absence-requests', () => new HttpResponse(null, { status: 503 }), {
        once: true,
      }),
    )
    const wrapper = await mountCalendar()

    expect(wrapper.find('[role="alert"]').text()).toContain("Your absences couldn't be loaded.")

    await wrapper.find('[role="alert"] button').trigger('click')
    await flushPromises()

    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(cell(wrapper, '2026-10-12').find('.chip').exists()).toBe(true)
  })

  it('selects a request from its chip, with one tab stop per labelled chip', async () => {
    const wrapper = await mountCalendar()

    const first = cell(wrapper, '2026-10-12').find('.chip')
    expect(first.element.tagName).toBe('BUTTON')
    expect(first.attributes('tabindex')).toBe('0')
    // The rest of the same week is clickable but not another tab stop
    expect(cell(wrapper, '2026-10-13').find('.chip').attributes('tabindex')).toBe('-1')

    await cell(wrapper, '2026-10-14').find('.chip').trigger('click')

    const selected = wrapper.emitted('select')![0]![0] as AbsenceRequest
    expect([selected.startDate, selected.endDate]).toEqual(['2026-10-12', '2026-10-16'])
  })
})
