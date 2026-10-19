import { useEffect, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Alert, App, Button, Collapse, DatePicker, Descriptions, Form, Input, Modal, Popconfirm, Radio, Select, Space, Spin, Switch, Table, type TableColumnsType, Tag, Tooltip } from "antd";
import { ExportOutlined, PlusOutlined, SafetyCertificateOutlined } from "@ant-design/icons";
import dayjs, { type Dayjs } from "dayjs";
import utc from "dayjs/plugin/utc";
import timezone from "dayjs/plugin/timezone";
import { api, useSingleFlight } from "./api";
import { CursorTable, SearchSelect, useCursorList } from "./list";
import { type DraftGroup, type Eligibility, eligibilitySummary, fromDraft, newGroup, toDraft, validateDraft } from "./eligibility";
import { EligibilityEditor } from "./eligibility-editor";
import { type Category, categoryLabel, categoryOptions, categoryOrder, UNCATEGORIZED } from "./category";

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
  /** 사유 the operator left when pinning the period (contract "사유"). */
  curatedNote?: string | null;
};
/** `PATCH activities/:id` schedule value: all-day dates or Korea-time instants (either end may be missing for 모집 기간), an optional 사유, or null to drop the structured dates. */
type ScheduleInput = { startDate?: string; endDate?: string; startAt?: string; endAt?: string; note?: string } | null;
type DuplicateKind = "alias" | "exclude";
export type PublicationStatus = "DRAFT" | "PUBLISHED";
/** 대상자 조건 types live in eligibility.ts (contract "대상자 조건(eligibility)"); re-exported for callers that imported them from here. */
export type { Eligibility, EligibilityCondition } from "./eligibility";
/** The fields `PATCH activities/:id` edits; the tree and the list both open the editor with them. */
export type ActivityText = {
  id: number;
  name: string;
  nameEn: string | null;
  description: string;
  descriptionEn: string | null;
  /** Absent on older API responses; the editor treats that as none. */
  eligibility?: Eligibility | null;
  /** 활동 종류 code (contract "활동 종류"); null or absent = 미분류. */
  category?: string | null;
  /** Absent on the tree path; then the URL fields are hidden (2026-10-19 공식 안내·신청 링크 편집). */
  officialUrl?: string;
  applicationUrl?: string | null;
  /** The tree opens the editor without schedules; then the 모집 기간·활동 기간 blocks are hidden. */
  application?: Schedule | null;
  activitySchedule?: Schedule;
  /** Fields the collector copied from the program's previous edition (contract "이전 기수 정보 자동완성"); absent on the tree path. */
  inheritedFields?: string[];
  /** Fields an operator changed through this editor (contract "운영자 지정 필드"); the collector never overwrites them. */
  curatedFields?: string[];
};
/** True when the collector filled [field] from an earlier edition and no operator has edited it since. */
const inherited = (activity: { inheritedFields?: string[] }, field: "eligibility" | "audience" | "participationType") =>
  activity.inheritedFields?.includes(field) ?? false;
