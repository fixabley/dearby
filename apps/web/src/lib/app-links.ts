// Universal/App Links association files for shared-card links (/s/*).
// Values come only from server environment; anything missing or malformed
// means the file is not published (404) rather than published wrong.
type Env = Record<string, string | undefined>;

const list = (value: string | undefined) =>
  (value ?? "")
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);

export function appleAppSiteAssociation(env: Env = process.env) {
  const team = env.DEARBY_APPLE_TEAM_ID?.trim() ?? "";
  const bundles = list(env.DEARBY_IOS_BUNDLE_IDS);
  if (
    !/^[A-Z0-9]{10}$/.test(team) ||
    !bundles.length ||
    !bundles.every((id) => /^[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)+$/.test(id))
  )
    return;
  return {
    applinks: {
      details: [
        {
          appIDs: bundles.map((id) => `${team}.${id}`),
          components: [{ "/": "/s/*" }],
        },
      ],
    },
  };
}

// Digital Asset Links has no path scope; /s/* is declared in the app's intent filter.
export function assetLinks(env: Env = process.env) {
  const packageName = env.DEARBY_ANDROID_PACKAGE?.trim() ?? "";
  const fingerprints = list(env.DEARBY_ANDROID_CERT_SHA256).map((value) =>
    value.toUpperCase(),
  );
  if (
    !/^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$/.test(packageName) ||
    !fingerprints.length ||
    !fingerprints.every((value) =>
      /^([0-9A-F]{2}:){31}[0-9A-F]{2}$/.test(value),
    )
  )
    return;
  return [
    {
      relation: ["delegate_permission/common.handle_all_urls"],
      target: {
        namespace: "android_app",
        package_name: packageName,
        sha256_cert_fingerprints: fingerprints,
      },
    },
  ];
}

export function associationResponse(body: object | undefined) {
  return body
    ? Response.json(body, {
        headers: { "Cache-Control": "public, max-age=3600" },
      })
    : new Response(null, { status: 404 });
}
