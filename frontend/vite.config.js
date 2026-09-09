import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    open: true,
    proxy: {
      // 백엔드는 별도 프로세스(8080)로 뜬다. 포트가 다르면 브라우저가 CORS 로 막으므로
      // Vite 가 /api 요청을 백엔드로 중계한다. 브라우저는 같은 출처(5173)로 인식하고
      // 세션 쿠키도 그대로 오간다. 배포 시에는 nginx 가 같은 역할을 한다.
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: false,
      },
    },
  },
})
