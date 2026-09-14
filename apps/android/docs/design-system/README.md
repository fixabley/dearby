# Android native 디자인 시스템

Issue #2. Android 12/API31 이상, 기존 Compose BOM 2026.02.01/Material3를 사용하며 의존성을 추가하지 않는다.

## 테마와 native API

`DearbyTheme(darkTheme = isSystemInDarkTheme(), dynamicColor = true, content)`는 App 최상위에서 한 번 사용한다. API31부터 제공되는 wallpaper 기반 `dynamicLightColorScheme`/`dynamicDarkColorScheme`이 기본 정책이다. 제품 설정이나 저장 키는 추가하지 않는다. `dynamicColor = false`이면 완전한 Material3 기본 light/dark 팔레트를 사용한다. preview/테스트는 이 옵션으로 재현성을 확보한다. 사용자 시스템의 다크 모드·글꼴 확대·폰트 fallback을 따른다.

| 공개 진입점 / native mapping | 사용 규칙 · 현재 사용처 |
| --- | --- |
| `MaterialTheme.colorScheme` | 본문 onSurface, 보조 onSurfaceVariant, 주요 액션 primary/onPrimary, 오류 errorContainer/onErrorContainer. 카드와 버튼은 native defaults가 role을 선택한다. 색 hex를 화면에 추가하지 않는다. |
| `MaterialTheme.typography` / `Typography()` | 카드·시트 제목 headlineSmall/titleLarge, 조직 제목 titleMedium, 정보 bodyMedium, 보조 bodySmall/labelMedium. 별도 폰트·sp 복사·고정 높이 텍스트 금지. 제목은 소유 화면/widget 안에 둔다. |
| `MaterialTheme.shapes` / `Shapes()` | native shape scale. OutlinedCard·Button·ModalBottomSheet의 기본 shape를 유지한다. |
| `Spacing` | extraSmall 4, small 8, medium 12, large 16, extraLarge 24, section 32dp. 콘텐츠 간격만 담당하며 버튼 높이·아이콘·탭 규격을 덮어쓰지 않는다. |
| `PrimaryButton(onClick, modifier, enabled, content)`, `SecondaryButton(onClick, modifier, enabled, content)`, native `TextButton` | 저장/원문/재시도는 Button, 상세/지도/캘린더는 OutlinedButton, 연결 공고/삭제는 TextButton. enabled·상호작용·48dp 최소 터치영역·semantics는 시스템 API를 보존한다. 승인된 두 강조수준 진입점은 유지하고 다른 모든 native 컨트롤의 alias는 추가하지 않는다. |
| `OutlinedCard`, `Surface`, `HorizontalDivider` (직접 사용) | 공고/조직 card는 widget에 남긴다. 시트·navigation·tab은 기존 Material3를 그대로 사용한다. |

`NoticeCardSaveButton`은 조직 이름, 저장 문구, 하트 선택을 조합하는 도메인 widget이다. Shared로 옮기지 않는다. icon은 옆의 Text로 읽을 수 있어 contentDescription=null이며 간격은 ButtonDefaults.IconSpacing이다. 버튼 텍스트 줄 수를 강제하지 않아 큰 글자·긴 조직명에서도 줄바꿈한다.

근거(2026-09-14 확인): [Android Material3 테마](https://developer.android.com/develop/ui/compose/designsystems/material3), [Compose 접근성 기본값](https://developer.android.com/develop/ui/compose/accessibility/api-defaults). 기존 안정 버전 API 범위에 적용하며 최신 문서의 새 SDK/API를 도입하기 위해 업그레이드하지 않는다.

첫 테마/버튼 변경 검증: 2026-09-14 Debug 빌드와 lint 성공, FSD 60파일/self-test24 통과. `ThemePreview.kt`의 light/dark/2배 글자 및 disabled native controls preview를 제공한다. baseline f963401의 화면은 `evidence/before-*.png`다. 전체 최종 계측은 후속 컴포넌트 적용 뒤 수행한다.

## 정보행과 섹션

| Shared 진입점 | native mapping · 사용처 · 유지 이유 |
| --- | --- |
| `InformationRow(label, value, modifier, maxLines = Int.MAX_VALUE)` | Column + Text(labelMedium/bodyMedium), mergeDescendants. 기존 NoticeFact의 도메인명을 없애고 label/value를 한 접근성 노드로 읽는다. 발견 카드와 상세 모든 정보에 사용. 좁은 폭과 큰 글자에서 수평 ListItem의 label/value 열보다 세로 배치가 적합하다. 카드에서만 2줄 요약을 허용하며 상세는 생략하지 않는다. |
| `ContentSection(modifier, content: ColumnScope)` | surfaceContainer/onSurface + shapes.large + 동일 inset/간격. 상세의 조직·신청·일정·장소에 사용. 반복되는 정보+액션 묶음을 통일하며 제목/도메인 값/이벤트는 소유 화면이 전달한다. |

`InformationPreview.kt`는 light/dark/2배 글자에서 긴 정보와 액션 묶음을 제공한다. `DesignSystemTest`는 정보의 병합 읽기, disabled 콜백 차단·48dp native 터치 높이, 큰 글자 카드의 본문/버튼 겹침 회귀를 검증한다. 발견 카드의 compact 분기는 높이/시스템 fontScale로 판단하며 상세에서 전체 정보를 유지한다. baseline 2배 글자 겹침은 `evidence/before-dark-large-discovery.png`에서 확인할 수 있다.

정보/액션 컴포넌트 검증(2026-09-14): 전용5556의 DesignSystemTest 3/3 통과. 버튼은 40dp 시각 높이를 임의로 확대하지 않고 SemanticsNode.touchBoundsInRoot의 48dp 이상 폭·높이를 검사한다. 큰 글자 카드의 저장·상세 버튼 배치와 콜백, 병합된 label/value를 함께 검증했다.
