-- 2026-10-14 사용자 결정: 활동은 원래 도메인 필드에 공식 안내 링크(official_url)와 이미지(image_url)만 더한다.
-- 카탈로그에서 가져온 모집 상태·참여 방식·확인 정보·상세 문구 열과 역할 테이블을 지운다. 모집 기간은 application 일정이 계속 맡는다
drop table activity_roles;
alter table activities drop column participation_type;
alter table activities drop column recruitment_status;
alter table activities drop column location;
alter table activities drop column cost;
alter table activities drop column audience;
alter table activities drop column application_url;
alter table activities drop column source_checked_at;
alter table activities drop column valid_until;
alter table activities drop column freshness;
alter table activities drop column source_note;
alter table activities add column image_url varchar(2000); -- 대표 이미지. 없으면 null
