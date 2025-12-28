import { spawn, spawnSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const here = path.dirname(fileURLToPath(import.meta.url))
const projectRoot = path.resolve(here, '..')
const repoRoot = path.resolve(projectRoot, '..')
const backendDir = path.join(repoRoot, 'backend')

function resolveNpx() {
  return process.platform === 'win32' ? 'npx.cmd' : 'npx'
}

function resolveMvn() {
  return process.platform === 'win32' ? 'mvn.cmd' : 'mvn'
}

function resolveJava() {
  return process.platform === 'win32' ? 'java.exe' : 'java'
}

function wait(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

async function isOpenapiReady(url, timeoutMs = 2000) {
  try {
    const controller = new AbortController()
    const timer = setTimeout(() => controller.abort(), timeoutMs)
    const resp = await fetch(url, { method: 'GET', signal: controller.signal })
    clearTimeout(timer)
    return resp.ok
  } catch {
    return false
  }
}

async function waitForOpenapi(url, waitSeconds) {
  const maxTries = Math.max(1, waitSeconds)
  for (let i = 0; i < maxTries; i += 1) {
    if (await isOpenapiReady(url)) return true
    await wait(1000)
  }
  return false
}

function listBackendJars() {
  const targetDir = path.join(backendDir, 'target')
  if (!fs.existsSync(targetDir)) return []
  return fs
    .readdirSync(targetDir)
    .filter((name) => /^accessible-nav-backend-.*\.jar$/i.test(name))
    .map((name) => {
      const full = path.join(targetDir, name)
      const stat = fs.statSync(full)
      return {
        full,
        mtimeMs: stat.mtimeMs,
      }
    })
    .sort((a, b) => b.mtimeMs - a.mtimeMs)
}

function runBackendBuildIfNeeded() {
  const shouldBuild = String(process.env.OPENAPI_BUILD_BACKEND || '1').trim() !== '0'
  if (!shouldBuild) {
    return
  }

  console.log('[api:gen] backend OpenAPI unavailable, building backend jar...')
  const settings = path.join(backendDir, '.mvn', 'settings.xml')
  const args = ['-f', path.join(backendDir, 'pom.xml')]
  if (fs.existsSync(settings)) {
    args.push('-s', settings)
  }
  args.push('-DskipTests', 'package', '-B')

  const isWin = process.platform === 'win32'
  const buildCmd = isWin ? (process.env.ComSpec || 'cmd.exe') : resolveMvn()
  const buildArgs = isWin ? ['/c', resolveMvn(), ...args] : args

  const result = spawnSync(buildCmd, buildArgs, {
    cwd: repoRoot,
    stdio: 'inherit',
    shell: false,
  })

  if (result.error) {
    throw result.error
  }
  if ((result.status ?? 1) !== 0) {
    throw new Error(`[api:gen] backend build failed (exit=${result.status})`)
  }
}

async function stopProcess(child) {
  if (!child || child.exitCode != null) return

  if (process.platform === 'win32') {
    await new Promise((resolve) => {
      const killer = spawn('taskkill', ['/PID', String(child.pid), '/T', '/F'], {
        stdio: 'ignore',
        shell: false,
      })
      killer.on('exit', () => resolve())
      killer.on('error', () => resolve())
    })
    return
  }

  child.kill('SIGTERM')
  await new Promise((resolve) => {
    const timer = setTimeout(() => {
      child.kill('SIGKILL')
      resolve()
    }, 2000)
    child.once('exit', () => {
      clearTimeout(timer)
      resolve()
    })
  })
}

function shouldAutoStartBackend(openapiUrl, defaultUrl) {
  const autoStart = String(process.env.OPENAPI_AUTO_START_BACKEND || '1').trim() !== '0'
  if (!autoStart) return false
  if (process.env.OPENAPI_URL) return false
  return openapiUrl === defaultUrl
}

function extractPort(urlText, fallbackPort) {
  try {
    const u = new URL(urlText)
    if (u.port) return Number(u.port)
    return u.protocol === 'https:' ? 443 : 80
  } catch {
    return fallbackPort
  }
}

async function startBackendForOpenapi(jarPath, port, profile, waitSeconds, openapiUrl) {
  console.log(`[api:gen] starting backend for OpenAPI on port ${port} (profile=${profile})...`)

  const stdoutChunks = []
  const stderrChunks = []
  const MAX_CHUNKS = 50

  const child = spawn(resolveJava(), [
    '-jar',
    jarPath,
    `--server.port=${port}`,
    `--spring.profiles.active=${profile}`,
  ], {
    cwd: repoRoot,
    stdio: ['ignore', 'pipe', 'pipe'],
    shell: false,
  })

  child.stdout?.on('data', (buf) => {
    stdoutChunks.push(String(buf))
    if (stdoutChunks.length > MAX_CHUNKS) stdoutChunks.shift()
  })
  child.stderr?.on('data', (buf) => {
    stderrChunks.push(String(buf))
    if (stderrChunks.length > MAX_CHUNKS) stderrChunks.shift()
  })

  const ready = await waitForOpenapi(openapiUrl, waitSeconds)
  if (!ready) {
    await stopProcess(child)
    const tail = `${stdoutChunks.join('')}\n${stderrChunks.join('')}`.trim()
    throw new Error(
      `[api:gen] backend did not become ready at ${openapiUrl} within ${waitSeconds}s.${tail ? `\n--- backend log tail ---\n${tail}` : ''}`
    )
  }

  return child
}

// Default backend port in this repo is 8081 (see start.ps1 / .env.example).
const defaultUrl = 'http://localhost:8081/v3/api-docs'
const openapiUrl = process.env.OPENAPI_URL || defaultUrl
const backendPort = extractPort(openapiUrl, 8081)
const backendProfile = process.env.OPENAPI_BACKEND_PROFILE || 'test'
const waitSeconds = Number(process.env.OPENAPI_WAIT_SECONDS || 90)

const outDir = path.join(projectRoot, 'src', 'api')
const outFile = path.join(outDir, 'schema.d.ts')

fs.mkdirSync(outDir, { recursive: true })

// On Windows, spawning .cmd directly with shell=false can fail (EINVAL). Use cmd.exe /c for reliability.
const isWin = process.platform === 'win32'
const cmd = isWin ? (process.env.ComSpec || 'cmd.exe') : resolveNpx()
const args = isWin
  ? ['/c', resolveNpx(), 'openapi-typescript', openapiUrl, '--output', outFile]
  : ['openapi-typescript', openapiUrl, '--output', outFile]

function runOpenapiTypes() {
  const result = spawnSync(cmd, args, {
    cwd: projectRoot,
    stdio: 'inherit',
    shell: false,
  })

  if (result.error) {
    throw result.error
  }
  if ((result.status ?? 1) !== 0) {
    throw new Error(`[api:gen] openapi-typescript failed (exit=${result.status})`)
  }
}

let bootedBackend = null

try {
  const ready = await isOpenapiReady(openapiUrl)
  if (!ready) {
    if (!shouldAutoStartBackend(openapiUrl, defaultUrl)) {
      throw new Error(
        `[api:gen] OpenAPI endpoint is unavailable: ${openapiUrl}. `
        + 'Please start backend first, or unset OPENAPI_URL to enable local auto-start fallback.'
      )
    }

    runBackendBuildIfNeeded()
    const jars = listBackendJars()
    if (!jars.length) {
      throw new Error('[api:gen] cannot find backend jar under backend/target (accessible-nav-backend-*.jar)')
    }

    bootedBackend = await startBackendForOpenapi(
      jars[0].full,
      backendPort,
      backendProfile,
      waitSeconds,
      openapiUrl,
    )
  }

  runOpenapiTypes()
  console.log(`[api:gen] done -> ${path.relative(projectRoot, outFile).replace(/\\/g, '/')}`)
} catch (err) {
  console.error(err)
  process.exitCode = 1
} finally {
  await stopProcess(bootedBackend)
}
