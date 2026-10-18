-- 2026-10-18 명함 웹 버전(native-v1.md "웹 버전"): 모바일 본문(template·introduction) 외에 선택 웹 본문을 둔다.
-- 둘은 함께 있거나 함께 null이다. 기존 명함은 null로 남아 웹에서도 모바일 본문을 보인다.
alter table business_cards
    add column web_template     varchar,
    add column web_introduction varchar;
