import { describe, expect, it } from 'vitest'
import { backlogSummary } from './backlog'

const item = (minutesLeft: number | null) => ({ minutesLeft })

describe('backlogSummary', () => {
  it('counts overdue, due within 1 h and later, like the time badges', () => {
    const summary = backlogSummary([item(-5), item(-1), item(0), item(59), item(60), item(300)])
    expect(summary).toEqual({ overdue: 2, soon: 2, later: 2, unknown: 0 })
  })

  it('counts unknown deadlines separately, never as on time', () => {
    expect(backlogSummary([item(null), item(120)])).toEqual({ overdue: 0, soon: 0, later: 1, unknown: 1 })
  })

  it('is all zero for an empty queue', () => {
    expect(backlogSummary([])).toEqual({ overdue: 0, soon: 0, later: 0, unknown: 0 })
  })
})
