# Nginx HTTPS reverse proxy

모든 볼륨은 `compose.yaml`이 있는 폴더 기준의 bind mount입니다.

| 로컬 경로 | 컨테이너 경로 | 용도 |
| --- | --- | --- |
| `./nginx/conf.d` | `/etc/nginx/conf.d` | Nginx 설정 (읽기 전용) |
| `./certs` | `/etc/nginx/certs` | TLS 인증서 (읽기 전용) |
| `./logs` | `/var/log/nginx` | access.log, error.log |
| `./html` | `/usr/share/nginx/html` | dearby 정적 페이지 (읽기 전용) |

## 라우팅

- ACME 인증 경로를 제외한 HTTP 80 → HTTPS 443으로 308 리다이렉트합니다. 경로·쿼리 및 요청 메서드를 보존합니다.
- `wid.io.kr`은 기본 서버이며 HTTPS에서 간단한 텍스트를 반환합니다.
- `https://dearby.wid.io.kr/api` 및 `/api/...` → Docker 호스트의 `3000` 포트
- `https://dev.dearby.wid.io.kr/api` 및 `/api/...` → Docker 호스트의 `3001` 포트
- `/api` 접두사와 쿼리를 그대로 전달합니다. 예: `/api/users?page=1` → `http://host.docker.internal:3000/api/users?page=1`.
- `https://dearby.wid.io.kr/`은 `./html/index.html`의 Nginx 기본 페이지를 제공합니다. `/api` 이외 경로는 `./html`의 정적 파일을 제공하며 없는 파일은 404를 반환합니다.
- 80포트는 HTTPS 리다이렉트 전용이므로 반복 리다이렉트를 피하기 위해 정적 페이지는 443에서 직접 제공합니다.
- `dev.dearby.wid.io.kr`의 `/api` 이외 경로는 404를 반환합니다.

백엔드는 Docker 호스트에서 실행하는 것으로 가정합니다. Linux에서는 Docker bridge에서 접근 가능한 주소(예: `0.0.0.0`)에 바인딩해야 합니다. 별도 컨테이너의 백엔드라면 호스트에 3000/3001을 publish하거나, 공유 Docker 네트워크와 서비스 이름을 사용하도록 upstream을 변경하세요.

## 실행

운영 인증서가 있다면 `certs/fullchain.pem`, `certs/privkey.pem`에 먼저 배치하세요.
인증서가 없는 로컬 테스트에서는 다음 명령으로 30일 유효한 자체 서명 인증서를 생성합니다. 기존 인증서는 덮어쓰지 않습니다.

```sh
sh scripts/init-local-cert.sh
docker compose up -d
docker compose exec nginx nginx -t
```

자체 서명 인증서는 브라우저 신뢰 경고가 발생하므로 운영에서는 신뢰할 수 있는 CA 인증서로 교체해야 합니다.
인증서는 `wid.io.kr`, `dearby.wid.io.kr`, `dev.dearby.wid.io.kr` 세 이름을 모두 포함해야 합니다. `*.wid.io.kr`만으로는 `dev.dearby.wid.io.kr`을 포함하지 못합니다.
Certbot 인증서는 아래 구성으로 자동 갱신됩니다. 수동 관리하는 인증서는 갱신 후 실제 PEM 파일을 위 경로에 복사하고 Nginx를 reload하세요. 외부 경로를 가리키는 심볼릭 링크는 컨테이너에서 접근할 수 없습니다.

```sh
docker compose exec nginx nginx -t
docker compose exec nginx nginx -s reload
```

세 도메인의 DNS A/AAAA 레코드를 실제 서버로 연결하고 서버의 80/443 포트를 열어야 외부에서 접근할 수 있습니다.

## 확인 및 관리

```sh
curl -I --resolve dearby.wid.io.kr:80:127.0.0.1 http://dearby.wid.io.kr/api
curl -k --resolve wid.io.kr:443:127.0.0.1 https://wid.io.kr/
curl -k --resolve dearby.wid.io.kr:443:127.0.0.1 https://dearby.wid.io.kr/api
docker compose logs --tail=50 nginx
tail -n 50 logs/error.log
docker compose down
```

`curl -k`는 자체 서명 인증서를 사용하는 로컬 테스트용입니다. 백엔드가 실행 중이지 않으면 API 요청은 502를 반환합니다. 요청 로그는 `logs/`에 누적되므로 운영 시 로그 회전 정책을 설정하세요.

참고: [Nginx proxy_pass](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_pass), [Docker Compose extra_hosts](https://docs.docker.com/reference/compose-file/services/#extra_hosts).

## Let’s Encrypt / Certbot

연락처 이메일은 `fixabley@naver.com`입니다. 세 도메인의 DNS A/AAAA가 이 Nginx 서버를 가리키고 외부에서 TCP 80에 접근 가능해야 합니다. 공유기를 사용하는 경우 포트 포워딩도 필요합니다. `/.well-known/acme-challenge/`만 HTTP로 직접 제공하고 다른 요청은 HTTPS로 리다이렉트합니다.

DNS와 네트워크 설정 후 최초 발급:

```sh
sh scripts/issue-cert.sh
```

위 명령은 Let’s Encrypt 이용약관에 동의하여 세 도메인이 포함된 인증서를 신청합니다. 발급에 실패하면 기존 인증서를 유지합니다.

- `./certbot/conf`: ACME 계정, 발급 인증서, 갱신 설정
- `./certbot/www`: HTTP-01 인증 파일
- `./certbot/work`: Certbot 작업 파일
- `./logs/certbot`: Certbot 로그
- `./certs`: Nginx가 사용하는 인증서 사본과 갱신 알림 파일
- `./scripts`: 컨테이너에 읽기 전용으로 연결하는 실행 스크립트

`certbot-renew`가 12시간마다 갱신 필요 여부를 확인합니다. 발급·갱신 성공 시 deploy hook이 인증서를 `./certs`로 복사하고, Nginx가 30초 이내에 설정 검사 후 reload합니다. 최초 발급 전에는 갱신할 인증서가 없으므로 자체 서명 인증서를 계속 사용합니다. Docker가 실행 중이어야 자동 갱신이 작동합니다.

발급 완료 후 갱신 검증:

```sh
docker compose stop certbot-renew
docker compose run --rm certbot renew --dry-run
docker compose up -d certbot-renew
```

`--dry-run`은 테스트 CA를 사용하며 운영 인증서를 교체하지 않습니다. `./certbot/conf`에는 계정 키와 개인 키가 있으므로 백업 시 비공개로 보관하세요.

참고: [Certbot webroot 및 갱신 공식 문서](https://eff-certbot.readthedocs.io/en/stable/using.html#webroot).
