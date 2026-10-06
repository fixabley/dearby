import { NextResponse, type NextRequest } from "next/server";
import { guestCookie, readGuestCookie } from "@/lib/guest-upstream";
import { redeemHandoff } from "@/lib/handoff";

/**
 * /saved?handoff=<code>: an empty home-screen app jar exchanges the code for the
 * Safari session. Success or not, redirect to /saved without the code; never log it.
 */
export async function proxy(request: NextRequest) {
  const code = request.nextUrl.searchParams.get("handoff");
  if (code === null) return NextResponse.next();
  const response = NextResponse.redirect(new URL("/saved", request.url), 303);
  response.headers.set("Cache-Control", "private, no-store, max-age=0");
  response.headers.set("Referrer-Policy", "no-referrer");
  if (readGuestCookie(request.headers.get("cookie")) === undefined) {
    const token = await redeemHandoff(code);
    if (token) response.headers.append("Set-Cookie", guestCookie(token));
  }
  return response;
}

export const config = { matcher: "/saved" };
