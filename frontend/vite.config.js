import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
  // 下面是我们新加的 server 代理板块
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080', // 指向你本地的 Spring Boot 后端
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '') // 剥离 /api 前缀，保持和云端 Nginx 逻辑一致
      }
    }
  }
})
