-- 2026-10-18 명함 웹 버전 삭제(앱 안 꾸미기 제거 결정). V20에서 더한 두 열을 지운다. 기존 명함의 template·introduction은 그대로다.
alter table business_cards
    drop column web_template,
    drop column web_introduction;
