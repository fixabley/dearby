// Android App Links (contract "유니버설 링크·App Links·활동 공유·오픈그래프"): 업로드 키와 개발 기기용 디버그 키.
// Play 앱 서명 키는 첫 AAB 업로드 뒤 Play Console(앱 무결성)의 SHA-256을 더한다; 그 전까지 Play 설치본은 브라우저로 연다.
export function GET() {
  return Response.json([
    {
      relation: ["delegate_permission/common.handle_all_urls"],
      target: {
        namespace: "android_app",
        package_name: "io.wid.dearby",
        sha256_cert_fingerprints: [
          "32:B6:2B:E3:CD:56:FC:B5:0C:67:CC:09:67:3B:D3:20:D2:33:10:3F:8F:EC:5C:0C:D8:3A:C6:A2:61:0D:C6:85",
          "91:E3:35:92:3F:21:75:61:A3:D1:7A:D0:F7:E8:5E:EA:BC:86:41:01:EA:48:96:70:DF:CA:DF:1B:9B:20:69:46",
        ],
      },
    },
  ]);
}
