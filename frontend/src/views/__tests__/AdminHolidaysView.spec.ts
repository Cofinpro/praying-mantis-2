import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, type VueWrapper } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'

import { queryKeys } from '@/api/queryKeys'
import { findMockUser } from '@/mocks/data/users'
import { startMockSession } from '@/mocks/handlers'
import { server } from '@/mocks/node'
import { mountAdminPage, recordCalls } from '@/test/adminPage'

const alex = findMockUser('alex.admin@cofinpro.pt')!
const PATH = '/admin/public-holidays'

const rowTexts = (wrapper: VueWrapper) =>
  wrapper
    .findAll('tbody tr:not(.add)')
    .map((r) => [r.get('th').text(), ...r.findAll('td').map((c) => c.text())].join(' | '))
const button = (wrapper: VueWrapper, text: string) =>
  wrapper.findAll('button').find((b) => b.text() === text || b.attributes('aria-label') === text)!
const dialog = (wrapper: VueWrapper) => wrapper.find('dialog[open]')
const input = (wrapper: VueWrapper, label: string) =>
  wrapper.get(`tr.add input[aria-label="${label}"]`)
const errorOf = (wrapper: VueWrapper, label: string) => {
  const id = input(wrapper, label).attributes('aria-describedby')
  return id ? wrapper.get(`#${id}`).text() : undefined
}

async function openAddRow(wrapper: VueWrapper) {
  await button(wrapper, 'Add holiday').trigger('click')
  await flushPromises()
}

async function saveRow(wrapper: VueWrapper) {
  await wrapper.get('form#add-holiday').trigger('submit')
  await flushPromises()
}

