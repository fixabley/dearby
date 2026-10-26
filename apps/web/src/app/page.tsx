import { Discovery } from "@/components/discovery";
export default async function Page({
  searchParams,
}: {
  searchParams: Promise<{ [key: string]: string | string[] | undefined }>;
}) {
  const { q, kind } = await searchParams;
  const query = (typeof q === "string" ? q : "").trim().slice(0, 100);
  // 프로그램 | 이벤트 (`?kind=event`); anything else is 프로그램.
  const shown = kind === "event" ? "event" : "program";
  return <Discovery key={`${shown}:${query}`} q={query} kind={shown} />;
}
