const LABELS: Record<string, string> = {
  PENDING_REVIEW: 'Waiting for review',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  RECAPTURE_REQUESTED: 'Retake requested',
  ESCALATED: 'Escalated',
}

/** Case status in plain words; an unknown status is shown as it is. */
export function statusLabel(status: string): string {
  return LABELS[status] ?? status
}
