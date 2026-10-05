export function Icon({
  name,
  size = 24,
}: {
  name:
    | "compass"
    | "bookmark"
    | "arrow"
    | "calendar"
    | "pin"
    | "external"
    | "card"
    | "check";
  size?: number;
}) {
  const paths = {
    compass: (
      <>
        <circle cx="11" cy="11" r="7" />
        <path d="m16 16 5 5M14 8l-2 4-4 2 2-4z" />
      </>
    ),
    bookmark: <path d="M6 3h12v18l-6-4-6 4z" />,
    arrow: <path d="m14 5-7 7 7 7M7 12h14" />,
    calendar: (
      <>
        <rect x="3" y="5" width="18" height="16" rx="2" />
        <path d="M7 3v4m10-4v4M3 11h18" />
      </>
    ),
    pin: (
      <>
        <path d="M19 10c0 5-7 11-7 11S5 15 5 10a7 7 0 1 1 14 0Z" />
        <circle cx="12" cy="10" r="2" />
      </>
    ),
    external: (
      <>
        <path d="M14 3h7v7m0-7L10 14M10 5H4v16h16v-6" />
      </>
    ),
    card: (
      <>
        <rect x="3" y="5" width="18" height="14" rx="2" />
        <circle cx="8" cy="11" r="2" />
        <path d="M5 16c0-3 6-3 6 0m3-6h4m-4 4h4" />
      </>
    ),
    check: <path d="m5 12 4 4L20 5" />,
  };
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {paths[name]}
    </svg>
  );
}
