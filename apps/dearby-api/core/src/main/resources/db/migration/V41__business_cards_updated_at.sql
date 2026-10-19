-- 2026-10-20 사용자 결정(native-v1.md "명함 고치기는 같은 명함을 수정"): PUT /v1/cards/:id가 같은 id의 스냅샷을 다시 만들므로
-- 고친 시각을 둔다. 기존 행은 만든 시각으로 채우고, 이후에는 코드가 항상 넣는다(created_at과 같은 방식)
alter table business_cards add column updated_at timestamp with time zone not null default now();
update business_cards set updated_at = created_at;
alter table business_cards alter column updated_at drop default;
