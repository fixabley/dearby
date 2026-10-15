import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Same-origin API paths, mirroring the Vercel rewrites in vercel.json.
// localhost, not 127.0.0.1: the local passkey RP ID is `localhost` and an IP origin cannot match it.
export default defineConfig({
  plugins: [react()],
  envPrefix: [],
  server: {
    host: "localhost",
    port: 5173,
    strictPort: true,
    proxy: {
      "/api/auth": { target: "http://localhost:8080", rewrite: (path) => path.replace(/^\/api/, "/v1") },
      "/api/admin": { target: "http://localhost:8081", rewrite: (path) => path.replace(/^\/api/, "/v1") },
    },
  },
});
