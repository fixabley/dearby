-- 2026-10-19 사용자 결정(admin-v1.md "브라우저 렌더링"): 자바스크립트로 내용을 그리는 사이트(예 woowacon.com)는
-- 서버 HTML에 글자가 없어 수집이 비었다. 이 플래그가 켜진 프로그램만 헤드리스 Chromium으로 페이지를 실행한 뒤 본문을 읽는다
alter table program_collection_settings add column render_pages boolean not null default false;
