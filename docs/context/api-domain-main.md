# Main API 도메인 배포 인계

검증일: 2026-09-29 KST. 담당 checkout `/Users/jominjun/Documents/dearby/api-domain-main`, branch `fixabley/api-domain-main`. 시작 기준 `b3094f9` (origin/main 일치 확인). Orca handle `term_ed77215a-daf0-49d7-92da-127ad6e91e3e`는 이번 실행 시점 식별자이며 재개 시 목록을 다시 조회한다. 세션은 유지한다.

서버 PR: [#61](https://github.com/fixabley/dearby/pull/61). 구현 커밋 `6d336a9`, 배포/운영 커밋 `a46e8c6`. 원격 PR diff를 실제 runtime key와 비교해 노출 없음 확인. 최초 push는 macOS keychain의 다른 계정으로 403이었으며, 전역 설정을 바꾸지 않고 해당 명령에서만 활성 `gh` 자격증명 helper로 재실행해 성공했다.

## 승인·범위

main API 독립 배포와 nginx 변경안. 최신 승인으로 `8984fbf`의 Supabase 탐색 reader만 선별 재사용: app 주입, server 선택, catalog optional imageUrl, reader와 테스트. admin/collector/presentation/mobile 코드는 통합하지 않았다. auth/cards/wallet은 SQLite 유지. public HTTPS는 root 결정에 따라 `https://wid.io.kr/v1/catalog`; DNS/공유기/인증서/shared nginx 변경·reload는 root 소유다.

기존 API 58765, admin 5173/5174, Supabase 54321~54327, 기존 DB/worker, root 58365는 건드리지 않았다. 새 Compose project `dearby-api-main`, container `dearby-api-main-api-1`, port `127.0.0.1:58865`, volume `dearby-api-main_data`만 생성했다. 검증용 nginx 컨테이너 58866은 종료했다.

## 구현·현재 런타임

- `apps/dearby-api/deploy/compose.yaml`: unless-stopped, UID 1000, readonly root, cap drop, 리소스/로그 제한, 독립 영속 volume, catalog healthcheck.
- Dockerfile: Node 24.21.0 기반 digest 고정; 빌드에서 lint/typecheck/테스트/build; 최종 이미지에는 production dependencies만. 빌드 context allowlist로 env/키/다른 앱 제외.
- ignored `.env.runtime`은 0600. 기존 root ignored env에서 anon 키만 복사하고 독립 OTP_SECRET 생성. URL은 `http://host.docker.internal:54321`. service-role/SMTP/기존 DB 복사 없음.
- Supabase RPC 실패/잘못된 응답은 sanitized 503. SQLite fallback 없음. 공개 DTO 밖 필드 거부. 요청·토큰 로그 비활성.
- nginx location안은 기존 apex HTTPS server 내부에만 삽입: GET/HEAD exact catalog 허용, 다른 /v1 404, 인증/쿠키 제거, forwarding headers 덮어쓰기. root만 적용한다. 위치별 요청/오류 로그를 끄므로 요청별 진단 정보가 줄어든다.
- Fastify trustProxy=false 유지: 위조 forwarding header를 신뢰하지 않는다. 인증을 공개하면 IP 한도가 프록시에 합쳐지므로 향후 별도 신뢰 설정/검증 필요. 현재 nginx안은 인증 자체를 외부에 노출하지 않는다.

## 실행한 검증

재현 기본 명령은 [배포 runbook](../../apps/dearby-api/deploy/README.md)에 있다.

1. `docker compose -f apps/dearby-api/deploy/compose.yaml build`: lint 0 warnings/errors, typecheck, 25 tests 통과, build 성공. HTTP OTP/session/profile/card/wallet 권한, 저장 실패/rollback, 재개 영속성, Supabase sanitized 실패, 프록시 위조 한도 테스트 포함. 최초 빌드는 SQLite native compiler 부재로 실패해 build stage에만 도구 추가. 이후 테스트의 잘못된 logger-level assertion 제거 후 성공; 최종 재빌드도 25/25.
2. `curl http://127.0.0.1:58865/v1/catalog`: 200. `docker exec nginx-nginx-1 sh -c 'wget -q -O /dev/null http://host.docker.internal:58865/v1/catalog && echo OK'`: 성공. 실제 컨테이너에서 Supabase RPC 접근 검증됨.
3. Supabase anon key로 raw `catalog_activities?select=id` GET: 401. 서비스는 공개 RPC만 읽는다. DB에 대한 mutation 요청은 실행하지 않았다.
4. 별도 nginx 58866에 동일 snippet을 적용해 GET/HEAD catalog 200, POST 405, profile/auth/catalog trailing slash 및 /v1 404, 기존 apex / 응답 200. 기존 nginx에는 적용하지 않았다.
5. 실제 SMTP 미배정 인증 요청: 503 MAIL_UNAVAILABLE. 독립 API restart 후 같은 이메일 재요청 429로 실패 발송 quota 영속 보존. 정상 메일 발송으로 표시하지 않음.
6. 런타임 UID 1000, /data 700, DB 600, loopback publish, healthy 확인. 최종 컨테이너 재생성에도 같은 volume 유지.
7. 독립 `docker run --rm --read-only --tmpfs /tmp:mode=1777 dearby-api-main:local`에서 공식 snapshot import 후 createApp HTTP inject: 200, 활동 30개, 모두 stale/non-recruiting. 실제 Supabase 또는 runtime catalog에는 seed/게시하지 않음.
8. runtime secret 실제값과 commit 후보 파일 전체 비교, 이미지 archive 전체 layer 내용 비교, runtime 로그 비교에서 불일치(노출 없음). Authorization/Cookie sentinel도 nginx/API 로그에 없음. env는 gitignore 및 0600 확인. 앱 source에 실제 runtime 키 없음; 이 checkout은 앱 번들을 만들지 않으며 iOS 번들 최종 검사는 iOS 담당 소유.
9. ponytail-review: 변경 diff와 reader/배포 호출 흐름 검토. 불필요한 wrapper나 dependency 추가 없음. Lean already. Ship. 정확성/권한/실패 검증은 위 별도 검사로 수행.

## 공개 데이터 차단과 남은 작업

실제 Supabase 공개 RPC 결과 조직/프로그램/활동 0. read-only SQL 확인: draft 35 / hidden 4 / published 0, 조직 총 34. 이는 upstream 연결 성공과 별개로 게시 데이터가 없기 때문이다. [#59](https://github.com/fixabley/dearby/issues/59)에 원인·영향·해소조건 기록. 데이터는 임의 게시하지 않았다. 최신 사용자 지시는 데이터 게시 없이 API 연결만 수행하는 것이므로 draft/hidden/published를 유지한다. #59는 향후 콘텐츠 공개의 추적 항목이며 이번 연결 작업의 완료 조건은 아니다.

SMTP 실제 수신은 [#42](https://github.com/fixabley/dearby/issues/42) 미해소. root가 nginx 적용과 로컬 TLS 검증을 완료했다고 전달했다(아래 구분). 공개 신뢰 인증서·외부망 검증은 미완료다. 호스트 sleep/재부팅/Docker 기동/기존 Supabase 가용성에 의존한다. 백업·복원 실습, 모니터링, 장기 uptime 검증은 미실행. named volume을 삭제하지 말고 env와 함께 안전 보관해야 한다.

## root 적용 결과 — root 전달 증거

2026-09-29 root는 `/Users/jominjun/nginx/backups/dearby-20260929-203907/default.conf`에 기존 파일을 백업한 뒤 apex 443 server에 제안 snippet을 삽입하고 `nginx -t`/reload를 수행했다고 전달했다. 자체 인증서를 명시 신뢰한 로컬 TLS에서 GET/HEAD catalog 200 JSON, POST 405, 다른 `/v1/auth` 404, apex `/` 200 확인. 이는 root가 실행한 결과이며 API 담당이 공개 신뢰/외부 도달성을 검증했다는 뜻이 아니다. 공개 신뢰 인증서와 외부 443은 미완료.

## root 다음 행동

1. [서버 PR #61](https://github.com/fixabley/dearby/pull/61) 검토/병합. 발표/수집/admin/앱 코드는 없다. 실제 env/키는 Git에 없다.
2. 기존 로컬 TLS 검증과 별도로 공개 신뢰 인증서 hostname/체인 및 외부 443 도달성을 검증한다. 자체 인증서 예외 설정을 앱에 넣지 않는다.
3. iOS origin `https://wid.io.kr` + `/v1/catalog`, 앱 직접 Supabase 키/URL 제거 및 Release bundle 키 미포함 검사는 iOS 담당과 조율한다.
4. 데이터 게시 없이 현재 상태 유지. #59는 향후 콘텐츠 공개 추적이며 이번 사용자 요청의 차단 조건으로 취급하지 않는다. #42 SMTP는 별도 미완료다.
