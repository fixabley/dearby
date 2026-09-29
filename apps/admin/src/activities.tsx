import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import { useCreate, useList, useOne, useUpdate } from "@refinedev/core";
import {
  Alert,
  App,
  Button,
  Card,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from "antd";
import {
  ArrowLeftOutlined,
  PlusOutlined,
  ReloadOutlined,
  CheckCircleOutlined,
  MinusCircleOutlined,
} from "@ant-design/icons";
import {
  type Activity,
  type Program,
  type Schedule,
  dateText,
  discoveryStatus,
  normalizeSchedules,
  publicationLabels,
  recruitmentLabels,
} from "./catalog";
import { supabase } from "./supabase";
const resource = "catalog_activities";
const options = (values: Record<string, string>) =>
  Object.entries(values).map(([value, label]) => ({ value, label }));
const required = [{ required: true, message: "필수 항목입니다." }];
const urlRules = [
  { type: "url" as const, message: "올바른 웹 주소를 입력해 주세요." },
  { pattern: /^https?:\/\//, message: "http 또는 https 주소를 입력해 주세요." },
];
const localZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
function localInput(value: string | null) {
  if (!value) return "";
  const d = new Date(value);
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000)
    .toISOString()
    .slice(0, 16);
}
function instant(value: string | undefined | null) {
  return value ? new Date(value).toISOString() : null;
}
export function ActivityList() {
  const [page, setPage] = useState(1),
    [search, setSearch] = useState(""),
    [status, setStatus] = useState<string>();
  const { query, result } = useList<Activity>({
    resource,
    pagination: { currentPage: page, pageSize: 15 },
    sorters: [{ field: "updated_at", order: "desc" }],
    filters: [
      ...(search
        ? [{ field: "title", operator: "contains" as const, value: search }]
        : []),
      ...(status
        ? [
            {
              field: "publication_status",
              operator: "eq" as const,
              value: status,
            },
          ]
        : []),
    ],
    queryOptions: { refetchInterval: 30000 },
  });
  return (
    <>
      <header className="page-heading">
        <div>
          <span className="eyebrow">DISCOVERY</span>
          <h1>활동 관리</h1>
          <p>공식 정보를 확인하고, 탐색에 보여줄 활동을 관리하세요.</p>
        </div>
        <Link to="/activities/new">
          <Button
            type="primary"
            size="large"
            icon={<PlusOutlined aria-hidden="true" />}
          >
            활동 만들기
          </Button>
        </Link>
      </header>
      <div className="editorial-note">
        <CheckCircleOutlined aria-hidden="true" />
        <div>
          <strong>게시와 모집 확인은 별개예요.</strong>
          <span>
            게시한 활동 중 공식 모집 정보가 확인된 활동만 탐색에 보여요. 확인은
            최대 24시간 유효합니다.
          </span>
        </div>
      </div>
      <div className="list-toolbar">
        <Space wrap>
          <Input.Search
            aria-label="활동 제목 검색"
            placeholder="활동 제목 검색"
            allowClear
            onSearch={(value) => {
              setSearch(value);
              setPage(1);
            }}
            style={{ width: 280 }}
          />
          <Select
            virtual={false}
            aria-label="게시 상태 필터"
            placeholder="전체 게시 상태"
            allowClear
            options={options(publicationLabels)}
            value={status}
            onChange={(value) => {
              setStatus(value);
              setPage(1);
            }}
            style={{ width: 170 }}
          />
        </Space>
        <Button
          icon={<ReloadOutlined aria-hidden="true" />}
          onClick={() => query.refetch()}
        >
          새로고침
        </Button>
      </div>
      {query.isError ? (
        <Alert
          type="error"
          showIcon
          message="활동을 불러오지 못했어요."
          description="연결 또는 관리자 권한을 확인하고 다시 시도해 주세요."
          action={<Button onClick={() => query.refetch()}>다시 시도</Button>}
        />
      ) : (
        <Table
          rowKey="id"
          dataSource={result.data}
          loading={query.isLoading}
          scroll={{ x: 900 }}
          pagination={{
            current: page,
            pageSize: 15,
            total: result.total,
            onChange: setPage,
            showSizeChanger: false,
            showTotal: (total) => `전체 ${total}개`,
          }}
          locale={{
            emptyText: "등록된 활동이 없습니다. 첫 활동을 만들어 주세요.",
          }}
          columns={[
            {
              title: "활동",
              dataIndex: "title",
              width: "35%",
              render: (_, a) => (
                <Link className="activity-title" to={`/activities/${a.id}`}>
                  <strong>{a.title}</strong>
                  <span>{a.date_label || "행사 일정 미확인"}</span>
                </Link>
              ),
            },
            {
              title: "게시",
              dataIndex: "publication_status",
              render: (value) => (
                <Tag>
                  {publicationLabels[value as keyof typeof publicationLabels]}
                </Tag>
              ),
            },
            {
              title: "탐색 노출",
              render: (_, a) => {
                const s = discoveryStatus(a);
                return <Tag color={s.color}>{s.label}</Tag>;
              },
            },
            {
              title: "공식 확인",
              dataIndex: "source_checked_at",
              render: dateText,
            },
            { title: "수정", dataIndex: "updated_at", render: dateText },
            {
              title: "",
              render: (_, a) => <Link to={`/activities/${a.id}`}>편집</Link>,
            },
          ]}
        />
      )}
    </>
  );
}
export function ActivityEditor() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { message } = App.useApp();
  const [form] = Form.useForm();
  const [loadedVersion, setLoadedVersion] = useState<string>();
  const [dirty, setDirty] = useState(false),
    [error, setError] = useState(""),
    [programSearch, setProgramSearch] = useState("");
  const [verifyOpen, setVerifyOpen] = useState(false),
    [evidence, setEvidence] = useState(""),
    [verifying, setVerifying] = useState(false);
  const { query } = useOne<Activity>({
    resource,
    id: id ?? "",
    queryOptions: { enabled: !!id },
  });
  const activity = query.data?.data;
  const programs = useList<Program>({
    resource: "catalog_programs",
    pagination: { pageSize: 50 },
    filters: programSearch
      ? [{ field: "title", operator: "contains", value: programSearch }]
      : [],
  });
  const selectedProgram = useOne<Program>({
    resource: "catalog_programs",
    id: activity?.program_id ?? "",
    queryOptions: { enabled: !!activity?.program_id },
  });
  const programOptions = [
    ...new Map(
      [
        ...programs.result.data,
        ...(selectedProgram.query.data
          ? [selectedProgram.query.data.data]
          : []),
      ].map((p) => [p.id, p]),
    ).values(),
  ];
  const create = useCreate<Activity, any, Record<string, unknown>>(),
    update = useUpdate<Activity, any, Record<string, unknown>>();
  const saving = create.mutation.isPending || update.mutation.isPending;
  useEffect(() => {
    if (activity && !dirty) {
      setLoadedVersion(activity.updated_at);
      form.setFieldsValue({
        ...activity,
        recruitment_start_at: localInput(activity.recruitment_start_at),
        recruitment_end_at: localInput(activity.recruitment_end_at),
        schedules: activity.schedules.map((s) => ({
          ...s,
          startAt: localInput(s.startAt),
          endAt: localInput(s.endAt),
        })),
      });
      setDirty(false);
    }
  }, [activity, form, dirty]);
  useEffect(() => {
    if (!dirty) return;
    const leave = (event: BeforeUnloadEvent) => {
      event.preventDefault();
    };
    const click = (event: MouseEvent) => {
      const link = (event.target as HTMLElement).closest("a[href]");
      if (
        link &&
        !window.confirm("저장하지 않은 변경이 있습니다. 나가시겠어요?")
      ) {
        event.preventDefault();
        event.stopPropagation();
      }
    };
    window.addEventListener("beforeunload", leave);
    document.addEventListener("click", click, true);
    return () => {
      window.removeEventListener("beforeunload", leave);
      document.removeEventListener("click", click, true);
    };
  }, [dirty]);
  async function save(values: Record<string, any>) {
    setError("");
    try {
      const payload: Record<string, any> = {
        ...values,
        recruitment_start_at: instant(values.recruitment_start_at),
        recruitment_end_at: instant(values.recruitment_end_at),
        schedules: normalizeSchedules(
          (values.schedules ?? []).map((s: Schedule) => ({
            ...s,
            startAt: instant(s.startAt),
            endAt: instant(s.endAt),
          })),
        ),
        roles: values.roles ?? [],
      };
      for (const field of [
        "location",
        "cost",
        "audience",
        "qualification",
        "application_url",
        "image_url",
      ])
        payload[field] = values[field]?.trim() || null;
      if (
        payload.recruitment_start_at &&
        payload.recruitment_end_at &&
        payload.recruitment_start_at >= payload.recruitment_end_at
      )
        throw new Error("모집 종료는 시작보다 늦어야 합니다.");
      if (id) {
        await update.mutateAsync({
          resource,
          id,
          values: payload,
          meta: { expectedUpdatedAt: loadedVersion },
        });
        setDirty(false);
        await query.refetch();
      } else {
        const result = await create.mutateAsync({ resource, values: payload });
        setDirty(false);
        navigate(`/activities/${result.data.id}`, { replace: true });
      }
      message.success("활동을 저장했어요.");
    } catch (e) {
      setError(
        e instanceof Error
          ? e.message
          : "저장하지 못했어요. 입력값과 연결 상태를 확인해 주세요.",
      );
    }
  }
  async function verify() {
    setVerifying(true);
    setError("");
    try {
      const { error } = await supabase!.rpc("verify_catalog_activity", {
        activity_id: id,
        evidence_note: evidence,
      });
      if (error) throw error;
      setVerifyOpen(false);
      await query.refetch();
      message.success("공식 정보 확인을 기록했어요.");
    } catch {
      setError(
        "공식 확인을 저장하지 못했어요. 근거를 10자 이상 입력하고 연결 상태를 확인해 주세요.",
      );
    } finally {
      setVerifying(false);
    }
  }
  if (id && query.isLoading) return <Card loading />;
  if (id && (query.isError || !activity))
    return (
      <Alert
        type="error"
        message="활동을 불러오지 못했어요."
        action={<Button onClick={() => query.refetch()}>다시 시도</Button>}
      />
    );
  return (
    <>
      <Link to="/activities" className="back">
        <ArrowLeftOutlined aria-hidden="true" /> 활동 목록
      </Link>
      <header className="page-heading">
        <div>
          <h1>{id ? "활동 편집" : "새 활동"}</h1>
          <p>
            {id
              ? "내용을 수정하면 공식 확인 상태가 해제됩니다. 저장 후 다시 확인해 주세요."
              : "초안으로 시작해 공식 정보를 확인한 뒤 게시하세요."}
          </p>
        </div>
        {activity && (
          <Tag color={discoveryStatus(activity).color}>
            {discoveryStatus(activity).label}
          </Tag>
        )}
      </header>
      {error && (
        <Alert className="form-error" type="error" showIcon message={error} />
      )}
      <Form
        form={form}
        layout="vertical"
        onFinish={save}
        onValuesChange={() => setDirty(true)}
        initialValues={{
          participation_type: "registration",
          recruitment_status: "unknown",
          publication_status: "draft",
          summary: "",
          date_label: "",
          roles: [],
          schedules: [],
        }}
      >
        <div className="editor-grid">
          <div>
            <Card title="활동 정보">
              <Form.Item
                label="활동 제목"
                name="title"
                rules={[...required, { max: 300 }]}
              >
                <Input />
              </Form.Item>
              <Form.Item
                label="프로그램"
                name="program_id"
                rules={required}
                extra={<Link to="/programs">프로그램 관리</Link>}
              >
                <Select
                  virtual={false}
                  showSearch
                  filterOption={false}
                  onSearch={setProgramSearch}
                  loading={programs.query.isLoading}
                  options={programOptions.map((p) => ({
                    value: p.id,
                    label: p.title,
                  }))}
                  onChange={(value) =>
                    form.setFieldValue(
                      "organization_id",
                      programOptions.find((p) => p.id === value)
                        ?.organization_id,
                    )
                  }
                  placeholder="프로그램 이름으로 검색"
                />
              </Form.Item>
              <Form.Item name="organization_id" hidden rules={required}>
                <Input />
              </Form.Item>
              <Form.Item label="소개" name="summary">
                <Input.TextArea rows={4} />
              </Form.Item>
              <div className="two-fields">
                <Form.Item label="참여 방식" name="participation_type">
                  <Select
                    virtual={false}
                    options={[
                      { value: "registration", label: "참가등록형" },
                      { value: "selection", label: "선발형" },
                    ]}
                  />
                </Form.Item>
                <Form.Item label="모집 상태" name="recruitment_status">
                  <Select
                    virtual={false}
                    options={options(recruitmentLabels)}
                  />
                </Form.Item>
              </div>
              <div className="two-fields">
                <Form.Item label="모집 시작" name="recruitment_start_at">
                  <Input type="datetime-local" />
                </Form.Item>
                <Form.Item label="모집 마감" name="recruitment_end_at">
                  <Input type="datetime-local" />
                </Form.Item>
              </div>
              <p className="field-note">
                입력 시각 기준: {localZone}. 확인되지 않은 시각은 비워 두세요.
              </p>
              <Form.Item label="참가 대상" name="audience">
                <Input />
              </Form.Item>
              <Form.Item label="지원 자격" name="qualification">
                <Input.TextArea rows={2} />
              </Form.Item>
              <Form.Item label="모집 역할" name="roles">
                <Select
                  virtual={false}
                  mode="tags"
                  placeholder="역할 입력 후 Enter"
                />
              </Form.Item>
              <div className="two-fields">
                <Form.Item label="장소" name="location">
                  <Input />
                </Form.Item>
                <Form.Item label="비용" name="cost">
                  <Input />
                </Form.Item>
              </div>
            </Card>
            <Card title="행사 일정">
              <Form.Item label="일정 안내 문구" name="date_label">
                <Input placeholder="예: 10월 24일 · 종료 시각 미정" />
              </Form.Item>
              <p className="field-note">
                날짜만 알려진 경우 안내 문구만 입력하세요. 정확한 시작·종료가
                있어야 앱에서 캘린더 겹침을 비교할 수 있습니다.
              </p>
              <Form.List name="schedules">
                {(fields, { add, remove }) => (
                  <>
                    {fields.map(({ key, name, ...rest }) => (
                      <div className="schedule" key={key}>
                        <div className="schedule-heading">
                          <strong>일정 {name + 1}</strong>
                          <Button
                            aria-label={`일정 ${name + 1} 제거`}
                            type="text"
                            icon={<MinusCircleOutlined aria-hidden="true" />}
                            onClick={() => remove(name)}
                          >
                            제거
                          </Button>
                        </div>
                        <Form.Item {...rest} name={[name, "id"]} hidden>
                          <Input />
                        </Form.Item>
                        <Form.Item
                          {...rest}
                          label="일정 제목"
                          name={[name, "title"]}
                          rules={required}
                        >
                          <Input />
                        </Form.Item>
                        <Form.Item
                          {...rest}
                          label="날짜 안내"
                          name={[name, "dateLabel"]}
                        >
                          <Input />
                        </Form.Item>
                        <div className="two-fields">
                          <Form.Item
                            {...rest}
                            label="시작"
                            name={[name, "startAt"]}
                          >
                            <Input type="datetime-local" />
                          </Form.Item>
                          <Form.Item
                            {...rest}
                            label="종료"
                            name={[name, "endAt"]}
                          >
                            <Input type="datetime-local" />
                          </Form.Item>
                        </div>
                        <Form.Item
                          {...rest}
                          label="행사 시간대"
                          name={[name, "timeZone"]}
                          rules={required}
                          extra={`위 입력 시각은 ${localZone} 기준입니다. 행사 시간대는 앱의 표시 기준입니다.`}
                        >
                          <Input placeholder="Asia/Seoul" />
                        </Form.Item>
                      </div>
                    ))}
                    <Button
                      icon={<PlusOutlined aria-hidden="true" />}
                      onClick={() => {
                        add({
                          id: crypto.randomUUID(),
                          title: "",
                          dateLabel: "",
                          startAt: null,
                          endAt: null,
                          timeZone: "Asia/Seoul",
                        });
                        setDirty(true);
                      }}
                    >
                      일정 추가
                    </Button>
                  </>
                )}
              </Form.List>
            </Card>
          </div>
          <div>
            <Card title="게시 설정">
              <Form.Item name="publication_status" label="게시 상태">
                <Select virtual={false} options={options(publicationLabels)} />
              </Form.Item>
              <p className="field-note">
                초안·숨김은 앱에 전달되지 않습니다. 게시 후에도 모집 중이며 공식
                확인이 유효해야 탐색에 노출됩니다.
              </p>
              <Button
                type="primary"
                htmlType="submit"
                block
                size="large"
                loading={saving}
                aria-label="저장"
                aria-busy={saving}
              >
                저장
              </Button>
              {dirty && <p className="unsaved">저장하지 않은 변경이 있어요.</p>}
            </Card>
            <Card title="공식 출처">
              <Form.Item
                label="공식 안내 URL"
                name="official_url"
                rules={[...required, ...urlRules]}
              >
                <Input type="url" />
              </Form.Item>
              <Form.Item
                label="신청 URL"
                name="application_url"
                rules={urlRules}
              >
                <Input type="url" />
              </Form.Item>
              <Form.Item
                label="대표 이미지 URL"
                name="image_url"
                rules={urlRules}
              >
                <Input type="url" />
              </Form.Item>
              {activity && (
                <>
                  <dl>
                    <dt>마지막 공식 확인</dt>
                    <dd>{dateText(activity.source_checked_at)}</dd>
                    <dt>확인 유효기간</dt>
                    <dd>{dateText(activity.valid_until)}</dd>
                  </dl>
                  {activity.source_note && (
                    <Typography.Paragraph className="source-note">
                      {activity.source_note}
                    </Typography.Paragraph>
                  )}
                  <Space direction="vertical">
                    <a
                      href={activity.official_url}
                      target="_blank"
                      rel="noreferrer"
                    >
                      공식 안내 열기 ↗
                    </a>
                    <Button
                      icon={<CheckCircleOutlined aria-hidden="true" />}
                      disabled={dirty || saving}
                      onClick={() => {
                        setEvidence("");
                        setVerifyOpen(true);
                      }}
                    >
                      공식 정보 확인 기록
                    </Button>
                  </Space>
                </>
              )}
              {!id && (
                <p className="field-note">
                  활동을 먼저 저장한 뒤 공식 정보 확인을 기록할 수 있어요.
                </p>
              )}
            </Card>
          </div>
        </div>
      </Form>
      <Modal
        title="공식 정보를 확인했나요?"
        open={verifyOpen}
        onCancel={() => setVerifyOpen(false)}
        onOk={verify}
        confirmLoading={verifying}
        okText="확인 기록"
        cancelText="취소"
        okButtonProps={{ disabled: evidence.trim().length < 10 }}
      >
        <p>
          공식 원문에서 모집 상태·마감·행사 일정을 확인하고 근거를 남겨 주세요.
          저장하면 현재 시각부터 최대 24시간 유효합니다.
        </p>
        <label htmlFor="evidence">확인 근거</label>
        <Input.TextArea
          id="evidence"
          value={evidence}
          onChange={(e) => setEvidence(e.target.value)}
          rows={4}
          placeholder="공식 원문에서 확인한 내용과 해당 위치를 적어 주세요."
        />
      </Modal>
    </>
  );
}
