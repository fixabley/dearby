// Test-only contract double. Never imported by src or used to seed production.
import { createServer } from "node:http";
import { randomBytes } from "node:crypto";
import { card, cardId, secondCardId, revokedId, catalog } from "./fixtures";
const wallets = new Map<string, Set<string>>();
const cards = new Map([
  [cardId, card],
  [secondCardId, { ...card, id: secondCardId, profileName: "테스트 서연" }],
]);
createServer((req, res) => {
  const path = req.url!;
  const json = (status: number, data: unknown) => {
    res.writeHead(status, { "Content-Type": "application/json" });
    res.end(JSON.stringify(data));
  };
  if (path === "/v1/catalog") return json(200, catalog());
  if (path.startsWith("/v1/cards/"))
    return cards.has(path.split("/").at(-1)!)
      ? json(200, cards.get(path.split("/").at(-1)!))
      : json(404, {});
  if (!path.startsWith("/v1/guest/")) return json(404, {});
  if (
    req.headers["x-guest-proxy-key"] !==
    "test-proxy-secret-with-more-than-32-characters"
  )
    return json(403, {});
  let token = req.headers["x-guest-token"] as string | undefined;
  if (token && !wallets.has(token)) return json(401, {});
  if (path === "/v1/guest/cards" && req.method === "GET")
    return json(200, {
      items: [...(wallets.get(token!) ?? [])]
        .map((id) => cards.get(id))
        .filter(Boolean),
    });
  if (path === "/v1/guest/session" && req.method === "DELETE") {
    wallets.delete(token!);
    res.writeHead(204);
    return res.end();
  }
  const id = path.split("/").at(-1)!;
  if (req.method === "DELETE") {
    wallets.get(token!)?.delete(id);
    res.writeHead(204);
    return res.end();
  }
  if (req.method !== "PUT") return json(405, {});
  if (!cards.has(id) || id === revokedId) return json(404, {});
  const fresh = !token;
  if (!token) {
    token = randomBytes(32).toString("base64url");
    wallets.set(token, new Set());
  }
  const wallet = wallets.get(token)!;
  const status = wallet.has(id) ? "alreadySaved" : "saved";
  wallet.add(id);
  return json(fresh ? 201 : 200, {
    cardId: id,
    status,
    ...(fresh ? { guestToken: token } : {}),
  });
}).listen(4319, "127.0.0.1");
