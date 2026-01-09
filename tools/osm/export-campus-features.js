#!/usr/bin/env node
'use strict';

const fs = require('fs');
const path = require('path');
const https = require('https');

function parseArgs(argv) {
  const out = { _: [] };
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg.startsWith('--')) {
      const key = arg.slice(2);
      const next = argv[i + 1];
      if (!next || next.startsWith('--')) {
        out[key] = true;
      } else {
        out[key] = next;
        i++;
      }
    } else {
      out._.push(arg);
    }
  }
  return out;
}

function parseBBox(value) {
  if (!value) return null;
  const parts = String(value)
    .split(',')
    .map((v) => Number(v.trim()));
  if (parts.length !== 4 || parts.some((v) => !Number.isFinite(v))) return null;
  const [minLat, minLng, maxLat, maxLng] = parts;
  return { minLat, minLng, maxLat, maxLng };
}

function usage() {
  console.log('Usage:');
  console.log('  node tools/osm/export-campus-features.js --bbox "minLat,minLng,maxLat,maxLng" --prefix gbuc-jianggao --out-dir frontend/public/data');
  console.log('');
  console.log('Options:');
  console.log('  --bbox           Required. Bounding box as minLat,minLng,maxLat,maxLng');
  console.log('  --prefix         Output file prefix (default: campus)');
  console.log('  --out-dir        Output directory (default: frontend/public/data)');
  console.log('  --overpass       Overpass API endpoint (default: https://overpass-api.de/api/interpreter)');
  console.log('  --buildings-only Export only buildings');
  console.log('  --poi-only       Export only POI');
}

function postOverpass(url, query) {
  const body = `data=${encodeURIComponent(query)}`;
  return new Promise((resolve, reject) => {
    const req = https.request(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        'Content-Length': Buffer.byteLength(body),
      },
    }, (res) => {
      let data = '';
      res.on('data', (chunk) => { data += chunk; });
      res.on('end', () => {
        if (res.statusCode && res.statusCode >= 200 && res.statusCode < 300) {
          resolve(data);
        } else {
          reject(new Error(`Overpass HTTP ${res.statusCode}: ${data.slice(0, 200)}`));
        }
      });
    });

    req.on('error', reject);
    req.write(body);
    req.end();
  });
}

function indexElements(elements) {
  const nodes = new Map();
  const ways = new Map();
  const relations = new Map();
  for (const el of elements || []) {
    if (!el || !el.type) continue;
    if (el.type === 'node') nodes.set(el.id, el);
    if (el.type === 'way') ways.set(el.id, el);
    if (el.type === 'relation') relations.set(el.id, el);
  }
  return { nodes, ways, relations };
}

function closeRing(coords) {
  if (!coords || coords.length < 3) return null;
  const first = coords[0];
  const last = coords[coords.length - 1];
  if (first[0] !== last[0] || first[1] !== last[1]) {
    coords.push([first[0], first[1]]);
  }
  if (coords.length < 4) return null;
  return coords;
}

function wayToRing(way, nodes) {
  if (!way || !Array.isArray(way.nodes)) return null;
  const coords = [];
  for (const id of way.nodes) {
    const node = nodes.get(id);
    if (!node || !Number.isFinite(node.lat) || !Number.isFinite(node.lon)) continue;
    coords.push([node.lon, node.lat]);
  }
  return closeRing(coords);
}

function pickName(tags, fallback) {
  if (!tags) return fallback;
  return tags['name:zh'] || tags.name || tags['name:en'] || tags.short_name || tags.brand || tags.operator || fallback;
}

function buildProperties(tags, fallbackName, osmId) {
  const props = {};
  const name = pickName(tags, fallbackName);
  if (name) props.name = name;
  if (tags) {
    const keys = ['building', 'amenity', 'shop', 'office', 'tourism', 'leisure', 'public_transport', 'highway', 'type', 'category', 'level'];
    for (const key of keys) {
      if (tags[key]) props[key] = tags[key];
    }
    const category = tags.amenity || tags.shop || tags.office || tags.tourism || tags.leisure || tags.public_transport || tags.highway || tags.building;
    if (category && !props.category) props.category = category;
  }
  if (osmId) props.osmId = osmId;
  props.source = 'OSM';
  return props;
}

