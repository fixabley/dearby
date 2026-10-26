import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, App, Button, Form, Input, Modal, Popconfirm, Select, Space, Spin, Table, Tabs, Tag, TimePicker, Tooltip } from "antd";
import { ArrowDownOutlined, ArrowUpOutlined, DeleteOutlined, MinusCircleOutlined, PlusOutlined } from "@ant-design/icons";
import dayjs, { type Dayjs } from "dayjs";
import { api, useSingleFlight } from "./api";
import { type Schedule, scheduleText, SEOUL } from "./activities";
import { resolveTagIds, type TagItem, TagSelect, type TagValue, useTags } from "./tag-select";

/** 트랙·세션 시간표 편집 (contract admin-v1 / native-v1 "트랙·세션 시간표", 2026-10-26): the sub-timetable of one schedule block. */

type SessionKind = "TALK" | "KEYNOTE" | "BREAK" | "MEAL" | "OTHER";
const sessionKindLabels: Record<SessionKind, string> = { TALK: "일반", KEYNOTE: "키노트", BREAK: "쉬는시간", MEAL: "점심", OTHER: "기타" };
type Speaker = { name: string; affiliation: string | null };
type TimetableResponse = {
  scheduleId: number;
  tracks: { id: number; name: string; room: string | null; position: number }[];
  sessions: { id: number; trackId: number | null; kind: SessionKind; title: string; description: string | null; startAt: string; endAt: string; speakers: Speaker[]; tags: TagItem[] }[];
};
type TimetableInput = {
  tracks: { id?: number; name: string; room?: string }[];
  sessions: { id?: number; track?: number; kind: SessionKind; title: string; description?: string; startAt: string; endAt: string; speakers: Speaker[]; tagIds: number[] }[];
};

/** Local rows carry a `key` so inputs keep identity while moved; `id` is kept for stored rows so the save keeps their 세션 찜. */
type DraftTrack = { key: string; id?: number; name: string; room: string };
/** `trackKey` null = 전체 트랙(전폭). Times are epoch ms. */
type DraftSession = { key: string; id?: number; trackKey: string | null; kind: SessionKind; title: string; description: string; start: number; end: number; speakers: Speaker[]; tags: TagValue[] };
type Draft = { tracks: DraftTrack[]; sessions: DraftSession[] };

// Server limits (TimetableRules).
const MAX_TRACKS = 20;
const MAX_SESSIONS = 300;
const MAX_SPEAKERS = 10;
const MAX_TAGS = 5;
const ALL_TRACKS = "all";

type BlockRange = { from: number; to: number };
/** The block's time window, or why a timetable cannot hang on it (same cases as the server's 400). 모집 기간 is never offered. */
export function timetableWindow(schedule: Schedule): BlockRange | string {
  if (schedule.recurrence) return "반복 일정에는 시간표를 붙일 수 없어요.";
  if (schedule.startAt || schedule.endAt) {
    if (!schedule.startAt || !schedule.endAt) return "시간표를 붙이려면 일정에 시작과 끝 시각이 모두 있어야 해요.";
    return { from: Date.parse(schedule.startAt), to: Date.parse(schedule.endAt) };
  }
  if (schedule.startDate && schedule.endDateExclusive)
    return { from: dayjs.tz(schedule.startDate, SEOUL).valueOf(), to: dayjs.tz(schedule.endDateExclusive, SEOUL).valueOf() };
  return "날짜가 정해지지 않은 일정에는 시간표를 붙일 수 없어요.";
}

/** `시간표 추가/편집` for one block; disabled with the reason when the block cannot carry a timetable. */
export function TimetableButton({ activityId, schedule }: { activityId: number; schedule: Schedule }) {
  const [open, setOpen] = useState(false);
  const block = timetableWindow(schedule);
  const button = (
    <Button size="small" disabled={typeof block === "string"} onClick={() => setOpen(true)}>
      {schedule.hasTimetable ? "시간표 편집" : "시간표 추가"}
    </Button>
  );
  return (
    <>
      {typeof block === "string" ? <Tooltip title={block}>{button}</Tooltip> : button}
      {open && typeof block !== "string" && <TimetableModal activityId={activityId} schedule={schedule} block={block} onClose={() => setOpen(false)} />}
    </>
  );
}

