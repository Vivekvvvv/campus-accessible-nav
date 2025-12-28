import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { VitePWA } from 'vite-plugin-pwa'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const rootDir = path.dirname(fileURLToPath(import.meta.url))

// https://vite.dev/config/
export default defineConfig({
  root: rootDir,
  appType: 'spa',
  plugins: [
    vue(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon.ico', 'apple-touch-icon.png', 'masked-icon.svg'],
      manifest: {
        name: 'Accessible Nav',
        short_name: 'Nav',
        description: '校园无障碍导航助手',
        theme_color: '#ffffff',
        icons: [
          {
            src: 'pwa-192x192.png',
            sizes: '192x192',
            type: 'image/png'
          },
          {
            src: 'pwa-512x512.png',
            sizes: '512x512',
            type: 'image/png'
          }
        ]
      },
      workbox: {
        globPatterns: ['**/*.{js,css,html,ico,png,svg,json,vue,txt,woff2}'],
        runtimeCaching: [
          {
            urlPattern: /^https:\/\/(.*)tile\.openstreetmap\.org\/.*/i,
            handler: 'CacheFirst',
            options: {
              cacheName: 'osm-tiles',
              expiration: {
                maxEntries: 1000,
                maxAgeSeconds: 60 * 60 * 24 * 30 // 30 days
              },
              cacheableResponse: {
                statuses: [0, 200]
              }
            }
          },
          {
            urlPattern: /^https:\/\/webrd(.*)\.is\.autonavi\.com\/.*/i,
            handler: 'CacheFirst',
            options: {
              cacheName: 'gaode-tiles',
              expiration: {
                maxEntries: 1000,
                maxAgeSeconds: 60 * 60 * 24 * 30 
              }
            }
          },
          {
             urlPattern: /^https:\/\/api\.maptiler\.com\/.*/i,
             handler: 'CacheFirst',
             options: {
               cacheName: 'maptiler-tiles',
               expiration: {
                 maxEntries: 1000,
                 maxAgeSeconds: 60 * 60 * 24 * 30
               }
             }
          }
        ]
      }
    })
  ],
  server: {
    proxy: {
      '/api': {
        target: process.env.VITE_API_BASE_URL || 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
  build: {
    chunkSizeWarningLimit: 1500,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules')) {
            if (id.includes('maplibre-gl')) return 'maplibre'
            if (id.includes('@mapbox/mapbox-gl-draw')) return 'mapbox-draw'
            if (id.includes('qrcode')) return 'qrcode'
            if (id.includes('html2canvas') || id.includes('jspdf')) return 'reporting'
            return 'vendor'
          }
        },
      },
    },
  },
})
