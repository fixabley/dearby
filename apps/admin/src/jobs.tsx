import { useState, type Key, type KeyboardEvent } from "react";
import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, App, Button, Input, Modal, Popconfirm, Radio, Space, Table, Tabs, Tag } from "antd";
import { PlusOutlined } from "@ant-design/icons";
import { api, useSingleFlight } from "./api";
import { CursorTable, SearchSelect, allPages, useCursorList } from "./list";

/** Contract admin-v1 "직무": a hierarchy like organizations, plus free-typed profile entries waiting for approval. */
type Job = { id: number; name: string; nameEn: string | null; parentId: number | null; childCount: number; ancestors: { id: number; name: string }[] };
type Entry = { id: string; profileName: string; job: { id: number; name: string } | null; jobName: string | null; approval: "approved" | "pending" | "rejected"; approvalReason: string | null };
/** A job row, a "new" row (no job) or a note row while a level loads. */
type Row = { key: string; job?: Job; note?: string; children?: Row[] };
type Draft = { key: string; id?: number; name: string; nameEn: string; parentId?: number; parentName?: string };

const byName = (a: { name: string }, b: { name: string }) => a.name.localeCompare(b.name, "ko");
const idsOf = (keys: Key[]) => [...new Set(keys.map((key) => Number(String(key).split("/").at(-1))))].filter((id) => !Number.isNaN(id));

export function Jobs() {
  return (
    <>
      <header className="page-heading">
        <div>
          <span className="eyebrow">JOBS</span>
          <h1>직무</h1>
          <p>프로필에서 검색해 고르는 직무 분류예요. 조직처럼 상위 직무 아래 계층을 만들고, 사용자가 직접 적은 직무를 승인하세요.</p>
        </div>
      </header>
      <Tabs
        items={[
          { key: "tree", label: "직무 분류", children: <JobTree /> },
          { key: "entries", label: "승인 대기", children: <EntryList /> },
        ]}
      />
    </>
  );
}