const seoul = (ms: number) => dayjs(ms).tz(SEOUL);
const clock = (ms: number) => seoul(ms).format("HH:mm");
/** Seoul days the block touches, as YYYY-MM-DD (an all-day block ends at the next midnight, which is not a day of its own). */
function windowDays({ from, to }: BlockRange): string[] {
  const days: string[] = [];
  for (let day = seoul(from).startOf("day"); day.valueOf() < to && days.length < 366; day = day.add(1, "day")) days.push(day.format("YYYY-MM-DD"));
  return days;
}

function fromResponse(data: TimetableResponse | undefined): Draft {
  return {
    tracks: [...(data?.tracks ?? [])].sort((a, b) => a.position - b.position).map((track) => ({ key: `track-${track.id}`, id: track.id, name: track.name, room: track.room ?? "" })),
    sessions: (data?.sessions ?? []).map((session) => ({
      key: `session-${session.id}`,
      id: session.id,
      trackKey: session.trackId == null ? null : `track-${session.trackId}`,
      kind: session.kind,
      title: session.title,
      description: session.description ?? "",
      start: Date.parse(session.startAt),
      end: Date.parse(session.endAt),
      speakers: session.speakers,
      tags: session.tags.map((tag) => tag.id),
    })),
  };
}

const byStart = (a: DraftSession, b: DraftSession) => a.start - b.start || a.end - b.end;

/** Client copy of the server rules (TimetableRules.validate), so most 400s show before saving. */
function problemsOf({ tracks, sessions }: Draft, { from, to }: BlockRange): string[] {
  const problems: string[] = [];
  const names = tracks.map((track) => track.name.trim());
  if (names.some((name) => !name)) problems.push("트랙 이름을 모두 입력해 주세요.");
  if (new Set(names.filter(Boolean)).size !== names.filter(Boolean).length) problems.push("같은 이름의 트랙이 있어요.");
  if (sessions.length > MAX_SESSIONS) problems.push(`세션은 ${MAX_SESSIONS}개까지예요.`);
  const sorted = [...sessions].sort(byStart);
  for (const session of sorted) {
    if (session.end <= session.start) problems.push(`끝이 시작보다 뒤여야 해요: ${session.title}`);
    else if (session.start < from || session.end > to) problems.push(`일정 시간 밖이에요: ${session.title}`);
  }
  sorted.forEach((a, index) => {
    for (const b of sorted.slice(index + 1)) {
      const sameLane = a.trackKey === null || b.trackKey === null || a.trackKey === b.trackKey;
      if (sameLane && a.start < b.end && b.start < a.end) problems.push(`같은 트랙(또는 전체 트랙) 세션끼리 시간이 겹쳐요: ${a.title} / ${b.title}`);
    }
  });
  return problems;
}

