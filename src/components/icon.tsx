export function Icon({
  name,
}: {
  name: "menu" | "search" | "home" | "compass" | "bookmark";
}) {
  const paths = {
    menu: "M4 6h16M4 12h16M4 18h16",
    search: "m16 16 5 5M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0",
    home: "m3 10 9-7 9 7v11h-6v-7H9v7H3Z",
    compass: "m16 8-3 5-5 3 3-5ZM22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0",
    bookmark: "M6 3h12v19l-6-4-6 4Z",
  };
  return (
    <svg
      width="22"
      height="22"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d={paths[name]} />
    </svg>
  );
}