function JobTree() {
  const client = useQueryClient();
  const { message } = App.useApp();
  const [expanded, setExpanded] = useState<Key[]>([]),
    [matches, setMatches] = useState<Set<number>>(),
    [draft, setDraft] = useState<Draft>();
  const roots = useQuery({ queryKey: ["jobs", "tree", "roots"], queryFn: () => allPages<Job>("jobs", { rootsOnly: "true" }) });
  const find = useMutation({
    mutationFn: (q: string) => allPages<Job>("jobs", { q }),
    onSuccess: (found) => {
      setMatches(new Set(found.map((j) => j.id)));
      const paths = found.flatMap((j) => j.ancestors.map((_, i) => j.ancestors.slice(0, i + 1).map((a) => `j${a.id}`).join("/")));
      setExpanded((keys) => [...new Set([...keys, ...paths])]);
    },
  });
  const ids = idsOf(expanded.map((k) => String(k).replace(/j(\d+)$/, "$1")));
  const levels = useQueries({
    queries: ids.map((id) => ({ queryKey: ["jobs", "tree", "children", id], queryFn: () => allPages<Job>("jobs", { parentId: id }) })),
  });
  const save = useMutation({
    mutationFn: async (draft: Draft) => {
      const name = draft.name.trim(),
        nameEn = draft.nameEn.trim();
      if (!name) throw new Error("이름을 입력해 주세요.");
      return draft.id === undefined
        ? api("jobs", "POST", { name, nameEn, parentId: draft.parentId })
        : api(`jobs/${draft.id}`, "PATCH", { name, nameEn, ...(draft.parentId === undefined ? { clearParent: true } : { parentId: draft.parentId }) });
    },
    onSuccess: async (_, saved) => {
      setDraft(undefined);
      message.success(saved.id === undefined ? "만들었어요." : "저장했어요.");
      await client.invalidateQueries({ queryKey: ["jobs"] });
    },
  });
  const saveOnce = useSingleFlight();
  const submit = () => saveOnce(() => save.mutateAsync(draft!));
  const remove = useMutation({
    mutationFn: (id: number) => api<void>(`jobs/${id}`, "DELETE"),
    onSuccess: async () => {
      message.success("삭제했어요.");
      await client.invalidateQueries({ queryKey: ["jobs"] });
    },
  });
  const removeOnce = useSingleFlight();
  const start = (next: Draft, expand?: string) => {
    save.reset();
    remove.reset();
    setDraft(next);
    if (expand) setExpanded((keys) => (keys.includes(expand) ? keys : [...keys, expand]));
  };
  const row = (job: Job, parentKey?: string): Row => {
    const key = parentKey ? `${parentKey}/j${job.id}` : `j${job.id}`;
    const adding = draft?.id === undefined && draft?.key === `${key}/new`;
    let children: Row[] | undefined;
    if (expanded.includes(key) && (job.childCount > 0 || adding)) {
      const level = levels[ids.indexOf(job.id)];
      children = [
        ...(adding ? [{ key: draft.key } as Row] : []),
        ...(job.childCount > 0
          ? !level || level.isError
            ? [{ key: `${key}/note`, note: "하위 직무를 불러오지 못했어요." } as Row]
            : !level.data
              ? [{ key: `${key}/note`, note: "불러오는 중…" } as Row]
              : [...level.data].sort(byName).map((child) => row(child, key))
          : []),
      ];
    } else if (job.childCount > 0) children = [];
    return { key, job, children };
  };
  const tree: Row[] = [...(roots.data ?? [])].sort(byName).map((job) => row(job));
  if (draft?.key === "new") tree.unshift({ key: "new" });
  const editing = (r: Row) => draft?.key === r.key;
  const cancelOnEscape = (e: KeyboardEvent) => e.key === "Escape" && !save.isPending && setDraft(undefined);
  return (
    <>
      <div className="list-toolbar">
        <Input.Search
          aria-label="직무 검색"
          placeholder="직무 이름 검색"
          allowClear
          loading={find.isPending}
          onSearch={(value) => {
            find.reset();
            if (value.trim()) find.mutate(value.trim());
            else setMatches(undefined);
          }}
          style={{ maxWidth: 320 }}
        />
        {matches && (
          <span className="muted" role="status">
            {matches.size ? `맞는 직무 ${matches.size}개를 펼치고 표시했어요.` : "검색 결과가 없어요."}
          </span>
        )}
        <Button type="primary" icon={<PlusOutlined aria-hidden="true" />} disabled={!!draft} onClick={() => start({ key: "new", name: "", nameEn: "" })}>
          최상위 직무 만들기
        </Button>
      </div>
      {find.error && <Alert className="form-error" type="error" showIcon message="검색하지 못했어요." description={find.error.message} />}
      {roots.isError ? (
        <Alert type="error" showIcon message="직무를 불러오지 못했어요." description={roots.error.message} action={<Button onClick={() => roots.refetch()}>다시 시도</Button>} />
      ) : (
        <Table<Row>
          rowKey="key"
          loading={roots.isLoading}
          dataSource={tree}
          pagination={false}
          rowClassName={(r) => (r.job && matches?.has(r.job.id) ? "tree-match" : "")}
          expandable={{ expandedRowKeys: expanded, onExpandedRowsChange: (keys) => setExpanded([...keys]) }}
          locale={{ emptyText: "등록된 직무가 없어요." }}
          columns={[
            {
              title: "이름",
              width: 360,
              render: (_, r) => {
                if (r.note) return <span className="muted">{r.note}</span>;
                if (editing(r))
                  return (
                    <>
                      <Input autoFocus aria-label="직무 이름" placeholder="직무 이름" className="tree-input" value={draft!.name} disabled={save.isPending}
                        onChange={(e) => setDraft({ ...draft!, name: e.target.value })} onPressEnter={submit} onKeyDown={cancelOnEscape} />
                      <Input aria-label="영문 이름(선택)" placeholder="영문 이름(선택)" className="tree-input" value={draft!.nameEn} disabled={save.isPending}
                        onChange={(e) => setDraft({ ...draft!, nameEn: e.target.value })} onPressEnter={submit} onKeyDown={cancelOnEscape} />
                      {save.error && <div className="tree-error" role="alert">{save.error.message}</div>}
                    </>
                  );
                return r.job ? (
                  <>
                    {r.job.name}
                    {r.job.nameEn && <span className="muted name-en">{r.job.nameEn}</span>}
                  </>
                ) : null;
              },
            },
            {
              title: "상위 직무",
              width: 220,
              render: (_, r) => {
                if (r.note) return null;
                if (editing(r))
                  return (
                    <SearchSelect path="jobs" aria-label="상위 직무" placeholder="없음 (최상위 직무)" value={draft!.parentId} label={draft!.parentName}
                      onChange={(parentId) => setDraft({ ...draft!, parentId })} style={{ width: "100%" }} />
                  );
                return r.job ? <span className="muted">{r.job.ancestors.at(-1)?.name ?? "최상위"}</span> : null;
              },
            },
            { title: "내용", width: 160, render: (_, r) => (r.job ? <span className="muted">하위 직무 {r.job.childCount}</span> : null) },
            {
              title: "",
              className: "actions",
              render: (_, r) => {
                if (r.note) return null;
                if (editing(r))
                  return (
                    <Space>
                      <Button type="primary" size="small" loading={save.isPending} onClick={submit}>{draft!.id === undefined ? "만들기" : "저장"}</Button>
                      <Button size="small" disabled={save.isPending} onClick={() => setDraft(undefined)}>취소</Button>
                    </Space>
                  );
                if (!r.job) return null;
                const { id, name, nameEn, parentId, ancestors } = r.job;
                return (
                  <>
                    <Space>
                      <Button size="small" disabled={!!draft} onClick={() => start({ key: r.key, id, name, nameEn: nameEn ?? "", parentId: parentId ?? undefined, parentName: ancestors.at(-1)?.name })}>편집</Button>
                      <Button size="small" disabled={!!draft} onClick={() => start({ key: `${r.key}/new`, name: "", nameEn: "", parentId: id, parentName: name }, r.key)}>하위 직무 추가</Button>
                      <Popconfirm title={`‘${name}’ 직무를 삭제할까요?`} description="하위 직무가 있거나 프로필이 쓰고 있으면 삭제되지 않아요." okText="삭제" cancelText="취소"
                        okButtonProps={{ danger: true }} disabled={!!draft || remove.isPending} onConfirm={() => removeOnce(() => remove.mutateAsync(id))}>
                        <Button size="small" danger disabled={!!draft || remove.isPending} loading={remove.isPending && remove.variables === id}>삭제</Button>
                      </Popconfirm>
                    </Space>
                    {remove.error && remove.variables === id && <div className="tree-error" role="alert">{remove.error.message}</div>}
                  </>
                );
              },
            },
          ]}
        />
      )}
    </>
  );
}

