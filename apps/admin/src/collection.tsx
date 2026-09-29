import { useState } from "react";
import { useList } from "@refinedev/core";
import { Alert, App, Button, Select, Space, Table, Tag } from "antd";
import { Link } from "react-router";
import { supabase } from "./supabase";
import { dateText, type Program } from "./catalog";

type Job = {
  id: string;
  program_name: string;
  scheduled_day: string;
  status: string;
  attempts: number;
  error: string | null;
  finished_at: string | null;
  stats: Record<string, unknown>;
  usage: Record<string, unknown>;
  run_history: unknown[];
};
type Candidate = {
  id: string;
  activity_id: string | null;
  source_url: string;
  occurrence: string;
  status: string;
  proposed_activity: Record<string, unknown>;
  evidence: {
    quote?: string;
    fetched_at?: string;
    possible_duplicates?: string[];
  };
  checked_at: string;
};
const labels: Record<string, string> = {
  queued: "대기",
  running: "수집 중",
  succeeded: "완료",
  failed: "실패 · 재시도 대기",
  blocked: "조치 필요",
  skipped: "건너뜀",
};
function Results({ job }: { job: Job }) {
  const { query, result } = useList<Candidate>({
    resource: "catalog_collection_results",
    filters: [{ field: "job_id", operator: "eq", value: job.id }],
    pagination: { pageSize: 100 },
  });
  return (
    <Space direction="vertical" style={{ width: "100%" }}>
      {job.error && <Alert type="error" message={job.error} />}
      <strong>실행 요약 · 사용량</strong>
      <pre className="audit-json">
        {JSON.stringify(
          { stats: job.stats, usage: job.usage, attempts: job.run_history },
          null,
          2,
        )}
      </pre>
      <p>
        토큰은 CLI 보고값이며 구독 잔여량이나 API 청구액이 아닙니다. 후보는
        회차별 최신 결과만 보존하므로 후속 수집 후에는 최신 작업에서 확인하세요.
      </p>
      {query.isError ? (
        <Alert
          type="error"
          message="후보를 불러오지 못했어요."
          action={<Button onClick={() => query.refetch()}>다시 시도</Button>}
        />
      ) : (
        <Table
          rowKey="id"
          loading={query.isLoading}
          dataSource={result.data}
          pagination={false}
          scroll={{ x: 650 }}
          locale={{ emptyText: "이 작업에 연결된 최신 후보가 없습니다." }}
          columns={[
            { title: "회차", dataIndex: "occurrence" },
            {
              title: "상태",
              render: (_, row) => (
                <Tag color={row.status === "review" ? "orange" : "default"}>
                  {row.status === "review" ? "관리자 검토 필요" : "초안 반영"}
                </Tag>
              ),
            },
            { title: "확인 시각", dataIndex: "checked_at", render: dateText },
            {
              title: "검토",
              render: (_, row) => (
                <Space>
                  <a href={row.source_url} target="_blank" rel="noreferrer">
                    공식 원문
                  </a>
                  {row.activity_id && (
                    <Link to={`/activities/${row.activity_id}`}>활동 편집</Link>
                  )}
                </Space>
              ),
            },
          ]}
          expandable={{
            expandedRowRender: (row) => (
              <>
                <p>
                  원문 구절 일치만 기계 확인했습니다. 모집 여부·날짜·신청 링크의
                  정확성과 공개 여부는 활동 편집에서 별도로 확인하세요.
                </p>
                <blockquote>{row.evidence.quote}</blockquote>
                {!!row.evidence.possible_duplicates?.length && (
                  <p>
                    중복 가능성이 있는 기존 활동:{" "}
                    {row.evidence.possible_duplicates.map((id) => (
                      <Link key={id} to={`/activities/${id}`}>
                        {id}{" "}
                      </Link>
                    ))}
                  </p>
                )}
                <pre className="audit-json">
                  {JSON.stringify(
                    { proposal: row.proposed_activity, evidence: row.evidence },
                    null,
                    2,
                  )}
                </pre>
              </>
            ),
          }}
        />
      )}
    </Space>
  );
}
export function CollectionJobs() {
  const [page, setPage] = useState(1),
    [program, setProgram] = useState<string>(),
    [busy, setBusy] = useState(false);
  const { message } = App.useApp();
  const jobs = useList<Job>({
    resource: "catalog_collection_jobs",
    pagination: { currentPage: page, pageSize: 20 },
    sorters: [{ field: "created_at", order: "desc" }],
    queryOptions: { refetchInterval: 15000 },
  });
  const programs = useList<Program>({
    resource: "catalog_programs",
    pagination: { pageSize: 1000 },
  });
  const settings = useList<{
    id: string;
    enabled: boolean;
    pause_reason: string | null;
  }>({
    resource: "catalog_collection_settings",
    pagination: { pageSize: 1 },
    queryOptions: { refetchInterval: 15000 },
  });
  async function action(name: string, args: Record<string, unknown>) {
    setBusy(true);
    try {
      const { data, error } = await supabase!.rpc(name, args);
      if (error) throw error;
      message.success(
        name === "request_catalog_collection"
          ? `${data}개 작업을 등록했어요. 오늘 이미 등록된 작업과 수집 OFF 프로그램은 제외합니다.`
          : "재시도를 등록했어요. 구독 일시정지도 해제됩니다.",
      );
      await Promise.all([jobs.query.refetch(), settings.query.refetch()]);
    } catch {
      message.error("처리하지 못했어요. 권한·작업 상태·연결을 확인해 주세요.");
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <header className="page-heading">
        <div>
          <span className="eyebrow">COLLECTION</span>
          <h1>활동 수집</h1>
          <p>
            매일 오전 9시 등록 · 로그인된 Mac 워커가 순차 수집 · 신규 활동은
            초안으로 저장합니다.
          </p>
        </div>
      </header>
      <Alert
        type="info"
        showIcon
        message="ChatGPT 구독 사용량을 공유합니다."
        description="Mac과 Supabase가 실행 중이어야 합니다. 인증·한도 오류는 전체 워커를 일시정지합니다. 원인을 해소한 뒤 해당 작업의 재시도를 누르세요. 자동 게시·공식 확인은 하지 않습니다."
      />
      {settings.query.isError && (
        <Alert type="error" message="자동 등록 설정을 불러오지 못했어요." />
      )}
      {settings.result.data[0] && (
        <p>
          자동 등록: {settings.result.data[0].enabled ? "ON" : "OFF"}{" "}
          {settings.result.data[0].pause_reason && (
            <strong> · 일시정지: {settings.result.data[0].pause_reason}</strong>
          )}
        </p>
      )}
      <Space wrap className="list-toolbar">
        <Select
          virtual={false}
          aria-label="수집 프로그램"
          placeholder="수집 ON 프로그램 전체"
          allowClear
          showSearch
          optionFilterProp="label"
          style={{ minWidth: 280 }}
          value={program}
          onChange={setProgram}
          options={programs.result.data
            .filter((p) => p.collection_enabled)
            .map((p) => ({ value: p.id, label: p.title }))}
        />
        <Button
          loading={busy}
          disabled={programs.query.isError || programs.query.isLoading}
          onClick={() =>
            action("request_catalog_collection", {
              target_program: program ?? null,
            })
          }
        >
          오늘 수집 등록
        </Button>
        <Button onClick={() => jobs.query.refetch()}>새로고침</Button>
      </Space>
      {jobs.query.isError ? (
        <Alert type="error" message="작업을 불러오지 못했어요." />
      ) : (
        <Table
          rowKey="id"
          loading={jobs.query.isLoading}
          dataSource={jobs.result.data}
          scroll={{ x: 850 }}
          pagination={{
            current: page,
            pageSize: 20,
            total: jobs.result.total,
            onChange: setPage,
            showSizeChanger: false,
          }}
          columns={[
            { title: "프로그램", dataIndex: "program_name" },
            { title: "등록일 (한국)", dataIndex: "scheduled_day" },
            {
              title: "상태",
              dataIndex: "status",
              render: (value) => (
                <Tag
                  color={
                    value === "blocked" || value === "failed"
                      ? "orange"
                      : "default"
                  }
                >
                  {labels[value] ?? value}
                </Tag>
              ),
            },
            { title: "시도", dataIndex: "attempts", width: 70 },
            { title: "오류", dataIndex: "error", ellipsis: true, width: 240 },
            { title: "완료 시각", dataIndex: "finished_at", render: dateText },
            {
              title: "조치",
              render: (_, row) =>
                ["failed", "blocked"].includes(row.status) ? (
                  <Button
                    loading={busy}
                    onClick={() =>
                      action("resume_catalog_collection", { job_id: row.id })
                    }
                  >
                    원인 해소 후 재시도
                  </Button>
                ) : null,
            },
          ]}
          expandable={{ expandedRowRender: (job) => <Results job={job} /> }}
        />
      )}
    </>
  );
}
