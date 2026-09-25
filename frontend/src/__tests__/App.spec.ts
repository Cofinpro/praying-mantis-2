import { describe, it, expect } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

import App from '../App.vue'
import { routes } from '@/router'
import { queryKeys } from '@/api/queryKeys'
import { mockUser } from '@/mocks/handlers'
import { queryPlugin, testQueryClient } from '@/test/query'

async function mountAppAt(path: string) {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push(path)
  const queryClient = testQueryClient()
  queryClient.setQueryData(queryKeys.me, mockUser)
  const wrapper = mount(App, { global: { plugins: [router, queryPlugin(queryClient)] } })
  await flushPromises()
  return wrapper
}

describe('App layout', () => {
  it('shows the header on app pages', async () => {
    const wrapper = await mountAppAt('/absences')

    expect(wrapper.find('header nav').exists()).toBe(true)
  })

  it('shows the login page without the header', async () => {
    const wrapper = await mountAppAt('/login')

    expect(wrapper.find('header nav').exists()).toBe(false)
    expect(wrapper.find('h1').text()).toBe('Sign in')
  })
})
