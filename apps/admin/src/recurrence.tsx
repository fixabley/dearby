import { Checkbox, DatePicker, Form, InputNumber, Radio, Space, Typography } from "antd";
import dayjs, { type Dayjs } from "dayjs";
import customParseFormat from "dayjs/plugin/customParseFormat";
import { type OverlapDraft, type RangeDraft, SEOUL } from "./activities";

/** 반복 (native-v1 "반복 일정과 겹침 결과 묶음", 2026-10-22): editor state for one schedule's RRULE. */
export type RecurrenceDraft = {
  freq: "NONE" | "DAILY" | "WEEKLY";
  byDay: string[];
  interval: number;
  endKind: "UNTIL" | "COUNT";
  until: Dayjs | null;
  count: number | null;
};
/** RRULE weekday codes in dayjs `day()` order (0 = Sunday). */
const CODES = ["SU", "MO", "TU", "WE", "TH", "FR", "SA"];
const NAMES: Record<string, string> = { MO: "월", TU: "화", WE: "수", TH: "목", FR: "금", SA: "토", SU: "일" };
const WEEK = ["MO", "TU", "WE", "TH", "FR", "SA", "SU"];
const WEEKDAYS = WEEK.slice(0, 5);
const MAX_COUNT = 400;
// UNTIL is read with an explicit format.
dayjs.extend(customParseFormat);

export const noRecurrence = (): RecurrenceDraft => ({ freq: "NONE", byDay: WEEKDAYS, interval: 1, endKind: "UNTIL", until: null, count: null });

/** Stored RRULE body → editor state; anything unreadable opens as 끔 (saving then clears it). */
export function recurrenceDraft(rule: string | null | undefined): RecurrenceDraft {
  const parts = Object.fromEntries((rule ?? "").split(";").map((part) => part.split("=") as [string, string]));
  if (parts.FREQ !== "DAILY" && parts.FREQ !== "WEEKLY") return noRecurrence();
  // UNTIL is a date or a UTC instant; either way the editor keeps the Seoul day.
  const until = parts.UNTIL ? (parts.UNTIL.endsWith("Z") ? dayjs.utc(parts.UNTIL, "YYYYMMDD[T]HHmmss[Z]").tz(SEOUL) : dayjs.tz(parts.UNTIL, "YYYYMMDD", SEOUL)) : null;
  return {
    freq: parts.FREQ,
    byDay: parts.BYDAY ? parts.BYDAY.split(",") : WEEKDAYS,
    interval: Number(parts.INTERVAL ?? 1),
    endKind: parts.COUNT ? "COUNT" : "UNTIL",
    until,
    count: parts.COUNT ? Number(parts.COUNT) : null,
  };
}

/** The day the rule ends on: the picked date, else the schedule's own end. */
const untilDay = (draft: RecurrenceDraft, range: RangeDraft["range"]) => draft.until ?? range?.[1] ?? null;

/**
 * Editor state → normalized RRULE body `FREQ;INTERVAL;BYDAY;UNTIL|COUNT` (INTERVAL=1 omitted), or null for 끔.
 * A timed schedule ends at the close of the Seoul day as a UTC instant (RFC 5545: UNTIL matches DTSTART's type); a date-only one by date.
 */
export function recurrenceInput(draft: RecurrenceDraft | undefined, { range, timed }: RangeDraft): string | null {
  if (!draft || draft.freq === "NONE") return null;
  const until = untilDay(draft, range);
  const end =
    draft.endKind === "COUNT"
      ? draft.count && `COUNT=${draft.count}`
      : until && `UNTIL=${timed ? until.tz(SEOUL).endOf("day").utc().format("YYYYMMDD[T]HHmmss[Z]") : until.format("YYYYMMDD")}`;
  const byDay = draft.freq === "WEEKLY" ? `BYDAY=${WEEK.filter((code) => draft.byDay.includes(code)).join(",")}` : null;
  return [`FREQ=${draft.freq}`, draft.interval > 1 && `INTERVAL=${draft.interval}`, byDay, end].filter(Boolean).join(";");
}

/** Occurrence days from [start] (Seoul), capped at 400 like the apps. */
function occurrences(draft: RecurrenceDraft, start: Dayjs, until: Dayjs | null): Dayjs[] {
  const first = start.tz(SEOUL).startOf("day");
  const last = draft.endKind === "UNTIL" ? until?.tz(SEOUL).startOf("day") : null;
  const limit = draft.endKind === "COUNT" ? Math.min(draft.count ?? 0, MAX_COUNT) : MAX_COUNT;
  const days: Dayjs[] = [];
  // Weeks count from the start's Monday (RRULE WKST=MO).
  const monday = first.subtract((first.day() + 6) % 7, "day");
  for (let day = first; days.length < limit && (!last || !day.isAfter(last)) && day.diff(first, "year") < 3; day = day.add(1, "day")) {
    const matches =
      draft.freq === "DAILY"
        ? day.diff(first, "day") % draft.interval === 0
        : draft.byDay.includes(CODES[day.day()]) && Math.floor(day.diff(monday, "day") / 7) % draft.interval === 0;
    if (matches) days.push(day);
  }
  return days;
}

