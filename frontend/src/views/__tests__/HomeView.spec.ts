import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'

import HomeView from '../HomeView.vue'

describe('HomeView', () => {
  it('renders the app title', () => {
    const wrapper = mount(HomeView)

    expect(wrapper.find('h1').text()).toBe('Praying Mantis')
  })
})
