#!/usr/bin/env node
'use strict'

const fs = require('fs')
const path = require('path')

function usage() {
  console.log(`Usage:
  node tools/geojson-to-graph.js --input <geojson> --output <graph.json> [--node-type <type>] [--keep-tags] [--highway <list>]

Options:
  --input        Path to GeoJSON with LineString/MultiLineString features
  --output       Path to output GraphImportRequest JSON
  --node-type    Node type for created nodes (default: default)
  --keep-tags    Store feature properties into node extraJson
  --highway      Comma-separated list of highway values to keep (optional)
`)
}

function getArg(name, fallback) {
  const idx = process.argv.indexOf(name)
  if (idx === -1) return fallback
  const value = process.argv[idx + 1]
  if (!value || value.startsWith('--')) return fallback
  return value
}

function hasFlag(name) {
  return process.argv.includes(name)
}

const input = getArg('--input')
const output = getArg('--output')
const nodeType = getArg('--node-type', 'default')
const keepTags = hasFlag('--keep-tags')
const highwayFilter = (getArg('--highway', '') || '')
  .split(',')
  .map((v) => v.trim())
  .filter(Boolean)
const allowHighway = new Set(highwayFilter)

if (!input || !output) {
  usage()
  process.exit(1)
}

function readJson(filePath) {
  const raw = fs.readFileSync(filePath, 'utf8')
  return JSON.parse(raw)
}

function writeJson(filePath, data) {
  const json = JSON.stringify(data, null, 2)
  fs.mkdirSync(path.dirname(filePath), { recursive: true })
  fs.writeFileSync(filePath, json, 'utf8')
}

function toRad(v) {
  return (v * Math.PI) / 180
}

function distanceMeters(lat1, lng1, lat2, lng2) {
  const R = 6371000
  const dLat = toRad(lat2 - lat1)
  const dLng = toRad(lng2 - lng1)
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2)
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
  return R * c
}

function normalizeBool(value) {
  if (value === null || value === undefined) return null
  const v = String(value).trim().toLowerCase()
  if (['yes', 'true', '1'].includes(v)) return true
  if (['no', 'false', '0'].includes(v)) return false
  return null
}

function parseOneway(props) {
  const v = props.oneway
  if (v === null || v === undefined) return { oneway: false, reverse: false }
  const s = String(v).trim().toLowerCase()
  if (s === '-1' || s === 'reverse' || s === 'backward') return { oneway: true, reverse: true }
  const yes = normalizeBool(s)
  return { oneway: !!yes, reverse: false }
}

function hasStairs(props) {
  const highway = String(props.highway || '').toLowerCase()
  if (highway === 'steps') return true
  const stairs = normalizeBool(props.stairs)
  if (stairs === true) return true
  const stepCount = props.step_count || props.stepCount
  return stepCount !== null && stepCount !== undefined
}

function parseSlopeLevel(value) {
  if (value === null || value === undefined) return 0
  let raw = String(value).trim().toLowerCase()
  if (!raw) return 0
  if (raw.endsWith('%')) raw = raw.slice(0, -1)
  const num = Number.parseFloat(raw)
  if (!Number.isFinite(num)) return 0
  const pct = Math.abs(num)
  if (pct >= 10) return 3
  if (pct >= 6) return 2
  if (pct >= 3) return 1
  return 0
}

function accessibleDefault(props, stairs) {
  const wheelchair = normalizeBool(props.wheelchair)
  const access = normalizeBool(props.access)
  const foot = normalizeBool(props.foot)
  if (wheelchair === false || access === false || foot === false) return false
  if (stairs) return false
  return true
}

function toLineCoords(geometry) {
  if (!geometry) return []
  if (geometry.type === 'LineString') return [geometry.coordinates || []]
  if (geometry.type === 'MultiLineString') return geometry.coordinates || []
  if (geometry.type === 'GeometryCollection') {
    const out = []
    for (const g of geometry.geometries || []) {
      out.push(...toLineCoords(g))
    }
    return out
  }
  return []
}

const geo = readJson(input)
const features = Array.isArray(geo.features) ? geo.features : []

const nodesByKey = new Map()
const edges = []
const edgeSeen = new Set()

function addNode(lng, lat, props) {
  const lngNum = Number(lng)
  const latNum = Number(lat)
  if (!Number.isFinite(lngNum) || !Number.isFinite(latNum)) return null
  const key = `${latNum.toFixed(7)},${lngNum.toFixed(7)}`
  if (nodesByKey.has(key)) return key
  const node = {
    key,
    lat: latNum.toFixed(7),
    lng: lngNum.toFixed(7),
    nodeType,
  }
  if (keepTags) {
    node.extraJson = JSON.stringify(props || {})
  }
  nodesByKey.set(key, node)
  return key
}

function addEdge(fromKey, toKey, distanceM, props) {
  if (!fromKey || !toKey || fromKey === toKey) return
  const edgeKey = `${fromKey}|${toKey}`
  if (edgeSeen.has(edgeKey)) return
  edgeSeen.add(edgeKey)

  const stairs = hasStairs(props)
  const slopeLevel = parseSlopeLevel(props.incline)
  const { oneway } = parseOneway(props)
  const accessible = accessibleDefault(props, stairs)

  edges.push({
    fromKey,
    toKey,
    distanceM: Math.round(distanceM * 100) / 100,
    oneway,
    hasStairs: stairs,
    slopeLevel,
    accessibleDefault: accessible,
    baseCost: Math.round(distanceM * 100) / 100,
  })
}

for (const feature of features) {
  const props = feature && feature.properties ? feature.properties : {}
  if (allowHighway.size) {
    const highway = String(props.highway || '').toLowerCase()
    if (!allowHighway.has(highway)) continue
  }

  const lines = toLineCoords(feature.geometry)
  if (!lines.length) continue

  const onewayInfo = parseOneway(props)
  for (const rawLine of lines) {
    if (!Array.isArray(rawLine) || rawLine.length < 2) continue
    const coords = onewayInfo.reverse ? [...rawLine].reverse() : rawLine
    for (let i = 0; i < coords.length - 1; i++) {
      const a = coords[i]
      const b = coords[i + 1]
      if (!Array.isArray(a) || !Array.isArray(b)) continue
      const [lng1, lat1] = a
      const [lng2, lat2] = b
      const fromKey = addNode(lng1, lat1, props)
      const toKey = addNode(lng2, lat2, props)
      if (!fromKey || !toKey) continue
      const dist = distanceMeters(Number(lat1), Number(lng1), Number(lat2), Number(lng2))
      addEdge(fromKey, toKey, dist, props)
    }
  }
}

const result = {
  nodes: Array.from(nodesByKey.values()),
  edges,
}

writeJson(output, result)
console.log(`Done: ${result.nodes.length} nodes, ${result.edges.length} edges -> ${output}`)
