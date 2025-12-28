import type maplibregl from 'maplibre-gl'

let maplibreglModule: typeof maplibregl | null = null

export async function loadMapDeps(): Promise<{ maplibregl: typeof maplibregl }> {
  if (maplibreglModule) {
    return { maplibregl: maplibreglModule }
  }
  const [maplibreMod] = await Promise.all([
    import('maplibre-gl'),
    import('maplibre-gl/dist/maplibre-gl.css'),
  ])
  maplibreglModule = (maplibreMod.default || maplibreMod) as typeof maplibregl
  return { maplibregl: maplibreglModule }
}
