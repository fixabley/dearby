-- 2026-10-19 태그(shared/contracts/native-v1.md "태그(얻을 수 있는 것)"): 참가자가 얻는 것을 나타내는 통제 어휘. 운영자가 사전을 관리하고
-- 프로그램·활동에 연결한다. 이름은 띄어쓰기를 뺀 소문자로 하나만 둔다(프로그램 이름과 같은 중복 규칙)
create table tags
(
    id         bigint generated always as identity primary key,
    name       varchar(50)              not null, -- 한글 이름
    name_en    varchar(50),                       -- 영문 이름(어드민이 직접 입력해 만든 태그는 비어 있을 수 있다)
    sort_order integer                  not null default 0,
    created_at timestamp with time zone not null default now()
);
create unique index tags_name_normalized_key on tags (lower(regexp_replace(name, '\s', '', 'g')));

create table program_tags
(
    program_id bigint not null references programs (id) on delete cascade,
    tag_id     bigint not null references tags (id) on delete cascade,
    primary key (program_id, tag_id)
);

create table activity_tags
(
    activity_id bigint not null references activities (id) on delete cascade,
    tag_id      bigint not null references tags (id) on delete cascade,
    primary key (activity_id, tag_id)
);
create index activity_tags_tag_id_idx on activity_tags (tag_id);

insert into tags (name, name_en, sort_order)
values ('창업', 'startup', 1),
       ('창의성', 'creativity', 2),
       ('개발지식', 'engineering', 3),
       ('프로젝트 개발', 'projectBuilding', 4),
       ('협업', 'collaboration', 5),
       ('멘토링', 'mentoring', 6),
       ('네트워킹', 'networking', 7),
       ('취업', 'career', 8),
       ('포트폴리오', 'portfolio', 9),
       ('발표', 'presentation', 10),
       ('글로벌', 'global', 11),
       ('장학·지원금', 'funding', 12),
       ('리더십', 'leadership', 13),
       ('문제해결', 'problemSolving', 14);
