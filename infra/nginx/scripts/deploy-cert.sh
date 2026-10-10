#!/bin/sh
set -eu
[ "${RENEWED_LINEAGE:-}" = /etc/letsencrypt/live/wid.io.kr ] || exit 0
umask 077
cp "$RENEWED_LINEAGE/fullchain.pem" /etc/nginx/certs/fullchain.pem.new
cp "$RENEWED_LINEAGE/privkey.pem" /etc/nginx/certs/privkey.pem.new
chmod 644 /etc/nginx/certs/fullchain.pem.new
mv /etc/nginx/certs/privkey.pem.new /etc/nginx/certs/privkey.pem
mv /etc/nginx/certs/fullchain.pem.new /etc/nginx/certs/fullchain.pem
sha256sum /etc/nginx/certs/fullchain.pem > /etc/nginx/certs/.reload.new
mv /etc/nginx/certs/.reload.new /etc/nginx/certs/.reload
