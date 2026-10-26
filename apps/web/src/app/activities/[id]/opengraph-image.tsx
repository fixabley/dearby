import { activityId, recruitmentDate } from "@/lib/models";
import { appIcon, defaultOgImage, logoDataUrl, OG_SIZE, ogImage } from "@/lib/og";
import { previewActivity } from "@/lib/preview";
// 활동별 미리보기 이미지 (contract "유니버설 링크·App Links·활동 공유·오픈그래프"): 프로그램 로고(있으면), 활동 이름, 프로그램·주최,
// 모집 기간. A failed or missing activity gets the default image.
export const revalidate = 3600;
export const alt = "Dearby 활동";
export const size = OG_SIZE;
export const contentType = "image/png";
export default async function Image({ params }: { params: Promise<{ id: string }> }) {
  const id = activityId((await params).id);
  const activity = id === undefined ? undefined : await previewActivity(id);
  if (!activity) return defaultOgImage();
  const logo = await logoDataUrl(activity.program.logoUrl);
  // 모집 날짜 표기 (contract "모집 날짜 표기 — 2026-10-26") without the D-day: the image is cached for an hour and shared for longer.
  const period = recruitmentDate(activity.application).text;
  return ogImage(
    <div style={{ flex: 1, display: "flex", flexDirection: "column", padding: "64px 80px" }}>
      <div style={{ display: "flex", alignItems: "center", gap: 16 }}>
        <img src={await appIcon()} width={56} height={56} style={{ borderRadius: 14 }} alt="" />
        <span style={{ fontSize: 32, color: "#007f80" }}>Dearby</span>
      </div>
      <div style={{ flex: 1, display: "flex", flexDirection: "column", justifyContent: "center", gap: 20 }}>
        {logo && <img src={logo} width={112} height={112} style={{ borderRadius: 28, objectFit: "cover" }} alt="" />}
        <div style={{ fontSize: 68, lineHeight: 1.2, lineClamp: 2 }}>{activity.name}</div>
        <div style={{ fontSize: 34, color: "#3c3c4399" }}>
          {`${activity.program.name} · ${activity.organization.name}`}
        </div>
      </div>
      {period && (
        <div style={{ display: "flex", alignSelf: "flex-start", gap: 20, fontSize: 32, padding: "14px 24px", borderRadius: 16, background: "#e6f2f2", color: "#007f80" }}>
          <span>모집 기간</span>
          <span>{period}</span>
        </div>
      )}
    </div>,
  );
}
