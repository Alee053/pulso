#!/usr/bin/env bash
# Per-boot reconciliation for the Pulso Cloud Agent environment.
# Starts the Docker daemon (nested-container friendly), applies the bridge
# networking fix, brings up the local Supabase Compose stack, and launches the
# backend and frontend dev servers in the background. Idempotent: safe to run
# on every boot and tolerant of an already-running daemon/stack/servers.
set -euo pipefail

export PATH="/opt/pulso/bin:${PATH}"
cd "$(dirname "$0")/.."

log() { echo "[start] $*"; }

# 1. Start dockerd if it is not already serving.
if ! sudo docker info >/dev/null 2>&1; then
  log "starting dockerd"
  sudo rm -f /var/run/docker.pid
  sudo bash -c 'nohup dockerd >/var/log/dockerd.log 2>&1 &'
  for _ in $(seq 1 30); do
    if sudo docker info >/dev/null 2>&1; then break; fi
    sleep 1
  done
fi
sudo docker info >/dev/null 2>&1 || { log "dockerd failed to start"; sudo tail -n 40 /var/log/dockerd.log || true; exit 1; }
log "dockerd ready"

# 2. Nested-container bridge fix: intra-bridge (container-to-container) traffic
# must bypass the nft FORWARD chain, otherwise Supabase services cannot reach
# Postgres. This sysctl is kernel runtime state and resets on every boot.
if [ -e /proc/sys/net/bridge/bridge-nf-call-iptables ]; then
  sudo sysctl -w net.bridge.bridge-nf-call-iptables=0 >/dev/null
  sudo sysctl -w net.bridge.bridge-nf-call-ip6tables=0 >/dev/null
  log "bridge-nf-call-iptables disabled"
fi

# 3. Bring up the local Supabase stack and apply migrations + seed.
log "bringing up Supabase Compose stack"
sudo docker compose up -d

# 4. Wait for the Supabase API gateway to report healthy.
for _ in $(seq 1 60); do
  if curl -fsS -o /dev/null http://127.0.0.1:54321/auth/v1/health 2>/dev/null; then
    log "Supabase Auth healthy"
    break
  fi
  sleep 2
done

# 5. Launch the application tiers in the background so a booted agent has the
# full stack running. Both are non-fatal: infrastructure (above) is what a
# successful start guarantees, and each tier is guarded so re-runs do not
# spawn duplicates. Logs are written to /tmp for inspection.
port_in_use() { ss -ltnH 2>/dev/null | grep -qE "[:.]$1[[:space:]]"; }

if port_in_use 8080; then
  log "backend already running on :8080"
else
  log "starting backend (logs: /tmp/pulso-backend.log)"
  setsid bash -lc 'export PATH=/opt/pulso/bin:$PATH; cd "'"$PWD"'" && pnpm backend:run' \
    >/tmp/pulso-backend.log 2>&1 < /dev/null &
fi

if port_in_use 4200; then
  log "frontend already running on :4200"
else
  log "starting frontend (logs: /tmp/pulso-frontend.log)"
  setsid bash -lc 'export PATH=/opt/pulso/bin:$PATH; cd "'"$PWD"'" && pnpm dev' \
    >/tmp/pulso-frontend.log 2>&1 < /dev/null &
fi

log "done"
