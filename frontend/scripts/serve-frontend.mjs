import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

import { angularDefineArguments, frontendConfig } from './frontend-env.mjs'

const config = frontendConfig()
const forwardedArguments = process.argv.slice(2).filter((argument) => argument !== '--')

const angularCli = fileURLToPath(
  new URL('../node_modules/@angular/cli/bin/ng.js', import.meta.url),
)

const angularArguments = [
  angularCli,
  'serve',
  ...forwardedArguments,
  ...angularDefineArguments(config),
]

const result = spawnSync(process.execPath, angularArguments, {
  stdio: 'inherit',
})

if (result.error) {
  console.error(`Could not start the Angular development server: ${result.error.message}`)
  process.exit(1)
}

process.exit(result.status ?? 1)
