import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, App, Button, Form, Input, Modal, Popconfirm, Select, Space, Spin, Table, Tag } from "antd";
import { ArrowDownOutlined, ArrowUpOutlined, DeleteOutlined, PlusOutlined } from "@ant-design/icons";
import dayjs, { type Dayjs } from "dayjs";
import FullCalendar from "@fullcalendar/react";
import dayGridPlugin from "@fullcalendar/daygrid";
import timeGridPlugin from "@fullcalendar/timegrid";
import interactionPlugin from "@fullcalendar/interaction";
import type { DateSelectArg, EventClickArg, EventDropArg, EventInput } from "@fullcalendar/core";
import { api, useSingleFlight } from "./api";
import { type RecurrenceDraft, recurrenceDays, recurrenceDraft, recurrenceInput } from "./recurrence";
import {
  CuratedTag,
  type OverlapCheck,
  type OverlapDraft,
  overlapDraft,
  overlapInput,
  overlapUnavailable,
  rangeDraft,
  type RangeDraft,
  type Schedule,
  type SchedulePhase,
  phaseLabels,
  PhaseTag,
  ScheduleBlock,
  scheduleInput,
  scheduleText,
  SEOUL,
} from "./activities";
import { TimetableButton } from "./timetable-editor";

/** 세부 일정 편집기 (contract admin-v1 "세부 일정 편집기", 2026-10-19): calendar and list views over an activity's detail schedules. */

type ScheduleKind = "DEADLINE" | "ANNOUNCEMENT" | "INTERVIEW" | "EVENT";
type ScheduleStatus = "TENTATIVE" | "CONFIRMED" | "CANCELLED";
const kindLabels: Record<ScheduleKind, string> = { DEADLINE: "마감", ANNOUNCEMENT: "발표", INTERVIEW: "면접", EVENT: "행사" };
const kindColors: Record<ScheduleKind, string> = { DEADLINE: "red", ANNOUNCEMENT: "purple", INTERVIEW: "gold", EVENT: "cyan" };
const statusLabels: Record<ScheduleStatus, string> = { TENTATIVE: "미정", CONFIRMED: "확정", CANCELLED: "취소" };
const kindOf = (value: string): ScheduleKind => (value in kindLabels ? (value as ScheduleKind) : "EVENT");
const statusOf = (value: string): ScheduleStatus => (value in statusLabels ? (value as ScheduleStatus) : "CONFIRMED");
/**
 * 구분 choice in the form (contract "선발 과정·활동 일정(phase)", "phaseSource"): `AUTO` means the server rule (kind·상태 지정). A new
 * schedule then sends no `phase`; an edited one whose value is `STORED` sends `phase: null` to drop it, a `RESOLVED` one sends nothing.
 */
type PhaseChoice = SchedulePhase | "AUTO";

/** `POST/PATCH activities/:id/schedules[/:sid]` body. Period fields come from the shared `scheduleInput`; `timeZone` is left to the server default (Asia/Seoul). */
type ScheduleItemInput = {
  title: string;
  kind: ScheduleKind;
  status: ScheduleStatus;
  /** Omitted to keep (PATCH) or let the server resolve (POST); null on PATCH drops the stored value so the rule applies again. */
  phase?: SchedulePhase | null;
  startAt?: string;
  endAt?: string;
  startDate?: string;
  endDate?: string;
  dateLabel?: string;
  note?: string;
  /** null clears it on PATCH. */
  overlapCheck?: OverlapCheck | null;
  /** 반복 RRULE body; null clears it on PATCH. */
  recurrence?: string | null;
};

/** 일정 상태 (native-v1 "내 활동 서버 저장·일정 상태", 2026-10-22): the choices a user picks for one schedule's 진행 단계. */
type StatusKind = "WAITING" | "PASSED" | "FAILED" | "OTHER";
/** `GET/PUT activities/:id/schedules/:sid/statuses` item. Defaults (no stored rows) come back with `id: null`. */
type ScheduleStatusItem = { id?: number | null; name: string; kind: StatusKind };
const statusKindLabels: Record<StatusKind, string> = { WAITING: "대기", PASSED: "통과", FAILED: "탈락", OTHER: "기타" };
type DraftStatus = ScheduleStatusItem & { key: string };
const MAX_STATUSES = 8;
const MAX_STATUS_NAME = 20;

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
function byTime(a: Schedule, b: Schedule): number {
  const first = startDay(a)?.valueOf() ?? Number.MAX_SAFE_INTEGER;
  const second = startDay(b)?.valueOf() ?? Number.MAX_SAFE_INTEGER;
  return first - second || a.title.localeCompare(b.title, "ko");
}