function TimetableModal({ activityId, schedule, block, onClose }: { activityId: number; schedule: Schedule; block: BlockRange; onClose: () => void }) {
  const path = `activities/${activityId}/schedules/${schedule.id}/timetable`;
  const queryKey = ["timetable", activityId, schedule.id];
  const { message, modal } = App.useApp();
  const client = useQueryClient();
  const saveOnce = useSingleFlight();
  const tags = useTags();
  const loaded = useQuery({ queryKey, queryFn: () => api<TimetableResponse>(path) });
  const [edited, setEdited] = useState<Draft | null>(null);
  const [form, setForm] = useState<{ session?: DraftSession } | null>(null);
  const draft = edited ?? fromResponse(loaded.data);
  const save = useMutation({
    mutationFn: async (next: Draft) => {
      const body: TimetableInput = {
        tracks: next.tracks.map((track) => ({ ...(track.id != null && { id: track.id }), name: track.name.trim(), ...(track.room.trim() && { room: track.room.trim() }) })),
        sessions: [],
      };
      for (const session of [...next.sessions].sort(byStart)) {
        const track = next.tracks.findIndex((item) => item.key === session.trackKey);
        body.sessions.push({
          ...(session.id != null && { id: session.id }),
          ...(track >= 0 && { track }),
          kind: session.kind,
          title: session.title.trim(),
          ...(session.description.trim() && { description: session.description.trim() }),
          startAt: seoul(session.start).format("YYYY-MM-DDTHH:mm:ssZ"),
          endAt: seoul(session.end).format("YYYY-MM-DDTHH:mm:ssZ"),
          speakers: session.speakers,
          tagIds: await resolveTagIds(session.tags, client),
        });
      }
      return api<TimetableResponse>(path, "PUT", body);
    },
    onSuccess: async (_, next) => {
      message.success(next.sessions.length || next.tracks.length ? "시간표를 저장했어요." : "시간표를 지웠어요.");
      // Activity responses carry `hasTimetable` and the block's 운영자 지정 mark.
      await Promise.all([client.invalidateQueries({ queryKey }), client.invalidateQueries({ queryKey: ["activities"] })]);
      onClose();
    },
  });
  const storedSessions = loaded.data?.sessions ?? [];
  const dropped = (next: Draft) => storedSessions.filter((stored) => !next.sessions.some((kept) => kept.id === stored.id));
  const submit = (next: Draft) => {
    const removed = dropped(next);
    if (!removed.length) return saveOnce(() => save.mutateAsync(next));
    modal.confirm({
      title: next.sessions.length || next.tracks.length ? "시간표를 저장할까요?" : "시간표를 지울까요?",
      content: `세션 ${removed.length}개(${removed.map((session) => session.title).slice(0, 3).join(", ")}${removed.length > 3 ? " 등" : ""})와 그 세션의 찜이 함께 지워져요.`,
      okText: next.sessions.length || next.tracks.length ? "저장" : "지우기",
      okButtonProps: { danger: true },
      cancelText: "취소",
      onOk: () => saveOnce(() => save.mutateAsync(next)),
    });
  };
  const problems = problemsOf(draft, block);
  const days = windowDays(block);
  const tagName = (value: TagValue) => (typeof value === "string" ? value : (tags.data?.find((tag) => tag.id === value)?.name ?? `#${value}`));
  const trackName = (key: string | null) => (key === null ? "전체 트랙" : (draft.tracks.find((track) => track.key === key)?.name || "이름 없는 트랙"));
  const sessionTime = (session: DraftSession) => `${days.length > 1 ? `${seoul(session.start).format("M/D")} ` : ""}${clock(session.start)}–${clock(session.end)}`;

  const updateTrack = (index: number, change: Partial<DraftTrack>) => setEdited({ ...draft, tracks: draft.tracks.map((track, at) => (at === index ? { ...track, ...change } : track)) });
  const moveTrack = (index: number, by: number) => {
    const tracks = [...draft.tracks];
    [tracks[index], tracks[index + by]] = [tracks[index + by], tracks[index]];
    setEdited({ ...draft, tracks });
  };
  const removeTrack = (key: string) => setEdited({ tracks: draft.tracks.filter((track) => track.key !== key), sessions: draft.sessions.filter((session) => session.trackKey !== key) });
  const isEmpty = !loaded.data?.tracks.length && !storedSessions.length;

  return (
    <Modal
      open
      title={`시간표 · ${schedule.title}`}
      onCancel={onClose}
      width={1100}
      destroyOnClose
      footer={
        <Space>
          <Button danger disabled={isEmpty || loaded.isPending} onClick={() => submit({ tracks: [], sessions: [] })}>
            시간표 삭제
          </Button>
          <Button onClick={onClose}>취소</Button>
          <Button type="primary" loading={save.isPending} disabled={edited === null || problems.length > 0} onClick={() => submit(draft)}>
            저장
          </Button>
        </Space>
      }
    >
      <p className="field-note">
        블록 시간 {scheduleText(schedule)}. 세션은 이 안에 있어야 하고, 같은 트랙의 세션끼리와 전체 트랙 세션은 다른 세션과 시간이 겹치면 안 돼요. 시간은 한국 시간이에요. 저장 전에는 앱에 반영되지 않아요.
      </p>
      {loaded.error && <Alert className="form-error" type="error" showIcon message={loaded.error.message} />}
      {save.error && <Alert className="form-error" type="error" showIcon message={save.error.message} />}
      {problems.length > 0 && <Alert className="form-error" type="warning" showIcon message="저장하기 전에 고쳐 주세요" description={<ul className="timetable-problems">{problems.map((problem) => <li key={problem}>{problem}</li>)}</ul>} />}
      {loaded.isPending ? (
        <Spin />
      ) : (
        <Tabs
          items={[
            {
              key: "sessions",
              label: `세션 ${draft.sessions.length}`,
              children: (
                <>
                  <Space style={{ marginBottom: 8 }}>
                    <Button icon={<PlusOutlined />} disabled={draft.sessions.length >= MAX_SESSIONS} onClick={() => setForm({})}>
                      세션 추가
                    </Button>
                    <span className="muted">시작 시각 순. 트랙이 없으면 단일 진행이에요.</span>
                  </Space>
                  <Table<DraftSession>
                    size="small"
                    rowKey="key"
                    pagination={false}
                    dataSource={[...draft.sessions].sort(byStart)}
                    locale={{ emptyText: "세션이 없어요." }}
                    columns={[
                      { title: "시간", render: (_, session) => <span className="timetable-time">{sessionTime(session)}</span> },
                      { title: "트랙", render: (_, session) => trackName(session.trackKey) },
                      { title: "종류", dataIndex: "kind", render: (kind: SessionKind) => sessionKindLabels[kind] },
                      { title: "제목", dataIndex: "title" },
                      { title: "연사", render: (_, session) => session.speakers.map((speaker) => (speaker.affiliation ? `${speaker.name}(${speaker.affiliation})` : speaker.name)).join(", ") },
                      { title: "태그", render: (_, session) => session.tags.map((value) => <Tag key={String(value)}>{tagName(value)}</Tag>) },
                      {
                        title: "",
                        render: (_, session) => (
                          <Space>
                            <Button size="small" onClick={() => setForm({ session })}>
                              편집
                            </Button>
                            <Button size="small" danger onClick={() => setEdited({ ...draft, sessions: draft.sessions.filter((item) => item.key !== session.key) })}>
                              삭제
                            </Button>
                          </Space>
                        ),
                      },
                    ]}
                  />
                </>
              ),
            },
            {
              key: "tracks",
              label: `트랙 ${draft.tracks.length}`,
              children: (
                <Space direction="vertical" style={{ width: "100%" }}>
                  <p className="field-note">위에서부터 이 순서로 열이 놓여요. 트랙 색은 앱이 이 순서로 정해요. 트랙을 지우면 그 트랙의 세션도 지워져요.</p>
                  {draft.tracks.map((track, index) => {
                    const count = draft.sessions.filter((session) => session.trackKey === track.key).length;
                    const name = track.name.trim();
                    return (
                      <Space key={track.key} wrap>
                        <Input
                          aria-label={`${index + 1}번째 트랙 이름`}
                          placeholder="트랙 이름 (예: 트랙 A)"
                          style={{ width: 220 }}
                          maxLength={50}
                          value={track.name}
                          status={!name || draft.tracks.findIndex((other) => other.name.trim() === name) !== index ? "error" : undefined}
                          onChange={(event) => updateTrack(index, { name: event.target.value })}
                        />
                        <Input aria-label={`${index + 1}번째 트랙 방`} placeholder="방(선택, 예: 그랜드볼룸)" style={{ width: 260 }} maxLength={100} value={track.room} onChange={(event) => updateTrack(index, { room: event.target.value })} />
                        <Button aria-label="위로" icon={<ArrowUpOutlined />} disabled={index === 0} onClick={() => moveTrack(index, -1)} />
                        <Button aria-label="아래로" icon={<ArrowDownOutlined />} disabled={index === draft.tracks.length - 1} onClick={() => moveTrack(index, 1)} />
                        {count ? (
                          <Popconfirm title="이 트랙을 지울까요?" description={`이 트랙의 세션 ${count}개도 지워져요.`} okText="지우기" okButtonProps={{ danger: true }} onConfirm={() => removeTrack(track.key)}>
                            <Button aria-label="지우기" danger icon={<DeleteOutlined />} />
                          </Popconfirm>
                        ) : (
                          <Button aria-label="지우기" danger icon={<DeleteOutlined />} onClick={() => removeTrack(track.key)} />
                        )}
                        <span className="muted">세션 {count}개</span>
                      </Space>
                    );
                  })}
                  <Space>
                    <Button
                      icon={<PlusOutlined />}
                      disabled={draft.tracks.length >= MAX_TRACKS}
                      onClick={() => setEdited({ ...draft, tracks: [...draft.tracks, { key: crypto.randomUUID(), name: `트랙 ${String.fromCharCode(65 + (draft.tracks.length % 26))}`, room: "" }] })}
                    >
                      트랙 추가
                    </Button>
                    <span className="muted">0~{MAX_TRACKS}개. 0개면 단일 진행이에요.</span>
                  </Space>
                </Space>
              ),
            },
            {
              key: "preview",
              label: "미리보기",
              children: problems.some((problem) => problem.startsWith("같은 트랙") || problem.startsWith("끝이")) ? (
                <Alert type="warning" showIcon message="시간이 겹치거나 잘못된 세션을 고치면 미리볼 수 있어요." />
              ) : (
                <TimetablePreview draft={draft} multiDay={days.length > 1} tagName={tagName} />
              ),
            },
          ]}
        />
      )}
      {form && (
        <SessionForm
          session={form.session}
          tracks={draft.tracks}
          block={block}
          days={days}
          after={draft.sessions.length ? Math.max(...draft.sessions.map((session) => session.end)) : block.from}
          onClose={() => setForm(null)}
          onSave={(session) => {
            setEdited({ ...draft, sessions: form.session ? draft.sessions.map((item) => (item.key === session.key ? session : item)) : [...draft.sessions, session] });
            setForm(null);
          }}
        />
      )}
    </Modal>
  );
}

