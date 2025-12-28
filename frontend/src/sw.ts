/// <reference lib="webworker" />

/**
 * Service Worker - 离线导航支持
 */

import { logger } from './utils/logger';

declare const self: ServiceWorkerGlobalScope;

const CACHE_NAME = 'accessible-nav-v1';
const TILE_CACHE_NAME = 'map-tiles-v1';
const DATA_CACHE_NAME = 'app-data-v1';

// 需要预缓存的静态资源
const STATIC_ASSETS = [
  '/',
  '/index.html',
  '/manifest.json',
];

// 地图瓦片URL模式
const TILE_URL_PATTERNS = [
  /^https:\/\/[abc]\.tile\.openstreetmap\.org/,
  /^https:\/\/.*\.tiles\.mapbox\.com/,
  /^https:\/\/restapi\.amap\.com\/v3\/staticmap/,
];

// API URL模式
const API_URL_PATTERN = /\/api\//;

self.addEventListener('install', (event: ExtendableEvent) => {
  logger.debug('[SW] 安装中...');
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      logger.debug('[SW] 预缓存静态资源');
      return cache.addAll(STATIC_ASSETS);
    })
  );
  // 跳过等待，立即激活
  self.skipWaiting();
});

self.addEventListener('activate', (event: ExtendableEvent) => {
  logger.debug('[SW] 激活中...');
  event.waitUntil(
    caches.keys().then((cacheNames) => {
      return Promise.all(
        cacheNames
          .filter((name) => {
            // 删除旧版本缓存
            return name !== CACHE_NAME &&
                   name !== TILE_CACHE_NAME &&
                   name !== DATA_CACHE_NAME;
          })
          .map((name) => {
            logger.debug('[SW] 删除旧缓存:', name);
            return caches.delete(name);
          })
      );
    })
  );
  // 立即控制所有客户端
  self.clients.claim();
});

self.addEventListener('fetch', (event: FetchEvent) => {
  const { request } = event;
  const url = new URL(request.url);

  // 只处理同源请求和特定的第三方请求
  if (url.origin !== self.location.origin) {
    // 检查是否是地图瓦片请求
    if (isTileRequest(request.url)) {
      event.respondWith(handleTileRequest(request));
      return;
    }
    // 其他第三方请求直接放行
    return;
  }

  // API请求：网络优先，失败时使用缓存
  if (API_URL_PATTERN.test(url.pathname)) {
    event.respondWith(handleApiRequest(request));
    return;
  }

  // 静态资源：缓存优先
  event.respondWith(handleStaticRequest(request));
});

/**
 * 检查是否是地图瓦片请求
 */
function isTileRequest(url: string): boolean {
  return TILE_URL_PATTERNS.some((pattern) => pattern.test(url));
}

/**
 * 处理地图瓦片请求 - 缓存优先策略
 */
async function handleTileRequest(request: Request): Promise<Response> {
  const cache = await caches.open(TILE_CACHE_NAME);

  // 先检查缓存
  const cachedResponse = await cache.match(request);
  if (cachedResponse) {
    // 后台更新缓存
    fetchAndCache(request, cache);
    return cachedResponse;
  }

  // 没有缓存，从网络获取
  try {
    const networkResponse = await fetch(request);
    if (networkResponse.ok) {
      cache.put(request, networkResponse.clone());
    }
    return networkResponse;
  } catch (_error) {
    // 返回占位图或错误响应
    return new Response('Tile not available offline', { status: 503 });
  }
}

/**
 * 后台获取并缓存
 */
async function fetchAndCache(request: Request, cache: Cache): Promise<void> {
  try {
    const response = await fetch(request);
    if (response.ok) {
      cache.put(request, response);
    }
  } catch {
    // 忽略后台更新失败
  }
}

/**
 * 处理API请求 - 网络优先策略
 */
async function handleApiRequest(request: Request): Promise<Response> {
  const cache = await caches.open(DATA_CACHE_NAME);

  try {
    const networkResponse = await fetch(request);

    // 只缓存GET请求的成功响应
    if (request.method === 'GET' && networkResponse.ok) {
      cache.put(request, networkResponse.clone());
    }

    return networkResponse;
  } catch (_error) {
    // 网络失败，尝试从缓存获取
    const cachedResponse = await cache.match(request);
    if (cachedResponse) {
      return cachedResponse;
    }

    // 返回离线错误响应
    return new Response(
      JSON.stringify({
        success: false,
        message: '您当前处于离线状态，请检查网络连接',
        offline: true,
      }),
      {
        status: 503,
        headers: { 'Content-Type': 'application/json' },
      }
    );
  }
}

