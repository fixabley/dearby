// iOS 유니버설 링크 (contract "유니버설 링크·App Links·활동 공유·오픈그래프"): 정적 값, `application/json`, 리다이렉트 없이 200.
export function GET() {
  return Response.json({
    applinks: {
      details: [{ appIDs: ["4V9FVN3VQF.io.wid.dearby"], components: [{ "/": "/cards/*" }, { "/": "/activities/*" }] }],
    },
  });
}
