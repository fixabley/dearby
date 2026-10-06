import { test, expect } from "@playwright/test";

// Synthetic session and intercepted HTTP only.
test("activities show automatic re-check results and verification sends the evidence quote", async ({
  page,
}, testInfo) => {
  const user = {
    id: "00000000-0000-4000-8000-000000000001",
    email: "test@example.com",
  };
  await page.addInitScript(
    (value) => {
      localStorage.setItem("sb-localhost-auth-token", JSON.stringify(value));
    },
    {
      access_token: "test-token",
      refresh_token: "test-refresh",
      expires_at: 4102444800,
      token_type: "bearer",
      user,
    },
  );
  const now = Date.now();
  const activity = {
    id: "00000000-0000-4000-8000-0000000000a1",
    program_id: "00000000-0000-4000-8000-0000000000b1",
    organization_id: "00000000-0000-4000-8000-0000000000c1",
    title: "하반기 디자인 펠로우십",
    summary: "",
    participation_type: "selection",
    recruitment_status: "open",
    publication_status: "published",
    recruitment_start_at: null,
    recruitment_end_at: null,
    date_label: "10월 24일 마감",
    location: null,
    cost: null,
    audience: null,
    qualification: null,
    roles: [],
    criteria: { audience: {}, qualification: {}, roles: {} },
    schedules: [],
    official_url: "https://official.example/fellowship",
    application_url: null,
    image_url: null,
    source_checked_at: new Date(now - 20 * 3600000).toISOString(),
    valid_until: new Date(now + 4 * 3600000).toISOString(),
    freshness: "verified",
    source_note: "공식 공지에서 마감일을 확인했습니다.",
    updated_at: new Date(now - 20 * 3600000).toISOString(),
  };
  // Published without any confirmation record: never shown in discovery.
  const unverified = {
    ...activity,
    id: "00000000-0000-4000-8000-0000000000a2",
    title: "Let'Swift 2026",
    source_checked_at: null,
    valid_until: null,
    freshness: "stale",
  };
  const evidence = {
    activity_id: activity.id,
    quote: "하반기 펠로우십 지원은 10월 24일 오후 6시에 마감합니다.",
    verified_at: activity.source_checked_at,
    last_check_at: new Date(now - 3600000).toISOString(),
    last_check_ok: false,
    last_error: "BLOCKED: Official page exceeds 3 MB",
  };
  const program = {
    id: activity.program_id,
    organization_id: activity.organization_id,
    title: "디자인 펠로우십",
    description: "",
    collection_enabled: false,
    collection_hosts: [],
  };
  const calls: unknown[] = [];
  await page.route("http://localhost:54321/**", async (route) => {
    const request = route.request(),
      path = new URL(request.url()).pathname;
    const single = (request.headers()["accept"] ?? "").includes(
      "vnd.pgrst.object",
    );
    const rows = (data: unknown[]) =>
      route.fulfill({
        json: single ? data[0] : data,
        headers: { "content-range": `0-${data.length - 1}/${data.length}` },
      });
    if (path === "/auth/v1/user") return route.fulfill({ json: user });
    if (path === "/rest/v1/rpc/is_catalog_admin")
      return route.fulfill({ json: true });
    if (path === "/rest/v1/rpc/verify_catalog_activity") {
      calls.push(request.postDataJSON());
      return route.fulfill({ json: activity });
    }
    if (path === "/rest/v1/catalog_activities")
      return rows(
        new URL(request.url()).searchParams.get("id")
          ? [activity]
          : [activity, unverified],
      );
    if (path === "/rest/v1/catalog_activity_evidence") return rows([evidence]);
    if (path === "/rest/v1/catalog_programs") return rows([program]);
    return route.fulfill({ json: [], headers: { "content-range": "0-0/0" } });
  });

  await page.goto("/activities");
  const row = page.getByRole("row").filter({ hasText: activity.title });
  await expect(row).toContainText("수동 확인 필요");
  await expect(row).toContainText("재확인 불가(원문 차단)");
  await expect(row).toContainText(/만료까지 [34]시간/);
  await page.screenshot({
    path: testInfo.outputPath("activities-reverify-1440.png"),
    fullPage: true,
    animations: "disabled",
  });

  await expect(row).toContainText("발견 노출 중");
  await expect(
    page.getByRole("row").filter({ hasText: unverified.title }),
  ).toContainText("미노출 · 공식 확인 없음");

  await row.getByRole("link", { name: "편집" }).click();
  await expect(page.getByText(evidence.quote)).toBeVisible();
  await page.getByRole("button", { name: "공식 정보 확인 기록" }).click();
  const dialog = page.getByRole("dialog");
  await expect(dialog.getByLabel("재확인 기준 구절")).toHaveValue(
    evidence.quote,
  );
  await dialog
    .getByLabel("확인 근거")
    .fill("공식 공지 본문 두 번째 문단에서 마감 일시를 확인했습니다.");
  await dialog
    .getByLabel("재확인 기준 구절")
    .fill("지원은 10월 24일 오후 6시에 마감합니다 자세한 내용은...");
  await expect(dialog).toContainText("생략부호");
  await expect(
    dialog.getByRole("button", { name: "확인 기록" }),
  ).toBeDisabled();
  await dialog.getByLabel("재확인 기준 구절").fill("");
  await expect(dialog).toContainText("비워 두면 자동 재확인 대상이 아니에요");
  await dialog.getByLabel("재확인 기준 구절").fill(`  ${evidence.quote}  `);
  await page.screenshot({
    path: testInfo.outputPath("verify-quote-1440.png"),

    animations: "disabled",
  });
  await dialog.getByRole("button", { name: "확인 기록" }).click();
  await expect(page.getByText("공식 정보 확인을 기록했어요.")).toBeVisible();
  expect(calls).toEqual([
    {
      activity_id: activity.id,
      evidence_note:
        "공식 공지 본문 두 번째 문단에서 마감 일시를 확인했습니다.",
      evidence_quote: evidence.quote,
    },
  ]);

  // The period decides the status; the preview follows the inputs before saving.
  const preview = page.getByRole("status").filter({ hasText: "현재 상태" });
  await expect(preview).toContainText("날짜 없음 · 선택한 상태 모집 중");
  await page.getByLabel("모집 시작", { exact: true }).fill("2099-01-01T09:00");
  await expect(preview).toContainText("기간 기준 모집 예정");
  await page.screenshot({
    path: testInfo.outputPath("recruitment-period-1440.png"),
    animations: "disabled",
  });
  await page.getByLabel("모집 시작", { exact: true }).fill("");
  await expect(preview).toContainText("날짜 없음");
});
