#!/usr/bin/env bash
# Pre-commit verification gate: full build + tests + japicmp API-compat check.
# On Windows the default JAVA_HOME may point at JDK 17; the build requires 21.
set -euo pipefail

if [[ -d "C:\\Program Files\\Eclipse Adoptium\\jdk-21.0.4.7-hotspot" ]]; then
  current_major=$("${JAVA_HOME:-}/bin/java" -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' || echo 0)
  if [[ "${current_major:-0}" -lt 21 ]]; then
    export JAVA_HOME="C:\\Program Files\\Eclipse Adoptium\\jdk-21.0.4.7-hotspot"
    export PATH="$JAVA_HOME/bin:$PATH"
  fi
fi

cd "$(dirname "$0")/.."
mvn -B -ntp -Pquality verify
