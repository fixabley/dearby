# 공통 데이터 역할 — 규격·원문·추론 규칙

> **최신 상태 — 2026-09-14:** 구조 개선 PR #3·#4·#5·#6은 main에 병합했고 #1을 닫았다. 하위 worktree/담당 터미널은 정리 완료했다. #2 디자인 구현은 아직 시작하지 않았다. 이전 세션 ID·draft/retained 기록은 이력이다. [통합·정리 및 다음 작업 인계](coordinator-architecture-merged-and-worktrees-cleaned.md)를 먼저 읽는다.

## 정본

- shared/contracts/activities/sample.json: 수동 검토 샘플, v1.0.0, reviewed_sample, 2026-09-14 스냅샷.
- schema.json: JSON Schema draft 2020-12.
- target-rules.json: 사용자 승인 사례 3개와 관심 대상 규칙.
- docs/product/activity-data-v1.md: 필드·원문 검토.
- docs/product/interest-target-rules.md: 대상·분류 판단.
- docs/product/iteration-01.md: 구현 동작·검증·남은 사항.

## 관계를 섞지 않는다

조직 ID와 공고 ID는 별개다. parentOrganizationId는 실제 조직 계층(미확인/최상위는 null), categoryPath는 활동 분류, contexts는 이번 행사의 학교 등 역할과 근거, edition은 개별 회차다. organizationLinks의 publisher/operator/subject/contact 역할을 부모 관계로 자동 변환하지 않는다. favoriteOrganizationId는 사용자가 지속적으로 관심을 가질 대상이다. 조직 저장은 부모·자식 자동 저장을 뜻하지 않는다. 기존 운영부서 즐겨찾기를 기업으로 강제 이관하지 않는다.

## 승인 사례

1. 영남권 AI·정보보호영재교육원 → 대회 분류 → 영남권 사이버 공격 방어 대회 → 제2회 공고. 저장 대상 yeongnam-cyber-defense, 부모 yeongnam-ai-security. '대회'는 분류이며 별도 조직 노드가 아니다. 사용자 편집 분류를 원문상의 단독 주관 사실로 단정하지 않는다.
2. 한국농어촌공사 → 채용 → 채용행사 → 충북대학교 행사 맥락 → 2026 신입사원 채용설명회. krc 저장, 대학일자리센터 운영 역할 유지.
3. DB손해보험 → 채용 → 채용행사 → 충북대학교 행사 맥락 → 2026 신입사원 채용상담회. db-insurance 저장. 행사 학교로 지원 자격을 추정하지 않는다.

## 검토한 원문 5건

- https://software.cbnu.ac.kr/sub0401/1153966 : 영어 대체인정 학사행정. 피드 제외. 9/16 날짜 단위 마감, S4-1 217은 서류 제출 사무실이며 행사 장소가 아니다. HWP 세부 기준 미확인.
- https://software.cbnu.ac.kr/sub0401/1154064 : 제2회 영남권 사이버 공격 방어 대회. 포스터 확인. 대학/대학원 또는 중고등 부문, 팀 최대 4명, 적어도 한 명이 영남 5개 지역 재학 또는 거주. 10/7 24시를 10/8 00:00+09:00으로 정규화. 온라인 예선 10/14, 본선진출 발표 10/22, 오프라인 본선 11/4 정확 장소 미확인.
- https://cieat.cbnu.ac.kr/ncrProgramAppl/a/m/getProgramDetail.do?npiKeyId=NCR000000007344 : 한국농어촌공사 설명회 9/15 14–16, 신청 마감 13시, 도서관 2관 5층 세미나실. 전체 학과/1–6학년, 대학 소속 자격 미확인. 표 3시간과 본문 2시간 충돌 보존.
- https://cieat.cbnu.ac.kr/ncrProgramAppl/a/m/getProgramDetail.do?npiKeyId=NCR000000007306 : DB손해보험 상담회 9/15 10–17, 신청 마감 16시, 도서관 1관 스터디룸 1·6. 전체 학과/1–6학년, 대학 소속 자격 미확인.
- https://cieat.cbnu.ac.kr/ncrProgramAppl/a/m/getProgramDetail.do?npiKeyId=NCR000000007382 : 러시아언어문화학과 동문 멘토링 9/21 18–21, N16-1 459. 신청 9/16 24시 → 9/17. 전체 학과 표기와 학과 대상 본문 충돌, 관심 대상 cbnu-russian은 잠정.

