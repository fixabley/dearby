import Link from "next/link";
import { Icon, type IconName } from "./icon";
import styles from "./centered-message.module.css";

/** A link or a button; server callers pass `href` only (callbacks cannot cross the server boundary). */
export type MessageAction = { label: string; disabled?: boolean } & ({ href: string } | { onClick: () => void });

/**
 * Single-message screen (contract "웹에도 같은 규칙", 2026-10-20 → "비스크롤 화면 배치·CTA"): 64px teal circle with a 28px white glyph
 * (`danger` circle for errors), 22/700 title, 17px secondary message, full-width primary CTA and a text secondary action, centred
 * on the background without a card. Page-level messages use `level={1}`; messages under a page heading keep the default `h2`.
 * Inside an expanded program card (`.program-inner`) the global stylesheet compacts it.
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
}) {
  const Heading = level === 1 ? "h1" : "h2";
  return (
    <div className={styles.screen} role={role}>
      <span className={styles.symbol} data-tone={tone}>
        <Icon name={icon} size="state" />
      </span>
      <Heading className={styles.title}>{title}</Heading>
      {message && <p className={styles.message}>{message}</p>}
      {(primary || secondary) && (
        <div className={styles.actions}>
          {primary && <Action action={primary} className="button" />}
          {secondary && <Action action={secondary} className={styles.secondary} />}
        </div>
      )}
    </div>
  );
}

function Action({ action, className }: { action: MessageAction; className: string }) {
  if ("href" in action)
    return (
      <Link className={className} href={action.href}>
        {action.label}
      </Link>
    );
  return (
    <button type="button" className={className} disabled={action.disabled} onClick={action.onClick}>
      {action.label}
    </button>
  );
}
