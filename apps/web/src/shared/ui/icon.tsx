/**
 * The one inline SVG icon set (contract "아이콘 통일" + "웹에도 같은 규칙", 2026-10-20). Names carry the mobile table's meaning
 * (달력 calendar, 시각 clock, 장소 map-pin, 외부 링크 external-link, 검색 search, 지우기 clear, 닫기 close, 뒤로 chevron-left,
 * 펼침 chevron-down, 확인 check-circle, 오류 warning, 정보 info, QR qr, 명함 card, 공유 share, 지도 map, 활동 둘러보기 explore,
 * 받은 명함 inbox, 전화 phone, 메일 mail) plus the contact kinds the card rows need (kakao, instagram, github, behance — not in the
 * mobile table yet). Three sizes only: `inline` follows the text (1em), `row` is the 24px glyph before a row, `state` is the 28px
 * glyph inside a CenteredMessage circle. Colour is `currentColor`; `tone` pins it to the secondary or teal accent.
 */
export type IconName = keyof typeof paths;
export type IconSize = "inline" | "row" | "state";

const sizes: Record<IconSize, string> = { inline: "1em", row: "24", state: "28" };

const paths = {
  calendar: (
    <>
      <rect x="3" y="5" width="18" height="16" rx="2" />
      <path d="M7 3v4m10-4v4M3 11h18" />
    </>
  ),
  clock: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7v5l3 2" />
    </>
  ),
  "map-pin": (
    <>
      <path d="M19 10c0 5-7 11-7 11S5 15 5 10a7 7 0 1 1 14 0Z" />
      <circle cx="12" cy="10" r="2" />
    </>
  ),
  "external-link": <path d="M7 17 17 7M9 7h8v8" />,
  search: (
    <>
      <circle cx="11" cy="11" r="7" />
      <path d="m20 20-4.2-4.2" />
    </>
  ),
  clear: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="m9 9 6 6m0-6-6 6" />
    </>
  ),
  close: <path d="M6 6l12 12M18 6 6 18" />,
  "chevron-left": <path d="m15 6-6 6 6 6" />,
  "chevron-down": <path d="m6 9 6 6 6-6" />,
  "check-circle": (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="m8 12 3 3 5-6" />
    </>
  ),
  warning: (
    <>
      <path d="M12 3.5 2.8 19.5h18.4L12 3.5Z" />
      <path d="M12 10v4m0 3h.01" />
    </>
  ),
  info: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 11v5m0-8h.01" />
    </>
  ),
  qr: (
    <>
      <rect x="4" y="4" width="6" height="6" rx="1" />
      <rect x="14" y="4" width="6" height="6" rx="1" />
      <rect x="4" y="14" width="6" height="6" rx="1" />
      <path d="M14 14h3v3h-3zm6 0v6m-3 3h-3" />
    </>
  ),
  card: (
    <>
      <rect x="3" y="5" width="18" height="14" rx="2" />
      <circle cx="8" cy="11" r="2" />
      <path d="M5 16c0-3 6-3 6 0m3-6h4m-4 4h4" />
    </>
  ),
  share: <path d="M12 3v12m-4-8 4-4 4 4M6 11H5v10h14V11h-1" />,
  map: <path d="m9 4-6 2v14l6-2 6 2 6-2V4l-6 2-6-2Zm0 0v14m6-12v14" />,
  explore: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="m15.5 8.5-2 5-5 2 2-5z" />
    </>
  ),
  inbox: (
    <>
      <path d="M3 13h5l1.5 2h5L16 13h5M3 13l2.5-7h13L21 13v6H3z" />
      <path d="M8 9.5h8" />
    </>
  ),
  // 연락처 종류 아이콘(브랜드 로고가 아닌 단순 선 그림). 명함 연락처 행이 쓴다.
  phone: (
    <path d="M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2Z" />
  ),
  mail: (
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
  github: <path d="m8 8-4 4 4 4m8-8 4 4-4 4M14 5l-4 14" />,
  behance: (
    <>
      <path d="M4 6h5a3 3 0 0 1 0 6H4Zm0 6h5.5a3 3 0 0 1 0 6H4Z" />
      <path d="M14 14h7a3.5 3.5 0 1 0-7 0c0 2 1.6 3.5 3.5 3.5 1.5 0 2.6-.7 3.2-1.8M15 7h5" />
    </>
  ),
};

export function Icon({
  name,
  size = "row",
  tone,
}: {
  name: IconName;
  size?: IconSize;
  /** Secondary text colour or the teal accent; omitted, the glyph inherits (white inside a filled button). */
  tone?: "secondary" | "teal";
}) {
  return (
    <svg
      className={tone && `icon-${tone}`}
      width={sizes[size]}
      height={sizes[size]}
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
