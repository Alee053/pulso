import { spawnSync } from 'node:child_process'

import { angularDefineArguments, frontendConfig } from './frontend-env.mjs'

const config = frontendConfig()

const angularArguments = ['exec', 'ng', 'build', ...angularDefineArguments(config)]

const result = spawnSync('pnpm', angularArguments, { stdio: 'inherit' })
if (result.error) {
  console.error(`Could not start the Angular build: ${result.error.message}`)
  process.exit(1)
}

process.exit(result.status ?? 1)
