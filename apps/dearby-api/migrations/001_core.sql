CREATE TABLE profiles (id TEXT PRIMARY KEY, email TEXT NOT NULL UNIQUE, data TEXT NOT NULL);
CREATE TABLE challenges (id TEXT PRIMARY KEY, email TEXT NOT NULL, digest TEXT NOT NULL, expires_at INTEGER NOT NULL, attempts INTEGER NOT NULL DEFAULT 0, consumed INTEGER NOT NULL DEFAULT 0);
CREATE TABLE rate_limits (key TEXT PRIMARY KEY, window_start INTEGER NOT NULL, count INTEGER NOT NULL);
CREATE TABLE sessions (digest TEXT PRIMARY KEY, profile_id TEXT NOT NULL REFERENCES profiles(id), expires_at INTEGER NOT NULL);
CREATE TABLE cards (id TEXT PRIMARY KEY, owner_id TEXT NOT NULL REFERENCES profiles(id), data TEXT NOT NULL, revoked INTEGER NOT NULL DEFAULT 0);
CREATE TABLE receipts (id TEXT PRIMARY KEY, recipient_id TEXT NOT NULL REFERENCES profiles(id), card_id TEXT NOT NULL REFERENCES cards(id), context TEXT NOT NULL, received_at TEXT NOT NULL);
CREATE INDEX receipts_recipient_card ON receipts(recipient_id, card_id);
CREATE TABLE exchanges (sender_id TEXT NOT NULL REFERENCES profiles(id), request_id TEXT NOT NULL, body TEXT NOT NULL, receipt_id TEXT NOT NULL REFERENCES receipts(id), delivered_at TEXT NOT NULL, PRIMARY KEY(sender_id, request_id));
