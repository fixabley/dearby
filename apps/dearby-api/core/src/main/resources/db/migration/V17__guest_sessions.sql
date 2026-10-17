-- 웹 비로그인 저장(native-v1.md "웹 비로그인 명함 저장", "명함 공유 기록과 게스트 공유 정보 저장"). 옛 TS API의 표를 Kotlin으로 옮긴다.
-- 세션은 토큰의 SHA-256 digest만 둔다(원문 토큰·쿠키·방문자 정보 없음). 자동 만료는 없다
create table guest_sessions
(
    digest     varchar(64) primary key,
    created_at timestamp with time zone not null
);

-- 세션이 저장한 명함(논리키 business_cards). 철회된 명함은 조회에서 빠지고 다음 저장 때 정리된다
create table guest_cards
(
    session_digest varchar(64) not null references guest_sessions (digest) on delete cascade,
    card_id        uuid        not null,
    seq            bigint generated always as identity,
    primary key (session_digest, card_id)
);

-- 저장한 명함에 연결된 공유 기록(명함당 20개)
create table guest_card_shares
(
    session_digest varchar(64)              not null,
    card_id        uuid                     not null,
    share_id       uuid                     not null, -- 논리키 card_shares
    saved_at       timestamp with time zone not null,
    seq            bigint generated always as identity,
    primary key (session_digest, share_id),
    foreign key (session_digest, card_id) references guest_cards (session_digest, card_id) on delete cascade
);
create index guest_card_shares_card_idx on guest_card_shares (session_digest, card_id);

-- 홈 화면 세션 잇기: 1회용 코드의 digest와, 코드에서 파생한 키로 암호화한 토큰. 세션당 하나
create table guest_handoffs
(
    digest           varchar(64) primary key,
    session_digest   varchar(64)              not null unique references guest_sessions (digest) on delete cascade,
    token_ciphertext varchar(500),
    expires_at       timestamp with time zone not null,
    used_at          timestamp with time zone
);
