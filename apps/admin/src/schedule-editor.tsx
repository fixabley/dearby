import { useMemo, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Alert, App, Button, Calendar, Form, Input, Modal, Popconfirm, Segmented, Select, Space, Table, Tag, Tooltip } from "antd";
import dayjs, { type Dayjs } from "dayjs";
import { api, useSingleFlight } from "./api";
import { CuratedTag, rangeDraft, type RangeDraft, type Schedule, ScheduleBlock, scheduleInput, scheduleText, SEOUL } from "./activities";

/** 세부 일정 편집기 (contract admin-v1 "세부 일정 편집기", 2026-10-19): calendar and list views over an activity's detail schedules. */

type ScheduleKind = "DEADLINE" | "ANNOUNCEMENT" | "INTERVIEW" | "EVENT";
type ScheduleStatus = "TENTATIVE" | "CONFIRMED" | "CANCELLED";
const kindLabels: Record<ScheduleKind, string> = { DEADLINE: "마감", ANNOUNCEMENT: "발표", INTERVIEW: "면접", EVENT: "행사" };
const kindColors: Record<ScheduleKind, string> = { DEADLINE: "red", ANNOUNCEMENT: "purple", INTERVIEW: "gold", EVENT: "cyan" };
const statusLabels: Record<ScheduleStatus, string> = { TENTATIVE: "미정", CONFIRMED: "확정", CANCELLED: "취소" };
const kindOf = (value: string): ScheduleKind => (value in kindLabels ? (value as ScheduleKind) : "EVENT");
const statusOf = (value: string): ScheduleStatus => (value in statusLabels ? (value as ScheduleStatus) : "CONFIRMED");

/** `POST/PATCH activities/:id/schedules[/:sid]` body. Period fields come from the shared `scheduleInput`; `timeZone` is left to the server default (Asia/Seoul). */
type ScheduleItemInput = {
  title: string;
  kind: ScheduleKind;
  status: ScheduleStatus;
  startAt?: string;
  endAt?: string;
  startDate?: string;
  endDate?: string;
  dateLabel?: string;
  note?: string;
};

export type ScheduleEditorActivity = {
  id: number;
  name: string;
  application: Schedule | null;
  activitySchedule: Schedule;
  schedules: Schedule[];
};

/** Seoul day a schedule starts (instant or date), for sorting and for placing it in the calendar. */
function startDay(schedule: Schedule): Dayjs | null {
  if (schedule.startAt) return dayjs(schedule.startAt).tz(SEOUL).startOf("day");
  if (schedule.endAt) return dayjs(schedule.endAt).tz(SEOUL).startOf("day");
  if (schedule.startDate) return dayjs.tz(schedule.startDate, SEOUL);
  return null;
}
/** Last Seoul day a schedule covers (inclusive). */
function lastDay(schedule: Schedule): Dayjs | null {
  if (schedule.endAt) return dayjs(schedule.endAt).tz(SEOUL).startOf("day");
  if (schedule.endDateExclusive) return dayjs.tz(schedule.endDateExclusive, SEOUL).subtract(1, "day");
  return startDay(schedule);
}
function covers(schedule: Schedule | null, day: Dayjs): boolean {
  if (!schedule) return false;
  const first = startDay(schedule);
  const last = lastDay(schedule);
  if (!first && !last) return false;
  return !(first && day.isBefore(first, "day")) && !(last && day.isAfter(last, "day"));
}
function byTime(a: Schedule, b: Schedule): number {
  const first = startDay(a)?.valueOf() ?? Number.MAX_SAFE_INTEGER;
  const second = startDay(b)?.valueOf() ?? Number.MAX_SAFE_INTEGER;
  return first - second || a.title.localeCompare(b.title, "ko");
}

