-- 2026-10-26 프로필 사진(native-v1.md "프로필 사진"). 서버가 다시 인코딩한 정사각 JPEG(메타데이터 없음)만 둔다.
-- 인증자료처럼 DB(bytea)에 두고 프로필 버전 스냅샷에는 넣지 않는다. 계정 삭제와 함께 지워진다
create table profile_photos
(
    user_id    uuid primary key references users (id) on delete cascade,
    data       bytea                    not null,
    updated_at timestamp with time zone not null
);
