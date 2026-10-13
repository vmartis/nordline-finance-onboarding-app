import { describe, expect, it } from 'vitest'
import { attemptFor, decisionSchema, errorMessage, rememberedReviewer, type DecisionInput } from './decision'

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

describe('attemptFor', () => {
  let n = 0
  const newKey = () => `key-${++n}`

  it('reuses the key when the same decision is saved again (retry or double click)', () => {
    const first = attemptFor(valid as DecisionInput, null, newKey)
    const again = attemptFor({ ...valid } as DecisionInput, first, newKey)
    expect(again.key).toBe(first.key)
  })

  it('uses a new key when the reviewer changed anything', () => {
    const first = attemptFor(valid as DecisionInput, null, newKey)
    const changed = attemptFor({ ...valid, reasonCode: 'FACE_MISMATCH' } as DecisionInput, first, newKey)
    expect(changed.key).not.toBe(first.key)
  })
})