export function ScheduleEditorModal({ activity, onClose }: { activity: ScheduleEditorActivity; onClose: () => void }) {
  const [form, setForm] = useState<{ schedule?: Schedule; selection?: CalendarSelection } | null>(null);
  const [statusesFor, setStatusesFor] = useState<Schedule | null>(null);
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
  const undated = useMemo(() => activity.schedules.filter((schedule) => !startDay(schedule) && schedule.dateLabel).sort(byTime), [activity.schedules]);
  return (
    <Modal open title={`일정 · ${activity.name}`} onCancel={onClose} footer={null} width={1100} destroyOnClose>
      <Space style={{ marginBottom: 12 }} wrap>
        <span className="muted">
          활동 기간 {scheduleText(activity.activitySchedule)} <PhaseTag phase="ACTIVITY" fixed />
        </span>
        <TimetableButton activityId={activity.id} schedule={activity.activitySchedule} />
        <span className="muted">
          <span className="schedule-band-swatch application" /> 모집 기간 <span className="schedule-band-swatch activity" /> 활동 기간 · 빈 날이나 시간을 끌어 일정을 만들어요.
        </span>
      </Space>
      <ScheduleCalendar activity={activity} onSelect={(selection) => setForm({ selection })} onPickSchedule={(schedule) => setForm({ schedule })} onSaved={refresh} />
      <UndatedSchedules schedules={undated} onEdit={(schedule) => setForm({ schedule })} onStatuses={setStatusesFor} activityId={activity.id} onDelete={(schedule) => remove.mutate(schedule.id)} />
      {form && <ScheduleForm activityId={activity.id} schedule={form.schedule} selection={form.selection} onClose={() => setForm(null)} onSaved={refresh} onDelete={form.schedule ? async () => { await remove.mutateAsync(form.schedule!.id); setForm(null); } : undefined} onStatuses={form.schedule ? () => { setForm(null); setStatusesFor(form.schedule!); } : undefined} />}
      {statusesFor && <ScheduleStatusesModal activityId={activity.id} schedule={statusesFor} onClose={() => setStatusesFor(null)} />}
    </Modal>
  );
}

type CalendarSelection = { range: [Dayjs, Dayjs]; timed: boolean };
type CalendarEventProps = { schedule?: Schedule; band?: boolean };

function dateRange(schedule: Schedule): { start: Dayjs; end: Dayjs; allDay: boolean } | null {
  if (schedule.startAt || schedule.endAt) {
    const start = dayjs(schedule.startAt ?? schedule.endAt).tz(SEOUL);
    return { start, end: dayjs(schedule.endAt ?? schedule.startAt).tz(SEOUL), allDay: false };
  }
  if (schedule.startDate) {
    const start = dayjs.tz(schedule.startDate, SEOUL);
    return { start, end: schedule.endDateExclusive ? dayjs.tz(schedule.endDateExclusive, SEOUL) : start.add(1, "day"), allDay: true };
  }
  return null;
}

function scheduleEvents(schedule: Schedule): EventInput[] {
  const range = dateRange(schedule);
  if (!range) return [];
  const event = (start: Dayjs, end: Dayjs, suffix = "") => ({
    id: `schedule-${schedule.id}${suffix}`,
    title: schedule.title,
    start: range.allDay ? start.format("YYYY-MM-DD") : start.toISOString(),
    end: range.allDay ? end.format("YYYY-MM-DD") : end.toISOString(),
    allDay: range.allDay,
    // One schedule owns a recurrence rule; dragging one occurrence must not silently shift the entire series.
    editable: !schedule.recurrence,
    classNames: [schedule.phase === "SELECTION" ? "schedule-event-selection" : "schedule-event-activity", overlapUnavailable(schedule) ? "schedule-event-no-overlap" : ""],
    extendedProps: { schedule } satisfies CalendarEventProps,
  });
  if (!schedule.recurrence) return [event(range.start, range.end)];
  const baseDay = range.start.startOf("day");
  return recurrenceDays(schedule.recurrence, baseDay, range.end.startOf("day")).map((day) => {
    const shift = day.diff(baseDay, "day");
    return event(range.start.add(shift, "day"), range.end.add(shift, "day"), `-${day.format("YYYYMMDD")}`);
  });
}

