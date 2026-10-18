import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Alert, App, Button, DatePicker, Descriptions, Form, Input, Modal, Popconfirm, Radio, Select, Space, Switch, Tag } from "antd";
import { ExportOutlined, SafetyCertificateOutlined } from "@ant-design/icons";
import dayjs, { type Dayjs } from "dayjs";
import utc from "dayjs/plugin/utc";
import timezone from "dayjs/plugin/timezone";
import { api, useSingleFlight } from "./api";
import { CursorTable, SearchSelect, useCursorList } from "./list";

dayjs.extend(utc);
dayjs.extend(timezone);
/** Operator-entered times are Korea time regardless of the browser's zone (contract "일정 직접 지정"). */
const SEOUL = "Asia/Seoul";

type Schedule = {
  id: number;
  title: string;
  kind: string;
  status: string;
  startAt: string | null;
  endAt: string | null;
  timeZone: string | null;
  startDate: string | null;
  endDateExclusive: string | null;
  dateLabel: string;
  address: { addressLine1: string; addressLine2: string } | null;
  conferenceUrl: string | null;
  /** Set by an operator in the editor; the collector leaves such schedules alone. Absent on older API responses. */
  curated?: boolean;
};
/** `PATCH activities/:id` schedule value: all-day dates, Korea-time instants, or null to drop the structured dates. */
type ScheduleInput = { startDate: string; endDate: string } | { startAt: string; endAt: string } | null;
type DuplicateKind = "alias" | "exclude";
export type PublicationStatus = "DRAFT" | "PUBLISHED";
/** 대상자 조건(contract "대상자 조건(eligibility)"): a nested group of conditions. JSON keys are camelCase; snake_case is DB-only. */
export type EligibilityCondition = {
  field: string;
  fieldEn: string;
  from?: number | null;
  to?: number | null;
  values?: string[] | null;
  valuesEn?: string[] | null;
  note?: string | null;
  noteEn?: string | null;
};
export type Eligibility = { op: "all" | "any"; items: (Eligibility | EligibilityCondition)[] };
/** The fields `PATCH activities/:id` edits; the tree and the list both open the editor with them. */
export type ActivityText = {
  id: number;
  name: string;
  nameEn: string | null;
  description: string;
  descriptionEn: string | null;
  /** Absent on older API responses; the editor treats that as none. */
  eligibility?: Eligibility | null;
  /** The tree opens the editor without schedules; then the 활동 기간·모집 기간 blocks are hidden. */
  application?: Schedule | null;
  activitySchedule?: Schedule;
};
type Activity = ActivityText & {
  organization: { id: number; name: string };
  program: { id: number; name: string };
  officialUrl: string;
  application: Schedule | null;
  activitySchedule: Schedule;
  schedules: Schedule[];
  publicationStatus: PublicationStatus;
  officialSource: boolean;
  applicationUrl: string | null;
  location: string | null;
  cost: string | null;
  audience: string | null;
  sourceCheckedAt: string | null;
};

export const statusLabels: Record<PublicationStatus, string> = { DRAFT: "초안", PUBLISHED: "게시됨" };

/** Exact times in the schedule's own time zone, all-day ranges as dates, otherwise the label. Never invents a time. */
function scheduleText(schedule: Schedule | null): string {
  if (!schedule) return "미정";
  const range = (start: string | null, end: string | null) =>
    start && end ? (start === end ? start : `${start} ~ ${end}`) : start ? `${start} 부터` : `~ ${end}`;
  if (schedule.startAt || schedule.endAt) {
    const format = (value: string | null) =>
      value &&
      new Date(value).toLocaleString("ko-KR", {
        dateStyle: "medium",
        timeStyle: "short",
        timeZone: schedule.timeZone ?? undefined,
      });
    return range(format(schedule.startAt), format(schedule.endAt));
  }
  if (schedule.startDate || schedule.endDateExclusive) {
    const lastDay = schedule.endDateExclusive && new Date(Date.parse(schedule.endDateExclusive) - 86_400_000).toISOString().slice(0, 10);
    return range(schedule.startDate, lastDay);
  }
  return schedule.dateLabel || "미정";
}

