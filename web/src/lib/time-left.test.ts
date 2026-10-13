import { describe, expect, it } from 'vitest'
import { timeLeft } from './time-left'

describe('timeLeft', () => {
  it('shows hours and minutes left', () => {
    expect(timeLeft(124)).toEqual({ label: '2 h 04 min', status: 'ok' })
  })

  it('marks overdue cases with how long they are overdue', () => {
    expect(timeLeft(-30)).toEqual({ label: 'overdue 0 h 30 min', status: 'overdue' })
  })

  it('shows an unknown deadline as a data problem, not as zero', () => {
    expect(timeLeft(null)).toEqual({ label: 'unknown (data problem)', status: 'unknown' })
  })
})