export function ScheduleEditorModal({ activity, onClose }: { activity: ScheduleEditorActivity; onClose: () => void }) {
  const [view, setView] = useState<"calendar" | "list">("calendar");
  const [form, setForm] = useState<{ schedule?: Schedule; day?: Dayjs } | null>(null);
  const { message } = App.useApp();
  const client = useQueryClient();
  const refresh = () => client.invalidateQueries({ queryKey: ["activities"] });
  const remove = useMutation({
    mutationFn: (scheduleId: number) => api<void>(`activities/${activity.id}/schedules/${scheduleId}`, "DELETE"),
    onSuccess: async () => {
      message.success("일정을 지웠어요.");
      await refresh();
    },
    onError: (error: Error) => message.error(error.message),
  });
  const sorted = useMemo(() => [...activity.schedules].sort(byTime), [activity.schedules]);
  return (
    <Modal open title={`일정 · ${activity.name}`} onCancel={onClose} footer={null} width={960} destroyOnClose>
      <Space style={{ marginBottom: 12 }} wrap>
        <Segmented
          value={view}
          onChange={(value) => setView(value as "calendar" | "list")}
          options={[
            { label: "달력 보기", value: "calendar" },
            { label: "목록 보기", value: "list" },
          ]}
        />
        <Button type="primary" onClick={() => setForm({})}>
          일정 추가
        </Button>
        <span className="muted">
          <span className="schedule-band-swatch application" /> 모집 기간 <span className="schedule-band-swatch activity" /> 활동 기간 · 날짜 칸을 누르면 그 날짜로 일정을 추가해요.
        </span>
      </Space>
      {view === "calendar" ? (
        <ScheduleCalendar activity={activity} onPickDay={(day) => setForm({ day })} onPickSchedule={(schedule) => setForm({ schedule })} />
      ) : (
        <Table<Schedule>
          size="small"
          rowKey="id"
          pagination={false}
          dataSource={sorted}
          locale={{ emptyText: "세부 일정이 없어요. 일정 추가로 만들거나 수집을 기다려요." }}
          columns={[
            { title: "제목", dataIndex: "title", render: (title: string, schedule) => <>{title}{schedule.curated && <CuratedTag />}</> },
            { title: "종류", dataIndex: "kind", render: (kind: string) => <Tag color={kindColors[kindOf(kind)]}>{kindLabels[kindOf(kind)]}</Tag> },
            { title: "상태", dataIndex: "status", render: (status: string) => statusLabels[statusOf(status)] },
            { title: "기간", render: (_, schedule) => scheduleText(schedule) },
            { title: "원문", dataIndex: "dateLabel", render: (label: string) => label || <span className="muted">없음</span> },
            { title: "사유", dataIndex: "curatedNote", render: (note: string | null | undefined) => note || <span className="muted">없음</span> },
            {
              title: "",
              render: (_, schedule) => (
                <Space>
                  <Button size="small" onClick={() => setForm({ schedule })}>
                    편집
                  </Button>
                  <Popconfirm title="이 일정을 지울까요?" description="수집이 같은 일정을 다시 넣지 않아요." okText="지우기" okButtonProps={{ danger: true }} onConfirm={() => remove.mutate(schedule.id)}>
                    <Button size="small" danger>
                      삭제
                    </Button>
                  </Popconfirm>
                </Space>
              ),
            },
          ]}
        />
      )}
      {form && <ScheduleForm activityId={activity.id} schedule={form.schedule} day={form.day} onClose={() => setForm(null)} onSaved={refresh} />}
    </Modal>
  );
}

/** Month calendar: 모집·활동 기간 as background bands, detail schedules as chips (first 3 + `+n`), whole cell click → add on that day. */
function ScheduleCalendar({
  activity,
  onPickDay,
  onPickSchedule,
}: {
  activity: ScheduleEditorActivity;
  onPickDay: (day: Dayjs) => void;
  onPickSchedule: (schedule: Schedule) => void;
}) {
  const byDay = useMemo(() => {
    const map = new Map<string, Schedule[]>();
    for (const schedule of activity.schedules) {
      const first = startDay(schedule);
      const last = lastDay(schedule) ?? first;
      if (!first || !last) continue;
      for (let day = first; !day.isAfter(last, "day") && map.size < 10_000; day = day.add(1, "day")) {
        const key = day.format("YYYY-MM-DD");
        map.set(key, [...(map.get(key) ?? []), schedule]);
      }
    }
    return map;
  }, [activity.schedules]);
  const initial = startDay(activity.activitySchedule) ?? startDay(activity.application ?? activity.activitySchedule) ?? dayjs().tz(SEOUL);
  return (
    <Calendar
      defaultValue={initial}
      fullCellRender={(date, info) => {
        if (info.type !== "date") return info.originNode;
        const day = date.tz(SEOUL, true);
        const items = byDay.get(day.format("YYYY-MM-DD")) ?? [];
        const bands = [covers(activity.application, day) && "application", covers(activity.activitySchedule, day) && "activity"].filter(Boolean).join(" ");
        return (
          <div
            className={`schedule-cell ${bands} ${day.isSame(dayjs(), "day") ? "today" : ""}`}
            role="button"
            tabIndex={0}
            aria-label={`${day.format("M월 D일")} 일정 추가`}
            onClick={() => onPickDay(day)}
            onKeyDown={(event) => {
              if (event.key === "Enter" || event.key === " ") onPickDay(day);
            }}
          >
            <div className="schedule-cell-day">{day.date()}</div>
            {items.slice(0, 3).map((schedule) => (
              <Tooltip key={schedule.id} title={`${schedule.title} · ${scheduleText(schedule)}`}>
                <button
                  type="button"
                  className={`schedule-chip kind-${kindOf(schedule.kind).toLowerCase()}`}
                  onClick={(event) => {
                    event.stopPropagation();
                    onPickSchedule(schedule);
                  }}
                >
                  {schedule.title}
                </button>
              </Tooltip>
            ))}
            {items.length > 3 && <span className="muted schedule-more">+{items.length - 3}</span>}
          </div>
        );
      }}
    />
  );
}

