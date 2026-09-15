import { execFileSync } from 'node:child_process'
import net from 'node:net'

const port = Number(process.argv[2])

if (!Number.isInteger(port) || port < 1 || port > 65_535) {
  process.exit(2)
}

if (process.platform === 'win32') {
  const output = execFileSync('netstat.exe', ['-ano', '-p', 'tcp'], { encoding: 'utf8' })
  const isOccupied = output
    .split(/\r?\n/)
    .some((line) => {
      const columns = line.trim().split(/\s+/)
      return columns[0] === 'TCP'
        && columns[1]?.endsWith(`:${port}`)
        && columns[3] === 'LISTENING'
    })

  process.exit(isOccupied ? 1 : 0)
}

const server = net.createServer()

server.once('error', () => {
  process.exit(1)
})

server.listen({ host: '127.0.0.1', port }, () => {
  server.close(() => process.exit(0))
})
