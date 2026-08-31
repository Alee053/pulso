import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const defaultEnvFile = path.join(frontendRoot, '.env.development')

const forbiddenVariables = [
  'SUPABASE_SERVICE_ROLE_KEY',
  'SUPABASE_JWT_SECRET',
  'SPRING_DATASOURCE_PASSWORD',
  'DATABASE_URL',
  'POSTGRES_PASSWORD',
]

export function loadFrontendEnv() {
  const envFile = process.env.PULSO_FRONTEND_ENV_FILE || defaultEnvFile

  if (!fs.existsSync(envFile)) {
    return
  }

  const entries = parseEnvFile(envFile)
  for (const [name, value] of entries) {
    if (process.env[name] === undefined) {
      process.env[name] = value
    }
  }
}

export function frontendConfig() {
  loadFrontendEnv()

  const configuredForbiddenVariables = forbiddenVariables.filter(
    (name) => process.env[name],
  )

  if (configuredForbiddenVariables.length > 0) {
    throw new Error(
      `Frontend build refuses server-only variables: ${configuredForbiddenVariables.join(', ')}`,
    )
  }

  return {
    apiBaseUrl: publicUrl('PULSO_API_BASE_URL', 'http://localhost:8080'),
    supabaseUrl: publicUrl('PULSO_SUPABASE_URL', 'http://127.0.0.1:54321'),
    supabasePublishableKey: publicKey('PULSO_SUPABASE_PUBLISHABLE_KEY'),
  }
}

export function angularDefineArguments(config) {
  return [
    '--define',
    `__PULSO_API_BASE_URL__=${JSON.stringify(config.apiBaseUrl)}`,
    '--define',
    `__PULSO_SUPABASE_URL__=${JSON.stringify(config.supabaseUrl)}`,
    '--define',
    `__PULSO_SUPABASE_PUBLISHABLE_KEY__=${JSON.stringify(config.supabasePublishableKey)}`,
  ]
}

function parseEnvFile(envFile) {
  const entries = []
  const lines = fs.readFileSync(envFile, 'utf8').split(/\r?\n/)

  for (const [index, rawLine] of lines.entries()) {
    const lineNumber = index + 1
    const line = rawLine.trim()

    if (!line || line.startsWith('#')) {
      continue
    }

    const separator = line.indexOf('=')
    if (separator <= 0) {
      throw new Error(`${envFile}:${lineNumber} must use KEY=value syntax.`)
    }

    const name = line.slice(0, separator).trim()
    if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(name)) {
      throw new Error(`${envFile}:${lineNumber} contains an invalid variable name.`)
    }

    entries.push([name, parseValue(line.slice(separator + 1).trim(), envFile, lineNumber)])
  }

  return entries
}

function parseValue(value, envFile, lineNumber) {
  if (value.startsWith('"') || value.startsWith("'")) {
    const quote = value[0]
    if (value.length < 2 || value.at(-1) !== quote) {
      throw new Error(`${envFile}:${lineNumber} contains an unterminated value.`)
    }

    const unquoted = value.slice(1, -1)
    return quote === '"'
      ? unquoted.replace(/\\n/g, '\n').replace(/\\"/g, '"').replace(/\\\\/g, '\\')
      : unquoted
  }

  return value
}

function publicUrl(name, fallback) {
  const value = (process.env[name] ?? fallback).trim().replace(/\/+$/, '')
  let parsedUrl

  try {
    parsedUrl = new URL(value)
  } catch {
    throw new Error(`${name} must be an absolute HTTP(S) URL.`)
  }

  if (
    !['http:', 'https:'].includes(parsedUrl.protocol)
    || parsedUrl.username
    || parsedUrl.password
  ) {
    throw new Error(`${name} must be an absolute HTTP(S) URL without credentials.`)
  }

  return value
}

function publicKey(name) {
  const value = (process.env[name] ?? '').trim()
  if (hasServiceRoleClaim(value)) {
    throw new Error(`${name} must contain a publishable/anon key, not a service-role token.`)
  }
  return value
}

function hasServiceRoleClaim(value) {
  const payload = value.split('.')[1]
  if (!payload) {
    return false
  }

  try {
    const claims = JSON.parse(Buffer.from(payload, 'base64url').toString('utf8'))
    return claims.role === 'service_role'
  } catch {
    return false
  }
}
