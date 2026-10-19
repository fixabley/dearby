-- 2026-10-19 밤 프로그램 로고(admin-v1.md "프로그램 로고", native-v1.md "프로그램 로고"): 발견 카드 로고 자리에 보일 이미지.
-- 인증자료처럼 DB에 둔다(프로그램 수가 적고 백업이 함께 된다). 프로그램을 지우면 함께 지워진다.
-- 공개 URL은 /v1/programs/{id}/logo?v={updated_at epoch 초}라 바꿀 때마다 캐시가 깨진다
create table program_logos
(
    program_id   bigint primary key references programs (id) on delete cascade,
    content_type varchar(100)             not null,
    data         bytea                    not null,
    updated_at   timestamp with time zone not null
);
