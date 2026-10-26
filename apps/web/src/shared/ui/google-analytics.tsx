"use client";
import Script from "next/script";
import { usePathname } from "next/navigation";
import { useEffect } from "react";
import { analyticsUrl } from "@/shared/lib/analytics";

/**
 * GA4 web stream (contract analytics-v1.md "웹"). The automatic page view is off because it would send the raw URL; one
 * `page_view` per App Router path change carries the cleaned `page_location`. Google signals and ad personalisation are off
 * and no user id is set.
 */
export function GoogleAnalytics({ id }: { id: string }) {
  const pathname = usePathname();
  useEffect(() => {
    const location = analyticsUrl(window.location.origin + pathname);
    if (!window.gtag) {
      window.dataLayer = window.dataLayer ?? [];
      // gtag.js reads the `arguments` object, not an array, from the data layer.
      window.gtag = function () {
        // eslint-disable-next-line prefer-rest-params
        window.dataLayer!.push(arguments);
      };
      window.gtag("js", new Date());
      window.gtag("config", id, {
        send_page_view: false,
        allow_google_signals: false,
        allow_ad_personalization_signals: false,
        page_location: location,
      });
    }
    window.gtag("set", { page_location: location });
    window.gtag("event", "page_view", { page_location: location });
  }, [id, pathname]);
  return <Script src={`https://www.googletagmanager.com/gtag/js?id=${id}`} strategy="afterInteractive" />;
}
