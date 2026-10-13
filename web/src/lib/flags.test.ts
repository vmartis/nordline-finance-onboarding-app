import { describe, expect, it } from 'vitest'
import { flagInfo, scoreBand, scoreBandFor } from './flags'

const ALL_FLAGS = [
  'LOW_FACE_MATCH',
  'IMAGE_QUALITY',
  'REPEAT_ATTEMPT',
  'FACE_NOT_CAPTURED',
  'DOC_EXPIRED',
  'DOC_AUTHENTICITY_LOW',
  'NAME_MISMATCH',
  'LIVENESS_FAIL',
]

describe('flagInfo', () => {
  it.each(ALL_FLAGS)('explains %s in plain English with how serious it usually is', (flag) => {
    const info = flagInfo(flag)
    expect(info.text.length).toBeGreaterThan(20)
    expect(['serious', 'check', 'minor']).toContain(info.severity)
    expect(info.known).toBe(true)
  })

  it('shows an unknown flag as is, without inventing an explanation', () => {
    expect(flagInfo('NEW_FLAG')).toEqual({
      label: 'NEW_FLAG',
      text: 'No explanation yet. Ask a senior colleague.',
      severity: 'check',
      known: false,
    })
  })
})

describe('scoreBand', () => {
  it('labels scores as good, borderline or bad, and missing stays missing', () => {
    expect(scoreBand(0.92)).toBe('good')
    expect(scoreBand(0.8)).toBe('good')
    expect(scoreBand(0.7)).toBe('borderline')
    expect(scoreBand(0.6)).toBe('borderline')
    expect(scoreBand(0.59)).toBe('bad')
    expect(scoreBand(0)).toBe('bad')
    expect(scoreBand(null)).toBe('missing')
  })
})

describe('scoreBandFor', () => {
  it('never shows a score as good when its own check raised a flag', () => {
    expect(scoreBandFor('faceMatch', 0.85, ['LOW_FACE_MATCH'])).toBe('flagged')
    expect(scoreBandFor('documentAuthenticity', 0.9, ['DOC_AUTHENTICITY_LOW'])).toBe('flagged')
    expect(scoreBandFor('liveness', 0.95, ['LIVENESS_FAIL'])).toBe('flagged')
  })

  it('keeps the normal band when the flag belongs to another check', () => {
    expect(scoreBandFor('faceMatch', 0.85, ['IMAGE_QUALITY'])).toBe('good')
    expect(scoreBandFor('faceMatch', 0.5, ['LOW_FACE_MATCH'])).toBe('bad')
    expect(scoreBandFor('faceMatch', null, ['LOW_FACE_MATCH'])).toBe('missing')
  })
})
