"use client";
import { Analytics } from "@vercel/analytics/next";
import { analyticsUrl } from "@/shared/lib/analytics";

/** Vercel Web Analytics page views (contract analytics-v1.md "웹"), with the same cleaned URL as GA; no custom events. */
export function VercelAnalytics() {
  return <Analytics beforeSend={(event) => ({ ...event, url: analyticsUrl(event.url) })} />;
}
