# Dearby iOS 클릭형 프로토타입

2026-10-04 최신 승인으로 selected 시안의 발견·내 활동·QR·받은 명함·내 프로필 5탭과
활동 상세/신청/일정 비교, 명함 만들기(실제 발행, 발행할 때만 이메일 로그인)/보기/선택/공유 메뉴를 SwiftUI로 제공합니다.
모든 사람·활동·연락처는 예시이며, 앱을 종료하면 변경 상태가 사라집니다.

- 발견·상세: `GET /v1/catalog`(`entities/catalog/api/CatalogClient.swift`)의 실제 활동. 모집 중 판정은 웹과 같다. 실패하면 오류와 다시 시도이며 예시를 섞지 않는다. `DemoActivities`는 예시로 남은 화면(받은 명함 묶음·캘린더 테스트)용이다.
  날짜가 지나도 모집 상태가 변하지 않습니다. 실제 모집 정보가 아닙니다.
- 명함 fixture: `Sources/entities/identity/model/DemoIdentity.swift`의 가상 사람·연락처·이력.
- 상태: `CatalogViewModel`의 신청·참여 확정 표시(컨퍼런스 1건 신청으로 시작, 주최 측 확정 아님), `IdentityViewModel`의 예시 로그인·프로필·명함·교환·프리셋.
  View의 탭·필터·선택·검색도 모두 메모리만 사용합니다.
- 일정: `CalendarConflictState.swift`의 2026-10-24 14–15시 Asia/Seoul 고정 바쁜 시간.
  컨퍼런스 60분/1건, 캠프·밋업 0건. 결과는 30분 2열 격자로 표시합니다.
- 신청: 하단 CTA와 목록의 빠른 신청은 https 공식 신청 주소를 외부 브라우저로 연다. `신청 상태 수정`의 표시는 메모리만 쓰는 사용자 표시다.
- 테스트: 자동 테스트는 2026-10-06 사용자 요청으로 모두 지웠다. 사용자와 하나씩 다시 만든다. Debug 전용 launch 환경값 `DEARBY_API_ORIGIN` 덮어쓰기는 남겨 두었다. Release에는 덮어쓰기가 없다.
- QR: 고정 `https://example.com` PNG를 표시/확대합니다. 스캔/사진 버튼은 예시 명함을 엽니다.
  카메라·사진 접근/QR 디코딩은 없습니다. 공유 메뉴도 외부 전달·클립보드·이미지 저장 없이 안내만 표시합니다.
- 예시 로그인은 서버 인증 없이 화면 상태를 전환합니다. 명함 보내기는 받은 명함의 메모리 그룹만 바꿉니다.

API·인증/Keychain·영구 저장·실제 캘린더·카메라·푸시는 없습니다.
이전 앱의 기기 데이터는 읽기·초기화·삭제·마이그레이션하지 않습니다.
복구 태그: `backup/mobile-service-before-prototype-20261003`.
서버/웹/어드민/운영 DB는 변경하지 않습니다.
사진·QR은 root가 준비한 `shared/assets/prototype` 원본의 앱 번들 사본이며 런타임 다운로드가 없습니다.

## 연결 설정

계약 `shared/contracts/native-v1.md`의 "연결 설정"을 따른다. 코드에는 운영 도메인을 두지 않는다.

