import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Alert, App, Button, Form, Input, Modal, Popconfirm, Space } from "antd";
import { api, useSingleFlight } from "./api";
import { CursorTable, useCursorList } from "./list";

type Role = { id: number; name: string; nameEn: string | null };

export function Roles() {
  const [search, setSearch] = useState("");
  const [editing, setEditing] = useState<Role | "new">();
  const cursor = useCursorList<Role>("roles", { q: search });
  const { message } = App.useApp();
  const client = useQueryClient();
  const remove = useMutation({
    mutationFn: (id: number) => api<void>(`roles/${id}`, "DELETE"),
    onSuccess: async () => {
      message.success("삭제했어요.");
      await client.invalidateQueries({ queryKey: ["roles"] });
    },
  });
  const removeOnce = useSingleFlight();
  return (
    <>
      <header className="page-heading">
        <div>
          <span className="eyebrow">CATALOG</span>
          <h1>역할</h1>
          <p>이력에서 고르는 역할 목록이에요. 띄어쓰기만 다른 이름은 같은 역할로 봐요.</p>
        </div>
        <Button type="primary" onClick={() => setEditing("new")}>
          역할 추가
        </Button>
      </header>
      <div className="list-toolbar">
        <Input.Search aria-label="역할 검색" placeholder="역할 이름 검색" allowClear onSearch={setSearch} style={{ width: 260 }} />
      </div>
      <CursorTable
        cursor={cursor}
        locale={{ emptyText: "역할이 없어요." }}
        columns={[
          {
            title: "역할",
            render: (_, role) => (
              <strong>
                {role.name}
                {role.nameEn && <span className="name-en">{role.nameEn}</span>}
              </strong>
            ),
          },
          {
            title: "",
            className: "actions",
            render: (_, role) => (
              <>
                <Space>
                  <Button onClick={() => setEditing(role)}>이름 편집</Button>
                  <Popconfirm
                    title={`‘${role.name}’ 역할을 삭제할까요?`}
                    description="이력이 쓰고 있으면 삭제되지 않아요."
                    okText="삭제"
                    cancelText="취소"
                    okButtonProps={{ danger: true }}
                    disabled={remove.isPending}
                    onConfirm={() => removeOnce(() => remove.mutateAsync(role.id))}
                  >
                    <Button danger loading={remove.isPending && remove.variables === role.id} disabled={remove.isPending && remove.variables !== role.id}>
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
      {editing && <RoleEditor role={editing === "new" ? undefined : editing} onClose={() => setEditing(undefined)} />}
    </>
  );
}

function RoleEditor({ role, onClose }: { role?: Role; onClose: () => void }) {
  const [form] = Form.useForm();
  const { message } = App.useApp();
  const client = useQueryClient();
  const save = useMutation({
    mutationFn: (body: { name: string; nameEn: string }) => (role ? api<Role>(`roles/${role.id}`, "PATCH", body) : api<Role>("roles", "POST", body)),
    onSuccess: async () => {
      message.success("저장했어요.");
      onClose();
      await client.invalidateQueries({ queryKey: ["roles"] });
    },
  });
  const saveOnce = useSingleFlight();
  return (
    <Modal
      open
      title={role ? `역할 편집 · ${role.name}` : "역할 추가"}
      onCancel={onClose}
      onOk={() => form.submit()}
      confirmLoading={save.isPending}
      okText="저장"
      cancelText="취소"
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={{ name: role?.name ?? "", nameEn: role?.nameEn ?? "" }}
        onFinish={(values: { name: string; nameEn: string }) => saveOnce(() => save.mutateAsync({ name: values.name.trim(), nameEn: values.nameEn.trim() }))}
      >
        {save.error && <Alert className="form-error" type="error" showIcon message={save.error.message} />}
        <Form.Item name="name" label="한글 이름" rules={[{ required: true, whitespace: true, message: "이름을 입력해 주세요." }]}>
          <Input />
        </Form.Item>
        <Form.Item name="nameEn" label="영문 이름(선택)">
          <Input />
        </Form.Item>
      </Form>
    </Modal>
  );
}
