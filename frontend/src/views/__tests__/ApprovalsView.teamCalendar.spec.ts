import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'
import { h } from 'vue'
import { RouterView, createMemoryHistory, createRouter } from 'vue-router'

import { server } from '@/mocks/node'
import { startMockSession } from '@/mocks/handlers'
import { findMockUser } from '@/mocks/data/users'
import { routes } from '@/router'
import { queryPlugin } from '@/test/query'

// FE-5.3: the third tab of the Approvals page. Mock data: Ana's team in October 2026
// (mocks/data/team.ts and her own requests in mocks/data/absences.ts).

async function mountPage(path = '/approvals?tab=team-calendar') {
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

const panel = (wrapper: Wrapper) => wrapper.get('#panel-team-calendar')
const title = (wrapper: Wrapper) => panel(wrapper).get('h2').text()
const header = (wrapper: Wrapper, date: string) =>
  panel(wrapper).get(`thead th[data-date="${date}"]`)
const row = (wrapper: Wrapper, name: string) =>
  panel(wrapper)
    .findAll('tbody tr')
    .find((tr) => tr.get('th').text().includes(name))!
const cell = (wrapper: Wrapper, name: string, date: string) =>
  row(wrapper, name).get(`td[data-date="${date}"]`)
const away = (wrapper: Wrapper, date: string) => panel(wrapper).get(`tfoot td[data-date="${date}"]`)

/** Records the ranges the page asks for, without replacing the mock's handler */
function recordRanges() {
  const ranges: string[] = []
  server.use(
    http.get('*/api/team/absences', ({ request }) => {
      const params = new URL(request.url).searchParams
      ranges.push(`${params.get('from')}..${params.get('to')}`)
    }),
  )
  return ranges
}

describe('ApprovalsView, Team calendar tab', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(2026, 9, 1))
    startMockSession()
  })
  afterEach(() => vi.useRealTimers())

  it('is the third tab, selected by ?tab=team-calendar', async () => {
    const { wrapper, router } = await mountPage('/approvals')
    const tabs = wrapper.findAll('[role="tab"]')
    expect(tabs.map((t) => t.text().replace(/\s*\d+$/, ''))).toEqual([
      'Absences',
      'Timesheets',
      'Team calendar',
    ])
    expect(wrapper.find('#panel-team-calendar table').exists()).toBe(false)

    await tabs[2]!.trigger('click')
    await vi.waitFor(() => expect(router.currentRoute.value.query.tab).toBe('team-calendar'))
    await flushPromises()
    expect(tabs[2]!.attributes('aria-selected')).toBe('true')
    expect(panel(wrapper).attributes('hidden')).toBeUndefined()
    expect(panel(wrapper).find('table').exists()).toBe(true)
  })

  it('shows one column per day of the month, weekends and holidays shaded', async () => {
    const { wrapper } = await mountPage()
    expect(title(wrapper)).toBe('October 2026')

    const days = panel(wrapper).findAll('thead th[data-date]')
    expect(days).toHaveLength(31)
    expect(days[0]!.find('.visually-hidden').text()).toBe('Thursday 1 Oct')
    expect(days[0]!.attributes('abbr')).toBe('Thursday 1 Oct')
    expect(header(wrapper, '2026-10-03').classes()).toContain('day--off')
    expect(header(wrapper, '2026-10-05').classes()).toContain('day--off')
    expect(header(wrapper, '2026-10-05').text()).toContain('public holiday: Republic Day')
    expect(header(wrapper, '2026-10-06').classes()).not.toContain('day--off')
    expect(cell(wrapper, 'Ana Silva', '2026-10-04').classes()).toContain('cell--off')
  })

  it('lists me first, then my team by name, with their absences as blocks', async () => {
    const { wrapper } = await mountPage()
    expect(
      panel(wrapper)
        .findAll('tbody th')
        .map((th) => th.text()),
    ).toEqual(['AS Ana Silva', 'BC Bruno Costa', 'CM Carla Mendes', 'DP Diogo Pereira'])

    const vacation = cell(wrapper, 'Carla Mendes', '2026-10-14').get('.block')
    expect(vacation.text()).toBe('Carla Mendes, vacation, approved, 14–16 Oct')
    expect(vacation.attributes('style')).toContain('var(--color-primary)')
    expect(cell(wrapper, 'Carla Mendes', '2026-10-13').find('.block').exists()).toBe(false)
    // Bruno's vacation runs over the weekend: no blocks on Saturday and Sunday
    expect(cell(wrapper, 'Bruno Costa', '2026-10-02').find('.block').exists()).toBe(true)
    expect(cell(wrapper, 'Bruno Costa', '2026-10-03').find('.block').exists()).toBe(false)
    expect(cell(wrapper, 'Diogo Pereira', '2026-10-07').get('.block').text()).toBe(
      'Diogo Pereira, sick, approved, 7 Oct',
    )
  })

  it('draws pending requests dashed and half days on their side', async () => {
    const { wrapper } = await mountPage()
    const pending = cell(wrapper, 'Ana Silva', '2026-10-26').get('.block')
    expect(pending.classes()).toContain('block--pending')
    expect(pending.text()).toBe('Ana Silva, vacation, pending, 26–27 Oct')

    const afternoon = cell(wrapper, 'Ana Silva', '2026-10-30').get('.block')
    expect(afternoon.classes()).toContain('block--afternoon')
    expect(afternoon.text()).toBe('Ana Silva, vacation, approved, 30 Oct, afternoon only')
    expect(cell(wrapper, 'Bruno Costa', '2026-10-23').get('.block').classes()).toContain(
      'block--morning',
    )
  })

  it('highlights the days where 2+ people are away and counts them in the Away row', async () => {
    const { wrapper } = await mountPage()
    const conflicts = panel(wrapper)
      .findAll('thead th.day--conflict')
      .map((th) => th.attributes('data-date'))
    expect(conflicts).toEqual(['2026-10-14', '2026-10-15', '2026-10-16'])
    expect(header(wrapper, '2026-10-15').text()).toContain('several people away')
    expect(header(wrapper, '2026-10-15').attributes('title')).toBe(
      'Away: Ana Silva, Carla Mendes, Diogo Pereira',
    )

    const badge = away(wrapper, '2026-10-15').get('.count')
    expect(badge.text()).toBe('3')
    expect(badge.classes()).toContain('badge--filled')
    expect(away(wrapper, '2026-10-15').get('.visually-hidden').text()).toBe(
      '3 people away: Ana Silva, Carla Mendes, Diogo Pereira',
    )
    expect(away(wrapper, '2026-10-08').get('.count').classes()).not.toContain('badge--filled')
    expect(away(wrapper, '2026-10-08').text()).toContain('1')
    expect(away(wrapper, '2026-10-09').text()).toBe('')
  })

  it('moves between months, fetching each month’s range', async () => {
    const ranges = recordRanges()
    const { wrapper } = await mountPage()
    expect(ranges).toEqual(['2026-10-01..2026-10-31'])

    await panel(wrapper).get('button[aria-label="Next month"]').trigger('click')
    await flushPromises()
    expect(title(wrapper)).toBe('November 2026')
    expect(ranges).toContain('2026-11-01..2026-11-30')
    expect(panel(wrapper).findAll('thead th[data-date]')).toHaveLength(30)
    // Carla's pending request from the Absences tab
    expect(cell(wrapper, 'Carla Mendes', '2026-11-16').get('.block').classes()).toContain(
      'block--pending',
    )

    await panel(wrapper).get('button[aria-label="Previous month"]').trigger('click')
    await panel(wrapper).get('button[aria-label="Previous month"]').trigger('click')
    await flushPromises()
    expect(title(wrapper)).toBe('September 2026')
    expect(ranges).toContain('2026-09-01..2026-09-30')
    expect(away(wrapper, '2026-09-29').text()).toContain('2')

    const today = panel(wrapper)
      .findAll('button')
      .find((b) => b.text() === 'Today')!
    await today.trigger('click')
    await flushPromises()
    expect(title(wrapper)).toBe('October 2026')
  })

  it('says so when I lead nobody: only my own row', async () => {
    startMockSession(findMockUser('carla.mendes@cofinpro.pt'))
    const { wrapper } = await mountPage()
    expect(panel(wrapper).text()).toContain(
      'Nobody has you as their team lead, so the calendar only shows your own absences.',
    )
    expect(panel(wrapper).findAll('tbody tr')).toHaveLength(1)
  })

  it('shows an error with Try again when the calendar can’t be loaded', async () => {
    let fail = true
    server.use(
      http.get('*/api/team/absences', () =>
        fail
          ? HttpResponse.json(
              { type: 'about:blank', title: 'Internal Server Error', status: 500 },
              { status: 500 },
            )
          : undefined,
      ),
    )
    const { wrapper } = await mountPage()
    expect(panel(wrapper).get('[role="alert"]').text()).toContain(
      "The team calendar couldn't be loaded.",
    )
    expect(panel(wrapper).find('table').exists()).toBe(false)

    fail = false
    await panel(wrapper)
      .findAll('button')
      .find((b) => b.text() === 'Try again')!
      .trigger('click')
    await flushPromises()
    expect(panel(wrapper).find('table').exists()).toBe(true)
  })
})
