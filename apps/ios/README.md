# Dearby iOS 클릭형 프로토타입

2026-10-04 최신 승인으로 selected 시안의 발견·내 활동·QR·받은 명함·내 프로필 5탭과
활동 상세/신청/일정 비교, 명함 만들기(실제 발행, 발행할 때만 패스키 로그인)/보기/선택/공유 메뉴를 SwiftUI로 제공합니다.
모든 사람·활동·연락처는 예시이며, 앱을 종료하면 변경 상태가 사라집니다.

- 발견·상세: `GET /v1/activities`(`entities/activity/api/ActivityClient.swift`, 계약 "활동 조회 — 2026-10-14")의 실제 활동. 모집 여부와 관계없이 서버 순서(id 오름차순)대로 모두 보인다. 실패하면 오류와 다시 시도이며 예시를 섞지 않는다.
  - 모집 상태는 `application`(모집 기간)과 지금 시각만으로 정한다: 시작 전 "모집 예정", 기간 안 "모집 중"(없는 쪽 끝은 열린 것으로 봄), 끝난 뒤 "모집 마감", 기간이 없거나 시각을 읽을 수 없으면 "모집 여부 확인 필요". 목록을 불러온 시각으로 판정한다.
  - 검색 칸은 입력을 약 300ms 기다린 뒤 `q`로 서버 검색하고 첫 페이지부터 다시 불러온다. 목록 끝 가까이 가면 `nextCursor`로 다음 페이지를 붙인다. 다음 페이지 실패는 목록 아래 "활동 더 불러오기"로 다시 시도한다.
  - 신청·공식 안내는 `officialUrl`(https만)을 외부 브라우저로 연다. 카드의 신청 바로가기는 "모집 중"일 때만 보인다. `imageUrl`(https)이 있으면 보이고, 없으면 달력 타일을 보인다.
  - API의 활동 id는 숫자이며 앱 안에서는 그 숫자의 문자열로 다룬다(공유 요청 `activityIds`도 문자열).
- 명함 fixture: `Sources/entities/identity/model/DemoIdentity.swift`의 가상 사람·연락처·이력.
- 상태: `ActivityViewModel`의 신청·참여 확정 표시(컨퍼런스 1건 신청으로 시작, 주최 측 확정 아님), `IdentityViewModel`의 예시 로그인·프로필·명함·교환·프리셋.
  View의 탭·필터·선택·검색도 모두 메모리만 사용합니다.
- 일정: `CalendarConflictState.swift`의 2026-10-24 14–15시 Asia/Seoul 고정 바쁜 시간.
  컨퍼런스 60분/1건, 캠프·밋업 0건. 결과는 30분 2열 격자로 표시합니다.
- 신청: 하단 CTA와 목록의 빠른 신청은 https 공식 신청 주소를 외부 브라우저로 연다. `신청 상태 수정`의 표시는 메모리만 쓰는 사용자 표시다.
- 테스트: 자동 테스트는 2026-10-06 사용자 요청으로 모두 지웠다. 사용자와 하나씩 다시 만든다. Debug 전용 launch 환경값 `DEARBY_API_ORIGIN` 덮어쓰기는 남겨 두었다. Release에는 덮어쓰기가 없다.
- QR: 고정 `https://example.com` PNG를 표시/확대합니다. 스캔/사진 버튼은 예시 명함을 엽니다.
  카메라·사진 접근/QR 디코딩은 없습니다. 공유 메뉴도 외부 전달·클립보드·이미지 저장 없이 안내만 표시합니다.
- 명함 보내기는 받은 명함의 메모리 그룹만 바꿉니다.

로그인 토큰(Keychain)과 첫 실행 소개를 봤는지 표시하는 UserDefaults `dearby.onboarding.shown`(참/거짓 하나) 외의 영구 저장과 푸시는 없습니다.
이전 앱의 기기 데이터는 읽기·초기화·삭제·마이그레이션하지 않습니다.
복구 태그: `backup/mobile-service-before-prototype-20261003`.
서버/웹/어드민/운영 DB는 변경하지 않습니다.
사진·QR은 root가 준비한 `shared/assets/prototype` 원본의 앱 번들 사본이며 런타임 다운로드가 없습니다.

## 시작 화면·첫 실행 소개 (2026-10-14)

