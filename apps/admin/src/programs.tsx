import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, App, Button, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Spin, Switch, Table, Tag } from "antd";
import { PlusOutlined } from "@ant-design/icons";
import { api, useSingleFlight } from "./api";
import { CursorTable, SearchSelect, useCursorList } from "./list";

/** 조심 목록 entry (contract "조심 목록"): a name the collector treats as an alias of a canonical activity or never collects. */
type WatchKind = "alias" | "exclude";
type WatchEntry = {
  id: number;
  programId: number;
  name: string;
  normalizedName: string;
  kind: WatchKind;
  canonicalActivityId: number | null;
  canonicalActivityName: string | null;
  reason: string | null;
  createdAt: string;
};
const watchKindLabels: Record<WatchKind, string> = { alias: "별칭", exclude: "제외" };

type CollectionInput = { officialUrl?: string; allowedHosts: string[]; intervalDays: number; enabled: boolean };
type CollectionSettings = {
  configured: boolean;
  officialUrl: string | null;
  allowedHosts: string[];
  intervalDays: number;
  enabled: boolean;
  officialOnly: boolean;
  lastSucceededOn: string | null;
};
type Program = { id: number; name: string; nameEn: string | null; organization: { id: number; name: string }; collection: CollectionSettings };

const hostPattern = /^([a-z0-9]([a-z0-9-]*[a-z0-9])?\.)+[a-z]([a-z0-9-]*[a-z0-9])?$/;
const defaults: CollectionInput = { allowedHosts: [], intervalDays: 1, enabled: true };
/** Form values → API body: trims, lowercases hosts and drops an empty URL. */
const collectionBody = (values: CollectionInput): CollectionInput => ({
  ...values,
  officialUrl: values.officialUrl?.trim() || undefined,
  allowedHosts: values.allowedHosts.map((host) => host.trim().toLowerCase()),
});

export function Programs() {
  const [search, setSearch] = useState(""),
    [organizationId, setOrganizationId] = useState<number>(),
    [creating, setCreating] = useState(false),
    [editing, setEditing] = useState<Program>();
  const cursor = useCursorList<Program>("programs", { q: search, organizationId });
  return (
    <>
      <header className="page-heading">
        <div>
          <span className="eyebrow">PROGRAMS</span>
          <h1>프로그램 관리</h1>
          <p>같은 조직의 반복되는 활동을 프로그램으로 묶고, 매일 밤 수집할 공식 출처를 정하세요.</p>
        </div>
        <Button type="primary" icon={<PlusOutlined aria-hidden="true" />} onClick={() => setCreating(true)}>
          프로그램 만들기
        </Button>
      </header>
      <div className="list-toolbar">
        <Space wrap>
          <Input.Search
            aria-label="프로그램 검색"
            placeholder="프로그램 이름 검색"
            allowClear
            onSearch={setSearch}
            style={{ width: 280 }}
          />
          <SearchSelect
            path="organizations"
            aria-label="조직 필터"
            placeholder="전체 조직"
            value={organizationId}
            onChange={setOrganizationId}
            style={{ width: 220 }}
          />
        </Space>
      </div>
      <CursorTable
        cursor={cursor}
        scroll={{ x: 900 }}
        locale={{ emptyText: "조건에 맞는 프로그램이 없어요." }}
        expandable={{ expandedRowRender: (program) => <Watchlist program={program} /> }}
        columns={[
          {
            title: "프로그램",
            render: (_, program) => (
              <>
                {program.name}
                {program.nameEn && <span className="muted name-en">{program.nameEn}</span>}
              </>
            ),
          },
          { title: "운영 조직", render: (_, program) => program.organization.name },
          {
            title: "수집",
            render: (_, { collection }) =>
              collection.enabled ? <Tag color="green">{collection.intervalDays}일마다</Tag> : <Tag>꺼짐</Tag>,
          },
          {
            title: "공식 출처 호스트",
            render: (_, { collection }) =>
              collection.allowedHosts.length ? (
                collection.allowedHosts.map((host) => <Tag key={host}>{host}</Tag>)
              ) : (
                <Tag color="orange">없음 · 웹 전체 검색</Tag>
              ),
          },
          { title: "마지막 수집 성공", render: (_, { collection }) => collection.lastSucceededOn ?? "없음" },
          { title: "", render: (_, program) => <Button onClick={() => setEditing(program)}>수집 설정</Button> },
        ]}
      />
      {creating && <CreateProgram onClose={() => setCreating(false)} />}
      {editing && <CollectionEditor program={editing} onClose={() => setEditing(undefined)} />}
    </>
  );
}