function dayText(draft: RecurrenceDraft) {
  const every = draft.interval > 1 ? `${draft.interval}${draft.freq === "DAILY" ? "일" : "주"}마다 ` : "";
  if (draft.freq === "DAILY") return every ? `${every}매일` : "매일";
  const days = WEEK.filter((code) => draft.byDay.includes(code));
  const name = days.join() === WEEKDAYS.join() ? "평일" : days.join() === "SA,SU" ? "주말" : days.length === 7 ? "매일" : `매주 ${days.map((code) => NAMES[code]).join("·")}`;
  return every + name;
}

/** 반복 field group for a schedule block; [name] holds a RecurrenceDraft, the other names are the block's own fields for the preview. */
export function RecurrenceField({ name, rangeName, timedName, overlapName }: { name: string; rangeName: string; timedName: string; overlapName: string }) {
  const form = Form.useFormInstance();
  const draft: RecurrenceDraft = Form.useWatch(name, form) ?? noRecurrence();
  const range: RangeDraft["range"] = Form.useWatch(rangeName, form) ?? null;
  const timed: boolean = Form.useWatch(timedName, form) ?? false;
  const overlap: OverlapDraft = Form.useWatch(overlapName, form) ?? null;
  const repeating = draft.freq !== "NONE";
  const start = range?.[0] ?? null;
  const time = timed ? start && range?.[1] && `${start.tz(SEOUL).format("HH:mm")}~${range[1].tz(SEOUL).format("HH:mm")}` : overlap && `${overlap[0].format("HH:mm")}~${overlap[1].format("HH:mm")}`;
  const days = repeating && start ? occurrences(draft, start, untilDay(draft, range)) : [];
  const date = (day: Dayjs) => day.format("YYYY년 M월 D일");
  return (
    <div style={{ marginBottom: 8 }}>
      <Form.Item name={[name, "freq"]} label="반복" style={{ marginBottom: 8 }}>
        <Radio.Group
          optionType="button"
          options={[
            { value: "NONE", label: "끔" },
            { value: "DAILY", label: "매일" },
            { value: "WEEKLY", label: "매주" },
          ]}
        />
      </Form.Item>
      {repeating && (
        <>
          {draft.freq === "WEEKLY" && (
            <Form.Item name={[name, "byDay"]} label="요일" rules={[{ required: true, type: "array", min: 1, message: "요일을 하나 이상 골라 주세요." }]} style={{ marginBottom: 8 }}>
              <Checkbox.Group options={WEEK.map((code) => ({ value: code, label: NAMES[code] }))} />
            </Form.Item>
          )}
          <Space align="start" wrap>
            <Form.Item name={[name, "interval"]} label="간격" style={{ marginBottom: 8 }}>
              <InputNumber min={1} max={4} precision={0} addonBefore="매" addonAfter={draft.freq === "DAILY" ? "일" : "주"} style={{ width: 140 }} />
            </Form.Item>
            <Form.Item name={[name, "endKind"]} label="끝" style={{ marginBottom: 8 }}>
              <Radio.Group options={[{ value: "UNTIL", label: "날짜" }, { value: "COUNT", label: "횟수" }]} />
            </Form.Item>
            {draft.endKind === "UNTIL" ? (
              <Form.Item
                name={[name, "until"]}
                label="끝 날짜"
                style={{ marginBottom: 8 }}
                rules={[{ validator: (_, value) => (value || range?.[1] ? Promise.resolve() : Promise.reject(new Error("끝 날짜를 골라 주세요."))) }]}
              >
                <DatePicker placeholder={range?.[1] ? range[1].format("YYYY-MM-DD") : "끝 날짜"} />
              </Form.Item>
            ) : (
              <Form.Item name={[name, "count"]} label="횟수" rules={[{ required: true, message: "횟수를 입력해 주세요." }]} style={{ marginBottom: 8 }}>
                <InputNumber min={1} max={MAX_COUNT} precision={0} addonAfter="회" style={{ width: 120 }} />
              </Form.Item>
            )}
          </Space>
          <p className="field-note">
            {days.length
              ? `${dayText(draft)}${time ? ` ${time}` : ""}, ${date(days[0])}~${date(days[days.length - 1])}, ${days.length}회`
              : "시작 날짜를 고르면 반복 미리보기가 보여요."}
            {draft.endKind === "UNTIL" && !draft.until && range?.[1] && " 끝 날짜를 비우면 기간 종료일까지 반복해요."}
          </p>
          {!timed && !overlap && <Typography.Text type="warning">날짜만 있는 일정은 겹침 확인 시간이 있어야 반복할 수 있어요(매 회차 시간).</Typography.Text>}
        </>
      )}
    </div>
  );
}
