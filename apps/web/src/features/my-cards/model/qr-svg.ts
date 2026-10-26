import QRCode from "qrcode";

/**
 * QR modules as one SVG path (contract "명함 교환" › 내 명함 QR 화면): error correction M like the apps, a 4-module quiet zone, dark
 * modules on the panel colour. `qrcode` only encodes; the drawing is ours so it stays crisp at any size.
 */
export function qrSvgPath(text: string): { path: string; size: number } {
  const { modules } = QRCode.create(text, { errorCorrectionLevel: "M" });
  const quiet = 4;
  const size = modules.size + quiet * 2;
  let path = "";
  for (let row = 0; row < modules.size; row++) {
    let run = 0;
    for (let col = 0; col <= modules.size; col++) {
      const dark = col < modules.size && modules.get(row, col) === 1;
      if (dark) run++;
      else if (run) {
        path += `M${col - run + quiet} ${row + quiet}h${run}v1h-${run}z`;
        run = 0;
      }
    }
  }
  return { path, size };
}
