import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, App, Button, Descriptions, Drawer, Form, Input, Popconfirm, Segmented, Space, Table, Tag } from "antd";
import { api, useSingleFlight } from "./api";

// admin-v1.md "명함 신고 — 2026-10-22".
type Status = "OPEN" | "RESOLVED" | "DISMISSED";
type Reason = "SPAM" | "IMPERSONATION" | "INAPPROPRIATE" | "OTHER";
type Contact = { kind?: string; label?: string | null; value?: string };
type CardSnapshot = { name?: string; profileName?: string; job?: string | null; introduction?: string | null; introLink?: string | null; contacts?: Contact[] };
type Report = {
  id: string;
  cardId: string;
  cardSnapshot: CardSnapshot | null;
  reason: Reason;
  detail: string | null;
  createdAt: string;
  status: Status;
  note: string | null;
  reportCount: number;
  cardWithdrawn: boolean;
};

const reasons: Record<Reason, string> = { SPAM: "스팸·광고", IMPERSONATION: "다른 사람 사칭", INAPPROPRIATE: "부적절한 내용", OTHER: "기타" };
const statuses: Record<Status, { label: string; color?: string }> = {
  OPEN: { label: "열림", color: "red" },
  RESOLVED: { label: "처리 완료", color: "green" },
  DISMISSED: { label: "무시" },
};
const at = (value: string) => new Date(value).toLocaleString("ko-KR", { timeZone: "Asia/Seoul" });
const cardName = (snapshot: CardSnapshot | null) => snapshot?.profileName || snapshot?.name || "(이름 없음)";

export function Reports() {
  const [status, setStatus] = useState<Status | "ALL">("OPEN");
  const [opened, setOpened] = useState<Report>();
  const reports = useQuery({
    queryKey: ["card-reports", status],
    queryFn: () => api<{ items: Report[] }>(status === "ALL" ? "card-reports" : `card-reports?status=${status}`),
    select: (page) => page.items,
  });
  return (
    <>
      <header className="page-heading">
        <div>
          <span className="eyebrow">CARD</span>
          <h1>신고</h1>
          <p>사용자가 신고한 명함이에요. 24시간 안에 확인하고, 필요하면 명함을 내려요. 신고 기록은 신고일로부터 1개월이 지나면 처리 여부와 관계없이 지워져요.</p>
        </div>
      </header>
      <div className="list-toolbar">
        <Segmented
          aria-label="신고 상태"
          value={status}
          onChange={(value) => setStatus(value as Status | "ALL")}
          options={[
            { label: "열린 신고", value: "OPEN" },
            { label: "처리 완료", value: "RESOLVED" },
            { label: "무시", value: "DISMISSED" },
            { label: "전체", value: "ALL" },
          ]}
        />
      </div>
      {reports.error && <Alert className="form-error" type="error" showIcon message={reports.error.message} />}
      <Table<Report>
        rowKey="id"
        loading={reports.isLoading}
        dataSource={reports.data}
        pagination={false}
        locale={{ emptyText: "신고가 없어요." }}
        onRow={(report) => ({ onClick: () => setOpened(report), style: { cursor: "pointer" } })}
        columns={[
          { title: "명함", render: (_, report) => <strong>{cardName(report.cardSnapshot)}</strong> },
          { title: "사유", render: (_, report) => reasons[report.reason] },
          { title: "설명", render: (_, report) => report.detail ?? "", ellipsis: true },
          { title: "신고 시각", render: (_, report) => at(report.createdAt) },
          { title: "같은 명함 신고", render: (_, report) => `${report.reportCount}건` },
          { title: "상태", render: (_, report) => <Tag color={statuses[report.status].color}>{statuses[report.status].label}</Tag> },
          { title: "명함", render: (_, report) => (report.cardWithdrawn ? <Tag>내려짐</Tag> : "") },
        ]}
      />
      {opened && <ReportDrawer report={opened} onClose={() => setOpened(undefined)} />}
    </>
  );
}

