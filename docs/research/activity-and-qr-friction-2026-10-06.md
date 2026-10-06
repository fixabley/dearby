# 활동 신청·QR 공유·게스트 명함 경로 조사

- 조사일: 2026-10-06 (KST)
- 코드 기준: `origin/main` `db027a51e2c38bcaa76649a634416134ee09b9b2`
- 범위: iOS·Android·웹의 현재 화면 및 UI 테스트, Mobbin 검색 결과, iOS 웹 앱 관련 Apple/WebKit 문서
- 산출물: 경로별 사용자 탭 수와 후속 개선 제안. 소스·제품 코드는 변경하지 않았다.
- 화면 이미지는 Mobbin에서 화면 내용을 판독하는 데만 사용했고 저장소에 복사하지 않았다.

## 먼저 볼 결론

1. **활동 상세에 도달해 공식 신청 링크를 누르는 Dearby 탭은 세 플랫폼 모두 2회**다. 신청 링크를 브라우저에서 여는 탭까지 포함하면 iOS 3회, Android 3회, 웹 2회다. 외부 신청 사이트의 폼 입력·제출은 이 수에 포함하지 않는다. 모바일 앱은 오프라인 예시라 실제 신청이나 신청 완료를 제공하지 않는다.
2. **QR 탭은 네이티브 앱에서 1회**, 현재 QR을 공유 메뉴에서 실제 동작(링크 복사/공유)을 택하는 데 iOS 3회, Android 3회와 안내 확인 1회가 더 든다. 웹에는 QR 발행·공유 화면이 없다.
3. **휴대폰 기본 카메라에서 게스트 명함으로 들어오는 Dearby 경로는 현재 iOS·Android에 없다.** 웹 `/cards/<cardId>`를 직접 연 경우 화면 열기까지 0회, 게스트 저장 버튼까지 1회다. 요청한 `/s/<shareID>` URL은 현재 main 웹 경로에 없다.
4. iOS Safari는 웹페이지가 자체적으로 홈 화면 설치를 한 번의 탭으로 완료하는 API를 제공하지 않는다. 사용자가 공유 메뉴에서 직접 추가해야 한다. 홈 화면 웹 앱은 Safari와 별도 웹사이트 데이터 저장소를 사용하지만, 지원되는 추가 흐름에서는 iOS 17.2부터 쿠키를 추가 시점에 복사할 수 있다. 따라서 “쿠키가 완전히 공유된다”와 “쿠키가 처음부터 전혀 복사되지 않는다” 둘 다 부정확하다.
5. 검색 목표인 **5탭/3탭은 현재 신청 경로와 QR 화면 진입 경로에서 이미 충족**한다. 실제로 해결할 제품 결함은 “실제 서비스 공유 QR”과 “설치된 Dearby가 없는 상대의 기본 카메라 유입”이다. 1탭 홈 화면 추가는 웹만으로 보장할 수 없으므로, 1탭 게스트 명함 저장을 핵심 성공 경로로 두고 설치를 선택적 안내로 제공하는 편이 현실적이다.

## 계산 기준

- 시작점은 앱/웹의 첫 발견 화면이다. 화면을 여는 데 필요한 탭만 세며 스크롤, 시스템 권한 승인, 키보드 입력, 뒤로 가기는 제외한다.
- `Dearby 내` 탭은 Dearby가 소유한 화면에서 누른 횟수다. 웹의 공식 신청 링크를 열거나 네이티브 브라우저를 실행하는 동작은 Dearby 탭과 별도 열에 표시한다.
- 목표가 “참가 신청까지”여도 외부 공식 사이트의 신청 제출은 Dearby 코드와 테스트가 통제하지 않으므로 합산하지 않는다. 이 문서의 수치는 **신청 사이트 진입까지**이며 실제 제출 완료까지의 총 탭 수가 아니다.
- Mobbin의 `screen_count`는 저장된 플로우의 화면 수다. 터치 로그/탭 수가 아니다. 확인한 미리보기에서 CTA 전환이 명확한 경우에만 CTA 탭 수를 별도로 셌다. 불확실한 흐름은 화면 수로만 기록했다.

