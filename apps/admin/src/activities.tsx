import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { App, Button, Descriptions, Input, Select, Space, Tag } from "antd";
import { ExportOutlined, SafetyCertificateOutlined } from "@ant-design/icons";
import { api } from "./api";
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
type Activity = {
  id: number;
  name: string;
  description: string;
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

export function Activities() {
  const [search, setSearch] = useState(""),
    [organizationId, setOrganizationId] = useState<number>(),
    [programId, setProgramId] = useState<number>(),
    [publicationStatus, setPublicationStatus] = useState<PublicationStatus>();
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
    else publication.mutate({ id: activity.id, status });
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
                <strong>{activity.name}</strong>
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
              <Space>
                <a href={activity.officialUrl} target="_blank" rel="noreferrer">
                  공식 안내 <ExportOutlined aria-hidden="true" />
                </a>
                <Button
                  type={activity.publicationStatus === "PUBLISHED" ? "default" : "primary"}
                  loading={publication.isPending && publication.variables?.id === activity.id}
                  onClick={() => toggle(activity)}
                >
                  {activity.publicationStatus === "PUBLISHED" ? "게시 취소" : "게시"}
                </Button>
              </Space>
            ),
          },
        ]}
      />
    </>
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
        { label: "장소", children: activity.location ?? "미확인" },
        { label: "비용", children: activity.cost ?? "미확인" },
        { label: "대상", children: activity.audience ?? "미확인" },
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
