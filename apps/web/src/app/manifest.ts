import type { MetadataRoute } from "next";
// Installability only: no service worker, so saved cards are never cached offline.
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "Dearby",
    short_name: "Dearby",
    description: "공유받은 공개 명함을 이 브라우저에 모아 봐요.",
    start_url: "/saved",
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
  };
}