CIEAT 시작 주소 https://cieat.cbnu.ac.kr/clientMain/a/t/main.do . 공개 페이지와 공개 목록 요청을 수동 검토했다. 자동 크롤러·정기 수집은 없다. 외부 마일리지는 Dearby 크레딧이 아니다. 미확인 비용을 무료로 표시하지 않는다. 출처·충돌·불확실성을 보존한다.

## 스크립트와 검증

scripts/sync-activity-samples.py는 정본을 iOS Resources 및 Android assets에 바이트 그대로 복사한다. --check는 검사만 한다. 동기화는 양쪽 앱을 쓰므로 메인에서 조율한다.
scripts/organization-tree.mjs는 중복 ID·누락 참조·순환을 거절한다.
scripts/infer-activity-target.mjs는 이미 검토된 엔티티/역할 입력에 결정적 규칙을 적용한다. 원문 추출기가 아니고 사용자 즐겨찾기를 변경하지 않는다. 유일 subject·종류 일치·승인된 공고와 맥락의 정확 일치면 approved, 새 공고/변경 맥락이면 suggested, 후보 누락·복수·알 수 없는 ID·상충 규칙·선례 없음은 needs_review. 이 상태는 개인 자격 판정이나 최신 모집 상태를 뜻하지 않는다.
루트 npm test는 공통 테스트 13건(데이터 8, 추론 5), npm run samples:check / samples:infer가 있다. 이후 실행 결과는 별도로 기록한다.

## 2026-09-14 모델 단순화·지도 연결 최종 완료
공통 PR#6 https://github.com/fixabley/dearby/pull/6 head a157e42 (feat/activity-location-contract, root현재branch). 선택coordinates/coordinateEvidence 확장+공식건물좌표 3공고+공통JSONmetadata보존+양root리소스동기화+문서/15테스트.
iOS PR#5 72e48a57f73fd4fb67c095f2c1c349d1bd93a5ab 단순래퍼제거/장소모델 → e0b16b20634ef1fa9fe2e85b5bfe416778709e6d 좌표/MapURL/Appdestination/error표현 → 910360901513013b3594d9d9c32eadf667448cda 온라인유효좌표UI제외수정. ActivityNotice audience/eligibility/application:String benefits/qualityIssues[String], location typed summary/mode/status/venues. ActivityCoordinates finite/range checked; invalidoptional decode dropscoords retainsvenue. App puremapURL+launcher+NoticeDetailDestination errorsheetsafe. Native button each ownUIfile. Apple Maps 실제pin/label 확인 (건물대표점) 및 DBfavorites/nav복귀 PASS. Failure launcher주입테스트 PASS, 미설치OS거절실기기UI 미검증. 마지막온라인guard변경 purefilter standalone/FSD28/buildPASS, runtime 재실행불필요.
Android PR#4 b90adacaea9ee4a94f3e27a48c38da45be038613. String필드기존유지, typedlocation, coordsparse, onlinebuttonsuppress, App geo ACTION_VIEW unpinned; missinghandler/security nativeToast. FSD24+selftest11 JVM7/계측20 Debug/Linterror0advisory11 PASS. 실제Mapsrending/chooser미검증, handlerexists 확인+Intentcapture/error tests. 전용5556종료, user5554유지.
Main: rootnpm15/samplecheckPASS; childFSD28/24+fixtures직접PASS; AndroidXML JVM7계측20failure0확인; canonical3fileshash407b0c5e...일치. merge-tree 공통branch와각앱branch 무충돌 (실제merge안함). PRhead확인.
Run run_5feb189ab803 originaliOS task_212033abaf14/ctx_415579c0d7ba 완료→즉시수정task_2ccf48a09197/ctx_8cf87f51a1ee 재사용, 최종succeededretain. Android task_6842c653abbe/ctx_7c78d6143a66 succeededretain. 모든completionack/reclaimable0. API/기존untracked보존. 역할문서최신, noforce/merge. 다음 새요청은 새dispatch로기존세션재사용.


