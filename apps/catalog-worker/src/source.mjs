import { lookup } from "node:dns/promises";
import { BlockList, isIP } from "node:net";
import { request } from "node:https";
const blocked = new BlockList();
for (const [ip, prefix] of [
  ["0.0.0.0", 8],
  ["10.0.0.0", 8],
  ["100.64.0.0", 10],
  ["127.0.0.0", 8],
  ["169.254.0.0", 16],
  ["172.16.0.0", 12],
  ["192.0.0.0", 24],
  ["192.0.2.0", 24],
  ["192.168.0.0", 16],
  ["198.18.0.0", 15],
  ["198.51.100.0", 24],
  ["203.0.113.0", 24],
  ["224.0.0.0", 4],
  ["240.0.0.0", 4],
])
  blocked.addSubnet(ip, prefix, "ipv4");
export function officialUrl(value, hosts) {
  const url = new URL(value);
  if (
    url.protocol !== "https:" ||
    url.username ||
    url.password ||
    (url.port && url.port !== "443") ||
    isIP(url.hostname) ||
    !hosts.includes(url.hostname.toLowerCase())
  )
    throw new Error("Source must use a configured official HTTPS host");
  url.hash = "";
  // Snapshot keys before deleting: iterating the live URLSearchParams would skip adjacent tracking keys.
  // oxlint-disable-next-line unicorn/no-useless-spread
  for (const key of [...url.searchParams.keys()])
    if (/^utm_|^(fbclid|gclid)$/i.test(key)) url.searchParams.delete(key);
  url.searchParams.sort();
  return url.href;
}
export const normalizeText = (text) =>
  text.normalize("NFC").replace(/\s+/g, " ").trim();
function entities(text) {
  const named = { amp: "&", lt: "<", gt: ">", quot: '"', apos: "'", nbsp: " " };
  return text.replace(
    /&(#x[0-9a-f]+|#\d+|amp|lt|gt|quot|apos|nbsp);/gi,
    (_, key) => {
      if (!key.startsWith("#")) return named[key.toLowerCase()];
      const n =
        key[1].toLowerCase() === "x"
          ? parseInt(key.slice(2), 16)
          : Number(key.slice(1));
      return n > 0 && n <= 0x10ffff ? String.fromCodePoint(n) : " ";
    },
  );
}
export function pageText(html) {
  return normalizeText(
    entities(
      html
        .replace(/<(script|style|noscript)\b[^>]*>[\s\S]*?<\/\1>/gi, " ")
        .replace(/<[^>]*>/g, " "),
    ),
  );
}
export async function fetchOfficialPage(
  value,
  hosts,
  { resolve = lookup, httpsRequest = request, timeoutMs = 15000 } = {},
) {
  const controller = new AbortController();
  const timer = setTimeout(
    () => controller.abort(new Error("Official page deadline exceeded")),
    timeoutMs,
  );
  try {
    return await fetchPage(value, 0);
  } finally {
    clearTimeout(timer);
  }
  async function fetchPage(value, redirects) {
    const url = new URL(officialUrl(value, hosts));
    // Pin a verified public IPv4 address to the TLS request (prevents DNS rebinding).
    const addresses = await Promise.race([
      resolve(url.hostname, { all: true, family: 4 }),
      new Promise((_, reject) =>
        controller.signal.addEventListener(
          "abort",
          () => reject(controller.signal.reason),
          { once: true },
        ),
      ),
    ]);
    if (
      !addresses.length ||
      addresses.some(
        (a) => isIP(a.address) !== 4 || blocked.check(a.address, "ipv4"),
      )
    )
      throw new Error("Official host did not resolve to a public address");
    const response = await new Promise((resolve, reject) => {
      const req = httpsRequest(
        url,
        {
          signal: controller.signal,
          headers: {
            "User-Agent": "DearbyCatalogBot/1.0 (official event information)",
            Accept: "text/html,text/plain",
          },
          lookup: (_host, options, callback) =>
            options.all
              ? callback(null, [addresses[0]])
              : callback(null, addresses[0].address, 4),
        },
        (res) => {
          let size = 0;
          const chunks = [];
          res.on("data", (chunk) => {
            size += chunk.length;
            if (size > 3_000_000) {
              res.destroy(new Error("Official page exceeds 3 MB"));
              return;
            }
            chunks.push(chunk);
          });
          res.on("error", reject);
          res.on("end", () =>
            resolve({
              status: res.statusCode,
              headers: res.headers,
              html: Buffer.concat(chunks).toString("utf8"),
            }),
          );
        },
      );
      req.on("error", reject);
      req.end();
    });
    if (
      response.status >= 300 &&
      response.status < 400 &&
      response.headers.location
    ) {
      if (redirects >= 3) throw new Error("Too many official page redirects");
      return fetchPage(
        new URL(response.headers.location, url).href,
        redirects + 1,
      );
    }
    if (
      response.status !== 200 ||
      !/text\/(html|plain)/i.test(response.headers["content-type"] ?? "")
    )
      throw new Error(`Official page unavailable (${response.status})`);
    return {
      url: url.href,
      html: response.html,
      text: pageText(response.html),
    };
  }
}

// Accept a full, explicit anchor destination, never a substring in scripts or text.
export function applicationLink(value, page) {
  if (value === null) return null;
  let wanted;
  try {
    wanted = new URL(value);
  } catch {
    return null;
  }
  if (wanted.protocol !== "https:" || wanted.username || wanted.password)
    return null;
  for (const match of page.html.matchAll(
    /<a\b[^>]*\bhref\s*=\s*(["'])(.*?)\1/gi,
  )) {
    try {
      if (new URL(entities(match[2]), page.url).href === wanted.href)
        return wanted.href;
    } catch {}
  }
  return null;
}
