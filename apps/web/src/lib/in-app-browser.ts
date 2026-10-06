// Known in-app browser user-agent tokens. Best effort only: an unknown
// in-app browser is simply not detected and saving is never blocked.
const inAppBrowsers: [RegExp, string][] = [
  [/KAKAOTALK/i, "카카오톡"],
  [/NAVER\(inapp/i, "네이버 앱"],
  [/Instagram/, "인스타그램"],
  [/FBAN|FBAV/, "페이스북"],
  [/\bLine\//, "라인"],
  [/DaumApps/, "다음 앱"],
  [/; wv\)/, "앱 내장 브라우저"],
];

export function inAppBrowser(userAgent: string): string | undefined {
  return inAppBrowsers.find(([pattern]) => pattern.test(userAgent))?.[1];
}
