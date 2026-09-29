import { execFileSync } from "node:child_process";
import { writeFileSync, chmodSync } from "node:fs";
import { fileURLToPath } from "node:url";
const root = fileURLToPath(new URL("../../../", import.meta.url));
const config = JSON.parse(
  execFileSync(
    "npm",
    [
      "exec",
      "--yes",
      "--package=supabase@2.118.0",
      "--",
      "supabase",
      "status",
      "-o",
      "json",
    ],
    { cwd: root, encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] },
  ),
);
if (
  new URL(config.API_URL).hostname !== "127.0.0.1" ||
  !config.SERVICE_ROLE_KEY
)
  throw new Error("Expected local Supabase");
const file = new URL("../.env.local", import.meta.url);
writeFileSync(
  file,
  `SUPABASE_URL=${config.API_URL}\nSUPABASE_SERVICE_ROLE_KEY=${config.SERVICE_ROLE_KEY}\n`,
  { mode: 0o600 },
);
chmodSync(file, 0o600);
console.log(
  "Worker local environment written (0600). No users or catalog rows changed.",
);
