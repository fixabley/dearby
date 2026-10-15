-- 2026-10-15 사용자 결정: 같은 계위에서 띄어쓰기만 다른 이름도 중복으로 보고 막는다(대소문자는 구분).
-- 조직은 같은 상위 조직 아래(최상위끼리 포함), 프로그램은 같은 조직 아래. 서버가 먼저 확인해 안내하고, 동시에 들어와도 이 인덱스가 막는다
create unique index organizations_parent_name_uq
    on organizations (coalesce(parent_organization_id, 0), regexp_replace(name, '\s', '', 'g'));
create unique index programs_organization_name_uq
    on programs (organization_id, regexp_replace(name, '\s', '', 'g'));
