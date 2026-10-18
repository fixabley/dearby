# Dearby iOS 클릭형 프로토타입

발견·내 활동·내 명함·받은 명함·내 프로필 5탭과 활동 상세/신청/일정 비교, 명함 만들기(실제 발행, 발행할 때만 패스키 로그인)/보기/선택을 SwiftUI로 제공합니다.
2026-10-18 사용자 결정으로 명함 공유(공유 기록·QR 표시·OS 공유·스캔·유니버설 링크·받은 공유 저장·활동별 묶음)는 모두 지웠습니다.
모든 사람·활동·연락처는 예시이며, 앱을 종료하면 변경 상태가 사라집니다.

- 발견·상세: 발견 탭은 `GET /v1/discovery/programs`(계약 "발견 프로그램 묶음 — 2026-10-18")로 열린 활동이 있는 프로그램을 가장 가까운 마감 순으로 보이는 아코디언이다(`ProgramHeader`: 이름·조직·열린 활동 수·마감). 머리글을 펼치면 `GET /v1/activities?programId=`로 받은 활동 중 모집 중인 것만 `ActivityCard`로 보인다. 열린 활동이 1개인 프로그램과 검색어가 활동 이름에 맞은 프로그램은 펼친 채로 시작하고, 검색은 프로그램·조직·활동 이름에 부분 일치한다. 실패하면 오류와 다시 시도이며 예시를 섞지 않는다. 활동 상세는 `eligibility`(계약 "대상자 조건", 2026-10-18)가 있으면 "대상" 절에 조건을 한글 칩으로 보인다(`EligibilityText`: `나이 19~34세`·`재학상태 재학`, `any`는 `또는`, `기타`는 note). 없으면 절을 숨긴다.
  - 모집 상태는 `application`(모집 기간)과 지금 시각만으로 정한다: 시작 전 "모집 예정", 기간 안 "모집 중"(없는 쪽 끝은 열린 것으로 봄), 끝난 뒤 "모집 마감", 기간이 없거나 시각을 읽을 수 없으면 "모집 여부 확인 필요". 목록을 불러온 시각으로 판정한다.
  - 검색 칸은 입력을 약 300ms 기다린 뒤 `q`로 서버 검색하고 첫 페이지부터 다시 불러온다. 목록 끝 가까이 가면 `nextCursor`로 다음 페이지를 붙인다. 다음 페이지 실패는 목록 아래 "활동 더 불러오기"로 다시 시도한다.
  - 신청·공식 안내는 `officialUrl`(https만)을 외부 브라우저로 연다. 카드의 신청 바로가기는 "모집 중"일 때만 보인다. `imageUrl`(https)이 있으면 보이고, 없으면 달력 타일을 보인다.
  - API의 활동 id는 숫자이며 앱 안에서는 그 숫자의 문자열로 다룬다.
- 명함 fixture: `Sources/entities/identity/model/DemoIdentity.swift`의 가상 사람·연락처·이력.
- 상태: `ActivityViewModel`의 신청·참여 확정 표시(컨퍼런스 1건 신청으로 시작, 주최 측 확정 아님), `IdentityViewModel`의 예시 로그인·프로필·명함·교환·프리셋.
  View의 탭·필터·선택·검색도 모두 메모리만 사용합니다.
- 일정: `CalendarConflictState.swift`의 2026-10-24 14–15시 Asia/Seoul 고정 바쁜 시간.
  컨퍼런스 60분/1건, 캠프·밋업 0건. 결과는 30분 2열 격자로 표시합니다.
