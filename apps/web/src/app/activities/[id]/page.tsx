import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { activityId, recruitmentPhrase } from "@/lib/models";
import { previewActivity, siteOpenGraph } from "@/lib/preview";
import { ActivityDetail } from "@/components/activity-detail";
// Shared links preview from the server for an hour (contract "유니버설 링크·App Links·활동 공유·오픈그래프").
export const revalidate = 3600;
/** `og:title` = 활동 이름, `og:description` = `<프로그램> · <모집 문구>[ · <장소>]`; any failure keeps the default metadata. */
export async function generateMetadata({ params }: { params: Promise<{ id: string }> }): Promise<Metadata> {
  const id = activityId((await params).id);
  const activity = id === undefined ? undefined : await previewActivity(id);
  if (!activity) return { title: "활동 상세" };
  const place = activity.place?.name ?? activity.location;
  const preview = {
    title: activity.name,
    description: [activity.program.name, recruitmentPhrase(activity.application), place].filter(Boolean).join(" · "),
  };
  return {
    ...preview,
    openGraph: { ...siteOpenGraph, ...preview },
    twitter: { card: "summary_large_image", ...preview },
  };
}
export default async function Page({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const id = activityId((await params).id);
  if (id === undefined) notFound();
  return <ActivityDetail key={id} id={id} />;
}
