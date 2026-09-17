import { defineConfig } from 'vite'

// The bundle is served by WebViewAssetLoader under /assets/web/, so every URL stays relative.
export default defineConfig({
  base: './',
  build: {
    outDir: '../src/main/assets/web',
    emptyOutDir: true,
  },
})
