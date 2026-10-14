-- 활동 검색(q)을 글자 세 개 단위 유사도로 넓힌다(pg_trgm). 오타("워크샵"→"워크숍")·띄어쓰기 차이("개발환경")도 찾는다.
-- 조건은 JooqActivityRepository: 부분 일치(ilike) 또는 word_similarity(q, 열) >= 0.4. 두 인덱스가 둘 다 받친다
create extension if not exists pg_trgm;
create index activities_name_trgm_idx on activities using gin (name gin_trgm_ops);
create index activities_description_trgm_idx on activities using gin (description gin_trgm_ops);
