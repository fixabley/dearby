# Calendar 참고 상세 기간·장소 — 2026-09-15 KST

## 실제 참고와 디자인 해석

[Apple 공식 iPhone Guide](https://support.apple.com/en-lamr/guide/iphone/iph3d110f84/ios)를 열어 설명 및 [공식 이미지](https://help.apple.com/assets/69F8EBBDF3B89A4F6E0C704C/69F8EBC43862495245036393/en_US/343265f87c471a5f59200a3d76b75d74.png)를 직접 확인했다. 이미지는 일정 상세가 아니라 일간 보기로, 제목 아래 시간 범위를 보여준다. 설명은 제목, 장소/영상통화, 시작·종료를 분리해 입력하도록 안내한다. 이전 ../metadata/application-editor.png와 schedule-editor.png는 EventKit **입력 구조**의 참고다. 실제 Calendar 일정 상세 화면을 관찰하거나 replica를 만들었다고 주장하지 않는다. 새 일정 생성/실제 저장 없이 이 자료에 근거해 Dearby 상세의 날짜·시간·장소 위계를 설계했다.

## 변경

- 제목/headline 오른쪽에 해당 calendar action, 아래에 날짜/body.medium와 시간/subheadline.secondary, 시간대/footnote를 분리했다. 같은날은 날짜 한 번·시간 범위, 여러날은 시작/종료 블록이다.
- venue 이름과 명시적인 층/호실/주소를 분리하고 map action을 장소에 붙였다. AX5에서 긴 이름이 좁아지는 것을 발견해 이 크기에서는 지도 버튼을 장소 바로 아래로 배치했다.
- 반복 원문 신청/장소 summary는 기본 화면에서 native DisclosureGroup으로 접는다. 펼치면 원문 전체를 보존하며 새로운 조건을 추론하지 않는다. 온라인은 온라인 표기와 안전한 HTTP(S) Link를 제공한다.
- 카드, 저장 model/cache/favorites, App calendar/export/map mapper와 콜백 순서 및 notes 계약은 변경하지 않았다. Shared UI는 값과 action slot, 상위 State/VM은 원본 projection을 담당한다.

## 이번에 실행한 검증

- `bash apps/ios/tests/run_detail_presentations.sh`: 독립 날짜/장소/URL 변환 및 FSD73파일/negative fixtures PASS (`tests.txt`). 같은날·여러날·연도경계·혼합 정밀도·초·시간/종료 미확인, on/at 충돌·invalid·역전·시간대 fallback, 여러 호실·건물코드·불확실 괄호·주소 중복·안전URL을 검사한다.
- 기존 NoticeViewModelTests를 현재 sources로 다시 컴파일해 앱 fixture 한 개에 실행 PASS (`viewmodel-tests.txt`): 카드/상세 source 읽기와 같은 favorite owner 및 재구성 계약. 전체 standalone/SwiftData/OS mapper suite는 이번에 재실행하지 않았다.
- Xcode26.6(17F113)/iOS26.5, 최종 build/run 2026-09-14 15:28:00Z 성공 (`build.txt`). SDK/라이브러리 추가 없음. 로그 행 끝 공백만 정규화했다.
- 기존 전용4156AE92-5308-4050-93DC-C9E241918DCB/window3055만 사용했다. 실제 상세 scroll, 원문 disclosure 펼침/접힘, 날짜/시간/층·호실, AX labels와 map/calendar 위치를 확인했다. 대표 사진 4장만 추가: `detail-light.png`/`detail-dark.png`는 KRC 신청과 행사 날짜·시간 위계(스크롤 위치에 따라 신청 일부는 화면 밖), `schedule-ax5-dark.png`는 AX5 날짜·시간·헤더 action, `place-ax5-light.png`는 전체 너비 장소와 5층·지도 action이다. AX5 사진의 상단 잘림은 해당 scroll 위치다.
- 마지막으로 날짜·시간 묶음 앞에 작은 calendar 정보 아이콘을 복원했다. 최종 build/run 후 `detail-light.png` 한 장을 갱신했으며 나머지 세 장은 이 장식 아이콘 추가 직전의 동일한 날짜·장소 레이아웃 검증이다. 헤더의 calendar 추가 액션과 별개이며 접근성에서 장식 아이콘은 숨긴다.
- before는 ../metadata/detail-light.png 및 detail-dark.png이며 after는 이 디렉터리다. A434/C38E는 조작하지 않았다. 종료 설정은 light/large, 상세 sheet를 유지했다.

## 한계

AX snapshot이 배경/이전 화면을 반환하는 문제는 지속되어 실제 캡처를 판정 기준으로 썼다. 모든 공고별 screenshot/VoiceOver 음성/실제 calendar save/Canvas 렌더/지도·편집기 재실행은 이번 범위에서 하지 않았다. 다단계 title/date/place/callback 순서는 기존 연결을 유지한 소스 검토와 관련 테스트에 근거하며 모든 단계 OS editor를 직접 열었다고 주장하지 않는다. 일부 timestamp offset/정밀도가 표준 round-trip과 맞지 않으면 축약 대신 원문+확인 메시지로 표시하는 보수적 fallback을 사용한다. 일정이나 장소에 원문만 가진 부가 설명은 disclosure를 열어 확인한다.