type CuratedField = "name" | "nameEn" | "description" | "descriptionEn" | "eligibility" | "officialUrl" | "applicationUrl" | "category";
/** True when an operator set [field] here; the server keeps that mark even when the field is saved again or emptied. */
const curated = (activity: { curatedFields?: string[] }, field: CuratedField) => activity.curatedFields?.includes(field) ?? false;
const curatedFieldLabels: Record<CuratedField, string> = {
  name: "한글 이름",
  nameEn: "영문 이름",
  description: "한글 설명",
  descriptionEn: "영문 설명",
  eligibility: "대상자 조건",
  officialUrl: "공식 안내 URL",
  applicationUrl: "신청 링크",
  category: "종류",
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
type RangeDraft = { range: [Dayjs | null, Dayjs | null] | null; timed: boolean };
/** Structured dates of a collected schedule → editor state (dates come back as Korea-time days; `endDateExclusive` becomes the last day). */
function rangeDraft(schedule: Schedule | null | undefined): RangeDraft {
  if (!schedule) return { range: null, timed: false };
  // A deadline-only or start-only window comes back timed with one end missing (contract "마감일만 지정").
  if (schedule.startAt || schedule.endAt)
    return { range: [schedule.startAt ? dayjs(schedule.startAt).tz(SEOUL) : null, schedule.endAt ? dayjs(schedule.endAt).tz(SEOUL) : null], timed: true };
  if (schedule.startDate && schedule.endDateExclusive)
    return { range: [dayjs.tz(schedule.startDate, SEOUL), dayjs.tz(schedule.endDateExclusive, SEOUL).subtract(1, "day")], timed: false };
  return { range: null, timed: false };
}
/** Editor state → PATCH value. Times keep the +09:00 offset so the server stores Korea time; a missing end is simply left out; the trimmed 사유 rides along. */
function scheduleInput({ range, timed }: RangeDraft, note?: string): ScheduleInput {
  const [start, end] = (range ?? [null, null]).map((value) => value?.tz(SEOUL) ?? null);
  if (!start && !end) return null;
  const at = (value: Dayjs) => value.second(0).millisecond(0).format("YYYY-MM-DDTHH:mm:ssZ");
  const day = (value: Dayjs) => value.format("YYYY-MM-DD");
  const trimmed = note?.trim();
  return {
    ...(timed ? { ...(start && { startAt: at(start) }), ...(end && { endAt: at(end) }) } : { ...(start && { startDate: day(start) }), ...(end && { endDate: day(end) }) }),
    ...(trimmed && { note: trimmed }),
  };
}
/** 활동 기간 must have both ends (the server stores it as an all-day range); returns the field error or null. */
function activityRangeError(range: RangeDraft["range"]): string | null {
  return range && (!range[0] || !range[1]) ? "활동 기간은 시작과 종료를 모두 골라 주세요." : null;
}


export function Activities() {
  const [search, setSearch] = useState(""),
    [organizationId, setOrganizationId] = useState<number>(),
    [programId, setProgramId] = useState<number>(),
    [publicationStatus, setPublicationStatus] = useState<PublicationStatus>(),
    [editing, setEditing] = useState<Activity>(),
    [marking, setMarking] = useState<Activity>(),
    [creating, setCreating] = useState<{ programId?: number; programName?: string }>();
  // 발견 탭처럼 프로그램 아래 활동을 묶어 보인다(2026-10-19). 검색어나 프로그램 필터가 있으면 평평한 결과 표를 쓴다.
  const grouped = !search && programId === undefined;
  const cursor = useCursorList<Activity>("activities", { q: search, organizationId, programId, publicationStatus }, { enabled: !grouped });
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
  const columns: TableColumnsType<Activity> = [
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
      title: "종류",
      render: (_, activity) => (
        <>
          {categoryLabel(activity.category)}
          {curated(activity, "category") && <CuratedTag />}
        </>
      ),
    },
    {
      title: "게시",
      render: (_, activity) => (
        <>
          <Tag color={activity.publicationStatus === "PUBLISHED" ? "blue" : undefined}>{statusLabels[activity.publicationStatus]}</Tag>
          {(activity.inheritedFields?.length ?? 0) > 0 && (
            <Tooltip title="이전 기수에서 자동완성한 값이 있어요. 상세에서 어떤 항목인지 보여요.">
              <span className="muted" aria-label="이전 기수에서 자동완성한 값 있음">
                자동완성
              </span>
            </Tooltip>
          )}
          {(activity.curatedFields?.length ?? 0) > 0 && (
            <Tooltip title="어드민에서 고친 값이 있어요. 수집이 덮어쓰지 않아요. 상세에서 어떤 항목인지 보여요.">
              <span className="muted" aria-label="운영자가 지정한 값 있음" style={{ marginLeft: 6 }}>
                운영자 지정
              </span>
            </Tooltip>
          )}
        </>
      ),
    },
    {
      title: "모집 기간",
      render: (_, activity) => (
        <>
          {scheduleText(activity.application)}
          {activity.application?.curated && <CuratedTag />}
          <PeriodTag reason={periodSuspicion(activity.application, activity.activitySchedule)} />
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
  ];
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
          <Button type="primary" icon={<PlusOutlined aria-hidden="true" />} onClick={() => setCreating({})}>
            활동 추가
          </Button>
        </Space>
      </div>
      {creating && <ActivityCreateModal programId={creating.programId} programName={creating.programName} onClose={() => setCreating(undefined)} />}
      {grouped ? (
        <ProgramGroups
          organizationId={organizationId}
          publicationStatus={publicationStatus}
          columns={columns}
          onCreate={(program) => setCreating({ programId: program.id, programName: program.name })}
        />
      ) : (
        <CursorTable
          // With a program filter the loaded page is ordered by recruitment deadline, like the grouped view; search results keep server order.
          cursor={programId !== undefined ? { ...cursor, items: [...cursor.items].sort(byRecruitmentDeadline) } : cursor}
          scroll={{ x: 1000 }}
          locale={{ emptyText: "조건에 맞는 활동이 없어요." }}
          expandable={{ expandedRowRender: (activity) => <ActivityDetail activity={activity} /> }}
          columns={columns}
        />
      )}
      {editing && <ActivityTextEditor activity={editing} onClose={() => setEditing(undefined)} />}
      {marking && <DuplicateDialog activity={marking} onClose={() => setMarking(undefined)} />}
    </>
  );
}

/** Marks a value an operator set here (a schedule or a text/eligibility field); the collector leaves it alone. */
/**
 * 수집이 모집 기간과 활동 기간을 바꿔 넣었을 가능성(2026-10-19 사용자 보고): 모집 시작이 활동 종료 뒤, 두 기간이 같음,
 * 활동 기간 원문에 모집·접수·지원·신청 낱말. 이유 문구를 돌려주고 의심이 없으면 null.
 */
function periodSuspicion(application: Schedule | null | undefined, activity: Schedule | null | undefined): string | null {
  if (!activity) return null;
  const start = (schedule: Schedule) => schedule.startDate ?? schedule.startAt?.slice(0, 10) ?? null;
  const end = (schedule: Schedule) => schedule.endDateExclusive ?? schedule.endAt?.slice(0, 10) ?? null;
  if (application) {
    const applicationStart = start(application);
    const applicationEnd = end(application);
    if (applicationStart && end(activity) && applicationStart > end(activity)!) return "모집 시작이 활동 종료보다 늦어요";
    if (applicationStart && applicationEnd && applicationStart === start(activity) && applicationEnd === end(activity)) return "모집 기간과 활동 기간이 같아요";
  }
  if (/모집|접수|지원|신청/.test(activity.dateLabel ?? "")) return "활동 기간 원문에 모집·접수 낱말이 있어요";
  return null;
}
/** 모집 마감 순(2026-10-19 사용자 요청): the application end (`endAt`, else `endDateExclusive`), activities without one last, ties by name. */
function byRecruitmentDeadline(a: Activity, b: Activity): number {
  const deadline = (activity: Activity) => activity.application?.endAt ?? activity.application?.endDateExclusive ?? null;
  const [da, db] = [deadline(a), deadline(b)];
  if (da && db && da !== db) return da < db ? -1 : 1;
  if (da && !db) return -1;
  if (!da && db) return 1;
  return a.name.localeCompare(b.name, "ko");
}
function PeriodTag({ reason }: { reason: string | null }) {
  if (!reason) return null;
  return (
    <Tooltip title={`${reason}. 두 기간이 바뀌었을 수 있어요. 편집에서 ‘모집 ↔ 활동 바꾸기’로 고칠 수 있어요.`}>
      <Tag color="orange" style={{ marginLeft: 6 }}>
        기간 확인
      </Tag>
    </Tooltip>
  );
}
function CuratedTag() {
  return (
    <Tooltip title="어드민에서 고친 값이에요. 수집이 덮어쓰지 않아요.">
      <Tag color="purple" style={{ marginLeft: 6 }}>
        운영자 지정
      </Tag>
    </Tooltip>
  );
}

/** Marks a value the collector copied from the program's previous edition; editing or clearing the field removes it. */
function InheritedTag() {
  return (
    <Tooltip title="같은 프로그램의 이전 활동에서 자동완성한 값이에요. 고치거나 비우면 표시가 사라져요.">
      <Tag color="gold" style={{ marginLeft: 6 }}>
        이전 기수에서 가져옴
      </Tag>
    </Tooltip>
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

type TextBody = { name: string; nameEn: string; description: string; descriptionEn: string; eligibility: Eligibility | null; officialUrl?: string; applicationUrl?: string; category?: string | null };
type ScheduleBody = { activitySchedule?: ScheduleInput; application?: ScheduleInput };

export function ActivityTextEditor({ activity, onClose }: { activity: ActivityText; onClose: () => void }) {
  const [form] = Form.useForm();
  const [eligibility, setEligibility] = useState<DraftGroup>(() => toDraft(activity.eligibility));
  const [eligibilityProblems, setEligibilityProblems] = useState<string[]>([]);
  const eligibilityJson = fromDraft(eligibility);
  const activityTimed: boolean = Form.useWatch("activityTimed", form) ?? false;
  const applicationTimed: boolean = Form.useWatch("applicationTimed", form) ?? false;
  // Only the list passes schedules; the tree does not, so the blocks (and the PATCH keys) are skipped there.
  const withSchedules = activity.activitySchedule !== undefined;
  const withUrls = activity.officialUrl !== undefined;
  const initialActivity = rangeDraft(activity.activitySchedule);
  const initialApplication = rangeDraft(activity.application);
  const initialActivityNote = activity.activitySchedule?.curatedNote ?? "";
  const initialApplicationNote = activity.application?.curatedNote ?? "";
  /** 수집이 두 기간을 바꿔 넣었을 때 폼 값만 서로 바꾼다(사유 포함). 활동 기간은 종일만 허용하므로 시각 지정은 끈다. 저장해야 반영된다. */
  const swapPeriods = () => {
    const values = form.getFieldsValue(["activityRange", "activityTimed", "activityNote", "applicationRange", "applicationTimed", "applicationNote"]);
    form.setFieldsValue({
      activityRange: values.applicationRange,
      activityTimed: false,
      activityNote: values.applicationNote,
      applicationRange: values.activityRange,
      applicationTimed: values.activityTimed,
      applicationNote: values.activityNote,
    });
  };
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
          officialUrl: activity.officialUrl ?? "",
          applicationUrl: activity.applicationUrl ?? "",
          category: activity.category ?? null,
          activityRange: initialActivity.range,
          activityTimed: initialActivity.timed,
          activityNote: initialActivityNote,
          applicationRange: initialApplication.range,
          applicationTimed: initialApplication.timed,
          applicationNote: initialApplicationNote,
        }}
        // Every text field is sent: an empty English field clears it, an empty 대상자 조건 sends null.
        // Schedule keys go only when the operator changed them: a cleared picker sends null, untouched blocks are omitted.
        onFinish={(values: {
          name: string;
          nameEn: string;
          description: string;
          descriptionEn: string;
          officialUrl: string;
          applicationUrl: string;
          category: Category | null;
          activityRange: RangeDraft["range"];
          activityTimed: boolean;
          activityNote: string;
          applicationRange: RangeDraft["range"];
          applicationTimed: boolean;
          applicationNote: string;
        }) => {
          const problems = validateDraft(eligibility);
          setEligibilityProblems(problems);
          if (problems.length) return;
          const schedules: ScheduleBody = {};
          if (withSchedules) {
            const rangeError = activityRangeError(values.activityRange);
            if (rangeError) {
              form.setFields([{ name: "activityRange", errors: [rangeError] }]);
              return;
            }
            // A block goes out when its range or its 사유 changed; the 사유 rides inside the schedule object.
            const nextActivity = scheduleInput({ range: values.activityRange, timed: values.activityTimed }, values.activityNote);
            if (JSON.stringify(nextActivity) !== JSON.stringify(scheduleInput(initialActivity, initialActivityNote))) schedules.activitySchedule = nextActivity;
            const nextApplication = scheduleInput({ range: values.applicationRange, timed: values.applicationTimed }, values.applicationNote);
            if (JSON.stringify(nextApplication) !== JSON.stringify(scheduleInput(initialApplication, initialApplicationNote))) schedules.application = nextApplication;
          }
          saveOnce(() =>
            save.mutateAsync({
              name: values.name.trim(),
              nameEn: values.nameEn.trim(),
              description: values.description.trim(),
              descriptionEn: values.descriptionEn.trim(),
              eligibility: eligibilityJson,
              // URL fields exist only on the list path; the tree omits them so the server keeps the current values.
              ...(withUrls ? { officialUrl: values.officialUrl.trim(), applicationUrl: values.applicationUrl.trim() } : {}),
              // 종류 goes only when changed, so an untouched field does not become 운영자 지정.
              ...(values.category !== (activity.category ?? null) ? { category: values.category } : {}),
              ...schedules,
            }),
          );
        }}
      >
        {save.error && <Alert className="form-error" type="error" showIcon message={save.error.message} />}
        <p className="field-note">초안은 다음 수집 때 덮어써질 수 있어요. 고친 내용을 지키려면 게시해 주세요. 직접 지정한 기간은 수집이 덮어쓰지 않아요.</p>
        {withSchedules && (
          <>
            <Space align="center" wrap style={{ marginBottom: 8 }}>
              <Button size="small" onClick={swapPeriods}>
                모집 ↔ 활동 바꾸기
              </Button>
              <PeriodTag reason={periodSuspicion(activity.application, activity.activitySchedule)} />
            </Space>
            <ScheduleBlock
              title="모집 기간"
              schedule={activity.application}
              rangeName="applicationRange"
              timedName="applicationTimed"
              noteName="applicationNote"
              timed={applicationTimed}
              startOptional
            />
            <ScheduleBlock
              title="활동 기간"
              schedule={activity.activitySchedule}
              rangeName="activityRange"
              timedName="activityTimed"
              noteName="activityNote"
              timed={activityTimed}
            />
          </>
        )}
        <Form.Item
          name="name"
          label={<>한글 이름{curated(activity, "name") && <CuratedTag />}</>}
          rules={[{ required: true, whitespace: true, message: "이름을 입력해 주세요." }]}
        >
          <Input />
        </Form.Item>
        <Form.Item name="nameEn" label={<>영문 이름(선택){curated(activity, "nameEn") && <CuratedTag />}</>}>
          <Input />
        </Form.Item>
        <Form.Item name="description" label={<>한글 설명{curated(activity, "description") && <CuratedTag />}</>}>
          <Input.TextArea autoSize={{ minRows: 3, maxRows: 10 }} />
        </Form.Item>
        <Form.Item name="descriptionEn" label={<>영문 설명(선택){curated(activity, "descriptionEn") && <CuratedTag />}</>}>
          <Input.TextArea autoSize={{ minRows: 3, maxRows: 10 }} />
        </Form.Item>
        <Form.Item name="category" label={<>종류{curated(activity, "category") && <CuratedTag />}</>}>
          <Select virtual={false} options={categoryOptions} style={{ width: 200 }} />
        </Form.Item>
        {withUrls && (
          <>
            <Form.Item
              name="officialUrl"
              label={<>공식 안내 URL{curated(activity, "officialUrl") && <CuratedTag />}</>}
              rules={[{ required: true, whitespace: true, message: "공식 안내 URL을 입력해 주세요." }, { pattern: /^\s*https:\/\/\S+\s*$/, message: "https 주소여야 해요." }]}
            >
              <Input inputMode="url" placeholder="https://" />
            </Form.Item>
            <Form.Item
              name="applicationUrl"
              label={<>신청 링크(선택){curated(activity, "applicationUrl") && <CuratedTag />}</>}
              rules={[{ pattern: /^\s*(https:\/\/\S+)?\s*$/, message: "https 주소여야 해요. 비우면 지워져요." }]}
            >
              <Input inputMode="url" placeholder="https://" />
            </Form.Item>
          </>
        )}
        <div className="eligibility-field">
          <div className="eligibility-field-heading">
            <strong>대상자 조건(선택)</strong>
            {inherited(activity, "eligibility") && <InheritedTag />}
            {curated(activity, "eligibility") && <CuratedTag />}
            <Button
              size="small"
              aria-label="대상자 조건 비우기"
              disabled={eligibility.items.length === 0}
              onClick={() => {
                if (window.confirm("대상자 조건을 모두 지우고 조건 없음으로 저장할까요?")) {
                  setEligibility(newGroup());
                  setEligibilityProblems([]);
                }
              }}
            >
              비우기
            </Button>
          </div>
          <p className="field-note">
            공지의 대상 문구를 조건으로 옮겨요. 숫자 항목은 부터·까지 범위, 나머지는 값 선택이에요. 영문 표기는 어휘에서 자동으로 채우고, 조건이 하나도 없으면 조건 없음으로
            저장해요.
          </p>
          <EligibilityEditor
            value={eligibility}
            onChange={(next) => {
              setEligibility(next);
              if (eligibilityProblems.length) setEligibilityProblems(validateDraft(next));
            }}
          />
          {eligibilityProblems.length > 0 && (
            <Alert
              className="form-error"
              type="error"
              showIcon
              message="대상자 조건을 확인해 주세요."
              description={
                <ul style={{ margin: 0, paddingLeft: 18 }}>
                  {eligibilityProblems.map((problem) => (
                    <li key={problem}>{problem}</li>
                  ))}
                </ul>
              }
            />
          )}
          <p className="field-note">읽기: {eligibilitySummary(eligibilityJson) || <span className="muted">조건 없음</span>}</p>
          <Collapse
            size="small"
            ghost
            items={[
              {
                key: "json",
                label: "JSON 보기",
                children: (
                  <pre className="eligibility-json" aria-label="대상자 조건 JSON">
                    {eligibilityJson ? JSON.stringify(eligibilityJson, null, 2) : "null"}
                  </pre>
                ),
              },
            ]}
          />
        </div>
      </Form>
    </Modal>
  );
}

