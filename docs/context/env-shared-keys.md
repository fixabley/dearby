# 앱 간 공유 환경값

2026-10-10 결정: env 파일은 앱 폴더마다 따로 둔다(`apps/*/.env*`, 예시만 커밋). 상위 폴더에서 한꺼번에 관리하지 않는다. 배포 대상(Vercel·서버 compose·앱 빌드)마다 값을 넣는 곳이 다르고, 각 대상에는 필요한 키만 들어가야 하기 때문이다. 대신 여러 앱에서 **같아야 하는 값**을 아래에 모아 둔다. 한쪽을 바꾸면 같은 행의 다른 쪽도 함께 바꾼다.

나중에 사람이나 서버가 늘면 비밀 관리 도구(1Password CLI, Doppler, SOPS 등)를 쓴다. 값을 한 곳에서 관리하고 대상마다 필요한 키만 내보내는 방식이다.

## 앱별 env 파일

| 앱 | 파일 | 운영 값을 넣는 곳 |
| --- | --- | --- |
| API `apps/dearby-api` | `.env.local.example`, `.env.prod.example` | 서버의 `.env.prod` (`compose.prod.yaml`) |
| 사용자 웹 `apps/web` | `.env.example` | Vercel 환경변수 |
| 어드민 `apps/admin` | `.env.example` (`VITE_*`는 브라우저에 공개되는 값) | Vercel 환경변수 |
| 수집 워커 `apps/catalog-worker` | 파일 없음, 실행 환경변수 | 실행 환경 |
| iOS | 빌드 설정 `DEARBY_API_ORIGIN`, `DEARBY_WEB_ORIGIN` | GitHub Actions Variables (`ios-testflight.yml`) |
| Android | 환경값 또는 Gradle 속성 `DEARBY_API_ORIGIN`, `DEARBY_WEB_ORIGIN` | 빌드 환경 |

## 같아야 하는 값

| 값 | 어디에 들어가는가 | 맞추는 규칙 |
| --- | --- | --- |
| API 주소 | web `DEARBY_API_ORIGIN`, iOS·Android `DEARBY_API_ORIGIN` | API가 실제로 받는 공개 origin. `/v1`은 붙이지 않는다 |
| 웹 주소 | iOS·Android `DEARBY_WEB_ORIGIN`, API `WEBAUTHN_ALLOWED_ORIGINS` | 패스키를 웹에서 쓰면 웹 origin이 API 허용 목록에 있어야 한다. API `JWT_ISSUER`는 API 안에서만 쓰는 식별자라 다른 앱과 맞출 필요가 없다 |
| 패스키 RP ID | API `WEBAUTHN_RP_ID`, iOS entitlements `webcredentials:`, `wid.io.kr`의 AASA·assetlinks | 웹·앱 도메인과 같거나 그 상위 도메인이어야 한다. **바꾸면 등록된 패스키를 모두 쓸 수 없다** |
| Apple Team ID | web `DEARBY_APPLE_TEAM_ID`, CI secret `TEAM_ID`, `wid.io.kr` AASA `webcredentials.apps` | 같은 Apple 개발자 팀 |
| iOS bundle ID | web `DEARBY_IOS_BUNDLE_IDS` | 앱 설정: Release `io.wid.dearby`, Debug `com.dearby.dearby` (`apps/ios/scripts/generate_project.rb`) |
| Android 패키지·서명 | web `DEARBY_ANDROID_PACKAGE`, `DEARBY_ANDROID_CERT_SHA256`, `wid.io.kr` assetlinks, API `WEBAUTHN_ALLOWED_ORIGINS`의 `android:apk-key-hash:` | 앱 설정 `applicationId = com.dearby.nativeapp`과 배포 서명 인증서 |
| Supabase 프로젝트 | admin `VITE_SUPABASE_URL`, 워커 `SUPABASE_URL` | 같은 프로젝트. 워커의 `SUPABASE_SERVICE_ROLE_KEY`는 워커에만 둔다 |

## 로컬 개발 포트

| 대상 | 포트 | 근거 |
| --- | --- | --- |
| API (`./gradlew bootRun`) | 8080 | Spring 기본값 |
| API 로컬 DB (`compose.yaml`) | 5432 | `127.0.0.1`에서만 받음 |
| API 운영 (`compose.prod.yaml`) | 58866 | `127.0.0.1`에서만 받음. 기존 TS API는 58865 |

## 맞지 않는 점 (2026-10-10 확인, 수정 안 함)

- **앱 Debug 기본 API 주소:** iOS는 `http://localhost:3000`(`AppLinks.swift`), Android는 `http://10.0.2.2:3000`(`app/build.gradle.kts`)이다. 이는 삭제한 TS API의 포트다. Kotlin API는 8080이므로, 지금은 `DEARBY_API_ORIGIN`을 지정해야 로컬 API에 붙는다.
- **로컬 웹 포트:** 앱 Debug 기본 웹 주소는 3210이다. 반면 API `.env.local.example`의 `WEBAUTHN_ALLOWED_ORIGINS`는 `next dev` 기본값인 3000을 웹으로 가정했다. 웹의 실제 로컬 포트를 정한 뒤 한쪽을 맞춘다.
- **iOS 패스키 도메인:** `Dearby.entitlements`에는 `applinks:`만 있다. 앱에서 패스키를 쓰려면 `webcredentials:<RP ID>`와 그 도메인의 `apple-app-site-association` `webcredentials` 항목이 필요하다. Android도 `assetlinks.json`에 `get_login_creds` 관계가 필요하다. 2026-10-10 RP ID `wid.io.kr` 유지·패스키 전용 로그인으로 결정했고, 연결 방식은 [모바일 계약](../../shared/contracts/native-v1.md) "패스키 로그인"을 따른다.
- **web `GUEST_PROXY_SECRET`:** 삭제한 TS API와 함께 쓰던 비밀이다. Kotlin API에는 아직 대응하는 기능이 없다.