function selectionFor({ start, end, allDay }: DateSelectArg): CalendarSelection {
  const first = dayjs(start).tz(SEOUL);
  const last = dayjs(end).tz(SEOUL);
  return { range: allDay ? [first, last.subtract(1, "day")] : [first, last], timed: !allDay };
}

/** FullCalendar month/week editor. Slot periods are background-only events; detail schedules carry their resolved phase color. */
function ScheduleCalendar({
  activity,
  onSelect,
  onPickSchedule,
  onSaved,
}: {
  activity: ScheduleEditorActivity;
  onSelect: (selection: CalendarSelection) => void;
  onPickSchedule: (schedule: Schedule) => void;
  onSaved: () => Promise<unknown>;
}) {
  const { message } = App.useApp();
  const events = useMemo<EventInput[]>(() => {
    const bands = [
      [activity.application, "application"],
      [activity.activitySchedule, "activity"],
    ] as const;
    return [
      ...activity.schedules.flatMap(scheduleEvents),
      ...bands.flatMap(([schedule, band]) => {
        const range = schedule && dateRange(schedule);
        return range
          ? [{ id: `band-${band}`, title: band === "application" ? "모집 기간" : "활동 기간", start: range.allDay ? range.start.format("YYYY-MM-DD") : range.start.toISOString(), end: range.allDay ? range.end.format("YYYY-MM-DD") : range.end.toISOString(), allDay: range.allDay, display: "background" as const, classNames: [`schedule-band-${band}`], extendedProps: { band: true } satisfies CalendarEventProps }]
          : [];
      }),
    ];
  }, [activity]);
  const move = useMutation({
    mutationFn: ({ schedule, start, end, allDay }: { schedule: Schedule; start: Date; end: Date | null; allDay: boolean }) => {
      const first = dayjs(start).tz(SEOUL);
      const last = dayjs(end ?? start).tz(SEOUL);
      const period = scheduleInput({ range: allDay ? [first, last.subtract(1, "day")] : [first, last], timed: !allDay });
      return api<Schedule>(`activities/${activity.id}/schedules/${schedule.id}`, "PATCH", period ?? {});
    },
    onSuccess: async () => {
      message.success("일정을 옮겼어요.");
      await onSaved();
    },
    onError: (error: Error) => message.error(error.message),
  });
  const updatePosition = (arg: Pick<EventDropArg, "event" | "revert">) => {
    const schedule = arg.event.extendedProps.schedule as Schedule | undefined;
    if (!schedule || !arg.event.start) return;
    move.mutate({ schedule, start: arg.event.start, end: arg.event.end, allDay: arg.event.allDay }, { onError: () => arg.revert() });
  };
  const initial = startDay(activity.activitySchedule) ?? startDay(activity.application ?? activity.activitySchedule) ?? dayjs().tz(SEOUL);
  return (
    <div className="schedule-fullcalendar">
      <FullCalendar
        plugins={[dayGridPlugin, timeGridPlugin, interactionPlugin]}
        initialView="dayGridMonth"
        initialDate={initial.format("YYYY-MM-DD")}
        // FullCalendar needs a named-zone plugin for Asia/Seoul. Operators use Korea-local browsers, so local keeps Date drag values and ISO instants aligned.
        timeZone="local"
        locale="ko"
        headerToolbar={{ left: "prev,next today", center: "title", right: "dayGridMonth,timeGridWeek" }}
        buttonText={{ today: "오늘", month: "월", week: "주" }}
        height="auto"
        selectable
        selectMirror
        editable
        eventStartEditable
        eventDurationEditable
        events={events}
        select={(arg) => onSelect(selectionFor(arg))}
        eventClick={(arg: EventClickArg) => {
          const props = arg.event.extendedProps as CalendarEventProps;
          if (props.schedule) onPickSchedule(props.schedule);
        }}
        eventDrop={updatePosition}
        eventResize={updatePosition}
      />
    </div>
  );
}