describe('Admin – Public holidays', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(2026, 8, 26)) // Saturday 26 Sept 2026
    startMockSession(alex)
  })
  afterEach(() => vi.useRealTimers())

  it('opens on next year, with the date, the day and weekend holidays marked', async () => {
    const { wrapper } = await mountAdminPage(PATH)

    expect(wrapper.get('h2').text()).toBe('Public holidays 2027')
    expect(wrapper.text()).toContain('Excluded from working days. Weekend holidays have no effect.')
    const rows = rowTexts(wrapper)
    expect(rows).toHaveLength(12)
    expect(rows[0]).toBe('1 Jan 2027 | Fri | New Year’s Day | ')
    expect(rows[2]).toBe('25 Apr 2027 | Sun | Freedom Day Weekend | ')
    expect(wrapper.findAll('tbody tr.weekend')).toHaveLength(4)
    expect(button(wrapper, 'Delete Republic Day').exists()).toBe(true)
    // No "Copy from 2026": the contract has no such endpoint
    expect(wrapper.text()).not.toContain('Copy from')
  })

  it('shows another year, and says when it has no holidays yet', async () => {
    const { wrapper } = await mountAdminPage(PATH)

    await wrapper.get('select[aria-label="Year"]').setValue('2026')
    await flushPromises()
    expect(wrapper.get('h2').text()).toBe('Public holidays 2026')
    expect(rowTexts(wrapper)[0]).toBe('1 Jan 2026 | Thu | New Year’s Day | ')

    await wrapper.get('select[aria-label="Year"]').setValue('2028')
    await flushPromises()
    expect(wrapper.text()).toContain(
      'No public holidays for 2028 yet. Add them with “Add holiday”.',
    )
  })

  it('adds a holiday in the inline row, and refreshes everyone’s holidays', async () => {
    const calls = recordCalls('post', '*/api/admin/public-holidays')
    const { wrapper, queryClient } = await mountAdminPage(PATH)
    queryClient.setQueryData(queryKeys.publicHolidays(2027), [])

    await openAddRow(wrapper)
    expect(document.activeElement).toBe(input(wrapper, 'Date').element)
    expect(button(wrapper, 'Add holiday').attributes('disabled')).toBeDefined()
    await input(wrapper, 'Date').setValue('2027-12-24')
    expect(wrapper.get('tr.add').findAll('td')[1]!.text()).toBe('Fri')
    await input(wrapper, 'Name').setValue(' Christmas Eve (company day) ')
    await saveRow(wrapper)

    expect(calls.map((c) => c.body)).toEqual([
      { date: '2027-12-24', name: 'Christmas Eve (company day)' },
    ])
    expect(wrapper.find('tr.add').exists()).toBe(false)
    const rows = rowTexts(wrapper)
    expect(rows[rows.length - 2]).toBe('24 Dec 2027 | Fri | Christmas Eve (company day) | ')
    expect(queryClient.getQueryState(queryKeys.publicHolidays(2027))?.isInvalidated).toBe(true)
  })

  it('checks the row before sending, and names the holiday already on a date (409)', async () => {
    const calls = recordCalls('post', '*/api/admin/public-holidays')
    const { wrapper } = await mountAdminPage(PATH)
    await openAddRow(wrapper)

    await saveRow(wrapper)
    expect(errorOf(wrapper, 'Date')).toBe('Pick a date.')
    expect(errorOf(wrapper, 'Name')).toBe('Enter a name.')

    await input(wrapper, 'Date').setValue('2028-01-03')
    await input(wrapper, 'Name').setValue('Too late')
    await saveRow(wrapper)
    expect(errorOf(wrapper, 'Date')).toBe('Pick a date in 2027.')
    expect(calls).toEqual([])

    await input(wrapper, 'Date').setValue('2027-10-05')
    await saveRow(wrapper)
    expect(errorOf(wrapper, 'Date')).toBe('5 Oct 2027 is already a holiday (Republic Day).')
    expect(errorOf(wrapper, 'Name')).toBeUndefined()
    expect(calls).toHaveLength(1)
  })

  it('shows a 400 under its field and other errors in the row', async () => {
    const { wrapper } = await mountAdminPage(PATH)
    await openAddRow(wrapper)
    await input(wrapper, 'Date').setValue('2027-02-09')
    await input(wrapper, 'Name').setValue('Carnival')

    server.use(
      http.post('*/api/admin/public-holidays', () =>
        HttpResponse.json(
          {
            type: 'about:blank',
            title: 'Bad Request',
            status: 400,
            errors: [{ field: 'name', message: 'size must be between 1 and 255' }],
          },
          { status: 400 },
        ),
      ),
    )
    await saveRow(wrapper)
    expect(errorOf(wrapper, 'Name')).toBe('size must be between 1 and 255')

    server.use(
      http.post('*/api/admin/public-holidays', () =>
        HttpResponse.json({ type: 'about:blank', title: 'Error', status: 500 }, { status: 500 }),
      ),
    )
    await saveRow(wrapper)
    expect(wrapper.get('tr.add [role="alert"]').text()).toBe(
      'Something went wrong. Please try again.',
    )
  })

  it('cancels the row with the close button or Escape', async () => {
    const { wrapper } = await mountAdminPage(PATH)

    await openAddRow(wrapper)
    await input(wrapper, 'Name').setValue('Half typed')
    await button(wrapper, 'Cancel adding').trigger('click')
    expect(wrapper.find('tr.add').exists()).toBe(false)

    await openAddRow(wrapper)
    expect((input(wrapper, 'Name').element as HTMLInputElement).value).toBe('')
    await input(wrapper, 'Name').trigger('keydown', { key: 'Escape' })
    expect(wrapper.find('tr.add').exists()).toBe(false)
  })

  it('deletes a holiday after a confirmation', async () => {
    const calls = recordCalls('delete', '*/api/admin/public-holidays/:id')
    const { wrapper } = await mountAdminPage(PATH)

    await button(wrapper, 'Delete Republic Day').trigger('click')
    await flushPromises()
    const d = dialog(wrapper)
    expect(d.get('h2').text()).toBe('Delete Republic Day?')
    expect(d.text()).toContain('5 Oct 2027 becomes a working day again')

    await button(wrapper, 'Cancel').trigger('click')
    await flushPromises()
    expect(dialog(wrapper).exists()).toBe(false)
    expect(calls).toEqual([])

    await button(wrapper, 'Delete Republic Day').trigger('click')
    await flushPromises()
    await button(wrapper, 'Delete holiday').trigger('click')
    await flushPromises()
    expect(calls).toHaveLength(1)
    expect(dialog(wrapper).exists()).toBe(false)
    expect(rowTexts(wrapper).some((r) => r.includes('Republic Day'))).toBe(false)
  })

  it('keeps the confirmation open with the error when the delete fails', async () => {
    server.use(
      http.delete('*/api/admin/public-holidays/:id', () =>
        HttpResponse.json({ type: 'about:blank', title: 'Error', status: 500 }, { status: 500 }),
      ),
    )
    const { wrapper } = await mountAdminPage(PATH)

    await button(wrapper, 'Delete Christmas Day').trigger('click')
    await flushPromises()
    // A weekend holiday changes no working days
    expect(dialog(wrapper).text()).toContain(
      '25 Dec 2027 is a Saturday, so no working days change.',
    )
    await button(wrapper, 'Delete holiday').trigger('click')
    await flushPromises()

    expect(dialog(wrapper).get('[role="alert"]').text()).toBe(
      'Something went wrong. Please try again.',
    )
    expect(rowTexts(wrapper).some((r) => r.includes('Christmas Day'))).toBe(true)
  })

  it('tells a non-admin they need an admin account (403)', async () => {
    startMockSession(findMockUser('bruno.costa@cofinpro.pt')!)
    const { wrapper } = await mountAdminPage(PATH)

    expect(wrapper.get('[role="alert"]').text()).toContain(
      'Only admins can manage public holidays.',
    )
    expect(wrapper.find('table').exists()).toBe(false)
    expect(wrapper.findAll('button').some((b) => b.text() === 'Add holiday')).toBe(false)
  })
})
