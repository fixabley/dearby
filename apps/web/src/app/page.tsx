import { Discovery } from "@/components/discovery";
import { parseStatusGroups } from "@/lib/models";
import { parseCategories } from "@/lib/category";
export default async function Page({
  searchParams,
}: {
  searchParams: Promise<{ [key: string]: string | string[] | undefined }>;
}) {
  const { q, kind, status, category } = await searchParams;
  const query = (typeof q === "string" ? q : "").trim().slice(0, 100);
  // 프로그램 | 이벤트 (`?kind=event`); anything else is 프로그램.
  const shown = kind === "event" ? "event" : "program";
  // 활동 상태 묶음 (`?status=available,past`); none or unknown is 지원 가능 (contract "활동 상태").
  const groups = parseStatusGroups(typeof status === "string" ? status : undefined);
  // 종류 필터 (`?category=BOOTCAMP,CLUB`, contract "종류 필터"); only this side's codes, none is 전체.
  const categories = parseCategories(typeof category === "string" ? category : undefined, shown);
  return <Discovery key={`${shown}:${query}`} q={query} kind={shown} groups={groups} categories={categories} />;
}
