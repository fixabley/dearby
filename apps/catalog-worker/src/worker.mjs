import { spawn, spawnSync } from "node:child_process";
import { mkdtemp, writeFile, readFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { collectionSchema, promptFor, prepareCollection } from "./collect.mjs";
const base = process.env.SUPABASE_URL,
  service = process.env.SUPABASE_SERVICE_ROLE_KEY;
if (!base || !service)
  throw new Error(
    "Set SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY in the worker's private environment",
  );
const url = new URL(base);
if (
  url.protocol !== "https:" &&
  !["127.0.0.1", "localhost"].includes(url.hostname)
)
  throw new Error("Supabase must use HTTPS outside loopback");
const codex = process.env.CODEX_BIN ?? "codex";
// Subscription-only: no API keys, DB keys, repository config, MCP or shell tools enter Codex.
const childEnv = Object.fromEntries(
  ["HOME", "PATH", "USER", "LOGNAME", "TMPDIR", "LANG", "LC_ALL", "CODEX_HOME"]
    .filter((key) => process.env[key])
    .map((key) => [key, process.env[key]]),
);
async function rpc(name, body = {}) {
  const response = await fetch(base + "/rest/v1/rpc/" + name, {
    method: "POST",
    headers: {
      apikey: service,
      Authorization: `Bearer ${service}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(20000),
  });
  if (!response.ok) {
    const data = await response.json().catch(() => ({}));
    throw new Error(`${name}: ${data.message ?? response.status}`);
  }
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}
const programIndex = process.argv.indexOf("--program");
const target = programIndex >= 0 ? process.argv[programIndex + 1] : null;
if (programIndex >= 0 && (!target || !/^[0-9a-f-]{36}$/i.test(target)))
  throw new Error("--program requires a UUID");
if (process.argv.includes("--enqueue"))
  await rpc("enqueue_catalog_collection", { target_program: target });
if (process.argv.includes("--catch-up")) {
  const res = await fetch(
    base + "/rest/v1/catalog_collection_settings?select=enabled&id=eq.true",
    {
      headers: { apikey: service, Authorization: `Bearer ${service}` },
      signal: AbortSignal.timeout(10000),
    },
  );
  if (!res.ok) throw new Error("Cannot read collection schedule settings");
  const hour = new Intl.DateTimeFormat("en-GB", {
    timeZone: "Asia/Seoul", hour: "2-digit", hourCycle: "h23",
  }).format(new Date());
  if ((await res.json())[0]?.enabled && hour >= "09")
    await rpc("enqueue_catalog_collection");
}
const task = await rpc("claim_catalog_collection", { target_program: target });
if (!task) {
  console.log("No collection job ready.");
  process.exit(0);
}
const identity = { job_id: task.job.id, token: task.job.lease_token };
let temp;
let usage = {},
  searches = 0;
try {
  const auth = spawnSync(codex, ["login", "status"], {
    env: childEnv,
    encoding: "utf8",
    timeout: 15000,
  });
  if (
    auth.status !== 0 ||
    !/Logged in using ChatGPT/i.test(auth.stdout + auth.stderr)
  )
    throw new Error(
      "BLOCKED: Codex subscription login required; run codex login using ChatGPT",
    );
  if (!task.program.collection_hosts.length)
    throw new Error("BLOCKED: Configure official hosts for this program");
  temp = await mkdtemp(join(tmpdir(), "dearby-collection-"));
  const schema = join(temp, "schema.json"),
    output = join(temp, "result.json");
  await writeFile(schema, JSON.stringify(collectionSchema), { mode: 0o600 });
  const args = [
    "exec",
    "--ignore-user-config",
    "--ephemeral",
    "--ignore-rules",
    "--skip-git-repo-check",
    "--sandbox",
    "read-only",
    "--disable",
    "shell_tool",
    "--disable",
    "multi_agent",
    "--disable",
    "apps",
    "--disable",
    "js_repl",
    "--model",
    "gpt-6-luna",
    "-c",
    'forced_login_method="chatgpt"',
    "-c",
    'web_search="live"',
    "-c",
    'model_reasoning_effort="low"',
    "-c",
    'service_tier="default"',
    "-c",
    'approval_policy="never"',
    "--json",
    "--output-schema",
    schema,
    "--output-last-message",
    output,
    "-C",
    temp,
    "-",
  ];

  await new Promise((resolve, reject) => {
    const child = spawn(codex, args, {
      env: childEnv,
      stdio: ["pipe", "pipe", "pipe"],
      detached: true,
    });
    let pending = "",
      errorText = "",
      failure,
      outputBytes = 0;
    const stop = (reason) => {
      if (failure) return;
      failure = reason;
      try {
        process.kill(-child.pid, "SIGTERM");
      } catch {}
      setTimeout(() => {
        try {
          process.kill(-child.pid, "SIGKILL");
        } catch {}
      }, 3000).unref();
    };
    const onSignal = () => stop(new Error("Worker interrupted"));
    process.once("SIGTERM", onSignal);
    process.once("SIGINT", onSignal);
    const cleanup = () => {
      clearTimeout(timer);
      process.removeListener("SIGTERM", onSignal);
      process.removeListener("SIGINT", onSignal);
    };
    const timer = setTimeout(
      () => stop(new Error("Codex collection exceeded 4 minutes")),
      240000,
    );
    child.on("error", (error) => {
      cleanup();
      reject(error);
    });
    child.stdout.on("data", (chunk) => {
      outputBytes += chunk.length;
      pending += chunk.toString();
      if (outputBytes > 2_000_000) {
        stop(new Error("Codex output exceeded limit"));
        return;
      }
      const lines = pending.split("\n");
      pending = lines.pop();
      for (const line of lines) {
        try {
          const event = JSON.parse(line);
          if (event.type === "turn.completed") usage = event.usage ?? {};
          if (
            event.type === "item.started" &&
            event.item?.type === "web_search" &&
            ++searches > 12
          )
            stop(new Error("Web search call budget exceeded"));
          if (event.type === "turn.failed" || event.type === "error")
            errorText += JSON.stringify(event).slice(0, 1000);
        } catch {}
      }
    });
    child.stderr.on("data", (chunk) => {
      errorText = (errorText + chunk.toString()).slice(-5000);
    });
    child.on("close", (code) => {
      cleanup();
      if (failure) reject(failure);
      else if (code !== 0)
        reject(
          new Error(
            /rate.limit|usage.limit|quota|log.?in|unauthorized|refresh.token/i.test(
              errorText,
            )
              ? "BLOCKED: Codex subscription quota or login requires attention"
              : `Codex exited with status ${code}`,
          ),
        );
      else resolve();
    });
    child.stdin.end(
      promptFor(
        task,
        new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Seoul" }).format(
          new Date(),
        ),
      ),
    );
  });
  const raw = await readFile(output, "utf8");
  if (raw.length > 200000) throw new Error("Collection result too large");
  const parsed = JSON.parse(raw);
  usage.collection_summary = String(parsed.summary ?? "").slice(0, 4000);
  usage.outcome = parsed.outcome;
  usage.reported_sources = parsed.checkedSources;
  const result = await prepareCollection(parsed, task.program.collection_hosts);
  const stats = await rpc("finish_catalog_collection", {
    ...identity,
    ...result,
    run_usage: {
      ...usage,
      web_search_calls: searches,
      model: "gpt-6-luna",
      auth: "chatgpt-subscription",
    },
  });
  console.log(
    JSON.stringify({ program: task.program.title, job: task.job.id, ...stats }),
  );
} catch (error) {
  try {
    await rpc("fail_catalog_collection", {
      ...identity,
      reason: error.message,
      blocked: error.message.startsWith("BLOCKED:"),
      run_usage: {
        ...usage,
        web_search_calls: searches,
        model: "gpt-6-luna",
        auth: "chatgpt-subscription",
      },
    });
  } catch {
    console.error(
      "Could not persist failure; lease recovery will handle the job.",
    );
  }
  console.error(JSON.stringify({ usage, web_search_calls: searches }));
  console.error(
    `Collection failed for ${task.program.title}: ${error.message}`,
  );
  process.exitCode = 1;
} finally {
  if (temp) await rm(temp, { recursive: true, force: true });
}
