import { describe, expect, it } from 'vitest'
import { statusLabel } from './status'

describe('statusLabel', () => {
  it.each([
    ['PENDING_REVIEW', 'Waiting for review'],
    ['APPROVED', 'Approved'],
    ['REJECTED', 'Rejected'],
    ['RECAPTURE_REQUESTED', 'Retake requested'],
    ['ESCALATED', 'Escalated'],
  ])('shows %s as "%s"', (code, label) => {
    expect(statusLabel(code)).toBe(label)
  })

  it('shows an unknown status as it is, without guessing', () => {
    expect(statusLabel('SOMETHING_NEW')).toBe('SOMETHING_NEW')
  })
})