- 신청: 하단 CTA와 목록의 빠른 신청은 https 공식 신청 주소를 외부 브라우저로 연다. `신청 상태 수정`의 표시는 메모리만 쓰는 사용자 표시다.
- 테스트: 자동 테스트는 2026-10-06 사용자 요청으로 모두 지웠다. 사용자와 하나씩 다시 만든다. Debug 전용 launch 환경값 `DEARBY_API_ORIGIN` 덮어쓰기는 남겨 두었다. Release에는 덮어쓰기가 없다.
- 내 명함(`MyCardsPage`): `GET /v1/cards`의 발행 명함 목록을 2열 격자(첫 칸 `+ 새 명함`, 각 칸은 명함 이름·프로필 이름·직무, 선택 칸은 민트 바탕·청록 테두리, 접근성 글자 크기에서는 1열)로 보이고, 격자 아래에 고른 명함의 카드와 "이 명함 고치기"를 둔다. QR·스캔·공유는 없다.
- 명함 카드(`CardView`, 계약 "명함 표현" 2026-10-18, Mobbin 참고): 내 명함·받은 명함 상세가 같은 카드를 쓴다. 흰 바탕·모서리 20·구분선 테두리·안쪽 여백 20인 한 장의 카드 안에 가운데 머리(이니셜 아바타 56 → 이름 → `job`을 ` · `로 나눈 직무 칩(줄바꿈) → 가장 최근 승인 이력의 조직), `introduction` 마크다운, `연락처` 절(종류 아이콘·라벨 또는 종류 이름·값, 열 수 있으면 외부 링크 표시와 행 전체 버튼), `활동 이력` 절(기간·조직 · 프로그램 · 활동·역할과 `확인됨`/`미확인` 배지·설명)이 쌓인다. 비면 `공개된 연락처가 없어요`/`공개된 활동 이력이 없어요`. 행동 버튼은 카드 바깥에 둔다.
- 받은 명함(`WalletPage`): `GET /v1/wallet`의 저장 명함을 이름 첫 글자(한글 초성 ㄱ…ㅎ, 영문 A–Z, 그 밖 `#`, `NameIndex`)로 묶은 색인 목록으로 보인다(2026-10-18, Outlook 연락처 참고). 묶음 머리글은 고정되고, 묶음이 7개 이상이면 오른쪽 색인 레일을 누르거나 끌어 이동한다(동작 줄이기에서는 애니메이션 없음). 행은 이니셜·이름·직무와 가장 최근 승인 이력의 조직 칩이며 화살표는 없다. 검색 중에도 묶음을 유지한다. 앱에서 새로 저장하는 경로는 없다(공유 제거).

로그인 토큰(Keychain)과 첫 실행 소개를 봤는지 표시하는 UserDefaults `dearby.onboarding.shown`(참/거짓 하나) 외의 영구 저장과 푸시는 없습니다.
이전 앱의 기기 데이터는 읽기·초기화·삭제·마이그레이션하지 않습니다.
복구 태그: `backup/mobile-service-before-prototype-20261003`.
서버/웹/어드민/운영 DB는 변경하지 않습니다.
사진은 root가 준비한 `shared/assets/prototype` 원본의 앱 번들 사본이며 런타임 다운로드가 없습니다.

## 시작 화면·첫 실행 소개 (2026-10-14)

- 실행할 때마다 OS 시작 화면(Info.plist `UILaunchScreen`)이 흰 배경(`LaunchBackground`)에 로고(`LaunchLogo`, `DearbyLogo` 원본을 줄인 사본)를 보여 준다. 인위적인 지연은 없다. 두 Info.plist에 같은 값이 있고, 생성 스크립트의 빈 시작 화면 자동 생성은 뺐다.
- 첫 실행에만 `pages/intro/ui/IntroPage.swift`가 로고와 "패스키로 시작하기"·"둘러보기"(애플 로그인을 켠 빌드는 그 위에 "Apple로 계속하기", 서버가 구글 로그인을 켰으면 "Google로 계속하기")를 보여 준다. "패스키로 시작하기"는 기존 `SignInSheet`(패스키 로그인 기본, "새 패스키로 시작" 가능)를 띄운다. 로그인 성공이나 "둘러보기"를 누르면 `dearby.onboarding.shown`을 저장하고 홈 탭으로 간다. 취소·실패면 소개에 남고 오류 문구를 보여 준다. 로그인은 필수가 아니다(App Store 5.1.1).
- 실행할 때 토큰이 이미 있으면 소개를 건너뛰고 표시를 저장한다.
- 소개를 다시 보려면 앱을 지우고 다시 설치한다.

## 연결 설정

