import { createContext, useContext } from 'react'

import type { Session } from './session'

export interface AuthState {
  session: Session | null
  signIn: (username: string, password: string) => Promise<Session>
  signOut: () => void
}

export const AuthContext = createContext<AuthState | null>(null)

export function useAuth(): AuthState {
  const auth = useContext(AuthContext)
  if (!auth) {
    throw new Error('useAuth must be used inside AuthProvider')
  }
  return auth
}

/** The signed-in session. Only for pages behind RequireRole, which guarantees there is one. */
export function useSession() {
  const { session } = useAuth()
  if (!session) {
    throw new Error('No session: this page must be behind RequireRole')
  }
  return session
}
