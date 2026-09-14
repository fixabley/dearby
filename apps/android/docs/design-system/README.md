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
| `PrimaryButton(onClick, modifier, enabled, content)`, `SecondaryButton(onClick, modifier, enabled, content)`, native `TextButton` | 재시도처럼 텍스트가 필요한 행동은 Primary/Secondary를 사용한다. 저장·상세·삭제·지도·캘린더·원문 액션은 아래 후속 정책의 native icon button을 사용한다. enabled·상호작용·48dp 최소 터치영역·semantics는 시스템 API를 보존한다. 승인된 두 강조수준 진입점은 유지하고 다른 모든 native 컨트롤의 alias는 추가하지 않는다. |
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

## 상태와 피드백

`StatusPanel(title, modifier, message?, kind = Neutral, action?)`은 `Surface` + `Text` + 선택 `CircularProgressIndicator` 및 action slot이다. `StatusKind`는 Neutral/Loading/Error 세 가지만 제공한다. 상태의 제목/설명은 polite live region으로 묶고 액션은 독립적인 native 버튼으로 읽는다. 오류는 errorContainer/onErrorContainer, 나머지는 surfaceContainer/onSurface를 사용하며 색 외에 문구로도 구분한다.

App의 초기 로딩/오류·재시도와 발견/즐겨찾기의 빈 화면에서 실제 사용한다. 큰 글자에서 상태 화면 전체는 스크롤 가능하다. 상태 저장·fetch·retry 정책은 App에 남아 있으며 패널은 값을 렌더링할 뿐이다. `StatusPreview.kt`의 light/dark/2배 글자와 `StatusPanelTest`의 loading→failure 접근성/재시도 콜백이 검증 진입점이다. 발견의 저장 피드백도 polite live region으로 제공하며 navigation은 기존 native selected semantics를 유지한다.

상태 컴포넌트 검증(2026-09-14): 전용5556 StatusPanelTest 1/1 통과, FSD 66파일/self-test24 통과. 기존 화면 상태 소유권·재시도 로직은 변경하지 않았다.

전체 최종 실행 결과·before/after 비교·기기 보존·검증 한계는 [검증 문서](VERIFICATION.md)에 정리한다.

남기는 custom 조합: `NoticeCard`는 기존 세로 pager·더블탭·State/콜백을 연결하므로 domain widget에 유지한다. `FavoriteOrganizationCard`도 조직과 연결 공고·삭제를 조합하므로 Shared로 이동하지 않는다. 버튼의 도메인 label/하트와 제목은 소유 widget에 남고 native NavigationBar/ModalBottomSheet를 다시 구현하지 않는다.

## PR9 후속: 아이콘과 메타데이터

저장·상세·삭제·지도·캘린더 추가·원문 열기는 Material3 IconButton/OutlinedIconButton/FilledTonalIconButton을 직접 사용한다. text control 진입점 Primary/Secondary는 그대로 유지한다. 로컬 24dp vector와 theme tint를 사용하고 SDK/패키지는 추가하지 않는다. 각 아이콘은 조직명/일정명까지 포함하는 contentDescription을 가지며 native 최소48dp touch bounds를 보존한다. 저장은 toggle 삭제가 아니라 기존 idempotent save이며 selected/stateDescription과 채운 하트로 상태를 나타낸다. 관심 조직 이름은 카드 하단 titleMedium, 보조 labelMedium으로 명시한다.

`MetadataRow(icon: Painter, value: String, description: String, modifier, maxLines)`는 아이콘+bodyMedium 값이다. description에는 역할과 전체 원문 정보를 전달하고 clearAndSetSemantics로 중복 낭독을 막는다. 카드의 compact 1줄 표시는 시각적 요약이며 전체 값은 접근성과 상세에 유지한다. 일정·장소를 도메인명 없이 받으며 저장/조회/OS 행동을 소유하지 않는다.

`compactPeriodText(startAt, startOn, endAt, endOn, timezone, fallback, endLabel)`는 java.time의 엄격한 날짜와 원본 시간대로 표시만 만든다. 같은 날짜의 시간 범위는 한 번의 날짜와 HH:mm–HH:mm, 연도 경계는 두 연도, date-only는 시간 미확인으로 표시한다. malformed/충돌/역전 값은 fallback을 유지하며 calendar exporter의 종일/자정 규칙은 변경하지 않는다.

