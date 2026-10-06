import { useState } from "react";
import { useCreate, useList, useUpdate } from "@refinedev/core";
import {
  Alert,
  App,
  Button,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Switch,
  Table,
  Tag,
} from "antd";
import { PlusOutlined } from "@ant-design/icons";
import { type Organization, type Program, dateText } from "./catalog";

type Entry = Organization & Partial<Program>;
export function Directory({ kind }: { kind: "organizations" | "programs" }) {
  const isOrg = kind === "organizations",
    resource = `catalog_${kind}`,
    label = isOrg ? "조직" : "프로그램";
  const [page, setPage] = useState(1),
    [search, setSearch] = useState(""),
    [editing, setEditing] = useState<Entry | null | undefined>();
  const [error, setError] = useState("");
  const [form] = Form.useForm();
  const { message } = App.useApp();
  const { query, result } = useList<Entry>({
    resource,
    pagination: { currentPage: page, pageSize: 15 },
    sorters: [{ field: "updated_at", order: "desc" }],
    filters: search
      ? [
          {
            field: isOrg ? "name" : "title",
            operator: "contains",
            value: search,
          },
        ]
      : [],
  });
  const organizations = useList<Organization>({
    resource: "catalog_organizations",
    pagination: { pageSize: 1000 },
    queryOptions: { enabled: !isOrg },
  });
  const create = useCreate<Entry, any, Record<string, unknown>>(),
    update = useUpdate<Entry, any, Record<string, unknown>>();
  function open(entry: Entry | null) {
    setEditing(entry);
    setError("");
    form.resetFields();
    form.setFieldsValue(
      entry ?? {
        description: "",
        collection_enabled: false,
        collection_hosts: [],
      },
    );
  }
  async function save(values: Record<string, unknown>) {
    try {
      if (editing)
        await update.mutateAsync({ resource, id: editing.id, values });
      else await create.mutateAsync({ resource, values });
      setEditing(undefined);
      message.success(`${label}을 저장했어요.`);
    } catch {
      setError(
        "저장하지 못했어요. 필수 값과 연결 상태를 확인해 주세요. 활동에 연결된 프로그램의 조직은 변경할 수 없습니다.",
      );
    }
  }
  return (
    <>
      <header className="page-heading">
        <div>
          <span className="eyebrow">
            {isOrg ? "ORGANIZATIONS" : "PROGRAMS"}
          </span>
          <h1>{label} 관리</h1>
          <p>
            {isOrg
              ? "활동을 운영하는 조직의 이름과 소개를 관리하세요."
              : "같은 조직의 반복되는 활동을 하나의 프로그램으로 묶으세요."}
          </p>
        </div>
        <Button
          type="primary"
          icon={<PlusOutlined aria-hidden="true" />}
          onClick={() => open(null)}
        >
          {label} 만들기
        </Button>
      </header>
      <div className="list-toolbar">
        <Input.Search
          aria-label={`${label} 검색`}
          placeholder={`${label} 이름 검색`}
          allowClear
          onSearch={(value) => {
            setSearch(value);
            setPage(1);
          }}
          style={{ maxWidth: 320 }}
        />
      </div>
      {query.isError ? (
        <Alert
          type="error"
          message="목록을 불러오지 못했어요."
          action={<Button onClick={() => query.refetch()}>다시 시도</Button>}
        />
      ) : (
        <Table
          rowKey="id"
          loading={query.isLoading}
          dataSource={result.data}
          pagination={{
            current: page,
            pageSize: 15,
            total: result.total,
            onChange: setPage,
            showSizeChanger: false,
          }}
          locale={{ emptyText: `등록된 ${label}이 없습니다.` }}
          columns={[
            {
              title: `${label} 이름`,
              render: (_, entry) => (
                <Button type="link" onClick={() => open(entry)}>
                  {isOrg ? entry.name : entry.title}
                </Button>
              ),
            },
            ...(!isOrg
              ? [
                  {
                    title: "운영 조직",
                    render: (_: unknown, entry: Entry) =>
                      organizations.result.data.find(
                        (o) => o.id === entry.organization_id,
                      )?.name ?? "조직 확인 중",
                  },
                ]
              : []),
            { title: "소개", dataIndex: "description" },
            {
              title: "",
              render: (_, entry) => (
                <Button onClick={() => open(entry)}>편집</Button>
              ),
            },
          ]}
        />
      )}
      <Modal
        title={`${label} ${editing ? "편집" : "만들기"}`}
        open={editing !== undefined}
        onCancel={() => setEditing(undefined)}
        onOk={() => form.submit()}
        confirmLoading={create.mutation.isPending || update.mutation.isPending}
        okText="저장"
        cancelText="취소"
        destroyOnHidden
      >
        <Form form={form} layout="vertical" onFinish={save}>
          {error && <Alert type="error" message={error} />}
          <Form.Item
            name={isOrg ? "name" : "title"}
            label={`${label} 이름`}
            rules={[{ required: true, message: "이름을 입력해 주세요." }]}
          >
            <Input />
          </Form.Item>
          {!isOrg && (
            <Form.Item
              name="organization_id"
              label="운영 조직"
              rules={[{ required: true, message: "조직을 선택해 주세요." }]}
            >
              <Select
                virtual={false}
                showSearch
                optionFilterProp="label"
                loading={organizations.query.isLoading}
                options={organizations.result.data.map((o) => ({
                  value: o.id,
                  label: o.name,
                }))}
              />
            </Form.Item>
          )}
          {!isOrg && (
            <>
              <Form.Item
                name="collection_enabled"
                label="매일 활동 수집"
                valuePropName="checked"
              >
                <Switch />
              </Form.Item>
              <Form.Item
                name="collection_hosts"
                label="공식 출처 호스트"
                extra="주소 전체 대신 정확한 호스트를 입력하세요. 예: www.sopt.org. 새 호스트는 관리자가 공식 출처인지 확인해 추가합니다."
                rules={[
                  {
                    validator: async (_, values: string[] | undefined) => {
                      if (
                        (values?.length ?? 0) > 20 ||
                        values?.some(
                          (v) =>
                            !/^([a-z0-9]([a-z0-9-]*[a-z0-9])?\.)+[a-z]([a-z0-9-]*[a-z0-9])?$/.test(
                              v,
                            ),
                        )
                      )
                        throw new Error(
                          "소문자 호스트를 최대 20개 입력하세요.",
                        );
                    },
                  },
                ]}
              >
                <Select mode="tags" tokenSeparators={[",", " "]} open={false} />
              </Form.Item>
            </>
          )}
          <Form.Item name="description" label="소개">
            <Input.TextArea rows={4} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
export function AuditLog() {
  const [page, setPage] = useState(1);
  const { query, result } = useList({
    resource: "catalog_audit_log",
    pagination: { currentPage: page, pageSize: 20 },
    sorters: [{ field: "id", order: "desc" }],
  });
  const labels: Record<string, string> = {
    catalog_organizations: "조직",
    catalog_programs: "프로그램",
    catalog_activities: "활동",
  };
  return (
    <>
      <header className="page-heading">
        <div>
          <span className="eyebrow">HISTORY</span>
          <h1>변경 기록</h1>
          <p>
            저장과 공식 확인 기록을 시간순으로 확인하세요. 기록은 수정하거나
            지울 수 없습니다.
          </p>
        </div>
      </header>
      {query.isError ? (
        <Alert
          type="error"
          message="변경 기록을 불러오지 못했어요."
          action={<Button onClick={() => query.refetch()}>다시 시도</Button>}
        />
      ) : (
        <Table
          rowKey="id"
          loading={query.isLoading}
          dataSource={result.data}
          scroll={{ x: 800 }}
          pagination={{
            current: page,
            pageSize: 20,
            total: result.total,
            onChange: setPage,
            showSizeChanger: false,
          }}
          columns={[
            { title: "시각", dataIndex: "occurred_at", render: dateText },
            {
              title: "대상",
              dataIndex: "resource",
              render: (value) => labels[value] ?? value,
            },
            {
              title: "이름",
              render: (_, row) =>
                row.after_data?.title ??
                row.after_data?.name ??
                row.before_data?.title ??
                row.record_id,
            },
            {
              title: "변경",
              dataIndex: "operation",
              render: (value: string) => (
                <Tag>
                  {{
                    INSERT: "생성",
                    UPDATE: "수정",
                    DELETE: "삭제",
                    reverify: "자동 재확인",
                  }[value] ?? value}
                </Tag>
              ),
            },
            {
              title: "담당자 ID",
              dataIndex: "actor_id",
              render: (value) => (
                <span className="muted">{value ?? "초기 데이터 가져오기"}</span>
              ),
            },
          ]}
          expandable={{
            expandedRowRender: (row) => (
              <Space direction="vertical" style={{ width: "100%" }}>
                {["before_data", "after_data"].map((key) => (
                  <div key={key}>
                    <strong>
                      {key === "before_data" ? "변경 전" : "변경 후"}
                    </strong>
                    <pre className="audit-json">
                      {JSON.stringify(row[key], null, 2)}
                    </pre>
                  </div>
                ))}
              </Space>
            ),
          }}
        />
      )}
    </>
  );
}
