import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const projectRoot = path.resolve(__dirname, '..')
const distAssetsDir = path.join(projectRoot, 'dist', 'assets')

const budget = {
  totalKb: Number(process.env.BUNDLE_MAX_TOTAL_KB || 2500),
  entryKb: Number(process.env.BUNDLE_MAX_ENTRY_KB || 500),
  maplibreKb: Number(process.env.BUNDLE_MAX_MAPLIBRE_KB || 1400),
  cssTotalKb: Number(process.env.BUNDLE_MAX_CSS_TOTAL_KB || 350),
}

function kb(bytes) {
  return bytes / 1024
}

function fail(msg) {
  console.error(`[budget] ${msg}`)
  process.exitCode = 1
}

if (!fs.existsSync(distAssetsDir)) {
  console.error(`[budget] Missing build output: ${distAssetsDir}`)
  process.exit(1)
}

const files = fs
  .readdirSync(distAssetsDir)
  .map((name) => {
    const fullPath = path.join(distAssetsDir, name)
    const stat = fs.statSync(fullPath)
    return { name, fullPath, bytes: stat.size }
  })
  .filter((f) => fs.statSync(f.fullPath).isFile())

const jsFiles = files.filter((f) => f.name.endsWith('.js'))
const cssFiles = files.filter((f) => f.name.endsWith('.css'))

const totalJsBytes = jsFiles.reduce((acc, f) => acc + f.bytes, 0)
const totalCssBytes = cssFiles.reduce((acc, f) => acc + f.bytes, 0)
const totalAssetsBytes = files.reduce((acc, f) => acc + f.bytes, 0)

const entryCandidate = jsFiles
  .filter((f) => !/maplibre/i.test(f.name))
  .sort((a, b) => b.bytes - a.bytes)[0]
const maplibreChunk = jsFiles.find((f) => /maplibre/i.test(f.name))

console.log('[budget] Frontend bundle budget report')
console.log(`[budget] total assets: ${kb(totalAssetsBytes).toFixed(1)} KB`)
console.log(`[budget] total js:     ${kb(totalJsBytes).toFixed(1)} KB`)
console.log(`[budget] total css:    ${kb(totalCssBytes).toFixed(1)} KB`)
if (entryCandidate) {
  console.log(`[budget] entry chunk:  ${entryCandidate.name} (${kb(entryCandidate.bytes).toFixed(1)} KB)`)
} else {
  console.log('[budget] entry chunk:  not found')
}
if (maplibreChunk) {
  console.log(`[budget] maplibre:     ${maplibreChunk.name} (${kb(maplibreChunk.bytes).toFixed(1)} KB)`)
} else {
  console.log('[budget] maplibre:     chunk not found')
}

if (kb(totalAssetsBytes) > budget.totalKb) {
  fail(`total assets ${kb(totalAssetsBytes).toFixed(1)} KB > ${budget.totalKb} KB`)
}
if (entryCandidate && kb(entryCandidate.bytes) > budget.entryKb) {
  fail(`entry chunk ${entryCandidate.name} ${kb(entryCandidate.bytes).toFixed(1)} KB > ${budget.entryKb} KB`)
}
if (maplibreChunk && kb(maplibreChunk.bytes) > budget.maplibreKb) {
  fail(`maplibre chunk ${maplibreChunk.name} ${kb(maplibreChunk.bytes).toFixed(1)} KB > ${budget.maplibreKb} KB`)
}
if (kb(totalCssBytes) > budget.cssTotalKb) {
  fail(`total css ${kb(totalCssBytes).toFixed(1)} KB > ${budget.cssTotalKb} KB`)
}

if (process.exitCode) {
  console.error('[budget] Budget check failed')
} else {
  console.log('[budget] Budget check passed')
}
