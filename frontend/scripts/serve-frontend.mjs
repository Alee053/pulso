import { spawnSync } from 'node:child_process'

import { angularDefineArguments, frontendConfig } from './frontend-env.mjs'

const config = frontendConfig()
const forwardedArguments = process.argv.slice(2).filter((argument) => argument !== '--')

const angularArguments = [
  'exec',
  'ng',
  'serve',
  ...forwardedArguments,
  ...angularDefineArguments(config),
]

const result = spawnSync('pnpm.cmd', angularArguments, { stdio: 'inherit' })
if (result.error) {
  console.error(`Could not start the Angular development server: ${result.error.message}`)
  process.exit(1)
}

process.exit(result.status ?? 1)
