import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    // The browser only ever talks to the Vite server, so there are no cross-origin calls in development.
    // API_TARGET points it at a backend on another port (for example a second one started for a walkthrough).
    proxy: {
      '/api': {
        target: process.env.API_TARGET ?? 'http://localhost:8080',
        // The browser's Origin is this dev server (localhost:5173, or the PC's network address when a phone
        // opens it). Passing it on would make the backend apply its CORS rule to what is really a same-origin
        // call, and refuse any address other than localhost:5173. So the proxy drops it.
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyRequest) => proxyRequest.removeHeader('origin'))
        },
      },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    // Whole-app tests (router, auth, user clicks) can take a few seconds on a busy machine
    testTimeout: 15000,
  },
})
