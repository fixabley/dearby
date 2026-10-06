import { test, expect } from "@playwright/test";

// Before the hierarchy migration: organizations have no parent_id column. Synthetic session, intercepted HTTP only.
test("organization tree works without parent_id and never sends it", async ({
  page,
}) => {
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
  const organizations = [
    {
      id: "00000000-0000-4000-8000-0000000000a1",
      name: "가나재단",
      description: "",
    },
    {
      id: "00000000-0000-4000-8000-0000000000b1",
      name: "다라협회",
      description: "",
    },
  ];
  const program = {
    id: "00000000-0000-4000-8000-0000000000c1",
    organization_id: organizations[0].id,
    title: "여름 학교",
    description: "",
    collection_enabled: false,
    collection_hosts: [],
  };
  const writes: unknown[] = [];
  await page.route("http://localhost:54321/**", async (route) => {
    const request = route.request(),
      path = new URL(request.url()).pathname;
    if (path === "/auth/v1/user") return route.fulfill({ json: user });
    if (path === "/rest/v1/rpc/is_catalog_admin")
      return route.fulfill({ json: true });
    if (request.method() === "PATCH") {
      writes.push(request.postDataJSON());
      return route.fulfill({
        json: [{ ...organizations[0], ...request.postDataJSON() }],
      });
    }
    if (path === "/rest/v1/catalog_organizations")
      return route.fulfill({ json: organizations });
    if (path === "/rest/v1/catalog_programs")
      return route.fulfill({ json: [program] });
    return route.fulfill({ json: [] });
  });
  await page.goto("/organizations");
  await expect(
    page.getByRole("heading", { name: "조직 관리", exact: true }),
  ).toBeVisible();
  await page
    .getByRole("row")
    .filter({ has: page.getByRole("cell", { name: /조직 가나재단$/ }) })
    .getByRole("button", { name: "펼치기" })
    .click();
  await expect(
    page.getByRole("cell", { name: /프로그램 여름 학교$/ }),
  ).toBeVisible();

  await page
    .getByRole("row")
    .filter({ has: page.getByRole("cell", { name: /조직 가나재단$/ }) })
    .getByRole("button", { name: "편집" })
    .click();
  await expect(page.getByRole("combobox", { name: "상위 조직" })).toHaveCount(
    0,
  );
  await page.getByLabel("조직 이름", { exact: true }).fill("가나재단 본부");
  await page.getByRole("button", { name: "완료" }).click();
  await expect(page.getByText("저장했어요.")).toBeVisible();
  expect(writes).toEqual([{ name: "가나재단 본부", description: "" }]);
});
