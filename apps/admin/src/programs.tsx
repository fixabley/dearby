import { useEffect, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, App, Button, Checkbox, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Spin, Switch, Table, Tag, Tooltip, Upload } from "antd";
import type { InputRef } from "antd";
import { PlusOutlined } from "@ant-design/icons";
import { api, apiBlob, useSingleFlight } from "./api";
import { CursorTable, SearchSelect, useCursorList } from "./list";
import { type Category, categoryLabel, categoryOptions } from "./category";
import { resolveTagIds, TagChips, type TagItem, TagSelect, type TagValue } from "./tag-select";

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

type CollectionInput = { officialUrl?: string; allowedHosts: string[]; intervalDays: number; enabled: boolean; renderPages: boolean };
type CollectionSettings = {
  configured: boolean;
  officialUrl: string | null;
  allowedHosts: string[];
  intervalDays: number;
  enabled: boolean;
  /** 헤드리스 브라우저로 페이지를 실행해 본문을 읽는다(contract "브라우저 렌더링"); absent on older responses. */
  renderPages?: boolean;
  officialOnly: boolean;
  lastSucceededOn: string | null;
  /** 기수 번호 read from the program's latest activity, null when none (contract "주소 토큰"); absent on older responses. */
  previousRound?: number | null;
  /** The saved URL expanded by the server as the collector would see it today; absent on older responses. */
  previewUrls?: string[];
  /** Pages the last successful collection actually read; absent on older responses. */
  lastFetchedUrls?: string[];
};
type Program = {
  id: number;
  name: string;
  nameEn: string | null;
  organization: { id: number; name: string };
  /** 프로그램 기본 종류(contract admin-v1.md "프로그램 기본 종류"); absent on older responses. */
  category?: Category | null;
  /** 얻을 수 있는 것 태그 (contract native-v1.md "태그"); absent on older responses. */
  tags?: TagItem[];
  collection: CollectionSettings;
  appliedActivityCount?: number | null;
  /** 프로그램 로고 (contract admin-v1.md "프로그램 로고"): public path with `?v=`, null when none; absent on older responses. */
  logoUrl?: string | null;
};

const logoTypes = ["image/png", "image/jpeg", "image/webp"];
const logoMaxBytes = 1024 * 1024;
const logoRule = "로고는 PNG·JPEG·WebP 1MB 이하여야 합니다";

/**
 * 프로그램 로고 미리보기: the admin GET is bearer-gated, so the image comes down as a blob through `apiBlob` and is shown
 * from an object URL. Keyed by `logoUrl` (carries `?v=`), so it refetches exactly when the logo changes and not on every
 * `["programs"]` invalidation. Loading, failure and null show the first-letter avatar like the discovery card.
 */
function ProgramLogo({ program, logoUrl, size = 44 }: { program: Pick<Program, "id" | "name">; logoUrl: string | null; size?: number }) {
  const blob = useQuery({
    queryKey: ["programLogo", program.id, logoUrl],
    queryFn: () => apiBlob(`programs/${program.id}/logo`),
    enabled: !!logoUrl,
    staleTime: Infinity,
    retry: false,
  });
  const [src, setSrc] = useState<string>();
  useEffect(() => {
    if (!blob.data) {
      setSrc(undefined);
      return;
    }
    const url = URL.createObjectURL(blob.data);
    setSrc(url);
    return () => URL.revokeObjectURL(url);
  }, [blob.data]);
  const style = { width: size, height: size, borderRadius: Math.round(size * 14 / 44), flex: "none" } as const;
  if (src && logoUrl) return <img src={src} alt={`${program.name} 로고`} style={{ ...style, objectFit: "cover" }} />;
  return (
    <div
      aria-hidden
      style={{ ...style, display: "grid", placeItems: "center", background: "#d8f3ee", color: "#0f766e", fontWeight: 600, fontSize: size * 0.4 }}
    >
      {program.name.trim().charAt(0)}
    </div>
  );
}

