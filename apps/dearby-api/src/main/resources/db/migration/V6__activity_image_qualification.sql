-- 2026-10-14 사용자 결정: 공개 활동(Activity 도메인)은 원래 필드에 공식 안내 링크(official_url)와 이미지(image_url)만 둔다.
-- 나머지 카탈로그 열(모집 상태·참여 방식·확인 정보·상세 문구)과 activity_roles는 테이블을 나누지 않고 그대로 두며,
-- 운영자용 도메인(ActivityCuration)이 다룬다. 공개 API는 이 열을 내보내지 않는다
alter table activities add column image_url varchar(2000); -- 대표 이미지. 없으면 null
-- 자격요건. JSON 객체: 키는 조건 이름, 값은 문자열 배열. 예: {"나이": ["만 19세 이상"], "지역": ["서울", "경기"]}. 없으면 null
alter table activities add column qualification json;
