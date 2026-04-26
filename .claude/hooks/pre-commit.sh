#!/usr/bin/env bash
# Pre-commit-style gate run on Stop. Fails the commit (non-zero exit) if tests are broken.
# Cheap path: only runs full test on langgraph-core (the only module with code in Phase 1).
set -e

cd "$(git rev-parse --show-toplevel 2>/dev/null || echo .)"

if ! command -v mvn >/dev/null 2>&1; then
  echo "[pre-commit] mvn not on PATH — skipping" >&2
  exit 0
fi

echo "[pre-commit] running mvn -q -B -pl langgraph-core test"
mvn -q -B -pl langgraph-core test
