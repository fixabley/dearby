-- 2026-10-15 사용자 결정: 다국어 1단계. 조직·프로그램·활동에 영문 이름, 활동에 영문 설명을 둔다(선택).
-- 기존 name·description은 한글. 공개 API는 요청 언어가 영어면 영문을, 비어 있으면 한글을 준다
alter table organizations add column name_en varchar(200);
alter table programs add column name_en varchar(200);
alter table activities add column name_en varchar(200);
alter table activities add column description_en varchar;

-- 영문 이름도 같은 계위에서 띄어쓰기만 다르면 중복(V12와 같은 규칙, 대소문자 구분). 비어 있는 것은 제외
create unique index organizations_parent_name_en_uq
    on organizations (coalesce(parent_organization_id, 0), regexp_replace(name_en, '\s', '', 'g'))
    where name_en is not null;
create unique index programs_organization_name_en_uq
    on programs (organization_id, regexp_replace(name_en, '\s', '', 'g'))
    where name_en is not null;

-- 활동 검색(q)이 영문 이름·설명도 찾는다(V7과 같은 pg_trgm)
create index activities_name_en_trgm_idx on activities using gin (name_en gin_trgm_ops);
create index activities_description_en_trgm_idx on activities using gin (description_en gin_trgm_ops);