function UndatedSchedules({ schedules, onEdit, onStatuses, activityId, onDelete }: { schedules: Schedule[]; onEdit: (schedule: Schedule) => void; onStatuses: (schedule: Schedule) => void; activityId: number; onDelete: (schedule: Schedule) => void }) {
  if (!schedules.length) return null;
  return (
    <section className="undated-schedules">
      <h3>날짜 미정</h3>
      <Table<Schedule>
        size="small"
        rowKey="id"
        pagination={false}
        dataSource={schedules}
        columns={[
          { title: "제목", dataIndex: "title", render: (title: string, schedule) => <>{title}{schedule.curated && <CuratedTag />}</> },
          { title: "원문 문구", dataIndex: "dateLabel" },
          { title: "종류", dataIndex: "kind", render: (kind: string) => <Tag color={kindColors[kindOf(kind)]}>{kindLabels[kindOf(kind)]}</Tag> },
          { title: "구분", dataIndex: "phase", render: (phase: SchedulePhase | undefined, schedule) => <PhaseTag phase={phase} stored={schedule.phaseSource === "STORED"} /> },
          { title: "", render: (_, schedule) => <Space><Button size="small" onClick={() => onEdit(schedule)}>편집</Button><Button size="small" onClick={() => onStatuses(schedule)}>진행 상태</Button><TimetableButton activityId={activityId} schedule={schedule} /><Popconfirm title="이 일정을 지울까요?" description="수집이 같은 일정을 다시 넣지 않아요." okText="지우기" okButtonProps={{ danger: true }} onConfirm={() => onDelete(schedule)}><Button size="small" danger>삭제</Button></Popconfirm></Space> },
        ]}
      />
    </section>
  );
}

