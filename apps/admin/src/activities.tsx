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
  type ActivityEvidence,
  evidenceQuoteError,
  recheckState,
  type Program,
  type Schedule,
  dateText,
  discoveryStatus,
  inferredRecruitment,
  normalizeSchedules,
  publicationLabels,
  recruitmentLabels,
} from "./catalog";
import { supabase } from "./supabase";
import { CriteriaEditor } from "./criteria-editor";
import {
  criteriaCategories,
  criteriaRows,
  serializeCriteria,
  type CriteriaCategory,
} from "./criteria";
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
  const ids = result.data.map((a) => a.id);
  const evidence = useList<ActivityEvidence>({
    resource: "catalog_activity_evidence",
    filters: [{ field: "activity_id", operator: "in", value: ids }],
    pagination: { pageSize: 15 },
    queryOptions: { enabled: ids.length > 0, refetchInterval: 30000 },
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
              title: "발견 노출",
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
            {
              title: "자동 재확인",
              width: 220,
              render: (_, a) => (
                <Recheck
                  state={recheckState(
                    a,
                    evidence.result.data.find((e) => e.activity_id === a.id),
                  )}
                />
              ),
            },
            { title: "수정", dataIndex: "updated_at", render: dateText },
            {
              title: "",
              width: 64,
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
  const watched = {
    recruitment_status: Form.useWatch("recruitment_status", form) ?? "unknown",
    recruitment_start_at: instant(Form.useWatch("recruitment_start_at", form)),
    recruitment_end_at: instant(Form.useWatch("recruitment_end_at", form)),
  };
  const inferred = inferredRecruitment(watched);
  const recruitmentPreview = inferred.byPeriod
    ? `기간 기준 ${recruitmentLabels[inferred.status]}`
    : inferred.status === "closed"
      ? "조기 마감 (관리자 선택)"
      : `날짜 없음 · ${recruitmentLabels[inferred.status]}`;
  const [loadedVersion, setLoadedVersion] = useState<string>();
  const [dirty, setDirty] = useState(false),
    [error, setError] = useState(""),
    [programSearch, setProgramSearch] = useState("");
  const [verifyOpen, setVerifyOpen] = useState(false),
    [evidence, setEvidence] = useState(""),
    [quote, setQuote] = useState(""),
    [verifying, setVerifying] = useState(false);
  const quoteError = evidenceQuoteError(quote);
  const { query } = useOne<Activity>({
    resource,
    id: id ?? "",
    queryOptions: { enabled: !!id },
  });
  const activity = query.data?.data;
  const evidenceRow = useList<ActivityEvidence>({
    resource: "catalog_activity_evidence",
    filters: [{ field: "activity_id", operator: "eq", value: id }],
    queryOptions: { enabled: !!id },
  }).result.data[0];
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
        conditions: criteriaRows(activity.criteria),
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
        criteria: serializeCriteria(values.conditions),
        recruitment_start_at: instant(values.recruitment_start_at),
        recruitment_end_at: instant(values.recruitment_end_at),
        schedules: normalizeSchedules(
          (values.schedules ?? []).map((s: Schedule) => ({
            ...s,
            startAt: instant(s.startAt),
            endAt: instant(s.endAt),
          })),
        ),
      };
      delete payload.conditions;
      for (const field of [
        "location",
        "cost",
        "audience",
        "qualification",
        "application_url",
        "image_url",
      ])
        payload[field] = values[field]?.trim() || null;
      payload.public_note = values.public_note?.trim() ?? "";
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
        evidence_quote: quote.trim() || null,
      });
      if (error) throw error;
      setVerifyOpen(false);
      await query.refetch();
      message.success("공식 정보 확인을 기록했어요.");
    } catch (e) {
      setError(
        (e as { message?: string })?.message?.startsWith("Evidence quote")
          ? "재확인 기준 구절은 줄바꿈·생략부호 없는 20~200자 한 구절이어야 해요."
          : "공식 확인을 저장하지 못했어요. 내부 확인 근거를 10자 이상 입력하고 연결 상태를 확인해 주세요.",
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
          conditions: { audience: [], qualification: [], roles: [] },
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
              </div>
              <section className="recruitment-period" aria-label="모집 기간">
                <strong>모집 기간</strong>
                <div className="two-fields">
                  <Form.Item label="모집 시작" name="recruitment_start_at">
                    <Input type="datetime-local" />
                  </Form.Item>
                  <Form.Item label="모집 마감" name="recruitment_end_at">
                    <Input type="datetime-local" />
                  </Form.Item>
                </div>
                <Form.Item
                  label="모집 상태"
                  name="recruitment_status"
                  extra="보통은 자동을 두고 기간만 입력하세요. 조기 마감은 기간과 관계없이 마감으로 표시돼요. 상시 모집 중은 날짜가 없을 때만 의미가 있어요."
                >
                  <Select
                    virtual={false}
                    options={[
                      { value: "unknown", label: "자동 (기간 기준)" },
                      { value: "open", label: "상시 모집 중 (날짜 없음)" },
                      { value: "closed", label: "조기 마감" },
                      // Legacy value: shown so editing does not silently change it.
                      ...(watched.recruitment_status === "scheduled"
                        ? [{ value: "scheduled", label: "모집 예정 (기존 값)" }]
                        : []),
                    ]}
                  />
                </Form.Item>
                <p className="recruitment-preview" role="status">
                  현재 상태: <strong>{recruitmentPreview}</strong>
                </p>
                <p className="field-note">
                  입력 시각 기준: {localZone}. 확인되지 않은 시각은 비워 두세요.
                </p>
              </section>
              <p className="field-note">
                항목 이름을 검색하거나 직접 추가하세요.
                숫자·문자열·날짜·참/거짓·null·객체·배열을 선택할 수 있어요.
                객체와 배열 안에도 항목을 중첩할 수 있습니다. 비슷한 이름은
                추천만 하며 자동으로 합치지 않습니다.
              </p>
              {Object.entries(criteriaCategories).map(([category, label]) => (
                <Form.Item
                  key={category}
                  label={label}
                  name={["conditions", category]}
                >
                  <CriteriaEditor category={category as CriteriaCategory} />
                </Form.Item>
              ))}
              <Form.Item label="참가 대상 추가 설명" name="audience">
                <Input.TextArea rows={2} />
              </Form.Item>
              <Form.Item label="지원 자격 추가 설명" name="qualification">
                <Input.TextArea rows={2} />
              </Form.Item>
              <Form.Item noStyle shouldUpdate>
                {() => {
                  let preview: string;
                  try {
                    preview = JSON.stringify(
                      serializeCriteria(form.getFieldValue("conditions")),
                      null,
                      2,
                    );
                  } catch {
                    preview =
                      "항목 이름과 값을 모두 입력하면 저장할 JSON이 표시됩니다.";
                  }
                  return (
                    <details className="criteria-preview">
                      <summary>저장할 조건 JSON 보기</summary>
                      <pre>{preview}</pre>
                    </details>
                  );
                }}
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
              <Form.Item
                label="방문자에게 보일 확인 안내"
                name="public_note"
                extra="웹 활동 상세의 '공식 출처'에 그대로 보여요. 비워 두면 기본 안내가 보여요. 바꾸면 공식 확인이 해제되니 저장 후 다시 확인해 주세요."
              >
                <Input.TextArea
                  rows={3}
                  maxLength={1000}
                  placeholder="예: 공식 공지에 따르면 지원은 10월 24일 오후 6시(한국 시간)에 마감합니다."
                />
              </Form.Item>
              {activity && (
                <>
                  <dl>
                    <dt>마지막 공식 확인</dt>
                    <dd>{dateText(activity.source_checked_at)}</dd>
                    <dt>확인 유효기간</dt>
                    <dd>{dateText(activity.valid_until)}</dd>
                    <dt>자동 재확인</dt>
                    <dd>
                      {activity.publication_status === "published" ? (
                        <Recheck state={recheckState(activity, evidenceRow)} />
                      ) : (
                        "게시한 활동만 자동 재확인해요."
                      )}
                    </dd>
                    {evidenceRow && (
                      <>
                        <dt>재확인 기준 구절</dt>
                        <dd>{evidenceRow.quote}</dd>
                      </>
                    )}
                  </dl>
                  {activity.source_note && (
                    <Typography.Paragraph className="source-note">
                      <strong>내부 확인 근거</strong>
                      <br />
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
                        setQuote(evidenceRow?.quote ?? "");
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
        okButtonProps={{
          disabled: evidence.trim().length < 10 || !!quoteError,
        }}
      >
        <p>
          공식 원문에서 모집 상태·마감·행사 일정을 확인해 주세요. 저장하면 현재
          시각부터 최대 24시간 유효합니다.
        </p>
        <label htmlFor="evidence">
          내부 확인 근거 (방문자에게 보이지 않음)
        </label>
        <Input.TextArea
          id="evidence"
          value={evidence}
          onChange={(e) => setEvidence(e.target.value)}
          rows={4}
          placeholder="공식 원문에서 확인한 내용과 위치를 적어 주세요. 방문자에게 보일 안내는 활동 편집의 '방문자에게 보일 확인 안내' 칸에 써요."
        />
        <label htmlFor="evidence-quote">재확인 기준 구절</label>
        <Input.TextArea
          id="evidence-quote"
          value={quote}
          onChange={(e) => setQuote(e.target.value)}
          rows={2}
          status={quoteError ? "error" : undefined}
          aria-describedby="evidence-quote-help"
          placeholder="모집 상태나 마감이 드러나는 원문 문장을 그대로 복사해 붙여 넣어 주세요."
        />
        <p
          id="evidence-quote-help"
          className={quoteError ? "field-error" : "field-note"}
        >
          {quoteError ??
            (quote.trim()
              ? "수집 워커가 이 구절이 원문에 그대로 남아 있는지 확인해 24시간 확인을 자동으로 연장해요. 20~200자, 줄바꿈·생략부호·마크다운 기호 없이 입력하세요."
              : "비워 두면 자동 재확인 대상이 아니에요. 24시간마다 직접 다시 확인해야 해요. 기존 구절도 삭제돼요.")}
        </p>
      </Modal>
    </>
  );
}

function Recheck({ state }: { state: ReturnType<typeof recheckState> }) {
  if (!state) return <span className="muted">게시 전</span>;
  return (
    <>
      <Tag color={state.color}>{state.label}</Tag>
      <div className="muted">{state.detail}</div>
    </>
  );
}
