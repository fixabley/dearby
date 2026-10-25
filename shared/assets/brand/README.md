# Dearby 브랜드 심볼 — 악수의 추상화

**2026-10-19 사용자 선택: B(맞물린 두 고리).** `symbol.svg`가 최종 심볼이고, `symbol-a/c/d/d2/d2-duo/d3/e.svg`는 탐색 기록으로 남긴다. 선택 이유: 두 고리가 서로를 통과하며 한 번씩 양보하는 구조가 "맞잡음·교환"을 손 모양 없이 전달하고, 실제 끊김으로 그려 어떤 배경에서도 단색으로 쓸 수 있으며, 48px까지 윤곽이 유지된다.

## 아이콘 규칙과 산출물

- `icon-source.svg` → `icon-1024.png`: 청록 `#007F80` 바탕이 정사각형 전체를 채우고 흰 심볼의 외접 상자가 아이콘 너비의 약 56%가 되도록(512 격자 1.8배) 가운데 둔다. 1024×1024, 알파 채널 없음(App Store 요구). 헤드리스 Chrome으로 그린 뒤 표준 라이브러리로 RGB PNG로 다시 썼다.
- `wordmark.svg` → `wordmark-2172x724.png`: 심볼 + "Dearby"(Pretendard Bold), 청록, 투명 배경. 앱·웹 로고 자리는 기존 2172×724 캔버스를 그대로 써 레이아웃이 바뀌지 않는다.
- iOS: `AppIcon.appiconset/AppIcon.png`(1024, 불투명), `DearbyLogo.imageset/logo-teal.png`(2172×724, 투명), `LaunchLogo.imageset/launch-logo@3x.png`(480×160, 투명).
- Android: 적응형 아이콘 `mipmap-anydpi-v26/ic_launcher(.round).xml` = `@color/ic_launcher_background`(#007F80) + `@drawable/ic_launcher_foreground`(108dp 캔버스, 66dp 안전 영역에 흰 심볼 벡터). 옛 `drawable/ic_launcher.xml`(흰 D)은 지웠고 매니페스트는 `@mipmap/ic_launcher`·`roundIcon`을 가리킨다. 최소 SDK 26이라 레거시 래스터 세트는 두지 않는다. 앱 안 로고 `drawable-nodpi/dearby_logo.png`도 새 워드마크.
- 웹: 심볼은 `src/shared/ui/brand-symbol.tsx`(symbol.svg 인라인, `currentColor`로 테마 청록을 따름) + Pretendard Bold "Dearby" 텍스트. `src/app/apple-icon.png`(180), `src/app/icon.png`(512), `public/icons/icon-192.png`·`icon-512.png`(매니페스트). 옛 `public/logo-teal.png`은 2026-10-19 지웠다.
- `sign-in-lockup.html` → `sign-in-lockup-light.png`·`sign-in-lockup-dark.png`(2026-10-25, 로그인 시트): 워드마크 아래 캐치프레이즈 "대외활동, 매일 맞춤으로"(Pretendard SemiBold), 1240×480 투명. 라이트는 청록 #007F80 + 회색 #6E6E73, 다크(`?theme=dark`)는 #3FB3B4 + #AEAEB2. iOS `DearbyLockup.imageset`(다크 appearance), Android `drawable-nodpi/dearby_lockup.png`·`drawable-night-nodpi/dearby_lockup.png`. 뽑기: `"Google Chrome" --headless --hide-scrollbars --default-background-color=00000000 --window-size=1240,480 --virtual-time-budget=2000 --screenshot=<out>.png "file://<abs>/sign-in-lockup.html?theme=light|dark"`.
- 다시 뽑을 때: `qlmanage`는 SVG의 @font-face를 읽지 못하므로 워드마크는 헤드리스 Chrome(`--default-background-color=00000000`)으로 그린다(브라우저로 SVG를 열어 캡처).


사용자 요청: "악수를 형상화하되, 상징성 있게 추상화". 손을 그리지 않고 **두 요소가 서로를 붙드는 구조**로 악수를 표현했다. 세 안 모두 단색(청록 `#007F80`) 한 가지로만 그려지며 그라데이션·그림자가 없다. 배경이 청록이면 흰색(`#FFFFFF`) 또는 민트(`#F0FCFA`)로 반전해 쓴다.

| 파일 | 개념 | 구성 |
| --- | --- | --- |
| `symbol-a.svg` | **두 손이 만드는 D** — 왼손(세로 기둥 + 위로 뻗는 팔)과 오른손(아래로 뻗는 팔 + 둥근 호)이 만나 글자 D가 된다. 두 손이 맞닿는 왼쪽 아래 이음이 "맞잡음"이다. | 선 2개, 굵기 62, 둥근 끝. 512 격자에서 기둥 x=160, 호 반지름 116, 중심 (262, 256). |
| `symbol-b.svg` | **맞물린 두 고리** — 두 고리가 서로를 통과한다. 위에서는 왼쪽 고리가, 아래에서는 오른쪽 고리가 앞에 온다(한 번씩 양보). 끊김은 마스크가 아니라 실제 빈 틈이라 어떤 배경에서도 단색 도형이다. | 호 2개, 반지름 100, 중심 (198, 256)·(314, 256), 굵기 58, 교차 지점 양쪽 42°씩 비움. |
| `symbol-c.svg` | **건네는 두 명함** — 두 장의 둥근 사각형(명함)이 서로에게 밀려 들어가고, 겹치는 부분을 비워 둔다. 교환 자체가 형태가 된다. 명함 교환이라는 제품 기능과 가장 직접 닿는다. | 면 1개(evenodd), 카드 240×224, 모서리 40, 겹침 112×152. |
| `symbol-d.svg` | **맞잡은 두 손(갈고리 걸쇠)** — 왼쪽·오른쪽에서 들어온 두 팔이 가운데서 서로를 갈고리처럼 건다. 매듭 안의 빈 렌즈가 "쥔 손", 손바닥 끝의 짧은 사선이 엄지다. 다섯 안 중 악수 동작이 가장 직접적으로 읽힌다. | 선 4개(팔 2 + 엄지 2), 굵기 46, 고리 반지름 62, 고리 중심 (216, 282)·(296, 230). |
| `symbol-e.svg` | **악수 리본** — 끊기지 않는 리본 하나가 두 고리(두 손)를 만들고 가운데서 대각선으로 교차하며, 교차점만 살짝 두꺼워져 "쥠"이 된다. 고리 사이 빈 공간이 손목. | 선 2개(리본 1 + 교차 두께 1), 굵기 48/60. 32px에서는 무한대 기호(∞)로 읽히는 한계가 있다. |

## 구성 격자와 여백

- 작업 격자 512×512. 심볼은 가운데 정렬, 상하좌우 최소 72의 안쪽 여백을 둔다(A: 좌 160 우 ~378, B: 좌 98 우 414, C: 좌 72 우 440).
- 여백(clear space): 심볼 높이의 1/8 이상(A·B는 선 굵기 하나, 62/58). 그 안에 글자·다른 요소를 두지 않는다.
- 최소 크기: 24px(파비콘·탭 아이콘). 세 안 모두 24px에서 윤곽이 유지되는지 `brand-preview.html`에서 확인했다. B는 24px에서 끊김이 가늘어지므로 20px 아래에서는 쓰지 않는다.
- 앱 아이콘: 심볼을 아이콘 너비의 60%로 두고 배경은 청록(심볼 흰색) 또는 민트(심볼 청록) 두 가지만 쓴다. iOS 1024×1024, Android 적응형(전경 108dp 중 안전 영역 66dp)으로 뽑을 때 같은 비율을 유지한다.

## 워드마크

`wordmark.svg`: A 심볼이 대문자 D 자리를 맡고 "earby"는 Pretendard Bold(`shared/assets/fonts/pretendard/Pretendard-Bold.otf`, @font-face)로 잇는다. 브라우저에서는 Pretendard로, macOS QuickLook 미리보기(`preview/wordmark.svg.png`)는 글꼴을 읽지 못해 시스템 대체 글꼴로 그려졌다. 글자 색은 심볼과 같은 청록, 자간 −4.

## 추천과 순위 (2026-10-19, 다섯 안)

코디네이터 검토: A는 평범한 D, B는 체인 링크, C는 겹친 카드로 읽혀 악수 단서가 약하다는 지적을 받아 D·E를 추가했다. 악수가 읽히는 정도와 작게 줄였을 때의 안정성을 함께 본 순위:

1. **D 맞잡은 두 손** — 두 팔이 서로를 거는 동작이 보이고, 48px에서도 "걸쇠"로 읽힌다. 엄지 사선이 손임을 알려 준다. 단점: 32px 아래에서는 매듭이 뭉친다(앱 아이콘·48px 이상 권장). 추천.
2. **A 두 손이 만드는 D** — 악수 단서는 약하지만 브랜드 첫 글자와 한 몸이라 아이콘·워드마크 일관성이 가장 좋다. D를 심볼로 쓰고 A를 워드마크의 D로 쓰는 조합이 가능하다.
3. **B 맞물린 두 고리** — 연결은 읽히지만 흔한 링크 아이콘과 겹친다.
4. **E 악수 리본** — 작게 줄이면 ∞ 기호로 읽힌다. 큰 그래픽(온보딩) 전용.
5. **C 건네는 두 명함** — 명함 교환 기능과는 맞지만 악수 단서가 없고 작게 줄이면 창문·레이어로 읽힌다. 보조 그래픽으로만.

제안: 심볼 D + 워드마크의 D 글자는 A(두 선 구조를 공유해 같은 굵기·둥근 끝으로 통일). 선택이 끝나면 앱 아이콘(iOS 1024, Android 적응형 전경/배경)과 파비콘을 그 심볼로 뽑는다.

## 미리보기

`brand-preview.html`을 브라우저로 열면 다섯 안을 512/128/48/24px, 흰·민트·청록 배경으로 본다. `preview/*.png`는 QuickLook(`qlmanage`)으로 뽑은 512px 확인용이고 `preview/small/`은 48px(가독성 확인)이다.
