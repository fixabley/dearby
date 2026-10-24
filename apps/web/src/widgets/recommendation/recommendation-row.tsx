import Link from "next/link";
import { type Recommendation, fieldTagsOf, programLogoSrc, traitTagsOf } from "@/lib/models";
import { Badge } from "@/shared/ui/badge";
import { TagIcon } from "@/shared/ui/tag-icon";
import { ProgramAvatar } from "@/widgets/activity/program-avatar";

/**
 * One recommended activity (contract "발견 탭 노출"): program logo, activity name, program name, and the server's reasons as
 * chips. A reason that names one of the activity's tags carries that tag's icon; the rest (종류, 마감) are text only.
 */
export function RecommendationRow({ item }: { item: Recommendation }) {
  const { activity, program, reasons } = item;
  const tags = [...(activity.fieldTagsAll ?? fieldTagsOf(activity)), ...traitTagsOf(activity)];
  const logoSrc = programLogoSrc(program.logoUrl ?? activity.program.logoUrl);
  return (
    <Link href={`/activities/${activity.id}`} className="flex items-start gap-3 px-4 py-3 active:opacity-70">
      <ProgramAvatar key={logoSrc ?? ""} name={program.name} src={logoSrc} />
      <span className="flex min-w-0 flex-1 flex-col gap-0.5">
        <span className="text-headline font-semibold break-keep">{activity.name}</span>
        <span className="text-subheadline text-secondary-label break-keep">{program.name}</span>
        {reasons.length > 0 && (
          <span className="mt-1.5 flex flex-wrap gap-1.5">
            <span className="sr-only">추천 이유: </span>
            {reasons.map((reason) => {
              const tag = tags.find((tag) => tag.name === reason);
              return (
                <Badge key={reason} variant="tinted">
                  {tag && (
                    <span aria-hidden="true" className="flex">
                      <TagIcon nameEn={tag.nameEn} />
                    </span>
                  )}
                  {reason}
                </Badge>
              );
            })}
          </span>
        )}
      </span>
    </Link>
  );
}