/** Editor state for one schedule block: the picked range and whether it carries times. */
type RangeDraft = { range: [Dayjs, Dayjs] | null; timed: boolean };
/** Structured dates of a collected schedule → editor state (dates come back as Korea-time days; `endDateExclusive` becomes the last day). */
function rangeDraft(schedule: Schedule | null | undefined): RangeDraft {
  if (!schedule) return { range: null, timed: false };
  if (schedule.startAt && schedule.endAt) return { range: [dayjs(schedule.startAt).tz(SEOUL), dayjs(schedule.endAt).tz(SEOUL)], timed: true };
  if (schedule.startDate && schedule.endDateExclusive)
    return { range: [dayjs.tz(schedule.startDate, SEOUL), dayjs.tz(schedule.endDateExclusive, SEOUL).subtract(1, "day")], timed: false };
  return { range: null, timed: false };
}
/** Editor state → PATCH value. Times keep the +09:00 offset so the server stores Korea time. */
function scheduleInput({ range, timed }: RangeDraft): ScheduleInput {
  if (!range) return null;
  const [start, end] = range.map((value) => value.tz(SEOUL));
  return timed
    ? { startAt: start.second(0).millisecond(0).format("YYYY-MM-DDTHH:mm:ssZ"), endAt: end.second(0).millisecond(0).format("YYYY-MM-DDTHH:mm:ssZ") }
    : { startDate: start.format("YYYY-MM-DD"), endDate: end.format("YYYY-MM-DD") };
}

/** One-line Korean reading of the eligibility JSON for operators: `나이 19~34세 · 재학상태 재학 또는 취업상태 미취업`. */
export function eligibilitySummary(eligibility: Eligibility | null | undefined): string {
  if (!eligibility || typeof eligibility !== "object") return "";
  const unit = (field: string) => (field === "나이" ? "세" : field === "경력연수" ? "년" : "");
  const condition = (item: EligibilityCondition): string => {
    const label = item.field || item.fieldEn || "조건";
    if (item.fieldEn === "other" || item.field === "기타") return item.note || item.noteEn || label;
    const hasFrom = item.from !== undefined && item.from !== null,
      hasTo = item.to !== undefined && item.to !== null;
    if (hasFrom || hasTo) {
      const suffix = unit(item.field);
      if (hasFrom && hasTo) return `${label} ${item.from}~${item.to}${suffix}`;
      if (hasFrom) return `${label} ${item.from}${suffix} 이상`;
      return `${label} ${item.to}${suffix} 이하`;
    }
    if (item.values?.length) return `${label} ${item.values.join("/")}`;
    return label;
  };
  const group = (node: Eligibility): string =>
    (Array.isArray(node.items) ? node.items : [])
      .map((item) => ("items" in item && Array.isArray(item.items) ? `(${group(item as Eligibility)})` : condition(item as EligibilityCondition)))
      .filter(Boolean)
      .join(node.op === "any" ? " 또는 " : " · ");
  return group(eligibility);
}

/** JSON text → eligibility (null for blank). Throws with a Korean message when the text is not valid JSON. */
function parseEligibility(text: string): Eligibility | null {
  const trimmed = text.trim();
  if (!trimmed) return null;
  let parsed: unknown;
  try {
    parsed = JSON.parse(trimmed);
  } catch {
    throw new Error("JSON 형식이 아니에요. 중괄호·따옴표·쉼표를 확인해 주세요.");
  }
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed) || !Array.isArray((parsed as Eligibility).items))
    throw new Error("최상위는 { \"op\": \"all\"|\"any\", \"items\": [...] } 그룹이어야 해요.");
  return parsed as Eligibility;
}

