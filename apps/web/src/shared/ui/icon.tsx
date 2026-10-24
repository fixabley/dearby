/**
 * The one inline SVG icon set (contract "아이콘 통일" + "웹에도 같은 규칙", 2026-10-20). Names carry the mobile table's meaning
 * (달력 calendar, 시각 clock, 장소 map-pin, 외부 링크 external-link, 검색 search, 지우기 clear, 닫기 close, 뒤로 chevron-left,
 * 펼침 chevron-down, 확인 check-circle, 오류 warning, 정보 info, QR qr, 명함 card, 공유 share, 지도 map, 활동 둘러보기 explore,
 * 받은 명함 inbox, 전화 phone, 메일 mail) plus the contact kinds the card rows need (kakao, instagram, github, behance — not in the
 * mobile table yet), the tag glyphs `TagIcon` picks by `nameEn`, and the 맞춤 대기열 controls and highlight glyphs (trophy, ticket,
 * chevron-up, more, pause, play; 지원 대상 cake, hash, people, graduation, briefcase, globe). Three sizes only: `inline` follows the text (1em), `row` is the 24px glyph before a row, `state` is the 28px
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
  // 태그 아이콘(contract "분야·성격 태그와 아이콘", 2026-10-24): SF Symbols 표와 같은 모양. `TagIcon`이 nameEn으로 고른다.
  tag: (
    <>
      <path d="M12 3H4v8l9.5 9.5a1.5 1.5 0 0 0 2 0l5-5a1.5 1.5 0 0 0 0-2Z" />
      <circle cx="8" cy="8" r="1.3" />
    </>
  ),
  lightbulb: (
    <>
      <path d="M9 18h6m-5 3h4" />
      <path d="M12 3a6 6 0 0 0-3.5 10.9c.6.5 1 1.2 1 2v.1h5v-.1c0-.8.4-1.5 1-2A6 6 0 0 0 12 3Z" />
    </>
  ),
  hammer: (
    <>
      <path d="m13 8-8.5 8.5a1.8 1.8 0 0 0 2.5 2.5L15.5 10.5" />
      <path d="M11 6.5 14 3.5l6.5 6.5-3 3Z" />
    </>
  ),
  bubbles: (
    <>
      <path d="M4 4h10a1 1 0 0 1 1 1v6a1 1 0 0 1-1 1H8l-4 3V5a1 1 0 0 1 1-1Z" />
      <path d="M15 9h4a1 1 0 0 1 1 1v9l-3.5-2.5H10a1 1 0 0 1-1-1V15" />
    </>
  ),
  network: (
    <>
      <circle cx="12" cy="5" r="2" />
      <circle cx="5" cy="18" r="2" />
      <circle cx="19" cy="18" r="2" />
      <path d="M11 6.8 6 16.2m7-9.4 5 9.4M7 18h10" />
    </>
  ),
  won: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="m7.5 8 2 8 2.5-7 2.5 7 2-8M7 11.5h10" />
    </>
  ),
  laptop: (
    <>
      <rect x="5" y="5" width="14" height="10" rx="1.5" />
      <path d="M3 19h18" />
    </>
  ),
  sparkles: (
    <>
      <path d="m10 3.5 1.6 4.9 4.9 1.6-4.9 1.6-1.6 4.9-1.6-4.9L3.5 10l4.9-1.6Z" />
      <path d="m18 14.5.8 2.2 2.2.8-2.2.8-.8 2.2-.8-2.2-2.2-.8 2.2-.8Z" />
    </>
  ),
  cloud: <path d="M7 18a4 4 0 0 1-.6-7.95A6 6 0 0 1 17.7 9a4.5 4.5 0 0 1-.2 9Z" />,
  cpu: (
    <>
      <rect x="7" y="7" width="10" height="10" rx="1.5" />
      <rect x="10" y="10" width="4" height="4" />
      <path d="M10 3v4m4-4v4m-4 10v4m4-4v4M3 10h4m-4 4h4m10-4h4m-4 4h4" />
    </>
  ),
  gear: (
    <>
      <circle cx="12" cy="12" r="3" />
      <circle cx="12" cy="12" r="6" />
      <path d="M12 3v3m0 12v3M3 12h3m12 0h3M5.6 5.6l2.1 2.1m8.6 8.6 2.1 2.1M5.6 18.4l2.1-2.1m8.6-8.6 2.1-2.1" />
    </>
  ),
  building: <path d="M4 21V7l6-3v17m0 0V9h10v12M3 21h18M13 12h4m-4 3h4m-4 3h4M6.5 10h1m-1 3h1m-1 3h1" />,
  "lock-shield": (
    <>
      <path d="M12 3 5 6v5c0 4.5 3 8.5 7 10 4-1.5 7-5.5 7-10V6Z" />
      <rect x="9.5" y="11" width="5" height="4" rx=".5" />
      <path d="M10.5 11V9.5a1.5 1.5 0 0 1 3 0V11" />
    </>
  ),
  "chart-bar": <path d="M4 20h16M6 20v-6m4 6V8m4 12v-9m4 9V5" />,
  megaphone: (
    <>
      <path d="M3 10v4h4l9 5V5l-9 5Z" />
      <path d="m7 14 1.5 5H11l-1-3.5M19 9.5a3 3 0 0 1 0 5" />
    </>
  ),
  "trend-up": <path d="M3 20h18M4 16l5-5 4 3 7-7m-5 0h5v5" />,
  heart: <path d="M12 20s-7.5-4.5-7.5-10A4.3 4.3 0 0 1 12 7.5a4.3 4.3 0 0 1 7.5 2.5c0 5.5-7.5 10-7.5 10Z" />,
  trophy: <path d="M8 4h8v5a4 4 0 0 1-8 0V4Zm0 2H5a3 3 0 0 0 3 4m8-4h3a3 3 0 0 1-3 4m-4 3v4m-4 3h8m-6-3h4" />,
  ticket: <path d="M4 7h16v3a2 2 0 0 0 0 4v3H4v-3a2 2 0 0 0 0-4V7Zm10 0v10" />,
  "chevron-up": <path d="m6 15 6-6 6 6" />,
  cake: <path d="M4 21h16v-8a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8Zm0-5c2 1.5 4 1.5 5.3 0 1.4 1.5 4 1.5 5.4 0 1.3 1.5 3.3 1.5 5.3 0M12 11V7m0-4c-1 1-1 2.2 0 3 1-.8 1-2 0-3Z" />,
  hash: <path d="M10 4 8 20m8-16-2 16M5 9h15M4 15h15" />,
  people: (
    <>
      <circle cx="12" cy="7.5" r="2.5" />
      <circle cx="5.5" cy="10" r="2" />
      <circle cx="18.5" cy="10" r="2" />
      <path d="M7.5 19a4.5 4.5 0 0 1 9 0M2 19a3.5 3.5 0 0 1 5-3.2M22 19a3.5 3.5 0 0 0-5-3.2" />
    </>
  ),
  graduation: <path d="m3 9 9-4 9 4-9 4-9-4Zm4 2v4.5c1.5 1.3 3 2 5 2s3.5-.7 5-2V11m4-2v5" />,
  briefcase: <path d="M4 8h16v11H4V8Zm5 0V5h6v3M4 13h16" />,
  globe: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M3 12h18M12 3c2.5 2.5 3.5 5.5 3.5 9s-1 6.5-3.5 9c-2.5-2.5-3.5-5.5-3.5-9s1-6.5 3.5-9Z" />
    </>
  ),
  more: <path d="M5.5 12h.01M12 12h.01M18.5 12h.01" />,
  pause: <path d="M9 6v12m6-12v12" />,
  play: <path d="M8 5.5v13l10-6.5-10-6.5Z" />,
  book: <path d="M12 6.5C10 5 7 4.5 3.5 5v13c3.5-.5 6.5 0 8.5 1.5 2-1.5 5-2 8.5-1.5V5C17 4.5 14 5 12 6.5Zm0 0v13" />,
  // 휴대폰과 창(웹·앱).
  devices: (
    <>
      <path d="M13 15H4.5A1.5 1.5 0 0 1 3 13.5v-8A1.5 1.5 0 0 1 4.5 4h11A1.5 1.5 0 0 1 17 5.5V8M3 8h14" />
      <rect x="15" y="10" width="6" height="10" rx="1.5" />
      <path d="M17.5 17.5h1" />
    </>
  ),
  gamepad: (
    <>
      <path d="M7 7h10a4 4 0 0 1 4 4.5l-.6 4.3a2.5 2.5 0 0 1-4.3 1.4L14.5 15h-5l-1.6 2.2a2.5 2.5 0 0 1-4.3-1.4L3 11.5A4 4 0 0 1 7 7Z" />
      <path d="M8 10v3m-1.5-1.5h3M15.5 11h.01M17 12.5h.01" />
    </>
  ),
  // 렌치와 드라이버를 엇갈린 모양(공학).
  tools: (
    <>
      <path d="M14.5 6.5a3.5 3.5 0 0 1 4.6-3.3l-2.3 2.3.7 1.7 1.7.7 2.3-2.3a3.5 3.5 0 0 1-4.6 4.6L7 20.1a1.6 1.6 0 0 1-2.2-2.2l9.9-9.9a3.5 3.5 0 0 1-.2-1.5Z" />
      <path d="m4 4 2.5 1 4.5 4.5M4 4l1 2.5M13.5 13.5l5.5 5.5a1.4 1.4 0 0 1-2 2l-5.5-5.5" />
    </>
  ),
  video: (
    <>
      <rect x="3" y="6" width="13" height="12" rx="2" />
      <path d="m16 10.5 5-3v9l-5-3" />
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
      className={tone && (tone === "teal" ? "text-teal" : "text-secondary-label")}
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
