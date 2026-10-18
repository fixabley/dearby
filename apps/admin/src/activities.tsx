import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Alert, App, Button, Descriptions, Form, Input, Modal, Popconfirm, Select, Space, Tag } from "antd";
import { ExportOutlined, SafetyCertificateOutlined } from "@ant-design/icons";
import { api, useSingleFlight } from "./api";
import { CursorTable, SearchSelect, useCursorList } from "./list";

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
};
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
    [editing, setEditing] = useState<Activity>();
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
          { title: "모집 기간", render: (_, activity) => scheduleText(activity.application) },
          { title: "활동 일정", render: (_, activity) => scheduleText(activity.activitySchedule) },
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
    </>
  );
}

export function ActivityTextEditor({ activity, onClose }: { activity: ActivityText; onClose: () => void }) {
  const [form] = Form.useForm();
  const eligibilityText: string = Form.useWatch("eligibility", form) ?? "";
  const { message } = App.useApp();
  const client = useQueryClient();
  const save = useMutation({
    mutationFn: (body: Omit<ActivityText, "id">) => api<ActivityText>(`activities/${activity.id}`, "PATCH", body),
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
        }}
        // Every field is sent: an empty English field clears it, an empty 대상자 조건 sends null.
        onFinish={(values: { name: string; nameEn: string; description: string; descriptionEn: string; eligibility: string }) =>
          saveOnce(() =>
            save.mutateAsync({
              name: values.name.trim(),
              nameEn: values.nameEn.trim(),
              description: values.description.trim(),
              descriptionEn: values.descriptionEn.trim(),
              eligibility: parseEligibility(values.eligibility ?? ""),
            }),
          )
        }
      >
        {save.error && <Alert className="form-error" type="error" showIcon message={save.error.message} />}
        <p className="field-note">초안은 다음 수집 때 덮어써질 수 있어요. 고친 내용을 지키려면 게시해 주세요.</p>
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