export function Activities() {
  const [search, setSearch] = useState(""),
    [organizationId, setOrganizationId] = useState<number>(),
    [programId, setProgramId] = useState<number>(),
    [publicationStatus, setPublicationStatus] = useState<PublicationStatus>(),
    [editing, setEditing] = useState<Activity>(),
    [marking, setMarking] = useState<Activity>();
  const cursor = useCursorList<Activity>("activities", { q: search, organizationId, programId, publicationStatus });
  const { modal, message } = App.useApp();
  const client = useQueryClient();
  const publication = useMutation({
    mutationFn: ({ id, status }: { id: number; status: PublicationStatus }) =>
      api<Activity>(`activities/${id}/publication-status`, "PATCH", { publicationStatus: status }),
    onSuccess: (activity) => {
      client.invalidateQueries({ queryKey: ["activities"] });
      message.success(activity.publicationStatus === "PUBLISHED" ? "게시했어요." : "게시를 취소하고 초안으로 돌렸어요.");
    },
    onError: (error) => message.error(error.message),
  });
  const publishOnce = useSingleFlight();
  const remove = useMutation({
    mutationFn: (id: number) => api<void>(`activities/${id}`, "DELETE"),
    onSuccess: async () => {
      message.success("삭제했어요.");
      await client.invalidateQueries({ queryKey: ["activities"] });
    },
  });
  const removeOnce = useSingleFlight();
  function toggle(activity: Activity) {
    const status = activity.publicationStatus === "PUBLISHED" ? "DRAFT" : "PUBLISHED";
    if (status === "PUBLISHED" && !activity.officialSource)
      modal.confirm({
        title: "웹 검색 출처 초안이에요. 게시할까요?",
        content:
          "이 활동은 프로그램의 공식 도메인이 아닌 웹 전체 검색에서 모았어요. 공식 안내 링크를 열어 주최 기관의 원문인지, 일정·모집 정보가 맞는지 확인한 뒤 게시해 주세요. 게시하면 앱과 웹 탐색에 바로 보여요.",
        okText: "확인했고 게시",
        cancelText: "취소",
        onOk: () => publication.mutateAsync({ id: activity.id, status }),
      });
    else publishOnce(() => publication.mutateAsync({ id: activity.id, status }));
  }
  return (
    <>
      <header className="page-heading">
        <div>
          <span className="eyebrow">DISCOVERY</span>
          <h1>활동 관리</h1>
          <p>매일 밤 수집한 초안을 공식 정보와 대조하고, 탐색에 보여줄 활동을 게시하세요.</p>
        </div>
      </header>
      <div className="editorial-note">
        <SafetyCertificateOutlined aria-hidden="true" />
        <div>
          <strong>게시한 활동만 앱과 웹에 보여요.</strong>
          <span>‘웹 검색 출처’ 초안은 공식 도메인 밖에서 모은 것이라 원문을 확인한 뒤 게시해 주세요.</span>
        </div>
      </div>
      <div className="list-toolbar">
        <Space wrap>
          <Input.Search
            aria-label="활동 검색"
            placeholder="활동 이름·설명 검색"
            allowClear
            onSearch={setSearch}
            style={{ width: 260 }}
          />
          <SearchSelect
            path="organizations"
            aria-label="조직 필터"
            placeholder="전체 조직"
            value={organizationId}
            onChange={(id) => {
              setOrganizationId(id);
              setProgramId(undefined);
            }}
            style={{ width: 190 }}
          />
          <SearchSelect
            key={organizationId ?? "all"}
            path="programs"
            params={{ organizationId }}
            aria-label="프로그램 필터"
            placeholder="전체 프로그램"
            value={programId}
            onChange={setProgramId}
            style={{ width: 190 }}
          />
          <Select
            virtual={false}
            aria-label="게시 상태 필터"
            placeholder="전체 게시 상태"
            allowClear
            options={Object.entries(statusLabels).map(([value, label]) => ({ value, label }))}
            value={publicationStatus}
            onChange={setPublicationStatus}
            style={{ width: 150 }}
          />
        </Space>
      </div>
      <CursorTable
        cursor={cursor}
        scroll={{ x: 1000 }}
        locale={{ emptyText: "조건에 맞는 활동이 없어요." }}
        expandable={{ expandedRowRender: (activity) => <ActivityDetail activity={activity} /> }}
        columns={[
          {
            title: "활동",
            width: "32%",
            render: (_, activity) => (
              <div className="activity-title">
                <strong>
                  {activity.name}
                  {activity.nameEn && <span className="name-en">{activity.nameEn}</span>}
                </strong>
                <span>
                  {activity.organization.name} · {activity.program.name}
                </span>
              </div>
            ),
          },
          {
            title: "출처",
            render: (_, { officialSource }) =>
              officialSource ? <Tag color="green">공식 출처</Tag> : <Tag color="orange">웹 검색 출처</Tag>,
          },
          {
            title: "게시",
            render: (_, { publicationStatus }) => (
              <Tag color={publicationStatus === "PUBLISHED" ? "blue" : undefined}>{statusLabels[publicationStatus]}</Tag>
            ),
          },
          {
            title: "모집 기간",
            render: (_, activity) => (
              <>
                {scheduleText(activity.application)}
                {activity.application?.curated && <CuratedTag />}
              </>
            ),
          },
          {
            title: "활동 일정",
            render: (_, activity) => (
              <>
                {scheduleText(activity.activitySchedule)}
                {activity.activitySchedule.curated && <CuratedTag />}
              </>
            ),
          },
          {
            title: "",
            className: "actions",
            render: (_, activity) => (
              <>
                <Space>
                  <a href={activity.officialUrl} target="_blank" rel="noreferrer">
                    공식 안내 <ExportOutlined aria-hidden="true" />
                  </a>
                  <Button
                    type={activity.publicationStatus === "PUBLISHED" ? "default" : "primary"}
                    loading={publication.isPending && publication.variables?.id === activity.id}
                    disabled={publication.isPending && publication.variables?.id !== activity.id}
                    onClick={() => toggle(activity)}
                  >
                    {activity.publicationStatus === "PUBLISHED" ? "게시 취소" : "게시"}
                  </Button>
                  <Button onClick={() => setEditing(activity)}>편집</Button>
                  <Button onClick={() => setMarking(activity)}>중복으로 지정</Button>
                  <Popconfirm
                    title={`‘${activity.name}’ 활동을 삭제할까요?`}
                    description="삭제하면 되돌릴 수 없고, 수집으로 생긴 활동은 다시 수집되지 않아요."
                    okText="삭제"
                    cancelText="취소"
                    okButtonProps={{ danger: true }}
                    disabled={remove.isPending}
                    onConfirm={() => removeOnce(() => remove.mutateAsync(activity.id))}
                  >
                    <Button
                      danger
                      loading={remove.isPending && remove.variables === activity.id}
                      disabled={remove.isPending && remove.variables !== activity.id}
                    >
                      삭제
                    </Button>
                  </Popconfirm>
                </Space>
                {remove.error && remove.variables === activity.id && (
                  <div className="tree-error" role="alert">
                    {remove.error.message}
                  </div>
                )}
              </>
            ),
          },
        ]}
      />
      {editing && <ActivityTextEditor activity={editing} onClose={() => setEditing(undefined)} />}
      {marking && <DuplicateDialog activity={marking} onClose={() => setMarking(undefined)} />}
    </>
  );
}

