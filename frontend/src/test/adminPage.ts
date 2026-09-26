import { expect, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { http } from 'msw'
import { h } from 'vue'
import { RouterView, createMemoryHistory, createRouter } from 'vue-router'

import { server } from '@/mocks/node'
import { routes } from '@/router'
import { queryPlugin, testQueryClient } from './query'

/**
 * An admin section through a <RouterView>, since the layout and its sections are nested routes
 * (and route guards need one). Attached, so the native <dialog> and focus behave as in the
 * browser. The routes are lazy, so it waits for the section's heading.
 */
export async function mountAdminPage(path: string) {
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

/** Collects the calls the page sends, without replacing the mock's handler */
export function recordCalls(method: 'post' | 'put' | 'delete', path: string) {
  const calls: { url: string; body: unknown }[] = []
  server.use(
    http[method](path, async ({ request }) => {
      const text = await request.clone().text()
      calls.push({ url: new URL(request.url).pathname, body: text ? JSON.parse(text) : undefined })
    }),
  )
  return calls
}