type SessionValues = {
  kind: SessionKind;
  track: string;
  day: string;
  time: [Dayjs, Dayjs] | null;
  title: string;
  description: string;
  speakers: { name: string; affiliation?: string }[];
  tags: TagValue[];
};

/** Minutes after the Seoul midnight of `day` that the block allows: [earliest start, latest end] (1440 = next midnight). */
function dayLimits(day: string, { from, to }: BlockRange): [number, number] {
  const midnight = dayjs.tz(day, SEOUL).valueOf();
  return [Math.max(0, (from - midnight) / 60_000), Math.min(1440, (to - midnight) / 60_000)];
}
const minuteOf = (value: Dayjs) => value.hour() * 60 + value.minute();

/** Add/edit one session. Times are a day (multi-day blocks only) plus a Korea-time HH:mm range; an end of 00:00 means the next midnight. */
function SessionForm({
  session,
  tracks,
  block,
  days,
  after,
  onClose,
  onSave,
}: {
  session?: DraftSession;
  tracks: DraftTrack[];
  block: BlockRange;
  days: string[];
  /** Default start for a new session: the latest end so far, inside the block. */
  after: number;
  onClose: () => void;
  onSave: (session: DraftSession) => void;
}) {
  const [form] = Form.useForm<SessionValues>();
  const day: string = Form.useWatch("day", form) ?? days[0];
  const [earliest, latest] = dayLimits(day, block);
  const start = session?.start ?? Math.max(block.from, Math.min(after, block.to - 30 * 60_000));
  const end = session?.end ?? Math.min(start + 30 * 60_000, block.to);
  // Time pickers work on a plain local day; only HH:mm is read back.
  const pickerTime = (ms: number) => dayjs().startOf("day").hour(seoul(ms).hour()).minute(seoul(ms).minute());
  return (
    <Modal open title={session ? `세션 편집 · ${session.title}` : "세션 추가"} onCancel={onClose} okText="확인" onOk={() => form.submit()} destroyOnClose width={640}>
      <Form<SessionValues>
        form={form}
        layout="vertical"
        initialValues={{
          kind: session?.kind ?? "TALK",
          track: session?.trackKey ?? (session || !tracks.length ? ALL_TRACKS : tracks[0].key),
          day: days.includes(seoul(start).format("YYYY-MM-DD")) ? seoul(start).format("YYYY-MM-DD") : days[0],
          time: [pickerTime(start), pickerTime(end)],
          title: session?.title ?? "",
          description: session?.description ?? "",
          speakers: session?.speakers.map((speaker) => ({ name: speaker.name, affiliation: speaker.affiliation ?? "" })) ?? [],
          tags: session?.tags ?? [],
        }}
        onFinish={(values) => {
          const midnight = dayjs.tz(values.day, SEOUL);
          const [from, to] = values.time!.map(minuteOf);
          // 00:00 as an end is the next midnight (an all-day block ends there).
          const toMinute = to === 0 && from > 0 ? 1440 : to;
          onSave({
            key: session?.key ?? crypto.randomUUID(),
            ...(session?.id != null && { id: session.id }),
            trackKey: values.track === ALL_TRACKS ? null : values.track,
            kind: values.kind,
            title: values.title.trim(),
            description: values.description.trim(),
            start: midnight.add(from, "minute").valueOf(),
            end: midnight.add(toMinute, "minute").valueOf(),
            speakers: values.speakers.map((speaker) => ({ name: speaker.name.trim(), affiliation: speaker.affiliation?.trim() || null })),
            tags: values.tags,
          });
        }}
      >
        <Space wrap>
          <Form.Item name="kind" label="종류">
            <Select style={{ width: 140 }} options={(Object.keys(sessionKindLabels) as SessionKind[]).map((kind) => ({ value: kind, label: sessionKindLabels[kind] }))} />
          </Form.Item>
          <Form.Item name="track" label="트랙">
            <Select style={{ width: 200 }} options={[{ value: ALL_TRACKS, label: "전체 트랙" }, ...tracks.map((track) => ({ value: track.key, label: track.name.trim() || "이름 없는 트랙" }))]} />
          </Form.Item>
          {days.length > 1 && (
            <Form.Item name="day" label="날짜">
              <Select style={{ width: 160 }} options={days.map((value) => ({ value, label: dayjs(value).format("M월 D일") }))} />
            </Form.Item>
          )}
          <Form.Item
            name="time"
            label="시간 (한국 시간)"
            dependencies={["day"]}
            rules={[
              {
                validator: (_, value: SessionValues["time"]) => {
                  if (!value?.[0] || !value[1]) return Promise.reject(new Error("시작과 끝 시각을 골라 주세요."));
                  const from = minuteOf(value[0]);
                  const to = minuteOf(value[1]) === 0 && from > 0 ? 1440 : minuteOf(value[1]);
                  if (to <= from) return Promise.reject(new Error("끝이 시작보다 뒤여야 해요."));
                  if (from < earliest || to > latest) return Promise.reject(new Error("일정 블록 시간 안으로 골라 주세요."));
                  return Promise.resolve();
                },
              },
            ]}
          >
            <TimePicker.RangePicker
              format="HH:mm"
              minuteStep={5}
              order={false}
              needConfirm={false}
              disabledTime={(_, type) => ({
                // Start: [earliest, latest); end: (earliest, latest], with 00:00 standing for 24:00.
                disabledHours: () =>
                  Array.from({ length: 24 }, (__, hour) => hour).filter((hour) =>
                    type === "end" && hour === 0 && latest === 1440 ? false : hour * 60 + 59 < earliest || hour * 60 > (type === "start" ? latest - 1 : latest),
                  ),
                disabledMinutes: (hour) =>
                  Array.from({ length: 60 }, (__, minute) => minute).filter((minute) => {
                    const at = type === "end" && hour === 0 && latest === 1440 ? 1440 : hour * 60 + minute;
                    return type === "start" ? at < earliest || at >= latest : at <= earliest || at > latest;
                  }),
              })}
            />
          </Form.Item>
        </Space>
        <Form.Item name="title" label="제목" rules={[{ required: true, whitespace: true, message: "제목을 입력해 주세요." }]}>
          <Input maxLength={200} placeholder="예: 대규모 트래픽을 견디는 설계" />
        </Form.Item>
        <Form.Item name="description" label="설명(선택)">
          <Input.TextArea maxLength={2000} autoSize={{ minRows: 2, maxRows: 6 }} />
        </Form.Item>
        <Form.List name="speakers">
          {(fields, { add, remove }) => (
            <Form.Item label={`연사 (최대 ${MAX_SPEAKERS}명)`}>
              <Space direction="vertical" style={{ width: "100%" }}>
                {fields.map((field, index) => (
                  <Space key={field.key} align="baseline" wrap>
                    <Form.Item name={[field.name, "name"]} noStyle rules={[{ required: true, whitespace: true, message: "이름을 입력해 주세요." }]}>
                      <Input aria-label={`${index + 1}번째 연사 이름`} placeholder="이름" maxLength={100} style={{ width: 200 }} />
                    </Form.Item>
                    <Form.Item name={[field.name, "affiliation"]} noStyle>
                      <Input aria-label={`${index + 1}번째 연사 소속`} placeholder="소속(선택)" maxLength={100} style={{ width: 240 }} />
                    </Form.Item>
                    <Button aria-label="연사 지우기" type="text" icon={<MinusCircleOutlined />} onClick={() => remove(field.name)} />
                  </Space>
                ))}
                <Button icon={<PlusOutlined />} disabled={fields.length >= MAX_SPEAKERS} onClick={() => add({ name: "", affiliation: "" })}>
                  연사 추가
                </Button>
              </Space>
            </Form.Item>
          )}
        </Form.List>
        <Form.Item
          name="tags"
          label={`분야 태그 (최대 ${MAX_TAGS}개)`}
          rules={[{ validator: (_, value?: TagValue[]) => ((value?.length ?? 0) > MAX_TAGS ? Promise.reject(new Error(`태그는 ${MAX_TAGS}개까지예요.`)) : Promise.resolve()) }]}
        >
          <TagSelect />
        </Form.Item>
      </Form>
    </Modal>
  );
}

