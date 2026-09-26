import { beforeEach, describe, it, expect, vi } from 'vitest'
import { flushPromises, mount, type DOMWrapper, type VueWrapper } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'
import { h } from 'vue'
import { RouterView, createMemoryHistory, createRouter } from 'vue-router'

import { queryKeys } from '@/api/queryKeys'
import { server } from '@/mocks/node'
import { startMockSession } from '@/mocks/handlers'
import { findMockUser } from '@/mocks/data/users'
import { routes } from '@/router'
import { queryPlugin, testQueryClient } from '@/test/query'

const alex = findMockUser('alex.admin@cofinpro.pt')!

// Through a <RouterView>, since the layout and its sections are nested routes. Attached, so the
// native <dialog> and focus behave as in the browser. The routes are lazy, so wait for the page.
async function mountPage(path = '/admin') {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push(path)
  const queryClient = testQueryClient()
  const wrapper = mount(
    { render: () => h(RouterView) },
    { attachTo: document.body, global: { plugins: [router, queryPlugin(queryClient)] } },
  )
  await vi.waitFor(() => expect(wrapper.find('h2').exists()).toBe(true))
  await flushPromises()
  return { wrapper, router, queryClient }
}

type Wrapper = VueWrapper

const rowNames = (wrapper: Wrapper) =>
  wrapper.findAll('tbody th .person__name').map((n) => n.text())
const button = (wrapper: Wrapper, text: string) =>
  wrapper.findAll('button').find((b) => b.text() === text || b.attributes('aria-label') === text)!

/** The input, select or switch that a visible label names */
function field(root: Wrapper | DOMWrapper<Element>, label: string) {
  const el = root.findAll('label').find((l) => l.text() === label)
  if (!el) throw new Error(`No field labelled ${label}`)
  return root.get(`#${el.attributes('for')}`)
}
/** The error under a field, via its aria-describedby */
function errorOf(root: Wrapper | DOMWrapper<Element>, label: string) {
  const id = field(root, label).attributes('aria-describedby')
  return id ? root.get(`#${id}`).text() : undefined
}

const dialog = (wrapper: Wrapper) => wrapper.find('dialog[open]')

/** Collects the bodies the page sends, without replacing the mock's handler */
function record(method: 'post' | 'put', path: string) {
  const calls: { url: string; body: unknown }[] = []
  server.use(
    http[method](path, async ({ request }) => {
      calls.push({ url: new URL(request.url).pathname, body: await request.clone().json() })
    }),
  )
  return calls
}

async function openEdit(wrapper: Wrapper, name: string) {
  await button(wrapper, `Edit ${name}`).trigger('click')
  await flushPromises()
  return dialog(wrapper)
}

async function save(wrapper: Wrapper) {
  await dialog(wrapper).get('form').trigger('submit')
  await flushPromises()
}

describe('Admin layout', () => {
  beforeEach(() => startMockSession(alex))

  it('opens Users at /admin, with the sub-nav', async () => {
    const { wrapper, router } = await mountPage('/admin')

    expect(router.currentRoute.value.name).toBe('admin-users')
    expect(wrapper.get('h1').text()).toBe('Admin')
    expect(wrapper.text()).toContain('Users, entitlements, projects and public holidays')
    const links = wrapper.get('nav[aria-label="Admin sections"]').findAll('a')
    expect(links.map((a) => a.text())).toEqual([
      'Users',
      'Entitlements',
      'Projects',
      'Public holidays',
    ])
    expect(links[0]!.classes()).toContain('subnav__link--active')
    expect(links[0]!.attributes('aria-current')).toBe('page')
  })

  it('moves between sections with the sub-nav', async () => {
    const { wrapper, router } = await mountPage('/admin/users')

    await wrapper.findAll('nav a')[2]!.trigger('click')
    await vi.waitFor(() => expect(wrapper.get('h2').text()).toBe('Projects'))

    expect(router.currentRoute.value.path).toBe('/admin/projects')
    expect(wrapper.text()).toContain('This section comes with FE-9.3.')
    const active = wrapper.findAll('nav a').filter((a) => a.classes('subnav__link--active'))
    expect(active.map((a) => a.text())).toEqual(['Projects'])
  })

  it('tells a non-admin they need an admin account (403)', async () => {
    startMockSession(findMockUser('carla.mendes@cofinpro.pt')!)
    const { wrapper } = await mountPage('/admin/users')

    expect(wrapper.get('[role="alert"]').text()).toContain('You need an admin account')
    expect(wrapper.find('table').exists()).toBe(false)
    expect(wrapper.findAll('button').some((b) => b.text() === 'Add user')).toBe(false)
  })
})

