import { cn } from "@/shared/lib/utils";
import { Card } from "./card";

/**
 * iOS inset-grouped section: a small secondary header, the rows on one rounded card split by hairlines, an optional footnote.
 * Children are the rows (`<li>`); `plain` takes free content instead of a list.
 */
export function InsetGroup({
  title,
  trailing,
  footer,
  plain = false,
  className,
  children,
}: {
  title?: string;
  /** Right end of the header line (a status badge, contract "진행 단계 화면 정리"). */
  trailing?: React.ReactNode;
  footer?: React.ReactNode;
  plain?: boolean;
  className?: string;
  children: React.ReactNode;
}) {
  return (
    <section className={cn("mt-7", className)}>
      {title && (
        <div className="mb-1.5 flex items-center justify-between gap-3 px-4">
          <h2 className="text-footnote font-semibold text-secondary-label">{title}</h2>
          {trailing}
        </div>
      )}
      <Card>
        {plain ? (
          <div className="px-4 py-3">{children}</div>
        ) : (
          <ul className="divide-y divide-separator [&>li]:min-h-11">{children}</ul>
        )}
      </Card>
      {footer && <p className="mt-1.5 px-4 text-footnote text-secondary-label">{footer}</p>}
    </section>
  );
}
