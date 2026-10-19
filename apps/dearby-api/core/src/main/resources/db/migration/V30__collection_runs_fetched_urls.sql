-- 2026-10-19 (shared/contracts/admin-v1.md "수집 설정" lastFetchedUrls): 이 실행에서 실제로 받아 본 페이지 주소(받기 순서, 실패 제외).
-- 어드민이 "지난 수집에서 본 주소"로 보여 주며, 시작 주소 토큰이 어떻게 펼쳐졌는지 확인하는 데 쓴다
alter table collection_runs add column fetched_urls varchar[] not null default '{}';
