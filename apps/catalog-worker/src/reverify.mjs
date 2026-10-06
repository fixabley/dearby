import { createHash } from "node:crypto";
import { normalizeText, fetchOfficialPage } from "./source.mjs";
import { plainQuote, structural } from "./collect.mjs";
// No Codex: re-fetch each due published activity and check that the administrator's quote is still there.
// A failure is only recorded; the activity expires on its own at valid_until.
export async function reverifyPublished(rpc, { fetchPage = fetchOfficialPage } = {}) {
  const due = (await rpc("catalog_reverify_candidates")) ?? [];
  const results = [];
  for (const activity of due) {
    let ok = false,
      reason = null,
      sha = null;
    try {
      const page = await fetchPage(activity.officialUrl, activity.hosts);
      sha = createHash("sha256").update(page.html).digest("hex");
      if (page.text.length < 20)
        throw new Error("Official page has no readable text");
      if (!normalizeText(page.text).includes(plainQuote(activity.quote)))
        throw new Error("Official evidence quote could not be confirmed");
      ok = true;
    } catch (error) {
      reason =
        (structural(error.message) ? "BLOCKED: " : "") + error.message;
    }
    const extended = await rpc("reverify_catalog_activity", {
      activity_id: activity.id,
      ok,
      body_sha256: sha,
      reason,
    });
    results.push({ id: activity.id, ok, extended, reason });
  }
  return results;
}
