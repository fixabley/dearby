import { programs, organizations } from "../catalog/data";
export type Saved = { programs: string[]; organizations: string[] };
export const blankSaved: Saved = { programs: [], organizations: [] };
export const storageKey = "dearby:saved:v1";
export function parseSaved(raw: string | null): Saved {
  if (raw === null) return blankSaved;
  const value: unknown = JSON.parse(raw);
  if (
    !value ||
    typeof value !== "object" ||
    !("programs" in value) ||
    !("organizations" in value)
  )
    throw new Error("Invalid saved data");
  const v = value as Saved;
  if (
    !Array.isArray(v.programs) ||
    !Array.isArray(v.organizations) ||
    ![...v.programs, ...v.organizations].every((x) => typeof x === "string")
  )
    throw new Error("Invalid saved data");
  return {
    programs: [...new Set(v.programs)].filter((id) =>
      programs.some((p) => p.id === id),
    ),
    organizations: [...new Set(v.organizations)].filter((id) =>
      organizations.some((o) => o.id === id),
    ),
  };
}
