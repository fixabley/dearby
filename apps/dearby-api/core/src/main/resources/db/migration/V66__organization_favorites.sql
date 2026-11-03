-- 2026-11-03 사용자 결정(native-v1.md "조직 화면·조직 팔로우"): 조직 팔로우. target_type에 ORGANIZATION(target_id = organizations.id, 논리키).
-- 'ORGANIZATION'은 12자라 열을 넓힌다. notify_level·seen_at은 프로그램 팔로우와 같은 뜻(새 모집 알림은 그 조직과 하위 조직의 프로그램 활동)
alter table user_favorites alter column target_type type varchar(20);
alter table user_favorites drop constraint user_favorites_target_type_check;
alter table user_favorites add constraint user_favorites_target_type_check check (target_type in ('ACTIVITY', 'PROGRAM', 'ORGANIZATION'));
