CREATE TABLE catalog_organizations (
  id TEXT PRIMARY KEY,
  content TEXT NOT NULL CHECK(json_valid(content))
);
CREATE TABLE catalog_programs (
  id TEXT PRIMARY KEY,
  organization_id TEXT NOT NULL REFERENCES catalog_organizations(id),
  content TEXT NOT NULL CHECK(json_valid(content)),
  UNIQUE(id, organization_id)
);
CREATE TABLE catalog_activities (
  id TEXT PRIMARY KEY,
  program_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  source_key TEXT NOT NULL UNIQUE,
  content TEXT NOT NULL CHECK(json_valid(content)),
  last_failure TEXT,
  good_body_sha256 TEXT,
  FOREIGN KEY(program_id, organization_id) REFERENCES catalog_programs(id, organization_id)
);
CREATE TABLE catalog_refreshes (
  source_key TEXT PRIMARY KEY,
  attempted_at TEXT NOT NULL,
  succeeded INTEGER NOT NULL CHECK(succeeded IN (0,1)),
  body_sha256 TEXT,
  note TEXT NOT NULL
);
