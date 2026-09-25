import { afterEach, beforeEach, describe, it, expect, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'

import ExportMonthDialog from '@/components/timesheets/ExportMonthDialog.vue'
import { server } from '@/mocks/node'
import { startMockSession } from '@/mocks/handlers'
import { findMockUser } from '@/mocks/data/users'
import { queryPlugin } from '@/test/query'

async function mountDialog() {
  const wrapper = mount(ExportMonthDialog, {
    attachTo: document.body,
    global: { plugins: [queryPlugin()] },
  })
  await vi.waitFor(() => expect(wrapper.findAll('input[type="radio"]')).not.toHaveLength(0))
  await flushPromises()
  return wrapper
}

type Wrapper = Awaited<ReturnType<typeof mountDialog>>

const squash = (text: string) => text.replace(/\s+/g, ' ').trim()
const cards = (wrapper: Wrapper) => wrapper.findAll('label.card').map((c) => squash(c.text()))
const checkedValue = (wrapper: Wrapper) =>
  (wrapper.find('input[type="radio"]:checked').element as HTMLInputElement | null)?.value
const downloadButton = (wrapper: Wrapper) =>
  wrapper.findAll('button').find((b) => /Download \.xlsx|Preparing…/.test(b.text()))!
const warning = (wrapper: Wrapper) => wrapper.find('.export__warning')

/** Collects the export requests' query strings, without replacing the mock's handler */
function recordExports() {
  const queries: Record<string, string>[] = []
  server.use(
    http.get('*/api/me/timesheet-exports', ({ request }) => {
      queries.push(Object.fromEntries(new URL(request.url).searchParams))
    }),
  )
  return queries
}

describe('ExportMonthDialog', () => {
  let clicked: { href: string; download: string }[]
  const createObjectURL = vi.fn((blob: Blob) => (blob ? 'blob:mock-url' : ''))
  const revokeObjectURL = vi.fn()

  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(2026, 8, 25)) // Friday 25 Sep 2026
    startMockSession()
    // jsdom has no object URLs, and clicking a link would try to navigate
    clicked = []
    Object.assign(URL, { createObjectURL, revokeObjectURL })
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
      this: HTMLAnchorElement,
    ) {
      clicked.push({ href: this.href, download: this.download })
    })
  })
  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
    createObjectURL.mockClear()
    revokeObjectURL.mockClear()
  })

  it('shows the templates as radio cards, generic first, with the user’s client preselected', async () => {
    const wrapper = await mountDialog()

    expect(wrapper.find('h2').text()).toBe('Export timesheet')
    expect(wrapper.text()).toContain('Download one month as an Excel file in your client’s format.')
    expect(cards(wrapper)).toEqual([
      'Generic Generic monthly timesheet',
      'DBIS DBIS Stundenübersicht (mock)',
      'Deka Deka Stundennachweis (mock)',
      'DKB Your client DKB Leistungsnachweis (mock)',
      'Union Union Investment Leistungsnachweis (mock)',
      'VV VV Tätigkeitsnachweis (mock)',
    ])
    // Ana works for DKB
    expect(checkedValue(wrapper)).toBe('DKB')
    expect(wrapper.find('.card--selected').text()).toContain('DKB')
    expect(wrapper.findAll('.card__badge')).toHaveLength(1)
  })

  it('preselects the generic template when the user’s client has none', async () => {
    server.use(
      http.get('*/api/export-templates', () =>
        HttpResponse.json([
          { code: 'DEKA', name: 'Deka', client: 'DEKA' },
          { code: 'GENERIC', name: 'Generic monthly timesheet', client: null },
        ]),
      ),
    )

    const wrapper = await mountDialog()

    expect(checkedValue(wrapper)).toBe('GENERIC')
    expect(wrapper.find('.card__badge').exists()).toBe(false)
  })

  it('is one radio group: a fieldset named "Template", radios sharing a name', async () => {
    const wrapper = await mountDialog()

    const fieldset = wrapper.find('fieldset')
    expect(fieldset.find('legend').text()).toBe('Template')
    const radios = fieldset.findAll('input[type="radio"]')
    expect(radios).toHaveLength(6)
    const names = new Set(radios.map((r) => r.attributes('name')))
    expect(names.size).toBe(1)
    expect([...names][0]).toBeTruthy()

    // Picking another card (what arrow keys do in a native group) moves the selection
    await radios[2]!.setValue()
    expect(checkedValue(wrapper)).toBe('DEKA')
    expect(wrapper.findAll('.card--selected')).toHaveLength(1)
    expect(wrapper.find('.card--selected').text()).toContain('Deka')
  })

  it('starts on the current month and offers next month to a year ago', async () => {
    const wrapper = await mountDialog()

    const select = wrapper.find('select')
    expect((select.element as HTMLSelectElement).value).toBe('2026-09')
    expect(wrapper.find('label[for]').text()).toBe('Month')
    const options = select.findAll('option').map((o) => o.text())
    expect(options[0]).toBe('October 2026')
    expect(options[1]).toBe('September 2026')
    expect(options[options.length - 1]).toBe('September 2025')
  })

  it('warns about the weeks with hours that aren’t approved yet, for the chosen month', async () => {
    const wrapper = await mountDialog()

    // September: week 37 is approved, week 38 is submitted
    expect(warning(wrapper).text()).toBe(
      'Week 38 in September is not approved yet. It will be included as it is.',
    )

    await wrapper.find('select').setValue('2026-10')
    await flushPromises()
    // October: week 41 was rejected, week 43 is a draft; the other weeks have no hours
    expect(warning(wrapper).text()).toBe(
      '2 weeks in October are not approved yet (weeks 41 and 43). They will be included as they are.',
    )
  })

  it('shows no warning when every week with hours is approved', async () => {
    server.use(
      http.get('*/api/me/timesheet-months/:month', ({ params }) =>
        HttpResponse.json({
          month: params.month,
          totalHours: 40,
          weeks: [
            { weekStart: '2026-08-31', status: 'DRAFT', hoursInMonth: 0 },
            { weekStart: '2026-09-07', status: 'APPROVED', hoursInMonth: 40 },
          ],
        }),
      ),
    )

    const wrapper = await mountDialog()

    expect(warning(wrapper).exists()).toBe(false)
  })

  it('downloads the month in the chosen template and closes', async () => {
    const queries = recordExports()
    const wrapper = await mountDialog()

    await wrapper.find('select').setValue('2026-10')
    await wrapper.findAll('input[type="radio"]')[0]!.setValue()
    await downloadButton(wrapper).trigger('click')
    await vi.waitFor(() => expect(clicked).toHaveLength(1))

    expect(queries).toEqual([{ month: '2026-10', template: 'GENERIC' }])
    expect(clicked[0]).toEqual({
      href: 'blob:mock-url',
      download: 'timesheet-2026-10-ana.silva-GENERIC.xlsx',
    })
    expect(createObjectURL.mock.calls[0]![0]).toBeInstanceOf(Blob)
    // The temporary link is gone, and the object URL is let go right after
    expect(document.querySelector('a[download]')).toBeNull()
    await vi.waitFor(() => expect(revokeObjectURL).toHaveBeenCalledWith('blob:mock-url'))
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('says "Preparing…" while the file is on its way', async () => {
    let release!: () => void
    const gate = new Promise<void>((resolve) => (release = resolve))
    server.use(
      http.get('*/api/me/timesheet-exports', async () => {
        await gate
      }),
    )
    const wrapper = await mountDialog()

    await downloadButton(wrapper).trigger('click')
    await flushPromises()
    expect(downloadButton(wrapper).text()).toBe('Preparing…')
    expect(downloadButton(wrapper).attributes('disabled')).toBeDefined()

    release()
    await vi.waitFor(() => expect(clicked).toHaveLength(1))
    expect(clicked[0]!.download).toBe('timesheet-2026-09-ana.silva-DKB.xlsx')
  })

  it('explains a template the server doesn’t know, and clears it when the choice changes', async () => {
    server.use(
      http.get('*/api/me/timesheet-exports', () =>
        HttpResponse.json(
          {
            type: 'about:blank',
            title: 'Bad Request',
            status: 400,
            errors: [{ field: 'template', message: 'unknown template DKB' }],
          },
          { status: 400, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )
    const wrapper = await mountDialog()

    await downloadButton(wrapper).trigger('click')
    await flushPromises()

    expect(wrapper.find('[role="alert"]').text()).toBe(
      'This template isn’t available any more. Pick another one.',
    )
    expect(clicked).toHaveLength(0)
    expect(wrapper.emitted('close')).toBeUndefined()

    await wrapper.findAll('input[type="radio"]')[0]!.setValue()
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  it('shows a general message for a server error', async () => {
    server.use(
      http.get('*/api/me/timesheet-exports', () =>
        HttpResponse.json(
          { type: 'about:blank', title: 'Internal Server Error', status: 500 },
          { status: 500, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )
    const wrapper = await mountDialog()

    await downloadButton(wrapper).trigger('click')
    await flushPromises()

    expect(wrapper.find('[role="alert"]').text()).toBe('Something went wrong. Please try again.')
    expect(downloadButton(wrapper).text()).toBe('Download .xlsx')
  })

  it('offers to try again when the templates can’t be loaded', async () => {
    server.use(
      http.get('*/api/export-templates', () => HttpResponse.json(null, { status: 500 }), {
        once: true,
      }),
    )
    const wrapper = mount(ExportMonthDialog, {
      attachTo: document.body,
      global: { plugins: [queryPlugin()] },
    })
    await vi.waitFor(() => expect(wrapper.text()).toContain('The templates couldn’t be loaded.'))
    expect(downloadButton(wrapper).attributes('disabled')).toBeDefined()

    await wrapper
      .findAll('button')
      .find((b) => b.text() === 'Try again')!
      .trigger('click')
    await vi.waitFor(() => expect(wrapper.findAll('input[type="radio"]')).toHaveLength(6))
    expect(checkedValue(wrapper)).toBe('DKB')
  })

  it('preselects another client for another user', async () => {
    startMockSession(findMockUser('eva.santos@cofinpro.pt'))

    const wrapper = await mountDialog()

    // Eva works for VV
    expect(checkedValue(wrapper)).toBe('VV')
    expect(wrapper.find('.card--selected').text()).toContain('Your client')
  })
})
