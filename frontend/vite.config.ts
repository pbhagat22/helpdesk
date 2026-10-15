import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    // During development the UI runs on Vite's server (port 5173) and the API on
    // Spring Boot (port 8080). Forwarding /api to Spring Boot makes the browser see a
    // single origin, so no CORS configuration is needed.
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
