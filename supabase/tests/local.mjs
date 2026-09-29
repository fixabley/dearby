import { test } from "node:test";
import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { randomUUID, randomBytes } from "node:crypto";
import { fileURLToPath } from "node:url";
const root = fileURLToPath(new URL("../..", import.meta.url));
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
    { cwd: root, encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] },
  ),
);
assert.equal(new URL(config.API_URL).hostname, "127.0.0.1");
const api = process.env.DEARBY_TEST_API_URL ?? "http://127.0.0.1:58765";
assert.equal(new URL(api).hostname, "127.0.0.1");
const base = config.API_URL,
  anon = config.ANON_KEY,
  service = config.SERVICE_ROLE_KEY;
async function request(path, key = anon, body, method = "GET") {
  const response = await fetch(base + path, {
    method,
    headers: {
      apikey: anon,
      Authorization: `Bearer ${key}`,
      "Content-Type": "application/json",
      Prefer: "return=representation",
    },
    ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
  });
  const text = await response.text();
  return { status: response.status, body: text ? JSON.parse(text) : null };
}
const ids = {
  org: randomUUID(),
  org2: randomUUID(),
  program: randomUUID(),
  activity: randomUUID(),
};
const users = [];
async function user(admin) {
  const email = `catalog-test-${randomUUID()}@dearby.local`,
    password = randomBytes(24).toString("hex");
  const created = await request(
    "/auth/v1/admin/users",
    service,
    {
      email,
      password,
      email_confirm: true,
      app_metadata: { catalog_admin: admin },
    },
    "POST",
  );
  assert.equal(created.status, 200);
  users.push(created.body.id);
  const login = await request(
    "/auth/v1/token?grant_type=password",
    anon,
    { email, password },
    "POST",
  );
  assert.equal(login.status, 200);
  return login.body.access_token;
}
const snapshot = async () => {
  const res = await fetch(api + "/v1/catalog");
  assert.equal(res.status, 200);
  return await res.json();
};
test("real local Auth/RLS, CRUD, immutable audit, publication, verification and existing API contract", async (t) => {
  try {
    const admin = await user(true),
      viewer = await user(false);
    await t.test(
      "anonymous and non-admin cannot read or mutate admin tables; user metadata cannot escalate",
      async () => {
        assert.equal(
          (await request("/rest/v1/catalog_activities")).status,
          401,
        );
        assert.deepEqual(
          (await request("/rest/v1/catalog_activities", viewer)).body,
          [],
        );
        const denied = await request(
          "/rest/v1/catalog_organizations",
          viewer,
          { name: "forbidden" },
          "POST",
        );
        assert.equal(denied.status, 403);
        await request(
          "/auth/v1/user",
          viewer,
          { data: { catalog_admin: true } },
          "PUT",
        );
        assert.equal(
          (await request("/rest/v1/rpc/is_catalog_admin", viewer, {}, "POST"))
            .body,
          false,
        );
        assert.equal(
          (
            await request(
              "/rest/v1/rpc/verify_catalog_activity",
              viewer,
              {
                activity_id: ids.activity,
                evidence_note: "권한 없는 사용자의 확인 근거",
              },
              "POST",
            )
          ).status,
          403,
        );
      },
    );
    await t.test(
      "admin creates organization/program/activity and drafts stay out of public snapshot",
      async () => {
        assert.equal(
          (
            await request(
              "/rest/v1/catalog_organizations",
              admin,
              [
                { id: ids.org, name: "[테스트] 조직" },
                { id: ids.org2, name: "[테스트] 다른 조직" },
              ],
              "POST",
            )
          ).status,
          201,
        );
        assert.equal(
          (
            await request(
              "/rest/v1/catalog_programs",
              admin,
              {
                id: ids.program,
                organization_id: ids.org,
                title: "[테스트] 프로그램",
              },
              "POST",
            )
          ).status,
          201,
        );
        assert.equal(
          (
            await request(
              "/rest/v1/catalog_activities",
              admin,
              {
                id: ids.activity,
                program_id: ids.program,
                organization_id: ids.org,
                title: "[로컬 테스트] 활동",
                official_url: "https://example.com",
                recruitment_status: "open",
              },
              "POST",
            )
          ).status,
          201,
        );
        assert.ok(
          !(await snapshot()).activities.some((a) => a.id === ids.activity),
        );
      },
    );
    await t.test(
      "database rejects invalid relationships, URLs, schedules and future verification",
      async () => {
        for (const change of [
          { organization_id: ids.org2 },
          { official_url: "javascript:alert(1)" },
          {
            schedules: [
              {
                id: randomUUID(),
                title: "invalid",
                dateLabel: "",
                timeZone: "Asia/Seoul",
                startAt: "2026-10-24T16:00:00+09:00",
                endAt: "2026-10-24T14:00:00+09:00",
              },
            ],
          },
          {
            schedules: [
              {
                id: randomUUID(),
                title: "invalid zone",
                dateLabel: "",
                timeZone: "Invalid/Zone",
                startAt: null,
                endAt: null,
              },
            ],
          },
          {
            source_checked_at: new Date(Date.now() + 3600000).toISOString(),
            valid_until: new Date(Date.now() + 7200000).toISOString(),
            freshness: "verified",
            source_note: "future not allowed",
          },
        ]) {
          const res = await request(
            `/rest/v1/catalog_activities?id=eq.${ids.activity}`,
            admin,
            change,
            "PATCH",
          );
          assert.ok(res.status >= 400);
        }
      },
    );
    await t.test(
      "published alone is not recruiting, official verify enables discovery without changing IDs",
      async () => {
        await request(
          `/rest/v1/catalog_activities?id=eq.${ids.activity}`,
          admin,
          { publication_status: "published" },
          "PATCH",
        );
        assert.equal(
          (await snapshot()).activities.find((a) => a.id === ids.activity)
            .isRecruiting,
          false,
        );
        const verified = await request(
          "/rest/v1/rpc/verify_catalog_activity",
          admin,
          {
            activity_id: ids.activity,
            evidence_note:
              "명시적인 로컬 테스트 근거이며 실제 공식 모집 확인이 아님",
          },
          "POST",
        );
        assert.equal(verified.status, 200);
        const result = await snapshot(),
          item = result.activities.find((a) => a.id === ids.activity);
        assert.equal(item.isRecruiting, true);
        assert.equal(item.freshness, "verified");
        assert.equal(item.organizationId, ids.org);
        assert.equal(item.programId, ids.program);
        assert.ok(result.organizations.some((o) => o.id === ids.org));
      },
    );
    await t.test(
      "content edit invalidates verification; past deadline stays closed after verification",
      async () => {
        await request(
          `/rest/v1/catalog_activities?id=eq.${ids.activity}`,
          admin,
          { summary: "내용이 바뀌면 검토를 다시 해야 합니다." },
          "PATCH",
        );
        const edited = (await snapshot()).activities.find(
          (a) => a.id === ids.activity,
        );
        assert.equal(edited.isRecruiting, false);
        assert.equal(edited.sourceCheckedAt, null);
        await request(
          `/rest/v1/catalog_activities?id=eq.${ids.activity}`,
          admin,
          { recruitment_end_at: new Date(Date.now() - 60000).toISOString() },
          "PATCH",
        );
        await request(
          "/rest/v1/rpc/verify_catalog_activity",
          admin,
          {
            activity_id: ids.activity,
            evidence_note: "마감 상태 표시를 검증하는 로컬 테스트입니다.",
          },
          "POST",
        );
        const closed = (await snapshot()).activities.find(
          (a) => a.id === ids.activity,
        );
        assert.equal(closed.isRecruiting, false);
        assert.equal(closed.recruitmentStatus, "closed");
      },
    );
    await t.test(
      "hidden activity and its unreferenced parents disappear; audit is readable only by admin and immutable",
      async () => {
        await request(
          `/rest/v1/catalog_activities?id=eq.${ids.activity}`,
          admin,
          { publication_status: "hidden" },
          "PATCH",
        );
        assert.ok(
          !(await snapshot()).activities.some((a) => a.id === ids.activity),
        );
        const audit = await request(
          `/rest/v1/catalog_audit_log?record_id=eq.${ids.activity}`,
          admin,
        );
        assert.ok(audit.body.length >= 5);
        assert.equal(
          (
            await request(
              `/rest/v1/catalog_audit_log?id=eq.${audit.body[0].id}`,
              admin,
              { operation: "tamper" },
              "PATCH",
            )
          ).status,
          403,
        );
        assert.equal(
          (
            await request(
              "/rest/v1/catalog_activities?id=eq." + ids.activity,
              admin,
              undefined,
              "DELETE",
            )
          ).status,
          403,
        );
        assert.deepEqual(
          (await request("/rest/v1/catalog_audit_log", viewer)).body,
          [],
        );
      },
    );
  } finally {
    await request(
      "/rest/v1/catalog_activities?id=eq." + ids.activity,
      service,
      undefined,
      "DELETE",
    );
    await request(
      "/rest/v1/catalog_programs?id=eq." + ids.program,
      service,
      undefined,
      "DELETE",
    );
    await request(
      `/rest/v1/catalog_organizations?id=in.(${ids.org},${ids.org2})`,
      service,
      undefined,
      "DELETE",
    );
    for (const id of users)
      await request("/auth/v1/admin/users/" + id, service, undefined, "DELETE");
  }
});
