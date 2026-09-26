import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, type VueWrapper } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'

import { queryKeys } from '@/api/queryKeys'
import { findMockUser } from '@/mocks/data/users'
import { startMockSession } from '@/mocks/handlers'
import { server } from '@/mocks/node'
import { mountAdminPage, recordCalls } from '@/test/adminPage'

const alex = findMockUser('alex.admin@cofinpro.pt')!
const PATH = '/admin/entitlements'

const rowOf = (wrapper: VueWrapper, name: string) =>
  wrapper.findAll('tbody tr').find((r) => r.get('th').text().endsWith(name))!
const cell = (wrapper: VueWrapper, label: string) => wrapper.get(`input[aria-label="${label}"]`)
const values = (wrapper: VueWrapper, name: string) => {
  const row = rowOf(wrapper, name)
  return [
    ...row.findAll('input').map((i) => (i.element as HTMLInputElement).value),
    row.get('.total').text(),
  ]
}
const button = (wrapper: VueWrapper, text: string) =>
  wrapper.findAll('button').find((b) => b.text() === text)!
const dialog = (wrapper: VueWrapper) => wrapper.find('dialog[open]')

async function type(wrapper: VueWrapper, label: string, value: string) {
  await cell(wrapper, label).setValue(value)
}

async function saveChanges(wrapper: VueWrapper) {
  await button(wrapper, 'Save changes').trigger('click')
  await flushPromises()
}

