import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { spawnSync } from 'node:child_process'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const root = path.resolve(__dirname, '..')

const REPORTS_DIR = path.join(root, 'reports')

const SKIP_DIR_NAMES = new Set([
  'node_modules',
  'dist',
  'playwright-report',
  'test-results',
  'reports',
  '.git',
])

function toPosix(p) {
  return p.split(path.sep).join('/')
}

function ensureDir(dir) {
  fs.mkdirSync(dir, { recursive: true })
}

function walkFiles(startDir) {
  /** @type {string[]} */
  const out = []

  /** @param {string} dir */
  function visit(dir) {
    const entries = fs.readdirSync(dir, { withFileTypes: true })
    for (const ent of entries) {
      if (ent.isDirectory()) {
        if (SKIP_DIR_NAMES.has(ent.name)) continue
        visit(path.join(dir, ent.name))
      } else if (ent.isFile()) {
        out.push(path.join(dir, ent.name))
      }
    }
  }

  visit(startDir)
  return out
}

function safeReadJson(filePath) {
  try {
    if (!fs.existsSync(filePath)) return null
    const raw = fs.readFileSync(filePath, 'utf8')
    try {
      return JSON.parse(raw)
    } catch {
      // 某些情况下（例如被重定向的 npm 输出）文件里会混入 "> pkg@..." 行。
      // 这里做一次“宽松解析”：截取最后一个 JSON 数组/对象片段。
      const firstArray = raw.indexOf('[')
      const lastArray = raw.lastIndexOf(']')
      if (firstArray >= 0 && lastArray > firstArray) {
        return JSON.parse(raw.slice(firstArray, lastArray + 1))
      }
      const firstObj = raw.indexOf('{')
      const lastObj = raw.lastIndexOf('}')
      if (firstObj >= 0 && lastObj > firstObj) {
        return JSON.parse(raw.slice(firstObj, lastObj + 1))
      }
      return null
    }
  } catch {
    return null
  }
}

function safeReadText(filePath) {
  try {
    return fs.readFileSync(filePath, 'utf8')
  } catch {
    return ''
  }
}

function binName(name) {
  return process.platform === 'win32' ? `${name}.cmd` : name
}

function runLocalBin(name, args, { cwd }) {
  const binPath = path.join(root, 'node_modules', '.bin', binName(name))
  const result = spawnSync(binPath, args, {
    cwd,
    encoding: 'utf8',
    maxBuffer: 10 * 1024 * 1024,
    // Windows 下直接执行 .cmd 往往需要 shell
    shell: process.platform === 'win32',
  })
  return {
    status: result.status,
    stdout: result.stdout || '',
    stderr: result.stderr || '',
  }
}

function getCandidatesInDir(relDir, exts) {
  const absDir = path.join(root, relDir)
  if (!fs.existsSync(absDir)) return []
  const files = walkFiles(absDir)
  return files
    .filter((f) => exts.some((ext) => f.endsWith(ext)))
    .map((f) => ({
      abs: f,
      rel: toPosix(path.relative(root, f)),
      base: path.basename(f),
    }))
}

function resolveImport(fromFileAbs, spec) {
  const srcRoot = path.join(root, 'src')
  const fromDir = path.dirname(fromFileAbs)

  let resolvedBase = null
  if (spec.startsWith('@/')) {
    resolvedBase = path.join(srcRoot, spec.slice(2))
  } else if (spec.startsWith('/src/')) {
    resolvedBase = path.join(root, spec.slice(1))
  } else if (spec.startsWith('src/')) {
    resolvedBase = path.join(root, spec)
  } else if (spec.startsWith('/')) {
    resolvedBase = path.join(root, spec.slice(1))
  } else if (spec.startsWith('.')) {
    resolvedBase = path.resolve(fromDir, spec)
  } else {
    // package import, ignore
    return null
  }

  const tryPaths = []
  tryPaths.push(resolvedBase)
  for (const ext of ['.js', '.mjs', '.cjs', '.vue', '.json']) {
    tryPaths.push(resolvedBase + ext)
  }
  for (const ext of ['.js', '.mjs', '.cjs', '.vue', '.json']) {
    tryPaths.push(path.join(resolvedBase, `index${ext}`))
  }

  for (const p of tryPaths) {
    try {
      if (fs.existsSync(p) && fs.statSync(p).isFile()) return p
    } catch {
      // ignore
    }
  }
  return null
}

