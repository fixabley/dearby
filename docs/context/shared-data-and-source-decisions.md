# 공통 데이터 — 유지할 의미와 정본

갱신: 2026-09-14. 메인이 shared/contracts·공통 추론·앱 리소스 동기화를 조율한다.

## 정본

- [공고 규격과 원문 검토](../product/activity-data-v1.md)
- [관심 대상 추론 규칙](../product/interest-target-rules.md)
- [첫 반복의 제품 동작](../product/iteration-01.md)
- [sample.json](../../shared/contracts/activities/sample.json), [schema.json](../../shared/contracts/activities/schema.json), [target-rules.json](../../shared/contracts/activities/target-rules.json)

현재 sample은 v1.0.0 / reviewed_sample / 2026-09-14 수동 검토 스냅샷이다. 학사행정 1건을 제외한 4건을 피드에 표시한다. 원문별 날짜·장소·조건·충돌의 상세는 제품 규격에 있으며, 현재 모집 정보로 재확인한 것은 아니다.

## 공고와 조직 관계

NoticeModel은 조직 ID·역할만 저장하며 OrganizationModel·이름·경로를 보유하거나 조회하지 않는다. 별도 조직 source/repository가 id·name·parentOrganizationId를 관리한다. ViewModel이 두 모델을 조회해 화면 State를 조립한다.

parentOrganizationId는 조직 계층, categoryPath는 활동 분류, contexts는 행사 학교 등의 맥락, edition은 회차다. organizationLinks의 publisher/operator/subject/contact를 부모 관계로 자동 변환하지 않는다. favoriteOrganizationId는 지속 관심 대상이며 부모·자식을 자동 저장하지 않는다. 선택한 말단 ID는 전역 트리의 leaf일 필요가 없다.

## 사용자 승인 사례

| 공고 | 저장 대상 | 구별할 맥락 |
| --- | --- | --- |
| 영남권 사이버 공격 방어 대회 제2회 | yeongnam-cyber-defense | 부모 yeongnam-ai-security, 대회는 활동 분류, 2회는 edition |
| 한국농어촌공사 채용설명회 | krc | 충북대학교는 행사 맥락, 대학일자리센터는 운영 역할 |
| DB손해보험 채용상담회 | db-insurance | 충북대학교는 행사 맥락이며 학교만으로 지원 자격을 추정하지 않음 |

멘토링의 cbnu-russian 관심 대상은 잠정이다. 사용자 지정 서비스 분류와 원문상의 주관 사실을 구별한다. 출처·충돌·첨부 미확인·비용 미확인을 보존하고 미확인 비용을 무료라고 표시하지 않는다. 외부 CIEAT 마일리지는 Dearby 크레딧이 아니다.

## 지도·캘린더·추론

장소 좌표는 완전한 위도·경도 쌍과 근거를 보존한다. 신청 접수 장소와 활동 장소, 온라인 예선과 오프라인 본선 장소를 섞지 않는다. 날짜 단위·KST·24:00 정규화 및 종료 불확실성을 유지한다.

캘린더 메모는 검증된 공고 원본 URL 하나만 담고 누락/잘못된 URL이면 빈 문자열이다. 신청 URL로 대체하지 않는다. iOS 별도 event.url과 Android DESCRIPTION의 플랫폼 차이는 유지한다.

infer-activity-target.mjs는 검토된 입력에 결정적 규칙을 적용하며 크롤러·원문 추출기가 아니다. approved/suggested/needs_review는 대상 매핑 상태이며 개인 자격이나 최신 모집 여부의 판정이 아니다.

루트 npm test와 npm run samples:check로 검사한다. samples:sync는 두 앱 파일을 실제 변경하므로 담당자와 조율한다. 마지막 검증은 [검증 문서](verification-and-local-devices.md)에 있다.
