#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
sh scripts/init-local-cert.sh
mkdir -p certbot/conf certbot/www certbot/work logs/certbot
docker compose up -d nginx
# Stop the renewal worker to avoid concurrent Certbot locks.
docker compose stop certbot-renew
trap 'docker compose up -d certbot-renew' EXIT
docker compose run --rm certbot certonly \
    --webroot --webroot-path /var/www/certbot \
    --email fixabley@naver.com --agree-tos --non-interactive \
    --cert-name wid.io.kr \
    -d wid.io.kr -d dearby.wid.io.kr -d dev.dearby.wid.io.kr \
    --deploy-hook 'sh /opt/scripts/deploy-cert.sh' "$@"
docker compose exec -T nginx nginx -t
docker compose exec -T nginx nginx -s reload