계약 `shared/contracts/native-v1.md`의 "연결 설정"을 따른다. 코드에는 운영 도메인을 두지 않는다.

- 빌드 환경값 `DEARBY_API_ORIGIN`이 Info.plist `DearbyAPIOrigin`으로 들어간다(`DEARBY_WEB_ORIGIN`은 공유 제거로 없어졌다). 명령행 `xcodebuild`는 환경값을 빌드 설정으로 읽는다. Xcode 화면에서 빌드할 때는 scheme의 환경값이 아니라 빌드 설정으로 넘겨야 한다.
- Release: `scripts/validate_origins.sh` 빌드 단계가 이 값이 `https://<도메인>`(IP·포트·경로·대문자 없음)인지 검사하고, 아니면 빌드를 실패시킨다. Associated Domains는 두 entitlements 모두 패스키용 `webcredentials:wid.io.kr`만이며 `applinks`는 없다. 기기 빌드는 provisioning profile에 Associated Domains 기능이 있어야 한다.
- Debug: 값이 없으면 `http://localhost:3000`(API 기본 포트)을 쓴다. http는 localhost·127.0.0.1만 허용한다. 코드는 `shared/config/AppOrigins.swift`, 조립은 `app/providers/ConnectionProvider.swift`다.

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
- 흐름: 이미 연결된 애플 계정(200)은 바로 로그인한다. 처음 보는 계정(202)은 로그인 시트에서 "새 계정으로 시작"(`register`)과 "기존 패스키 계정에 연결"(패스키 로그인 → `/me/identities/apple`)을 고른다. **2026-10-17 사용자 결정으로 202 직후 먼저 자동으로 시도한다(`AccountViewModel.linkOrStart`)**: `Passkey.assert(localOnly: true)`(`.preferImmediatelyAvailableCredentials`)로 이 기기(iCloud 키체인)의 Dearby 패스키만 묻는다. 패스키가 있으면 Face ID 한 번으로 로그인·연결하고, 없으면 묻지 않고 바로 새 계정으로 시작한다. OS는 "패스키 없음"과 "시트 닫음"을 모두 `canceled`로 돌려주므로, 시트가 뜰 틈도 없이(400ms 안에) 돌아온 취소는 패스키 없음으로 보고, 그보다 늦은 취소(사용자가 닫음)에만 위 선택 화면을 보여 준다. 웹은 조용히 확인할 수단이 없어 그대로 묻는다. 연결이 실패하면 패스키 토큰은 저장하지 않고 로그아웃한다(연결 안 된 채 로그인되지 않게). 연결 단계의 401은 시간 초과로 보고 처음부터 다시 하게 한다. 400(패스키 계정에 이미 다른 애플 계정이 있거나 이 애플 계정이 방금 다른 계정에 연결됨)은 "이미 다른 계정과 연결돼 있어서 연결하지 못했어요."다. 서버는 두 경우 모두 linkToken을 써 버리므로 선택 화면을 닫는다. 사용자 취소는 문구가 없고, 503·연결 실패는 "지금은 애플 로그인을 쓸 수 없어요."다. 이름은 첫 인가 때만 와서 그때만 `displayName`으로 보낸다. credential의 `authorizationCode`(UTF-8 문자열, 없으면 생략)도 함께 보내 서버가 계정 삭제 때 애플 토큰을 폐기할 수 있게 한다. 자격 철회 확인(`getCredentialState`)은 아직 없다.
- **켜짐(2026-10-16)**: 두 entitlements에 `com.apple.developer.applesignin` = `["Default"]`, 두 Info.plist의 `DearbyAppleSignInEnabled` = `true`. App Store profile은 Sign in with Apple 포함으로 다시 만들어 `IOS_APPSTORE_PROFILE_BASE64`에 넣었다(2026-10-16 13:45 UTC). 기기 Debug 빌드는 `com.dearby.dearby` 앱 ID에도 이 기능이 켜져 있어야 서명된다.
- 끄려면 두 값을 되돌린다. entitlement만 남기고 플래그를 끄면 버튼만 숨는다.
- 실제 애플 로그인은 entitlement가 있는 서명 빌드와 운영 API(`APPLE_CLIENT_IDS` 설정)에서만 확인할 수 있다. entitlement 없이 요청하면 OS가 실패로 돌려준다.

