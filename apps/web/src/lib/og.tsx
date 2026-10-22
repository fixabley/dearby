import { readFile } from "node:fs/promises";
import { join } from "node:path";
import { ImageResponse } from "next/og";
import { upstreamFetch } from "./guest-upstream";
import { PREVIEW_REVALIDATE } from "./preview";

// 미리보기 이미지 (contract "유니버설 링크·App Links·활동 공유·오픈그래프").
export const OG_SIZE = { width: 1200, height: 630 };

// next/og reads only ttf/otf/woff, so one static Pretendard weight lives in `assets/fonts` (the web serves woff2 subsets).
// Read on use: every page bundles the root image module for its metadata.
const font = () => readFile(join(process.cwd(), "assets/fonts/Pretendard-SemiBold.otf"));
/** App icon as a data URL (the root image renders at build time, with no origin to fetch from). */
export async function appIcon() {
  return `data:image/png;base64,${(await readFile(join(process.cwd(), "public/icons/icon-512.png"))).toString("base64")}`;
}

/** The program's nearest logo (`/v1/programs|organizations/{id}/logo?v=…`) as a data URL, or undefined. */
export async function logoDataUrl(logoUrl: string | null | undefined): Promise<string | undefined> {
  const match = logoUrl?.match(/^\/v1\/((?:programs|organizations)\/[1-9]\d{0,17}\/logo)\?v=(\d{1,18})$/);
  if (!match) return;
  try {
    const response = await upstreamFetch(match[1], {
      query: new URLSearchParams({ v: match[2] }),
      accept: "image/*",
      revalidate: PREVIEW_REVALIDATE,
    });
    const type = response.headers.get("content-type") ?? "";
    if (!response.ok || !/^image\/(png|jpeg|webp)$/.test(type)) return;
    return `data:${type};base64,${Buffer.from(await response.arrayBuffer()).toString("base64")}`;
  } catch {
    return;
  }
}

/** 1200×630 PNG on a light background in Pretendard. */
export async function ogImage(children: React.ReactNode) {
  return new ImageResponse(
    (
      <div
        style={{
          width: "100%",
          height: "100%",
          display: "flex",
          flexDirection: "column",
          background: "#ffffff",
          color: "#000000",
          fontFamily: "Pretendard",
        }}
      >
        {children}
      </div>
    ),
    { ...OG_SIZE, fonts: [{ name: "Pretendard", data: await font(), weight: 600 }] },
  );
}

/** Default preview: the app icon and `Dearby`. */
export async function defaultOgImage() {
  return ogImage(
    <div style={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "center", gap: 40 }}>
      {/* eslint-disable-next-line @next/next/no-img-element -- next/og renders plain <img> */}
      <img src={await appIcon()} width={200} height={200} style={{ borderRadius: 44 }} alt="" />
      <span style={{ fontSize: 120, color: "#007f80" }}>Dearby</span>
    </div>,
  );
}