describe('Admin – Users', () => {
  beforeEach(() => startMockSession(alex))

  it('lists everyone by name, with client, level, team lead and admin flag', async () => {
    const { wrapper } = await mountPage()

    expect(rowNames(wrapper)).toEqual([
      'Alex Admin',
      'Ana Silva',
      'Bruno Costa',
      'Carla Mendes',
      'Diogo Pereira',
      'Eva Santos',
      'Filipe Rocha',
      'Gabriela Lopes',
      'Hugo Marques',
    ])
    expect(wrapper.text()).toContain(
      '9 people · the team lead is who approves their absences and timesheets',
    )
    const cells = (i: number) =>
      wrapper
        .findAll('tbody tr')
        [i]!.findAll('td')
        .map((c) => c.text())
    const person = wrapper.findAll('tbody th')[0]!
    expect(person.get('.person__avatar').text()).toBe('AA')
    expect(person.get('.person__email').text()).toBe('alex.admin@cofinpro.pt')
    // "—" on screen, "None" for screen readers
    expect(cells(0)).toEqual(['DBIS', 'Architect', '—None', 'Yes', ''])
    expect(cells(5)).toEqual(['VV', 'Expert', 'Bruno Costa', 'No', ''])
    expect(button(wrapper, 'Edit Carla Mendes').exists()).toBe(true)
  })

  it('treats a null team lead like a missing one, as the backend sends it', async () => {
    server.use(
      http.get('*/api/admin/users', () =>
        HttpResponse.json([
          {
            id: 9,
            name: 'Gabriela Lopes',
            email: 'gabriela.lopes@cofinpro.pt',
            client: 'UNION',
            level: 'JUNIOR',
            isAdmin: false,
            isTeamLead: false,
            teamLead: null,
          },
        ]),
      ),
    )
    const { wrapper } = await mountPage()

    expect(wrapper.findAll('tbody td')[2]!.text()).toBe('—None')
    const d = await openEdit(wrapper, 'Gabriela Lopes')
    expect((field(d, 'Team lead').element as HTMLSelectElement).value).toBe('0')
  })

  it('filters by name or email', async () => {
    const { wrapper } = await mountPage()
    const search = wrapper.get('input[type="search"]')

    await search.setValue('costa')
    expect(rowNames(wrapper)).toEqual(['Bruno Costa'])
    await search.setValue('EVA.SANTOS@')
    expect(rowNames(wrapper)).toEqual(['Eva Santos'])
    await search.setValue('nobody')
    expect(wrapper.text()).toContain('No user matches “nobody”.')
  })

  it('adds a user with the initial password', async () => {
    const calls = record('post', '*/api/admin/users')
    const { wrapper } = await mountPage()

    await button(wrapper, 'Add user').trigger('click')
    await flushPromises()
    const d = dialog(wrapper)
    expect(d.get('h2').text()).toBe('Add user')
    expect(d.findAll('button').some((b) => b.text() === 'Set new password')).toBe(false)
    await field(d, 'Name').setValue('  Zé Novo ')
    await field(d, 'Email').setValue('ze.novo@cofinpro.pt')
    await field(d, 'Initial password (8 to 72 characters)').setValue('change-me-please')
    await field(d, 'Client').setValue('UNION')
    await field(d, 'Level').setValue('EXPERT')
    await field(d, 'Team lead').setValue('2')
    await save(wrapper)

    expect(calls).toEqual([
      {
        url: '/api/admin/users',
        body: {
          name: 'Zé Novo',
          email: 'ze.novo@cofinpro.pt',
          client: 'UNION',
          level: 'EXPERT',
          isAdmin: false,
          teamLeadId: 2,
          password: 'change-me-please',
        },
      },
    ])
    expect(wrapper.find('dialog[open]').exists()).toBe(false)
    expect(rowNames(wrapper)).toContain('Zé Novo')
  })

  it('shows 400 field errors under their fields', async () => {
    const { wrapper } = await mountPage()
    await button(wrapper, 'Add user').trigger('click')
    await flushPromises()
    const d = dialog(wrapper)
    await field(d, 'Name').setValue('Zé Novo')
    await field(d, 'Email').setValue('ze.novo@cofinpro.pt')
    await field(d, 'Initial password (8 to 72 characters)').setValue('short')
    await save(wrapper)

    expect(errorOf(d, 'Initial password (8 to 72 characters)')).toBe(
      'size must be between 8 and 72',
    )
    expect(d.find('[role="alert"]').exists()).toBe(false)

    // Typing again clears it
    await field(d, 'Initial password (8 to 72 characters)').setValue('long enough')
    expect(errorOf(d, 'Initial password (8 to 72 characters)')).toBeUndefined()
  })

  it('edits a user, leaving the team lead out for none', async () => {
    const calls = record('put', '*/api/admin/users/:id')
    const { wrapper } = await mountPage()

    const d = await openEdit(wrapper, 'Carla Mendes')
    expect(d.get('h2').text()).toBe('Edit user')
    expect((field(d, 'Name').element as HTMLInputElement).value).toBe('Carla Mendes')
    // Everyone but Carla herself can lead her
    const leads = field(d, 'Team lead')
      .findAll('option')
      .map((o) => o.text())
    expect(leads).toHaveLength(9)
    expect(leads[0]).toBe('No team lead (an admin approves)')
    expect(leads).not.toContain('Carla Mendes')

    await field(d, 'Level').setValue('SENIOR_ARCHITECT')
    await field(d, 'Team lead').setValue('0')
    await d.get('[role="switch"]').trigger('click')
    await save(wrapper)

    expect(calls).toEqual([
      {
        url: '/api/admin/users/4',
        body: {
          name: 'Carla Mendes',
          email: 'carla.mendes@cofinpro.pt',
          client: 'DKB',
          level: 'SENIOR_ARCHITECT',
          isAdmin: true,
        },
      },
    ])
    const carla = wrapper
      .findAll('tbody tr')[3]!
      .findAll('td')
      .map((c) => c.text())
    expect(carla).toEqual(['DKB', 'Senior architect', '—None', 'Yes', ''])
  })

  it('says who already has the email (409) under Email', async () => {
    const { wrapper } = await mountPage()
    const d = await openEdit(wrapper, 'Carla Mendes')

    await field(d, 'Email').setValue('Eva.Santos@cofinpro.pt')
    await save(wrapper)

    expect(errorOf(d, 'Email')).toBe('This email is already used by Eva Santos.')
    expect(field(d, 'Email').attributes('aria-invalid')).toBe('true')
    expect(d.find('[role="alert"]').exists()).toBe(false)
  })

  it('explains a team-lead cycle (409) under Team lead', async () => {
    const { wrapper } = await mountPage()
    // Ana leads Bruno, so Bruno can't lead Ana
    const d = await openEdit(wrapper, 'Ana Silva')

    await field(d, 'Team lead').setValue('3')
    await save(wrapper)

    expect(errorOf(d, 'Team lead')).toBe(
      'Ana Silva already leads Bruno Costa, directly or through others. Pick another team lead.',
    )
    expect(wrapper.find('dialog[open]').exists()).toBe(true)
  })

  it('keeps the last admin (409) and says so next to the switch', async () => {
    const { wrapper } = await mountPage()
    const d = await openEdit(wrapper, 'Alex Admin')

    const toggle = d.get('[role="switch"]')
    await toggle.trigger('click')
    expect(toggle.attributes('aria-checked')).toBe('false')
    await save(wrapper)

    const error = 'This is the only admin. Make someone else an admin first.'
    expect(d.text()).toContain(error)
    const describedBy = toggle.attributes('aria-describedby')!.split(' ')
    expect(describedBy.map((id) => d.get(`#${id}`).text())).toContain(error)
  })

  it('refreshes my own user after I edit myself', async () => {
    const { wrapper, queryClient } = await mountPage()
    await queryClient.fetchQuery({ queryKey: queryKeys.me, queryFn: async () => alex })

    const d = await openEdit(wrapper, 'Alex Admin')
    await field(d, 'Name').setValue('Alex Admin-Silva')
    await save(wrapper)

    await vi.waitFor(() =>
      expect(queryClient.getQueryData<{ name: string }>(queryKeys.me)?.name).toBe(
        'Alex Admin-Silva',
      ),
    )
  })

  it('sets a new password from the edit dialog', async () => {
    const calls = record('post', '*/api/admin/users/:id/password')
    const { wrapper } = await mountPage()
    await openEdit(wrapper, 'Carla Mendes')

    await button(wrapper, 'Set new password').trigger('click')
    await flushPromises()
    const dialogs = wrapper.findAll('dialog[open]')
    expect(dialogs).toHaveLength(2)
    const small = dialogs[1]!
    expect(small.get('h2').text()).toBe('Set new password')

    await field(small, 'New password (8 to 72 characters)').setValue('short')
    await small.get('form').trigger('submit')
    await flushPromises()
    expect(errorOf(small, 'New password (8 to 72 characters)')).toBe(
      'size must be between 8 and 72',
    )

    await field(small, 'New password (8 to 72 characters)').setValue('a-new-password')
    await small.get('form').trigger('submit')
    await flushPromises()

    expect(calls.map((c) => c.body)).toEqual([
      { password: 'short' },
      { password: 'a-new-password' },
    ])
    expect(wrapper.findAll('dialog[open]')).toHaveLength(1)
    expect(dialog(wrapper).get('[role="status"]').text()).toBe('New password set for Carla Mendes.')
  })
})