/** Add/edit one detail schedule. Period widgets are the shared block (dates or Korea-time instants, optional start, 사유). */
function ScheduleForm({
  activityId,
  schedule,
  day,
  onClose,
  onSaved,
}: {
  activityId: number;
  schedule?: Schedule;
  day?: Dayjs;
  onClose: () => void;
  onSaved: () => Promise<unknown>;
}) {
  const [form] = Form.useForm();
  const { message } = App.useApp();
  const saveOnce = useSingleFlight();
  const timed: boolean = Form.useWatch("timed", form) ?? false;
  const initial: RangeDraft = schedule ? rangeDraft(schedule) : { range: day ? [day, day] : null, timed: false };
  const save = useMutation({
    mutationFn: (body: ScheduleItemInput) =>
      schedule ? api<Schedule>(`activities/${activityId}/schedules/${schedule.id}`, "PATCH", body) : api<Schedule>(`activities/${activityId}/schedules`, "POST", body),
    onSuccess: async () => {
      message.success(schedule ? "일정을 고쳤어요." : "일정을 추가했어요.");
      await onSaved();
      onClose();
    },
  });
  return (
    <Modal open title={schedule ? `일정 편집 · ${schedule.title}` : "일정 추가"} onCancel={onClose} okText="저장" onOk={() => form.submit()} okButtonProps={{ loading: save.isPending }} destroyOnClose>
      <Form
        form={form}
        layout="vertical"
        initialValues={{
          title: schedule?.title ?? "",
          kind: schedule ? kindOf(schedule.kind) : "EVENT",
          status: schedule ? statusOf(schedule.status) : "CONFIRMED",
          dateLabel: schedule?.dateLabel ?? "",
          range: initial.range,
          timed: initial.timed,
          note: schedule?.curatedNote ?? "",
        }}
        onFinish={(values: { title: string; kind: ScheduleKind; status: ScheduleStatus; dateLabel: string; range: RangeDraft["range"]; timed: boolean; note: string }) => {
          const period = scheduleInput({ range: values.range, timed: values.timed }, values.note);
          const dateLabel = values.dateLabel.trim();
          if (!period && !dateLabel) {
            form.setFields([{ name: "dateLabel", errors: ["기간이나 원문 문구 중 하나는 입력해 주세요."] }]);
            return;
          }
          const { note, ...dates } = period ?? {};
          saveOnce(() =>
            save.mutateAsync({
              title: values.title.trim(),
              kind: values.kind,
              status: values.status,
              ...dates,
              ...(dateLabel && { dateLabel }),
              ...(note && { note }),
            }),
          );
        }}
      >
        {save.error && <Alert className="form-error" type="error" showIcon message={save.error.message} />}
        <Form.Item name="title" label="제목" rules={[{ required: true, whitespace: true, message: "제목을 입력해 주세요." }]}>
          <Input maxLength={200} placeholder="예: 1차 면접" />
        </Form.Item>
        <Space wrap>
          <Form.Item name="kind" label="종류">
            <Select style={{ width: 140 }} options={(Object.keys(kindLabels) as ScheduleKind[]).map((kind) => ({ value: kind, label: kindLabels[kind] }))} />
          </Form.Item>
          <Form.Item name="status" label="상태">
            <Select style={{ width: 120 }} options={(Object.keys(statusLabels) as ScheduleStatus[]).map((status) => ({ value: status, label: statusLabels[status] }))} />
          </Form.Item>
        </Space>
        <ScheduleBlock title="기간" schedule={schedule} rangeName="range" timedName="timed" noteName="note" timed={timed} startOptional />
        <Form.Item name="dateLabel" label="원문 문구(선택)">
          <Input maxLength={200} placeholder="예: 11월 둘째 주 중" />
        </Form.Item>
      </Form>
    </Modal>
  );
}
