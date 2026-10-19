import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Alert, App, Button, Form, Input, Modal, Popconfirm, Space, Table } from "antd";
import { api, useSingleFlight } from "./api";
import { type TagItem, tagsQueryKey, useTags } from "./tag-select";

/** 태그 사전 (contract native-v1.md "태그(얻을 수 있는 것)"): what a participant gains — 창업, 협업, 멘토링 …. Renames keep links; delete removes them. */
export function Tags() {
  const [editing, setEditing] = useState<TagItem | "new">();
  const tags = useTags();
  const { message } = App.useApp();
  const client = useQueryClient();
  const remove = useMutation({
    mutationFn: (id: number) => api<void>(`tags/${id}`, "DELETE"),
    onSuccess: async () => {
      message.success("삭제했어요.");
      await Promise.all([
        client.invalidateQueries({ queryKey: tagsQueryKey }),
        client.invalidateQueries({ queryKey: ["activities"] }),
        client.invalidateQueries({ queryKey: ["programs"] }),
      ]);
    },
  });
  const removeOnce = useSingleFlight();
  return (
    <>
      <header className="page-heading">
        <div>
          <span className="eyebrow">ACTIVITY</span>
          <h1>태그</h1>
          <p>활동과 프로그램에서 얻을 수 있는 것을 나타내는 어휘예요. 편집 폼에서 바로 적어 넣은 이름도 여기에 쌓여요. 띄어쓰기만 다른 이름은 같은 태그로 봐요.</p>
        </div>
        <Button type="primary" onClick={() => setEditing("new")}>
          태그 추가
        </Button>
      </header>
      {tags.isError ? (
        <Alert type="error" showIcon message="태그를 불러오지 못했어요." description={tags.error.message} action={<Button onClick={() => tags.refetch()}>다시 시도</Button>} />
      ) : (
        <Table<TagItem>
          rowKey="id"
          dataSource={tags.data ?? []}
          loading={tags.isLoading}
          pagination={false}
          locale={{ emptyText: "태그가 없어요." }}
          columns={[
            {
              title: "태그",
              render: (_, tag) => (
                <strong>
                  {tag.name}
                  {tag.nameEn ? <span className="name-en">{tag.nameEn}</span> : <span className="muted name-en">영문 이름 없음</span>}
                </strong>
              ),
            },
            {
              title: "",
              className: "actions",
              render: (_, tag) => (
                <>
                  <Space>
                    <Button onClick={() => setEditing(tag)}>이름 편집</Button>
                    <Popconfirm
                      title={`‘${tag.name}’ 태그를 삭제할까요?`}
                      description="연결된 활동·프로그램에서도 떼어져요."
                      okText="삭제"
                      cancelText="취소"
                      okButtonProps={{ danger: true }}
                      disabled={remove.isPending}
                      onConfirm={() => removeOnce(() => remove.mutateAsync(tag.id))}
                    >
                      <Button danger loading={remove.isPending && remove.variables === tag.id} disabled={remove.isPending && remove.variables !== tag.id}>
                        삭제
                      </Button>
                    </Popconfirm>
                  </Space>
                  {remove.error && remove.variables === tag.id && (
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
      {editing && <TagEditor tag={editing === "new" ? undefined : editing} onClose={() => setEditing(undefined)} />}
    </>
  );
}

function TagEditor({ tag, onClose }: { tag?: TagItem; onClose: () => void }) {
  const [form] = Form.useForm();
  const { message } = App.useApp();
  const client = useQueryClient();
  const save = useMutation({
    mutationFn: (body: { name: string; nameEn: string }) => (tag ? api<TagItem>(`tags/${tag.id}`, "PATCH", body) : api<TagItem>("tags", "POST", body)),
    onSuccess: async () => {
      message.success("저장했어요.");
      onClose();
      await Promise.all([
        client.invalidateQueries({ queryKey: tagsQueryKey }),
        client.invalidateQueries({ queryKey: ["activities"] }),
        client.invalidateQueries({ queryKey: ["programs"] }),
      ]);
    },
  });
  const saveOnce = useSingleFlight();
  return (
    <Modal
      open
      title={tag ? `태그 편집 · ${tag.name}` : "태그 추가"}
      onCancel={onClose}
      onOk={() => form.submit()}
      confirmLoading={save.isPending}
      okText="저장"
      cancelText="취소"
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={{ name: tag?.name ?? "", nameEn: tag?.nameEn ?? "" }}
        onFinish={(values: { name: string; nameEn: string }) => saveOnce(() => save.mutateAsync({ name: values.name.trim(), nameEn: values.nameEn.trim() }))}
      >
        {save.error && <Alert className="form-error" type="error" showIcon message={save.error.message} />}
        <Form.Item name="name" label="한글 이름" rules={[{ required: true, whitespace: true, message: "이름을 입력해 주세요." }]}>
          <Input placeholder="예: 멘토링" />
        </Form.Item>
        <Form.Item name="nameEn" label="영문 이름(선택)" extra="수집기와 검색은 한글·영문 이름을 모두 봐요. 비우면 지워져요.">
          <Input placeholder="예: mentoring" />
        </Form.Item>
      </Form>
    </Modal>
  );
}
