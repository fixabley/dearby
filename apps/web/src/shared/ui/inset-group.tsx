import { cn } from "@/shared/lib/utils";
import { Card } from "./card";

/**
 * iOS inset-grouped section: a small secondary header, the rows on one rounded card split by hairlines, an optional footnote.
 * Children are the rows (`<li>`); `plain` takes free content instead of a list.
 */
export function InsetGroup({
  title,
  footer,
  plain = false,
  className,
  children,
}: {
  title?: string;
  footer?: React.ReactNode;
  plain?: boolean;
  className?: string;
  children: React.ReactNode;
}) {
  return (
    <section className={cn("mt-7", className)}>
      {title && <h2 className="mb-1.5 px-4 text-footnote font-semibold text-secondary-label">{title}</h2>}
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