- 실행할 때마다 OS 시작 화면(Info.plist `UILaunchScreen`)이 흰 배경(`LaunchBackground`)에 로고(`LaunchLogo`, `DearbyLogo` 원본을 줄인 사본)를 보여 준다. 인위적인 지연은 없다. 두 Info.plist에 같은 값이 있고, 생성 스크립트의 빈 시작 화면 자동 생성은 뺐다.
- 첫 실행에만 `pages/intro/ui/IntroPage.swift`가 로고와 "패스키로 시작하기"·"둘러보기"(애플 로그인을 켠 빌드는 그 위에 "Apple로 계속하기")를 보여 준다. "패스키로 시작하기"는 기존 `SignInSheet`(패스키 로그인 기본, "새 패스키로 시작" 가능)를 띄운다. 로그인 성공이나 "둘러보기"를 누르면 `dearby.onboarding.shown`을 저장하고 홈 탭으로 간다. 취소·실패면 소개에 남고 오류 문구를 보여 준다. 로그인은 필수가 아니다(App Store 5.1.1).
- 실행할 때 토큰이 이미 있거나, 공유 명함 링크(`/s/<UUID>`)로 열리면 소개를 건너뛰고 표시를 저장한다.
- 소개를 다시 보려면 앱을 지우고 다시 설치한다.

## 연결 설정

계약 `shared/contracts/native-v1.md`의 "연결 설정"을 따른다. 코드에는 운영 도메인을 두지 않는다.

