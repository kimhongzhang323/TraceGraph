#!/usr/bin/env bash
# Compile-on-save: catches typos and broken imports immediately after an edit.
# Runs only when a Java file under any module's src/ was just touched.
set -e

cd "$(git rev-parse --show-toplevel 2>/dev/null || echo .)"

# Read tool input from stdin (Claude Code hook protocol). If we can't tell what changed, do nothing.
if ! command -v mvn >/dev/null 2>&1; then
  exit 0
fi

# Project targets JDK 21; pick it up if Maven's default Java is older.
if [ -d "/c/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot" ]; then
  export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

input=$(cat || true)
case "$input" in
  *"src/main/java"*|*"src/test/java"*|*"pom.xml"*)
    echo "[lint-on-save] mvn -q -B -pl tracegraph-core compile test-compile"
    mvn -q -B -pl tracegraph-core compile test-compile || {
      echo "[lint-on-save] compilation failed" >&2
      exit 2
    }
    ;;
  *)
    exit 0
    ;;
esac