/** Add/edit one detail schedule. Period widgets are the shared block (dates or Korea-time instants, optional start, 사유). */
function ScheduleForm({
  activityId,
  schedule,
  selection,
  onClose,
  onSaved,
  onDelete,
  onStatuses,
}: {
  activityId: number;
  schedule?: Schedule;
  selection?: CalendarSelection;
  onClose: () => void;
  onSaved: () => Promise<unknown>;
  onDelete?: () => Promise<unknown>;
  onStatuses?: () => void;
}) {
  const [form] = Form.useForm();
  const { message } = App.useApp();
  const saveOnce = useSingleFlight();
  const timed: boolean = Form.useWatch("timed", form) ?? false;
  const initial: RangeDraft = schedule ? rangeDraft(schedule) : { range: selection?.range ?? null, timed: selection?.timed ?? false };
  const stored = schedule?.phaseSource === "STORED";
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
    <Modal
      open
      title={schedule ? `일정 편집 · ${schedule.title}` : "일정 추가"}
      onCancel={onClose}
      okText="저장"
      onOk={() => form.submit()}
      okButtonProps={{ loading: save.isPending }}
      cancelText="취소"
      footer={(_, { OkBtn, CancelBtn }) => (
        <Space>
          {onDelete && (
            <Popconfirm title="이 일정을 지울까요?" description="수집이 같은 일정을 다시 넣지 않아요." okText="지우기" okButtonProps={{ danger: true }} onConfirm={onDelete}>
              <Button danger>삭제</Button>
            </Popconfirm>
          )}
          {onStatuses && <Button onClick={onStatuses}>진행 상태</Button>}
          {schedule && <TimetableButton activityId={activityId} schedule={schedule} />}
          <CancelBtn />
          <OkBtn />
        </Space>
      )}
      destroyOnClose
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={{
          title: schedule?.title ?? "",
          kind: schedule ? kindOf(schedule.kind) : "EVENT",
          status: schedule ? statusOf(schedule.status) : "CONFIRMED",
          phase: stored ? schedule.phase : "AUTO",
          dateLabel: schedule?.dateLabel ?? "",
          range: initial.range,
          timed: initial.timed,
          note: schedule?.curatedNote ?? "",
          overlap: overlapDraft(schedule?.overlapCheck),
          recurrence: recurrenceDraft(schedule?.recurrence),
        }}
        onFinish={(values: { title: string; kind: ScheduleKind; status: ScheduleStatus; phase: PhaseChoice; dateLabel: string; range: RangeDraft["range"]; timed: boolean; note: string; overlap: OverlapDraft; recurrence: RecurrenceDraft }) => {
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
              ...(values.phase !== "AUTO" ? { phase: values.phase } : stored && { phase: null }),
              ...dates,
              ...(dateLabel && { dateLabel }),
              ...(note && { note }),
              // Always sent, so clearing the pickers clears it (null on POST is simply none).
              overlapCheck: overlapInput(values.overlap),
              recurrence: recurrenceInput(values.recurrence, { range: values.range, timed: values.timed }),
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
          <Form.Item
            name="phase"
            label="구분"
            tooltip={
              stored
                ? "지금은 운영자나 수집기가 정한 구분이에요. 자동으로 바꾸면 저장값을 지우고 종류·진행 상태로 판정해요."
                : "자동은 종류·진행 상태로 서버가 판정해요. 코딩테스트·과제처럼 선발인 행사는 선발 과정으로 골라요. 고르면 그 값이 판정보다 우선해요."
            }
          >
            <Select<PhaseChoice>
              style={{ width: 200 }}
              options={[
                { value: "AUTO", label: schedule?.phase && !stored ? `자동 (지금: ${phaseLabels[schedule.phase]})` : "자동" },
                ...(Object.keys(phaseLabels) as SchedulePhase[]).map((phase) => ({ value: phase, label: phaseLabels[phase] })),
              ]}
            />
          </Form.Item>
        </Space>
        <ScheduleBlock title="기간" schedule={schedule} rangeName="range" timedName="timed" noteName="note" overlapName="overlap" recurrenceName="recurrence" timed={timed} startOptional />
        <Form.Item name="dateLabel" label="원문 문구(선택)">
          <Input maxLength={200} placeholder="예: 11월 둘째 주 중" />
        </Form.Item>
      </Form>
    </Modal>
  );
}

/**
 * 진행 상태 편집: the ordered choices users pick for this schedule. PUT replaces the whole list; an empty list goes back to the
 * defaults (대기·통과·탈락). Users who picked a status that is removed move to the default status of the same kind (server rule).
 */
function ScheduleStatusesModal({ activityId, schedule, onClose }: { activityId: number; schedule: Schedule; onClose: () => void }) {
  const path = `activities/${activityId}/schedules/${schedule.id}/statuses`;
  const queryKey = ["schedule-statuses", activityId, schedule.id];
  const { message, modal } = App.useApp();
  const client = useQueryClient();
  const saveOnce = useSingleFlight();
  const loaded = useQuery({ queryKey, queryFn: () => api<{ items: ScheduleStatusItem[] }>(path) });
  // Rows carry a local key so inputs keep focus and identity while they are moved.
  const [edited, setEdited] = useState<DraftStatus[] | null>(null);
  const draft: DraftStatus[] = edited ?? (loaded.data?.items ?? []).map((item, index) => ({ ...item, key: `loaded-${index}` }));
  const save = useMutation({
    mutationFn: (items: ScheduleStatusItem[]) => api<{ items: ScheduleStatusItem[] }>(path, "PUT", { items }),
    onSuccess: async () => {
      message.success("진행 상태를 저장했어요.");
      // Activity responses carry each schedule's statuses too.
      await Promise.all([client.invalidateQueries({ queryKey }), client.invalidateQueries({ queryKey: ["activities"] })]);
      onClose();
    },
  });
  const update = (index: number, change: Partial<DraftStatus>) => setEdited(draft.map((item, at) => (at === index ? { ...item, ...change } : item)));
  const move = (index: number, by: number) => {
    const next = [...draft];
    [next[index], next[index + by]] = [next[index + by], next[index]];
    setEdited(next);
  };
  const names = draft.map((item) => item.name.trim());
  const problem =
    draft.length === 0
      ? "상태를 하나 이상 두거나 기본값으로 되돌려 주세요."
      : names.some((name) => !name)
        ? "이름을 모두 입력해 주세요."
        : new Set(names).size !== names.length
          ? "같은 이름의 상태가 있어요."
          : null;
  /** Stored statuses that the save would drop; users who picked them move to the default of the same kind. */
  const removedNames = (next: ScheduleStatusItem[]) =>
    (loaded.data?.items ?? []).filter((item) => item.id != null && !next.some((kept) => kept.id === item.id)).map((item) => item.name);
  const submit = (items: ScheduleStatusItem[], title: string) => {
    const removed = removedNames(items);
    modal.confirm({
      title,
      content: removed.length ? `지우는 상태(${removed.join(", ")})를 고른 사용자는 같은 종류의 기본 상태(대기·통과·탈락)로 옮겨져요.` : undefined,
      okText: "저장",
      cancelText: "취소",
      onOk: () => saveOnce(() => save.mutateAsync(items)),
    });
  };
  const isDefault = loaded.data !== undefined && loaded.data.items.every((item) => item.id == null);
  return (
    <Modal
      open
      title={`진행 상태 · ${schedule.title}`}
      onCancel={onClose}
      okText="저장"
      cancelText="취소"
      // An untouched list is not sent: saving the defaults unchanged would store them and they would stop following the defaults.
      okButtonProps={{ disabled: edited === null || !!problem, loading: save.isPending }}
      onOk={() => submit(draft.map(({ id, name, kind }) => ({ ...(id != null && { id }), name: name.trim(), kind })), "진행 상태를 저장할까요?")}
      destroyOnClose
    >
      <p className="field-note">사용자가 이 일정의 진행 단계에서 고르는 상태예요. 위에서부터 이 순서로 보여요. 탈락 종류를 고르면 끝난 활동으로 옮겨져요.</p>
      {loaded.error && <Alert className="form-error" type="error" showIcon message={loaded.error.message} />}
      {save.error && <Alert className="form-error" type="error" showIcon message={save.error.message} />}
      {loaded.isPending ? (
        <Spin />
      ) : (
        <Space direction="vertical" style={{ width: "100%" }}>
          {isDefault && edited === null && <Tag>기본값 사용 중</Tag>}
          {draft.map((item, index) => (
            <Space key={item.key} wrap>
              <Input
                aria-label={`${index + 1}번째 상태 이름`}
                style={{ width: 200 }}
                maxLength={MAX_STATUS_NAME}
                value={item.name}
                status={!item.name.trim() || names.indexOf(item.name.trim()) !== index ? "error" : undefined}
                onChange={(event) => update(index, { name: event.target.value })}
              />
              <Select<StatusKind>
                aria-label={`${index + 1}번째 상태 종류`}
                style={{ width: 100 }}
                value={item.kind}
                onChange={(kind) => update(index, { kind })}
                options={(Object.keys(statusKindLabels) as StatusKind[]).map((kind) => ({ value: kind, label: statusKindLabels[kind] }))}
              />
              <Button aria-label="위로" icon={<ArrowUpOutlined />} disabled={index === 0} onClick={() => move(index, -1)} />
              <Button aria-label="아래로" icon={<ArrowDownOutlined />} disabled={index === draft.length - 1} onClick={() => move(index, 1)} />
              <Button aria-label="지우기" danger icon={<DeleteOutlined />} onClick={() => setEdited(draft.filter((_, at) => at !== index))} />
            </Space>
          ))}
          {problem && edited !== null && <Alert type="warning" showIcon message={problem} />}
          <Space>
            <Button icon={<PlusOutlined />} disabled={draft.length >= MAX_STATUSES} onClick={() => setEdited([...draft, { name: "", kind: "OTHER", key: crypto.randomUUID() }])}>
              상태 추가
            </Button>
            <Button disabled={isDefault && edited === null} onClick={() => submit([], "기본값(대기·통과·탈락)으로 되돌릴까요?")}>
              기본값으로
            </Button>
            <span className="muted">최대 {MAX_STATUSES}개, 이름은 {MAX_STATUS_NAME}자까지</span>
          </Space>
        </Space>
      )}
    </Modal>
  );
}
