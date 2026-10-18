-- 2026-10-18 운영자 지정 필드(shared/contracts/admin-v1.md "이전 기수 정보 자동완성" 보완)

-- 어드민 PATCH로 바꾼 필드 이름 배열(name·nameEn·description·descriptionEn·eligibility). 수집기는 이 목록에 있는 필드만 덮어쓰지 않는다.
-- 활동 기간·모집 기간은 schedules.curated가 같은 역할을 한다
alter table activities add column curated_fields jsonb not null default '[]'::jsonb;
