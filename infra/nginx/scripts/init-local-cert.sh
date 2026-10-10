#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p certs logs
if [ -e certs/fullchain.pem ] || [ -e certs/privkey.pem ]; then
    echo 'Certificate files already exist; leaving them unchanged.'
    exit 0
fi
umask 077
openssl req -x509 -nodes -newkey rsa:2048 -sha256 -days 30 \
    -keyout certs/privkey.pem \
    -out certs/fullchain.pem \
    -subj '/CN=wid.io.kr' \
    -addext 'subjectAltName=DNS:wid.io.kr,DNS:dearby.wid.io.kr,DNS:dev.dearby.wid.io.kr'
chmod 644 certs/fullchain.pem
echo 'Created a self-signed certificate for local testing (valid for 30 days).'
