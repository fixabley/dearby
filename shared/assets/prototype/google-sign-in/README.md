# Google 로그인 "G" 로고

2026-10-26. 로그인 시트·시작 화면의 `Google로 계속하기` 버튼 앞 "G" 원본이다(계약 `shared/contracts/native-v1.md` "구글 로그인"의 "앱 화면").
SVG는 받은 그대로이며 바꾸지 않는다. 앱은 아래 PNG 잘라낸 사본을 쓴다(iOS `apps/ios/Resources/Assets.xcassets/GoogleG.imageset`, Android `res/drawable-nodpi/ic_google_g.png`·`res/drawable-night-nodpi/ic_google_g.png`).

| 파일 | 내용 | SHA256 |
| --- | --- | --- |
| `button-icon-light.svg` | zip `Android + Web/SVG/Light/Theme=Light, Show text=No, Shape=Square, Platform=Android+Web.svg` 그대로(40×40 버튼, 흰 바탕·#747775 테두리, 가운데 20×20 G) | `6587b180203a99da15c0a041018e15c94ffd9556c4b9855bdc69806081a93791` |
| `button-icon-dark.svg` | zip `Android + Web/SVG/Dark/Theme=Dark, Show text=No, Shape=Square, Platform=Android+Web.svg` 그대로(#131314 바탕·#8E918F 테두리) | `2f7ecc307b1db0f1738456269ee2c4433b3a73c35060ced67a9afcfc6821e786` |
| `google-g-light.png` | zip `Android + Web/PNG @4x/Light/Theme=Light, Show text=No, Shape=Square, Platform=Android+Web@4x.png`(160×160, SHA256 `2bc2ae8e…a1c`)에서 G 영역(40,40)부터 80×80만 잘라냄(`sips -c 80 80 --cropOffset 40 40`, 다시 그리거나 크기 조정하지 않음). 모서리는 흰 바탕 | `232f08aa1d06cd02e60ab94d4479e2f1271e4c5c5067ea7a5ad030801f1fd2da` |
| `google-g-dark.png` | 같은 방식, `PNG @4x/Dark/...@4x.png`(SHA256 `0da0f2b6…0d5`)에서. 모서리는 #131314 바탕 | `778712f37b4330e7970d553ab72f09845635cc245e3441a3e412f4a0deeacd14` |

- 받은 곳: `https://developers.google.com/static/identity/images/signin-assets.zip`(2026-10-26, 855,303바이트, SHA256 `ba884069e12093b06bcfd776915081254a7c95094b80d50be2c5dc6bac1c1da1`). 안내 페이지 `https://developers.google.com/identity/branding-guidelines`("Download Pre-Approved Brand Icons", 페이지 표기 Last updated 2026-07-07 UTC).
- 그라데이션 G: 지금 가이드는 "standard color gradient super G"를 요구하고 옛 G(평면 4색) 사용을 금지한다("Don't … use an outdated Google 'G'"). 받은 자산도 그라데이션 G다. 계약의 "4색 G"라는 표현은 이 G를 가리키는 것으로 읽는다.
- 벡터로 옮기지 않은 이유: 공식 SVG의 G는 `mask`·`foreignObject`(CSS `conic-gradient`)·`feGaussianBlur` 필터로 그려져 Android VectorDrawable과 Xcode 에셋 카탈로그 SVG가 그리지 못한다. 손으로 다시 그리면 가이드의 "Don't create your own icon"에 걸리므로 공식 PNG를 잘라 쓴다. 가이드는 맞춤 크기 G를 "start with any of the logo sizes included in the download bundle"로 허용한다.
- 사용 조건(브랜드 가이드): Google 상표이며 Sign in with Google 버튼에만 쓴다. G의 색·모양을 바꾸지 않고(단색 금지), 버튼 경계와 행동 문구(`Google로 계속하기`)와 함께 두며, 바탕은 가이드의 라이트(#FFFFFF)·다크(#131314)·뉴트럴(#F2F2F2)만 쓴다. 잘라낸 PNG의 모서리에 바탕색이 들어 있으므로 앱 버튼 바탕은 정확히 라이트 #FFFFFF·다크 #131314여야 한다. 가이드에 없는 사용은 Google 서면 동의가 필요하다(Guidelines for Third Party Use of Google Brand Features). 페이지 글은 CC BY 4.0이지만 로고 사용 권리를 주지 않는다.
