-- 2026-10-24 추천 설정 퍼널·지원 자격 정보·추천(shared/contracts/native-v1.md "추천 설정 퍼널·지원 자격 정보·추천").

-- 지원 자격 정보: 별도 동의(consent_version) 뒤에만 저장한다. 명함·공개 프로필·운영자 화면·로그 어디에도 내보내지 않는다. 지우기·계정 삭제와 함께 지워진다
create table profile_eligibility
(
    user_id          uuid primary key references users (id) on delete cascade,
    education_status varchar(20),  -- 대상자 조건 어휘 영문 값(enrolled·onLeave·graduating·graduated)
    employment       varchar(20),  -- student·unemployed·employed·founder
    school_year      smallint check (school_year between 1 and 5), -- 5는 5학년 이상
    birth_year       smallint check (birth_year between 1900 and 2100), -- 나이는 서버가 서울 기준 올해로 계산한다
    consent_version  varchar(20)              not null,
    consented_at     timestamp with time zone not null,
    updated_at       timestamp with time zone not null
);

-- 추천 설정(퍼널 답). 처음 저장한 시각이 completed_at이고 건너뛰기도 완료로 남는다. 지운 태그 id는 읽을 때 버린다
create table recommendation_preferences
(
    user_id       uuid primary key references users (id) on delete cascade,
    field_tag_ids bigint[]                 not null default '{}',
    categories    varchar(20)[]            not null default '{}',
    trait_tag_ids bigint[]                 not null default '{}',
    completed_at  timestamp with time zone,
    updated_at    timestamp with time zone not null
);

-- 지원 자격을 확인했는지: 조건이 없을 때 "제한 없음"과 "아직 모름"을 나눈다. 운영자가 정했거나 수집기가 원문 대상 구절을 읽었으면 true.
-- 조건이 구조화된 활동은 확인한 것으로 채운다(대상 문구 없는 컨퍼런스는 운영 데이터 단계에서 채운다)
alter table activities
    add column eligibility_checked boolean not null default false;
update activities set eligibility_checked = true where eligibility is not null;
