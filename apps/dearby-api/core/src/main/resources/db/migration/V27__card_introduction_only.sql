-- 2026-10-18 사용자 결정(native-v1.md "템플릿"): 명함 본문은 자기소개만. 이름·직무·연락처는 카드 면이 따로 그리므로
-- 앱 내장 기본 템플릿(# 이름 / 직무 / 소개 / - 라벨: 연락처)으로 발행된 기존 명함의 본문에서 그 중복 줄을 떼어 낸다.
-- 기본 템플릿 모양이 아닌 명함(앱 안 꾸미기 시절 사용자가 고친 것)은 건드리지 않는다.
update business_cards
set template     = '{{profile.introduction}}',
    introduction = btrim(
        regexp_replace(
            regexp_replace(introduction, '^#[^\n]*\n[^\n]*\n', ''),      -- "# 이름" 줄과 "직무" 줄
            '(\n- [^\n]*: [^\n]*)+\s*$', ''),                            -- 끝의 "- 라벨: 값" 연락처 줄
        E' \n\r\t')
where template like '# {{profile.name}}' || E'\n{{profile.job}}\n\n{{profile.introduction}}' || '%';