function CreateProgram({ onClose }: { onClose: () => void }) {
  const [form] = Form.useForm();
  const withCollection = Form.useWatch("withCollection", form);
  const { message } = App.useApp();
  const client = useQueryClient();
  const create = useMutation({
    mutationFn: (body: { organizationId: number; name: string; nameEn?: string; collection?: CollectionInput }) =>
      api("programs", "POST", body),
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ["programs"] });
      message.success("프로그램을 만들었어요.");
      onClose();
    },
  });
  const createOnce = useSingleFlight();
  return (
    <Modal
      open
      title="프로그램 만들기"
      onCancel={onClose}
      onOk={() => form.submit()}
      confirmLoading={create.isPending}
      okText="만들기"
      cancelText="취소"
      width={620}
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={{ withCollection: true, collection: defaults }}
        onFinish={({ organizationId, name, nameEn, withCollection, collection }) =>
          createOnce(() =>
            create.mutateAsync({
              organizationId,
              name: name.trim(),
              nameEn: nameEn?.trim() || undefined,
              collection: withCollection ? collectionBody(collection) : undefined,
            }),
          )
        }
      >
        {create.error && <Alert className="form-error" type="error" showIcon message={create.error.message} />}
        <Form.Item name="organizationId" label="운영 조직" rules={[{ required: true, message: "조직을 골라 주세요." }]}>
          <SearchSelect path="organizations" placeholder="조직 이름으로 검색" />
        </Form.Item>
        <Form.Item
          name="name"
          label="프로그램 이름"
          rules={[{ required: true, whitespace: true, message: "이름을 입력해 주세요." }]}
        >
          <Input />
        </Form.Item>
        <Form.Item name="nameEn" label="영문 이름(선택)">
          <Input />
        </Form.Item>
        <Form.Item name="withCollection" label="수집 설정 함께 입력" valuePropName="checked">
          <Switch />
        </Form.Item>
        {withCollection ? (
          <CollectionFields name="collection" />
        ) : (
          <p className="field-note">수집 설정은 만든 뒤 목록의 ‘수집 설정’에서 입력할 수 있어요.</p>
        )}
      </Form>
    </Modal>
  );
}

function CollectionEditor({ program, onClose }: { program: Program; onClose: () => void }) {
  const [form] = Form.useForm();
  const { message } = App.useApp();
  const client = useQueryClient();
  const path = `programs/${program.id}/collection-settings`;
  const current = useQuery({ queryKey: ["programs", program.id, "collection"], queryFn: () => api<CollectionSettings>(path) });
  const save = useMutation({
    mutationFn: (body: CollectionInput) => api<CollectionSettings>(path, "PUT", body),
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ["programs"] });
      message.success("수집 설정을 저장했어요.");
      onClose();
    },
  });
  const saveOnce = useSingleFlight();
  const settings = current.data;
  return (
    <Modal
      open
      title={`수집 설정 · ${program.name}`}
      onCancel={onClose}
      onOk={() => form.submit()}
      okButtonProps={{ disabled: !settings }}
      confirmLoading={save.isPending}
      okText="저장"
      cancelText="취소"
      width={620}
    >
      {current.isError ? (
        <Alert type="error" showIcon message="수집 설정을 불러오지 못했어요." description={current.error.message} />
      ) : !settings ? (
        <Spin />
      ) : (
        <Form
          form={form}
          layout="vertical"
          initialValues={{ collection: { ...settings, officialUrl: settings.officialUrl ?? undefined } }}
          onFinish={({ collection }) => saveOnce(() => save.mutateAsync(collectionBody(collection)))}
        >
          {save.error && <Alert className="form-error" type="error" showIcon message={save.error.message} />}
          <p className="field-note">
            마지막 수집 성공: <strong>{settings.lastSucceededOn ?? "없음"}</strong>
            {settings.lastSucceededOn && ` · 이 날짜에서 ${settings.intervalDays}일이 지나면 다시 수집해요.`}
            {!settings.configured && " · 아직 저장된 설정이 없어 기본값을 보여 줘요."}
          </p>
          <CollectionFields name="collection" />
        </Form>
      )}
    </Modal>
  );
}

