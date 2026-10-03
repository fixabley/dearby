-- Guest identity is independent of profile identity; sessions do not expire.
CREATE TABLE guest_sessions (digest TEXT PRIMARY KEY);
CREATE TABLE guest_cards (
  session_digest TEXT NOT NULL REFERENCES guest_sessions(digest) ON DELETE CASCADE,
  card_id TEXT NOT NULL REFERENCES cards(id),
  PRIMARY KEY (session_digest, card_id)
);
