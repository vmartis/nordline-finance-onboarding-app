import { describe, expect, it } from 'vitest'
import { decisionSchema, errorMessage, rememberedReviewer } from './decision'

const valid = { reviewer: 'j.sebo', decision: 'APPROVE', reasonCode: 'VERIFIED_MANUAL', comment: 'Face matches' }

describe('decisionSchema', () => {
  it('accepts a complete decision', () => {
    expect(decisionSchema.safeParse(valid).success).toBe(true)
  })

  it.each(['reviewer', 'decision', 'reasonCode', 'comment'])('requires %s', (field) => {
    expect(decisionSchema.safeParse({ ...valid, [field]: '' }).success).toBe(false)
  })

  it('does not accept a comment of only spaces', () => {
    expect(decisionSchema.safeParse({ ...valid, comment: '   ' }).success).toBe(false)
  })

  it('limits the comment to what the back office stores (500 characters)', () => {
    expect(decisionSchema.safeParse({ ...valid, comment: 'x'.repeat(501) }).success).toBe(false)
  })
})

describe('errorMessage', () => {
  it('uses the server explanation when there is one', () => {
    expect(errorMessage({ detail: 'Unknown reason code.' }, 422)).toBe('Unknown reason code.')
  })

  it('falls back to a plain sentence', () => {
    expect(errorMessage(null, 500)).toBe('Something went wrong (500). Nothing was saved. Try again.')
  })
})

describe('rememberedReviewer', () => {
  const reviewers = [{ id: 'j.sebo' }, { id: 't.weber' }]

  it('keeps the remembered name when it is still on the list', () => {
    expect(rememberedReviewer('t.weber', reviewers)).toBe('t.weber')
  })

  it('drops a remembered name that is not on the list, so nobody saves under a hidden name', () => {
    expect(rememberedReviewer('someone.else', reviewers)).toBe('')
    expect(rememberedReviewer(null, reviewers)).toBe('')
  })
})