const hostPattern = /^([a-z0-9]([a-z0-9-]*[a-z0-9])?\.)+[a-z]([a-z0-9-]*[a-z0-9])?$/;
/** URL tokens (contract "주소 토큰" 2026-10-19): the collector fills them in when a program's page moves every edition. */
const urlTokens = ["{year}", "{yy}", "{round}"] as const;
const tokenHelp =
  "회차마다 주소가 바뀌면 토큰을 넣어요. {year} 네 자리 연도, {yy} 두 자리 연도, {round} 기수 번호(이전 활동 이름의 ‘N기’에서 읽음). 올해·내년, 다음 기수·현재 기수로 펼쳐 차례로 봅니다.";
const seoulYear = () => Number(new Intl.DateTimeFormat("en-US", { timeZone: "Asia/Seoul", year: "numeric" }).format(new Date()));
/** Same rule as the server: {year}/{yy} → this year and next, {round} → previousRound+1 and previousRound; combined, distinct, at most 4. */
function expandUrl(url: string, previousRound: number | null): string[] {
  const year = seoulYear();
  const two = (value: number) => String(value % 100).padStart(2, "0");
  let candidates = [url];
  const expand = (token: string, values: string[]) => {
    candidates = candidates.flatMap((candidate) => (candidate.includes(token) ? values.map((value) => candidate.split(token).join(value)) : [candidate]));
  };
  expand("{year}", [String(year), String(year + 1)]);
  expand("{yy}", [two(year), two(year + 1)]);
  if (previousRound === null) candidates = candidates.filter((candidate) => !candidate.includes("{round}"));
  else expand("{round}", [String(previousRound + 1), String(previousRound)]);
  return [...new Set(candidates)].slice(0, 4);
}
/** Tokens in [url] that the collector does not know. */
const unknownTokens = (url: string) => (url.match(/\{[^{}]*\}/g) ?? []).filter((token) => !(urlTokens as readonly string[]).includes(token));
const defaults: CollectionInput = { allowedHosts: [], intervalDays: 1, enabled: true, renderPages: false };
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
    [editing, setEditing] = useState<Program>(),
    [renaming, setRenaming] = useState<Program>();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  // 프로그램 삭제(2026-10-19 사용자 요청 "어드민에 프로그램 삭제 플로우가 없어"): 활동이 있으면 서버가 400으로 막는다
  const remove = useMutation({
    mutationFn: (id: number) => api<void>(`programs/${id}`, "DELETE"),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["programs"] });
      message.success("프로그램을 지웠어요.");
    },
    onError: (error: Error) => message.error(error.message),
  });
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
              <Space align="center">
                <ProgramLogo program={program} logoUrl={program.logoUrl ?? null} size={32} />
                <span>
                  {program.name}
                  {program.nameEn && <span className="muted name-en">{program.nameEn}</span>}
                </span>
              </Space>
            ),
          },
          { title: "운영 조직", render: (_, program) => program.organization.name },
          { title: "종류", render: (_, program) => (program.category ? <Tag>{categoryLabel(program.category)}</Tag> : <span className="muted">미분류</span>) },
          { title: "태그", render: (_, program) => (program.tags?.length ? <TagChips tags={program.tags} /> : <span className="muted">없음</span>) },
          {
            title: "수집",
            render: (_, { collection }) => {
              const fetched = collection.lastFetchedUrls ?? [];
              return (
                <>
                  {collection.enabled ? <Tag color="green">{collection.intervalDays}일마다</Tag> : <Tag>꺼짐</Tag>}
                  {collection.renderPages && (
                    <Tooltip title="헤드리스 브라우저로 페이지를 실행한 뒤 본문을 읽어요(자바스크립트로 그리는 사이트).">
                      <Tag color="geekblue">렌더링</Tag>
                    </Tooltip>
                  )}
                  <div className="muted" style={{ fontSize: 12, marginTop: 4, wordBreak: "break-all" }}>
                    {fetched.length ? (
                      <>
                        <a href={fetched[0]} target="_blank" rel="noreferrer">
                          {fetched[0]}
                        </a>
                        {fetched.length > 1 && ` 외 ${fetched.length - 1}개`}
                      </>
                    ) : (
                      "아직 수집한 페이지가 없어요"
                    )}
                  </div>
                </>
              );
            },
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
          {
            title: "",
            className: "actions",
            render: (_, program) => (
              <Space>
                <Button onClick={() => setRenaming(program)}>편집</Button>
                <Button onClick={() => setEditing(program)}>수집 설정</Button>
                <Popconfirm
                  title="이 프로그램을 지울까요?"
                  description="수집 설정과 수집 기록도 함께 지워져요. 활동이 있으면 지울 수 없어요."
                  okText="지우기"
                  okButtonProps={{ danger: true }}
                  cancelText="취소"
                  onConfirm={() => remove.mutate(program.id)}
                >
                  <Button danger loading={remove.isPending && remove.variables === program.id}>
                    삭제
                  </Button>
                </Popconfirm>
              </Space>
            ),
          },
        ]}
      />
      {creating && <CreateProgram onClose={() => setCreating(false)} />}
      {editing && <CollectionEditor program={editing} onClose={() => setEditing(undefined)} />}
      {renaming && <EditProgram program={renaming} onClose={() => setRenaming(undefined)} />}
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
          <CollectionFields name="collection" previousRound={null} />
        ) : (
          <p className="field-note">수집 설정은 만든 뒤 목록의 ‘수집 설정’에서 입력할 수 있어요.</p>
        )}
      </Form>
    </Modal>
  );
}

