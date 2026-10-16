import { useEffect, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Alert, App, Button, Form, Input, Modal, Space, Tabs, Tag } from "antd";
import { AuditOutlined } from "@ant-design/icons";
import { api, apiBlob, useSingleFlight } from "./api";
import { CursorTable, SearchSelect, useCursorList } from "./list";

type Ref = { id: number; name: string } | null;
type Evidence = { id: string; fileName: string; contentType: string; size: number; uploadedAt: string };
/** `GET histories` item: the user's history plus the author's profile name. Never an account id. */
export type HistoryForReview = {
  id: string;
  profileName: string;
  kind: "organization" | "activity";
  organization: Ref;
  organizationName: string | null;
  program: Ref;
  programName: string | null;
  activity: Ref;
  activityName: string | null;
  role: Ref;
  roleName: string | null;
  startDate: string;
  endDate: string | null;
  description: string;
  approval: "approved" | "pending" | "rejected";
  approvalReason: string | null;
  verification: "unverified" | "pending" | "verified" | "rejected";
  evidence: Evidence[];
};
type Mapping = { organizationId?: number; programId?: number; activityId?: number; roleId?: number };

const month = (date: string) => date.slice(0, 7).replace("-", ".");
const period = (h: HistoryForReview) => `${month(h.startDate)} – ${h.endDate ? month(h.endDate) : "진행 중"}`;

/** 조직 → 프로그램 → 활동 → 역할. Free-typed values (no catalog ref) get a tag so the reviewer knows what to map. */
function Chain({ history }: { history: HistoryForReview }) {
  const steps: [string, Ref, string | null][] = [
    ["조직", history.organization, history.organizationName],
    ["프로그램", history.program, history.programName],
    ["활동", history.activity, history.activityName],
    ["역할", history.role, history.roleName],
  ];
  return (
    <div className="chain">
      {steps
        .filter(([, ref, name]) => ref || name)
        .map(([label, ref, name]) => (
          <span key={label} className="chain-step">
            <span className="muted">{label}</span> {ref?.name ?? name}
            {!ref && <Tag color="orange">직접 입력</Tag>}
          </span>
        ))}
    </div>
  );
}

export function Histories() {
  const [tab, setTab] = useState<"approval" | "verification">("approval");
  return (
    <>
      <header className="page-heading">
        <div>
          <span className="eyebrow">REVIEW</span>
          <h1>이력 검토</h1>
          <p>사용자가 직접 적은 조직·프로그램·활동·역할을 등록된 활동 정보에 맞춰 승인하고, 올린 인증자료를 확인하세요.</p>
        </div>
      </header>
      <div className="editorial-note">
        <AuditOutlined aria-hidden="true" />
        <div>
          <strong>승인한 이력만 명함에 넣을 수 있어요.</strong>
          <span>인증은 명함을 막지 않고, 확인되지 않은 이력에는 앱이 ‘미확인’ 배지를 붙여요.</span>
        </div>
      </div>
      <Tabs
        activeKey={tab}
        onChange={(key) => setTab(key as typeof tab)}
        items={[
          { key: "approval", label: "승인 대기", children: <ReviewList mode="approval" /> },
          { key: "verification", label: "인증 확인", children: <ReviewList mode="verification" /> },
        ]}
      />
    </>
  );
}

