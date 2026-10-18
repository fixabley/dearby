-- 2026-10-18 이전 기수 정보 자동완성(shared/contracts/admin-v1.md "이전 기수 정보 자동완성")

-- 수집한 활동에 비어 있던 필드를 같은 프로그램의 이전 기수 활동에서 가져왔을 때 그 필드 이름 배열. 예 ["eligibility","audience"].
-- 운영자가 그 필드를 편집·비우면 목록에서 빠지고, 수집기는 목록에 있는(가져온) 값만 다시 가져온다
alter table activities add column inherited_fields jsonb not null default '[]'::jsonb;
