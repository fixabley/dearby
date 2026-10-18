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
    | "check"
    | "alert"
    | "phone"
    | "email"
    | "kakao"
    | "instagram"
    | "github"
    | "behance";
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
    alert: (
      <>
        <circle cx="12" cy="12" r="9" />
        <path d="M12 7v6m0 4h.01" />
      </>
    ),
    // 연락처 종류 아이콘(브랜드 로고가 아닌 단순 선 그림). 명함 연락처 행이 쓴다.
    phone: (
      <path d="M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2Z" />
    ),
    email: (
      <>
        <rect x="3" y="5" width="18" height="14" rx="2" />
        <path d="m3 7 9 6 9-6" />
      </>
    ),
    kakao: <path d="M12 4C7 4 3 7.1 3 11c0 2.4 1.6 4.5 4 5.8L6 21l4.3-2.4c.6.1 1.1.1 1.7.1 5 0 9-3.1 9-7s-4-7.7-9-7.7Z" />,
    instagram: (
      <>
        <rect x="4" y="4" width="16" height="16" rx="4" />
        <circle cx="12" cy="12" r="3.5" />
        <path d="M16.5 7.5h.01" />
      </>
    ),
    github: (
      <>
        <path d="m8 8-4 4 4 4m8-8 4 4-4 4M14 5l-4 14" />
      </>
    ),
    behance: (
      <>
        <path d="M4 6h5a3 3 0 0 1 0 6H4Zm0 6h5.5a3 3 0 0 1 0 6H4Z" />
        <path d="M14 14h7a3.5 3.5 0 1 0-7 0c0 2 1.6 3.5 3.5 3.5 1.5 0 2.6-.7 3.2-1.8M15 7h5" />
      </>
    ),
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
