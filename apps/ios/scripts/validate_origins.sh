#!/usr/bin/env bash
# Release builds need https://<domain> origins: no IP, port, path or upper case.
# Debug may leave them empty and falls back to local development servers in code.
set -euo pipefail
[[ "${CONFIGURATION:-}" == "Release" ]] || exit 0
domain='^https://([a-z0-9]([a-z0-9-]*[a-z0-9])?\.)+[a-z]([a-z0-9-]*[a-z0-9])?$'
for name in DEARBY_API_ORIGIN DEARBY_WEB_ORIGIN; do
  value="${!name:-}"
  if [[ ! "$value" =~ $domain ]]; then
    echo "error: $name must be https://<domain> without IP, port or path for Release builds (got '${value}')." >&2
    exit 1
  fi
done
