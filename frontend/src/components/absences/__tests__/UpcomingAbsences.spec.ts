import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'

import UpcomingAbsences from '../UpcomingAbsences.vue'
import { absenceRequests, absenceTypes } from '@/mocks/data/absences'

const fromOctober = absenceRequests.filter((r) => r.endDate >= '2026-10-01')

describe('UpcomingAbsences', () => {
  it('lists the next approved and pending absences, without rejected or cancelled ones', () => {
    const wrapper = mount(UpcomingAbsences, {
      props: { requests: fromOctober, types: absenceTypes, limit: 10 },
    })

    const items = wrapper.findAll('li').map((li) => li.text())
    expect(items).toEqual([
      '8 OctSick · 1 dayApproved',
      '12–16 OctVacation · 5 daysApproved',
      '21 OctTraining · 1 dayApproved',
      '26–27 OctVacation · 2 daysPending',
      '30 Oct (afternoon)Vacation · 0.5 dayApproved',
    ])
  })

  it('shows at most three by default', () => {
    const wrapper = mount(UpcomingAbsences, {
      props: { requests: fromOctober, types: absenceTypes },
    })

    expect(wrapper.findAll('li')).toHaveLength(3)
  })

  it('says so when nothing is booked', () => {
    const wrapper = mount(UpcomingAbsences, { props: { requests: [], types: absenceTypes } })

    expect(wrapper.text()).toContain('Nothing booked yet.')
  })

  it('selects a request when its item is clicked', async () => {
    const wrapper = mount(UpcomingAbsences, {
      props: { requests: fromOctober, types: absenceTypes },
    })

    const item = wrapper.findAll('li')[1]!.find('button')
    await item.trigger('click')

    expect(wrapper.emitted('select')![0]).toEqual([fromOctober[1]])
  })
})
