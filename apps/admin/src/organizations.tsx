import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Alert, App, Button, Form, Input, Modal } from "antd";
import { PlusOutlined } from "@ant-design/icons";
import { api } from "./api";
import { CursorTable, SearchSelect, useCursorList } from "./list";

type Organization = { id: number; name: string; parentOrganizationId: number | null };

export function Organizations() {
  const [search, setSearch] = useState(""),
    [creating, setCreating] = useState(false);
  const cursor = useCursorList<Organization>("organizations", { q: search });
  const names = new Map(cursor.items.map((o) => [o.id, o.name]));
  return (
    <>
      <header className="page-heading">
        <div>
          <span className="eyebrow">ORGANIZATIONS</span>
          <h1>조직 관리</h1>
          <p>활동을 운영하는 기관과 하위 조직을 등록하세요.</p>
        </div>
        <Button type="primary" icon={<PlusOutlined aria-hidden="true" />} onClick={() => setCreating(true)}>
          조직 만들기
        </Button>
      </header>
      <div className="list-toolbar">
        <Input.Search
          aria-label="조직 검색"
          placeholder="조직 이름 검색"
          allowClear
          onSearch={setSearch}
          style={{ maxWidth: 320 }}
        />
      </div>
      <CursorTable
        cursor={cursor}
        locale={{ emptyText: search ? "검색 결과가 없어요." : "등록된 조직이 없어요." }}
        columns={[
          { title: "ID", dataIndex: "id", width: 90 },
          { title: "조직 이름", dataIndex: "name" },
          {
            title: "상위 조직",
            dataIndex: "parentOrganizationId",
            render: (id: number | null) => (id === null ? "—" : (names.get(id) ?? `#${id}`)),
          },
        ]}
      />
      {creating && <CreateOrganization onClose={() => setCreating(false)} />}
    </>
  );
}

function CreateOrganization({ onClose }: { onClose: () => void }) {
  const [form] = Form.useForm();
  const { message } = App.useApp();
  const client = useQueryClient();
  const create = useMutation({
    mutationFn: (values: { name: string; parentOrganizationId?: number }) => api("organizations", "POST", values),
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ["organizations"] });
      message.success("조직을 만들었어요.");
      onClose();
    },
  });
  return (
    <Modal
      open
      title="조직 만들기"
      onCancel={onClose}
      onOk={() => form.submit()}
      confirmLoading={create.isPending}
      okText="만들기"
      cancelText="취소"
    >
      <Form form={form} layout="vertical" onFinish={(values) => create.mutate({ ...values, name: values.name.trim() })}>
        {create.error && <Alert className="form-error" type="error" showIcon message={create.error.message} />}
        <Form.Item
          name="name"
          label="조직 이름"
          rules={[{ required: true, whitespace: true, message: "이름을 입력해 주세요." }]}
        >
          <Input />
        </Form.Item>
        <Form.Item name="parentOrganizationId" label="상위 조직" extra="하위 조직이면 소속 기관을 검색해 고르세요.">
          <SearchSelect path="organizations" placeholder="없음 (최상위 조직)" />
        </Form.Item>
      </Form>
    </Modal>
  );
}
