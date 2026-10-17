-- 2026-10-17 사용자 결정: 직함(자유 문자열 하나) → 직무(조직처럼 계층을 가진 활동 정보). 프로필은 직무를 검색해 여러 개 태그로 두고,
-- 검색에 없으면 직접 적는다(이력과 같이 관리자 승인 전에는 명함에 넣지 않는다). 명함의 job은 발행 시점 승인된 직무 이름을 " · "로 이은 문자열 그대로다
create table jobs
(
    id         bigint generated always as identity primary key,
    name       varchar(100)             not null,
    name_en    varchar(100),
    parent_id  bigint references jobs (id),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);
-- 같은 상위 아래에서 띄어쓰기만 다른 이름도 중복(V12 조직 규칙)
create unique index jobs_parent_name_uq on jobs (coalesce(parent_id, 0), regexp_replace(name, '\s', '', 'g'));
create unique index jobs_parent_name_en_uq on jobs (coalesce(parent_id, 0), regexp_replace(name_en, '\s', '', 'g')) where name_en is not null;
create index jobs_parent_id_idx on jobs (parent_id);

-- 프로필의 직무 태그. id는 클라이언트가 만든 UUID(저장해도 승인 상태를 지킨다). job_id가 있으면 그것을, 없으면 직접 입력한 job_name을 쓴다
create table profile_jobs
(
    id              uuid primary key,
    user_id         uuid                     not null references users (id) on delete cascade,
    job_id          bigint, -- 논리키 jobs(쓰는 동안은 지울 수 없다)
    job_name        varchar(100),
    position        integer                  not null,
    approval_status varchar(20)              not null check (approval_status in ('APPROVED', 'PENDING', 'REJECTED')),
    approval_reason varchar(500),
    created_at      timestamp with time zone not null,
    updated_at      timestamp with time zone not null
);
create index profile_jobs_user_id_idx on profile_jobs (user_id);
create index profile_jobs_approval_idx on profile_jobs (approval_status);

-- 기존 직함 문자열은 승인 규칙 이전에 받은 값이라 승인된 직접 입력 직무로 옮긴다(관리자가 나중에 계층의 직무로 바꿀 수 있다). 운영에는 2026-10-17 기준 프로필이 없었다
insert into profile_jobs (id, user_id, job_id, job_name, position, approval_status, created_at, updated_at)
select gen_random_uuid(), user_id, null, job, 0, 'APPROVED', now(), now() from profiles where job <> '';
alter table profiles drop column job;

