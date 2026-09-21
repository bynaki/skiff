// `vitest/config` rather than `vite`: the same defineConfig, plus the types for the `test` block.
import { defineConfig } from 'vitest/config'

// The bundle is served by WebViewAssetLoader under /assets/web/, so every URL stays relative.
export default defineConfig({
  base: './',
  build: {
    outDir: '../src/main/assets/web',
    emptyOutDir: true,
  },
  test: {
    // No DOM: what these test is what the page keeps track of, not what it draws. Every module
    // that reaches Kotlin imports `bridge.ts`, which refuses to load outside the WebView, so the
    // setup file puts that global there before anything else loads.
    environment: 'node',
    setupFiles: ['test/bridge.ts'],
  },
})