function CuratedTag() {
  return (
    <Tag color="purple" style={{ marginLeft: 6 }}>
      운영자 지정
    </Tag>
  );
}

/**
 * "중복으로 지정": the activity is deleted and its name joins the program's 조심 목록, either as an alias of the
 * canonical activity (future collections update that one) or as a name this program never collects again.
 */
function DuplicateDialog({ activity, onClose }: { activity: Activity; onClose: () => void }) {
  const [form] = Form.useForm();
  const kind: DuplicateKind = Form.useWatch("kind", form) ?? "alias";
  const { message } = App.useApp();
  const client = useQueryClient();
  const mark = useMutation({
    mutationFn: (body: { kind: DuplicateKind; canonicalActivityId?: number; reason?: string }) =>
      api<void>(`activities/${activity.id}/duplicate`, "POST", body),
    onSuccess: async () => {
      message.success(
        kind === "alias"
          ? `‘${activity.name}’을(를) 지우고, 이 이름은 정본 활동의 다른 표기로 조심 목록에 넣었어요. 다음 수집부터 정본으로 모여요.`
          : `‘${activity.name}’을(를) 지우고, 이 이름은 이 프로그램의 제외 이름으로 조심 목록에 넣었어요. 다음 수집부터 만들지 않아요.`,
        6,
      );
      onClose();
      await Promise.all([client.invalidateQueries({ queryKey: ["activities"] }), client.invalidateQueries({ queryKey: ["programs"] })]);
    },
  });
  const markOnce = useSingleFlight();
  return (
    <Modal
      open
      title={`중복으로 지정 · ${activity.name}`}
      onCancel={onClose}
      onOk={() => form.submit()}
      confirmLoading={mark.isPending}
      okText="지정하고 삭제"
      okButtonProps={{ danger: true }}
      cancelText="취소"
      width={560}
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={{ kind: "alias" }}
        onFinish={(values: { kind: DuplicateKind; canonicalActivityId?: number; reason?: string }) =>
          markOnce(() =>
            mark.mutateAsync({
              kind: values.kind,
              canonicalActivityId: values.kind === "alias" ? values.canonicalActivityId : undefined,
              reason: values.reason?.trim() || undefined,
            }),
          )
        }
      >
        {mark.error && <Alert className="form-error" type="error" showIcon message={mark.error.message} />}
        <p className="field-note">
          이 활동은 삭제되고 되돌릴 수 없어요. 이름 ‘{activity.name}’은 {activity.program.name}의 조심 목록에 남아 수집기가 다음부터 피해요.
        </p>
        <Form.Item name="kind" label="어떤 중복인가요?">
          <Radio.Group
            options={[
              { value: "alias", label: "같은 활동이다 — 정본 활동의 다른 표기" },
              { value: "exclude", label: "이 프로그램 활동이 아니다" },
            ]}
          />
        </Form.Item>
        {kind === "alias" && (
          <Form.Item
            name="canonicalActivityId"
            label="정본 활동"
            rules={[
              { required: true, message: "정본 활동을 골라 주세요." },
              { validator: async (_, value?: number) => (value === activity.id ? Promise.reject(new Error("자기 자신은 정본이 될 수 없어요.")) : undefined) },
            ]}
          >
            <SearchSelect path="activities" params={{ programId: activity.program.id }} placeholder="같은 프로그램의 활동 이름으로 검색" />
          </Form.Item>
        )}
        <Form.Item name="reason" label="사유(선택)">
          <Input placeholder="예: 띄어쓰기만 다른 같은 기수" maxLength={200} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

type TextBody = { name: string; nameEn: string; description: string; descriptionEn: string; eligibility: Eligibility | null };
type ScheduleBody = { activitySchedule?: ScheduleInput; application?: ScheduleInput };

export function ActivityTextEditor({ activity, onClose }: { activity: ActivityText; onClose: () => void }) {
  const [form] = Form.useForm();
  const eligibilityText: string = Form.useWatch("eligibility", form) ?? "";
  const activityTimed: boolean = Form.useWatch("activityTimed", form) ?? false;
  const applicationTimed: boolean = Form.useWatch("applicationTimed", form) ?? false;
  // Only the list passes schedules; the tree does not, so the blocks (and the PATCH keys) are skipped there.
  const withSchedules = activity.activitySchedule !== undefined;
  const initialActivity = rangeDraft(activity.activitySchedule);
  const initialApplication = rangeDraft(activity.application);
  const { message } = App.useApp();
  const client = useQueryClient();
  const save = useMutation({
    mutationFn: (body: TextBody & ScheduleBody) => api<ActivityText>(`activities/${activity.id}`, "PATCH", body),
    onSuccess: async () => {
      message.success("저장했어요.");
      onClose();
      await client.invalidateQueries({ queryKey: ["activities"] }); // The list and every expanded tree level.
    },
  });
  const saveOnce = useSingleFlight();
  return (
    <Modal
      open
      title={`편집 · ${activity.name}`}
      onCancel={onClose}
      onOk={() => form.submit()}
      confirmLoading={save.isPending}
      okText="저장"
      cancelText="취소"
      width={620}
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={{
          name: activity.name,
          nameEn: activity.nameEn ?? "",
          description: activity.description,
          descriptionEn: activity.descriptionEn ?? "",
          eligibility: activity.eligibility ? JSON.stringify(activity.eligibility, null, 2) : "",
          activityRange: initialActivity.range,
          activityTimed: initialActivity.timed,
          applicationRange: initialApplication.range,
          applicationTimed: initialApplication.timed,
        }}
        // Every text field is sent: an empty English field clears it, an empty 대상자 조건 sends null.
        // Schedule keys go only when the operator changed them: a cleared picker sends null, untouched blocks are omitted.
        onFinish={(values: {
          name: string;
          nameEn: string;
          description: string;
          descriptionEn: string;
          eligibility: string;
          activityRange: RangeDraft["range"];
          activityTimed: boolean;
          applicationRange: RangeDraft["range"];
          applicationTimed: boolean;
        }) => {
          const schedules: ScheduleBody = {};
          if (withSchedules) {
            const nextActivity = scheduleInput({ range: values.activityRange, timed: values.activityTimed });
            if (JSON.stringify(nextActivity) !== JSON.stringify(scheduleInput(initialActivity))) schedules.activitySchedule = nextActivity;
            const nextApplication = scheduleInput({ range: values.applicationRange, timed: values.applicationTimed });
            if (JSON.stringify(nextApplication) !== JSON.stringify(scheduleInput(initialApplication))) schedules.application = nextApplication;
          }
          saveOnce(() =>
            save.mutateAsync({
              name: values.name.trim(),
              nameEn: values.nameEn.trim(),
              description: values.description.trim(),
              descriptionEn: values.descriptionEn.trim(),
              eligibility: parseEligibility(values.eligibility ?? ""),
              ...schedules,
            }),
          );
        }}
      >
        {save.error && <Alert className="form-error" type="error" showIcon message={save.error.message} />}
        <p className="field-note">초안은 다음 수집 때 덮어써질 수 있어요. 고친 내용을 지키려면 게시해 주세요. 직접 지정한 기간은 수집이 덮어쓰지 않아요.</p>
        {withSchedules && (
          <>
            <ScheduleBlock
              title="활동 기간"
              schedule={activity.activitySchedule}
              rangeName="activityRange"
              timedName="activityTimed"
              timed={activityTimed}
            />
            <ScheduleBlock
              title="모집 기간"
              schedule={activity.application}
              rangeName="applicationRange"
              timedName="applicationTimed"
              timed={applicationTimed}
            />
          </>
        )}
        <Form.Item name="name" label="한글 이름" rules={[{ required: true, whitespace: true, message: "이름을 입력해 주세요." }]}>
          <Input />
        </Form.Item>
        <Form.Item name="nameEn" label="영문 이름(선택)">
          <Input />
        </Form.Item>
        <Form.Item name="description" label="한글 설명">
          <Input.TextArea autoSize={{ minRows: 3, maxRows: 10 }} />
        </Form.Item>
        <Form.Item name="descriptionEn" label="영문 설명(선택)">
          <Input.TextArea autoSize={{ minRows: 3, maxRows: 10 }} />
        </Form.Item>
        <Form.Item
          name="eligibility"
          label="대상자 조건(JSON, 선택)"
          extra={
            <span>
              {(() => {
                try {
                  const summary = eligibilitySummary(parseEligibility(eligibilityText));
                  return summary ? `읽기: ${summary}` : "비워 두면 조건 없음으로 저장해요. 범위는 from·to, 어휘는 한글 field + 영문 fieldEn(camelCase).";
                } catch {
                  return "";
                }
              })()}
            </span>
          }
          rules={[
            {
              validator: (_, value: string) => {
                try {
                  parseEligibility(value ?? "");
                  return Promise.resolve();
                } catch (error) {
                  return Promise.reject(error instanceof Error ? error.message : "JSON 형식이 아니에요.");
                }
              },
            },
          ]}
        >
          <Input.TextArea
            autoSize={{ minRows: 4, maxRows: 16 }}
            spellCheck={false}
            style={{ fontFamily: "ui-monospace, SFMono-Regular, Menlo, monospace", fontSize: 12 }}
            placeholder={'{ "op": "all", "items": [ { "field": "나이", "fieldEn": "age", "from": 19, "to": 34 } ] }'}
          />
        </Form.Item>
      </Form>
    </Modal>
  );
}

/** One schedule block of the editor: the collected phrase to read from, a date range, and whether to pin times. */
function ScheduleBlock({
  title,
  schedule,
  rangeName,
  timedName,
  timed,
}: {
  title: string;
  schedule: Schedule | null | undefined;
  rangeName: string;
  timedName: string;
  timed: boolean;
}) {
  return (
    <div className="schedule-block">
      <div className="schedule-block-heading">
        <strong>{title}</strong>
        {schedule?.curated && <CuratedTag />}
      </div>
      <p className="field-note">
        수집 원문: {schedule?.dateLabel ? <q>{schedule.dateLabel}</q> : <span className="muted">없음</span>}
      </p>
      <Space align="start" wrap>
        <Form.Item name={rangeName} label="시작 ~ 종료" style={{ marginBottom: 8 }}>
          <DatePicker.RangePicker
            allowClear
            showTime={timed ? { format: "HH:mm", minuteStep: 5 } : false}
            format={timed ? "YYYY-MM-DD HH:mm" : "YYYY-MM-DD"}
            placeholder={["시작", "종료"]}
          />
        </Form.Item>
        <Form.Item name={timedName} label="시각 지정" valuePropName="checked" style={{ marginBottom: 8 }}>
          <Switch />
        </Form.Item>
      </Space>
      <p className="field-note">날짜만 고르면 종일 일정, 시각을 켜면 한국 시간으로 저장해요. 비워 두면 원문 문구만 남아요.</p>
    </div>
  );
}

function ActivityDetail({ activity }: { activity: Activity }) {
  const link = (url: string | null) =>
    url ? (
      <a href={url} target="_blank" rel="noreferrer">
        {url}
      </a>
    ) : (
      "없음"
    );
  return (
    <Descriptions
      size="small"
      column={{ xs: 1, md: 2 }}
      items={[
        { label: "설명", span: "filled", children: activity.description || "없음" },
        { label: "영문 설명", span: "filled", children: activity.descriptionEn || "없음" },
        {
          label: "활동 기간",
          children: (
            <>
              {scheduleText(activity.activitySchedule)}
              {activity.activitySchedule.curated && <CuratedTag />}
              {activity.activitySchedule.dateLabel && <div className="muted">원문: {activity.activitySchedule.dateLabel}</div>}
            </>
          ),
        },
        {
          label: "모집 기간",
          children: (
            <>
              {scheduleText(activity.application)}
              {activity.application?.curated && <CuratedTag />}
              {activity.application?.dateLabel && <div className="muted">원문: {activity.application.dateLabel}</div>}
            </>
          ),
        },
        { label: "장소", children: activity.location ?? "미확인" },
        { label: "비용", children: activity.cost ?? "미확인" },
        {
          label: "대상",
          span: "filled",
          children: (
            <>
              {eligibilitySummary(activity.eligibility) || <span className="muted">조건 없음</span>}
              {activity.audience && (
                <div className="muted" style={{ marginTop: 4 }}>
                  원문: {activity.audience}
                </div>
              )}
            </>
          ),
        },
        {
          label: "원문 확인 시각",
          children: activity.sourceCheckedAt ? new Date(activity.sourceCheckedAt).toLocaleString("ko-KR") : "없음",
        },
        { label: "공식 안내", children: link(activity.officialUrl) },
        { label: "신청 링크", children: link(activity.applicationUrl) },
        {
          label: "개별 일정",
          span: "filled",
          children: activity.schedules.length ? (
            <ul className="schedule-list">
              {activity.schedules.map((schedule) => (
                <li key={schedule.id}>
                  <strong>{schedule.title}</strong> {scheduleText(schedule)}
                  {schedule.address && ` · ${schedule.address.addressLine1} ${schedule.address.addressLine2}`.trimEnd()}
                </li>
              ))}
            </ul>
          ) : (
            "없음"
          ),
        },
      ]}
    />
  );
}
