import { test } from "node:test";
import assert from "node:assert/strict";
import { createServer } from "node:http";
import { spawn } from "node:child_process";
import { mkdtemp, writeFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
const worker = fileURLToPath(new URL("../src/worker.mjs", import.meta.url));
const run = (env) =>
  new Promise((resolve, reject) => {
    const child = spawn(process.execPath, [worker], {
      env: { ...process.env, SUPABASE_SERVICE_ROLE_KEY: "test-private", ...env },
      stdio: ["ignore", "pipe", "pipe"],
    });
    let output = "";
    child.stdout.on("data", (b) => (output += b));
    child.stderr.on("data", (b) => (output += b));
    child.on("error", reject);
    child.on("close", (code) => resolve({ code, output }));
  });
async function scenario(auth) {
  const calls = [];
  const server = createServer(async (req, res) => {
    let text = "";
    for await (const part of req) text += part;
    const body = JSON.parse(text);
    calls.push({ name: req.url.split("/").pop(), body });
    res.setHeader("Content-Type", "application/json");
    if (req.url.endsWith("claim_catalog_collection"))
      res.end(
        JSON.stringify({
          job: { id: "test-job", lease_token: "test-lease" },
          program: {
            title: "test",
            description: "",
            collection_hosts: ["official.example"],
          },
          knownActivities: [],
        }),
      );
    else {
      res.statusCode = 204;
      res.end();
    }
  });
  await new Promise((r) => server.listen(0, "127.0.0.1", r));
  const dir = await mkdtemp(join(tmpdir(), "collector-exec-test-")),
    bin = join(dir, "codex");
  try {
    await writeFile(
      bin,
      `#!${process.execPath}
import fs from 'node:fs';
if(process.env.OPENAI_API_KEY || process.env.CODEX_API_KEY || process.env.SUPABASE_SERVICE_ROLE_KEY || process.env.SUPABASE_URL)throw new Error('Secret entered child');
if(process.argv[2]==='login'){console.log(${JSON.stringify(auth)});process.exit(0);}
const args=process.argv.slice(2);
for(const value of ['--ignore-user-config','--ignore-rules','--ephemeral','shell_tool','multi_agent','apps','js_repl','read-only','gpt-6-luna','forced_login_method="chatgpt"','service_tier="default"','web_search="live"'])if(!args.includes(value))throw new Error('Missing isolation: '+value);
process.stdin.resume();process.stdin.on('end',()=>{
 fs.writeFileSync(args[args.indexOf('--output-last-message')+1],JSON.stringify({outcome:'source_unavailable',summary:'fixture unavailable',checkedSources:[],activities:[]}));
 console.log(JSON.stringify({type:'item.started',item:{type:'web_search'}}));
 console.log(JSON.stringify({type:'turn.completed',usage:{input_tokens:123,output_tokens:45}}));
});
`,
      { mode: 0o700 },
    );
    const result = await run({
      CODEX_BIN: bin,
      SUPABASE_URL: `http://127.0.0.1:${server.address().port}`,
      OPENAI_API_KEY: "must-not-forward",
      CODEX_API_KEY: "must-not-forward",
    });
    return { calls, ...result };
  } finally {
    server.close();
    await rm(dir, { recursive: true, force: true });
  }
}
test("exec isolates secrets, pins subscription model/tools, and records failed-run usage", async () => {
  const result = await scenario("Logged in using ChatGPT");
  assert.equal(result.code, 1);
  assert.ok(!result.output.includes("Could not persist failure"));
  const fail = result.calls.find((c) => c.name === "fail_catalog_collection");
  assert.ok(fail);
  assert.match(fail.body.reason, /Official source unavailable/);
  assert.equal(fail.body.run_usage.input_tokens, 123);
  assert.equal(fail.body.run_usage.web_search_calls, 1);
  // Structural source failure: no same-day retry, but never the subscription-wide pause.
  assert.equal(fail.body.blocked, true);
  assert.doesNotMatch(fail.body.reason, /^BLOCKED: Codex subscription/);
  assert.ok(!result.calls.some((c) => c.name === "finish_catalog_collection"));
});
test("API login cannot execute; authentication failure is blocked for operator attention", async () => {
  const result = await scenario("Logged in using an API key");
  assert.equal(result.code, 1);
  assert.ok(!result.output.includes("Could not persist failure"));
  const fail = result.calls.find((c) => c.name === "fail_catalog_collection");
  assert.equal(fail.body.blocked, true);
  assert.match(fail.body.reason, /subscription login required/);
  assert.equal(fail.body.run_usage.web_search_calls, 0);
});
test("claim network failure is logged with a timestamp instead of crashing", async () => {
  const server = createServer((req) => req.socket.destroy());
  await new Promise((r) => server.listen(0, "127.0.0.1", r));
  try {
    const result = await run({ SUPABASE_URL: `http://127.0.0.1:${server.address().port}` });
    assert.equal(result.code, 1);
    assert.match(
      result.output,
      /^\d{4}-\d\d-\d\dT[\d:.]+Z Collection setup failed: claim_catalog_collection: /,
    );
    assert.ok(!result.output.includes("triggerUncaughtException"));
  } finally {
    server.close();
  }
});
