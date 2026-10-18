-- 2026-10-18 사용자 결정: 활동의 대상자를 중첩 가능한 JSON(eligibility)으로 규격화한다(native-v1.md "대상자 조건").
-- 원문 audience는 그대로 두고, 검색용으로 조건을 activity_eligibility_terms에 펼쳐 둔다(그룹 구조는 무시하고 조건만).
-- 기존 qualification jsonb(V6, 운영자용 자격요건)는 그대로 둔다.
alter table activities add column eligibility jsonb check (eligibility is null or jsonb_typeof(eligibility) = 'object');

create table activity_eligibility_terms
(
    id          bigint generated always as identity primary key,
    activity_id bigint       not null references activities (id) on delete cascade,
    field_en    varchar(40)  not null, -- 영문 어휘(camelCase 값, 열 이름만 snake_case). age·birthYear·educationStatus …
    from_value  numeric,               -- 숫자 범위 시작(포함). 없으면 열림
    to_value    numeric,               -- 숫자 범위 끝(포함). 없으면 열림
    value_en    varchar(200)           -- 집합 값(영문). 집합 조건은 값마다 한 행
);
create index activity_eligibility_terms_activity_idx on activity_eligibility_terms (activity_id);
create index activity_eligibility_terms_value_idx on activity_eligibility_terms (field_en, value_en);
create index activity_eligibility_terms_range_idx on activity_eligibility_terms (field_en, from_value, to_value);
