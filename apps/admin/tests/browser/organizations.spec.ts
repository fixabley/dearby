import { test, expect, type Page } from "@playwright/test";
import { randomUUID } from "node:crypto";

// Local Supabase only: the admin dev server must point at the same URL.
const url = process.env.ADMIN_TEST_SUPABASE_URL ?? "";
const serviceKey = process.env.ADMIN_TEST_SERVICE_ROLE_KEY ?? "";
test.skip(
  !url || !serviceKey,
  "ADMIN_TEST_SUPABASE_URL and ADMIN_TEST_SERVICE_ROLE_KEY are required",
);
if (url && !["127.0.0.1", "localhost"].includes(new URL(url).hostname))
  throw new Error(
    "Organization tree tests write data and run against local Supabase only.",
  );

const headers = {
  apikey: serviceKey,
  Authorization: `Bearer ${serviceKey}`,
  Prefer: "return=representation",
};
const k = Date.now().toString(36);
const id = () => randomUUID();
const ids = {
  root: id(),
  platform: id(),
  search: id(),
  partner: id(),
  program: id(),
  partnerProgram: id(),
  activity: id(),
};
const names = {
  root: `테스트그룹 ${k}`,
  platform: `플랫폼본부 ${k}`,
  search: `검색팀 ${k}`,
  partner: `파트너재단 ${k}`,
  program: `리서치 펠로우십 ${k}`,
  partnerProgram: `장학 프로그램 ${k}`,
  activity: `여름 인턴 모집 ${k}`,
  child: `디자인실 ${k}`,
};
const login = { email: `tree-${k}@example.test`, password: randomUUID() };
let userId = "";

const row = (page: Page, name: string) =>
  page
    .locator("tr")
    .filter({ has: page.locator("td:first-child", { hasText: name }) });

test.beforeAll(async ({ request }) => {
  const user = await request.post(`${url}/auth/v1/admin/users`, {
    headers,
    data: {
      ...login,
      email_confirm: true,
      app_metadata: { catalog_admin: true },
    },
  });
  expect(user.ok()).toBe(true);
  userId = (await user.json()).id;
  const insert = async (table: string, data: unknown) =>
    expect(
      (await request.post(`${url}/rest/v1/${table}`, { headers, data })).ok(),
    ).toBe(true);
  await insert("catalog_organizations", [
    { id: ids.root, name: names.root, parent_id: null },
    { id: ids.partner, name: names.partner, parent_id: null },
  ]);
  await insert("catalog_organizations", {
    id: ids.platform,
    name: names.platform,
    parent_id: ids.root,
  });
  await insert("catalog_organizations", {
    id: ids.search,
    name: names.search,
    parent_id: ids.platform,
  });
  await insert("catalog_programs", [
    {
      id: ids.program,
      organization_id: ids.search,
      title: names.program,
      collection_hosts: [],
    },
    {
      id: ids.partnerProgram,
      organization_id: ids.partner,
      title: names.partnerProgram,
      collection_hosts: [],
    },
  ]);
  await insert("catalog_activities", {
    id: ids.activity,
    program_id: ids.program,
    organization_id: ids.search,
    title: names.activity,
    official_url: "https://official.example/summer",
    summary: "검색 품질을 함께 연구할 여름 인턴",
  });
});

test.afterAll(async ({ request }) => {
  const remove = (path: string) =>
    request.delete(`${url}/rest/v1/${path}`, { headers });
  await remove(`catalog_activities?id=eq.${ids.activity}`);
  await remove(`catalog_programs?id=in.(${ids.program},${ids.partnerProgram})`);
  await remove(
    `catalog_organizations?name=eq.${encodeURIComponent(names.child)}`,
  );
  for (const org of [ids.search, ids.platform, ids.root, ids.partner])
    await remove(`catalog_organizations?id=eq.${org}`);
  if (userId)
    await request.delete(`${url}/auth/v1/admin/users/${userId}`, { headers });
});

