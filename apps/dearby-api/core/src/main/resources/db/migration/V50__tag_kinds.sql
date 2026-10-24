-- 2026-10-24 분야·성격 태그(shared/contracts/native-v1.md "분야·성격 태그와 아이콘"). 스키마만 바꾼다.
-- 분야 태그 추가·종류 지정·하위 분야 연결은 운영 데이터 단계에서 한다(태그 id를 여기에 적지 않는다)
alter table tags
    add column kind      varchar(10) not null default 'TRAIT' check (kind in ('FIELD', 'TRAIT')), -- FIELD 분야(무엇을 하나), TRAIT 성격(무엇을 얻나)
    add column parent_id bigint references tags (id) on delete set null; -- 하위 분야의 상위 분야(한 단계, FIELD끼리만. 규칙은 어드민이 검사)

-- 운영자가 적은 프로그램 태그 순서(작을수록 앞). 분야 태그 순서에서 연관도가 없는 태그를 줄 세운다
alter table program_tags
    add column position smallint not null default 0;

-- 수집기가 답한 분야 태그와 활동의 연관도(0~100). 성격 태그·운영자 지정·프로그램 태그 기본값은 null
alter table activity_tags
    add column relevance smallint check (relevance between 0 and 100);