## 현재 main의 화면별 탭 수

### (a) 첫 화면 → 프로그램 신청 진입

| 플랫폼 | 화면 전환 및 탭 | Dearby 화면 안 탭 | 링크/브라우저 진입 | 근거·한계 |
|---|---|---:|---:|---|
| iOS | 발견 목록 → 활동 상세 (1) → `공식 사이트에서 신청` (2, 예시 신청 시트) → `예시 링크를 외부 브라우저로 열기` (3) | 2 | 3번째 동작 | `ActivityDetailView`, `DemoApplicationView`; `DiscoveryNavigationTests`/`PrototypeTests`. 현재 URL은 `example.com` 예시다. 실제 서비스 CTA가 아니다. |
| Android | 발견 목록 → 활동 상세 (1) → `공식 사이트에서 신청` (2, 예시 브라우저 화면) → `외부 브라우저` (3) | 2 | 3번째 동작 | `DearbyApp`, `ActivityDetailPage`, `ApplicationBrowser`; `PrototypeFlowTest.discoveryApplicationAndMemoryReport`. 실제 접수 없음. |
| 웹 | 탐색 목록 → 활동 상세 (1) → `공식 사이트에서 신청하기` (2, 새 탭의 외부 사이트) | 2 | CTA가 외부 사이트를 연다 | `Discovery`, `ActivityDetail`, `web.spec.ts`의 상세·링크 확인. E2E는 링크 URL을 확인하지만 외부 신청서를 제출하지 않는다. |

탐색 목록에서 대상 활동이 바로 보인다고 가정했다. 유형 필터를 써야 하는 경우 필터 탭이 추가된다. 테스트의 필터 상호작용은 활동 선택 전에 별도로 검증되므로, 필터 사용 시 각각 1회를 더해 Dearby 내 3회(iOS/Android)다. 웹 필터는 선택형 칩이다.

**판정:** 현재 경로는 “공식 신청 화면 진입” 기준 5회 이내다. “실제 신청 제출” 탭 수는 공식 사이트마다 달라 산정할 수 없다. 모바일은 실제 신청을 할 수 없는 오프라인 프로토타입이며, 화면 문구도 이를 예시로 표시한다.

### (b) 첫 화면 → QR 공유

| 플랫폼 | 화면 전환 및 탭 | QR 화면 도착 | 링크 공유/복사 동작까지 | 근거·한계 |
|---|---|---:|---:|---|
| iOS | 발견 → `QR` 탭 (1) → `명함 공유` (2, 공유 메뉴) → `링크 복사` 또는 `링크 공유` (3) | 1 | 3 | `HomePage`, `QRPage`; `IdentityNavigationTests.testQRSharingScanAndCardEditor`. 현재 메뉴는 확인용이며 외부 복사·전송은 하지 않는다. |
| Android | 발견 → `QR` 탭 (1) → 공유 메뉴 (2) → `링크 복사`/`링크 공유` (3) → 예시 안내 `확인` (4) | 1 | 3 (완료 안내 닫기까지 4) | `DearbyApp`, `QrPage`; `CardFlowTest`. 실제 복사·전송은 하지 않는다. |
| 웹 | 대응 화면 없음 | 해당 없음 | 해당 없음 | `apps/web/src`에 QR 공유·발행 route가 없다. 게스트 공개 카드는 `/cards/<cardId>`로 연다. |

탭 목표의 “QR 공유 화면”을 QR 표시 화면으로 해석하면 네이티브 1회다. 다른 앱에 보내기/링크 복사까지 완료하는 기준으로도 iOS 3회, Android 3회(확인 탭을 포함하면 4회)다. Android 안내 확인까지 3회로 줄일 필요가 있다면 아래 개선안을 적용할 수 있다.

### (c) 비로그인 상대의 QR 수신 → 명함 보기/저장/홈 화면

