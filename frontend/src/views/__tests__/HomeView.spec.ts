import { describe, it, expect } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { http, HttpResponse } from 'msw'

import HomeView from '../HomeView.vue'
import { server } from '@/mocks/node'
import { queryPlugin } from '@/test/query'

function mountHome() {
  return mount(HomeView, { global: { plugins: [queryPlugin()] } })
}

describe('HomeView', () => {
  it('renders the app title', () => {
    expect(mountHome().find('h1').text()).toBe('Praying Mantis')
  })

  it('shows the greeting from GET /api/hello', async () => {
    const wrapper = mountHome()
    await flushPromises()

    expect(wrapper.text()).toContain('Backend says: Hello from the MSW mock')
  })

  it('shows an error when the backend fails', async () => {
    server.use(http.get('*/api/hello', () => new HttpResponse(null, { status: 503 })))

    const wrapper = mountHome()
    await flushPromises()

    expect(wrapper.find('.error').text()).toContain('Backend unavailable')
  })
})
