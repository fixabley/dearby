-- 2026-10-14 사용자 결정: 공개 활동(Activity 도메인)은 원래 필드에 공식 안내 링크(official_url)와 이미지(image_url)만 둔다.
-- 카탈로그에서 가져온 나머지(모집 상태·참여 방식·확인 정보·상세 문구)는 운영자용으로 activity_curations에 옮긴다.
-- 공개 API는 이 테이블을 읽지 않는다. 같은 DB를 쓰는 운영자(admin) 모듈이 소유할 데이터다. activity_roles도 운영자용이다

create table activity_curations
(
    activity_id        bigint primary key references activities (id) on delete cascade,
    participation_type varchar(20)   not null check (participation_type in ('REGISTRATION', 'SELECTION')), -- 참가등록형·선발형
    recruitment_status varchar(20)   not null check (recruitment_status in ('OPEN', 'SCHEDULED', 'CLOSED', 'UNKNOWN')), -- 운영자가 고른 모집 상태
    location           varchar(300),           -- 장소 문구(좌표 없음)
    cost               varchar(300),
    audience           varchar(1000),
    application_url    varchar(2000),          -- 신청 링크
    source_checked_at  timestamp with time zone, -- 공식 안내를 마지막으로 확인한 시각
    valid_until        timestamp with time zone, -- 확인 내용이 유효한 끝 시각
    freshness          varchar(20)   not null check (freshness in ('VERIFIED', 'STALE', 'UNAVAILABLE')), -- 확인 상태
    source_note        varchar       not null  -- 안내 문구
);

insert into activity_curations (activity_id, participation_type, recruitment_status, location, cost, audience, application_url,
                                source_checked_at, valid_until, freshness, source_note)
select id, participation_type, recruitment_status, location, cost, audience, application_url,
       source_checked_at, valid_until, freshness, source_note
from activities;

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
