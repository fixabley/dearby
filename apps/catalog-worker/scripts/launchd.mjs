import { execFileSync, spawnSync } from "node:child_process";
import {
  mkdirSync,
  writeFileSync,
  chmodSync,
  existsSync,
  unlinkSync,
} from "node:fs";
import { homedir } from "node:os";
import { fileURLToPath } from "node:url";
import { join, dirname } from "node:path";
const label = "com.dearby.catalog-subscription-worker";
const directory = fileURLToPath(new URL("../", import.meta.url));
const file = join(homedir(), "Library/LaunchAgents", label + ".plist");
const domain = `gui/${process.getuid()}`,
  target = `${domain}/${label}`;
const action = process.argv[2];
if (process.platform !== "darwin") throw new Error("launchd requires macOS");
if (action === "status") {
  const r = spawnSync("launchctl", ["print", target], { stdio: "inherit" });
  process.exit(r.status ?? 1);
}
if (action === "uninstall") {
  const result = spawnSync("launchctl", ["bootout", target], {
    encoding: "utf8",
  });
  if (
    result.status !== 0 &&
    !/could not find service|no such process/i.test(result.stderr)
  )
    throw new Error("Could not unload worker; inspect launchctl status");
  if (existsSync(file)) unlinkSync(file);
  console.log("Worker unloaded. Database jobs and credentials preserved.");
  process.exit(0);
}
if (action !== "install")
  throw new Error("Usage: node scripts/launchd.mjs install|uninstall|status");
if (Number(process.versions.node.split(".")[0]) !== 24)
  throw new Error("Install with Node 24; launchd pins this executable");
if (!existsSync(join(directory, ".env.local")))
  throw new Error("Run scripts/bootstrap.mjs first");
if (existsSync(file))
  throw new Error(
    "Worker already installed; uninstall explicitly before replacing",
  );
const codex = execFileSync("/usr/bin/which", ["codex"], {
  encoding: "utf8",
}).trim();
const env = Object.fromEntries(
  ["HOME", "PATH", "USER", "LOGNAME", "TMPDIR", "LANG", "LC_ALL", "CODEX_HOME"]
    .filter((k) => process.env[k])
    .map((k) => [k, process.env[k]]),
);
const auth = spawnSync(codex, ["login", "status"], {
  env,
  encoding: "utf8",
  timeout: 15000,
});
if (
  auth.status !== 0 ||
  !/Logged in using ChatGPT/.test(auth.stdout + auth.stderr)
)
  throw new Error("ChatGPT subscription login required");
// Homebrew resolves node to a versioned Cellar path that brew upgrade deletes; pin the stable opt link.
const cellar = process.execPath.match(/^(.+)\/Cellar\/([^/]+)\/[^/]+\/bin\/node$/);
const node =
  cellar && existsSync(`${cellar[1]}/opt/${cellar[2]}/bin/node`)
    ? `${cellar[1]}/opt/${cellar[2]}/bin/node`
    : process.execPath;
const logs = join(directory, "logs");
mkdirSync(logs, { recursive: true, mode: 0o700 });
mkdirSync(dirname(file), { recursive: true });
const esc = (v) =>
  String(v)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
const str = (v) => `<string>${esc(v)}</string>`;
const args = [
  node,
  "--env-file=" + join(directory, ".env.local"),
  join(directory, "src/worker.mjs"),
  "--once",
  "--catch-up",
];
writeFileSync(
  file,
  `<?xml version="1.0" encoding="UTF-8"?><!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd"><plist version="1.0"><dict>
<key>Label</key>${str(label)}<key>ProgramArguments</key><array>${args.map(str).join("")}</array>
<key>WorkingDirectory</key>${str(directory)}<key>EnvironmentVariables</key><dict><key>HOME</key>${str(homedir())}<key>PATH</key>${str(dirname(node) + ":/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin")}<key>CODEX_BIN</key>${str(codex)}${env.CODEX_HOME ? "<key>CODEX_HOME</key>" + str(env.CODEX_HOME) : ""}</dict>
<key>StartInterval</key><integer>60</integer><key>RunAtLoad</key><true/><key>ProcessType</key><string>Background</string>
<key>StandardOutPath</key>${str(join(logs, "worker.log"))}<key>StandardErrorPath</key>${str(join(logs, "worker-error.log"))}
</dict></plist>`,
  { mode: 0o600 },
);
chmodSync(file, 0o600);
execFileSync("plutil", ["-lint", file], { stdio: "ignore" });
execFileSync("launchctl", ["bootstrap", domain, file], { stdio: "inherit" });
console.log(
  "Worker installed: one job per wake, every 60 seconds; no overlapping launchd process. Cron enablement is separate.",
);
