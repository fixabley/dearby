// Test-only contract double. Never imported by src or used to seed production.
import { createServer } from "node:http";
import { randomBytes } from "node:crypto";
import { card, cardId, secondCardId, revokedId, catalog } from "./fixtures";
// Copied from apps/dearby-api/test/fixtures/card-shares.json (API PR #84).
import shareData from "./card-shares.json";
type Wallet = { cards: Set<string>; shares: Map<string, string> };
const wallets = new Map<string, Wallet>();
const handoffs = new Map<
  string,
  { token: string; expires: number; used: boolean }
>();
const cards = new Map<string, unknown>([
  [cardId, card],
  [secondCardId, { ...card, id: secondCardId, profileName: "테스트 서연" }],
  // ownerId is private and dropped like the real public projection.
  ...shareData.cards.map(
    (shared) => [shared.id, { ...shared, ownerId: undefined }] as const,
  ),
]);
const revoked = new Set([revokedId, shareData.revokedCardId]);
const shares = new Map(shareData.shares.map((share) => [share.id, share]));
const publicShare = (id: string) => {
  const share = shares.get(id);
  return share && !revoked.has(share.cardId) ? share : undefined;
};
createServer(async (req, res) => {
  const path = req.url!;
  const json = (status: number, data: unknown) => {
    res.writeHead(status, { "Content-Type": "application/json" });
    res.end(JSON.stringify(data));
  };
  const id = path.split("/").at(-1)!;
  if (path === "/v1/catalog") return json(200, catalog());
  if (path.startsWith("/v1/cards/"))
    return cards.has(id) && !revoked.has(id)
      ? json(200, cards.get(id))
      : json(404, {});
  if (path.startsWith("/v1/shares/")) {
    const share = publicShare(id);
    return share
      ? json(200, { share, card: cards.get(share.cardId) })
      : json(404, {});
  }
  if (!path.startsWith("/v1/guest/")) return json(404, {});
  if (
    req.headers["x-guest-proxy-key"] !==
    "test-proxy-secret-with-more-than-32-characters"
  )
    return json(403, {});
  let token = req.headers["x-guest-token"] as string | undefined;
  if (token && !wallets.has(token)) return json(401, {});
  if (path === "/v1/guest/handoffs/redeem" && req.method === "POST") {
    let body = "";
    for await (const chunk of req) body += chunk;
    const code = JSON.parse(body || "{}").code;
    const entry = handoffs.get(code);
    if (
      !entry ||
      entry.used ||
      entry.expires < Date.now() ||
      !wallets.has(entry.token)
    )
      return json(404, { error: { code: "NOT_FOUND" } });
    entry.used = true;
    return json(200, { guestToken: entry.token });
  }
  if (path === "/v1/guest/handoffs" && req.method === "POST") {
    if (!token) return json(401, {});
    // One live code per session: issuing again invalidates the previous one.
    for (const entry of handoffs.values())
      if (entry.token === token) entry.used = true;
    const code = randomBytes(32).toString("base64url");
    handoffs.set(code, { token, expires: Date.now() + 600_000, used: false });
    return json(201, {
      code,
      expiresAt: new Date(Date.now() + 600_000).toISOString(),
    });
  }
  if (path === "/v1/guest/cards" && req.method === "GET") {
    const wallet = wallets.get(token!);
    const items = [...(wallet?.cards ?? [])].filter((id) => !revoked.has(id));
    return json(200, {
      items: items.map((id) => cards.get(id)),
      shares: [...(wallet?.shares ?? [])]
        .map(([shareId, savedAt]) => ({ share: shares.get(shareId)!, savedAt }))
        .filter(({ share }) => items.includes(share.cardId))
        .map(({ share, savedAt }) => ({
          cardId: share.cardId,
          shareId: share.id,
          activities: share.activities,
          savedAt,
        })),
    });
  }
  if (path === "/v1/guest/session" && req.method === "DELETE") {
    wallets.delete(token!);
    res.writeHead(204);
    return res.end();
  }
  if (req.method === "DELETE") {
    const wallet = wallets.get(token!);
    wallet?.cards.delete(id);
    for (const shareId of wallet?.shares.keys() ?? [])
      if (shares.get(shareId)?.cardId === id) wallet!.shares.delete(shareId);
    res.writeHead(204);
    return res.end();
  }
  if (req.method !== "PUT") return json(405, {});
  const share = path.startsWith("/v1/guest/shares/")
    ? publicShare(id)
    : undefined;
  const savedCard = share ? share.cardId : id;
  if (
    path.startsWith("/v1/guest/shares/")
      ? !share
      : !cards.has(id) || revoked.has(id)
  )
    return json(404, {});
  const fresh = !token;
  if (!token) {
    token = randomBytes(32).toString("base64url");
    wallets.set(token, { cards: new Set(), shares: new Map() });
  }
  const wallet = wallets.get(token)!;
  const known = share ? wallet.shares.has(id) : wallet.cards.has(id);
  wallet.cards.add(savedCard);
  if (share && !known) wallet.shares.set(id, new Date().toISOString());
  return json(fresh ? 201 : 200, {
    cardId: savedCard,
    ...(share ? { shareId: id } : {}),
    status: known ? "alreadySaved" : "saved",
    ...(fresh ? { guestToken: token } : {}),
  });
}).listen(4319, "127.0.0.1");
