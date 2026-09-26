import { beforeEach, describe, expect, it } from 'vitest'
import { flushPromises, type DOMWrapper, type VueWrapper } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'

import { queryKeys } from '@/api/queryKeys'
import { findMockUser } from '@/mocks/data/users'
import { startMockSession } from '@/mocks/handlers'
import { server } from '@/mocks/node'
import { mountAdminPage, recordCalls } from '@/test/adminPage'

const alex = findMockUser('alex.admin@cofinpro.pt')!
const PATH = '/admin/projects'

const cells = (wrapper: VueWrapper, code: string) => {
  const row = wrapper.findAll('tbody tr').find((r) => r.get('th').text() === code)!
  return [code, ...row.findAll('td').map((c) => c.text())]
}
const button = (root: VueWrapper | DOMWrapper<Element>, text: string) =>
  root.findAll('button').find((b) => b.text() === text || b.attributes('aria-label') === text)!
const dialog = (wrapper: VueWrapper) => wrapper.find('dialog[open]')

/** The input or select that a visible label names */
function field(root: DOMWrapper<Element>, label: string) {
  const el = root.findAll('label').find((l) => l.text() === label)
  if (!el) throw new Error(`No field labelled ${label}`)
  return root.get(`#${el.attributes('for')}`)
}
function errorOf(root: DOMWrapper<Element>, label: string) {
  const id = field(root, label).attributes('aria-describedby')
  return id ? root.get(`#${id}`).text() : undefined
}
/** A switch in the dialog, by its visible label */
const switchOf = (root: DOMWrapper<Element>, label: string) => {
  const id = root
    .findAll('p')
    .find((p) => p.text() === label)!
    .attributes('id')
  return root.get(`[role="switch"][aria-labelledby="${id}"]`)
}

async function submit(wrapper: VueWrapper) {
  await dialog(wrapper).get('form').trigger('submit')
  await flushPromises()
}