- 빌드 환경값 `DEARBY_API_ORIGIN`, `DEARBY_WEB_ORIGIN`이 Info.plist `DearbyAPIOrigin`, `DearbyWebOrigin`으로 들어간다. 명령행 `xcodebuild`는 환경값을 빌드 설정으로 읽는다. Xcode 화면에서 빌드할 때는 scheme의 환경값이 아니라 빌드 설정으로 넘겨야 한다.
- Release: `scripts/validate_origins.sh` 빌드 단계가 두 값이 `https://<도메인>`(IP·포트·경로·대문자 없음)인지 검사하고, 아니면 빌드를 실패시킨다. Associated Domains는 `Dearby.entitlements`의 `applinks:$(DEARBY_WEB_HOST)`이고, `DEARBY_WEB_HOST`는 `$(DEARBY_WEB_ORIGIN:file)`(origin의 host)이다. 기기 Release 빌드는 provisioning profile에 Associated Domains 기능이 있어야 한다.
- Debug: 값이 없으면 `http://localhost:3000`(API 기본 포트)과 `http://localhost:3210`(웹 e2e 포트)을 쓴다. http는 localhost·127.0.0.1만 허용한다. Debug는 `Dearby-Debug.entitlements`(패스키용 `webcredentials:wid.io.kr`만)를 쓰고 `applinks`는 없다. 기기 Debug 빌드도 provisioning profile에 Associated Domains 기능이 필요하다.
- 들어온 `<웹 origin>/s/<UUID>` 링크는 `AppLinkProvider`가 공유 ID로 바꾼다. 공유 명함 화면은 아직 연결하지 않았고, 링크를 받으면 그 사실만 안내한다. 실제 유니버설 링크 동작은 AASA 배포(#90·#91) 뒤에만 확인할 수 있다.

## 패스키 로그인 (2026-10-10)

계약 `shared/contracts/native-v1.md` "패스키 로그인"을 따른다. 이메일 인증번호 로그인은 없앴다.

- 코드: `entities/account/api/Passkey.swift`(OS 패스키 요청, 서버 옵션 → `RegistrationResponseJSON`·`AuthenticationResponseJSON`, base64url), `AccountClient`(`/v1/auth/passkeys/*`·`/auth/refresh`·`/auth/logout`), `TokenVault`, `widgets/identity/model/AccountViewModel.swift`, `widgets/identity/ui/SignInSheet.swift`.
- RP ID `wid.io.kr`는 `Passkey.relyingParty`와 두 entitlements의 `webcredentials:wid.io.kr`에만 둔다. 바꾸면 등록된 패스키를 모두 쓸 수 없다.
- 토큰(TokenPair)은 새 Keychain 서비스 `dearby.account.tokens`에 둔다. 이메일 로그인 때의 `dearby.account.session`과 2026-10-03 이전 앱이 남긴 값은 읽거나 지우지 않는다.
- 401이면 refresh를 한 번(동시에 하나만) 하고 다시 호출한다. refresh가 거절(401)되면 토큰을 지우고 로그인 화면으로 돌아간다. refresh 중 네트워크 실패는 토큰을 남기고 그 호출만 실패시킨다. 로그아웃은 `/v1/auth/logout` 뒤 기기 토큰을 지운다(네트워크 실패여도).
- 서버 쪽 필요 조건(앱 밖): `https://wid.io.kr/.well-known/apple-app-site-association`의 `webcredentials.apps`에 `4V9FVN3VQF.io.wid.dearby`(Release)와 `4V9FVN3VQF.com.dearby.dearby`(Debug), API `WEBAUTHN_ALLOWED_ORIGINS`에 `https://wid.io.kr`. 둘 다 없으면 OS가 패스키 요청을 거절하므로 로컬 API만으로는 실제 패스키를 확인할 수 없다.

## 애플 로그인 (2026-10-16)

계약 `shared/contracts/native-v1.md` "애플 로그인"을 따른다. 패스키와 나란한 로그인 수단이다.

- 코드: `entities/account/api/AppleSignIn.swift`(OS 요청, scope 이름·이메일, 서버 nonce를 해시하지 않고 그대로 넣음), `AccountClient`(`/v1/auth/apple/nonce`·`/auth/apple`·`/auth/apple/register`·`/me/identities/apple`), `AccountViewModel`, `SignInSheet`·`IntroPage`, 버튼 `shared/ui/DearbyAppleButton.swift`.
- 버튼: Apple의 `ASAuthorizationAppleIDButton`("Apple로 계속하기", 밝은 화면 검정·어두운 화면 흰색). SwiftUI `SignInWithAppleButton`은 요청을 동기로 만들어 서버 nonce를 먼저 받을 수 없어서, 누르면 nonce를 받은 뒤 `AuthorizationController`로 요청한다(패스키와 같은 방식).
- 흐름: 이미 연결된 애플 계정(200)은 바로 로그인한다. 처음 보는 계정(202)은 로그인 시트에서 "새 계정으로 시작"(`register`)과 "기존 패스키 계정에 연결"(패스키 로그인 → `/me/identities/apple`)을 고른다. 연결이 실패하면 패스키 토큰은 저장하지 않고 로그아웃한다(연결 안 된 채 로그인되지 않게). 연결 단계의 401은 시간 초과로 보고 처음부터 다시 하게 한다. 400(패스키 계정에 이미 다른 애플 계정이 있거나 이 애플 계정이 방금 다른 계정에 연결됨)은 "이미 다른 계정과 연결돼 있어서 연결하지 못했어요."다. 서버는 두 경우 모두 linkToken을 써 버리므로 선택 화면을 닫는다. 사용자 취소는 문구가 없고, 503·연결 실패는 "지금은 애플 로그인을 쓸 수 없어요."다. 이름은 첫 인가 때만 와서 그때만 `displayName`으로 보낸다. credential의 `authorizationCode`(UTF-8 문자열, 없으면 생략)도 함께 보내 서버가 계정 삭제 때 애플 토큰을 폐기할 수 있게 한다. 자격 철회 확인(`getCredentialState`)은 아직 없다.
- **켜짐(2026-10-16)**: 두 entitlements에 `com.apple.developer.applesignin` = `["Default"]`, 두 Info.plist의 `DearbyAppleSignInEnabled` = `true`. App Store profile은 Sign in with Apple 포함으로 다시 만들어 `IOS_APPSTORE_PROFILE_BASE64`에 넣었다(2026-10-16 13:45 UTC). 기기 Debug 빌드는 `com.dearby.dearby` 앱 ID에도 이 기능이 켜져 있어야 서명된다.
- 끄려면 두 값을 되돌린다. entitlement만 남기고 플래그를 끄면 버튼만 숨는다.
- 실제 애플 로그인은 entitlement가 있는 서명 빌드와 운영 API(`APPLE_CLIENT_IDS` 설정)에서만 확인할 수 있다. entitlement 없이 요청하면 OS가 실패로 돌려준다.

## 계정 삭제 (2026-10-16)

계약 `shared/contracts/native-v1.md` "계정 삭제"(App Store 5.1.1(v))를 따른다. 애플 로그인 플래그와 관계없이 보인다.

- 위치: 내 프로필 탭 맨 아래 "계정 삭제"(로그인 상태, 프로필을 불러오지 못한 화면에도). 앱에는 아직 로그아웃 버튼이 없다.
- 확인 창("계정을 삭제할까요?", 삭제/취소) 뒤 `DELETE /v1/me`. 다른 로그인 호출처럼 401이면 refresh를 한 번 하고, refresh가 거절되면 로그아웃 상태로 돌아가 "로그인이 만료됐어요"를 보인다. 요청 중에는 버튼을 막아 한 번에 하나만 보낸다.
- 204: Keychain 토큰을 지우고(서버가 refresh token을 이미 지워 `/auth/logout`은 부르지 않음) 로그아웃 상태로 돌아가 "계정을 삭제했어요. 기기에 남은 패스키는 설정 > 암호에서 지울 수 있어요."를 한 번 보인다. 받은 명함·QR 탭의 메모리 데이터는 로그아웃 상태로 다시 불러올 때 비운다.
- 실패: "계정을 삭제하지 못했어요. 잠시 후 다시 시도해 주세요."를 보이고 로그인을 유지한다.

## 프로필·명함 v2 (2026-10-17)

계약 `native-v1.md` "프로필·명함 v2". 2026-10-17 사용자 결정으로 유비쿼터스 언어는 "활동"이다(코드에서 조직·프로그램·활동·역할을 아우르는 이름은 `ActivityInfo`).

- 내 프로필(`ProfilePage`)은 데이터 입력만 한다. 칸을 바로 고치고, 불러온 프로필과 달라지면 아래에 "저장"·"원래대로" 막대가 나타난다(`ProfileForm.changed`). 저장은 `If-Match: <version>`을 보내고 409면 다시 불러오라고 안내한다. "변경 이력"(`ProfileVersionsPage`)에서 저장한 버전을 보고 되돌린다(새 버전으로 저장).
- 이력은 단계별 입력(`HistoryFunnelPage`): 조직/활동 → 조직 → 프로그램 → (활동) → 역할 → 기간 → 설명 → 확인. 각 단계는 `ActivityInfoPickerView`로 등록된 활동 정보를 검색하거나(`GET /v1/organizations|programs|activities|roles`) 그대로 적는다(직접 입력 = 관리자 승인 대기, `approval`). 인증자료(`EvidenceSection`)는 저장된 이력에서 사진·PDF를 3개까지 올리고, `verified`가 아니면 "미확인" 배지가 붙는다.
- 명함 만들기(`CardComposerPage`)에서 꾸민다. `CardDraft.template`은 `{{profile.name}}`·`{{profile.job}}`·`{{profile.introduction}}`·`{{contact:<id>}}` 토큰이 있는 마크다운이고, 서버가 발행할 때 채운다(`POST /v1/cards {template}`). 본문에 넣은 연락처는 자동으로 공개되고, 승인되지 않은 이력은 고를 수 없다. 내용이 바뀌면 "원래대로"가 나타나며, QR 탭의 "이 명함 고치기"는 그 명함의 `template`으로 새 명함을 발행한다.
- 렌더링 화면 편집(`TemplateEditor`): iOS 26 이상은 `TextEditor`의 `AttributedString`으로 제목·굵게·목록을 서식 그대로 보이며 고치고, 데이터 토큰은 값이 채워진 칩(토큰 attribute)으로 보인다. 칩 안을 고치면 `TemplateRendering.markdown(from:)`이 감지해 토큰을 되살린다. iOS 18~25는 마크다운 원문 편집 + 미리보기(`DearbyMarkdownText`)로 대체된다. "데이터 넣기"는 본문 끝에 토큰을 붙인다(커서 위치 삽입은 아직 없음).
- 공유 활동 id는 숫자(`ShareActivity.id: Int`). 오류는 ProblemDetail의 `detail`(`AccountError.rejected`)을 그대로 보인다.
- 확인하지 못한 것: 실제 서버·기기에서의 흐름, iOS 26 서식 편집기의 실제 입력 동작(시뮬레이터 빌드만 통과).

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
