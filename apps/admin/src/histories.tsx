import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Alert, App, Button, Form, Input, Modal, Radio, Space, Tabs, Tag } from "antd";
import { AuditOutlined } from "@ant-design/icons";
import { api, apiBlob, useSingleFlight } from "./api";
import { CursorTable, SearchSelect, useCursorList } from "./list";
import { type OwnerKind, type Role, ownerKindLabels, ownerParam } from "./roles";

type Ref = { id: number; name: string } | null;
type Evidence = { id: string; fileName: string; contentType: string; size: number; uploadedAt: string };
/** 귀속 단위 (contract native-v1.md "이력 (History) — 역할 매칭"): `id` is null when the user typed the unit's name instead of picking one. */
type Owner = { kind: OwnerKind; id: number | null; name: string };
/** `GET histories` item: the user's history plus the author's profile name. Never an account id. */
export type HistoryForReview = {
  id: string;
  profileName: string;
  /** The matched role; null when the user typed `roleName` instead. */
  role: Ref;
  roleName: string;
  owner: Owner;
  /** Convenience refs the server derives from the role's owner (activity → all three, program → organization·program, organization → organization). */
  organization: Ref;
  program: Ref;
  activity: Ref;
  startDate: string;
  endDate: string | null;
  description: string;
  approval: "approved" | "pending" | "rejected";
  approvalReason: string | null;
  verification: "unverified" | "pending" | "verified" | "rejected";
  evidence: Evidence[];
};

const month = (date: string) => date.slice(0, 7).replace("-", ".");
const period = (h: HistoryForReview) => `${month(h.startDate)} – ${h.endDate ? month(h.endDate) : "진행 중"}`;
const kindPaths: Record<OwnerKind, "organizations" | "programs" | "activities"> = { organization: "organizations", program: "programs", activity: "activities" };