## 구글 로그인 (2026-10-18)

계약 `shared/contracts/native-v1.md` "구글 로그인"을 따른다. 애플·패스키와 나란한 세 번째 수단이며 SDK 없이 서버의 웹 방식을 쓴다.

- 코드: `entities/account/api/GoogleSignIn.swift`(무작위 verifier 43자와 SHA-256 base64url 해시, `ASWebAuthenticationSession`(callback scheme `dearby`, 비임시 세션이라 Safari의 구글 로그인 상태를 다시 씀), `dearby://auth/google?code=` 해석), `AccountClient`(`/v1/auth/google/web/start`·`/auth/google/web/exchange`·`/auth/google/register`·`/me/identities/google`), `AccountViewModel.signInWithGoogle`, 버튼 `shared/ui/DearbyGoogleButton.swift`.
- 버튼: 흰 바탕·`line` 테두리·높이 50·모서리 11에 "Google로 계속하기". 앞의 "G"는 공식 로고 파일을 `shared/assets/prototype`에 받아 넣기 전까지의 글자 자리표시다. `SignInSheet`에서 애플 버튼 아래, `IntroPage`에서 애플 버튼 아래에 보인다.
- 켜짐 판단: 빌드 플래그가 없다. `googleSignInEnabled`는 처음 참이고, `web/start`가 503(`AccountError.disabled`, 서버에 구글 클라이언트 미설정)을 돌려주면 그 실행 동안 버튼을 숨기고 "지금은 구글 로그인을 쓸 수 없어요."를 보인다. 네트워크 실패·5xx(`unavailable`)는 버튼을 숨기지 않는다.
- 흐름: 이미 연결된 구글 계정(200)은 바로 로그인한다. 처음 보는 계정(202)은 애플과 같은 `linkOrStart`를 탄다(이 기기의 Dearby 패스키를 조용히 묻고, 있으면 연결·없으면 새 계정, 시트를 닫았을 때만 "처음 보는 구글 계정이에요" 선택 화면). 애플·구글이 같은 `AccountViewModel.PendingLink`(provider·linkToken·email)를 쓴다. 사용자가 브라우저 시트를 닫거나 구글이 `error=cancelled`를 돌려주면 문구 없이 끝난다.
- 서버 쪽 필요 조건(앱 밖): 운영 API의 `GOOGLE_CLIENT_ID`·`GOOGLE_CLIENT_SECRET`·`GOOGLE_CALLBACK_URL`. 없으면 버튼이 숨겨진다. 실기기에서만 확인할 수 있다.

## 계정 삭제 (2026-10-16)

계약 `shared/contracts/native-v1.md` "계정 삭제"(App Store 5.1.1(v))를 따른다. 애플 로그인 플래그와 관계없이 보인다.

- 위치: 내 프로필 탭 맨 아래 "계정 삭제"(로그인 상태, 프로필을 불러오지 못한 화면에도). 앱에는 아직 로그아웃 버튼이 없다.
- 확인 창("계정을 삭제할까요?", 삭제/취소) 뒤 `DELETE /v1/me`. 다른 로그인 호출처럼 401이면 refresh를 한 번 하고, refresh가 거절되면 로그아웃 상태로 돌아가 "로그인이 만료됐어요"를 보인다. 요청 중에는 버튼을 막아 한 번에 하나만 보낸다.
- 204: Keychain 토큰을 지우고(서버가 refresh token을 이미 지워 `/auth/logout`은 부르지 않음) 로그아웃 상태로 돌아가 "계정을 삭제했어요. 기기에 남은 패스키는 설정 > 암호에서 지울 수 있어요."를 한 번 보인다. 받은 명함·내 명함 탭의 메모리 데이터는 로그아웃 상태로 다시 불러올 때 비운다.
- 실패: "계정을 삭제하지 못했어요. 잠시 후 다시 시도해 주세요."를 보이고 로그인을 유지한다.

