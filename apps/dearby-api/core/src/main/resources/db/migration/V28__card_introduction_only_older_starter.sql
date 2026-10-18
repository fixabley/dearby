-- V27 보완(2026-10-18): 운영 명함은 더 이른 기본 템플릿("{{profile.name}}\n{{profile.job}}\n{{profile.introduction}}\n- 라벨: {{contact:…}}",
-- '# ' 제목과 빈 줄 없음)으로 발행돼 V27의 모양 대조에 걸리지 않았다. 토큰만으로 된 기본 템플릿(제목·빈 줄 유무 무관)을 모두 같은 규칙으로 정리한다.
update business_cards
set template     = '{{profile.introduction}}',
    introduction = btrim(
        regexp_replace(
            regexp_replace(introduction, '^(# )?[^\n]*\n[^\n]*\n', ''),   -- "이름" 줄과 "직무" 줄
            '(\n- [^\n]*: [^\n]*)+\s*$', ''),                             -- 끝의 "- 라벨: 값" 연락처 줄
        E' \n\r\t')
where template ~ '^(# )?\{\{profile\.name\}\}\n\{\{profile\.job\}\}\n\n?\{\{profile\.introduction\}\}\n?(\n?- [^\n]*: \{\{contact:[^}]*\}\})*\s*$';