/** Free-typed entries: approve as is, approve mapped to a job in the tree, or reject with a reason. */
function EntryList() {
  const cursor = useCursorList<Entry>("job-entries", { approval: "pending" });
  const [reviewing, setReviewing] = useState<Entry>();
  return (
    <>
      <CursorTable
        cursor={cursor}
        locale={{ emptyText: "승인을 기다리는 직무가 없어요." }}
        columns={[
          { title: "작성자", width: 160, render: (_, e) => <strong>{e.profileName || "이름 없음"}</strong> },
          { title: "직접 적은 직무", render: (_, e) => <>{e.jobName} <Tag color="orange">직접 입력</Tag></> },
          { title: "", className: "actions", width: 120, render: (_, e) => <Button type="primary" onClick={() => setReviewing(e)}>검토</Button> },
        ]}
      />
      {reviewing && <EntryModal entry={reviewing} onClose={() => setReviewing(undefined)} />}
    </>
  );
}

function EntryModal({ entry, onClose }: { entry: Entry; onClose: () => void }) {
  const { message } = App.useApp();
  const client = useQueryClient();
  const [decision, setDecision] = useState<"approve" | "reject">("approve");
  const [jobId, setJobId] = useState<number>();
  const [reason, setReason] = useState("");
  const decide = useMutation({
    mutationFn: (body: { decision: "approve" | "reject"; jobId?: number; reason?: string }) => api<Entry>(`job-entries/${entry.id}/approval`, "POST", body),
    onSuccess: async (_, { decision }) => {
      message.success(decision === "approve" ? "승인했어요." : "거절했어요.");
      onClose();
      await client.invalidateQueries({ queryKey: ["job-entries"] });
    },
  });
  const decideOnce = useSingleFlight();
  return (
    <Modal
      open
      title={`직무 검토 · ${entry.profileName || "이름 없음"}`}
      onCancel={onClose}
      okText={decision === "approve" ? "승인" : "거절"}
      okButtonProps={{ danger: decision === "reject", disabled: decision === "reject" && !reason.trim() }}
      cancelText="취소"
      confirmLoading={decide.isPending}
      onOk={() => decideOnce(() => decide.mutateAsync(decision === "approve" ? { decision, jobId } : { decision, reason: reason.trim() }))}
    >
      <Space direction="vertical" style={{ width: "100%" }} size="middle">
        <div>
          직접 적은 직무: <strong>{entry.jobName}</strong>
        </div>
        {decide.error && <Alert type="error" showIcon message={decide.error.message} />}
        <Radio.Group value={decision} onChange={(e) => setDecision(e.target.value)} options={[{ value: "approve", label: "승인" }, { value: "reject", label: "거절" }]} />
        {decision === "approve" ? (
          <>
            <SearchSelect path="jobs" aria-label="분류의 직무로 바꾸기" placeholder="분류의 직무로 바꾸기(선택) — 비우면 적은 이름 그대로 승인" value={jobId} onChange={setJobId} style={{ width: "100%" }} />
            <span className="field-note">분류에 없는 직무는 ‘직무 분류’ 탭에서 먼저 만든 뒤 고를 수 있어요.</span>
          </>
        ) : (
          <Input.TextArea aria-label="거절 사유" placeholder="거절 사유(사용자에게 보여요)" value={reason} maxLength={500} rows={3} onChange={(e) => setReason(e.target.value)} />
        )}
      </Space>
    </Modal>
  );
}
