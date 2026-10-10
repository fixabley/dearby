#!/bin/sh
set -eu
# A successful deploy hook changes this marker. Reload only after nginx -t.
(
    previous=''
    while sleep 30; do
        marker=/etc/nginx/certs/.reload
        [ -f "$marker" ] || continue
        current=$(cat "$marker")
        if [ "$current" != "$previous" ]; then
            if nginx -t && nginx -s reload; then
                previous=$current
            fi
        fi
    done
) &
exec nginx -g 'daemon off;'
