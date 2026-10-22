import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, App, Button, Calendar, Form, Input, Modal, Popconfirm, Segmented, Select, Space, Spin, Table, Tag, Tooltip } from "antd";
import { ArrowDownOutlined, ArrowUpOutlined, DeleteOutlined, PlusOutlined } from "@ant-design/icons";
import dayjs, { type Dayjs } from "dayjs";
import { api, useSingleFlight } from "./api";
import {
  CuratedTag,
  type OverlapCheck,
  type OverlapDraft,
  overlapDraft,
  overlapInput,
  overlapUnavailable,
  OverlapUnavailableTag,
  rangeDraft,
  type RangeDraft,
  type Schedule,
  ScheduleBlock,
  scheduleInput,
  scheduleText,
  SEOUL,
} from "./activities";

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
  /** null clears it on PATCH. */
  overlapCheck?: OverlapCheck | null;
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
            { title: "기간", render: (_, schedule) => <>{scheduleText(schedule)}{overlapUnavailable(schedule) && <OverlapUnavailableTag />}</> },
            { title: "원문", dataIndex: "dateLabel", render: (label: string) => label || <span className="muted">없음</span> },
            { title: "사유", dataIndex: "curatedNote", render: (note: string | null | undefined) => note || <span className="muted">없음</span> },
            {
              title: "",
              render: (_, schedule) => (
                <Space>
                  <Button size="small" onClick={() => setForm({ schedule })}>
                    편집
                  </Button>
                  <Button size="small" onClick={() => setStatusesFor(schedule)}>
                    진행 상태
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
      {statusesFor && <ScheduleStatusesModal activityId={activity.id} schedule={statusesFor} onClose={() => setStatusesFor(null)} />}
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
              <Tooltip key={schedule.id} title={`${schedule.title} · ${scheduleText(schedule)}${overlapUnavailable(schedule) ? " · 겹침 확인 불가" : ""}`}>
                <button
                  type="button"
                  className={`schedule-chip kind-${kindOf(schedule.kind).toLowerCase()}${overlapUnavailable(schedule) ? " no-overlap" : ""}`}
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
          overlap: overlapDraft(schedule?.overlapCheck),
        }}
        onFinish={(values: { title: string; kind: ScheduleKind; status: ScheduleStatus; dateLabel: string; range: RangeDraft["range"]; timed: boolean; note: string; overlap: OverlapDraft }) => {
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
              // Always sent, so clearing the pickers clears it (null on POST is simply none).
              overlapCheck: overlapInput(values.overlap),
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
        <ScheduleBlock title="기간" schedule={schedule} rangeName="range" timedName="timed" noteName="note" overlapName="overlap" timed={timed} startOptional />
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