## 프로필·명함 v2 (2026-10-17)

계약 `native-v1.md` "프로필·명함 v2". 2026-10-17 사용자 결정으로 유비쿼터스 언어는 "활동"이다(코드에서 조직·프로그램·활동·역할을 아우르는 이름은 `ActivityInfo`).

- 내 프로필(`ProfilePage`)은 데이터 입력만 한다. 칸을 바로 고치고, 불러온 프로필과 달라지면 아래에 "저장"·"원래대로" 막대가 나타난다(`ProfileForm.changed`). 저장은 `If-Match: <version>`을 보내고 409면 다시 불러오라고 안내한다. "변경 이력"(`ProfileVersionsPage`)에서 저장한 버전을 보고 되돌린다(새 버전으로 저장).
- 처음 저장하기 전(`version` 0) 프로필 탭은 토스식 아코디언 퍼널(`ProfileFunnelPage`, 2026-10-17 결정)이다: 이름 → 직무(선택) → 전화번호 → 이메일 → 소개(선택) 섹션이 순서대로 쌓이고, 열린 섹션은 하나뿐이며 답한 섹션은 56pt 행으로 접혀 탭하면 다시 열린다. 질문 28pt 굵게, 입력 20pt, 좌우 24pt, 하단 버튼 56pt("다음" → 모두 답하면 "프로필 저장"). 퍼널 동안 탭바를 숨긴다(`HomePage.hidesTabs`). 저장되면 인라인 편집기로 바뀐다. 프로필 없이 명함 만들기를 열면 안내만 보여 준다.
- 직무(계약 "직무", 2026-10-17): 직함 문자열 대신 `jobs` 태그. `JobTagPicker`가 `GET /v1/jobs`를 검색해 칩으로 여러 개(10개) 고르고, 없으면 직접 적는다(시계 아이콘 = 승인 대기, 승인 전에는 명함 `job`에 안 들어감). 퍼널의 직무 섹션과 편집기의 "직무" 항목이 같은 위젯을 쓴다. 명함·버전 화면의 `job`은 서버가 승인된 이름을 이어 준 문자열 그대로다.
- 이력은 단계별 입력(`HistoryFunnelPage`): 조직/활동 → 조직 → 프로그램 → (활동) → 역할 → 기간 → 설명 → 확인. 각 단계는 `ActivityInfoPickerView`로 등록된 활동 정보를 검색하거나(`GET /v1/organizations|programs|activities|roles`) 그대로 적는다(직접 입력 = 관리자 승인 대기, `approval`). 인증자료(`EvidenceSection`)는 저장된 이력에서 사진·PDF를 3개까지 올리고, `verified`가 아니면 "미확인" 배지가 붙는다.
- 명함 만들기(`CardComposerPage`): 명함 이름(선택)과 공개할 연락처·이력만 고른다. 본문은 앱 내장 기본 템플릿(`TemplateDocument.starter`: `{{profile.name}}`·`{{profile.job}}`·`{{profile.introduction}}`·전화·이메일 `{{contact:<id>}}`)이고 화면에는 채운 결과를 읽기 전용 미리보기(`DearbyMarkdownText`)로 보인다. 서버가 발행할 때 채운다(`POST /v1/cards {template}`). 템플릿에 든 연락처는 자동으로 공개되고, 승인되지 않은 이력은 고를 수 없다. 이름·선택이 바뀌면 "원래대로"가 나타나며, 내 명함 탭의 "이 명함 고치기"는 그 명함의 이름·선택으로 새 명함을 발행한다. 2026-10-18 사용자 결정으로 앱 안 꾸미기(렌더링 화면 편집기 `TemplateEditor`, 데이터 넣기)와 같은 날 추가한 웹 버전 스위치를 지웠다(계약 "템플릿 — 2026-10-18 앱 안 꾸미기 삭제").
- 오류는 ProblemDetail의 `detail`(`AccountError.rejected`)을 그대로 보인다.
- 확인하지 못한 것: 실제 서버·기기에서의 흐름(시뮬레이터 대상 빌드만 통과).

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