## 2026-09-14 신청·활동 캘린더 완료 (최신)
사용자 요청: 신청기간은 신청 URL, 활동기간은 해당 단계의 장소와 연결하고 온라인이면 접속 URL 또는 온라인 표시. 두 앱에 네이티브 편집기 추가를 구현했으며 사용자가 직접 저장/취소한다. 직접 이벤트 저장·권한 요청·초대·알림은 추가하지 않았다.
공통 PR #6 head79f5a82c6c62776fab718d5bd86f0cad09034e72, iOS PR #5 head9a8cd0272d1dfc657a127d90a2dae719bc701f94, Android PR #4 head6e5ad55420fe53cef305b81ad3db105ad470d43c. 모두 원격 확인 및 draft/미병합. 각 앱 신청/활동 두 기능 커밋으로 테스트·문서를 함께 묶었다. main에 병합하지 않았다.
공통 JSON SHA256 c649b0a1d898497adf9bd4e2363c5753a1eecf996a7467e604dadaaee4a9e95f가 root/iOS/Android 리소스에 일치한다. application.url, phase.onlineUrl/endsOn/timezone optional 규격. CIEAT 공개 신청 페이지 3건 확인, 이메일 신청 공고는 URL null. 종료 날짜는 inclusive, OS 종일 종료는 exclusive, 24:00은 다음날 00:00. 날짜 누락/모순/역전은 추측하지 않는다. 활동 장소는 phase 정확 일치, 온라인은 오프라인 장소를 섞지 않는다.
검증: root 공통17 테스트 및 sample sync 검사 통과. iOS 39파일 FSD/독립 날짜·상태·지도 검사와 Simulator 빌드 통과. 전용 Simulator 실제 신청/활동 편집기 기간·URL·장소 및 취소 복귀 확인, root가 calendar-regression의 두 editor PNG 확인. 종일/온라인 OS 화면·계정 없는 상황·실기기는 미검증. Android 35파일 FSD/self-test13, JVM17·계측28 실패0, Debug/Lint 오류0(권고12). 전용5556에 캘린더 handler가 없어 실제 외부 편집기 open/cancel 미검증; Intent 전달/오류/상세 유지 검증으로 한계를 기록. 실제 이벤트 저장 없음, 사용자 기기 보존.
Orca run_ee6bffef760e: iOS task_89dc548a4542/ctx_6e2ea9205c28, Android task_ea27305ed676/ctx_b77e3de3a94c 모두 succeeded/retained. 완료 delivery_11ad3bc86932 전부 검토·ack, reclaimable0 확인. 기존 담당 세션 유지, API 변경 없음. 기존 untracked/미커밋 변경 보존. 다음은 PR 검토 또는 후속 요청이며 이미 완료한 구현을 다시 시작하지 않는다.


## 2026-09-14 ActivityDetail 및 조직 ID 캐시 진행 (최신)
사용자는 상세에 원문 근거·관련 조직 경로를 포함하는 ActivityDetail을 승인했고, 조직은 말단 ID만 저장하고 cache-aside처럼 조회하도록 명시했다. 말단은 참조 경로의 마지막 선택 노드이며 전역 트리의 leaf 강제 아님. 공고 ID 관계 보존, 저장 원본 조직(id/name/parentId) 별도 인메모리, 성공 조회만 lazy cache, source snapshot 교체 시 무효화. 관련 경로는 detail의 transient projection, 중복 저장 아님. App 수명 소유/상세 UI는 detail+callback만 받음. sources/evidence 실제 디코딩 보존, 기존 UI/map/calendar/favorites 유지. shared JSON 자체 변경 불필요, 공통 product 문서에 의미 계약 기록 중.
run_b6299acf926a 기존 terminal 재사용: iOS task_6ab832b92fc2/ctx_61bc02e98694, Android task_1d72f23112c7/ctx_0710ea32dce0 모두 ready/input accepted/turn observed. 각 PR5/4 기능별 커밋+테스트+문서 followup, root는 PR6 공통 문서 담당. API 제외. 아직 구현/검증/완료 settlement 대기. 캐시 조회 횟수·missing/cycle·snapshot rename/parent invalidation·근거/detail 독립성 핵심 검토.


