import Link from "next/link";
import { cn } from "@/shared/lib/utils";
import { Button } from "./button";
import { Icon, type IconName } from "./icon";

/** A link or a button; server callers pass `href` only (callbacks cannot cross the server boundary). */
export type MessageAction = { label: string; disabled?: boolean } & ({ href: string } | { onClick: () => void });

/**
 * Single-message screen (contract "비스크롤 화면 배치·CTA", web "웹에도 같은 규칙"): 64px teal circle with a 28px white glyph
 * (`danger` circle for errors), 22/700 title, 17px secondary message (max three lines), full-width primary CTA and a text secondary
 * action, centred on the background without a card. Page-level messages use `level={1}`; messages under a page heading keep `h2`.
 * `compact` is the variant inside an expanded program card.
 */
export function CenteredMessage({
  icon,
  tone = "teal",
  title,
  message,
  primary,
  secondary,
  level = 2,
  role,
  compact = false,
}: {
  icon: IconName;
  tone?: "teal" | "danger";
  title: string;
  message?: string;
  primary?: MessageAction;
  secondary?: MessageAction;
  level?: 1 | 2;
  /** `alert` for failures that interrupt, `status` for outcomes announced politely. */
  role?: "alert" | "status";
  compact?: boolean;
}) {
  const Heading = level === 1 ? "h1" : "h2";
  return (
    <div
      role={role}
      className={cn(
        "mx-auto flex w-full max-w-[420px] flex-col items-center text-center",
        compact ? "gap-2 px-2 py-6" : "min-h-[55dvh] justify-center gap-3 px-6 py-10",
      )}
    >
      <span
        className={cn(
          "mb-2 grid place-items-center rounded-full text-on-teal",
          compact ? "size-12" : "size-16",
          tone === "danger" ? "bg-danger" : "bg-teal-fill",
        )}
      >
        <Icon name={icon} size="state" />
      </span>
      <Heading className={cn("font-bold break-keep", compact ? "text-headline" : "text-title2")}>{title}</Heading>
      {message && (
        <p className={cn("line-clamp-3 break-keep text-secondary-label", compact ? "text-subheadline" : "text-body")}>
          {message}
        </p>
      )}
      {(primary || secondary) && (
        <div className="mt-5 flex w-full flex-col gap-2">
          {primary && <Action action={primary} variant="filled" />}
          {secondary && <Action action={secondary} variant="plain" />}
        </div>
      )}
    </div>
  );
}

function Action({ action, variant }: { action: MessageAction; variant: "filled" | "plain" }) {
  if ("href" in action)
    return (
      <Button asChild variant={variant} size="lg">
        <Link href={action.href}>{action.label}</Link>
      </Button>
    );
  return (
    <Button type="button" variant={variant} size="lg" disabled={action.disabled} onClick={action.onClick}>
      {action.label}
    </Button>
  );
}
