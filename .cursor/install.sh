#!/usr/bin/env bash
# Idempotent repository bootstrap for the Pulso Cloud Agent environment.
# System packages (Docker, Node 24, Java, Chrome) live in the base snapshot;
# this script only refreshes source-derived state after checkout.
set -euo pipefail

# Node 24 / pnpm are installed under /opt/pulso/bin in the base snapshot and
# must win over any earlier `node` on PATH.
export PATH="/opt/pulso/bin:${PATH}"

cd "$(dirname "$0")/.."

echo "[install] node: $(node --version)"
echo "[install] pnpm: $(pnpm --version)"

echo "[install] installing workspace dependencies"
pnpm install --frozen-lockfile

# Local-only development configuration. The example files hold public,
# local-only fixtures; never replace them with hosted credentials.
if [ ! -f .env ]; then
  cp .env.example .env
  echo "[install] created .env from .env.example"
fi
if [ ! -f frontend/.env.development ]; then
  cp frontend/.env.development.example frontend/.env.development
  echo "[install] created frontend/.env.development"
fi

# Warm the Gradle distribution and backend dependency cache so the first
# backend run in a booted agent is fast. Safe to re-run.
echo "[install] warming backend Gradle cache"
./backend/gradlew -p backend --no-daemon classes

echo "[install] done"
