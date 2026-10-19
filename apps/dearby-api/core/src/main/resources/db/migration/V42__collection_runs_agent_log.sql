-- 2026-10-20 수집기 에이전트 루프화: API 엔진 실행의 도구 호출 기록(도구 이름·주소·결과), 멈춘 이유, 턴·페이지 수, 캐시를 나눈 사용량.
-- CLI 엔진(고정 파이프라인)과 이전 실행은 null이다
alter table collection_runs add column agent_log jsonb;
