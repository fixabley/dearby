// Run explicitly against local Supabase. Only UUID-scoped fixtures are removed.
import { test } from "node:test";
import assert from "node:assert/strict";
import { randomUUID, randomBytes } from "node:crypto";
import { execFileSync } from "node:child_process";
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
    {
      cwd: new URL("../../../", import.meta.url),
      encoding: "utf8",
      stdio: ["ignore", "pipe", "pipe"],
    },
  ),
);
assert.equal(new URL(config.API_URL).hostname, "127.0.0.1");
const service = config.SERVICE_ROLE_KEY,
  anon = config.ANON_KEY;
async function req(path, body, key = service, method = "POST") {
  const res = await fetch(config.API_URL + path, {
    method,
    headers: {
      apikey: anon,
      Authorization: `Bearer ${key}`,
      "Content-Type": "application/json",
      Prefer: "return=representation",
    },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
  const text = await res.text();
  return { status: res.status, data: text ? JSON.parse(text) : null };
}
async function rpc(name, body = {}, key = service) {
  return req("/rest/v1/rpc/" + name, body, key);
}
const org = randomUUID(),
  program = randomUUID(),
  users = [];
const sql = (text) =>
  execFileSync(
    "docker",
    [
      "exec",
      "-i",
      "supabase_db_dearby",
      "psql",
      "-v",
      "ON_ERROR_STOP=1",
      "-U",
      "postgres",
      "-d",
      "postgres",
      "-Atc",
      text,
    ],
    { encoding: "utf8" },
  ).trim();
async function login(admin) {
  const email = `collector-${randomUUID()}@dearby.local`,
    password = randomBytes(24).toString("hex");
  const user = await req("/auth/v1/admin/users", {
    email,
    password,
    email_confirm: true,
    app_metadata: { catalog_admin: admin },
  });
  assert.equal(user.status, 200);
  users.push(user.data.id);
  const auth = await req(
    "/auth/v1/token?grant_type=password",
    { email, password },
    anon,
  );
  assert.equal(auth.status, 200);
  return auth.data.access_token;
}
const item = {
  source_url: "https://official.example/2026",
  occurrence: "2026",
  evidence: {
    quote: "공식 프로그램 2026년 참가 신청 안내입니다.",
    url: "https://official.example/2026",
  },
  activity: {
    title: "테스트 2026",
    summary: "original",
    participation_type: "registration",
    recruitment_status: "open",
    recruitment_start_at: null,
    recruitment_end_at: null,
    date_label: "10월",
    location: "Seoul",
    cost: null,
    audience: null,
    qualification: null,
    roles: [],
    schedules: [],
    application_url: null,
  },
};
let job, identity, activity;
async function claim() {
  const r = await rpc("claim_catalog_collection", { target_program: program });
  assert.equal(r.status, 200);
  assert.ok(r.data);
  job = r.data.job;
  identity = { job_id: job.id, token: job.lease_token };
  return r.data;
}
async function reset() {
  sql(
    `update catalog_collection_jobs set status='queued',available_at=now(),attempts=0,lease_token=null,lease_until=null where id='${job.id}'`,
  );
  await claim();
}
async function finish(items = [item]) {
  return rpc("finish_catalog_collection", {
    ...identity,
    items,
    run_usage: { input_tokens: 10 },
    warnings: [],
  });
}
async function getActivity() {
  return (
    await req(
      `/rest/v1/catalog_activities?id=eq.${activity}`,
      undefined,
      service,
      "GET",
    )
  ).data[0];
}
test("real local queue and privilege contracts", async (t) => {
  const admin = await login(true),
    viewer = await login(false);
  try {
    assert.equal(
      (
        await req("/rest/v1/catalog_organizations", {
          id: org,
          name: "[로컬 테스트] collector integration",
        })
      ).status,
      201,
    );
    assert.equal(
      (
        await req("/rest/v1/catalog_programs", {
          id: program,
          organization_id: org,
          title: "[로컬 테스트] collector integration",
          collection_enabled: true,
          collection_hosts: ["official.example"],
        })
      ).status,
      201,
    );
    await t.test(
      "anon/viewer cannot queue, claim, finish, read jobs; admin cannot forge worker finish",
      async () => {
        for (const key of [anon, viewer, admin]) {
          assert.ok(
            (await rpc("claim_catalog_collection", {}, key)).status >= 400,
          );
          assert.ok(
            (
              await rpc(
                "finish_catalog_collection",
                { job_id: randomUUID(), token: randomUUID(), items: [] },
                key,
              )
            ).status >= 400,
          );
        }
        assert.ok(
          (
            await rpc(
              "request_catalog_collection",
              { target_program: program },
              viewer,
            )
          ).status >= 400,
        );
        assert.deepEqual(
          (
            await req(
              "/rest/v1/catalog_collection_jobs",
              undefined,
              viewer,
              "GET",
            )
          ).data,
          [],
        );
        assert.equal(
          (
            await rpc(
              "request_catalog_collection",
              { target_program: program },
              admin,
            )
          ).data,
          1,
        );
        assert.equal(
          (
            await rpc(
              "request_catalog_collection",
              { target_program: program },
              admin,
            )
          ).data,
          0,
        );
      },
    );
    await t.test("concurrent claims yield exactly one owner", async () => {
      const results = await Promise.all([
        rpc("claim_catalog_collection", { target_program: program }),
        rpc("claim_catalog_collection", { target_program: program }),
      ]);
      assert.equal(results.filter((r) => r.data?.job).length, 1);
      job = results.find((r) => r.data?.job).data.job;
      identity = { job_id: job.id, token: job.lease_token };
      assert.equal(
        (await rpc("claim_catalog_collection", { target_program: program }))
          .data,
        null,
      );
      assert.ok(
        (
          await rpc("finish_catalog_collection", {
            ...identity,
            token: randomUUID(),
            items: [],
          })
        ).status >= 400,
      );
    });
    await t.test(
      "null/malformed/duplicate batches rejected atomically",
      async () => {
        for (const items of [
          null,
          [{}],
          [{ ...item, activity: null }],
          [item, item],
          [
            item,
            {
              ...item,
              occurrence: "2027",
              activity: { ...item.activity, publication_status: "published" },
            },
          ],
        ])
          assert.ok((await finish(items)).status >= 400);
        assert.equal(
          (
            await req(
              `/rest/v1/catalog_activities?program_id=eq.${program}`,
              undefined,
              service,
              "GET",
            )
          ).data.length,
          0,
        );
        assert.ok(
          (
            await rpc("finish_catalog_collection", {
              ...identity,
              items: [],
              warnings: [{ reason: "failed" }],
            })
          ).status >= 400,
        );
      },
    );
    await t.test(
      "create draft, repeated finish loses lease, next collection remains idempotent",
      async () => {
        const r = await finish();
        assert.equal(r.status, 200);
        assert.equal(r.data.created, 1);
        assert.ok((await finish()).status >= 400);
        activity = (
          await req(
            `/rest/v1/catalog_activities?program_id=eq.${program}`,
            undefined,
            service,
            "GET",
          )
        ).data[0].id;
        const a = await getActivity();
        assert.equal(a.publication_status, "draft");
        assert.equal(a.source_checked_at, null);
        await reset();
        const again = await finish();
        assert.equal(again.data.created, 0);
        assert.equal(again.data.updated, 0);
        assert.equal((await getActivity()).updated_at, a.updated_at);
      },
    );
    await t.test(
      "unknown values cannot erase known collector data; changes update untouched drafts",
      async () => {
        await reset();
        const r = await finish([
          { ...item, activity: { ...item.activity, location: null } },
        ]);
        assert.equal(r.data.review, 1);
        assert.equal((await getActivity()).location, "Seoul");
        await reset();
        const changed = await finish([
          {
            ...item,
            activity: { ...item.activity, summary: "collector update" },
          },
        ]);
        assert.equal(changed.data.updated, 1);
        assert.equal((await getActivity()).summary, "collector update");
      },
    );
    await t.test(
      "manual edits, verification, publication are preserved; later round is distinct",
      async () => {
        assert.equal(
          (
            await req(
              `/rest/v1/catalog_activities?id=eq.${activity}`,
              { summary: "administrator", publication_status: "published" },
              admin,
              "PATCH",
            )
          ).status,
          200,
        );
        assert.equal(
          (
            await rpc(
              "verify_catalog_activity",
              {
                activity_id: activity,
                evidence_note: "자동수집 보존 검증만을 위한 로컬 테스트",
              },
              admin,
            )
          ).status,
          200,
        );
        const verified = (await getActivity()).source_checked_at;
        assert.ok(verified);
        await reset();
        assert.equal((await finish()).data.review, 1);
        assert.equal((await getActivity()).summary, "administrator");
        assert.equal((await getActivity()).publication_status, "published");
        assert.equal((await getActivity()).source_checked_at, verified);
        await reset();
        assert.equal(
          (await finish([{ ...item, occurrence: "2027" }])).data.created,
          1,
        );
        const results = (
          await req(
            `/rest/v1/catalog_collection_results?program_id=eq.${program}&occurrence=eq.2027`,
            undefined,
            service,
            "GET",
          )
        ).data;
        assert.ok(results[0].evidence.possible_duplicates.includes(activity));
      },
    );
    await t.test(
      "failed attempts delayed; expired owner rejected; third expiration blocked",
      async () => {
        await reset();
        assert.equal(
          (
            await rpc("fail_catalog_collection", {
              ...identity,
              reason: "source unavailable",
              run_usage: { input_tokens: 42 },
            })
          ).status,
          204,
        );
        assert.equal(
          (await rpc("claim_catalog_collection", { target_program: program }))
            .data,
          null,
        );
        await reset();
        const old = { ...identity };
        sql(
          `update catalog_collection_jobs set lease_until=now()-interval '1 second' where id='${job.id}'`,
        );
        assert.ok(
          (await rpc("finish_catalog_collection", { ...old, items: [] }))
            .status >= 400,
        );
        assert.equal(
          (await rpc("claim_catalog_collection", { target_program: program }))
            .data,
          null,
        );
        assert.equal(
          sql(
            `select status from catalog_collection_jobs where id='${job.id}'`,
          ),
          "failed",
        );
        await reset();
        sql(
          `update catalog_collection_jobs set attempts=3,lease_until=now()-interval '1 second' where id='${job.id}'`,
        );
        await rpc("claim_catalog_collection", { target_program: program });
        assert.equal(
          sql(
            `select status from catalog_collection_jobs where id='${job.id}'`,
          ),
          "blocked",
        );
        assert.ok(
          (await rpc("resume_catalog_collection", { job_id: job.id }, viewer))
            .status >= 400,
        );
        assert.equal(
          (await rpc("resume_catalog_collection", { job_id: job.id }, admin))
            .status,
          204,
        );
      },
    );
    await t.test(
      "subscription errors pause all claims; explicit admin resume and attempt usage history",
      async () => {
        await claim();
        assert.equal(
          (
            await rpc("fail_catalog_collection", {
              ...identity,
              reason: "BLOCKED: Codex subscription quota requires attention",
              blocked: true,
              run_usage: { input_tokens: 99 },
            })
          ).status,
          204,
        );
        assert.equal(
          (await rpc("claim_catalog_collection", { target_program: program }))
            .data,
          null,
        );
        assert.ok(
          (
            await req(
              "/rest/v1/catalog_collection_settings",
              undefined,
              service,
              "GET",
            )
          ).data[0].pause_reason,
        );
        assert.equal(
          (await rpc("resume_catalog_collection", { job_id: job.id }, admin))
            .status,
          204,
        );
        assert.equal(
          (
            await req(
              "/rest/v1/catalog_collection_settings",
              undefined,
              service,
              "GET",
            )
          ).data[0].pause_reason,
          null,
        );
        const history = (
          await req(
            `/rest/v1/catalog_collection_jobs?id=eq.${job.id}`,
            undefined,
            service,
            "GET",
          )
        ).data[0].run_history;
        assert.ok(history.some((run) => run.usage.input_tokens === 42));
        assert.ok(history.some((run) => run.usage.input_tokens === 99));
      },
    );
    await t.test(
      "daily enqueue skips previous backlog and preserves one job per program/day",
      async () => {
        sql(
          `insert into catalog_collection_jobs(program_id,program_name,scheduled_day) values ('${program}','fixture',(now() at time zone 'Asia/Seoul')::date-1)`,
        );
        assert.equal(
          (
            await rpc(
              "request_catalog_collection",
              { target_program: program },
              admin,
            )
          ).data,
          0,
        );
        assert.equal(
          sql(
            `select status from catalog_collection_jobs where program_id='${program}' and scheduled_day<(now() at time zone 'Asia/Seoul')::date`,
          ),
          "skipped",
        );
        assert.equal(
          sql(
            "select schedule from cron.job where jobname='dearby-daily-program-collection' and active",
          ),
          "0 0 * * *",
        );
      },
    );
    await t.test(
      "disabled program cannot be claimed or enqueued; invalid hosts rejected",
      async () => {
        assert.ok(
          (
            await req(
              `/rest/v1/catalog_programs?id=eq.${program}`,
              { collection_hosts: ["https://example.com"] },
              admin,
              "PATCH",
            )
          ).status >= 400,
        );
        await req(
          `/rest/v1/catalog_programs?id=eq.${program}`,
          { collection_enabled: false },
          admin,
          "PATCH",
        );
        assert.equal(
          (await rpc("claim_catalog_collection", { target_program: program }))
            .data,
          null,
        );
      },
    );
  } finally {
    await req(
      `/rest/v1/catalog_collection_results?program_id=eq.${program}`,
      undefined,
      service,
      "DELETE",
    );
    await req(
      `/rest/v1/catalog_collection_jobs?program_id=eq.${program}`,
      undefined,
      service,
      "DELETE",
    );
    await req(
      `/rest/v1/catalog_activities?program_id=eq.${program}`,
      undefined,
      service,
      "DELETE",
    );
    await req(
      `/rest/v1/catalog_programs?id=eq.${program}`,
      undefined,
      service,
      "DELETE",
    );
    await req(
      `/rest/v1/catalog_organizations?id=eq.${org}`,
      undefined,
      service,
      "DELETE",
    );
    for (const id of users)
      await req("/auth/v1/admin/users/" + id, undefined, service, "DELETE");
  }
});