describe('Admin – Entitlements', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(2026, 8, 26)) // Saturday 26 Sept 2026
    startMockSession(alex)
  })
  afterEach(() => vi.useRealTimers())

  it("lists everyone's vacation days for this year, with the total", async () => {
    const { wrapper } = await mountAdminPage(PATH)

    expect(wrapper.get('h2').text()).toBe('Entitlements')
    expect(wrapper.text()).toContain('Yearly vacation days per person for 2026, in steps of 0.5')
    expect((wrapper.get('select[aria-label="Year"]').element as HTMLSelectElement).value).toBe(
      '2026',
    )
    const types = wrapper.findAll('fieldset label').map((l) => l.text())
    expect(types).toEqual(['Vacation', 'Training'])
    expect(wrapper.findAll('tbody tr')).toHaveLength(9)
    expect(values(wrapper, 'Ana Silva')).toEqual(['22', '3', '25'])
    expect(values(wrapper, 'Carla Mendes')).toEqual(['22', '2.5', '24.5'])
    expect(values(wrapper, 'Diogo Pereira')).toEqual(['22', '0', '22'])
    expect(button(wrapper, 'Save changes').attributes('disabled')).toBeDefined()
  })

  it('switches to training days and to another year', async () => {
    const { wrapper } = await mountAdminPage(PATH)

    await wrapper.get('input[type="radio"][value="TRAINING"]').setValue(true)
    await flushPromises()
    expect(values(wrapper, 'Carla Mendes')).toEqual(['5', '0', '5'])
    expect(values(wrapper, 'Ana Silva')).toEqual(['', '', '0'])

    await wrapper.get('select[aria-label="Year"]').setValue('2027')
    await flushPromises()
    expect(wrapper.text()).toContain('Yearly training days per person for 2027')
    expect(values(wrapper, 'Carla Mendes')).toEqual(['', '', '0'])
  })

  it('saves the changed rows, one upsert each, and refreshes the balances', async () => {
    const calls = recordCalls('put', '*/api/admin/entitlements')
    const { wrapper, queryClient } = await mountAdminPage(PATH)
    queryClient.setQueryData(queryKeys.absences.balance(2026), [])

    await type(wrapper, 'Entitled days, Ana Silva', '24.5')
    await type(wrapper, 'Carried over days, Diogo Pereira', '1')
    // Typed and changed back: not a change
    await type(wrapper, 'Entitled days, Bruno Costa', '20')
    await type(wrapper, 'Entitled days, Bruno Costa', '22')
    expect(wrapper.text()).toContain('Unsaved changes')
    expect(values(wrapper, 'Ana Silva')).toEqual(['24.5', '3', '27.5'])

    await saveChanges(wrapper)

    expect(calls.map((c) => c.body)).toEqual([
      { userId: 2, type: 'VACATION', year: 2026, entitledDays: 24.5, carriedOverDays: 3 },
      { userId: 5, type: 'VACATION', year: 2026, entitledDays: 22, carriedOverDays: 1 },
    ])
    expect(wrapper.text()).not.toContain('Unsaved changes')
    expect(wrapper.get('.footer__saved').text()).toBe('Saved')
    expect(values(wrapper, 'Ana Silva')).toEqual(['24.5', '3', '27.5'])
    expect(queryClient.getQueryState(queryKeys.absences.balance(2026))?.isInvalidated).toBe(true)
  })

  it('creates next year’s entitlements, and deletes one whose cells were emptied', async () => {
    const puts = recordCalls('put', '*/api/admin/entitlements')
    const deletes = recordCalls('delete', '*/api/admin/entitlements/:id')
    const { wrapper } = await mountAdminPage(PATH)

    await type(wrapper, 'Entitled days, Eva Santos', '')
    await type(wrapper, 'Carried over days, Eva Santos', '')
    await saveChanges(wrapper)
    expect(deletes.map((c) => c.url)).toEqual(['/api/admin/entitlements/6'])
    expect(values(wrapper, 'Eva Santos')).toEqual(['', '', '0'])

    await wrapper.get('select[aria-label="Year"]').setValue('2027')
    await flushPromises()
    await type(wrapper, 'Entitled days, Hugo Marques', '23')
    await saveChanges(wrapper)
    expect(puts.map((c) => c.body)).toEqual([
      { userId: 8, type: 'VACATION', year: 2027, entitledDays: 23, carriedOverDays: 0 },
    ])
    expect(values(wrapper, 'Hugo Marques')).toEqual(['23', '0', '23'])
  })

  it('checks steps of 0.5 before saving', async () => {
    const { wrapper } = await mountAdminPage(PATH)

    await type(wrapper, 'Carried over days, Ana Silva', '2.25')

    const input = cell(wrapper, 'Carried over days, Ana Silva')
    expect(input.attributes('aria-invalid')).toBe('true')
    expect(wrapper.get(`#${input.attributes('aria-describedby')}`).text()).toBe(
      'Use steps of 0.5, e.g. 2.5.',
    )
    expect(rowOf(wrapper, 'Ana Silva').get('.total').text()).toBe('–')
    expect(button(wrapper, 'Save changes').attributes('disabled')).toBeDefined()
  })

  it('shows a 400 under its cell and names the rows that failed, keeping their edits', async () => {
    server.use(
      http.put('*/api/admin/entitlements', async ({ request }) => {
        const body = (await request.clone().json()) as { userId: number }
        if (body.userId === 2) {
          return HttpResponse.json(
            {
              type: 'about:blank',
              title: 'Bad Request',
              status: 400,
              errors: [{ field: 'entitledDays', message: 'must be in steps of 0.5' }],
            },
            { status: 400 },
          )
        }
        if (body.userId === 4) {
          return HttpResponse.json(
            { type: 'about:blank', title: 'Internal Server Error', status: 500 },
            { status: 500 },
          )
        }
        return undefined
      }),
    )
    const { wrapper } = await mountAdminPage(PATH)

    await type(wrapper, 'Entitled days, Ana Silva', '21')
    await type(wrapper, 'Entitled days, Carla Mendes', '21')
    await type(wrapper, 'Entitled days, Diogo Pereira', '21')
    await saveChanges(wrapper)

    const ana = cell(wrapper, 'Entitled days, Ana Silva')
    expect(wrapper.get(`#${ana.attributes('aria-describedby')}`).text()).toBe(
      'must be in steps of 0.5',
    )
    expect(wrapper.get('.banner').text()).toBe(
      'Couldn’t save Carla Mendes. Something went wrong. Please try again.',
    )
    expect(values(wrapper, 'Carla Mendes')[0]).toBe('21')
    expect(values(wrapper, 'Diogo Pereira')).toEqual(['21', '0', '21'])
    expect(wrapper.text()).toContain('Unsaved changes')

    // A new value clears that row's error
    await type(wrapper, 'Entitled days, Ana Silva', '21.5')
    expect(ana.attributes('aria-invalid')).toBeUndefined()
  })

  it('asks before a year switch throws unsaved changes away', async () => {
    const { wrapper } = await mountAdminPage(PATH)
    await type(wrapper, 'Entitled days, Ana Silva', '30')

    await wrapper.get('select[aria-label="Year"]').setValue('2027')
    await flushPromises()
    expect(dialog(wrapper).get('h2').text()).toBe('Discard unsaved changes?')
    await button(wrapper, 'Keep editing').trigger('click')
    await flushPromises()
    expect(dialog(wrapper).exists()).toBe(false)
    expect((wrapper.get('select[aria-label="Year"]').element as HTMLSelectElement).value).toBe(
      '2026',
    )
    expect(values(wrapper, 'Ana Silva')[0]).toBe('30')

    await wrapper.get('select[aria-label="Year"]').setValue('2027')
    await flushPromises()
    await button(wrapper, 'Discard changes').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('for 2027')
    expect(values(wrapper, 'Ana Silva')).toEqual(['', '', '0'])
    expect(wrapper.text()).not.toContain('Unsaved changes')
  })

  it('asks before leaving the page with unsaved changes, and when closing the tab', async () => {
    const { wrapper, router } = await mountAdminPage(PATH)
    await type(wrapper, 'Entitled days, Ana Silva', '30')

    const event = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(event)
    expect(event.defaultPrevented).toBe(true)

    void router.push('/admin/users')
    await flushPromises()
    expect(dialog(wrapper).get('h2').text()).toBe('Discard unsaved changes?')
    await button(wrapper, 'Discard changes').trigger('click')
    await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/admin/users'))
  })

  it('tells a non-admin they need an admin account (403)', async () => {
    startMockSession(findMockUser('carla.mendes@cofinpro.pt')!)
    const { wrapper } = await mountAdminPage(PATH)

    expect(wrapper.get('[role="alert"]').text()).toContain('You need an admin account')
    expect(wrapper.find('table').exists()).toBe(false)
    expect(wrapper.find('select').exists()).toBe(false)
  })

  it('offers to try again when the entitlements can’t be loaded', async () => {
    server.use(
      http.get('*/api/admin/entitlements', () =>
        HttpResponse.json({ type: 'about:blank', title: 'Error', status: 500 }, { status: 500 }),
      ),
    )
    const { wrapper } = await mountAdminPage(PATH)

    expect(wrapper.get('[role="alert"]').text()).toContain("The entitlements couldn't be loaded.")
    server.resetHandlers()
    await button(wrapper, 'Try again').trigger('click')
    await flushPromises()
    expect(values(wrapper, 'Ana Silva')).toEqual(['22', '3', '25'])
  })
})
