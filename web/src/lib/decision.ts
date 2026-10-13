import { z } from 'zod'

export const DECISIONS = [
  { value: 'APPROVE', label: 'Approve' },
  { value: 'REJECT', label: 'Reject' },
  { value: 'REQUEST_RECAPTURE', label: 'Ask customer to retake photos' },
  { value: 'ESCALATE', label: 'Escalate' },
] as const

/** Every decision needs a reviewer, a compliance reason code and a short comment. */
export const decisionSchema = z.object({
  reviewer: z.string().min(1, 'Pick your name.'),
  decision: z.enum(['APPROVE', 'REJECT', 'REQUEST_RECAPTURE', 'ESCALATE'], { error: 'Pick a decision.' }),
  reasonCode: z.string().min(1, 'Pick a reason code.'),
  comment: z.string().trim().min(1, 'Write a short comment.').max(500, 'Keep the comment under 500 characters.'),
})

export type DecisionInput = z.infer<typeof decisionSchema>

/** A sentence for the reviewer: the server's own explanation (problem detail) or a plain fallback. */
export function errorMessage(body: unknown, status: number): string {
  const detail = (body as { detail?: unknown } | null)?.detail
  return typeof detail === 'string' && detail.length > 0
    ? detail
    : `Something went wrong (${status}). Nothing was saved. Try again.`
}

/** The remembered reviewer, but only if still on the list; otherwise the reviewer must pick again. */
export function rememberedReviewer(saved: string | null, reviewers: { id: string }[]): string {
  return saved !== null && reviewers.some((r) => r.id === saved) ? saved : ''
}

/** One save attempt: the decision and its idempotency key. */
export type Attempt = { input: DecisionInput; key: string }

/** Same decision again (retry, double click) keeps its key, so the back office saves it once; any change gets a new key. */
export function attemptFor(input: DecisionInput, last: Attempt | null, newKey: () => string): Attempt {
  const same =
    last !== null &&
    last.input.reviewer === input.reviewer &&
    last.input.decision === input.decision &&
    last.input.reasonCode === input.reasonCode &&
    last.input.comment.trim() === input.comment.trim()
  return same ? last : { input, key: newKey() }
}
