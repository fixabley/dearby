-- 2026-11-03 사용자 결정(admin-v1.md "발견 필터 구성", native-v1.md "발견 필터 시트 표현"): 앱 필터 시트의 섹션·항목·아이콘을 어드민이 정한다.
-- 한 행(id = 1)에 구성 전체를 jsonb로 둔다: {sections: [{key, enabled, title, subtitle?, items?: [{code, enabled, label, icon}]}]}.
-- 행이 없으면 서버 기본 구성(상태 세 묶음·종류 여섯·분야 상위 전부·대상 켬)을 쓴다. 공개 GET /v1/discovery/filters가 그대로 내려 준다
create table discovery_filter_config
(
    id         integer primary key check (id = 1),
    config     jsonb                    not null,
    updated_at timestamp with time zone not null
);
