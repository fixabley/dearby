-- 2026-10-26 사용자 결정(native-v1.md "소개 링크·소개 글 폐지"): 이름 옆 소개 링크(노션·포트폴리오 등 https 주소 하나, 선택).
-- 명함은 발행 시점 스냅샷에 복사한다. 소개 글(profiles.introduction, business_cards.introduction) 열은 지우지 않고 더는 쓰지 않는다(운영 데이터 보존, 폐지 예정)
alter table profiles add column intro_link varchar(500);
alter table business_cards add column intro_link varchar(500);
