import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { ref } from 'vue'

import BaseToggle from '../BaseToggle.vue'

function mountToggle(initial = false, props: Record<string, unknown> = {}) {
  const value = ref(initial)
  const wrapper = mount(BaseToggle, {
    props: {
      modelValue: value.value,
      'onUpdate:modelValue': (v: boolean) => {
        value.value = v
        void wrapper.setProps({ modelValue: v })
      },
      label: 'Admin',
      ...props,
    },
  })
  return { wrapper, value }
}

describe('BaseToggle', () => {
  it('is a labelled switch that reports its state', async () => {
    const { wrapper, value } = mountToggle()
    const toggle = wrapper.get('button')

    expect(toggle.attributes('role')).toBe('switch')
    expect(toggle.attributes('aria-label')).toBe('Admin')
    expect(toggle.attributes('aria-checked')).toBe('false')

    await toggle.trigger('click')
    expect(value.value).toBe(true)
    expect(toggle.attributes('aria-checked')).toBe('true')
  })

  it('flips with Space and Enter', async () => {
    const { wrapper, value } = mountToggle()
    const toggle = wrapper.get('button')

    await toggle.trigger('keydown', { key: ' ' })
    expect(value.value).toBe(true)
    await toggle.trigger('keydown', { key: 'Enter' })
    expect(value.value).toBe(false)
    // Other keys leave it alone
    await toggle.trigger('keydown', { key: 'a' })
    expect(value.value).toBe(false)
  })

  it('only shows the state when read-only', () => {
    const { wrapper } = mountToggle(true, { readonly: true })

    expect(wrapper.find('button').exists()).toBe(false)
    expect(wrapper.find('[role="switch"]').exists()).toBe(false)
    expect(wrapper.text()).toBe('Yes')
  })
})
