import { useState, type KeyboardEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, App, Button, Input, Modal, Popconfirm, Space, Table, Tag } from "antd";
import { ArrowDownOutlined, ArrowUpOutlined } from "@ant-design/icons";
import { api, useSingleFlight } from "./api";
import { allPages } from "./list";

/** 역할 귀속(contract admin-v1.md "이력 검토·역할", 2026-11-03): every role belongs to one organization, program or activity. */
export type OwnerKind = "organization" | "program" | "activity";
export type RoleOwner = { kind: OwnerKind; id: number; name: string };
export type Role = { id: number; name: string; nameEn: string | null; owner: RoleOwner; source: "curated" | "collected" };

export const ownerKindLabels: Record<OwnerKind, string> = { organization: "조직", program: "프로그램", activity: "활동" };
/** `GET`·`POST roles` take the owner as exactly one of `organizationId`·`programId`·`activityId`. */
export const ownerParam = (kind: OwnerKind, id: number): Record<string, number> => ({ [`${kind}Id`]: id });

type Draft = { id?: number; name: string; nameEn: string };

/**
 * The 역할 절 of one organization, program or activity: its roles in display order, with add, rename, reorder and delete.
 * Every change goes to the API as it happens (no enclosing save button), so the section sits inside other editors
 * without rendering its own `<form>`.
 */