test("organization tree expands, searches, edits inline and opens activities", async ({
  page,
  request,
}, testInfo) => {
  const get = async (path: string) =>
    (
      await (await request.get(`${url}/rest/v1/${path}`, { headers })).json()
    )[0];
  await page.goto("/organizations");
  await page.getByLabel("이메일", { exact: true }).fill(login.email);
  await page.getByLabel("비밀번호", { exact: true }).fill(login.password);
  await page.getByRole("button", { name: "관리자로 로그인" }).click();
  await page.getByRole("link", { name: "조직", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "조직 관리", exact: true }),
  ).toBeVisible();

  // Several organizations stay open at once; children load on expand.
  await expect(row(page, names.platform)).toHaveCount(0);
  await row(page, names.root).getByRole("button", { name: "펼치기" }).click();
  await row(page, names.partner)
    .getByRole("button", { name: "펼치기" })
    .click();
  await expect(row(page, names.platform)).toBeVisible();
  await expect(row(page, names.partnerProgram)).toBeVisible();
  await expect(row(page, names.search)).toHaveCount(0);

  // A matching activity opens every ancestor down to its program.
  await page
    .getByRole("searchbox", { name: "조직·프로그램·활동 검색" })
    .fill(names.activity);
  await page
    .getByRole("searchbox", { name: "조직·프로그램·활동 검색" })
    .press("Enter");
  await expect(row(page, names.activity)).toBeVisible();
  await expect(row(page, names.activity)).toHaveClass(/tree-match/);
  await expect(page.locator(".ant-spin-spinning")).toHaveCount(0);
  await page.screenshot({
    path: testInfo.outputPath("organizations-1440.png"),
    fullPage: true,
    animations: "disabled",
  });

  // Parent choices exclude the organization itself and its descendants.
  await row(page, names.platform).getByRole("button", { name: "편집" }).click();
  await page.getByRole("combobox", { name: "상위 조직" }).fill(k);
  await expect(
    page.getByRole("option", { name: names.partner, exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("option", {
      name: `${names.root} › ${names.platform}`,
      exact: true,
    }),
  ).toHaveCount(0);
  await expect(
    page.getByRole("option", {
      name: `${names.root} › ${names.platform} › ${names.search}`,
      exact: true,
    }),
  ).toHaveCount(0);
  await page.keyboard.press("Escape");
  await page.getByRole("button", { name: "취소" }).click();

  // Rename and move a sub-organization in place.
  await row(page, names.search).getByRole("button", { name: "편집" }).click();
  const renamed = `검색본부 ${k}`;
  await page.getByLabel("조직 이름", { exact: true }).fill(renamed);
  await page.getByRole("combobox", { name: "상위 조직" }).fill(names.partner);
  await page.getByRole("option", { name: names.partner, exact: true }).click();
  await page.screenshot({
    path: testInfo.outputPath("organizations-edit-1440.png"),
    fullPage: true,
    animations: "disabled",
  });
  await page.getByRole("button", { name: "완료" }).click();
  await expect(page.getByText("저장했어요.")).toBeVisible();
  expect(await get(`catalog_organizations?id=eq.${ids.search}`)).toMatchObject({
    name: renamed,
    parent_id: ids.partner,
  });

  // Program description edits in place as well.
  await row(page, names.partnerProgram)
    .getByRole("button", { name: "편집" })
    .click();
  await page
    .getByLabel("소개", { exact: true })
    .fill("재단이 운영하는 장학 과정");
  await page.getByRole("button", { name: "완료" }).click();
  await expect(row(page, names.partnerProgram)).toContainText(
    "재단이 운영하는 장학 과정",
  );
  expect(
    (await get(`catalog_programs?id=eq.${ids.partnerProgram}`)).description,
  ).toBe("재단이 운영하는 장학 과정");

  // New organizations can be created under a parent.
  await page.getByRole("button", { name: "조직 만들기" }).click();
  await page.getByLabel("조직 이름", { exact: true }).fill(names.child);
  await page.getByRole("combobox", { name: "상위 조직" }).fill(names.root);
  await page.getByRole("option", { name: names.root, exact: true }).click();
  await page.getByRole("button", { name: "완료" }).click();
  await expect(row(page, names.child)).toBeVisible();
  expect(
    (
      await get(
        `catalog_organizations?name=eq.${encodeURIComponent(names.child)}`,
      )
    ).parent_id,
  ).toBe(ids.root);

  // Activity rows open the existing editor.
  await row(page, names.activity)
    .getByRole("link", { name: "활동 편집" })
    .click();
  await expect(page).toHaveURL(new RegExp(`/activities/${ids.activity}$`));
  await expect(page.getByLabel("활동 제목", { exact: true })).toHaveValue(
    names.activity,
  );
});