-- 시작 분류(어드민에서 고친다): 상위 11개, 하위 56개
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('개발', 'Engineering', null, now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('백엔드', 'Backend', (select id from jobs where parent_id is null and name = '개발'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('프론트엔드', 'Frontend', (select id from jobs where parent_id is null and name = '개발'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('iOS', 'iOS', (select id from jobs where parent_id is null and name = '개발'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('Android', 'Android', (select id from jobs where parent_id is null and name = '개발'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('풀스택', 'Full-stack', (select id from jobs where parent_id is null and name = '개발'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('데이터 엔지니어링', 'Data Engineering', (select id from jobs where parent_id is null and name = '개발'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('머신러닝·AI', 'Machine Learning / AI', (select id from jobs where parent_id is null and name = '개발'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('데브옵스·인프라', 'DevOps / Infrastructure', (select id from jobs where parent_id is null and name = '개발'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('QA·테스트', 'QA / Test', (select id from jobs where parent_id is null and name = '개발'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('보안', 'Security', (select id from jobs where parent_id is null and name = '개발'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('임베디드', 'Embedded', (select id from jobs where parent_id is null and name = '개발'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('게임 개발', 'Game Development', (select id from jobs where parent_id is null and name = '개발'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('디자인', 'Design', null, now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('프로덕트 디자인', 'Product Design', (select id from jobs where parent_id is null and name = '디자인'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('UX 리서치', 'UX Research', (select id from jobs where parent_id is null and name = '디자인'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('브랜드·그래픽 디자인', 'Brand / Graphic Design', (select id from jobs where parent_id is null and name = '디자인'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('모션·영상 디자인', 'Motion / Video Design', (select id from jobs where parent_id is null and name = '디자인'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('일러스트', 'Illustration', (select id from jobs where parent_id is null and name = '디자인'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('기획·PM', 'Product', null, now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('서비스 기획', 'Service Planning', (select id from jobs where parent_id is null and name = '기획·PM'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('프로덕트 매니저', 'Product Manager', (select id from jobs where parent_id is null and name = '기획·PM'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('프로젝트 매니저', 'Project Manager', (select id from jobs where parent_id is null and name = '기획·PM'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('사업 기획', 'Business Planning', (select id from jobs where parent_id is null and name = '기획·PM'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('전략', 'Strategy', (select id from jobs where parent_id is null and name = '기획·PM'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('데이터', 'Data', null, now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('데이터 분석', 'Data Analysis', (select id from jobs where parent_id is null and name = '데이터'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('데이터 사이언스', 'Data Science', (select id from jobs where parent_id is null and name = '데이터'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('BI', 'Business Intelligence', (select id from jobs where parent_id is null and name = '데이터'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('마케팅', 'Marketing', null, now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('퍼포먼스 마케팅', 'Performance Marketing', (select id from jobs where parent_id is null and name = '마케팅'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('콘텐츠 마케팅', 'Content Marketing', (select id from jobs where parent_id is null and name = '마케팅'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('브랜드 마케팅', 'Brand Marketing', (select id from jobs where parent_id is null and name = '마케팅'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('그로스', 'Growth', (select id from jobs where parent_id is null and name = '마케팅'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('PR·홍보', 'PR', (select id from jobs where parent_id is null and name = '마케팅'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('영업·사업개발', 'Sales & BD', null, now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('영업', 'Sales', (select id from jobs where parent_id is null and name = '영업·사업개발'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('사업개발', 'Business Development', (select id from jobs where parent_id is null and name = '영업·사업개발'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('파트너십', 'Partnerships', (select id from jobs where parent_id is null and name = '영업·사업개발'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('고객 성공', 'Customer Success', (select id from jobs where parent_id is null and name = '영업·사업개발'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('운영', 'Operations', null, now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('서비스 운영', 'Service Operations', (select id from jobs where parent_id is null and name = '운영'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('커뮤니티 운영', 'Community Operations', (select id from jobs where parent_id is null and name = '운영'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('행사 운영', 'Event Operations', (select id from jobs where parent_id is null and name = '운영'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('고객 지원', 'Customer Support', (select id from jobs where parent_id is null and name = '운영'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('인사·채용', 'HR / Recruiting', (select id from jobs where parent_id is null and name = '운영'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('재무·회계', 'Finance / Accounting', (select id from jobs where parent_id is null and name = '운영'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('법무', 'Legal', (select id from jobs where parent_id is null and name = '운영'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('연구·교육', 'Research & Education', null, now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('연구원', 'Researcher', (select id from jobs where parent_id is null and name = '연구·교육'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('대학원생', 'Graduate Student', (select id from jobs where parent_id is null and name = '연구·교육'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('교수·강사', 'Professor / Lecturer', (select id from jobs where parent_id is null and name = '연구·교육'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('교육 기획', 'Education Planning', (select id from jobs where parent_id is null and name = '연구·교육'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('학생', 'Student', null, now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('대학생', 'Undergraduate', (select id from jobs where parent_id is null and name = '학생'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('고등학생', 'High School Student', (select id from jobs where parent_id is null and name = '학생'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('취업 준비', 'Job Seeker', (select id from jobs where parent_id is null and name = '학생'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('부트캠프 수강', 'Bootcamp Student', (select id from jobs where parent_id is null and name = '학생'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('창업·경영', 'Founder & Management', null, now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('창업자', 'Founder', (select id from jobs where parent_id is null and name = '창업·경영'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('경영진', 'Executive', (select id from jobs where parent_id is null and name = '창업·경영'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('투자·VC', 'Investor / VC', (select id from jobs where parent_id is null and name = '창업·경영'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('콘텐츠·미디어', 'Content & Media', null, now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('작가·에디터', 'Writer / Editor', (select id from jobs where parent_id is null and name = '콘텐츠·미디어'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('영상 제작', 'Video Production', (select id from jobs where parent_id is null and name = '콘텐츠·미디어'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('크리에이터', 'Creator', (select id from jobs where parent_id is null and name = '콘텐츠·미디어'), now(), now());
insert into jobs (name, name_en, parent_id, created_at, updated_at) values ('기자', 'Journalist', (select id from jobs where parent_id is null and name = '콘텐츠·미디어'), now(), now());
