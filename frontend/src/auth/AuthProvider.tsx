import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'

import { api } from '../api/client'
import type { LoginResponse, Me } from '../api/types'
import { AuthContext, type AuthState } from './authContext'
import { clearSession, loadSession, onSessionCleared, saveSession, type Session } from './session'

/** The signed-in user for the screens. The api client reads the token itself (see apiSetup). */
export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [session, setSession] = useState<Session | null>(() => loadSession())

  const signOut = useCallback(() => {
    clearSession()
    queryClient.clear()
    setSession(null)
  }, [queryClient])

  // The api client clears the session on a 401; the screens must follow
  useEffect(() => onSessionCleared(() => setSession(null)), [])

  // After a reload, check the stored token is still good and pick up today's business date
  const storedToken = session?.token
  useEffect(() => {
    if (!storedToken) {
      return
    }
    api.get<Me>('/api/auth/me').then(
      (me) => {
        const refreshed = loadSession()
        if (refreshed && refreshed.token === storedToken) {
          const { businessDate, ...user } = me
          const next = { ...refreshed, businessDate, user }
          saveSession(next)
          setSession(next)
        }
      },
      () => {
        // A 401 is handled by the api client; anything else leaves the session as it was
      },
    )
  }, [storedToken])

  const signIn = useCallback(async (username: string, password: string) => {
    const response = await api.post<LoginResponse>('/api/auth/login', { username, password }, { anonymous: true })
    const next: Session = {
      token: response.token,
      expiresAt: response.expiresAt,
      businessDate: response.businessDate,
      user: response.user,
    }
    saveSession(next)
    setSession(next)
    return next
  }, [])

  const value = useMemo<AuthState>(() => ({ session, signIn, signOut }), [session, signIn, signOut])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