- 빌드 환경값 `DEARBY_API_ORIGIN`, `DEARBY_WEB_ORIGIN`이 Info.plist `DearbyAPIOrigin`, `DearbyWebOrigin`으로 들어간다. 명령행 `xcodebuild`는 환경값을 빌드 설정으로 읽는다. Xcode 화면에서 빌드할 때는 scheme의 환경값이 아니라 빌드 설정으로 넘겨야 한다.
- Release: `scripts/validate_origins.sh` 빌드 단계가 두 값이 `https://<도메인>`(IP·포트·경로·대문자 없음)인지 검사하고, 아니면 빌드를 실패시킨다. Associated Domains는 `Dearby.entitlements`의 `applinks:$(DEARBY_WEB_HOST)`이고, `DEARBY_WEB_HOST`는 `$(DEARBY_WEB_ORIGIN:file)`(origin의 host)이다. 기기 Release 빌드는 provisioning profile에 Associated Domains 기능이 있어야 한다.
- Debug: 값이 없으면 `http://localhost:3000`(API 기본 포트)과 `http://localhost:3210`(웹 e2e 포트)을 쓴다. http는 localhost·127.0.0.1만 허용한다. Debug에는 entitlement를 붙이지 않는다.
- 들어온 `<웹 origin>/s/<UUID>` 링크는 `AppLinkProvider`가 공유 ID로 바꾼다. 공유 명함 화면은 아직 연결하지 않았고, 링크를 받으면 그 사실만 안내한다. 실제 유니버설 링크 동작은 AASA 배포(#90·#91) 뒤에만 확인할 수 있다.

## 로그인·명함 발행 연결 (2026-10-06)

`entities/account`의 `AccountClient`(인증번호 로그인·`/v1/profile`·`POST /v1/cards`)와 Keychain `SessionVault`, `features/account`의 `AccountViewModel`이 있다. 화면 연결은 다음 PR이다. 세션은 새 Keychain 서비스 `dearby.account.session`에 두며, 2026-10-03 이전 앱이 남긴 세션은 읽거나 지우지 않는다. 소유자 호출이 401이면 다시 시도하지 않고 로그아웃한다. 운영 `/v1/auth`는 ingress에서 닫혀 있고 인증 메일(#89)도 미설정이므로 운영 동작 완료가 아니다.

로컬 실제 API 확인(선택, 운영 연결 금지): 격리 컨테이너 두 개를 띄운다. 이름은 다른 세션과 겹치지 않게 한다.
1. `public.ecr.aws/supabase/postgres:17.6.1.171`을 tmpfs로 띄우고 `supabase/migrations`를 버전 순서로 적용한 뒤 cron을 끄고 `dearby_api_runtime` 비밀번호를 정한다.
2. 자체 CA로 `localhost` 인증서를 만들어 `public.ecr.aws/supabase/mailpit`을 `--smtp-tls-cert --smtp-tls-key --smtp-require-starttls --smtp-auth-accept-any`로 띄운다.
3. API를 `PORT=4310`, `DATABASE_URL`(runtime 역할), 32자 이상 임시 `OTP_SECRET`, `SMTP_HOST=localhost`·포트·임의 사용자, `NODE_EXTRA_CA_CERTS=<CA>`로 실행한다.
4. Debug 빌드를 `DEARBY_API_ORIGIN=http://127.0.0.1:4310`으로 실행해 화면에서 확인한다. 발송 한도(같은 이메일 1분 1회, IP 1시간 20회)가 DB에 남으므로 새 DB에서 돌린다.

## 검증

`Dearby.xcodeproj`는 저장소에 두지 않는 생성물이다(병합 충돌 방지). Xcode로 열거나 빌드하기 전에 생성한다. `xcodeproj` gem이 필요하다(`gem install --user-install xcodeproj`). 자동 테스트는 2026-10-06 사용자 요청으로 모두 지웠다. 사용자와 하나씩 다시 만든다.

```sh
ruby apps/ios/scripts/generate_project.rb
bash apps/ios/scripts/setup_swiftlint.sh
bash apps/ios/scripts/run_swiftlint.sh
```

Debug ID `com.dearby.dearby`, Release ID `io.wid.dearby`, 버전 `0.1.0 (1)` 유지.
실기기 설치·서명·배포는 조율 세션 소유입니다.
검증 결과와 인계: `../../docs/context/ios-ui-prototype.md`.
현재 화면 증거는 [selected UI](docs/evidence/ui-prototype-selected/README.md)에 있습니다.
[중간 증거 정리 내역](docs/cleanup-2026-10-04.md)을 참고하세요.
`docs/context-archive`의 과거 텍스트 기록은 그대로 보존합니다.
