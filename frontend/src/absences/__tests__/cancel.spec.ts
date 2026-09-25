import { describe, it, expect } from 'vitest'

import { canCancel } from '../cancel'
import type { AbsenceRequest } from '@/api/client'

const request = (status: AbsenceRequest['status'], startDate: string): AbsenceRequest => ({
  id: 1,
  type: 'VACATION',
  startDate,
  endDate: startDate,
  startPart: 'FULL',
  endPart: 'FULL',
  workingDays: 1,
  status,
  createdAt: '2026-09-01T09:00:00Z',
})

describe('canCancel', () => {
  it('allows a pending request at any time', () => {
    expect(canCancel(request('PENDING', '2026-10-26'), '2026-10-01')).toBe(true)
    expect(canCancel(request('PENDING', '2026-09-28'), '2026-10-01')).toBe(true)
  })

  it('allows an approved request until the day it starts', () => {
    expect(canCancel(request('APPROVED', '2026-10-02'), '2026-10-01')).toBe(true)
    expect(canCancel(request('APPROVED', '2026-10-01'), '2026-10-01')).toBe(false)
    expect(canCancel(request('APPROVED', '2026-09-30'), '2026-10-01')).toBe(false)
  })

  it('never allows a rejected or cancelled request', () => {
    expect(canCancel(request('REJECTED', '2026-10-26'), '2026-10-01')).toBe(false)
    expect(canCancel(request('CANCELLED', '2026-10-26'), '2026-10-01')).toBe(false)
  })
})
