const KEY = 'morning-queue.reviewer'

/** The reviewer's own name, remembered in this browser only (a convenience; there is no login). */
export function loadReviewer(): string | null {
  try {
    return localStorage.getItem(KEY)
  } catch {
    return null
  }
}

export function saveReviewer(id: string) {
  try {
    localStorage.setItem(KEY, id)
  } catch {
    /* remembering the name is only a convenience */
  }
}