function ReviewList({ mode }: { mode: "approval" | "verification" }) {
  const cursor = useCursorList<HistoryForReview>("histories", mode === "approval" ? { approval: "pending" } : { verification: "pending" });
  const [reviewing, setReviewing] = useState<HistoryForReview>();
  const { message } = App.useApp();
  const client = useQueryClient();
  const verification = useMutation({
    mutationFn: ({ id, decision }: { id: string; decision: "verify" | "reject" }) =>
      api<HistoryForReview>(`histories/${id}/verification`, "POST", { decision }),
    onSuccess: async (_, { decision }) => {
      message.success(decision === "verify" ? "인증을 확인했어요." : "인증을 거절했어요.");
      await client.invalidateQueries({ queryKey: ["histories"] });
    },
    onError: (error) => message.error(error.message),
  });
  const verifyOnce = useSingleFlight();
  return (
    <>
      <CursorTable
        cursor={cursor}
        scroll={{ x: 900 }}
        locale={{ emptyText: mode === "approval" ? "승인을 기다리는 이력이 없어요." : "확인할 인증자료가 없어요." }}
        columns={[
          { title: "작성자", width: 140, render: (_, h) => <strong>{h.profileName || "이름 없음"}</strong> },
          {
            title: "이력",
            render: (_, h) => (
              <div className="activity-title">
                <Chain history={h} />
                <span>
                  {h.kind === "organization" ? "조직 활동" : "활동 참여"} · {period(h)}
                </span>
                {h.description && <span className="muted">{h.description}</span>}
              </div>
            ),
          },
          { title: "인증자료", width: 220, render: (_, h) => <EvidenceList history={h} /> },
          {
            title: "",
            className: "actions",
            render: (_, h) =>
              mode === "approval" ? (
                <Button type="primary" onClick={() => setReviewing(h)}>
                  검토
                </Button>
              ) : (
                <Space>
                  <Button
                    type="primary"
                    loading={verification.isPending && verification.variables?.id === h.id && verification.variables.decision === "verify"}
                    disabled={verification.isPending}
                    onClick={() => verifyOnce(() => verification.mutateAsync({ id: h.id, decision: "verify" }))}
                  >
                    확인
                  </Button>
                  <Button
                    danger
                    loading={verification.isPending && verification.variables?.id === h.id && verification.variables.decision === "reject"}
                    disabled={verification.isPending}
                    onClick={() => verifyOnce(() => verification.mutateAsync({ id: h.id, decision: "reject" }))}
                  >
                    거절
                  </Button>
                </Space>
              ),
          },
        ]}
      />
      {reviewing && <ApprovalModal history={reviewing} onClose={() => setReviewing(undefined)} />}
    </>
  );
}

/** Evidence files come through the authenticated proxy, so they open from a blob URL rather than a plain link. */
function EvidenceList({ history }: { history: HistoryForReview }) {
  const { message } = App.useApp();
  const open = useMutation({
    mutationFn: async (evidence: Evidence) => {
      const url = URL.createObjectURL(await apiBlob(`histories/${history.id}/evidence/${evidence.id}`));
      window.open(url, "_blank", "noopener");
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    },
    onError: (error) => message.error(error.message),
  });
  if (!history.evidence.length) return <span className="muted">없음</span>;
  return (
    <ul className="evidence-list">
      {history.evidence.map((evidence) => (
        <li key={evidence.id}>
          <Button type="link" size="small" loading={open.isPending && open.variables?.id === evidence.id} onClick={() => open.mutate(evidence)}>
            {evidence.fileName}
          </Button>
          <span className="muted">{Math.round(evidence.size / 1024)}KB</span>
        </li>
      ))}
    </ul>
  );
}

