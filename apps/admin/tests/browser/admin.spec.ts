import { test, expect } from "@playwright/test";
import { readFileSync } from "node:fs";
const login = JSON.parse(
  readFileSync(
    new URL("../../../../supabase/.env.credentials.json", import.meta.url),
    "utf8",
  ),
);
const key = Date.now().toString();
const organization = `[로컬 테스트] 조직 ${key}`,
  program = `[로컬 테스트] 프로그램 ${key}`,
  activity = `[로컬 테스트] 활동 ${key}`;
test("admin can create, edit, verify, publish and hide an activity through real Supabase", async ({
  page,
  request,
}) => {
  await page.goto("/");
  await page.getByLabel("이메일", { exact: true }).fill(login.email);
  await page.getByLabel("비밀번호", { exact: true }).fill(login.password);
  await page.getByRole("button", { name: "관리자로 로그인" }).click();
  await expect(
    page.getByRole("heading", { name: "활동 관리", exact: true }),
  ).toBeVisible();
  await expect(page.locator(".ant-spin-spinning")).toHaveCount(0);
  await page.screenshot({
    path: "../../apps/admin/docs/evidence/activities.png",
    fullPage: true,
  });
  await page.getByRole("link", { name: "조직", exact: true }).click();
  await page.getByRole("button", { name: "조직 만들기", exact: true }).click();
  await page.getByLabel("조직 이름", { exact: true }).fill(organization);
  await page
    .getByLabel("소개", { exact: true })
    .fill("사용자 운영을 검증하는 로컬 예시입니다.");
  await page.getByRole("button", { name: "저장", exact: true }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await page.getByRole("link", { name: "프로그램", exact: true }).click();
  await page
    .getByRole("button", { name: "프로그램 만들기", exact: true })
    .click();
  await page.getByLabel("프로그램 이름", { exact: true }).fill(program);
  await page.getByLabel("운영 조직", { exact: true }).fill(organization);
  await page.getByRole("option", { name: organization, exact: true }).click();
  await page.getByRole("button", { name: "저장", exact: true }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await page.getByRole("link", { name: "활동", exact: true }).click();
  await page.getByRole("button", { name: "활동 만들기" }).click();
  await page.getByLabel("활동 제목", { exact: true }).fill(activity);
  await page.getByLabel("프로그램", { exact: true }).fill(program);
  await page.getByRole("option", { name: program, exact: true }).click();
  await page
    .getByLabel("소개", { exact: true })
    .fill("실제 행사가 아닌 로컬 어드민 연결 검증용 활동입니다.");
  await page
    .getByLabel("공식 안내 URL", { exact: true })
    .fill("https://example.com/dearby-admin-local-test");
  await page.getByLabel("모집 상태", { exact: true }).press("ArrowDown");
  await page.getByRole("option", { name: "모집 중", exact: true }).click();
  await page
    .getByLabel("일정 안내 문구", { exact: true })
    .fill("2026년 10월 24일 · 로컬 예시");
  await page.getByRole("button", { name: "일정 추가", exact: true }).click();
  await page.getByLabel("일정 제목", { exact: true }).fill("로컬 데모 세션");
  await page.getByLabel("시작", { exact: true }).fill("2026-10-24T14:00");
  await page.getByLabel("종료", { exact: true }).fill("2026-10-24T16:00");
  // Typed JSON editor: scalar, range, dates, booleans, null and nested objects/arrays.
  async function kind(label: string, type: string) {
    const combo=page.getByRole("combobox",{name:`${label} 값 형식`,exact:true});
    await combo.press("ArrowDown");
    const list=await combo.getAttribute("aria-controls");
    await page.locator(`[id="${list}"]`).getByRole("option",{name:type,exact:true}).click();
  }
  await page
    .getByRole("button", { name: "지원 자격 항목 추가", exact: true })
    .click();
  await page
    .getByRole("combobox", { name: "지원 자격 1 항목 이름", exact: true })
    .fill("연차");
  await kind("지원 자격 1", "숫자");
  await page.getByLabel("지원 자격 1 값", { exact: true }).fill("3");
  await page
    .getByRole("button", { name: "지원 자격 항목 추가", exact: true })
    .click();
  await page
    .getByRole("combobox", { name: "지원 자격 2 항목 이름", exact: true })
    .fill("경력");
  await kind("지원 자격 2", "범위 객체 만들기");
  await page.getByLabel("지원 자격 경력 1 값", { exact: true }).fill("3");
  await page.getByLabel("지원 자격 경력 2 값", { exact: true }).fill("4");
  await page
    .getByRole("button", { name: "지원 자격 항목 추가", exact: true })
    .click();
  await page
    .getByRole("combobox", { name: "지원 자격 3 항목 이름", exact: true })
    .fill("입사일");
  await kind("지원 자격 3", "날짜");
  await page.getByLabel("지원 자격 3 값", { exact: true }).fill("2026-10-01");
  await page
    .getByRole("button", { name: "참가 대상 항목 추가", exact: true })
    .click();
  await page
    .getByRole("combobox", { name: "참가 대상 1 항목 이름", exact: true })
    .fill("재직");
  await kind("참가 대상 1", "참/거짓");
  await page
    .getByRole("switch", { name: "참가 대상 1 값", exact: true })
    .click();
  await page
    .getByRole("button", { name: "모집 역할 항목 추가", exact: true })
    .click();
  await page
    .getByRole("combobox", { name: "모집 역할 1 항목 이름", exact: true })
    .fill("기술");
  await kind("모집 역할 1", "객체");
  await page
    .getByRole("button", { name: "기술 항목 추가", exact: true })
    .click();
  await page
    .getByRole("combobox", { name: "모집 역할 기술 1 항목 이름", exact: true })
    .fill("언어");
  await kind("모집 역할 기술 1", "문자열 배열");
  await page
    .getByRole("combobox", { name: "모집 역할 기술 1 값", exact: true })
    .fill("TypeScript");
  await page
    .getByRole("combobox", { name: "모집 역할 기술 1 값", exact: true })
    .press("Enter");
  // Tags mode keeps its popup open for another value; dismiss it before clicking below.
  await page
    .getByRole("combobox", { name: "모집 역할 기술 1 값", exact: true })
    .press("Escape");
  await page
    .getByRole("button", { name: "기술 항목 추가", exact: true })
    .click();
  await page
    .getByRole("combobox", { name: "모집 역할 기술 2 항목 이름", exact: true })
    .fill("미정");
  await kind("모집 역할 기술 2", "null");
  await page.getByRole("button", { name: "저장", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "활동 편집", exact: true }),
  ).toBeVisible();
  await expect(
    page.getByText("활동을 저장했어요.", { exact: true }),
  ).toBeVisible();
  await page
    .getByRole("button", { name: "공식 정보 확인 기록", exact: true })
    .click();
  await page
    .getByLabel("내부 확인 근거 (방문자에게 보이지 않음)", { exact: true })
    .fill(
      "실제 공식 모집이 아닌 로컬 테스트입니다. 어드민과 API의 상태 연결만 검증합니다.",
    );
  await page.getByRole("button", { name: "확인 기록", exact: true }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await page.getByLabel("게시 상태", { exact: true }).press("ArrowDown");
  await page.getByRole("option", { name: "게시", exact: true }).click();
  await page.getByRole("button", { name: "저장", exact: true }).click();
  await expect(page.getByText("탐색 노출", { exact: true })).toBeVisible();
  const response = await request.get("http://127.0.0.1:58765/v1/catalog");
  expect(response.status()).toBe(200);
  const item = (await response.json()).activities.find(
    (a: { title: string }) => a.title === activity,
  );
  expect(item.isRecruiting).toBe(true);
  expect(item.schedules).toHaveLength(1);
  expect(item.qualification).toContain("연차: 3");
  expect(item.qualification).toContain("경력: 3 ~ 4");
  expect(item.roles.join(" ")).toContain("TypeScript");
  await page.reload();
  const preview = page.locator(".criteria-preview pre");
  await expect(preview).toContainText('"연차": 3');
  await expect(preview).toContainText('"미정": null');
  await expect(preview).toContainText('"재직": true');
  // Reuse a saved nested key from autocomplete without saving a duplicate.
  await page
    .getByRole("button", { name: "기술 항목 추가", exact: true })
    .click();
  await page
    .getByRole("combobox", { name: "모집 역할 기술 3 항목 이름", exact: true })
    .fill("언");
  await page.getByRole("option", { name: "언어 · 배열", exact: true }).click();
  await expect(
    page.getByRole("combobox", {
      name: "모집 역할 기술 3 항목 이름",
      exact: true,
    }),
  ).toHaveValue("언어");
  await page
    .getByRole("button", { name: "모집 역할 기술 3 제거", exact: true })
    .click();
  await page.getByRole("button", { name: "저장", exact: true }).click();

  await page.screenshot({
    path: "../../apps/admin/docs/evidence/activity-editor.png",
    fullPage: true,
  });
  await page
    .getByLabel("소개", { exact: true })
    .fill("내용 변경으로 공식 확인이 해제되는 로컬 테스트입니다.");
  await page.getByRole("button", { name: "저장", exact: true }).click();
  await expect(page.getByText("재확인 필요", { exact: true })).toBeVisible();
  await page.getByLabel("게시 상태", { exact: true }).press("ArrowDown");
  await page.getByRole("option", { name: "숨김", exact: true }).click();
  await page.getByRole("button", { name: "저장", exact: true }).click();
  await expect
    .poll(async () =>
      (
        await (await request.get("http://127.0.0.1:58765/v1/catalog")).json()
      ).activities.some((a: { title: string }) => a.title === activity),
    )
    .toBe(false);
  // A stale editor must not overwrite another admin's successful write.
  await page
    .getByLabel("소개", { exact: true })
    .fill("아직 저장하지 않은 내 수정입니다.");
  const token = await page.evaluate(() => {
    for (const key of Object.keys(localStorage)) {
      try {
        const value = JSON.parse(localStorage.getItem(key) || "{}");
        if (value.access_token) return value.access_token as string;
      } catch {}
    }
    return "";
  });
  const settings = readFileSync(
    new URL("../../.env.local", import.meta.url),
    "utf8",
  );
  const publicKey = settings.match(/^VITE_SUPABASE_ANON_KEY=(.+)$/m)![1];
  const recordId = page.url().split("/").pop();
  const external = await request.patch(
    `http://127.0.0.1:54321/rest/v1/catalog_activities?id=eq.${recordId}`,
    {
      headers: { apikey: publicKey, Authorization: `Bearer ${token}` },
      data: { summary: "다른 관리자가 먼저 저장한 내용입니다." },
    },
  );
  expect(external.status()).toBeLessThan(300);
  await page.getByRole("button", { name: "저장", exact: true }).click();
  await expect(
    page.getByText(
      "다른 관리자가 활동을 수정했거나 권한이 변경됐어요. 입력 내용을 복사한 뒤 새로고침해 주세요.",
      { exact: true },
    ),
  ).toBeVisible();
  await expect(page.getByLabel("소개", { exact: true })).toHaveValue(
    "아직 저장하지 않은 내 수정입니다.",
  );
  page.once("dialog", (dialog) => dialog.accept());
  await page.reload();
  await expect(page.getByLabel("소개", { exact: true })).toHaveValue(
    "다른 관리자가 먼저 저장한 내용입니다.",
  );
  await page.getByRole("link", { name: "변경 기록", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "변경 기록", exact: true }),
  ).toBeVisible();
  await page.screenshot({
    path: "../../apps/admin/docs/evidence/audit.png",
    fullPage: true,
  });
  await page.getByRole("button", { name: "로그아웃", exact: true }).click();
  await expect(
    page.getByRole("button", { name: "관리자로 로그인" }),
  ).toBeVisible();
});

test("narrow screen keeps navigation and logout reachable", async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/");
  await page.getByLabel("이메일", { exact: true }).fill(login.email);
  await page.getByLabel("비밀번호", { exact: true }).fill(login.password);
  await page.getByRole("button", { name: "관리자로 로그인" }).click();
  await expect(
    page.getByRole("heading", { name: "활동 관리", exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "로그아웃", exact: true }),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
  await expect(page.locator(".ant-spin-spinning")).toHaveCount(0);
  await page.screenshot({
    path: "../../apps/admin/docs/evidence/mobile.png",
    fullPage: true,
  });
});
