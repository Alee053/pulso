import { spawnSync } from 'node:child_process'

const forbiddenVariables = [
  'SUPABASE_SERVICE_ROLE_KEY',
  'SUPABASE_JWT_SECRET',
  'SPRING_DATASOURCE_PASSWORD',
  'DATABASE_URL',
  'POSTGRES_PASSWORD',
]

const configuredForbiddenVariables = forbiddenVariables.filter(
  (name) => process.env[name],
)

if (configuredForbiddenVariables.length > 0) {
  console.error(
    `Frontend build refuses server-only variables: ${configuredForbiddenVariables.join(', ')}`,
  )
  process.exit(1)
}

const definitions = [
  [
    '__PULSO_API_BASE_URL__',
    publicUrl('PULSO_API_BASE_URL', 'http://localhost:8080'),
  ],
  [
    '__PULSO_SUPABASE_URL__',
    publicUrl('PULSO_SUPABASE_URL', 'http://127.0.0.1:54321'),
  ],
  [
    '__PULSO_SUPABASE_PUBLISHABLE_KEY__',
    publicKey('PULSO_SUPABASE_PUBLISHABLE_KEY'),
  ],
]

const angularArguments = ['exec', 'ng', 'build']
for (const [identifier, value] of definitions) {
  angularArguments.push('--define', `${identifier}=${JSON.stringify(value)}`)
}

const result = spawnSync('pnpm', angularArguments, { stdio: 'inherit' })
if (result.error) {
  console.error(`Could not start the Angular build: ${result.error.message}`)
  process.exit(1)
}

process.exit(result.status ?? 1)

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