describe('Admin – Projects', () => {
  beforeEach(() => startMockSession(alex))

  it('lists every project by code, with client, billing and the active flag', async () => {
    const { wrapper } = await mountAdminPage(PATH)

    expect(wrapper.get('h2').text()).toBe('Projects')
    expect(wrapper.text()).toContain('Only active projects can be picked in timesheets')
    expect(wrapper.findAll('tbody th').map((c) => c.text())).toEqual([
      'DBIS-PORTAL',
      'DEKA-RISK',
      'DKB-APP',
      'DKB-CORE',
      'DKB-LEGACY',
      'INTERNAL',
      'TRAINING',
      'UNION-FUNDS',
      'VV-MIGRATION',
    ])
    expect(cells(wrapper, 'DEKA-RISK')).toEqual([
      'DEKA-RISK',
      'Deka risk reporting',
      'Deka',
      'Billable',
      '',
      '',
    ])
    expect(cells(wrapper, 'TRAINING').slice(2, 4)).toEqual(['Internal', 'Non-billable'])
    const active = (code: string) =>
      wrapper.get(`[role="switch"][aria-label="${code} active"]`).attributes('aria-checked')
    expect(active('DKB-CORE')).toBe('true')
    expect(active('DKB-LEGACY')).toBe('false')
    expect(button(wrapper, 'Edit DKB-CORE').exists()).toBe(true)
  })

  it('treats a null client like a missing one (internal), as the backend sends it', async () => {
    server.use(
      http.get('*/api/admin/projects', () =>
        HttpResponse.json([
          {
            id: 7,
            code: 'INTERNAL',
            name: 'Internal',
            client: null,
            isBillable: false,
            isActive: true,
          },
        ]),
      ),
    )
    const { wrapper } = await mountAdminPage(PATH)

    expect(cells(wrapper, 'INTERNAL')[2]).toBe('Internal')
    await button(wrapper, 'Edit INTERNAL').trigger('click')
    await flushPromises()
    expect((field(dialog(wrapper), 'Client').element as HTMLSelectElement).value).toBe('')
  })

  it('adds a project, and the timesheet’s project list is refreshed', async () => {
    const calls = recordCalls('post', '*/api/admin/projects')
    const { wrapper, queryClient } = await mountAdminPage(PATH)
    queryClient.setQueryData(queryKeys.projects(true), [])

    await button(wrapper, 'Add project').trigger('click')
    await flushPromises()
    const d = dialog(wrapper)
    expect(d.get('h2').text()).toBe('Add project')
    await field(d, 'Code').setValue(' dkb-ops ')
    await field(d, 'Name').setValue('Operations support')
    await switchOf(d, 'Billable').trigger('click')
    await field(d, 'Client').setValue('')
    await submit(wrapper)

    expect(calls.map((c) => c.body)).toEqual([
      { code: 'dkb-ops', name: 'Operations support', isBillable: false, isActive: true },
    ])
    expect(dialog(wrapper).exists()).toBe(false)
    expect(cells(wrapper, 'DKB-OPS')).toEqual([
      'DKB-OPS',
      'Operations support',
      'Internal',
      'Non-billable',
      '',
      '',
    ])
    expect(queryClient.getQueryState(queryKeys.projects(true))?.isInvalidated).toBe(true)
  })

  it('checks the code and the name before sending', async () => {
    const calls = recordCalls('post', '*/api/admin/projects')
    const { wrapper } = await mountAdminPage(PATH)

    await button(wrapper, 'Add project').trigger('click')
    await flushPromises()
    const d = dialog(wrapper)
    await field(d, 'Code').setValue('DKB CORE')
    await submit(wrapper)

    expect(errorOf(d, 'Code')).toBe('Use 2 to 30 letters, digits or dashes, e.g. DKB-CORE.')
    expect(errorOf(d, 'Name')).toBe('Enter a name.')
    expect(calls).toEqual([])
    await field(d, 'Code').setValue('DKB-CORE2')
    expect(errorOf(d, 'Code')).toBeUndefined()
  })

  it('edits a project, and shows a duplicate code (409) under Code', async () => {
    const calls = recordCalls('put', '*/api/admin/projects/:id')
    const { wrapper } = await mountAdminPage(PATH)

    await button(wrapper, 'Edit DKB-APP').trigger('click')
    await flushPromises()
    const d = dialog(wrapper)
    expect(d.get('h2').text()).toBe('Edit project')
    expect((field(d, 'Code').element as HTMLInputElement).value).toBe('DKB-APP')
    expect((field(d, 'Client').element as HTMLSelectElement).value).toBe('DKB')

    await field(d, 'Code').setValue('dkb-core')
    await submit(wrapper)
    expect(errorOf(d, 'Code')).toBe('Another project already has the code DKB-CORE.')

    await field(d, 'Code').setValue('DKB-MOBILE')
    await field(d, 'Name').setValue('DKB mobile banking')
    await switchOf(d, 'Active').trigger('click')
    await submit(wrapper)

    expect(calls[calls.length - 1]).toEqual({
      url: '/api/admin/projects/2',
      body: {
        code: 'DKB-MOBILE',
        name: 'DKB mobile banking',
        client: 'DKB',
        isBillable: true,
        isActive: false,
      },
    })
    expect(dialog(wrapper).exists()).toBe(false)
    expect(cells(wrapper, 'DKB-MOBILE').slice(1, 2)).toEqual(['DKB mobile banking'])
  })

  it('shows a 400 from the backend under its field, and other errors in a banner', async () => {
    const { wrapper } = await mountAdminPage(PATH)
    await button(wrapper, 'Edit INTERNAL').trigger('click')
    await flushPromises()
    const d = dialog(wrapper)

    server.use(
      http.put('*/api/admin/projects/:id', () =>
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
    await submit(wrapper)
    expect(errorOf(d, 'Name')).toBe('size must be between 1 and 255')

    server.use(
      http.put('*/api/admin/projects/:id', () =>
        HttpResponse.json({ type: 'about:blank', title: 'Error', status: 500 }, { status: 500 }),
      ),
    )
    await submit(wrapper)
    expect(d.get('[role="alert"]').text()).toBe('Something went wrong. Please try again.')
  })

  it('deactivates and reactivates a project with the row’s switch', async () => {
    const calls = recordCalls('put', '*/api/admin/projects/:id')
    const { wrapper } = await mountAdminPage(PATH)
    const toggle = () => wrapper.get('[role="switch"][aria-label="TRAINING active"]')

    await toggle().trigger('click')
    await flushPromises()
    expect(calls.map((c) => c.body)).toEqual([
      { code: 'TRAINING', name: 'Training', isBillable: false, isActive: false },
    ])
    expect(toggle().attributes('aria-checked')).toBe('false')
    expect(cells(wrapper, 'TRAINING')[1]).toBe('Training')

    await toggle().trigger('click')
    await flushPromises()
    expect(toggle().attributes('aria-checked')).toBe('true')
  })

  it('says so when the switch can’t save', async () => {
    server.use(
      http.put('*/api/admin/projects/:id', () =>
        HttpResponse.json(
          { type: 'about:blank', title: 'Not Found', status: 404 },
          { status: 404 },
        ),
      ),
    )
    const { wrapper } = await mountAdminPage(PATH)

    await wrapper.get('[role="switch"][aria-label="DKB-CORE active"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('.banner').text()).toBe(
      'Couldn’t deactivate DKB-CORE. Something went wrong. Please try again.',
    )
    expect(
      wrapper.get('[role="switch"][aria-label="DKB-CORE active"]').attributes('aria-checked'),
    ).toBe('true')
  })

  it('tells a non-admin they need an admin account (403)', async () => {
    startMockSession(findMockUser('ana.silva@cofinpro.pt')!)
    const { wrapper } = await mountAdminPage(PATH)

    expect(wrapper.get('[role="alert"]').text()).toContain('Only admins can manage projects.')
    expect(wrapper.find('table').exists()).toBe(false)
    expect(wrapper.findAll('button').some((b) => b.text() === 'Add project')).toBe(false)
  })
})
