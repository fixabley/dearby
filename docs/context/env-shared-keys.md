# 앱 간 공유 환경값

2026-10-10 결정: env 파일은 앱 폴더마다 따로 둔다(`apps/*/.env*`, 예시만 커밋). 상위 폴더에서 한꺼번에 관리하지 않는다. 배포 대상(Vercel·서버 compose·앱 빌드)마다 값을 넣는 곳이 다르고, 각 대상에는 필요한 키만 들어가야 하기 때문이다. 대신 여러 앱에서 **같아야 하는 값**을 아래에 모아 둔다. 한쪽을 바꾸면 같은 행의 다른 쪽도 함께 바꾼다.

나중에 사람이나 서버가 늘면 비밀 관리 도구(1Password CLI, Doppler, SOPS 등)를 쓴다. 값을 한 곳에서 관리하고 대상마다 필요한 키만 내보내는 방식이다.

## 앱별 env 파일

| 앱 | 파일 | 운영 값을 넣는 곳 |
| --- | --- | --- |
| API `apps/dearby-api` | `.env.local.example`, `.env.prod.example` | 서버의 `.env.prod` (`compose.prod.yaml`) |
| 사용자 웹 `apps/web` | `.env.example` | Vercel 환경변수 |
| 어드민 `apps/admin` | `.env.example` (`VITE_*`는 브라우저에 공개되는 값) | Vercel 환경변수 |
| iOS | 빌드 설정 `DEARBY_API_ORIGIN` | GitHub Actions Variables (`ios-testflight.yml`) |
| Android | 환경값 또는 Gradle 속성 `DEARBY_API_ORIGIN` | 빌드 환경 |

2026-10-18 명함 공유 삭제로 앱의 `DEARBY_WEB_ORIGIN`(공유 링크·유니버설 링크용)과 웹의 앱 연결 파일 환경값(`DEARBY_APPLE_TEAM_ID`, `DEARBY_IOS_BUNDLE_IDS`, `DEARBY_ANDROID_PACKAGE`, `DEARBY_ANDROID_CERT_SHA256`)은 더 쓰지 않는다. Vercel·GitHub Actions에 남아 있는 값은 사용자가 지운다.

## 같아야 하는 값

| 값 | 어디에 들어가는가 | 맞추는 규칙 |
| --- | --- | --- |
| API 주소 | web `DEARBY_API_ORIGIN`, iOS·Android `DEARBY_API_ORIGIN` | API가 실제로 받는 공개 origin. `/v1`은 붙이지 않는다 |
| 웹 주소 | API `WEBAUTHN_ALLOWED_ORIGINS` | 패스키를 웹에서 쓰면 웹 origin이 API 허용 목록에 있어야 한다. API `JWT_ISSUER`는 API 안에서만 쓰는 식별자라 다른 앱과 맞출 필요가 없다 |
| 패스키 RP ID | API `WEBAUTHN_RP_ID`, iOS entitlements `webcredentials:`, `wid.io.kr`의 AASA·assetlinks | 웹·앱 도메인과 같거나 그 상위 도메인이어야 한다. **바꾸면 등록된 패스키를 모두 쓸 수 없다** |
| Apple Team ID | CI secret `TEAM_ID`, `wid.io.kr` AASA `webcredentials.apps` | 같은 Apple 개발자 팀 |
| Android 패키지·서명 | `wid.io.kr` assetlinks, API `WEBAUTHN_ALLOWED_ORIGINS`의 `android:apk-key-hash:` | 앱 설정 `applicationId = io.wid.dearby`(2026-10-18 변경, 코드 패키지는 `com.dearby.nativeapp` 유지)과 배포 서명 인증서. assetlinks의 `package_name`도 `io.wid.dearby` |
| 게스트 프록시 키 | API `GUEST_PROXY_SECRET`(서버 `.env.prod`), web `GUEST_PROXY_SECRET`(Vercel `dearby-web`, Secret 타입) | 32자 이상 같은 값. 원본은 서버의 `~/.dearby-deploy/guest-proxy-secret`(저장소 밖). 2026-10-17 Kotlin API `/v1/guest/*` 배포 때 두 곳을 이 값으로 맞췄다. Vercel 값은 다시 읽을 수 없으므로 바꿀 때는 두 곳을 함께 바꾼다 |

## 로컬 개발 포트

| 대상 | 포트 | 근거 |
| --- | --- | --- |
| API (`./gradlew bootRun`) | 8080 | Spring 기본값 |
| API 로컬 DB (`compose.yaml`) | 5432 | `127.0.0.1`에서만 받음 |
| API 테스트 DB (`compose.yaml` postgres-test) | 5433 | `127.0.0.1`에서만 받음. `./gradlew test`·`:core:jooqCodegen` 전용, 메모리 저장 |
| API 운영 (`compose.prod.yaml`) | 58866 | `127.0.0.1`에서만 받음. 기존 TS API는 58865 |
| API 운영 DB (`compose.prod.yaml`) | 58867 | `127.0.0.1`에서만 받음. IntelliJ 등 직접 조회용 |
| 관리자 API 운영 (`compose.prod.yaml` admin) | 58868 | `127.0.0.1`에서만 받음. nginx가 `api.dearby.wid.io.kr/v1/admin/`을 보냄 |
| 관리자 API 로컬 (`./gradlew :admin:bootRun`) | 8081 | api(8080)와 겹치지 않게 |

## 맞지 않는 점 (2026-10-10 확인, 수정 안 함)

- **앱 Debug 기본 API 주소:** iOS는 `http://localhost:3000`(`AppLinks.swift`), Android는 `http://10.0.2.2:3000`(`app/build.gradle.kts`)이다. 이는 삭제한 TS API의 포트다. Kotlin API는 8080이므로, 지금은 `DEARBY_API_ORIGIN`을 지정해야 로컬 API에 붙는다.
- **로컬 웹 포트:** API `.env.local.example`의 `WEBAUTHN_ALLOWED_ORIGINS`는 `next dev` 기본값인 3000을 웹으로 가정했다. 웹의 실제 로컬 포트를 정한 뒤 맞춘다(앱의 Debug 기본 웹 주소 3210은 2026-10-18 공유 삭제로 없어졌다).
- **앱 패스키 도메인 (2026-10-10 해소):** iOS는 `webcredentials:wid.io.kr`(#178), Android는 Credential Manager(#179)로 연결했다. 운영 nginx(`~/nginx`, 이 저장소 밖)가 `https://wid.io.kr/.well-known/apple-app-site-association`(`4V9FVN3VQF.io.wid.dearby`, `4V9FVN3VQF.io.wid.dearby`)과 `assetlinks.json`(`get_login_creds`)을 정적 파일로 내준다. Apple CDN과 Google Digital Asset Links API가 읽어 가는 것을 확인했다. **assetlinks에는 이 Mac의 디버그 서명(`91:E3:35:…:69:46`)만 있다.** 배포 서명(Play Console 앱 서명 키) 값과 API `WEBAUTHN_ALLOWED_ORIGINS`의 `android:apk-key-hash:` 배포 값은 아직 없다. 디버그 origin은 `android:apk-key-hash:keM1kj8hdWGj0XrQ9-he6ryGQQHqSJZw38rfG5sgaUY`.
- **web `GUEST_PROXY_SECRET` (2026-10-17 해소):** Kotlin API가 `/v1/guest/*`를 다시 제공하며 위 표의 값으로 맞췄다.
