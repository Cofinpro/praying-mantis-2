import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'

import SegmentedControl from '../SegmentedControl.vue'

const options = [
  { value: 'FULL', label: 'Full day' },
  { value: 'MORNING', label: 'Morning' },
  { value: 'AFTERNOON', label: 'Afternoon', disabled: true },
]

describe('SegmentedControl', () => {
  it('is a labelled group of radio buttons bound to v-model', async () => {
    const wrapper = mount(SegmentedControl, {
      props: { label: 'Start', options, modelValue: 'FULL', 'onUpdate:modelValue': () => {} },
    })

    expect(wrapper.find('legend').text()).toBe('Start')
    const radios = wrapper.findAll('input[type="radio"]')
    expect(radios).toHaveLength(3)
    expect(new Set(radios.map((r) => r.attributes('name'))).size).toBe(1)
    expect((radios[0]!.element as HTMLInputElement).checked).toBe(true)
    expect(radios[2]!.attributes('disabled')).toBeDefined()

    await radios[1]!.setValue(true)
    expect(wrapper.emitted('update:modelValue')).toEqual([['MORNING']])
  })
})