## 2026-09-14 ActivityDetail 및 조직 캐시 최종 완료
사용자 승인 상세 모델을 두 앱에 적용했다. 화면은 ActivityDetail과 콜백만 받으며 카탈로그/원시 공고/저장소를 조회하지 않는다. 제목/aiDescription(기존 검토 summary라는 provenance)/대상/조건/신청방법·날짜/단계별 일정·장소/혜택·확인사항/원문 URL/출처/fieldPath를 가진 근거가 상세에 있다. 조직은 원본 공고에서 선택 ID만 저장하고 원본 조직(id/name/parentId)은 별도 인메모리 source에 저장한다. 관련 경로/기관이름은 저장하지 않는 조회 결과다. 말단은 선택 경로 끝이며 전역 leaf 강제 아님.
OrganizationRepository는 독립 빈 cache → source 조회 → 성공만 cache, missing 미캐시 및 부모 cycle-safe. App이 snapshot lifetime을 소유해 상세 재진입/재구성에도 공유한다. replaceSnapshot/source는 모든 캐시를 무효화한다. 영구저장/TTL/네트워크/live refresh UI는 추가 안 함. 현재 UI 갱신 기능은 없고 미래 refresh는 App 상태 연결 필요. 기존 favorites/map/calendar/화면 유지, canonical c649b0a1d898497adf9bd4e2363c5753a1eecf996a7467e604dadaaee4a9e95f 그대로 root/양app 확인.
공통 docs/product/activity-data-v1.md 문서 commit c71cac33f586ee53313efb742bf10bf4e970fc21 PR6. Android PR4: 1034366 조직 캐시 + b8838e2f2bd2165cffb64c858e0e28ce36b5c866 상세 조회. iOS PR5: 157d2024b7f790ae03e4eb0ab1511913302efa16 조직 캐시 + f6d28b7b878f33f57d8baa20d32f2515e956b5bf 상세 조회 + 2a2b05ff2a02efdde9683d08145ba84a141571fc 기관 링크 이름 보완. 모두 원격확인/draft/미병합; user기존미커밋/untracked보존.
검증: Android 구조41/self-test16, JVM24·전용5556계측30 실패0, Debug/Lint 오류0 권고12. main 실제 XML/로그 확인, 구조 직접통과. iOS source/cache 호출수·공유 경로/두 상세재진입·missing/cycle/rename-reparent invalidation·원문 근거/unknownsource·해결/미해결 기관이름 및 old/new JSON 상세/캘린더 검사, 기존 상태·지도 검사 및 Simulator build 통과. 최종 링크보완 빌드 11:24:30Z, main FSD45+fixtures 직접통과. 외부 Maps/캘린더 런타임은 이번 재실행하지 않음, 이전 검증을 새 결과로 주장하지 않음.
Orca run_b6299acf926a: Android task_1d72f23112c7/ctx_0710ea32dce0 succeeded/retained, completion delivery_67e85faf78c6 ack. iOS 최초 task_6ab832b92fc2/ctx_61bc02e98694 succeeded 뒤 누락된 링크 이름 보완을 즉시 같은 terminal 새 task_de204010b115/ctx_83dc09fa205d로 재사용하고 이전 delivery_a08d8a073ed0 ack. 보완 실제코드/테스트검토 후 succeeded/retain 및 delivery_1f74439b67d1 ack. reclaimable0 확인, 활성작업 없음. 기존 앱 세션 유지/API미변경. 다음은 PR 검토/새 사용자 요청이며 완료된 작업을 재실행하지 않는다.
