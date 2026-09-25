import { afterEach, beforeEach, describe, it, expect, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'
import { h } from 'vue'
import { RouterView, createMemoryHistory, createRouter } from 'vue-router'

import { server } from '@/mocks/node'
import { mockUser, setMockTimesheet, startMockSession } from '@/mocks/handlers'
import { timesheets } from '@/mocks/data/timesheets'
import { routes } from '@/router'
import { queryPlugin } from '@/test/query'

// Mounted through a <RouterView>, since the unsaved-changes guard (onBeforeRouteLeave) only works
// in a component the router rendered. The route is lazy, so wait for the grid to show up.
async function mountPage(path = '/timesheets?week=2026-10-19') {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push(path)
  const wrapper = mount(
    { render: () => h(RouterView) },
    { attachTo: document.body, global: { plugins: [router, queryPlugin()] } },
  )
  await vi.waitFor(() => expect(wrapper.find('table').exists()).toBe(true))
  await flushPromises()
  return { wrapper, router }
}

type Wrapper = Awaited<ReturnType<typeof mountPage>>['wrapper']

const input = (wrapper: Wrapper, label: string) =>
  wrapper.find(`input[aria-label="${label}, hours"]`)
const squash = (text: string) => text.replace(/\s+/g, ' ').trim()
const rowTexts = (wrapper: Wrapper) => wrapper.findAll('tbody th').map((th) => squash(th.text()))
const dayTotals = (wrapper: Wrapper) => wrapper.findAll('tfoot td').map((td) => td.text())
const saveButton = (wrapper: Wrapper) =>
  wrapper.findAll('button').find((b) => ['Save', 'Saving…'].includes(b.text()))!
const buttonByText = (wrapper: Wrapper, text: string) =>
  wrapper.findAll('button').find((b) => b.text() === text)

/** Collects the PUT bodies, without replacing the mock's handler */
function recordSaves() {
  const bodies: unknown[] = []
  server.use(
    http.put('*/api/me/timesheets/:weekStart/entries', async ({ request }) => {
      bodies.push(await request.clone().json())
    }),
  )
  return bodies
}

describe('TimesheetsView', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(2026, 8, 25)) // Friday 25 Sep 2026
    startMockSession()
  })
  afterEach(() => vi.useRealTimers())

  it('shows the week of ?week= with its rows, totals and absences', async () => {
    const { wrapper } = await mountPage()

    expect(wrapper.find('h2').text()).toBe('Week 43 · 19–25 Oct 2026')
    expect(wrapper.find('.badge').text()).toBe('Draft')
    expect(rowTexts(wrapper)).toEqual([
      'DKB-CORE, DKB · DKB core banking',
      'DKB-APP, DKB · DKB mobile app',
      'TRAINING, Internal · Training',
    ])
    expect(wrapper.findAll('tbody tr')[0]!.find('.total').text()).toBe('27')
    expect(dayTotals(wrapper)).toEqual(['8', '8', '8', '8', '6', '–', '–', '38'])
    expect(input(wrapper, 'DKB-CORE, Monday 19 Oct').element).toHaveProperty('value', '6')
    expect(input(wrapper, 'DKB-CORE, Wednesday 21 Oct').element).toHaveProperty('value', '')
    // Wednesday's approved training, as in the frame
    const wednesday = wrapper.findAll('thead th')[3]!
    expect(wednesday.find('.chip [aria-hidden]').text()).toBe('Training')
    expect(wednesday.text()).toBe('Wed 21Training, Training (approved absence)')
    expect(wrapper.findAll('thead .chip')).toHaveLength(1)
  })

  it('opens the current week without ?week=, and any day of a week opens that week', async () => {
    const { wrapper, router } = await mountPage('/timesheets')

    expect(wrapper.find('h2').text()).toBe('Week 39 · 21–27 Sept 2026')
    expect(wrapper.text()).toContain('No hours yet this week.')

    await router.push('/timesheets?week=2026-10-21')
    await flushPromises()
    expect(wrapper.find('h2').text()).toBe('Week 43 · 19–25 Oct 2026')

    await router.push('/timesheets?week=not-a-date')
    await flushPromises()
    expect(wrapper.find('h2').text()).toBe('Week 39 · 21–27 Sept 2026')
  })

  it('moves between weeks and back to today', async () => {
    const { wrapper, router } = await mountPage()

    await wrapper.find('button[aria-label="Next week"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.week).toBe('2026-10-26')
    expect(wrapper.find('h2').text()).toBe('Week 44 · 26 Oct – 1 Nov 2026')

    await wrapper.find('button[aria-label="Previous week"]').trigger('click')
    await flushPromises()
    await wrapper.find('button[aria-label="Previous week"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.week).toBe('2026-10-12')

    await buttonByText(wrapper, 'Today')!.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/timesheets')
  })

  it('updates the totals as you type and saves the whole week', async () => {
    const bodies = recordSaves()
    const { wrapper } = await mountPage()
    expect(saveButton(wrapper).attributes('disabled')).toBeDefined()

    await input(wrapper, 'DKB-APP, Friday 23 Oct').setValue('2,5')
    await input(wrapper, 'DKB-APP, Friday 23 Oct').trigger('blur')

    expect(input(wrapper, 'DKB-APP, Friday 23 Oct').element).toHaveProperty('value', '2.5')
    expect(dayTotals(wrapper)[4]).toBe('8.5')
    expect(dayTotals(wrapper)[7]).toBe('40.5')
    expect(wrapper.find('.toolbar__unsaved').text()).toBe('Unsaved changes')

    await saveButton(wrapper).trigger('click')
    await flushPromises()

    expect(bodies).toHaveLength(1)
    const { entries } = bodies[0] as { entries: { projectId: number; workDate: string }[] }
    expect(entries).toHaveLength(8)
    expect(entries).toContainEqual({ projectId: 2, workDate: '2026-10-23', hours: 2.5 })
    expect(entries).toContainEqual({
      projectId: 1,
      workDate: '2026-10-20',
      hours: 8,
      description: 'Sprint planning',
    })
    expect(wrapper.find('.toolbar__unsaved').exists()).toBe(false)
    expect(wrapper.find('.toolbar__saved').text()).toBe('Saved')
    expect(saveButton(wrapper).attributes('disabled')).toBeDefined()
  })

  it('flags hours that aren’t quarter hours or go over 24 a day, and blocks saving', async () => {
    const { wrapper } = await mountPage()

    await input(wrapper, 'DKB-CORE, Thursday 22 Oct').setValue('7.1')
    const cell = input(wrapper, 'DKB-CORE, Thursday 22 Oct')
    expect(cell.attributes('aria-invalid')).toBe('true')
    const message = wrapper.find(`#${cell.attributes('aria-describedby')}`)
    expect(message.text()).toBe('DKB-CORE, Thursday 22 Oct: Use quarter hours, e.g. 7.25')
    expect(saveButton(wrapper).attributes('disabled')).toBeDefined()

    await cell.setValue('7')
    await input(wrapper, 'DKB-APP, Monday 19 Oct').setValue('20')
    expect(wrapper.find('.errors').text()).toBe('Monday 19 Oct: More than 24 hours in one day')
    expect(wrapper.find('tfoot td.total--error').text()).toBe('26')
    expect(input(wrapper, 'DKB-CORE, Monday 19 Oct').attributes('aria-describedby')).toBeTruthy()
    expect(saveButton(wrapper).attributes('disabled')).toBeDefined()

    await input(wrapper, 'DKB-APP, Friday 23 Oct').setValue('25')
    expect(wrapper.find('.errors').text()).toContain('DKB-APP, Friday 23 Oct: At most 24 hours')
  })

  it('adds a project row and focuses its Monday', async () => {
    const { wrapper } = await mountPage()

    await buttonByText(wrapper, 'Add project')!.trigger('click')
    const options = wrapper.findAll('.add-form option').map((o) => o.text())
    // Only the active projects that aren't on the grid yet
    expect(options).not.toContain('DKB-CORE · DKB core banking')
    expect(options).toContain('DEKA-RISK · Deka risk reporting')
    await wrapper.find('.add-form select').setValue('3')
    await wrapper.find('.add-form').trigger('submit')
    await flushPromises()

    expect(rowTexts(wrapper)[3]).toBe('DEKA-RISK, Deka · Deka risk reporting')
    expect(document.activeElement).toBe(input(wrapper, 'DEKA-RISK, Monday 19 Oct').element)
    // An empty row changes nothing to save yet
    expect(wrapper.find('.toolbar__unsaved').exists()).toBe(false)
  })

  it('removes a row', async () => {
    const bodies = recordSaves()
    const { wrapper } = await mountPage()

    await wrapper.find('button[aria-label="Remove DKB-APP"]').trigger('click')
    expect(rowTexts(wrapper)).not.toContain('DKB-APP, DKB · DKB mobile app')
    await saveButton(wrapper).trigger('click')
    await flushPromises()

    const { entries } = bodies[0] as { entries: { projectId: number }[] }
    expect(entries.some((e) => e.projectId === 2)).toBe(false)
  })

  it('edits the descriptions of a row’s entries', async () => {
    const bodies = recordSaves()
    const { wrapper } = await mountPage()

    await wrapper.find('button[aria-label="Descriptions for DKB-CORE (1)"]').trigger('click')
    const dialog = wrapper.find('dialog[open]')
    expect(dialog.find('h2').text()).toBe('Descriptions · DKB-CORE')
    // One field per day with hours
    expect(dialog.findAll('label').map((l) => l.text())).toEqual([
      'Monday 19 Oct · 6 h',
      'Tuesday 20 Oct · 8 h',
      'Thursday 22 Oct · 7 h',
      'Friday 23 Oct · 6 h',
    ])
    const fields = dialog.findAll('input')
    expect(fields[1]!.element).toHaveProperty('value', 'Sprint planning')
    await fields[0]!.setValue('Code review')
    await dialog.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.find('dialog[open]').exists()).toBe(false)
    expect(wrapper.find('button[aria-label="Descriptions for DKB-CORE (2)"]').text()).toBe('2')
    await saveButton(wrapper).trigger('click')
    await flushPromises()
    const { entries } = bodies[0] as { entries: unknown[] }
    expect(entries).toContainEqual({
      projectId: 1,
      workDate: '2026-10-19',
      hours: 6,
      description: 'Code review',
    })
  })

  it('shows a submitted week read-only', async () => {
    const { wrapper } = await mountPage('/timesheets?week=2026-09-14')

    expect(wrapper.find('.badge').text()).toBe('Submitted')
    expect(wrapper.findAll('input')).toHaveLength(0)
    expect(saveButton(wrapper)).toBeUndefined()
    expect(buttonByText(wrapper, 'Add project')).toBeUndefined()
    expect(wrapper.find('button[aria-label="Remove DKB-CORE"]').exists()).toBe(false)
    expect(wrapper.find('[role="note"]').text()).toBe(
      'Submitted on 18 Sept 2026. Alex Admin decides on it next. The week is read-only.',
    )
    expect(dayTotals(wrapper)[7]).toBe('40')
  })

  it('shows an approved week read-only', async () => {
    const { wrapper } = await mountPage('/timesheets?week=2026-09-07')

    expect(wrapper.find('.badge').text()).toBe('Approved')
    expect(wrapper.findAll('input')).toHaveLength(0)
    expect(wrapper.find('[role="note"]').text()).toBe(
      'Approved by Alex Admin on 14 Sept 2026. The week is read-only.',
    )
  })

  it('shows why a week was rejected and keeps it editable', async () => {
    const { wrapper } = await mountPage('/timesheets?week=2026-10-05')

    expect(wrapper.find('.badge').text()).toBe('Rejected')
    expect(wrapper.findAll('[role="note"] p').map((p) => p.text())).toEqual([
      'Rejected by Alex Admin on 12 Oct 2026',
      '“Please book Thursday on DKB-CORE”',
      'Correct the hours, save, and submit the week again.',
    ])
    expect(input(wrapper, 'DKB-CORE, Thursday 8 Oct').exists()).toBe(true)
    // Monday is a public holiday, Thursday an approved sick day
    const chips = wrapper.findAll('thead .chip').map((c) => c.attributes('title'))
    expect(chips).toEqual(['Republic Day (public holiday)', 'Sick (approved absence)'])
  })

  it('asks before leaving the week with unsaved changes', async () => {
    const { wrapper, router } = await mountPage()
    await input(wrapper, 'DKB-APP, Friday 23 Oct').setValue('2')

    await wrapper.find('button[aria-label="Next week"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('dialog[open] h2').text()).toBe('Discard unsaved changes?')

    await buttonByText(wrapper, 'Keep editing')!.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.week).toBe('2026-10-19')
    expect(input(wrapper, 'DKB-APP, Friday 23 Oct').element).toHaveProperty('value', '2')

    // Leaving the page asks too
    void router.push('/absences')
    await flushPromises()
    await buttonByText(wrapper, 'Discard changes')!.trigger('click')
    // /absences is a lazy route too
    await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/absences'))
  })

  it('leaves without asking when nothing changed', async () => {
    const { wrapper, router } = await mountPage()

    await wrapper.find('button[aria-label="Next week"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('dialog[open]').exists()).toBe(false)
    expect(router.currentRoute.value.query.week).toBe('2026-10-26')
  })

  it('lets the browser warn before closing the tab with unsaved changes', async () => {
    const { wrapper } = await mountPage()
    const unload = () => {
      const event = new Event('beforeunload', { cancelable: true })
      window.dispatchEvent(event)
      return event.defaultPrevented
    }
    expect(unload()).toBe(false)

    await input(wrapper, 'DKB-APP, Friday 23 Oct').setValue('2')
    expect(unload()).toBe(true)
  })

  it('explains a 409 when the week was submitted meanwhile, and shows it read-only', async () => {
    const { wrapper } = await mountPage()
    await input(wrapper, 'DKB-APP, Friday 23 Oct').setValue('2')
    // Submitted in another tab after the page loaded
    setMockTimesheet(mockUser.id, { ...timesheets['2026-10-19']!, status: 'SUBMITTED' })

    await saveButton(wrapper).trigger('click')
    await flushPromises()

    expect(wrapper.find('[role="alert"]').text()).toBe(
      'This week was already submitted, so it can’t be changed any more.',
    )
    // The 409 refetched the week: the stored hours, read-only, and nothing left to save
    expect(wrapper.find('.badge').text()).toBe('Submitted')
    expect(wrapper.findAll('input')).toHaveLength(0)
    expect(dayTotals(wrapper)[4]).toBe('6')
    expect(wrapper.find('.toolbar__unsaved').exists()).toBe(false)
  })

  it('points a 400 from the server at its cell', async () => {
    server.use(
      http.put(
        '*/api/me/timesheets/:weekStart/entries',
        () =>
          HttpResponse.json(
            {
              type: 'about:blank',
              title: 'Bad Request',
              status: 400,
              errors: [{ field: 'entries[0].projectId', message: 'project DKB-CORE is inactive' }],
            },
            { status: 400 },
          ),
        { once: true },
      ),
    )
    const { wrapper } = await mountPage()
    await input(wrapper, 'DKB-APP, Friday 23 Oct').setValue('2')
    await saveButton(wrapper).trigger('click')
    await flushPromises()

    // The first entry sent is DKB-CORE on Monday
    expect(input(wrapper, 'DKB-CORE, Monday 19 Oct').attributes('aria-invalid')).toBe('true')
    expect(wrapper.find('.errors').text()).toBe(
      'DKB-CORE, Monday 19 Oct: project DKB-CORE is inactive',
    )
    expect(wrapper.find('[role="alert"]').text()).toBe(
      'Some hours weren’t accepted. See the list under the grid.',
    )
  })
})
