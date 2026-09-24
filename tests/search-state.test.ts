import { test } from "node:test";
import assert from "node:assert/strict";
import { readFilters, filtersUrl } from "../src/features/catalog/search-state";
import { emptyFilters } from "../src/features/catalog/model";
test("URL retains filters and validates unknown values", () => {
  const f = {
    ...emptyFilters,
    category: "연합동아리",
    query: "서비스",
    roles: ["iOS"],
    experiences: ["mentor", "app"],
    allRoles: true,
    priority: "app",
    openOnly: true,
  };
  assert.deepEqual(
    readFilters(new URL(filtersUrl(f, ""), "http://localhost").searchParams),
    f,
  );
  assert.deepEqual(
    readFilters(
      new URLSearchParams("category=unknown&roles=unknown&experiences=toString&priority=app"),
    ),
    emptyFilters,
  );
  assert.equal(
    new URL(
      filtersUrl(emptyFilters, "view=open"),
      "http://localhost",
    ).searchParams.has("view"),
    false,
  );
});
