#!/bin/sh
set -eu
trap 'exit 0' TERM INT
while :; do
    certbot renew --non-interactive \
        --deploy-hook 'sh /opt/scripts/deploy-cert.sh' || echo 'Certificate renewal failed; check Certbot logs.' >&2
    sleep 43200 &
    wait $! || true
done