function extractBuildingFeatures(elements) {
  const { nodes, ways, relations } = indexElements(elements);
  const features = [];

  for (const way of ways.values()) {
    if (!way.tags || !way.tags.building) continue;
    const ring = wayToRing(way, nodes);
    if (!ring) continue;
    const properties = buildProperties(way.tags, `Building ${way.id}`, `way/${way.id}`);
    features.push({
      type: 'Feature',
      properties,
      geometry: {
        type: 'Polygon',
        coordinates: [ring],
      },
    });
  }

  for (const rel of relations.values()) {
    if (!rel.tags || !rel.tags.building || !Array.isArray(rel.members)) continue;
    const polygons = [];
    for (const member of rel.members) {
      if (member.type !== 'way') continue;
      if (member.role && member.role !== 'outer') continue;
      const way = ways.get(member.ref);
      if (!way) continue;
      const ring = wayToRing(way, nodes);
      if (!ring) continue;
      polygons.push([ring]);
    }
    if (!polygons.length) continue;
    const properties = buildProperties(rel.tags, `Building ${rel.id}`, `relation/${rel.id}`);
    features.push({
      type: 'Feature',
      properties,
      geometry: {
        type: 'MultiPolygon',
        coordinates: polygons,
      },
    });
  }

  return features;
}

function extractPoiFeatures(elements) {
  const features = [];
  for (const el of elements || []) {
    if (!el || el.type !== 'node') continue;
    if (!Number.isFinite(el.lat) || !Number.isFinite(el.lon)) continue;
    if (!el.tags) continue;
    const properties = buildProperties(el.tags, `POI ${el.id}`, `node/${el.id}`);
    features.push({
      type: 'Feature',
      properties,
      geometry: {
        type: 'Point',
        coordinates: [el.lon, el.lat],
      },
    });
  }
  return features;
}

function writeGeoJson(outPath, features) {
  const fc = {
    type: 'FeatureCollection',
    features,
  };
  fs.mkdirSync(path.dirname(outPath), { recursive: true });
  fs.writeFileSync(outPath, JSON.stringify(fc, null, 2));
}

function buildBuildingQuery(bbox) {
  const bboxStr = `${bbox.minLat},${bbox.minLng},${bbox.maxLat},${bbox.maxLng}`;
  return `[out:json][timeout:60];
(
  way["building"](${bboxStr});
  relation["building"](${bboxStr});
);
out body;
>;
out skel qt;`;
}

function buildPoiQuery(bbox) {
  const bboxStr = `${bbox.minLat},${bbox.minLng},${bbox.maxLat},${bbox.maxLng}`;
  return `[out:json][timeout:60];
(
  node["amenity"](${bboxStr});
  node["shop"](${bboxStr});
  node["tourism"](${bboxStr});
  node["leisure"](${bboxStr});
  node["office"](${bboxStr});
  node["public_transport"](${bboxStr});
  node["highway"="bus_stop"](${bboxStr});
  node["building"="entrance"](${bboxStr});
);
out body;`;
}

async function run() {
  const args = parseArgs(process.argv.slice(2));
  if (args.help || args.h) {
    usage();
    process.exit(0);
  }

  const bbox = parseBBox(args.bbox || args._[0]);
  if (!bbox) {
    usage();
    process.exit(1);
  }

  const prefix = args.prefix || 'campus';
  const outDir = args['out-dir'] || path.resolve(__dirname, '..', '..', 'frontend', 'public', 'data');
  const overpass = args.overpass || 'https://overpass-api.de/api/interpreter';
  const buildingsOnly = Boolean(args['buildings-only']);
  const poiOnly = Boolean(args['poi-only']);

  if (buildingsOnly && poiOnly) {
    console.error('Choose only one of --buildings-only or --poi-only.');
    process.exit(1);
  }

  if (!poiOnly) {
    console.log('Fetching buildings from Overpass...');
    const buildingRaw = await postOverpass(overpass, buildBuildingQuery(bbox));
    const buildingJson = JSON.parse(buildingRaw);
    const features = extractBuildingFeatures(buildingJson.elements || []);
    const outPath = path.join(outDir, `${prefix}-buildings.geojson`);
    writeGeoJson(outPath, features);
    console.log(`Buildings: ${features.length} -> ${outPath}`);
  }

  if (!buildingsOnly) {
    console.log('Fetching POI from Overpass...');
    const poiRaw = await postOverpass(overpass, buildPoiQuery(bbox));
    const poiJson = JSON.parse(poiRaw);
    const features = extractPoiFeatures(poiJson.elements || []);
    const outPath = path.join(outDir, `${prefix}-poi.geojson`);
    writeGeoJson(outPath, features);
    console.log(`POI: ${features.length} -> ${outPath}`);
  }
}

run().catch((err) => {
  console.error(err && err.message ? err.message : err);
  process.exit(1);
});