export function RolesSection({ kind, id }: { kind: OwnerKind; id: number }) {
  const { message } = App.useApp();
  const client = useQueryClient();
  const [draft, setDraft] = useState<Draft>();
  const roles = useQuery({ queryKey: ["roles", kind, id], queryFn: () => allPages<Role>("roles", ownerParam(kind, id)) });
  const items = roles.data ?? [];
  const stale = () => client.invalidateQueries({ queryKey: ["roles"] }); // The sections and every 역할 search select.
  const save = useMutation({
    mutationFn: ({ id: roleId, name, nameEn }: Draft) => {
      const body = { name: name.trim(), nameEn: nameEn.trim() }; // An empty English name clears it.
      if (!body.name) throw new Error("이름을 입력해 주세요.");
      return roleId === undefined ? api<Role>("roles", "POST", { ...ownerParam(kind, id), ...body }) : api<Role>(`roles/${roleId}`, "PATCH", body);
    },
    onSuccess: async (_, saved) => {
      setDraft(undefined);
      message.success(saved.id === undefined ? "역할을 추가했어요." : "저장했어요.");
      await stale();
    },
  });
  const saveOnce = useSingleFlight();
  const submit = () => draft && saveOnce(() => save.mutateAsync(draft));
  const remove = useMutation({
    mutationFn: (roleId: number) => api<void>(`roles/${roleId}`, "DELETE"),
    onSuccess: async () => {
      message.success("삭제했어요.");
      await stale();
    },
  });
  const removeOnce = useSingleFlight();
  // Positions are not unique on the server (migrated rows may share 0), so a move renumbers every role whose index changed.
  const move = useMutation({
    mutationFn: async ({ from, to }: { from: number; to: number }) => {
      const order = [...items];
      order.splice(to, 0, ...order.splice(from, 1));
      await Promise.all(order.flatMap((role, index) => (items[index]?.id === role.id ? [] : [api<Role>(`roles/${role.id}`, "PATCH", { position: index })])));
    },
    onSuccess: stale,
    onError: (error) => message.error(error.message),
  });
  const moveOnce = useSingleFlight();
  const busy = save.isPending || remove.isPending || move.isPending;
  const cancelOnEscape = (e: KeyboardEvent) => e.key === "Escape" && !save.isPending && setDraft(undefined);
  const submitOnEnter = (e: KeyboardEvent) => {
    e.preventDefault(); // Keeps the enclosing editor's form from submitting.
    submit();
  };
  const editor = (label: string) => (
    <Space wrap>
      <Input
        autoFocus
        aria-label="역할 이름"
        placeholder="역할 이름"
        value={draft!.name}
        disabled={save.isPending}
        onChange={(e) => setDraft({ ...draft!, name: e.target.value })}
        onPressEnter={submitOnEnter}
        onKeyDown={cancelOnEscape}
        style={{ width: 180 }}
      />
      <Input
        aria-label="영문 이름(선택)"
        placeholder="영문 이름(선택)"
        value={draft!.nameEn}
        disabled={save.isPending}
        onChange={(e) => setDraft({ ...draft!, nameEn: e.target.value })}
        onPressEnter={submitOnEnter}
        onKeyDown={cancelOnEscape}
        style={{ width: 160 }}
      />
      <Button type="primary" size="small" loading={save.isPending} onClick={submit}>
        {label}
      </Button>
      <Button size="small" disabled={save.isPending} onClick={() => setDraft(undefined)}>
        취소
      </Button>
    </Space>
  );
  return (
    <div className="roles-section">
      <p className="field-note">
        이력에서 이 {ownerKindLabels[kind]}의 역할로 고를 수 있어요. 추가·수정·삭제는 저장 버튼과 상관없이 바로 반영되고, 띄어쓰기만 다른 이름은 같은 역할로 봐요.
        {kind === "activity" && " ‘수집’ 역할은 공고의 모집 역할 문구로 만든 것이고, 고치면 운영자 지정이 되어 다음 수집이 바꾸지 않아요."}
      </p>
      {roles.isError ? (
        <Alert type="error" showIcon message="역할을 불러오지 못했어요." description={roles.error.message} action={<Button onClick={() => roles.refetch()}>다시 시도</Button>} />
      ) : (
        <Table<Role>
          size="small"
          rowKey="id"
          pagination={false}
          loading={roles.isLoading}
          dataSource={items}
          locale={{ emptyText: "아직 역할이 없어요." }}
          columns={[
            {
              title: "역할",
              render: (_, role) =>
                draft?.id === role.id ? (
                  <>
                    {editor("저장")}
                    {save.error && (
                      <div className="tree-error" role="alert">
                        {save.error.message}
                      </div>
                    )}
                  </>
                ) : (
                  <>
                    {role.name}
                    {role.nameEn && <span className="muted name-en">{role.nameEn}</span>}
                  </>
                ),
            },
            {
              title: "출처",
              width: 90,
              render: (_, role) => (role.source === "collected" ? <Tag color="geekblue">수집</Tag> : <Tag>운영자</Tag>),
            },
            {
              title: "",
              className: "actions",
              width: 230,
              render: (_, role, index) => (
                <>
                  <Space>
                    <Button
                      size="small"
                      aria-label="위로"
                      icon={<ArrowUpOutlined aria-hidden="true" />}
                      disabled={!!draft || busy || index === 0}
                      onClick={() => moveOnce(() => move.mutateAsync({ from: index, to: index - 1 }))}
                    />
                    <Button
                      size="small"
                      aria-label="아래로"
                      icon={<ArrowDownOutlined aria-hidden="true" />}
                      disabled={!!draft || busy || index === items.length - 1}
                      onClick={() => moveOnce(() => move.mutateAsync({ from: index, to: index + 1 }))}
                    />
                    <Button
                      size="small"
                      disabled={!!draft || busy}
                      onClick={() => {
                        save.reset();
                        remove.reset();
                        setDraft({ id: role.id, name: role.name, nameEn: role.nameEn ?? "" });
                      }}
                    >
                      편집
                    </Button>
                    <Popconfirm
                      title={`‘${role.name}’ 역할을 삭제할까요?`}
                      description="이력이 쓰고 있으면 삭제되지 않아요."
                      okText="삭제"
                      cancelText="취소"
                      okButtonProps={{ danger: true }}
                      disabled={!!draft || busy}
                      onConfirm={() => removeOnce(() => remove.mutateAsync(role.id))}
                    >
                      <Button size="small" danger disabled={!!draft || busy} loading={remove.isPending && remove.variables === role.id}>
                        삭제
                      </Button>
                    </Popconfirm>
                  </Space>
                  {remove.error && remove.variables === role.id && (
                    <div className="tree-error" role="alert">
                      {remove.error.message}
                    </div>
                  )}
                </>
              ),
            },
          ]}
        />
      )}
      <div>
        {draft && draft.id === undefined ? (
          <>
            {editor("추가")}
            {save.error && (
              <div className="tree-error" role="alert">
                {save.error.message}
              </div>
            )}
          </>
        ) : (
          <Button
            size="small"
            disabled={!!draft || busy || roles.isError}
            onClick={() => {
              save.reset();
              setDraft({ name: "", nameEn: "" });
            }}
          >
            역할 추가
          </Button>
        )}
      </div>
    </div>
  );
}

/** The 역할 절 on its own, for screens without an editor modal (the organization tree). */
export function RolesModal({ owner, onClose }: { owner: RoleOwner; onClose: () => void }) {
  return (
    <Modal open title={`역할 · ${owner.name}`} onCancel={onClose} footer={null} width={640}>
      <RolesSection kind={owner.kind} id={owner.id} />
    </Modal>
  );
}
