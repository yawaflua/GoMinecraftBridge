import { defineConfig, loadEnv } from 'vite'
import { svelte } from '@sveltejs/vite-plugin-svelte'

export default defineConfig(({ mode }) => ({
  plugins: [svelte()],
  server: {
    port: 5173,
    proxy: {
      '/v1': {
        target: loadEnv(mode, '.', '').VITE_BACKEND_PROXY ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
}))
