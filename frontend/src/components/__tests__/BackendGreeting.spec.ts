import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import BackendGreeting from '../BackendGreeting.vue'

describe('BackendGreeting', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('renders the message returned by the backend', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: () => Promise.resolve({ message: 'Hello, world!' }),
      }),
    )

    const wrapper = mount(BackendGreeting)
    await flushPromises()

    expect(wrapper.text()).toContain('Hello, world!')
  })

  it('renders an error when the backend fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 500 }))

    const wrapper = mount(BackendGreeting)
    await flushPromises()

    expect(wrapper.text()).toContain('Backend unavailable')
  })
})