/** 프로그램 편집: name, nameEn and the owning organization via `PATCH programs/:id` (2026-10-19 사용자 요청). */
function EditProgram({ program, onClose }: { program: Program; onClose: () => void }) {
  const [form] = Form.useForm();
  const { message } = App.useApp();
  const client = useQueryClient();
  const save = useMutation({
    mutationFn: (body: {
      name: string;
      nameEn: string;
      organizationId: number;
      category: Category | null;
      applyCategoryToActivities: boolean;
      tagIds: number[];
      applyTagsToActivities: boolean;
    }) => api<Program>(`programs/${program.id}`, "PATCH", body),
    onSuccess: (saved) => {
      client.invalidateQueries({ queryKey: ["programs"] });
      client.invalidateQueries({ queryKey: ["activities"] });
      const applied = saved.appliedActivityCount;
      message.success(applied == null ? "프로그램을 고쳤어요." : `프로그램을 고쳤어요. 활동 ${applied}개의 종류를 바꿨어요.`);
      onClose();
    },
  });
  const chosenCategory: Category | null = Form.useWatch("category", form) ?? null;
  const chosenTags: TagValue[] = Form.useWatch("tags", form) ?? [];
  const saveOnce = useSingleFlight();
  // `program` is the row snapshot taken when the modal opened; the logo changes right away, independent of 저장, so it lives here.
  const [logoUrl, setLogoUrl] = useState(program.logoUrl ?? null);
  const uploadLogo = useMutation({
    mutationFn: (file: File) => {
      const body = new FormData();
      body.append("file", file);
      return api<{ logoUrl: string }>(`programs/${program.id}/logo`, "PUT", body);
    },
    onSuccess: ({ logoUrl }) => {
      setLogoUrl(logoUrl);
      client.invalidateQueries({ queryKey: ["programs"] });
      message.success("로고를 올렸어요.");
    },
    onError: (error) => message.error(error.message),
  });
  const removeLogo = useMutation({
    mutationFn: () => api<void>(`programs/${program.id}/logo`, "DELETE"),
    onSuccess: () => {
      setLogoUrl(null);
      client.invalidateQueries({ queryKey: ["programs"] });
      message.success("로고를 지웠어요.");
    },
    onError: (error) => message.error(error.message),
  });
  return (
    <Modal
      open
      title={`프로그램 편집 · ${program.name}`}
      onCancel={onClose}
      onOk={() => form.submit()}
      confirmLoading={save.isPending}
      okText="저장"
      cancelText="취소"
      width={520}
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={{
          organizationId: program.organization.id,
          name: program.name,
          nameEn: program.nameEn ?? "",
          category: program.category ?? null,
          applyCategoryToActivities: true,
          tags: (program.tags ?? []).map((tag) => tag.id),
          applyTagsToActivities: true,
        }}
        // An empty English name clears it, like the organization editor. 기본 종류는 null이면 해제; 일괄 적용은 종류가 있을 때만 뜻이 있다.
        onFinish={({
          organizationId,
          name,
          nameEn,
          category,
          applyCategoryToActivities,
          tags,
          applyTagsToActivities,
        }: {
          organizationId: number;
          name: string;
          nameEn: string;
          category: Category | null;
          applyCategoryToActivities: boolean;
          tags: TagValue[];
          applyTagsToActivities: boolean;
        }) =>
          saveOnce(async () => {
            // Typed tag names become dictionary entries first; 일괄 적용 only means something with at least one tag.
            const tagIds = await resolveTagIds(tags, client);
            return save.mutateAsync({
              organizationId,
              name: name.trim(),
              nameEn: nameEn.trim(),
              category: category ?? null,
              applyCategoryToActivities: category != null && applyCategoryToActivities,
              tagIds,
              applyTagsToActivities: tagIds.length > 0 && applyTagsToActivities,
            });
          })
        }
      >
        {save.error && <Alert className="form-error" type="error" showIcon message={save.error.message} />}
        <Form.Item name="organizationId" label="운영 조직" rules={[{ required: true, message: "조직을 골라 주세요." }]}>
          <SearchSelect path="organizations" placeholder="조직 이름으로 검색" label={program.organization.name} />
        </Form.Item>
        <Form.Item name="name" label="프로그램 이름" rules={[{ required: true, whitespace: true, message: "이름을 입력해 주세요." }]}>
          <Input />
        </Form.Item>
        <Form.Item name="nameEn" label="영문 이름(선택)">
          <Input />
        </Form.Item>
        <Form.Item
          name="category"
          label="기본 종류"
          extra="수집이 종류를 못 읽은 활동과 새로 만드는 활동에 이 종류를 써요. 운영자가 직접 지정한 활동은 바꾸지 않아요."
        >
          <Select allowClear placeholder="미분류" options={categoryOptions.filter((option) => option.value !== null).map(({ value, label }) => ({ value: value as Category, label }))} />
        </Form.Item>
        <Form.Item name="applyCategoryToActivities" valuePropName="checked" style={{ marginTop: -8 }}>
          <Checkbox disabled={!chosenCategory}>기존 활동에도 적용(운영자 지정 제외)</Checkbox>
        </Form.Item>
        <Form.Item
          name="tags"
          label="태그"
          extra="프로그램에서 얻을 수 있는 것(창업·협업·멘토링 …). 검색해 고르거나 새 이름을 적으면 저장할 때 사전에 추가돼요. 수집이 태그를 못 읽은 활동은 이 태그를 따라요."
        >
          <TagSelect />
        </Form.Item>
        <Form.Item name="applyTagsToActivities" valuePropName="checked" style={{ marginTop: -8 }}>
          <Checkbox disabled={chosenTags.length === 0}>기존 활동에도 적용(운영자 지정 제외, 합집합)</Checkbox>
        </Form.Item>
        <Form.Item label="로고" extra="PNG·JPEG·WebP 1MB 이하. 발견 카드의 로고 자리에 보여요. 업로드·지우기는 저장 버튼과 상관없이 바로 반영돼요.">
          <Space align="center">
            <ProgramLogo program={program} logoUrl={logoUrl} />
            <Upload
              accept={logoTypes.join(",")}
              showUploadList={false}
              disabled={uploadLogo.isPending}
              beforeUpload={(file) => {
                if (logoTypes.includes(file.type) && file.size <= logoMaxBytes) return true;
                message.error(logoRule);
                return Upload.LIST_IGNORE;
              }}
              customRequest={({ file }) => uploadLogo.mutate(file as File)}
            >
              <Button loading={uploadLogo.isPending}>업로드</Button>
            </Upload>
            <Popconfirm title="로고를 지울까요?" okText="지우기" okButtonProps={{ danger: true }} cancelText="취소" onConfirm={() => removeLogo.mutate()}>
              <Button danger disabled={!logoUrl} loading={removeLogo.isPending}>
                지우기
              </Button>
            </Popconfirm>
          </Space>
        </Form.Item>
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
          {settings.previewUrls?.length ? (
            <p className="field-note">
              저장된 설정으로 지금 수집하면 이 주소들을 봅니다: {settings.previewUrls.map((url) => <code key={url}>{url} </code>)}
            </p>
          ) : null}
          <p className="field-note" style={{ wordBreak: "break-all" }}>
            현시점 수집 페이지:{" "}
            {settings.lastFetchedUrls?.length
              ? settings.lastFetchedUrls.map((url, index) => (
                  <span key={url}>
                    {index > 0 && ", "}
                    <a href={url} target="_blank" rel="noreferrer">
                      {url}
                    </a>
                  </span>
                ))
              : "아직 수집한 페이지가 없어요"}
          </p>
          <CollectionFields name="collection" previousRound={settings.previousRound ?? null} />
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

function CollectionFields({ name, previousRound }: { name: string; previousRound: number | null }) {
  const form = Form.useFormInstance();
  const hosts: string[] | undefined = Form.useWatch([name, "allowedHosts"]);
  const url: string | undefined = Form.useWatch([name, "officialUrl"]);
  const urlInput = useRef<InputRef>(null);
  const trimmed = url?.trim() ?? "";
  const preview = trimmed && !unknownTokens(trimmed).length ? expandUrl(trimmed, previousRound) : [];
  /** Puts [token] at the caret of the URL input (or at the end) and keeps focus there. */
  const insertToken = (token: string) => {
    const element = urlInput.current?.input;
    const current = url ?? "";
    const start = element?.selectionStart ?? current.length;
    const end = element?.selectionEnd ?? current.length;
    form.setFieldValue([name, "officialUrl"], current.slice(0, start) + token + current.slice(end));
    setTimeout(() => {
      element?.focus();
      element?.setSelectionRange(start + token.length, start + token.length);
    });
  };
  return (
    <>
      <Form.Item name={[name, "enabled"]} label="매일 밤 활동 수집" valuePropName="checked">
        <Switch />
      </Form.Item>
      <Form.Item
        name={[name, "renderPages"]}
        valuePropName="checked"
        extra="자바스크립트로 내용을 그리는 사이트에서만 켜요. 페이지당 시간이 늘어요. 서버 HTML에 글자가 거의 없는 사이트(예: woowacon.com)가 대상이에요."
        style={{ marginBottom: 12 }}
      >
        <Checkbox>브라우저 렌더링 필요</Checkbox>
      </Form.Item>
      <Space size={4} wrap style={{ marginBottom: 4 }} aria-label="주소 토큰">
        {urlTokens.map((token) => (
          <Tag key={token} color="blue" style={{ cursor: "pointer", marginInlineEnd: 0 }} onClick={() => insertToken(token)} role="button" tabIndex={0}
            onKeyDown={(event) => { if (event.key === "Enter" || event.key === " ") { event.preventDefault(); insertToken(token); } }}>
            {token}
          </Tag>
        ))}
      </Space>
      <Form.Item
        name={[name, "officialUrl"]}
        label="공식 안내 URL"
        extra={tokenHelp}
        rules={[
          {
            validator: async (_, value?: string) => {
              if (!value?.trim()) return;
              const unknown = unknownTokens(value.trim());
              if (unknown.length) throw new Error(`알 수 없는 토큰이에요: ${unknown.join(", ")}. {year}, {yy}, {round}만 쓸 수 있어요.`);
              // Validate the address with sample values in place of the tokens, as the server does.
              const sample = value.trim().split("{year}").join("2026").split("{yy}").join("26").split("{round}").join("1");
              let parsed: URL;
              try {
                parsed = new URL(sample);
              } catch {
                throw new Error("올바른 주소를 입력해 주세요.");
              }
              if (parsed.protocol !== "https:") throw new Error("https 주소만 쓸 수 있어요.");
            },
          },
        ]}
      >
        <Input ref={urlInput} placeholder="https://toss.im/slash-{yy}" inputMode="url" />
      </Form.Item>
      {trimmed && (
        <p className="field-note" aria-live="polite">
          {preview.length ? (
            <>
              지금 수집하면 이 주소들을 봅니다: {preview.map((candidate) => <code key={candidate}>{candidate} </code>)}
            </>
          ) : null}
          {trimmed.includes("{round}") && previousRound === null && " 기수 번호를 알 수 없어 {round} 주소는 건너뜁니다."}
        </p>
      )}
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
