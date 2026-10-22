import { activitySchema, type Activity } from "./models";
import { upstreamFetch } from "./guest-upstream";

// 오픈그래프 (contract "유니버설 링크·App Links·활동 공유·오픈그래프"): metadataBase is the fixed web origin.
export const WEB_ORIGIN = new URL("https://dearby.wid.io.kr");
/** A page's `openGraph` replaces the root one wholesale, so every page that sets it spreads this. */
export const siteOpenGraph = { siteName: "Dearby", locale: "ko_KR", type: "website" } as const;
/** Activity previews are cached for an hour, the metadata and the image alike. */
export const PREVIEW_REVALIDATE = 3600;

/** Published activity for the preview, or undefined on any failure (→ default metadata and image). */
export async function previewActivity(id: number): Promise<Activity | undefined> {
  try {
    const response = await upstreamFetch(`activities/${id}`, { revalidate: PREVIEW_REVALIDATE });
    if (!response.ok) return;
    const parsed = activitySchema.safeParse(await response.json());
    return parsed.success ? parsed.data : undefined;
  } catch {
    return;
  }
}
