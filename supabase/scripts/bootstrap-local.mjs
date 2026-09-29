import { execFileSync } from "node:child_process";
import { createHash, randomBytes } from "node:crypto";
import { readFileSync, writeFileSync, existsSync, mkdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { resolve, dirname } from "node:path";
const root = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
process.chdir(root);
process.umask(0o077);
const config = JSON.parse(
  execFileSync(
    "npm",
    [
      "exec",
      "--yes",
      "--package=supabase@2.118.0",
      "--",
      "supabase",
      "status",
      "-o",
      "json",
    ],
    { encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] },
  ),
);
if (new URL(config.API_URL).hostname !== "127.0.0.1")
  throw new Error("Local loopback Supabase only");
const base = config.API_URL,
  service = config.SERVICE_ROLE_KEY,
  anon = config.ANON_KEY;
async function request(path, body, method = "POST") {
  const response = await fetch(base + path, {
    method,
    headers: {
      apikey: service,
      Authorization: `Bearer ${service}`,
      "Content-Type": "application/json",
      Prefer: "resolution=ignore-duplicates,return=minimal",
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
  if (!response.ok)
    throw new Error(
      `${method} ${path}: ${response.status} ${await response.text()}`,
    );
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}
const receipt = resolve(root, "supabase/.env.credentials.json");
const credentials = existsSync(receipt)
  ? JSON.parse(readFileSync(receipt, "utf8"))
  : {
      email: "admin@dearby.local",
      password: randomBytes(24).toString("base64url"),
    };
const users = await request("/auth/v1/admin/users", null, "GET");
if (!users.users.some((user) => user.email === credentials.email))
  await request("/auth/v1/admin/users", {
    email: credentials.email,
    password: credentials.password,
    email_confirm: true,
    app_metadata: { catalog_admin: true },
  });
writeFileSync(receipt, JSON.stringify(credentials, null, 2) + "\n", {
  mode: 0o600,
});
mkdirSync("apps/admin", { recursive: true });
writeFileSync(
  "apps/admin/.env.local",
  `VITE_SUPABASE_URL=${base}\nVITE_SUPABASE_ANON_KEY=${anon}\n`,
  { mode: 0o600 },
);
mkdirSync("apps/dearby-api/var", { recursive: true });
const apiEnv = "apps/dearby-api/.env.admin-local";
if (!existsSync(apiEnv))
  writeFileSync(
    apiEnv,
    `CATALOG_BACKEND=supabase\nSUPABASE_URL=${base}\nSUPABASE_ANON_KEY=${anon}\nOTP_SECRET=${randomBytes(32).toString("hex")}\nDATABASE_PATH=./var/admin-preview.sqlite\nHOST=127.0.0.1\nPORT=58765\n`,
    { mode: 0o600 },
  );
function id(kind, key) {
  const hash = createHash("sha1")
    .update(Buffer.from("6ba7b8109dad11d180b400c04fd430c8", "hex"))
    .update(`dearby/catalog/${kind}/${key}`)
    .digest()
    .subarray(0, 16);
  hash[6] = (hash[6] & 15) | 80;
  hash[8] = (hash[8] & 63) | 128;
  const s = hash.toString("hex");
  return `${s.slice(0, 8)}-${s.slice(8, 12)}-${s.slice(12, 16)}-${s.slice(16, 20)}-${s.slice(20)}`;
}
const source = JSON.parse(
  readFileSync("shared/data/catalog-snapshot-2026-09-24.json", "utf8"),
);
await request(
  "/rest/v1/catalog_organizations?on_conflict=id",
  source.organizations.map((o) => ({
    id: id("organization", o.id),
    name: o.name,
    description: o.description,
  })),
);
await request(
  "/rest/v1/catalog_programs?on_conflict=id",
  source.programs.map((p) => ({
    id: id("program", p.id),
    organization_id: id("organization", p.orgId),
    title: p.title,
    description: p.subtitle,
  })),
);
const activities = source.programs.flatMap((p) =>
  p.notices.map((n) => ({
    id: id("activity", n.id),
    program_id: id("program", p.id),
    organization_id: id("organization", p.orgId),
    title: `${p.title} · ${n.round}`,
    summary: p.subtitle,
    participation_type:
      n.participationType === "application" ? "selection" : "registration",
    recruitment_status: "unknown",
    date_label: [n.eventDate, n.eventEndDate].filter(Boolean).join(" ~ "),
    location: n.location,
    cost: n.cost,
    audience: Array.isArray(n.audience) ? n.audience.join(", ") : n.audience,
    qualification: n.qualification,
    roles: n.roles,
    schedules: [],
    official_url: n.officialUrl,
    application_url: n.registrationUrl,
    publication_status: "draft",
    freshness: "stale",
    source_note: `${source.snapshotDate} 과거 공식 출처 스냅샷. 현재 모집 여부와 일정은 재확인이 필요합니다.`,
  })),
);
await request("/rest/v1/catalog_activities?on_conflict=id", activities);
console.log(
  `Local admin ready. Imported ${activities.length} historical activities as drafts (existing rows preserved).`,
);
console.log(
  "Login credentials: supabase/.env.credentials.json (local private file; do not commit).",
);
console.log(
  "Admin: http://127.0.0.1:5173 · API: http://127.0.0.1:58765/v1/catalog",
);
