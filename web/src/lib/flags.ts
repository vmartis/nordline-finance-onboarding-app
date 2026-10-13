/**
 * Plain-English help for new reviewers. Guidance written for this tool, not compliance policy.
 * Severity says how serious the flag usually is, not what to decide.
 */
export type FlagSeverity = 'serious' | 'check' | 'minor'
export type FlagInfo = { label: string; text: string; severity: FlagSeverity; known: boolean }

const FLAGS: Record<string, Omit<FlagInfo, 'known'>> = {
  LOW_FACE_MATCH: {
    label: 'Low face match',
    text: 'The selfie and the document photo look less alike than expected. Compare the photos in the back-office tool; age, glasses or lighting can explain a lower score.',
    severity: 'serious',
  },
  IMAGE_QUALITY: {
    label: 'Image quality',
    text: 'A photo is blurred, dark or has glare, so the checks were less sure. Often solved by asking the customer to retake photos.',
    severity: 'minor',
  },
  REPEAT_ATTEMPT: {
    label: 'Repeat attempt',
    text: 'The same document was used in other applications. Look at the other attempts below: how were they decided, which flags did they have, and what changed?',
    severity: 'check',
  },
  FACE_NOT_CAPTURED: {
    label: 'Face not captured',
    text: 'No usable selfie was taken, so there is no face match score. The face cannot be verified; usually ask for a retake.',
    severity: 'check',
  },
  DOC_EXPIRED: {
    label: 'Document expired',
    text: 'The expiry date on the document has passed. Usually a reason to reject or to ask for a valid document; check the reason code list.',
    severity: 'serious',
  },
  DOC_AUTHENTICITY_LOW: {
    label: 'Document authenticity low',
    text: 'The document check is not sure the document is genuine (security features, fonts, layout). Look at the document images in the back-office tool.',
    severity: 'serious',
  },
  NAME_MISMATCH: {
    label: 'Name mismatch',
    text: 'The name read from the document differs from the name the customer typed. Often a typo or missing accents; a different person is serious.',
    severity: 'check',
  },
  LIVENESS_FAIL: {
    label: 'Liveness failed',
    text: 'The liveness check suspects the selfie is not a live person (a photo, a screen or a mask). Possible spoof attempt.',
    severity: 'serious',
  },
}

export function flagInfo(flag: string): FlagInfo {
  const info = FLAGS[flag]
  return info
    ? { ...info, known: true }
    : { label: flag, text: 'No explanation yet. Ask a senior colleague.', severity: 'check', known: false }
}

export type ScoreBand = 'good' | 'borderline' | 'bad' | 'missing' | 'flagged'

/** Check score from 0 to 1 as a band. Thresholds are guidance for reading the score, not decision rules. */
export function scoreBand(score: number | null): ScoreBand {
  if (score === null) return 'missing'
  if (score >= 0.8) return 'good'
  if (score >= 0.6) return 'borderline'
  return 'bad'
}

/** The flag each check raises when it is not sure. */
const FLAG_FOR_SCORE = {
  documentAuthenticity: 'DOC_AUTHENTICITY_LOW',
  faceMatch: 'LOW_FACE_MATCH',
  liveness: 'LIVENESS_FAIL',
} as const

/** Band for display: a score whose own check raised a flag is never shown as good, even if the number looks high. */
export function scoreBandFor(check: keyof typeof FLAG_FOR_SCORE, score: number | null, flags: string[]): ScoreBand {
  const band = scoreBand(score)
  return band === 'good' && flags.includes(FLAG_FOR_SCORE[check]) ? 'flagged' : band
}
