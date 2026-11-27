import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router'

import { ApiError } from './api/client'
import { setUpApi } from './auth/apiSetup'
import { AuthProvider } from './auth/AuthProvider'
import './index.css'
import { router } from './routes/router'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // One retry for a dropped connection; an answer from the server (403, 404, ...) will not change by asking again
      retry: (failures, error) => failures < 1 && error instanceof ApiError && error.isNetwork,
      refetchOnWindowFocus: false,
    },
  },
})

// Before the first render, so the first page's requests already carry the token
setUpApi(queryClient)

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <RouterProvider router={router} />
      </AuthProvider>
    </QueryClientProvider>
  </StrictMode>,
)
