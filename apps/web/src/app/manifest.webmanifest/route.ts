import { readGuestCookie } from "@/lib/guest-upstream";
import { issueHandoff } from "@/lib/handoff";
// Built per request: start_url may carry a one-time handoff code for this browser's session.
export const dynamic = "force-dynamic";
export async function GET(request: Request) {
  const token = readGuestCookie(request.headers.get("cookie"));
  const code = token && (await issueHandoff(token));
  return Response.json(
    {
      // The home-screen label: the shortcut opens the saved list, and differs from the Dearby app if both are there.
      name: "Dearby 명함함",
      short_name: "Dearby 명함함",
      description: "받은 공개 명함을 이 브라우저에 모아 봐요.",
      // Installability only: no service worker, so saved cards are never cached offline.
      start_url: code ? `/saved?handoff=${code}` : "/saved",
      scope: "/",
      display: "standalone",
      background_color: "#ffffff",
      theme_color: "#007f80",
      icons: [
        { src: "/icons/icon-192.png", sizes: "192x192", type: "image/png" },
        { src: "/icons/icon-512.png", sizes: "512x512", type: "image/png" },
        // Full-bleed source with the mark inside the maskable safe zone.
        {
          src: "/icons/icon-512.png",
          sizes: "512x512",
          type: "image/png",
          purpose: "maskable",
        },
      ],
    },
    {
      headers: {
        "Content-Type": "application/manifest+json",
        // Never cache: the body differs per session.
        "Cache-Control": "private, no-store, max-age=0",
        Vary: "Cookie",
      },
    },
  );
}