function extractImportSpecifiers(code) {
  /** @type {string[]} */
  const specs = []

  const importRe = /\bimport\s+(?:[^'"\n]+\s+from\s+)?['"]([^'"]+)['"]/g
  const importSideEffectRe = /\bimport\s*\(\s*['"]([^'"]+)['"]\s*\)/g
  const exportRe = /\bexport\s+(?:\*\s+from|\{[^}]*\}\s+from)\s+['"]([^'"]+)['"]/g

  for (const re of [importRe, importSideEffectRe, exportRe]) {
    re.lastIndex = 0
    let m
    while ((m = re.exec(code))) {
      if (m[1]) specs.push(m[1])
    }
  }

  return specs
}

function extractVueScriptBlocks(vueText) {
  /** @type {string[]} */
  const blocks = []

  const scriptBlockRe = /<script\b[^>]*>([\s\S]*?)<\/script>/gi
  let m
  while ((m = scriptBlockRe.exec(vueText))) {
    blocks.push(m[1] || '')
  }

  // handle <script src="..." /> case
  const scriptSrcRe = /<script\b[^>]*\bsrc=['"]([^'"]+)['"][^>]*\/>/gi
  while ((m = scriptSrcRe.exec(vueText))) {
    if (m[1]) blocks.push(`import '${m[1]}'`)
  }

  return blocks
}

function buildReachableFromEntries(entryAbsFiles) {
  /** @type {Set<string>} */
  const reachable = new Set()
  /** @type {Map<string, Set<string>>} */
  const importersByTarget = new Map()

  /** @type {string[]} */
  const queue = []
  for (const f of entryAbsFiles) {
    if (f && fs.existsSync(f)) queue.push(f)
  }

  while (queue.length) {
    const current = queue.shift()
    if (!current || reachable.has(current)) continue
    reachable.add(current)

    const ext = path.extname(current).toLowerCase()
    const raw = safeReadText(current)
    const code = ext === '.vue' ? extractVueScriptBlocks(raw).join('\n') : raw
    const specs = extractImportSpecifiers(code)

    for (const spec of specs) {
      const resolved = resolveImport(current, spec)
      if (!resolved) continue
      if (!resolved.startsWith(path.join(root, 'src'))) continue

      if (!importersByTarget.has(resolved)) importersByTarget.set(resolved, new Set())
      importersByTarget.get(resolved).add(current)

      if (!reachable.has(resolved)) queue.push(resolved)
    }
  }

  return { reachable, importersByTarget }
}

function findUnusedByReachability({ category, relDir, exts, reachable, allowlist = [] }) {
  const allow = new Set(allowlist)
  const candidates = getCandidatesInDir(relDir, exts).filter((c) => !allow.has(c.rel))

  const unused = []
  const used = []

  for (const c of candidates) {
    if (reachable.has(c.abs)) used.push(c)
    else unused.push(c)
  }

  return { category, unused, used, total: candidates.length }
}

function summarizeEslintReport(eslintReport) {
  if (!Array.isArray(eslintReport)) return null

  let errorCount = 0
  let warningCount = 0
  /** @type {Map<string, number>} */
  const ruleCounts = new Map()

  for (const file of eslintReport) {
    errorCount += Number(file.errorCount || 0)
    warningCount += Number(file.warningCount || 0)

    const messages = Array.isArray(file.messages) ? file.messages : []
    for (const msg of messages) {
      const ruleId = msg?.ruleId || 'unknown'
      ruleCounts.set(ruleId, (ruleCounts.get(ruleId) || 0) + 1)
    }
  }

  const topRules = [...ruleCounts.entries()]
    .sort((a, b) => b[1] - a[1])
    .slice(0, 15)
    .map(([ruleId, count]) => ({ ruleId, count }))

  return { errorCount, warningCount, topRules }
}

