# Static image sources

- `Assets.xcassets/DearbyLogo.imageset/logo-teal.png`: unchanged approved `docs/design/approved/logo-teal.png` (see root manifest).
- `Assets.xcassets/AppIcon.appiconset/AppIcon.png`: exact existing main Android launcher geometry/colors from `apps/android/app/src/main/res/drawable/ic_launcher.xml` (introduced in `911c501`, included in main `b3094f9`). Deterministic CoreGraphics rasterization to opaque sRGB 1024×1024; background `#007F80`, white D, even-odd fill, no new logo or AI generation. Source SHA256 `e071e8148b946d65574d0fa900f9fcffe08398c6aed96f62d92218466b7faa44`; PNG SHA256 `206782d19e5e36dc27c1479ba9a7a2f162e0db95eb132aae15cc68c44608bcc0`. This is an existing merged launcher candidate, not a separately documented visual-approval artifact. The approved 2172×724 wordmark is preserved for in-app use; reducing the entire wordmark into a square makes it much smaller at launcher size. The pre-web iOS AppIcon catalog had no image. Xcode generates iPhone/iPad variants from this single-size catalog.

## 2026-10-04 prototype assets

ConferencePhoto/CampPhoto/MeetupPhoto는 조율 세션 소유
`shared/assets/prototype/{conference.png,camp.png,meetup.png}`의 변경 없는 사본입니다.
생성 prompt/출처는 원본 폴더 README를 따릅니다. (ExampleQR 사본은 명함 공유 제거로 더 쓰지 않습니다.)
기존 DearbyLogo/GitHubMark/AppIcon은 보존했습니다. 화면 전체 screenshot을 UI로 사용하지 않습니다.

## 2026-10-26 연락처 브랜드 아이콘

`ContactGitHub`·`ContactInstagram`·`ContactBehance`·`ContactKakaoTalk`은 `shared/assets/prototype/contact-icons/*.svg`(Simple Icons 16.36.0)에
`width`·`height`(24)와 `fill`(계약 "연락처 아이콘·바로가기"의 색)만 더한 벡터 사본입니다. GitHub·Instagram은 다크 모양 흰색 사본이 함께 있습니다. 출처·라이선스는 원본 폴더 README.