/** 조심 목록 of one program: what the collector must skip (제외) or fold into an existing activity (별칭), with manual add/remove. */
function Watchlist({ program }: { program: Program }) {
  const [form] = Form.useForm();
  const kind: WatchKind = Form.useWatch("kind", form) ?? "exclude";
  const { message } = App.useApp();
  const client = useQueryClient();
  const path = `programs/${program.id}/watchlist`;
  const key = ["programs", program.id, "watchlist"];
  const entries = useQuery({ queryKey: key, queryFn: () => api<{ items: WatchEntry[] }>(path) });
  const add = useMutation({
    mutationFn: (body: { name: string; kind: WatchKind; canonicalActivityId?: number; reason?: string }) => api<WatchEntry>(path, "POST", body),
    onSuccess: () => {
      form.resetFields(["name", "canonicalActivityId", "reason"]);
      message.success("조심 목록에 넣었어요. 다음 수집부터 적용돼요.");
      client.invalidateQueries({ queryKey: key });
    },
  });
  const addOnce = useSingleFlight();
  const remove = useMutation({
    mutationFn: (entryId: number) => api<void>(`watchlist/${entryId}`, "DELETE"),
    onSuccess: () => {
      message.success("조심 목록에서 뺐어요.");
      client.invalidateQueries({ queryKey: key });
    },
    onError: (error) => message.error(error.message),
  });
  const removeOnce = useSingleFlight();
  return (
    <div className="watchlist">
      <p className="field-note">
        <strong>조심 목록</strong> · 수집기가 이 프로그램에서 피할 이름이에요. 활동 화면의 ‘중복으로 지정’이 여기에 쌓이고, 직접 추가할 수도 있어요.
        이름은 공백·대소문자를 무시해 대조해요.
      </p>
      {entries.isError ? (
        <Alert type="error" showIcon message="조심 목록을 불러오지 못했어요." description={entries.error.message} />
      ) : (
        <Table<WatchEntry>
          size="small"
          rowKey="id"
          pagination={false}
          loading={entries.isLoading}
          dataSource={entries.data?.items ?? []}
          locale={{ emptyText: "아직 지정한 이름이 없어요." }}
          columns={[
            { title: "이름", dataIndex: "name" },
            { title: "종류", render: (_, entry) => <Tag color={entry.kind === "alias" ? "blue" : "orange"}>{watchKindLabels[entry.kind]}</Tag> },
            { title: "정본 활동", render: (_, entry) => entry.canonicalActivityName ?? <span className="muted">—</span> },
            { title: "사유", render: (_, entry) => entry.reason ?? <span className="muted">—</span> },
            { title: "지정일", render: (_, entry) => new Date(entry.createdAt).toLocaleDateString("ko-KR") },
            {
              title: "",
              className: "actions",
              render: (_, entry) => (
                <Popconfirm
                  title={`‘${entry.name}’을(를) 조심 목록에서 뺄까요?`}
                  description="다음 수집부터 이 이름은 다시 활동으로 만들어질 수 있어요."
                  okText="빼기"
                  cancelText="취소"
                  okButtonProps={{ danger: true }}
                  disabled={remove.isPending}
                  onConfirm={() => removeOnce(() => remove.mutateAsync(entry.id))}
                >
                  <Button size="small" danger loading={remove.isPending && remove.variables === entry.id}>
                    삭제
                  </Button>
                </Popconfirm>
              ),
            },
          ]}
        />
      )}
      <Form
        form={form}
        layout="inline"
        className="watchlist-add"
        initialValues={{ kind: "exclude" }}
        onFinish={(values: { name: string; kind: WatchKind; canonicalActivityId?: number; reason?: string }) =>
          addOnce(() =>
            add.mutateAsync({
              name: values.name.trim(),
              kind: values.kind,
              canonicalActivityId: values.kind === "alias" ? values.canonicalActivityId : undefined,
              reason: values.reason?.trim() || undefined,
            }),
          )
        }
      >
        <Form.Item name="name" rules={[{ required: true, whitespace: true, message: "이름을 입력해 주세요." }]}>
          <Input placeholder="피할 활동 이름" style={{ width: 220 }} />
        </Form.Item>
        <Form.Item name="kind">
          <Select
            virtual={false}
            aria-label="종류"
            options={[
              { value: "exclude", label: "제외 — 이 프로그램 활동이 아님" },
              { value: "alias", label: "별칭 — 기존 활동의 다른 표기" },
            ]}
            style={{ width: 230 }}
          />
        </Form.Item>
        {kind === "alias" && (
          <Form.Item name="canonicalActivityId" rules={[{ required: true, message: "정본 활동을 골라 주세요." }]}>
            <SearchSelect path="activities" params={{ programId: program.id }} placeholder="정본 활동 검색" style={{ width: 240 }} />
          </Form.Item>
        )}
        <Form.Item name="reason">
          <Input placeholder="사유(선택)" maxLength={200} style={{ width: 200 }} />
        </Form.Item>
        <Form.Item>
          <Button type="primary" htmlType="submit" loading={add.isPending}>
            추가
          </Button>
        </Form.Item>
      </Form>
      {add.error && <Alert className="form-error" type="error" showIcon message={add.error.message} />}
    </div>
  );
}