function mdEscape(s) {
  return String(s).replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

function renderMd({ depcheck, eslintSummary, unusedFindings }) {
  const lines = []

  lines.push('# 清理候选报告')
  lines.push('')
  lines.push(`生成时间：${new Date().toISOString()}`)
  lines.push('')

  lines.push('## 依赖检查（depcheck）')
  if (!depcheck) {
    lines.push('- 未找到 depcheck 报告（reports/depcheck.json）')
  } else {
    const unusedDeps = depcheck.dependencies || []
    const unusedDevDeps = depcheck.devDependencies || []
    const missing = depcheck.missing || {}

    lines.push(`- 未使用 dependencies：${unusedDeps.length}`)
    lines.push(`- 未使用 devDependencies：${unusedDevDeps.length}`)
    lines.push(`- 缺失依赖（missing）：${Object.keys(missing).length}`)
  }
  lines.push('')

  lines.push('## ESLint（聚合）')
  if (!eslintSummary) {
    lines.push('- 未找到/无法解析 ESLint 报告（reports/eslint-report.json）')
  } else {
    lines.push(`- errors：${eslintSummary.errorCount}`)
    lines.push(`- warnings：${eslintSummary.warningCount}`)
    if (eslintSummary.topRules.length) {
      lines.push('- Top rules：')
      for (const r of eslintSummary.topRules) {
        lines.push(`  - ${mdEscape(r.ruleId)}: ${r.count}`)
      }
    }
  }
  lines.push('')

  lines.push('## 源码未引用候选（import 可达性）')
  lines.push('说明：以下候选基于入口文件（src/main.*、src/App.vue）的 import 图谱可达性；动态 import/运行时引用/全局注册等场景可能导致误判，删除前建议人工确认，并用 build+e2e 做门禁。')
  lines.push('')

  for (const f of unusedFindings) {
    lines.push(`### ${f.category}`)
    lines.push(`- 扫描文件数：${f.total}`)
    lines.push(`- 未引用候选：${f.unused.length}`)
    if (f.unused.length) {
      for (const item of f.unused) {
        lines.push(`  - ${item.rel}`)
      }
    }
    lines.push('')
  }

  return lines.join('\n')
}

function main() {
  ensureDir(REPORTS_DIR)

  const depcheckPath = path.join(REPORTS_DIR, 'depcheck.json')
  let depcheck = safeReadJson(depcheckPath)
  if (!depcheck) {
    const dep = runLocalBin('depcheck', [
      '--json',
      '--ignore-patterns=dist,playwright-report,test-results,reports',
    ], { cwd: root })
    if (dep.status === 0 && dep.stdout.trim().startsWith('{')) {
      fs.writeFileSync(depcheckPath, dep.stdout, 'utf8')
      depcheck = safeReadJson(depcheckPath)
    }
  }

  const eslintReportPath = path.join(REPORTS_DIR, 'eslint-report.json')
  let eslintReport = safeReadJson(eslintReportPath)
  if (!eslintReport) {
    const eslint = runLocalBin('eslint', ['.', '--format', 'json'], { cwd: root })
    if (eslint.stdout && eslint.stdout.trim().startsWith('[')) {
      fs.writeFileSync(eslintReportPath, eslint.stdout, 'utf8')
      eslintReport = safeReadJson(eslintReportPath)
    }
  }

  const eslintSummary = summarizeEslintReport(eslintReport)

  const entryCandidates = [
    path.join(root, 'src', 'main.js'),
    path.join(root, 'src', 'main.ts'),
    path.join(root, 'src', 'App.vue'),
  ]
  const { reachable } = buildReachableFromEntries(entryCandidates)

  const unusedFindings = [
    findUnusedByReachability({
      category: 'Vue Components (src/components)',
      relDir: 'src/components',
      exts: ['.vue'],
      reachable,
    }),
    findUnusedByReachability({
      category: 'Composables (src/composables)',
      relDir: 'src/composables',
      exts: ['.js', '.mjs'],
      reachable,
    }),
    findUnusedByReachability({
      category: 'Utils (src/utils)',
      relDir: 'src/utils',
      exts: ['.js', '.mjs'],
      reachable,
    }),
  ]

  const outJson = {
    generatedAt: new Date().toISOString(),
    depcheckSummary: depcheck
      ? {
          dependencies: (depcheck.dependencies || []).length,
          devDependencies: (depcheck.devDependencies || []).length,
          missing: Object.keys(depcheck.missing || {}).length,
        }
      : null,
    eslintSummary,
    unusedFindings: unusedFindings.map((x) => ({
      category: x.category,
      total: x.total,
      unused: x.unused.map((u) => u.rel),
    })),
  }

  fs.writeFileSync(path.join(REPORTS_DIR, 'cleanup-candidates.json'), JSON.stringify(outJson, null, 2), 'utf8')

  const outMd = renderMd({ depcheck, eslintSummary, unusedFindings })
  fs.writeFileSync(path.join(REPORTS_DIR, 'cleanup-candidates.md'), outMd, 'utf8')

  console.log('Wrote reports/cleanup-candidates.md and reports/cleanup-candidates.json')
}

main()
