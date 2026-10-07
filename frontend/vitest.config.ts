import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// https://vitest.dev/config/
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    passWithNoTests: true,
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html'],
    },
    exclude: ['**/node_modules/**', '**/e2e/**'],
  },
})