/** A program row of the grouped view; the shape `GET programs` returns, trimmed to what the table shows. */
type ProgramRow = {
  id: number;
  name: string;
  nameEn: string | null;
  organization: { id: number; name: string };
  collection: { enabled: boolean; intervalDays: number; allowedHosts: string[]; lastSucceededOn: string | null };
};

/** 발견 탭 모양: programs on top, each expanding to its activities (loaded on expand). One matching program opens by itself. */
function ProgramGroups({
  organizationId,
  publicationStatus,
  columns,
  onCreate,
}: {
  organizationId?: number;
  publicationStatus?: PublicationStatus;
  columns: TableColumnsType<Activity>;
  onCreate: (program: ProgramRow) => void;
}) {
  const programs = useCursorList<ProgramRow>("programs", { organizationId });
  const [expanded, setExpanded] = useState<number[]>([]);
  const single = programs.items.length === 1 && !programs.list.hasNextPage ? programs.items[0].id : undefined;
  useEffect(() => {
    if (single !== undefined) setExpanded([single]);
  }, [single]);
  // 최하위 조직(프로그램 바로 위 조직)별 묶음, 조직 이름순 (contract "어드민 웹 활동 화면" 2026-10-19). Groups cover the loaded pages.
  const byOrganization = new Map<number, { name: string; items: ProgramRow[] }>();
  for (const program of programs.items) {
    const group = byOrganization.get(program.organization.id) ?? { name: program.organization.name, items: [] };
    group.items.push(program);
    byOrganization.set(program.organization.id, group);
  }
  const groups = [...byOrganization.entries()].sort(([, a], [, b]) => a.name.localeCompare(b.name, "ko"));
  const programColumns: TableColumnsType<ProgramRow> = [
    {
      title: "프로그램",
      render: (_, program) => (
        <>
          {program.name}
          {program.nameEn && <span className="muted name-en">{program.nameEn}</span>}
        </>
      ),
    },
    {
      title: "수집",
      render: (_, { collection }) => (
        <span className="muted">
          {collection.enabled ? `${collection.intervalDays}일마다` : "꺼짐"}
          {collection.allowedHosts.length ? ` · ${collection.allowedHosts.join(", ")}` : " · 웹 전체 검색"}
          {collection.lastSucceededOn && ` · 마지막 성공 ${collection.lastSucceededOn}`}
        </span>
      ),
    },
  ];
  if (programs.list.isError)
    return (
      <Alert
        type="error"
        showIcon
        message="목록을 불러오지 못했어요."
        description={programs.list.error.message}
        action={<Button onClick={() => programs.list.refetch()}>다시 시도</Button>}
      />
    );
  if (!programs.list.isLoading && groups.length === 0) return <p className="muted">조건에 맞는 프로그램이 없어요.</p>;
  return (
    <>
      {programs.list.isLoading && <Table<ProgramRow> rowKey="id" dataSource={[]} loading pagination={false} columns={programColumns} />}
      {groups.map(([id, group]) => (
        <section key={id} className="category-group">
          <h3 className="category-heading">
            {group.name} ({group.items.length})
          </h3>
          <Table<ProgramRow>
            rowKey="id"
            dataSource={group.items}
            pagination={false}
            scroll={{ x: 900 }}
            showHeader={false}
            expandable={{
              expandedRowKeys: expanded,
              onExpandedRowsChange: (keys) => setExpanded(keys.map(Number)),
              expandedRowRender: (program) => (
                <ProgramActivities program={program} publicationStatus={publicationStatus} columns={columns} onCreate={() => onCreate(program)} />
              ),
            }}
            columns={programColumns}
          />
        </section>
      ))}
      {programs.list.hasNextPage && (
        <div className="load-more">
          <Button onClick={() => programs.list.fetchNextPage()} loading={programs.list.isFetchingNextPage}>
            더 보기
          </Button>
        </div>
      )}
    </>
  );
}