/**
 * The app's table rule (native-v1 "앱·웹 화면"): rows are the intervals between every session boundary that some session covers,
 * columns are tracks in order, a session spans the rows of its time (rowSpan), 전체 트랙 sessions span every column, and a track with
 * nothing at that time keeps an empty cell. No tracks = one column.
 */
function TimetablePreview({ draft, multiDay, tagName }: { draft: Draft; multiDay: boolean; tagName: (value: TagValue) => string }) {
  const { tracks, sessions } = draft;
  if (!sessions.length) return <p className="muted">세션을 추가하면 앱에 보일 표를 여기서 미리 볼 수 있어요.</p>;
  const bounds = [...new Set(sessions.flatMap((session) => [session.start, session.end]))].sort((a, b) => a - b);
  const rows = bounds
    .slice(0, -1)
    .map((from, index) => ({ from, to: bounds[index + 1] }))
    .filter((row) => sessions.some((session) => session.start <= row.from && session.end >= row.to));
  const span = (session: DraftSession) => rows.filter((row) => row.from >= session.start && row.to <= session.end).length;
  const columns: (string | null)[] = tracks.length ? tracks.map((track) => track.key) : [null];
  const card = (session: DraftSession) => (
    <>
      {session.kind !== "TALK" && <Tag>{sessionKindLabels[session.kind]}</Tag>}
      {session.tags.slice(0, 2).map((value) => (
        <Tag key={String(value)} color="cyan">
          {tagName(value)}
        </Tag>
      ))}
      <div className="timetable-title">{session.title}</div>
      {session.speakers.map((speaker, index) => (
        <div key={index} className="muted">
          {speaker.name}
          {speaker.affiliation && ` · ${speaker.affiliation}`}
        </div>
      ))}
    </>
  );
  const band = (session: DraftSession) => (session.kind === "BREAK" || session.kind === "MEAL" ? " band" : "");
  return (
    <div className="timetable-preview">
      <table>
        {tracks.length > 0 && (
          <thead>
            <tr>
              <th />
              {tracks.map((track) => (
                <th key={track.key}>
                  {track.name.trim() || "이름 없는 트랙"}
                  {track.room.trim() && <div className="muted">{track.room.trim()}</div>}
                </th>
              ))}
            </tr>
          </thead>
        )}
        <tbody>
          {rows.map((row) => {
            const wide = sessions.find((session) => session.trackKey === null && session.start <= row.from && session.end >= row.to);
            return (
              <tr key={row.from}>
                <th className="timetable-time">
                  {multiDay && <div className="muted">{seoul(row.from).format("M/D")}</div>}
                  {clock(row.from)} – {clock(row.to)}
                </th>
                {wide
                  ? wide.start === row.from && (
                      <td colSpan={columns.length} rowSpan={span(wide)} className={`timetable-session wide${band(wide)}`}>
                        {card(wide)}
                      </td>
                    )
                  : columns.map((key) => {
                      const session = sessions.find((item) => item.trackKey === key && item.start <= row.from && item.end >= row.to);
                      if (!session) return <td key={key ?? ALL_TRACKS} className="timetable-empty" />;
                      return (
                        session.start === row.from && (
                          <td key={key ?? ALL_TRACKS} rowSpan={span(session)} className={`timetable-session${band(session)}`}>
                            {card(session)}
                          </td>
                        )
                      );
                    })}
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
