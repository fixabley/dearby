// `.ics 내려받기` (2026-10-26 하루 시간표, web difference): the web has no device calendar, so a day's blocks go out as an iCalendar
// file instead of the apps' `캘린더에 추가`. One VEVENT per block, like the apps' block-level calendar entries.
export type IcsEvent = { uid: string; summary: string; start: number; end: number; location?: string | null; url?: string };

/** RFC 5545 text: `\`, `;`, `,` escaped and line breaks as `\n`. */
const escapeText = (value: string) => value.replace(/\\/g, "\\\\").replace(/[;,]/g, (c) => `\\${c}`).replace(/\r?\n/g, "\\n");
/** `yyyyMMddTHHmmssZ` in UTC, so no VTIMEZONE is needed. */
const stamp = (time: number) => new Date(time).toISOString().replace(/[-:]|\.\d{3}/g, "");

export function icsOf(events: IcsEvent[], now = Date.now()): string {
  const lines = [
    "BEGIN:VCALENDAR",
    "VERSION:2.0",
    "PRODID:-//Dearby//Web//KO",
    ...events.flatMap((event) => [
      "BEGIN:VEVENT",
      `UID:${event.uid}`,
      `DTSTAMP:${stamp(now)}`,
      `DTSTART:${stamp(event.start)}`,
      `DTEND:${stamp(event.end)}`,
      `SUMMARY:${escapeText(event.summary)}`,
      ...(event.location ? [`LOCATION:${escapeText(event.location)}`] : []),
      ...(event.url ? [`URL:${event.url}`] : []),
      "END:VEVENT",
    ]),
    "END:VCALENDAR",
  ];
  return lines.join("\r\n") + "\r\n";
}

/** Saves the text as `filename` through a transient object URL (iOS Safari offers 캘린더에 추가 for `text/calendar`). */
export function downloadIcs(filename: string, text: string) {
  const url = URL.createObjectURL(new Blob([text], { type: "text/calendar;charset=utf-8" }));
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
