import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// M9 决策点 A（已批 ①）：dev 联调走 Vite proxy，后端零改动、浏览器全同源。
// 生产同构：M10 由 Nginx 做同样的反代（/api → 9090），CORS 在目标架构中不存在。
export default defineConfig({
  plugins: [react()],
  server: {
    port: 9010,
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://localhost:9090',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
    },
  },
});
