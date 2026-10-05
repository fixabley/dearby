// Uses this checkout's private environment, never prints keys.
const enabled = process.argv[2];
if (!["on", "off"].includes(enabled))
  throw new Error(
    "Usage: node --env-file=.env.local scripts/schedule.mjs on|off",
  );
const base = process.env.SUPABASE_URL,
  key = process.env.SUPABASE_SERVICE_ROLE_KEY;
if (!base || !key) throw new Error("Worker environment required");
const res = await fetch(
  base + "/rest/v1/catalog_collection_settings?id=eq.true",
  {
    method: "PATCH",
    headers: {
      apikey: key,
      Authorization: `Bearer ${key}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ enabled: enabled === "on" }),
    signal: AbortSignal.timeout(10000),
  },
);
if (!res.ok) throw new Error("Could not update daily schedule: " + res.status);
console.log(
  "Daily enqueue " +
    enabled +
    ". Existing jobs are preserved; uninstall launchd to stop execution.",
);
