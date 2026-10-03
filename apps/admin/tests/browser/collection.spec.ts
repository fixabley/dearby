import { test, expect } from "@playwright/test";
import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { randomUUID } from "node:crypto";
const login = JSON.parse(
  readFileSync(
    new URL("../../../../supabase/.env.credentials.json", import.meta.url),
    "utf8",
  ),
);
const env = JSON.parse(
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
      cwd: new URL("../../../../", import.meta.url),
      encoding: "utf8",
      stdio: ["ignore", "pipe", "pipe"],
    },
  ),
);
test("collection configuration, manual queue, retry, evidence and protected activity review", async ({
  page,
  request,
}) => {
  const org = randomUUID(),
    program = randomUUID(),
    activity = randomUUID(),
    title = `[로컬 테스트] 수집 UI ${Date.now()}`;
  const headers = {
    apikey: env.SERVICE_ROLE_KEY,
    Authorization: `Bearer ${env.SERVICE_ROLE_KEY}`,
    Prefer: "return=representation",
  };
  const api = env.API_URL + "/rest/v1/";
  async function insert(table: string, data: unknown) {
    const r = await request.post(api + table, { headers, data });
    expect(r.ok()).toBe(true);
    return (await r.json())[0];
  }
  await insert("catalog_organizations", { id: org, name: title });
  await insert("catalog_programs", {
    id: program,
    organization_id: org,
    title,
    collection_hosts: [],
  });
  try {
    await page.goto("/");
    await page.getByLabel("이메일", { exact: true }).fill(login.email);
    await page.getByLabel("비밀번호", { exact: true }).fill(login.password);
    await page.getByRole("button", { name: "관리자로 로그인" }).click();
    await page.getByRole("link", { name: "프로그램", exact: true }).click();
    await page.getByRole("button", { name: title, exact: true }).click();
    await page.getByLabel("매일 활동 수집").click();
    await page
      .getByLabel("공식 출처 호스트", { exact: true })
      .fill("official.example");
    await page.getByLabel("공식 출처 호스트", { exact: true }).press("Enter");
    await page.getByRole("button", { name: "저장", exact: true }).click();
    await expect(page.getByRole("dialog")).toHaveCount(0);
    await page.getByRole("link", { name: "활동 수집", exact: true }).click();
    await expect(
      page.getByRole("heading", { name: "활동 수집" }),
    ).toBeVisible();
    await page.getByRole("combobox", { name: "수집 프로그램" }).fill(title);
    await page.getByRole("option", { name: title, exact: true }).click();
    await page
      .getByRole("button", { name: "오늘 수집 등록", exact: true })
      .click();
    await expect(
      page.getByText("1개 작업을 등록했어요.", { exact: false }),
    ).toBeVisible();
    const jobs = await request.get(
      api + `catalog_collection_jobs?program_id=eq.${program}`,
      { headers },
    );
    const job = (await jobs.json())[0];
    expect(job.status).toBe("queued");
    await page
      .getByRole("button", { name: "오늘 수집 등록", exact: true })
      .click();
    await expect(
      page.getByText("0개 작업을 등록했어요.", { exact: false }),
    ).toBeVisible();
    await request.patch(api + `catalog_collection_jobs?id=eq.${job.id}`, {
      headers,
      data: { status: "blocked", error: "테스트: 로그인 확인 필요" },
    });
    await page.getByRole("button", { name: "새로고침", exact: true }).click();
    const jobRow = page.getByRole("row").filter({ hasText: title });
    await jobRow.getByRole("button", { name: "원인 해소 후 재시도" }).click();
    await expect(jobRow.getByText("대기", { exact: true })).toBeVisible();
    await insert("catalog_activities", {
      id: activity,
      program_id: program,
      organization_id: org,
      title: title + " 활동",
      official_url: "https://official.example/2026",
      summary: "관리자 기존 내용",
    });
    await insert("catalog_collection_results", {
      program_id: program,
      job_id: job.id,
      source_url: "https://official.example/2026",
      occurrence: "2026",
      activity_id: activity,
      status: "review",
      proposed_activity: { title: "새 후보", summary: "제안 내용" },
      evidence: {
        quote: "공식 프로그램 회차의 원문 구절을 확인했습니다.",
        verification_scope: "quote_presence_only",
      },
    });
    await request.patch(api + `catalog_collection_jobs?id=eq.${job.id}`, {
      headers,
      data: {
        status: "succeeded",
        stats: { review: 1 },
        usage: {
          input_tokens: 123,
          output_tokens: 45,
          auth: "chatgpt-subscription",
        },
      },
    });
    await page.getByRole("button", { name: "새로고침", exact: true }).click();
    await jobRow.locator(".ant-table-row-expand-icon").click();
    await expect(
      page.getByText("관리자 검토 필요", { exact: true }),
    ).toBeVisible();
    await expect(page.getByRole("link", { name: "공식 원문" })).toHaveAttribute(
      "href",
      "https://official.example/2026",
    );
    const candidate = page
      .getByRole("row")
      .filter({ hasText: "관리자 검토 필요" });
    await candidate.locator(".ant-table-row-expand-icon").click();
    await expect(
      page.getByText("공식 프로그램 회차의 원문 구절을 확인했습니다.", {
        exact: true,
      }),
    ).toBeVisible();
    await expect(page.locator(".ant-message-notice")).toHaveCount(0);
    await page.screenshot({
      path: "docs/evidence/collection.png",
      fullPage: true,
    });
    await page.getByRole("link", { name: "활동 편집", exact: true }).click();
    await expect(page.getByLabel("소개", { exact: true })).toHaveValue(
      "관리자 기존 내용",
    );
    await page.setViewportSize({ width: 390, height: 844 });
    await page.getByRole("link", { name: "활동 수집", exact: true }).click();
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true);
    await expect(page.locator(".ant-message-notice")).toHaveCount(0);
    await page.screenshot({
      path: "docs/evidence/collection-mobile.png",
      fullPage: false,
    });
  } finally {
    for (const [table, filter] of [
      ["catalog_collection_results", `program_id=eq.${program}`],
      ["catalog_collection_jobs", `program_id=eq.${program}`],
      ["catalog_activities", `program_id=eq.${program}`],
      ["catalog_programs", `id=eq.${program}`],
      ["catalog_organizations", `id=eq.${org}`],
    ]) {
      const r = await request.delete(api + table + "?" + filter, { headers });
      expect(r.ok()).toBe(true);
    }
  }
});
