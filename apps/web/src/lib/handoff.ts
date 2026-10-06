import { z } from "zod";
import { TOKEN, upstreamFetch } from "./guest-upstream";

// Contract "홈 화면 세션 잇기": a one-time code carries the Safari guest session into
// the iOS home-screen app, whose cookie jar starts empty.
const CODE = /^[A-Za-z0-9_-]{43,128}$/;
const issued = z.object({ code: z.string().regex(CODE) });
const redeemed = z.object({ guestToken: z.string().regex(TOKEN) });
const saved = z.object({ items: z.array(z.unknown()) });

export const isHandoffCode = (value: string) => CODE.test(value);

/** A fresh code for a session with at least one saved card; anything else yields none. */
export async function issueHandoff(token: string) {
  if (!TOKEN.test(token)) return;
  try {
    const list = await upstreamFetch("guest/cards", { guest: true, token });
    if (!list.ok || !saved.parse(await list.json()).items.length) return;
    const result = await upstreamFetch("guest/handoffs", {
      method: "POST",
      guest: true,
      token,
      body: {},
    });
    if (result.status !== 201) return;
    return issued.parse(await result.json()).code;
  } catch {
    return;
  }
}

/** The guest token for an unused, unexpired code; unknown, used or expired codes yield none. */
export async function redeemHandoff(code: string) {
  if (!isHandoffCode(code)) return;
  try {
    const result = await upstreamFetch("guest/handoffs/redeem", {
      method: "POST",
      guest: true,
      body: { code },
    });
    if (!result.ok) return;
    return redeemed.parse(await result.json()).guestToken;
  } catch {
    return;
  }
}