| 플랫폼/유입 | 현재 화면 전환 및 탭 | 명함 보기 | 저장 | 홈 화면 추가 |
|---|---|---:|---:|---|
| iOS 앱 내부 예시 | 발견 → `QR` (1) → `QR 찍기` (2) → `예시 QR 읽기` (3) → 공유 카드 | 3 | 카드 화면에서 `카드 저장` 1회 추가 | 제공 안 함 |
| Android 앱 내부 예시 | 발견 → `QR` 탭 (1) → `QR 찍기` (2) → 스캔 영역 또는 사진 선택 (3) → 공유 카드 | 3 | 카드 화면에서 `카드 저장` 1회 추가 | 제공 안 함 |
| iOS/Android 기본 카메라 → Dearby | 현재 QR URL/Universal Link 연결과 카메라 유입 UI 테스트 없음 | 경로 없음 | 경로 없음 | 경로 없음 |
| 웹에서 유효한 `/cards/<cardId>` 직접 진입 | URL 로드 → 공개 명함 | Dearby 탭 0 | `명함 저장` 1회 | 현재 홈 화면 설치 CTA 없음 |
| 웹에서 요청된 `/s/<shareID>` 진입 | main에 해당 공유 route 없음 | 경로 없음 | 경로 없음 | 경로 없음 |

근거: iOS `QRPage`/`IdentityNavigationTests`, Android `QrPage`/`CardFlowTest.exampleScanPublicCardSaveAndSend`, 웹 `cards/[id]/page.tsx`·`PublicCard`·`web.spec.ts`. 네이티브 테스트는 앱 안의 가짜 읽기 또는 사진 선택을 검증한다. iOS 아키텍처는 카메라·QR 디코딩이 없고, Android 앱 매니페스트에도 카메라 권한이 없는 프로토타입 범위다. 웹 E2E는 공개 명함 조회와 비로그인 저장을 확인한다.

## Mobbin 참고 흐름

미리보기 화면의 이름은 Mobbin 플로우 이름과 화면에서 읽을 수 있는 타이틀/CTA로 적었다. Mobbin의 검색은 정밀한 제품별 필터가 아니어서 일부 검색은 다른 앱을 반환했다. 그 결과를 Blinq·Popl·HiHello의 화면 근거인 것처럼 사용하지 않았다.

