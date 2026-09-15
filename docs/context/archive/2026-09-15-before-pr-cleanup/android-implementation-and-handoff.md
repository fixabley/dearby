> 최신 상태 (2026-09-15 02:00 KST): #10 최신 요청 구현·검증 완료, iOS PR12 / Android PR11 Draft push 완료. 두 담당 세션 retained, active 작업 없음. 최종 head·검증·한계는 [issue-10-device-calendar.md](issue-10-device-calendar.md)의 최종 완료 확인 참조. 아래 진행 중 기록은 이전 이력입니다.

# Android — 구현과 인계

갱신: 2026-09-15 01:34 KST. #2 PR9 head18aeb6b 완료·미병합. #10 Draft PR11 head1cd2ae6의 권한/스위치/기기조회 구현은 완료했고, 현재 최신 시각 변경 후속이 active다. dearby-android worktree / fixabley/dearby-android-calendar-busy, Task task_67f4ae81cfc8 / Dispatch ctx_9489e36b2512, terminal term_3addcbfe-11e6-456f-96d7-9ad3af965286. [기기 캘린더 인계](issue-10-device-calendar.md)가 최신 정본. 본문 Primary/보조색+교집합점선/경고, 날짜시간줄구분/한국시간표시생략을 PR11에 반영 중. 아래 #2 검증은 과거 실행이다.

## 최신 후속 — 캘린더 참고 상세

날짜/시간 분리 DetailPeriodState·Shared DetailMetadata와 장소명/주소/호실 DetailPlaceState를 적용했다. 지도 버튼은 각 장소에 붙이고 중복 구획을 제거했다. 기존 card/cache/export 유지. Google 공식 문서 근거이며 실제 Calendar는 계정 설정까지만 확인했으므로 상세 레이아웃 복제를 주장하지 않는다.

새 실행: JVM52·관련계측7·Debug/계측APK·lint 오류0(기존12)·FSD71/fixtures24 통과, 상세 대표PNG8개 light/dark/2배. root가 XML/log·light/큰글자 PNG·PR scope/head·range diff check를 확인했다. 전체계측39/Release는 직전 검증이며 이번에 반복하지 않았다. 새 task_ad9ae2193dc5/ctx_89e0a6506dbb succeeded, delivery_4dcbf23e4737 처리/ack, terminal retained. 다음은 리뷰/통합이다.

## #2 초기 완료와 검증

MaterialTheme 동적 색상·native typography/shapes, spacing, Primary/Secondary(enabled), InformationRow·MetadataRow·ContentSection·StatusPanel을 Shared/UI에서 실제 화면에 적용했다. 카드 저장/상세·삭제·지도·캘린더는 native 아이콘 액션, 대상 조직명은 화면과 접근성에 유지한다. 신청 별도 구획과 일정별 굵은 이름→날짜→장소/온라인, 개별 calendar callback을 구현했다. compactPeriodText는 시간대/연도/자정/불명/충돌 날짜를 보존한다. 표시 State/VM 변경 외 domain·Room·샘플 규격은 유지했다.

최종 JVM46, 기기 계측39, FSD68/검사fixture24, Debug/Release(R8)/계측APK, lint 오류0(기존 warning12) 통과. light/dark·기본/2배 새 PNG36개. root가 실제 카드/신청/행사/다단계/큰글자 이미지를 검토하고 XML·build로그·원격 head·PR 앱범위를 확인했다. 상세 보고는 worker checkout의 `apps/android/docs/design-system/VERIFICATION.md`다.

외부 지도/Calendar 앱 내부와 실제 저장, TalkBack 전체 음성, API31 실기기·모든 OEM/태블릿/회전 조합은 미검증. 사용자5554 보존, 전용5556 설정/prefs 복원 후 리뷰용 유지.

Run run_207680e6497e의 후속 task_117faf7b80eb/ctx_386a8761ea2f succeeded 보고를 검토하고 delivery_22eddd2d921c를 ack했다. terminal term_3addcbfe-11e6-456f-96d7-9ad3af965286는 사용자 역할 세션 유지 요청에 따라 retained. 로컬 context/workstreams는 PR에 포함하지 않는다. 다음은 PR 리뷰와 사용자 요청에 따른 통합이다.

## 구현 기준

Dearby / `io.fixabley.dearby`, Kotlin·Jetpack Compose, Android 12(API 31) 이상. 실제 트리·노출 진입점과 버전 설정은 [Android 아키텍처](../../apps/android/ARCHITECTURE.md)와 앱 Gradle 파일을 따른다.

- App이 NoticeSession·저장소·공유 FavoritesState 및 라우팅을 소유한다. ViewModel이 독립 공고/조직 모델을 조합해 State를 제공한다.
- 즐겨찾기는 SharedPreferences 파일 `dearby.favorites.v1`, 키 `organizationIDs`를 유지한다.
- `widgets/notice/noticecard/`, `widgets/organization/favoriteorganizationcard/` 내부는 Composable·ViewModel·State 동위 배치다.
- Shared/UI PrimaryButton은 Material3 Button, SecondaryButton은 OutlinedButton이다. NoticeCardSaveButton에서 조직명·하트·콜백을 조합하며 제목은 카드 안에 둔다.

## Room 캐시

공고·조직 각각 L1 → Room ID 조회 → 번들 mock 순서다. NoticeRecord는 버전 있는 공고별 payload, OrganizationRecord는 id/name/parentId를 저장한다. NoticeStorageCodec은 전체 NoticeModel 필드·버전·ID·손상을 검증한다.

App의 RoomSnapshotStore는 manifest·레코드 준비와 snapshot 변경을 transaction으로 묶는다. 외부 성공 값을 저장한 후 메모리에 반영하며 읽기/쓰기 오류는 missing으로 처리하지 않는다. 실패·취소 시 이전 데이터를 보존하고 손상 DB를 자동 삭제하지 않는다. 기본 DB는 `dearby-notice-cache-v1.db`다.

NoticeSession의 준비 작업은 IO dispatcher에서 수행한다. 취소와 요청 generation을 확인해 늦게 끝난 이전 작업의 화면 게시를 막는다. transaction 완료 후 UI session에 snapshot을 반영하며 ViewModel 조회는 메모리만 읽는다. loading/failure/retry를 제공한다. 실제 API·TTL·동기화는 미구현이다.

## 지도·캘린더와 다음 작업

지도 Intent와 캘린더 ACTION_INSERT를 사용한다. DESCRIPTION은 검증된 원본 URL만 담고, 원본이 없으면 빈 문자열이다. 별도 가짜 URL extra를 만들지 않는다. 신청/활동 기간과 온라인 또는 단계에 맞는 장소를 유지한다. 실제 Calendar Save는 수행하지 않는다.

#2는 Material3 관례로 외형을 개선한다. iOS의 기능·책임과 일관성을 유지하지만 외형 복제를 강제하지 않는다.

[검증 결과·명령·기기 주의사항](verification-and-local-devices.md) / [앱 실행 안내](../../apps/android/README.md)