/**
 * 处理静态资源请求 - 缓存优先策略
 */
async function handleStaticRequest(request: Request): Promise<Response> {
  const cache = await caches.open(CACHE_NAME);

  const cachedResponse = await cache.match(request);
  if (cachedResponse) {
    return cachedResponse;
  }

  try {
    const networkResponse = await fetch(request);
    if (networkResponse.ok) {
      cache.put(request, networkResponse.clone());
    }
    return networkResponse;
  } catch {
    // 对于HTML请求，返回离线页面
    if (request.headers.get('Accept')?.includes('text/html')) {
      const offlineResponse = await cache.match('/');
      if (offlineResponse) {
        return offlineResponse;
      }
    }
    return new Response('Offline', { status: 503 });
  }
}

// 处理来自主线程的消息
self.addEventListener('message', (event: ExtendableMessageEvent) => {
  const { type, payload } = event.data;

  switch (type) {
    case 'CACHE_TILES':
      // 预缓存指定区域的地图瓦片
      cacheTilesForArea(payload);
      break;
    case 'CLEAR_TILE_CACHE':
      clearTileCache();
      break;
    case 'GET_CACHE_SIZE':
      getCacheSize().then((size) => {
        event.ports[0]?.postMessage({ type: 'CACHE_SIZE', size });
      });
      break;
  }
});

/**
 * 预缓存指定区域的地图瓦片
 */
async function cacheTilesForArea(area: {
  minLat: number;
  maxLat: number;
  minLng: number;
  maxLng: number;
  zoomLevels: number[];
}): Promise<void> {
  const cache = await caches.open(TILE_CACHE_NAME);
  const { minLat, maxLat, minLng, maxLng, zoomLevels } = area;

  for (const zoom of zoomLevels) {
    const tiles = getTilesForBounds(minLat, maxLat, minLng, maxLng, zoom);

    for (const tile of tiles) {
      const url = `https://a.tile.openstreetmap.org/${zoom}/${tile.x}/${tile.y}.png`;
      try {
        const response = await fetch(url);
        if (response.ok) {
          await cache.put(url, response);
        }
      } catch {
        logger.warn(`[SW] 缓存瓦片失败: ${url}`);
      }
    }
  }

  // 通知主线程缓存完成
  self.clients.matchAll().then((clients) => {
    clients.forEach((client) => {
      client.postMessage({ type: 'TILES_CACHED', area });
    });
  });
}

/**
 * 计算边界范围内的瓦片坐标
 */
function getTilesForBounds(
  minLat: number,
  maxLat: number,
  minLng: number,
  maxLng: number,
  zoom: number
): { x: number; y: number }[] {
  const tiles: { x: number; y: number }[] = [];

  const minTileX = Math.floor(((minLng + 180) / 360) * Math.pow(2, zoom));
  const maxTileX = Math.floor(((maxLng + 180) / 360) * Math.pow(2, zoom));

  const minTileY = Math.floor(
    ((1 - Math.log(Math.tan((maxLat * Math.PI) / 180) + 1 / Math.cos((maxLat * Math.PI) / 180)) / Math.PI) / 2) *
      Math.pow(2, zoom)
  );
  const maxTileY = Math.floor(
    ((1 - Math.log(Math.tan((minLat * Math.PI) / 180) + 1 / Math.cos((minLat * Math.PI) / 180)) / Math.PI) / 2) *
      Math.pow(2, zoom)
  );

  for (let x = minTileX; x <= maxTileX; x++) {
    for (let y = minTileY; y <= maxTileY; y++) {
      tiles.push({ x, y });
    }
  }

  return tiles;
}

/**
 * 清除瓦片缓存
 */
async function clearTileCache(): Promise<void> {
  await caches.delete(TILE_CACHE_NAME);
  logger.debug('[SW] 瓦片缓存已清除');
}

/**
 * 获取缓存大小
 */
async function getCacheSize(): Promise<number> {
  let totalSize = 0;

  const cacheNames = [CACHE_NAME, TILE_CACHE_NAME, DATA_CACHE_NAME];

  for (const name of cacheNames) {
    const cache = await caches.open(name);
    const keys = await cache.keys();

    for (const request of keys) {
      const response = await cache.match(request);
      if (response) {
        const blob = await response.blob();
        totalSize += blob.size;
      }
    }
  }

  return totalSize;
}

export {};