| 제품/흐름 | 관찰한 화면과 탭 수 | 패턴 및 조사 한계 |
|---|---|---|
| [Luma · Accepting an invitation (guest)](https://mobbin.com/flows/95377e33-88a3-49d4-a23d-1fe7a20b4fad) | 초대 이벤트 상세(`Accept Invite`) → 등록 확인(`Confirm Registration`) → 완료(`You're In`): 완료까지 CTA 2탭, 저장된 플로우는 3화면 | 상세 문맥에서 명시적 수락, 확인 화면, 완료 피드백. 무료/간단한 참가 흐름의 단계 분리를 참고. |
| [Luma · Purchase a ticket (fixed quantity)](https://mobbin.com/flows/92c2dde4-f712-43c5-b1c0-fba5bc81858b) | 검색 결과 6화면. MCP 검색이 반환한 미리보기에는 티켓 구매 흐름 화면이 포함됐지만, 이 실행에서 화면 제목/전환 액션을 모두 검증하지 못함. 탭 수 미산정. | 6화면을 6탭으로 해석하지 않음. |
| [Eventbrite · Purchasing a ticket](https://mobbin.com/flows/da7d4575-6036-4c60-a292-d6fe3d5a5a38) | 이벤트 상세(`Get tickets`) → 티켓 선택 → 결제 수단/카드 정보 → `Tickets Secured` → 주문 확인(`Your order is confirmed`): 검색 결과 14화면. 표시된 주요 CTA로 완료 탭 수는 세지 않음. | 티켓 유형·가격과 결제 전환을 분리하고, 완료 뒤 `View your tickets`로 다음 행동 제공. 14는 탭 수가 아님. |
| [LinkedIn · LinkedIn QR code](https://mobbin.com/flows/623ae78d-5f37-4a0d-a5a7-f781a7d7900b) | QR 관리 화면(`My code` / `Scan`)과 스캔 화면을 포함한 3화면 플로우. QR 표시 화면에서 `Share my code`, `Save to photos`; 수신 쪽은 `Scan from photos` 제공. 탭 수는 미산정. | 공유와 수신 모드를 한 QR 진입점에서 나누고, 카메라 외 사진에서 스캔 대안도 제공. |
| [LinkedIn · Scanning a code](https://mobbin.com/flows/a31b9287-49da-4068-8d10-26a6a4e7dcde) | 스캔 화면 → Alex Smith 프로필: 2화면. 캡처에는 사진 스캔 대안이 보임. 진입 탭은 플로우가 시작되는 화면 밖이라 미산정. | 스캔 성공 뒤 명함 미리보기/프로필로 곧장 이동하는 수신 패턴. |
| [Apple Wallet · Pass detail information](https://mobbin.com/flows/040484d6-4902-4213-9127-06b471d0f18f) | 패스 상세 정보 3화면 검색 결과. 이 흐름은 패스 추가 흐름이 아니므로 추가 탭 수는 미산정. | 패스 세부 정보 참고만 가능. `Add to Apple Wallet` 구매 후 발급 흐름은 이 검색에서 확인되지 않음. |

- Blinq·Popl·HiHello를 각각 지정한 검색은 BFF·GroupMe 등 무관한 결과를 반환했다. 해당 제품의 패턴·단계를 보고서 근거로 사용하지 않았다.
- Apple Wallet 검색도 패스 삭제/상세 정보 결과로 연결되어, “이벤트 완료 → Apple Wallet 패스 추가” 패턴을 확인하지 못했다.
- 따라서 Mobbin 비교에서 검증된 주요 패턴은 Luma의 수락 확인·완료 피드백, Eventbrite의 결제 단계·주문 확인, LinkedIn의 QR 공유/스캔 모드와 사진 스캔 대안이다.

## 목표를 반영한 공통 개선안

아래의 전후 수치는 첫 발견 화면에서 출발한다. “외부 진입”은 링크/브라우저를 여는 Dearby 마지막 동작이며, 외부 사이트 안에서의 탭은 계속 별도다. 담당은 [에이전트 운영](../context/agent-roster.md)의 UI·유저플로우·API 소유권을 따른다. 이 문서는 구현 배정이 아니다.

| 개선안 | 탭 수 전 → 후 | 영향 화면 | 담당 | 위험·완화 |
|---|---|---|---|---|
| **A. 발견 카드에 빠른 신청 CTA**: `바로 신청` 유형에만 `공식 사이트 신청` 보조 CTA를 둔다. 현재 목록 → 상세 → 신청 시도 2탭을 목록의 CTA 1탭으로 줄인다. 상세로 들어가는 기존 경로도 남긴다. | iOS 2 → 1, Android 2 → 1, 웹 2 → 1. 외부 링크 시작도 iOS 3 → 2, Android 3 → 2, 웹 2 → 1. | 발견 카드, 활동 상세와 CTA | UI + 유저플로우, 모집 상태/URL 보장은 API | 카드의 주요 정보·접근성 공간이 줄고, 조건을 읽기 전에 외부로 이동할 수 있다. 등록형·현재 신청 URL 검증 완료 상태에만 노출하고 선발형/출처 불명은 상세 유지. 웹·두 모바일 동일 조건을 적용. 현재 모바일은 예시 CTA라 실접수로 표현하지 않는다. |
| **B. QR 화면의 공유 CTA를 직접 OS 공유로 연결**: 현재 QR → 공유 메뉴 → 채널 선택 3탭(+Android 안내 확인)을 QR 화면 → OS 공유 시트 2탭으로 단순화. 공유 메뉴의 추가 안내 단계는 제거한다. | iOS 3 → 2, Android 3(+확인 1) → 2. QR 표시 화면 진입은 양쪽 모두 1로 유지. | 네이티브 QR 표시 화면, 공유 시트; 웹은 로그인 후 QR 발행 UI 추가 시 동등한 “공유 링크” 버튼 | UI + 유저플로우 + API; 모바일 서비스 담당은 실제 로그인/발행/공유 기록 연결 시 | 잘못된 카드/활동이 담긴 공유, OS 공유 취소와 성공 기록 혼동 위험. 공유 시트에는 발행된 공유 URL만 넘기고 실제 공유 완료 이벤트와 발행 기록을 구분한다. `https://dearby.wid.io.kr/s/<공유ID>` 사용, QR은 현재 선택 명함으로 발행하고 선택한 활동만 포함. |
| **C. 공유 URL을 공개 게스트 명함의 진입점으로 연결**: 로그인/명함 발행 후 QR에 `/s/<shareID>`를 encode하고, URL에서 공개 정보를 렌더한다. 기본 카메라는 HTTPS URL을 열어 바로 명함으로 보낸다. | 현재 기본 카메라 경로: iOS/Android 미지원 → QR 스캔 뒤 웹 명함까지 Dearby 탭 0. 웹 현재 `/cards/<cardId>` 진입 0 → 새 공유 URL 진입 0. | QR 발행, 웹 `/s/<shareID>` route, 게스트 명함, 저장 CTA, 도메인 링크 연결 | API + 유저플로우 + 모바일 서비스; 공통 표현 UI | 해지된 링크, 공유 정보 범위, 명함 소유자 식별, 웹 저장 쿠키, 앱 설치 유무의 동작 분기가 위험. 공유 ID를 추측하기 어렵게 하고 해지/만료 응답을 만든다. 웹 게스트 저장은 현재 `/cards`의 저장 계약을 재사용한다. 설치 앱은 같은 URL을 앱 route로 받되 웹 fallback을 유지한다. |
| **D. 비로그인 명함을 1탭 저장하고 홈 화면 추가는 선택 안내**: 공유 명함 공개 직후 `명함 저장` CTA를 첫 화면에 유지한다. 성공 후 Safari/브라우저별 홈 화면 추가 안내를 선택 제공하며 설치를 저장의 선행 조건으로 삼지 않는다. | 공유 URL 열린 뒤 카드 저장: 웹 1탭 유지. 홈 화면 설치: 현재 N/A → Safari에서 공유 메뉴 → `홈 화면에 추가` → `추가` 등 최소 2~3탭, 브라우저·버전에 따라 달라짐. 한 탭으로 보장 불가. | 공개 명함, 저장 성공 상태, 설치 안내, 웹 메타데이터/아이콘·manifest | UI + 유저플로우; 공유 저장 API는 기존 guest-card API; 웹/PWA 모바일 서비스 담당 | 쿠키 삭제·다른 브라우저·앱 내 브라우저 때문에 게스트 저장 목록이 분리될 수 있고, 설치 안내를 카드 저장으로 오인할 수 있다. 저장 완료와 설치 상태를 다른 상태로 안내한다. 사용자가 스킵해도 명함 열람/저장을 완료할 수 있게 한다. |
| **E. 저장 탭 대신 `내 활동` 탭**: 사용자가 정한 정보 구조에 맞춰 신청 활동과 참여 확정 표식을 모은다. 탭에서 신청 목록을 한 번에 열고, 실제 참가 확정은 별도 확인 상태로 보여 준다. | 앱 첫 화면 → 신청 목록: 발견에서 현재 1회(탭)로 진입 가능한 단일 탭. 활동 상세에서 외부 신청 진입은 현재 2회 → A를 적용하면 1회. 앱의 내 활동 탭이 이미 기본 선택인 경우 신청 목록 0회. | 5탭 내비게이션, 활동 상세 신청 CTA/상태, 내 활동 목록·빈 화면 | UI + 유저플로우, 신청/참가 상태의 서비스 계약은 API + 모바일 서비스 | `신청함`과 `참여 확정/실제 참석`을 같은 사실로 표시하면 안 된다. 외부 사이트에서 신청했다는 사실은 현재 Dearby가 알 수 없으므로 신청 기록은 사용자 확인 또는 파트너 콜백 근거를 표시하고, 실제 참여 확정은 별도 출처/시점과 취소 경로를 둔다. 기존 저장 활동의 제거·이관은 별도 결정·데이터 영향 확인이 필요하다. |

### 우선순위

> **결정 문서 차이:** 이번 배정의 최신 요구는 `저장` 탭을 없애고 `내 활동` 탭을 두는 것이다. 현재 [신청 활동·명함 공유 문서](../context/feature-applied-activities-and-cards.md)는 `저장` 탭 안에 `저장한 활동 | 신청한 활동` 전환을 두도록 적혀 있어 서로 다르다. 이 보고서는 최신 배정 내용을 우선해 E안을 제안한다. 통합 전에 정본 문서의 정보 구조도 갱신해야 한다.

1. **C + D**: 카메라 수신 후 바로 공개 명함을 열고, 1탭 게스트 저장을 완성한다. 본래의 QR 네트워킹 문제를 직접 해결한다.
2. **B**: 실제 `/s/<shareID>` 발행 흐름에 연결하며 공유 탭을 줄인다. QR 표시 화면 진입은 이미 1탭이므로 주된 개선은 QR 자체의 유효성과 발행 이력이다.
3. **E**: 사용자가 확정한 내 활동 정보 구조를 실제 상태 계약과 분리해 정의한다. 외부 신청 링크만으로 “참가 확정”을 자동 판정하지 않는다.
4. **A**는 신청 5탭 목표가 이미 만족되므로 우선순위가 낮다. 한 탭 절감이 상세 정보 확인을 건너뛰는 비용을 정당화하는지 실제 전환 데이터로 판단한다.

## iOS 홈 화면·쿠키·앱 내 브라우저 검토

### 사실

- Apple 안내의 Safari 절차는 웹페이지의 공유 메뉴에서 `Add to Home Screen`을 선택하고 추가를 완료하는 사용자 작업이다. Safari 페이지에서 웹사이트가 호출해 설치를 끝내는 일반적인 one-tap 웹 API는 문서에 없다. 따라서 서비스가 “명함 보기 후 1탭으로 iOS 홈 화면 추가”를 보장할 수 없다. [Apple Support: iPhone 홈 화면에 웹사이트 추가](https://support.apple.com/guide/iphone/bookmark-a-website-in-safari-iph42ab2f3a7/ios)
- WebKit은 홈 화면 웹 앱의 웹사이트 데이터를 Safari와 격리한다고 설명한다. iOS/iPadOS 17.2부터 Safari, Safari View Controller 및 해당 동작을 구현한 브라우저에서 홈 화면 웹 앱을 추가할 때 쿠키를 새 웹 앱 데이터 저장소로 복사하는 기능이 추가됐다. 이는 **설치 시 한 번 복사**하는 동작이지 설치 후 Safari와 쿠키를 계속 동기화한다는 뜻이 아니다. [WebKit: Safari 17.2](https://webkit.org/blog/14787/webkit-features-in-safari-17-2/), [WebKit: Tracking Prevention](https://webkit.org/tracking-prevention/)
- iOS 17부터 Safari View Controller와 Add to Home Screen 동작을 제공하는 일부 브라우저에서 웹 앱 추가를 지원할 수 있다. 이를 모든 앱 내 브라우저에 일반화할 수는 없다. [Apple WWDC23: What’s new in web apps](https://developer.apple.com/videos/play/wwdc2023/10120/)

### 제품 환경에서 확인되지 않은 추정

- KakaoTalk 등 앱 내 브라우저는 앱·OS 버전 및 해당 브라우저 구현에 따라 자체 메뉴, 외부 Safari 열기, 웹 앱 추가 지원 여부가 달라질 수 있다. 조사에서 KakaoTalk의 현재 iOS 버전별 Add to Home Screen 동작을 보증하는 공식 개발 문서를 찾지 못했다. `WKWebView`를 쓰는 임의 내장 브라우저가 Safari의 설치 메뉴·쿠키 복사와 같은 동작을 제공한다고 전제하지 않는다.
- iOS 기본 카메라로 HTTPS QR을 읽으면 기기 기본 링크 처리 설정에 따라 브라우저 또는 설치 앱으로 열릴 수 있다. 아직 앱 링크/Universal Link 연결과 도메인 연관 파일을 검증하지 않았으므로 앱 딥링크가 항상 우선한다고 가정하지 않는다.
- 게스트 저장 쿠키가 홈 화면 추가 과정에서 복사되는지 여부는 시작 브라우저, OS 버전, 추가 UI 구현에 좌우될 수 있다. 운영 공유 세션의 로그인/게스트 정책으로 취급하기 전에 iOS 실기기에서 Safari와 주요 앱 내 브라우저를 각각 확인해야 한다.

### 권장 동작

1. QR은 `https://dearby.wid.io.kr/s/<공유ID>` HTTPS 주소로 구성해 카메라에서 앱 설치 없이 웹 명함을 열 수 있게 한다.
2. `/s/<shareID>`에서 바로 명함을 렌더하고 `명함 저장` 한 번으로 기존 게스트 저장 계약을 수행한다. 회원가입이나 홈 화면 설치를 열람 조건으로 걸지 않는다.
3. 저장 성공 후에만 선택적 “홈 화면에서 다시 열기” 안내를 제공한다. Safari에서는 단계 안내를, 지원 여부를 판별할 수 없는 내장 브라우저에서는 `Safari/기본 브라우저에서 열기`와 복사 가능한 URL을 안내한다. Safari 수동 설치 단계를 자동 실행된 것처럼 표시하지 않는다.
4. 웹의 게스트 저장 세션은 홈 화면 웹 앱으로 쿠키가 복사되지 않거나 이후 분리될 가능성을 고려한다. 현재 계약/백엔드가 지원한다면 공유 URL 자체를 다시 열어도 명함을 조회할 수 있게 하고, 저장 목록은 브라우저 세션 단위임을 명확히 한다. 쿠키 간 세션 병합을 임의로 구현하지 않는다.
5. iOS·Android 실제 앱이 QR 도메인을 처리할 때는 같은 URL에서 명함을 열고, 앱이 없거나 association이 실패하면 웹 공개 페이지로 이어지는 fallback을 유지한다.

## 조사 출처·재현 위치

- Mobbin 링크는 위 표의 각 플로우 이름에 연결했다. 검색 도구는 Luma·Eventbrite·LinkedIn 이외 제품 요청에서 무관한 결과를 반환했다. Mobbin 로그인/계정별 검색 노출 차이는 재현하지 못했다.
- Apple/WebKit 링크는 홈 화면 추가 절차·저장소 격리·쿠키 복사 관련 공식 출처다. 카카오톡 브라우저 동작에 대한 사실 근거로 사용하지 않았다.
- 저장소 검사 지점: `apps/ios/Sources/pages/home/ui/HomePage.swift`, `apps/ios/Sources/widgets/catalog/ui/ActivityDetailView.swift`, `apps/ios/Sources/features/application/ui/DemoApplicationView.swift`, `apps/ios/Sources/widgets/identity/ui/QRPage.swift`, `apps/ios/uiTests/DiscoveryNavigationTests.swift`, `apps/ios/uiTests/IdentityNavigationTests.swift`; `apps/android/app/src/main/java/com/dearby/nativeapp/app/DearbyApp.kt`, `pages/catalog/ActivityDetailPage.kt`, `features/application/ApplicationBrowser.kt`, `pages/qr/QrPage.kt`, `apps/android/app/src/androidTest/java/com/dearby/nativeapp/PrototypeFlowTest.kt`, `CardFlowTest.kt`; `apps/web/src/components/discovery.tsx`, `activity-detail.tsx`, `public-card.tsx`, `apps/web/tests/e2e/web.spec.ts`.
- 이 보고서 작성은 조사·정적 코드 및 테스트 소스 검토다. 빌드·UI 테스트·실기기 검증을 실행하지 않았다.