근거: [공식 IconButton](https://developer.android.com/develop/ui/compose/components/icon-button), [DateTimeFormatter](https://developer.android.com/reference/java/time/format/DateTimeFormatter), 2026-09-14 확인.

일정은 Page의 NoticeScheduleState가 원래 NoticePhase와 phase별 venues로 표시값을 만든다. titleLarge/bold 일정 이름(온라인 접두사 중복 방지) → 달력 MetadataRow → 장소 MetadataRow 순서이며 우측 calendar-add IconButton은 같은 index의 기존 draft를 전달한다. 신청은 별도 ContentSection/구분선으로 분리하고 원래 마감 안내와 제출 조건을 bodySmall/정보행으로 남긴다. 장소의 짧은 이름은 원래 층·호실을 포함하며 전체 주소·온라인 URL은 접근성 설명에, 주소는 기존 상세 장소 구획에도 보존한다. unknown 장소·시간을 확정값으로 바꾸지 않는다. 원래 이슈/출처도 유지한다.

SchedulePreview의 light/dark/2×와 InformationPreview의 MetadataRow를 제공한다. 이번 신규 JVM 회귀7건은 compactPeriodText4/NoticeSchedulePresentation3이며 기존 PhaseCalendarFlowTest를 예선·발표·결선 3개 모두의 이름/날짜/원본URL/장소 매칭으로 강화했다. 기존 icon action 테스트는 accessible name/selected/최소48dp touch bounds/콜백을 검증한다.

후속 구현 검증(2026-09-14): 최종 소스로 JVM46, 전체계측39, Debug/Release/계측 APK, Lint 오류0/기존12, FSD68/self-test24 통과. 과거 f4784a1 결과와 구분하며 실행 로그는 `build/icons-schedule-final-build.log`, `build/icons-schedule-final-instrumentation.log`다.

날짜 충돌 때 접근성 설명은 startsAt/startsOn/endsAt/endsOn을 각각 보존하며 신청의 opens/closes 필드도 같은 규칙이다. 원본 시각과 날짜 중 하나를 선택해 나머지를 숨기지 않는다.

### 캘린더 상세 날짜/시간 (2026-09-15)

`DetailMetadata(icon, primary, secondary, description, modifier, action)`은 범용 두 줄 정보와 native action 슬롯이다. bodyLarge/Medium과 bodyMedium/onSurfaceVariant로 위계화하고 전체 접근성 설명을 병합하되 버튼은 독립 액션으로 유지한다. NoticeDetail의 `detailPeriod` projection이 같은날 날짜 하나/시간범위, 여러날 시작·종료 각각의 날짜/시간, 시간미확인·시간대·연도경계를 조립한다. 기존 compactPeriodText의 strict validation을 재사용하며 invalid/역전/충돌은 원문과 모든 raw 날짜 필드를 표시한다. 신청 원문은 정확히 일치하는 일반 ISO 마감문구만 생략하고 24:00·불확실성은 보존한다. 카드/Calendar exporter에는 변경이 없다.

참고: [Google Calendar Android 공식 도움말](https://support.google.com/calendar/answer/72143?co=GENIE.Platform%3DAndroid&hl=en)은 제목·장소·종일 옵션을 별도 속성으로 다룬다. 전용5556의 설치된 Calendar는 계정 설정 화면만 열려 실제 일정 상세는 확인하지 못했다. 줄 분리와 font 위계는 이 문서와 사용자 요구를 바탕으로 한 설계 해석이며 Google 화면을 그대로 재현했다는 뜻이 아니다. 계정 설정/일정 저장은 진행하지 않았다.

장소도 DetailMetadata를 사용한다. 상위 detailPlace projection은 명확한 끝의 `(5층)`/`459호`/`1호실·6호실`만 보조 줄로 분리하며 괄호 건물코드와 불확실 문구, 전체 주소는 유지한다. 지도 native IconButton 슬롯은 해당 일정/venue에 붙고 원본 NoticeVenue를 그대로 전달한다. 모든 venue를 일정에 표시한 경우 하단에서 반복하지 않으며 location summary 전체가 venue에 이미 포함된 경우만 생략한다. 온라인은 온라인 표시와 URL 텍스트/전체 접근성 설명을 유지하고 URL을 자동 실행하지 않는다. 신청은 접수방법과 제출처를 분리한다.