function CollectionFields({ name }: { name: string }) {
  const hosts: string[] | undefined = Form.useWatch([name, "allowedHosts"]);
  return (
    <>
      <Form.Item name={[name, "enabled"]} label="매일 밤 활동 수집" valuePropName="checked">
        <Switch />
      </Form.Item>
      <Form.Item
        name={[name, "officialUrl"]}
        label="공식 안내 URL"
        rules={[
          {
            validator: async (_, value?: string) => {
              if (!value?.trim()) return;
              let url: URL;
              try {
                url = new URL(value.trim());
              } catch {
                throw new Error("올바른 주소를 입력해 주세요.");
              }
              if (url.protocol !== "https:") throw new Error("https 주소만 쓸 수 있어요.");
            },
          },
        ]}
      >
        <Input placeholder="https://letswift.kr" inputMode="url" />
      </Form.Item>
      <Form.Item
        name={[name, "allowedHosts"]}
        label="공식 출처 호스트"
        extra="URL 전체가 아니라 호스트 이름만 입력하고 Enter를 누르세요. 예: letswift.kr, www.sopt.org"
        rules={[
          {
            validator: async (_, values: string[] = []) => {
              const invalid = values.find((host) => !hostPattern.test(host.trim().toLowerCase()));
              if (invalid) throw new Error(`‘${invalid}’는 호스트 이름이 아니에요. 스킴(https://)·경로·포트 없이 입력해 주세요.`);
            },
          },
        ]}
      >
        <Select mode="tags" open={false} tokenSeparators={[",", " "]} aria-label="공식 출처 호스트" />
      </Form.Item>
      {!hosts?.length && (
        <Alert
          className="form-error"
          type="warning"
          showIcon
          message="공식 출처 호스트가 비어 있어요."
          description="호스트가 없으면 매일 밤 ‘프로그램 이름 + 주최 기관’으로 웹 전체를 검색해요. 공식 도메인만 볼 때보다 토큰을 약 5배 쓰고, 공식 출처가 아닌 페이지가 섞입니다. 이렇게 모은 활동은 ‘웹 검색 출처’로 표시되며 게시 전에 원문을 확인해야 해요."
        />
      )}
      <Form.Item
        name={[name, "intervalDays"]}
        label="수집 간격(일)"
        rules={[
          { required: true, message: "간격을 입력해 주세요." },
          { type: "integer", min: 1, message: "1 이상의 정수를 입력해 주세요." },
        ]}
      >
        <InputNumber min={1} precision={0} />
      </Form.Item>
    </>
  );
}
