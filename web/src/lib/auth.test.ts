import { afterEach, describe, expect, it } from 'vitest'
import { authHeader, clearSession, endedBecause, getSession, setSession } from './auth'

afterEach(() => clearSession())

describe('session', () => {
  const later = new Date(Date.now() + 60_000).toISOString()

  it('is empty until login, then holds the token in memory and sends it as a bearer header', () => {
    expect(getSession()).toBeNull()
    expect(authHeader()).toEqual({})

    setSession({ token: 'abc', username: 'j.sebo', expiresAt: later })

    expect(getSession()?.username).toBe('j.sebo')
    expect(authHeader()).toEqual({ Authorization: 'Bearer abc' })
  })

  it('treats an expired token as logged out', () => {
    setSession({ token: 'abc', username: 'j.sebo', expiresAt: new Date(Date.now() - 1000).toISOString() })
    expect(getSession()).toBeNull()
    expect(authHeader()).toEqual({})
  })

  it('logout clears it', () => {
    setSession({ token: 'abc', username: 'j.sebo', expiresAt: later })
    clearSession()
    expect(getSession()).toBeNull()
  })

  it('remembers that a session ended on its own, so the login page can say so, until the next login', () => {
    setSession({ token: 'abc', username: 'j.sebo', expiresAt: later })
    clearSession('expired')
    expect(endedBecause()).toBe('expired')

    setSession({ token: 'def', username: 'j.sebo', expiresAt: later })
    expect(endedBecause()).toBeNull()

    clearSession()
    expect(endedBecause()).toBeNull()
  })
})