function ReportDrawer({ report, onClose }: { report: Report; onClose: () => void }) {
  const [note, setNote] = useState(report.note ?? "");
  const { message } = App.useApp();
  const client = useQueryClient();
  const once = useSingleFlight();
  const done = async (text: string) => {
    message.success(text);
    onClose();
    await client.invalidateQueries({ queryKey: ["card-reports"] });
  };
  const trimmed = note.trim() || undefined;
  const mark = useMutation({
    mutationFn: (status: Status) => api<Report>(`card-reports/${report.id}`, "PATCH", { status, note: trimmed }),
    onSuccess: (_, status) => done(status === "RESOLVED" ? "처리 완료로 바꿨어요." : "무시했어요."),
  });
  const withdraw = useMutation({
    mutationFn: () => api<void>(`cards/${report.cardId}/withdraw`, "POST", { reportId: report.id, note: note.trim() }),
    onSuccess: () => done("명함을 내렸어요."),
  });
  const busy = mark.isPending || withdraw.isPending;
  const snapshot = report.cardSnapshot;
  return (
    <Drawer open width={520} title={`신고 · ${cardName(snapshot)}`} onClose={onClose}>
      {(mark.error || withdraw.error) && <Alert className="form-error" type="error" showIcon message={(mark.error ?? withdraw.error)?.message} />}
      <Descriptions column={1} size="small" bordered title="신고">
        <Descriptions.Item label="사유">{reasons[report.reason]}</Descriptions.Item>
        <Descriptions.Item label="설명">{report.detail ?? "없음"}</Descriptions.Item>
        <Descriptions.Item label="신고 시각">{at(report.createdAt)}</Descriptions.Item>
        <Descriptions.Item label="같은 명함 신고">{report.reportCount}건</Descriptions.Item>
        <Descriptions.Item label="상태">
          {statuses[report.status].label}
          {report.cardWithdrawn && " · 명함 내려짐"}
        </Descriptions.Item>
      </Descriptions>
      <Descriptions column={1} size="small" bordered title="신고 당시 명함" style={{ marginTop: 24 }}>
        <Descriptions.Item label="이름">{snapshot?.profileName ?? ""}</Descriptions.Item>
        <Descriptions.Item label="명함 이름">{snapshot?.name ?? ""}</Descriptions.Item>
        <Descriptions.Item label="직무">{snapshot?.job ?? ""}</Descriptions.Item>
        <Descriptions.Item label="소개 링크">{snapshot?.introLink ?? ""}</Descriptions.Item>
        {/* 소개 글은 2026-10-26 폐지 전 신고에만 있다 */}
        {snapshot?.introduction ? (
          <Descriptions.Item label="소개 글(폐지 전)">
            <div style={{ whiteSpace: "pre-wrap" }}>{snapshot.introduction}</div>
          </Descriptions.Item>
        ) : null}
        <Descriptions.Item label="연락처">
          {(snapshot?.contacts ?? []).map((contact, index) => (
            <div key={index}>
              {contact.label || contact.kind}: {contact.value}
            </div>
          ))}
        </Descriptions.Item>
      </Descriptions>
      <Form layout="vertical" style={{ marginTop: 24 }}>
        <Form.Item label="메모" extra="명함을 내릴 때는 메모가 필요해요.">
          <Input.TextArea value={note} onChange={(event) => setNote(event.target.value)} autoSize={{ minRows: 2, maxRows: 6 }} maxLength={500} />
        </Form.Item>
      </Form>
      <Space wrap>
        <Button type="primary" loading={mark.isPending && mark.variables === "RESOLVED"} disabled={busy} onClick={() => once(() => mark.mutateAsync("RESOLVED"))}>
          처리 완료
        </Button>
        <Button loading={mark.isPending && mark.variables === "DISMISSED"} disabled={busy} onClick={() => once(() => mark.mutateAsync("DISMISSED"))}>
          무시
        </Button>
        <Popconfirm
          title="이 명함을 내릴까요?"
          description="모든 사람에게서 사라지고 되돌릴 수 없어요."
          okText="내리기"
          cancelText="취소"
          okButtonProps={{ danger: true }}
          disabled={busy || report.cardWithdrawn || !note.trim()}
          onConfirm={() => once(() => withdraw.mutateAsync())}
        >
          <Button danger loading={withdraw.isPending} disabled={busy || report.cardWithdrawn || !note.trim()}>
            {report.cardWithdrawn ? "이미 내려짐" : "명함 내리기"}
          </Button>
        </Popconfirm>
      </Space>
    </Drawer>
  );
}
