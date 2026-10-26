import Link from "next/link";
import type { ReactNode } from "react";
import { cn } from "@/shared/lib/utils";
import { Badge } from "@/shared/ui/badge";
import { Skeleton } from "@/shared/ui/skeleton";
import { TagChipRow } from "@/shared/ui/tag-icon";
import { type ActivityStatus, type Tag, STATUS_BADGE } from "@/lib/models";

/**
 * 활동 상태 capsule (contract "활동 상태" 2026-10-26): 모집 중 teal fill / 모집 예정 teal tint / 진행 중 system blue tint / 모집 마감·종료
 * system fill; 모집 정보 없음 has none.
 */
export const statusVariant = {
  RECRUITING: "filled",
  RECRUITING_UPCOMING: "tinted",
  IN_PROGRESS: "blue",
  RECRUITMENT_CLOSED: "neutral",
  NO_RECRUITMENT: "neutral",
  ENDED: "neutral",
} as const satisfies Record<ActivityStatus, string>;

/**
 * 펼친 프로그램 안의 활동 카드 한 장 (contract "발견 카드 리뉴얼 — 2026-10-19", iOS `ActivityCard`): grouped-background tile inside
 * the program card, 활동명 and the status capsule on top, the tag chip line (분야 1개 + 얻을 수 있는 것), then `모집`·`활동`·`장소` label/value rows. While recruitment is not past
 * (`recruitmentActive`) the `모집` value is teal semibold on a mint band. The whole card links to the detail through the name's
 * stretched link, so the `favorite` slot (the heart, contract "즐겨찾기(하트)") can be a button beside the capsules without nesting
 * it in the anchor.
 */
export function ActivityCard({
  id,
  name,
  subtitle,
  status,
  recruitmentLabel,
  recruitmentDday,
  startDday,
  recruitmentActive,
  activityLabel,
  placeName,
  fieldTag,
  traitTags = [],
  favorite,
}: {
  id: number;
  name: string;
  /** A line under the name (the program on `/favorites`, where cards are not grouped by program). */
  subtitle?: string;
  status: ActivityStatus;
  /** 모집 기간 문구; 없으면(모집 정보 없음 포함) `모집 일정 미정`. */
  recruitmentLabel: string;
  /** `D-3`·`D-DAY` beside the period while 모집 중 (contract "모집 날짜 표기 — 2026-10-26"). */
  recruitmentDday?: string;
  /** `시작 D-5`·`오늘 시작` beside the `모집 예정` capsule (contract "모집 예정 시작 D-day — 2026-10-26"). */
  startDday?: string;
  /** 모집 중·모집 예정이고 기간이 있을 때 참 → 강조. */
  recruitmentActive: boolean;
  /** 활동 기간 문구. */
  activityLabel: string;
  /** 장소 이름; 없으면 줄을 생략한다. */
  placeName?: string | null;
  /** Tag chip line under the name (contract "태그 아이콘+라벨·분야 1개·자동 펼침 없음"): the first 분야, then 얻을 수 있는 것. */
  fieldTag?: Tag;
  traitTags?: Tag[];
  /** The heart (a button), rendered above the stretched link. */
  favorite?: ReactNode;
}) {
  const rows: [string, string, boolean, string?][] = [
    ["모집", recruitmentLabel || "모집 일정 미정", recruitmentActive, recruitmentDday],
    ["활동", activityLabel || "일정 미확인", false],
  ];
  if (placeName) rows.push(["장소", placeName, false]);
  return (
    <div className="relative flex flex-col gap-2 rounded-[14px] bg-background px-3 pt-3 pb-2.5 transition-opacity has-[a:active]:opacity-70">
      <span className="flex items-start gap-2.5">
        <span className="flex min-w-0 flex-1 flex-col gap-0.5">
          <h3 className="text-subheadline font-semibold break-keep">
            <Link href={`/activities/${id}`} className="after:absolute after:inset-0 after:rounded-[14px] after:content-['']">
              {name}
            </Link>
          </h3>
          {subtitle && <span className="text-footnote text-secondary-label break-keep">{subtitle}</span>}
        </span>
        {/* 시작 D-n left of the 모집 예정 capsule (contract "모집 예정 시작 D-day"; the detail puts it right of the status badge). */}
        {startDday && (
          <Badge variant="tinted" className="text-caption2 tabular-nums">
            {startDday}
          </Badge>
        )}
        {STATUS_BADGE[status] && (
          <Badge variant={statusVariant[status]} className="text-caption2">
            {STATUS_BADGE[status]}
          </Badge>
        )}
        {favorite && <span className="relative z-10 -my-1.5 flex">{favorite}</span>}
      </span>
      <TagChipRow field={fieldTag} traits={traitTags} />
      <dl className="flex flex-col gap-[3px] text-footnote">
        {rows.map(([label, value, emphasized, badge]) => (
          <div
            key={label}
            className={cn("flex items-baseline gap-2", emphasized && "-mx-1.5 rounded-lg bg-mint px-1.5 py-[3px]")}
          >
            <dt className="w-9 shrink-0 text-secondary-label">{label}</dt>
            <dd className={cn("flex-1 break-keep", emphasized && "font-semibold text-teal")}>
              {value}
              {badge && (
                <Badge variant="filled" className="ml-1.5 align-[1px] text-caption2 tabular-nums">
                  {badge}
                </Badge>
              )}
            </dd>
          </div>
        ))}
      </dl>
    </div>
  );
}

/** Loading placeholder in the ActivityCard's shape: name line and status capsule, then the `모집`·`활동` rows. */
export function ActivityCardSkeleton() {
  return (
    <div className="flex flex-col gap-2 rounded-[14px] bg-background px-3 pt-3 pb-2.5">
      <span className="flex items-start gap-2.5">
        <Skeleton className="h-4 flex-1" />
        <Skeleton className="h-5 w-12 rounded-full" />
      </span>
      <Skeleton className="h-3.5 w-3/4" />
      <Skeleton className="h-3.5 w-1/2" />
    </div>
  );
}
