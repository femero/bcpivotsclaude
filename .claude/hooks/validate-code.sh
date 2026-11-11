#!/usr/bin/env bash
# PostToolUse hook: formats the project with Spotless after a .java file is edited.
set -euo pipefail

file_path="$(jq -r '.tool_input.file_path // empty')"

case "$file_path" in
  *.java) cd "${CLAUDE_PROJECT_DIR:-.}" && mvn -q spotless:apply >&2 ;;
esac