/** The activities of one program, with the same columns and row actions as the flat table. */
function ProgramActivities({
  program,
  publicationStatus,
  columns,
  onCreate,
}: {
  program: ProgramRow;
  publicationStatus?: PublicationStatus;
  columns: TableColumnsType<Activity>;
  onCreate: () => void;
}) {
  const cursor = useCursorList<Activity>("activities", { programId: program.id, publicationStatus });
  // 종류별 묶음: contract order, 미분류 last; only groups that have activities.
  const groups = [...categoryOrder.map((code) => ({ code, label: categoryLabel(code) })), { code: null, label: UNCATEGORIZED }]
    .map((group) => ({ ...group, items: cursor.items.filter((activity) => (activity.category ?? null) === group.code).sort(byRecruitmentDeadline) }))
    .filter((group) => group.items.length);
  return (
    <div className="program-activities">
      <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 8 }}>
        <Button size="small" icon={<PlusOutlined aria-hidden="true" />} onClick={onCreate}>
          이 프로그램에 활동 추가
        </Button>
      </div>
      {cursor.list.isError ? (
        <Alert type="error" showIcon message="활동을 불러오지 못했어요." description={cursor.list.error.message} />
      ) : cursor.list.isLoading ? (
        <Spin />
      ) : !groups.length ? (
        <p className="muted">이 프로그램에는 활동이 없어요.</p>
      ) : (
        groups.map((group) => (
          <section key={group.label} className="category-group" aria-label={`${group.label} ${group.items.length}개`}>
            <h3 className="category-heading">
              {group.label} ({group.items.length})
            </h3>
            <Table<Activity>
              rowKey="id"
              size="small"
              pagination={false}
              dataSource={group.items}
              scroll={{ x: 1000 }}
              expandable={{ expandedRowRender: (activity) => <ActivityDetail activity={activity} /> }}
              columns={columns}
            />
          </section>
        ))
      )}
      {cursor.list.hasNextPage && (
        <div className="load-more">
          <Button onClick={() => cursor.list.fetchNextPage()} loading={cursor.list.isFetchingNextPage}>
            더 보기
          </Button>
        </div>
      )}
    </div>
  );
}

