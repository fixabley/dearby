import Link from "next/link";
import { cn } from "@/shared/lib/utils";
import { Badge } from "@/shared/ui/badge";

/** Recruitment state the chip shows (contract "발견 카드 리뉴얼"): 모집 중 teal fill / 모집 예정 teal tint / 마감 system fill. */
export type StatusTone = "open" | "soon" | "closed";
const statusText: Record<StatusTone, string> = { open: "모집 중", soon: "모집 예정", closed: "마감" };
const statusVariant = { open: "filled", soon: "tinted", closed: "neutral" } as const;

/**
 * 펼친 프로그램 안의 활동 카드 한 장 (contract "발견 카드 리뉴얼 — 2026-10-19", iOS `ActivityCard`): grouped-background tile inside
 * the program card, 활동명 and the status capsule on top, then `모집`·`활동`·`장소` label/value rows. While recruitment is not past
 * (`recruitmentActive`) the `모집` value is teal semibold on a mint band. The whole card links to the detail.
 */
export function ActivityCard({
  id,
  name,
  statusTone,
  recruitmentLabel,
  recruitmentActive,
  activityLabel,
  placeName,
}: {
  id: number;
  name: string;
  statusTone: StatusTone;
  /** 모집 기간 문구; 없으면 `미정`. */
  recruitmentLabel: string;
  /** 모집 중·모집 예정이고 기간이 있을 때 참 → 강조. */
  recruitmentActive: boolean;
  /** 활동 기간 문구. */
  activityLabel: string;
  /** 장소 이름; 없으면 줄을 생략한다. */
  placeName?: string | null;
}) {
  const rows: [string, string, boolean][] = [
    ["모집", recruitmentLabel || "미정", recruitmentActive],
    ["활동", activityLabel || "일정 미확인", false],
  ];
  if (placeName) rows.push(["장소", placeName, false]);
  return (
    <Link
      href={`/activities/${id}`}
      className="flex flex-col gap-2 rounded-[14px] bg-background px-3 pt-3 pb-2.5 transition-opacity active:opacity-70"
    >
      <span className="flex items-start gap-2.5">
        <h3 className="flex-1 text-subheadline font-semibold break-keep">{name}</h3>
        <Badge variant={statusVariant[statusTone]} className="text-caption2">
          {statusText[statusTone]}
        </Badge>
      </span>
      <dl className="flex flex-col gap-[3px] text-footnote">
        {rows.map(([label, value, emphasized]) => (
          <div
            key={label}
            className={cn("flex items-baseline gap-2", emphasized && "-mx-1.5 rounded-lg bg-mint px-1.5 py-[3px]")}
          >
            <dt className="w-9 shrink-0 text-secondary-label">{label}</dt>
            <dd className={cn("flex-1 break-keep", emphasized && "font-semibold text-teal")}>{value}</dd>
          </div>
        ))}
      </dl>
    </Link>
  );
}
