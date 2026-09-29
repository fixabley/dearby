import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";
export function validatePublicConfig(url: string, key: string) {
  const endpoint = new URL(url);
  if (endpoint.protocol !== "https:" && !(endpoint.protocol === "http:" && ["localhost", "127.0.0.1"].includes(endpoint.hostname)))
    throw new Error("VITE_SUPABASE_URL must use HTTPS (except localhost).");
  let publicKey = /^sb_publishable_[A-Za-z0-9_-]+$/.test(key);
  try {
    publicKey ||= JSON.parse(Buffer.from(key.split(".")[1], "base64url").toString()).role === "anon";
  } catch { /* Non-JWT keys must have the publishable prefix. */ }
  if (!publicKey) throw new Error("VITE_SUPABASE_ANON_KEY must be a public anon/publishable key.");
}
export default defineConfig(({ command, mode }) => {
  const env = loadEnv(mode, process.cwd(), "VITE_");
  const url = env.VITE_SUPABASE_URL ?? "";
  const key = env.VITE_SUPABASE_ANON_KEY ?? "";
  if (command === "build" || url || key) validatePublicConfig(url, key);
  return {
    plugins: [react()],
    // Expose exactly these two public values, never arbitrary VITE_* credentials.
    envPrefix: [],
    define: {
      "import.meta.env.VITE_SUPABASE_URL": JSON.stringify(url),
      "import.meta.env.VITE_SUPABASE_ANON_KEY": JSON.stringify(key),
    },
    server: { host: "127.0.0.1", port: 5173, strictPort: true },
  };
});