/** Maps each free-typed value to a catalog item (search or create) and approves, or rejects with a reason. */
function ApprovalModal({ history, onClose }: { history: HistoryForReview; onClose: () => void }) {
  const { message } = App.useApp();
  const client = useQueryClient();
  const [mapping, setMapping] = useState<Mapping>({});
  const [reason, setReason] = useState("");
  const decide = useMutation({
    mutationFn: (body: { decision: "approve" | "reject"; reason?: string } & Mapping) => api<HistoryForReview>(`histories/${history.id}/approval`, "POST", body),
    onSuccess: async (_, { decision }) => {
      message.success(decision === "approve" ? "승인했어요." : "거절했어요.");
      onClose();
      await client.invalidateQueries({ queryKey: ["histories"] });
    },
  });
  const decideOnce = useSingleFlight();
  const organizationId = mapping.organizationId ?? history.organization?.id;
  const programId = mapping.programId ?? history.program?.id;
  return (
    <Modal open title={`이력 검토 · ${history.profileName || "이름 없음"}`} onCancel={onClose} footer={null} width={640}>
      {decide.error && <Alert className="form-error" type="error" showIcon message={decide.error.message} />}
      <Chain history={history} />
      <p className="field-note">
        {history.kind === "organization" ? "조직 활동" : "활동 참여"} · {period(history)}
        {history.description && ` · ${history.description}`}
      </p>
      <Form layout="vertical">
        {!history.organization && (
          <MapField
            label={`조직 ‘${history.organizationName}’`}
            path="organizations"
            value={mapping.organizationId}
            onChange={(organizationId) => setMapping({ ...mapping, organizationId, programId: undefined, activityId: undefined })}
            create={{ body: (name) => ({ name }), suggested: history.organizationName ?? "" }}
          />
        )}
        {!history.program && history.programName && (
          <MapField
            label={`프로그램 ‘${history.programName}’`}
            path="programs"
            params={{ organizationId }}
            disabled={!organizationId}
            hint={!organizationId ? "조직을 먼저 정해 주세요." : undefined}
            value={mapping.programId}
            onChange={(programId) => setMapping({ ...mapping, programId, activityId: undefined })}
            create={organizationId ? { body: (name) => ({ organizationId, name }), suggested: history.programName } : undefined}
          />
        )}
        {!history.activity && history.activityName && (
          <MapField
            label={`활동 ‘${history.activityName}’`}
            path="activities"
            params={{ programId, publicationStatus: "PUBLISHED" }}
            value={mapping.activityId}
            onChange={(activityId) => setMapping({ ...mapping, activityId })}
            hint="게시된 활동만 고를 수 있어요. 고르지 않으면 적힌 이름 그대로 승인돼요."
          />
        )}
        {!history.role && (
          <MapField
            label={`역할 ‘${history.roleName}’`}
            path="roles"
            value={mapping.roleId}
            onChange={(roleId) => setMapping({ ...mapping, roleId })}
            create={{ body: (name) => ({ name }), suggested: history.roleName ?? "" }}
          />
        )}
        <Form.Item label="거절 사유(거절할 때만)">
          <Input.TextArea value={reason} onChange={(e) => setReason(e.target.value)} autoSize={{ minRows: 2, maxRows: 4 }} maxLength={500} />
        </Form.Item>
      </Form>
      <Space className="modal-actions">
        <Button
          type="primary"
          loading={decide.isPending && decide.variables?.decision === "approve"}
          disabled={decide.isPending}
          onClick={() => decideOnce(() => decide.mutateAsync({ decision: "approve", ...mapping }))}
        >
          승인
        </Button>
        <Button
          danger
          loading={decide.isPending && decide.variables?.decision === "reject"}
          disabled={decide.isPending || !reason.trim()}
          onClick={() => decideOnce(() => decide.mutateAsync({ decision: "reject", reason: reason.trim() }))}
        >
          거절
        </Button>
        <Button onClick={onClose}>닫기</Button>
      </Space>
    </Modal>
  );
}

/** One free-typed value: pick an existing catalog item, or create one from the typed name and use its id. */
function MapField({
  label,
  path,
  params,
  value,
  onChange,
  create,
  disabled,
  hint,
}: {
  label: string;
  path: "organizations" | "programs" | "activities" | "roles";
  params?: Record<string, string | number | undefined>;
  value?: number;
  onChange: (value?: number) => void;
  create?: { body: (name: string) => Record<string, unknown>; suggested: string };
  disabled?: boolean;
  hint?: string;
}) {
  const { message } = App.useApp();
  const client = useQueryClient();
  const [name, setName] = useState(create?.suggested ?? "");
  useEffect(() => setName(create?.suggested ?? ""), [create?.suggested]);
  const creating = useMutation({
    mutationFn: () => api<{ id: number; name: string }>(path, "POST", create!.body(name.trim())),
    onSuccess: async (item) => {
      message.success(`‘${item.name}’을(를) 만들어 연결했어요.`);
      onChange(item.id);
      await client.invalidateQueries({ queryKey: [path] });
    },
    onError: (error) => message.error(error.message),
  });
  const createOnce = useSingleFlight();
  return (
    <Form.Item label={label} extra={hint}>
      <Space wrap>
        <SearchSelect
          key={JSON.stringify(params)}
          path={path}
          params={params}
          placeholder="기존 항목 검색"
          value={value}
          onChange={onChange}
          style={{ width: 260 }}
        />
        {create && (
          <>
            <Input aria-label="새로 만들 이름" value={name} onChange={(e) => setName(e.target.value)} style={{ width: 200 }} disabled={disabled} />
            <Button loading={creating.isPending} disabled={disabled || !name.trim()} onClick={() => createOnce(() => creating.mutateAsync())}>
              새로 만들기
            </Button>
          </>
        )}
      </Space>
    </Form.Item>
  );
}
