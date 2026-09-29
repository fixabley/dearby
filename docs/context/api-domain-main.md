# API 웹 게스트 저장 인계

검증일: 2026-09-29 KST. checkout `/Users/jominjun/Documents/dearby/api-domain-main`, branch `feat/guest-card-api`, 기준 `origin/main c4a8728` (PR61 병합 후). 담당 세션 유지. 서버 PR [#66](https://github.com/fixabley/dearby/pull/66), 기능 `df3363f`, 운영인계 `3bf3051`. 이전 API 배포 기록은 [보관본](archive/2026-09-29-before-web-guest/api-domain-main.md).

## 승인과 구현

- 웹 사용자 도메인은 dearby.wid.io.kr/Vercel, 서버 proxy origin은 https://wid.io.kr. 원본 개인 profile 대신 기존 공개 card snapshot만 제공. PDF 여부는 별도 결정이며 이 PR에 구현하지 않음.
- root 승인으로 shared/contracts/native-v1.md 끝에 **웹 guest 섹션만** 추가. PUT 최초저장 원자생성, 서버 자동만료 없음, cookie 400일 같은 token 갱신. API는 cookie를 직접 읽거나 Set-Cookie하지 않는다.
- 신규 `003_guest_cards.sql`: guest_sessions에는 digest만, guest_cards에는 digest/card ID 연결만. 회원 저장소와 격리; 세션 삭제 시 cascade. 명함 조회·저장 전에 UUID/DB존재/미철회 확인. 소문자 UUID 정규화로 기존 대소문자 동일 계약 준수.
- `X-Guest-Proxy-Key`는 서버 전용 `GUEST_PROXY_SECRET` 검증, `X-Guest-Token`은 SHA256 후 자기 세션 대조. Cookie·forwarded IP·path card ID를 방문자 인증으로 쓰지 않음. secret 미설정503, 잘못된 proxy403, 미존재/폐기token401이며 자동 새세션 전환 없음.
- 세션100장/전체10000세션. 철회된 명함은 목록 제외, 다음 저장에 참조 정리. 1분 고정창 전역1200요청/세션120요청/신규세션60회는 메모리 제한(재시작 초기화). DB용량은 재시작 유지. 무기한 세션이 전체상한에 이르면 **신규세션만** 차단; 오래된 세션 임의삭제 없음.
- GET/성공mutation/error no-store. 실패 시 원자 rollback, 내부정보 오류/로그 노출 없음. Next가 Origin 정확일치·JSON/custom-header CSRF 검증, 동일쿠키 갱신, Web Locks 첫저장 직렬화 및 client단위 abuse 방어를 담당.

## 검증 결과

`docker build -f apps/dearby-api/deploy/Dockerfile -t dearby-api-guest:review .`에서 lint/typecheck/build, 32개 테스트 통과. 별도 review 태그만 생성하며 운영 image를 덮어쓰거나 compose up/restart하지 않았다.

테스트 포함: 첫 무토큰 저장 전 card 검증/세션미생성, token digest만 DB저장, 두세션 목록격리, 타인회원profile/wallet 접근거부, 숨긴연락처 비노출, 동일명함/병렬저장 중복방지, 10년경과·동일토큰갱신·DB재개시 저장유지, 명시폐기401, 철회404/목록제외, 100장/10000세션 한도, 전역/세션/신규생성rate한도와 forwarded-header 우회거부, 저장실패 rollback·조회실패500, 기존2마이그레이션DB에서 회원세션/명함 보존하며 additive upgrade.

별도 nginx 후보를 58868에서 실제 HTTP 검증: 공개 card GET/HEAD200, public DELETE405, private profile/wallet/card collection/auth404, guest proxy없음403/토큰없음401, 첫 PUT201·duplicate200·list200·DELETE204·폐기후401. 성공 guest no-store, 응답/로그에 fixture proxy키/guest토큰/개인profile 노출 없음. 첫 nginx 시도는 정규식 `{36}` quoting 누락으로 시작실패했고 quoted regex로 수정 후 `nginx -t` 및 실HTTP 통과. 최초 테스트의 migration count 기대2를 새3에 맞춘 뒤 전체검사 통과.

키 검사: Git 후보/개인키 형식 및 review 이미지 config/layer에 기존 실제 runtime 키 미포함 확인. 새 root 운영 proxy secret 파일은 읽지 않음. 원격 PR diff도 기존 실제키와 비교해 미포함 확인.

ponytail-review: guest 단일 모듈·additive SQL·기존 crypto/SQLite/Fastify만 사용, 새 dependency/일반화 wrapper 없음. Lean already. Ship. 정확성·보안·저장회귀는 위 별도 테스트로 검증.

## 웹 통합용 임시 fixture — 운영과 별도

root 요청으로 독립 `dearby-api-guest-review`를 `http://127.0.0.1:58867`에 제공. `/data`는 tmpfs, 운영DB/volume/env 사용 없음. `dearby-api-guest-ingress-review` 58868은 nginx 경로검사용. test-only proxy와 공개가상명함 UUID2/3 및 fixture-owner 철회토큰을 웹 담당에게 직접 전달했다. 운영 secret 아님. 웹 담당 `d03b4ca` 및 root가 실제HTTP 저장·삭제·철회·브라우저격리 통합 PASS를 전달했다. API 담당은 이를 전달받은 웹 검증으로 구분하며 중복 실행하지 않았다. 완료 확인 후 두 임시 컨테이너를 `docker stop`으로 종료했고 `--rm`/tmpfs에 따라 fixture DB는 제거된다. 테스트용 가상 card를 production에 생성하지 않는다.

## 운영 상태·root 다음 행동

- 기존 `dearby-api-main-api-1` 58865는 running/healthy 관찰. API 담당은 이 작업에서 운영컨테이너·DB·Supabase·worker·shared nginx를 수정/재시작하지 않았다.
- root read-only 보고: production cards0/profiles0. fixture 검증 성공은 production 콘텐츠 존재를 의미하지 않음. 사용자 지시에 따라 임의게시/fixture생성 금지 유지.
- root 보고: 공개 도메인 same-LAN API timeout, LTE 흰화면 및 Vercel→운영catalog 약10초 후 `502 UPSTREAM_UNAVAILABLE`. [#65](https://github.com/fixabley/dearby/issues/65) 추적. 원인을 단정하지 않으며 AP 설정은 변경하지 않는다. 외부 도달성은 fixture 성공과 별개로 미해소; 웹 d03b4ca 배포/운영검증은 root 소유.
- root가 `GUEST_PROXY_SECRET` 운영값을 보호파일에 생성하고 Vercel production에 등록했다고 전달. API 담당은 그 파일을 **읽지 않았고 출력/복사/런타임적용하지 않았다**. 소스에는 env 이름과 빈 .env.example만 있음.
- 소스 PR 검토/통합 후 root의 별도 배정에만 runtime key 전달/배포를 진행. 기존 SQLite volume/OTP_SECRET 보존과 consistent backup 필요. `nginx-web-guest.location.conf`는 기존 catalog-only snippet의 **교체안**: 기존 `location ^~ /v1/`를 남기면 regex guest/public routes가 막힘. root가 적용하며 API 담당은 shared 파일 수정/reload 안 함.
- 회원인증/생성/SMTP/개인profile 경로를 공개하지 않는다. Fastify trustProxy=false 유지. 웹 실제브라우저 cookie/CSRF/탭간잠금·Vercel배포 검증은 웹담당 소유.