type CreateBody = {
  programId: number;
  name: string;
  nameEn?: string;
  description: string;
  descriptionEn?: string;
  officialUrl: string;
  applicationUrl?: string;
  participationType: "REGISTRATION" | "SELECTION";
  category?: Category | null;
  activitySchedule?: ScheduleInput | { dateLabel: string };
  application?: ScheduleInput;
  eligibility?: Eligibility;
  audience?: string;
  location?: string;
  cost?: string;
};
const httpsRule = { pattern: /^\s*https:\/\/\S+\s*$/, message: "https 주소여야 해요." };

/** 활동 추가 (contract `POST /v1/admin/activities`, 2026-10-19): an operator-made DRAFT with every sent field marked curated. */
function ActivityCreateModal({ programId, programName, onClose }: { programId?: number; programName?: string; onClose: () => void }) {
  const [form] = Form.useForm();
  const [eligibility, setEligibility] = useState<DraftGroup>(newGroup);
  const [eligibilityProblems, setEligibilityProblems] = useState<string[]>([]);
  const activityTimed: boolean = Form.useWatch("activityTimed", form) ?? false;
  const applicationTimed: boolean = Form.useWatch("applicationTimed", form) ?? false;
  const { message } = App.useApp();
  const client = useQueryClient();
  const create = useMutation({
    mutationFn: (body: CreateBody) => api<Activity>("activities", "POST", body),
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ["activities"] });
      message.success("활동을 추가했어요. 초안이라 게시 전까지 앱에는 보이지 않아요.");
      onClose();
    },
  });
  const createOnce = useSingleFlight();
  const optional = (value?: string) => value?.trim() || undefined;
  return (
    <Modal
      open
      title="활동 추가"
      onCancel={onClose}
      onOk={() => form.submit()}
      confirmLoading={create.isPending}
      okText="추가"
      cancelText="취소"
      width={720}
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={{ programId, participationType: "REGISTRATION", category: null, activityTimed: false, applicationTimed: false, activityNote: "", applicationNote: "" }}
        onFinish={(values: {
          programId: number;
          name: string;
          nameEn?: string;
          description: string;
          descriptionEn?: string;
          officialUrl: string;
          applicationUrl?: string;
          participationType: "REGISTRATION" | "SELECTION";
          category: Category | null;
          activityRange: RangeDraft["range"];
          activityTimed: boolean;
          activityNote: string;
          activityDateLabel?: string;
          applicationRange: RangeDraft["range"];
          applicationTimed: boolean;
          applicationNote: string;
          audience?: string;
          location?: string;
          cost?: string;
        }) => {
          const problems = validateDraft(eligibility);
          setEligibilityProblems(problems);
          if (problems.length) return;
          // 활동 기간: dates when picked (plus the typed phrase), otherwise the phrase alone; the server needs one of them.
          const rangeError = activityRangeError(values.activityRange);
          if (rangeError) {
            form.setFields([{ name: "activityRange", errors: [rangeError] }]);
            return;
          }
          const dates = scheduleInput({ range: values.activityRange, timed: values.activityTimed }, values.activityNote);
          const dateLabel = optional(values.activityDateLabel);
          if (!dates && !dateLabel) {
            form.setFields([{ name: "activityDateLabel", errors: ["활동 기간의 날짜나 원문 문구 중 하나는 입력해 주세요."] }]);
            return;
          }
          const application = scheduleInput({ range: values.applicationRange, timed: values.applicationTimed }, values.applicationNote);
          createOnce(() =>
            create.mutateAsync({
              programId: values.programId,
              name: values.name.trim(),
              nameEn: optional(values.nameEn),
              description: values.description.trim(),
              descriptionEn: optional(values.descriptionEn),
              officialUrl: values.officialUrl.trim(),
              applicationUrl: optional(values.applicationUrl),
              participationType: values.participationType,
              category: values.category,
              activitySchedule: dates ? { ...dates, ...(dateLabel ? { dateLabel } : {}) } : { dateLabel: dateLabel! },
              application: application ?? undefined,
              eligibility: fromDraft(eligibility) ?? undefined,
              audience: optional(values.audience),
              location: optional(values.location),
              cost: optional(values.cost),
            }),
          );
        }}
      >
        {create.error && <Alert className="form-error" type="error" showIcon message={create.error.message} />}
        <p className="field-note">운영자가 직접 만드는 활동이에요. 초안으로 저장되고, 수집이 덮어쓰거나 다시 만들지 않아요. 게시는 목록에서 바꿔요.</p>
        <Form.Item name="programId" label="프로그램" rules={[{ required: true, message: "프로그램을 골라 주세요." }]}>
          <SearchSelect path="programs" placeholder="프로그램 이름으로 검색" label={programName} />
        </Form.Item>
        <Form.Item name="name" label="한글 이름" rules={[{ required: true, whitespace: true, message: "이름을 입력해 주세요." }]}>
          <Input placeholder="예: 부스트캠프 11기" />
        </Form.Item>
        <Form.Item name="nameEn" label="영문 이름(선택)">
          <Input />
        </Form.Item>
        <Form.Item name="description" label="한글 설명" rules={[{ required: true, whitespace: true, message: "설명을 입력해 주세요." }]}>
          <Input.TextArea autoSize={{ minRows: 3, maxRows: 10 }} />
        </Form.Item>
        <Form.Item name="descriptionEn" label="영문 설명(선택)">
          <Input.TextArea autoSize={{ minRows: 3, maxRows: 10 }} />
        </Form.Item>
        <Form.Item name="officialUrl" label="공식 안내 URL" rules={[{ required: true, whitespace: true, message: "공식 안내 URL을 입력해 주세요." }, httpsRule]}>
          <Input inputMode="url" placeholder="https://" />
        </Form.Item>
        <Form.Item name="applicationUrl" label="신청 링크(선택)" rules={[{ pattern: /^\s*(https:\/\/\S+)?\s*$/, message: "https 주소여야 해요." }]}>
          <Input inputMode="url" placeholder="https://" />
        </Form.Item>
        <Form.Item name="category" label="종류">
          <Select virtual={false} options={categoryOptions} style={{ width: 200 }} />
        </Form.Item>
        <Form.Item name="participationType" label="참여 방식">
          <Radio.Group
            options={[
              { value: "REGISTRATION", label: "신청 — 신청하면 참가" },
              { value: "SELECTION", label: "선발 — 심사로 뽑음" },
            ]}
          />
        </Form.Item>
        <ScheduleBlock title="모집 기간" schedule={null} rangeName="applicationRange" timedName="applicationTimed" noteName="applicationNote" timed={applicationTimed} startOptional />
        <ScheduleBlock title="활동 기간" schedule={null} rangeName="activityRange" timedName="activityTimed" noteName="activityNote" timed={activityTimed} />
        <Form.Item name="activityDateLabel" label="활동 기간 원문(선택)" extra="날짜를 고르지 않으면 이 문구만 남아요. 예: 2026년 상반기">
          <Input maxLength={200} />
        </Form.Item>
        <div className="eligibility-field">
          <div className="eligibility-field-heading">
            <strong>대상자 조건(선택)</strong>
            <Button size="small" aria-label="대상자 조건 비우기" disabled={eligibility.items.length === 0} onClick={() => { setEligibility(newGroup()); setEligibilityProblems([]); }}>
              비우기
            </Button>
          </div>
          <EligibilityEditor
            value={eligibility}
            onChange={(next) => {
              setEligibility(next);
              if (eligibilityProblems.length) setEligibilityProblems(validateDraft(next));
            }}
          />
          {eligibilityProblems.length > 0 && (
            <Alert
              className="form-error"
              type="error"
              showIcon
              message="대상자 조건을 확인해 주세요."
              description={
                <ul style={{ margin: 0, paddingLeft: 18 }}>
                  {eligibilityProblems.map((problem) => (
                    <li key={problem}>{problem}</li>
                  ))}
                </ul>
              }
            />
          )}
          <p className="field-note">읽기: {eligibilitySummary(fromDraft(eligibility)) || <span className="muted">조건 없음</span>}</p>
        </div>
        <Form.Item name="audience" label="대상 원문(선택)">
          <Input maxLength={1000} />
        </Form.Item>
        <Form.Item name="location" label="장소(선택)">
          <Input />
        </Form.Item>
        <Form.Item name="cost" label="비용(선택)">
          <Input />
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
  noteName,
  timed,
  startOptional = false,
}: {
  title: string;
  schedule: Schedule | null | undefined;
  rangeName: string;
  timedName: string;
  noteName: string;
  timed: boolean;
  /** 모집 기간 may be a deadline alone (contract "마감일만 지정"); 활동 기간 needs both ends. */
  startOptional?: boolean;
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
            allowEmpty={startOptional ? [true, false] : undefined}
            showTime={timed ? { format: "HH:mm", minuteStep: 5 } : false}
            format={timed ? "YYYY-MM-DD HH:mm" : "YYYY-MM-DD"}
            placeholder={[startOptional ? "시작(선택)" : "시작", "종료"]}
          />
        </Form.Item>
        <Form.Item name={timedName} label="시각 지정" valuePropName="checked" style={{ marginBottom: 8 }}>
          <Switch />
        </Form.Item>
      </Space>
      <Form.Item name={noteName} label="사유(선택)" style={{ marginBottom: 8 }}>
        <Input maxLength={300} placeholder="예: 원문의 6/9는 접수 마감이라 모집 종료로 지정" />
      </Form.Item>
      <p className="field-note">
        날짜만 고르면 종일 일정, 시각을 켜면 한국 시간으로 저장해요.{startOptional && " 마감일만 고르면 그날까지로 저장해요."} 비워 두면 원문 문구만 남아요. 지정한 기간은 다음 수집의 해석 예시로도 쓰여요.
      </p>
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
        {
          label: "설명",
          span: "filled",
          children: (
            <>
              {activity.description || "없음"}
              {curated(activity, "description") && <CuratedTag />}
            </>
          ),
        },
        {
          label: "영문 설명",
          span: "filled",
          children: (
            <>
              {activity.descriptionEn || "없음"}
              {curated(activity, "descriptionEn") && <CuratedTag />}
            </>
          ),
        },
        ...((["name", "nameEn"] as const).some((field) => curated(activity, field))
          ? [
              {
                label: "운영자 지정 이름",
                span: "filled" as const,
                children: (
                  <>
                    {(["name", "nameEn"] as const)
                      .filter((field) => curated(activity, field))
                      .map((field) => curatedFieldLabels[field])
                      .join(" · ")}
                    <CuratedTag />
                  </>
                ),
              },
            ]
          : []),
        {
          label: "모집 기간",
          children: (
            <>
              {scheduleText(activity.application)}
              {activity.application?.curated && <CuratedTag />}
              <PeriodTag reason={periodSuspicion(activity.application, activity.activitySchedule)} />
              {activity.application?.dateLabel && <div className="muted">원문: {activity.application.dateLabel}</div>}
            </>
          ),
        },
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
        { label: "장소", children: activity.location ?? "미확인" },
        { label: "비용", children: activity.cost ?? "미확인" },
        {
          label: "대상",
          span: "filled",
          children: (
            <>
              {eligibilitySummary(activity.eligibility) || <span className="muted">조건 없음</span>}
              {inherited(activity, "eligibility") && <InheritedTag />}
              {curated(activity, "eligibility") && <CuratedTag />}
              {activity.audience && (
                <div className="muted" style={{ marginTop: 4 }}>
                  원문: {activity.audience}
                  {inherited(activity, "audience") && <InheritedTag />}
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
