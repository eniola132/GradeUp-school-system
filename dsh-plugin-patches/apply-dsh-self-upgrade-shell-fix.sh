#!/usr/bin/env bash
# Re-apply the local shell-service fix to dsh-self-upgrade.
#
# dsh-self-upgrade v1.1.4 calls `shell.run(spec)`, which does not exist on
# DSH 0.2.0-rc.2: the Host exposes only `shell.resolve()` and
# `shell.execute()`. Every dsh_upgrade_* tool therefore fails with
# "shell.run is not a function". This rewrites that one call.
#
# Idempotent: safe to run repeatedly, and after reinstalling the plugin.
set -euo pipefail

TARGET="${1:-/home/ak-david-dev/.dsh/profiles/web/node_modules/dsh-self-upgrade/index.js}"

if [ ! -f "$TARGET" ]; then
  echo "not found: $TARGET" >&2
  exit 1
fi

if ! grep -q 'await shell\.run(spec);' "$TARGET"; then
  if grep -q 'await shell\.execute(spec);' "$TARGET"; then
    echo "already patched: $TARGET"
    exit 0
  fi
  echo "unexpected file shape; neither shell.run nor shell.execute found" >&2
  exit 1
fi

cp -p "$TARGET" "$TARGET.bak-shellfix"

python3 - "$TARGET" <<'PY'
import sys

path = sys.argv[1]
with open(path, encoding="utf-8") as fh:
    src = fh.read()

old = "  const r = await shell.run(spec);\n"
new = (
    "  const execution = await shell.execute(spec);\n"
    "  const r = typeof execution.result === 'function' ? await execution.result() : execution;\n"
)

if old not in src:
    raise SystemExit("expected call not found; nothing changed")

with open(path, "w", encoding="utf-8") as fh:
    fh.write(src.replace(old, new, 1))
PY

node --check "$TARGET"
echo "patched and syntax-checked: $TARGET (backup: $TARGET.bak-shellfix)"