/** `귀속 단위 이름 · 역할 이름` with the unit's kind; a typed (unregistered) unit or role gets a tag so the reviewer knows what to pick. */
function Matched({ history }: { history: HistoryForReview }) {
  const { owner, role, roleName } = history;
  // Context above the unit: the program's organization, or the activity's organization·program.
  const above = owner.kind === "organization" ? [] : [history.organization, owner.kind === "activity" ? history.program : null].filter((ref) => ref && ref.id !== owner.id);
  return (
    <div className="chain">
      <span className="chain-step">
        <Tag bordered={false}>{ownerKindLabels[owner.kind]}</Tag>
        {above.length > 0 && <span className="muted">{above.map((ref) => ref!.name).join(" · ")} · </span>}
        {owner.name}
        {owner.id === null && <Tag color="orange">직접 입력</Tag>}
      </span>
      <span className="chain-step">
        <span className="muted">역할</span> {role?.name ?? roleName}
        {!role && <Tag color="orange">직접 입력</Tag>}
      </span>
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
          <p>사용자가 직접 적은 단위·역할을 등록된 조직·프로그램·활동과 그 역할에 맞춰 승인하고, 올린 인증자료를 확인하세요.</p>
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
                <Matched history={h} />
                <span>{period(h)}</span>
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

type Decision = { decision: "approve" | "reject"; ownerKind?: OwnerKind; ownerId?: number; roleId?: number; reason?: string };

/**
 * Approves with the server's rules (contract admin-v1.md "이력 검토·역할"): a typed unit needs a registered one (`ownerKind·ownerId`),
 * and then the role must be one of that unit's (`roleId`); a typed role on a registered unit needs only `roleId`. Rejecting needs a reason.
 */
export function ApprovalModal({ history, onClose }: { history: HistoryForReview; onClose: () => void }) {
  const { message } = App.useApp();
  const client = useQueryClient();
  const needsOwner = history.owner.id === null;
  const [ownerKind, setOwnerKind] = useState<OwnerKind>(history.owner.kind);
  const [ownerId, setOwnerId] = useState<number>();
  const [roleId, setRoleId] = useState<number>();
  const [reason, setReason] = useState("");
  const owner = needsOwner ? (ownerId === undefined ? undefined : { kind: ownerKind, id: ownerId }) : { kind: history.owner.kind, id: history.owner.id! };
  // Picking a unit makes the role mandatory even when one was matched: the server re-checks it against the new unit.
  const needsRole = needsOwner || !history.role;
  const decide = useMutation({
    mutationFn: (body: Decision) => api<HistoryForReview>(`histories/${history.id}/approval`, "POST", body),
    onSuccess: async (_, { decision }) => {
      message.success(decision === "approve" ? "승인했어요." : "거절했어요.");
      onClose();
      await client.invalidateQueries({ queryKey: ["histories"] });
    },
  });
  const decideOnce = useSingleFlight();
  // The created role is kept in state before approving, so a failed approval retries with the same id instead of a duplicate POST.
  const create = useMutation({
    mutationFn: () => api<Role>("roles", "POST", { ...ownerParam(owner!.kind, owner!.id), name: history.roleName }),
    onSuccess: async (created) => {
      setRoleId(created.id);
      message.success(`‘${created.name}’ 역할을 만들어 연결했어요.`);
      await client.invalidateQueries({ queryKey: ["roles"] });
    },
    onError: (error) => message.error(error.message),
  });
  const createOnce = useSingleFlight();
  const created = create.data && owner && create.data.owner.kind === owner.kind && create.data.owner.id === owner.id ? create.data : undefined;
  const ready = owner !== undefined && (!needsRole || roleId !== undefined);
  const approve = () =>
    decideOnce(() =>
      decide.mutateAsync({
        decision: "approve",
        ...(needsOwner ? { ownerKind: owner!.kind, ownerId: owner!.id } : {}),
        ...(needsRole ? { roleId } : {}),
      }),
    );
  return (
    <Modal open title={`이력 검토 · ${history.profileName || "이름 없음"}`} onCancel={onClose} footer={null} width={640}>
      {decide.error && <Alert className="form-error" type="error" showIcon message={decide.error.message} />}
      <Matched history={history} />
      <p className="field-note">
        {period(history)}
        {history.description && ` · ${history.description}`}
      </p>
      <Form layout="vertical">
        {needsOwner && (
          <Form.Item label={`단위 ‘${history.owner.name}’`} extra="사용자가 이름만 적었어요. 등록된 단위를 골라 주세요. 활동은 게시된 것만 고를 수 있어요.">
            <Space wrap>
              <Radio.Group
                aria-label="단위 종류"
                optionType="button"
                value={ownerKind}
                options={(Object.keys(ownerKindLabels) as OwnerKind[]).map((kind) => ({ value: kind, label: ownerKindLabels[kind] }))}
                onChange={(e) => {
                  setOwnerKind(e.target.value);
                  setOwnerId(undefined);
                  setRoleId(undefined);
                }}
              />
              <SearchSelect
                key={ownerKind}
                path={kindPaths[ownerKind]}
                params={ownerKind === "activity" ? { publicationStatus: "PUBLISHED" } : {}}
                aria-label={`${ownerKindLabels[ownerKind]} 검색`}
                placeholder={`${ownerKindLabels[ownerKind]} 이름 검색`}
                value={ownerId}
                onChange={(next) => {
                  setOwnerId(next);
                  setRoleId(undefined);
                }}
                style={{ width: 260 }}
              />
            </Space>
          </Form.Item>
        )}
        {needsRole && (
          <Form.Item
            label={`역할 ‘${history.roleName}’`}
            extra={owner ? "고른 단위의 역할 중에서 고르거나, 적힌 이름 그대로 역할을 만들어요." : "단위를 먼저 골라 주세요."}
          >
            {owner && (
              <Space wrap>
                <SearchSelect
                  // Remounts after a role is created so the select starts with that role's name as its label.
                  key={`${owner.kind}/${owner.id}/${created?.id ?? ""}`}
                  path="roles"
                  params={ownerParam(owner.kind, owner.id)}
                  aria-label="역할 검색"
                  placeholder="역할 검색"
                  value={roleId}
                  label={created && created.id === roleId ? created.name : undefined}
                  onChange={setRoleId}
                  style={{ width: 260 }}
                />
                <Button
                  loading={create.isPending}
                  disabled={create.isPending || roleId !== undefined}
                  // Already created for this unit (then cleared): reconnect it instead of a duplicate POST.
                  onClick={() => (created ? setRoleId(created.id) : createOnce(() => create.mutateAsync()))}
                >
                  이 이름으로 역할 만들기
                </Button>
              </Space>
            )}
          </Form.Item>
        )}
        <Form.Item label="거절 사유(거절할 때만)">
          <Input.TextArea value={reason} onChange={(e) => setReason(e.target.value)} autoSize={{ minRows: 2, maxRows: 4 }} maxLength={500} />
        </Form.Item>
      </Form>
      <Space className="modal-actions">
        <Button type="primary" loading={decide.isPending && decide.variables?.decision === "approve"} disabled={decide.isPending || !ready} onClick={approve}>
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
