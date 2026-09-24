import { emptyFilters, experiences, roles, type Filters } from "./model";
export function readFilters(params: Pick<URLSearchParams, "get">): Filters {
  const selectedRoles = [
    ...new Set((params.get("roles") || "").split(",")),
  ].filter((r) => roles.some((known) => known === r));
  const selectedExperiences = [
    ...new Set((params.get("experiences") || "").split(",")),
  ].filter((e) => Object.hasOwn(experiences, e));
  const priority = params.get("priority") || "";
  return {
    ...emptyFilters,
    category: ["컨퍼런스", "연합동아리"].includes(params.get("category") || "") ? params.get("category")! : "",
    query: params.get("q") || "",
    roles: selectedRoles,
    experiences: selectedExperiences,
    allRoles: params.get("all") === "1",
    openOnly: params.get("view") === "open" || params.get("open") === "1",
    priority: selectedExperiences.includes(priority) ? priority : "",
  };
}
export function filtersUrl(filters: Filters, current: string) {
  const params = new URLSearchParams(current);
  for (const key of ["category", "q", "roles", "experiences", "all", "open", "priority"])
    params.delete(key);
  if (params.get("view") === "open" && !filters.openOnly) params.delete("view");
  if (filters.category) params.set("category", filters.category);
  if (filters.query) params.set("q", filters.query);
  if (filters.roles.length) params.set("roles", filters.roles.join(","));
  if (filters.experiences.length)
    params.set("experiences", filters.experiences.join(","));
  if (filters.allRoles) params.set("all", "1");
  if (filters.openOnly && params.get("view") !== "open")
    params.set("open", "1");
  if (filters.priority) params.set("priority", filters.priority);
  return "/" + (params.size ? "?" + params.toString() : "");
}
