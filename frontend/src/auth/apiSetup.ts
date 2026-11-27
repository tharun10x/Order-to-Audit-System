import type { QueryClient } from '@tanstack/react-query'

import { configureApi } from '../api/client'
import { clearSession, currentToken } from './session'

/** Where the browser goes when the backend says the session is over. */
export const LOGIN_PATH = '/login'

/**
 * Connects the api client to the session. Must run BEFORE the app renders: React runs a child's effects before
 * its parent's, so setting this up in a provider's effect let the first page's requests go out without the token.
 */
export function setUpApi(queryClient: QueryClient, goTo: (path: string) => void = (path) => window.location.assign(path)): void {
  configureApi({
    getToken: currentToken,
    onUnauthorized: () => {
      clearSession({ expired: true })
      queryClient.clear()
      goTo(LOGIN_PATH)
    },
  })
}
