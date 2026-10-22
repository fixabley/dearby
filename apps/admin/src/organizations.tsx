import { useState, type Key, type KeyboardEvent } from "react";
import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, App, Button, Input, Popconfirm, Space, Table, Tag } from "antd";
import { ExportOutlined, PlusOutlined } from "@ant-design/icons";
import { api, useSingleFlight } from "./api";
import { ActivityTextEditor, type ActivityText, type PublicationStatus, statusLabels } from "./activities";
import { SearchSelect, allPages } from "./list";
import { LogoField } from "./logo";

type Organization = {
  id: number;
  name: string;
  nameEn: string | null;
  parentOrganizationId: number | null;
  childCount: number;
  programCount: number;
  /** Root first, up to the direct parent. */
  ancestors: { id: number; name: string }[];
  /** 조직 로고 (contract admin-v1.md "조직 로고"): public path with `?v=`, null when none; programs without a logo inherit it. */
  logoUrl?: string | null;
};
type Program = { id: number; name: string; nameEn: string | null; organization: { id: number; name: string } };
type Activity = ActivityText & { officialUrl: string; publicationStatus: PublicationStatus; officialSource: boolean };
/** Keys are tree paths ("o1/o5/p3"): a search match expands every prefix of its ancestors' path. */
type Row =
  | { key: string; kind: "organization"; record: Organization; children?: Row[] }
  | { key: string; kind: "program"; record: Program; children?: Row[] }
  | { key: string; kind: "activity"; record: Activity }
  | { key: string; kind: "new" }
  | { key: string; kind: "note"; text: string };
/** Inline edit of an existing row (`id`) or a new organization/program row (no `id`). */
type Draft = { key: string; kind: "organization" | "program"; id?: number; name: string; nameEn: string; parentId?: number; parentName?: string };
type Level<T> = { data?: T; isError: boolean };
type Removal = { key: string; kind: "organization" | "program" | "activity"; id: number };

const kindLabels = { organization: "조직", program: "프로그램", activity: "활동" };
const removal = {
  organization: { path: "organizations", note: "하위 조직이나 프로그램이 있으면 삭제할 수 없어요.", stale: [["organizations"]] },
  program: {
    path: "programs",
    note: "수집 설정과 수집 기록도 함께 삭제돼요. 활동이 있으면 삭제할 수 없어요.",
    stale: [["programs"], ["organizations"]],
  },
  activity: {
    path: "activities",
    note: "삭제하면 되돌릴 수 없고, 수집으로 생긴 활동은 다시 수집되지 않아요.",
    stale: [["activities"]],
  },
};
const byName = (a: { name: string }, b: { name: string }) => a.name.localeCompare(b.name, "ko");
const lastSegment = (key: Key) => String(key).split("/").at(-1)!;
const idsOf = (keys: Key[], prefix: string) => [
  ...new Set(keys.map(lastSegment).filter((segment) => segment.startsWith(prefix)).map((segment) => Number(segment.slice(1)))),
];

export function Organizations() {
  const client = useQueryClient();
  const { message } = App.useApp();
  const [expanded, setExpanded] = useState<Key[]>([]),
    [matches, setMatches] = useState<Set<number>>(),
    [draft, setDraft] = useState<Draft>(),
    [editingActivity, setEditingActivity] = useState<Activity>();
  const roots = useQuery({
    queryKey: ["organizations", "tree", "roots"],
    queryFn: () => allPages<Organization>("organizations", { rootsOnly: "true" }),
  });
  // Searching keeps the whole tree and opens the path to each match, like the previous admin.
  const find = useMutation({
    mutationFn: (q: string) => allPages<Organization>("organizations", { q }),
    onSuccess: (found) => {
      setMatches(new Set(found.map((o) => o.id)));
      const paths = found.flatMap((o) => o.ancestors.map((_, i) => o.ancestors.slice(0, i + 1).map((a) => `o${a.id}`).join("/")));
      setExpanded((keys) => [...new Set([...keys, ...paths])]);
    },
  });
  // Children load only for expanded rows; each organization level is its child organizations plus its programs.
  const organizationIds = idsOf(expanded, "o"),
    programIds = idsOf(expanded, "p");
  const levels = useQueries({
    queries: organizationIds.map((id) => ({
      queryKey: ["organizations", "tree", "children", id],
      queryFn: async () => {
        const [organizations, programs] = await Promise.all([
          allPages<Organization>("organizations", { parentId: id }),
          allPages<Program>("programs", { organizationId: id }),
        ]);
        return { organizations, programs };
      },
    })),
  });
  const activities = useQueries({
    queries: programIds.map((id) => ({
      queryKey: ["activities", "tree", id],
      queryFn: () => allPages<Activity>("activities", { programId: id }),
    })),
  });
  const save = useMutation({
    mutationFn: async (draft: Draft) => {
      const name = draft.name.trim(),
        nameEn = draft.nameEn.trim(); // Empty clears it.
      if (!name) throw new Error("이름을 입력해 주세요.");
      if (draft.kind === "program") {
        if (draft.parentId === undefined) throw new Error("운영 조직을 골라 주세요.");
        return draft.id === undefined
          ? api("programs", "POST", { organizationId: draft.parentId, name, nameEn })
          : api(`programs/${draft.id}`, "PATCH", { name, nameEn, organizationId: draft.parentId });
      }
      return draft.id === undefined
        ? api("organizations", "POST", { name, nameEn, parentOrganizationId: draft.parentId })
        : api(`organizations/${draft.id}`, "PATCH", {
            name,
            nameEn,
            ...(draft.parentId === undefined ? { clearParent: true } : { parentOrganizationId: draft.parentId }),
          });
    },
    onSuccess: async (_, saved) => {
      setDraft(undefined);
      message.success(saved.id === undefined ? "만들었어요." : "저장했어요.");
      // Counts, tree levels and the name pickers all change; programs also move between organizations.
      await Promise.all([
        client.invalidateQueries({ queryKey: ["organizations"] }),
        client.invalidateQueries({ queryKey: ["programs"] }),
      ]);
    },
  });
  const saveOnce = useSingleFlight();
  const submit = () => saveOnce(() => save.mutateAsync(draft!));
  const remove = useMutation({
    mutationFn: ({ kind, id }: Removal) => api<void>(`${removal[kind].path}/${id}`, "DELETE"),
    onSuccess: async (_, { key, kind }) => {
      setExpanded((keys) => keys.filter((k) => k !== key && !String(k).startsWith(`${key}/`)));
      message.success("삭제했어요.");
      await Promise.all(removal[kind].stale.map((queryKey) => client.invalidateQueries({ queryKey })));
    },
  });
  const removeOnce = useSingleFlight();
  function start(next: Draft, expand?: string) {
    save.reset();
    remove.reset();
    setDraft(next);
    if (expand) setExpanded((keys) => (keys.includes(expand) ? keys : [...keys, expand]));
  }

  const childrenOf = <T,>(key: string, level: Level<T> | undefined, build: (data: T) => Row[]): Row[] => {
    if (!level || level.isError) return [{ key: `${key}/note`, kind: "note", text: "하위 항목을 불러오지 못했어요." }];
    if (!level.data) return [{ key: `${key}/note`, kind: "note", text: "불러오는 중…" }];
    return build(level.data);
  };
  const programRow = (program: Program, parentKey: string): Row => {
    const key = `${parentKey}/p${program.id}`;
    return {
      key,
      kind: "program",
      record: program,
      // No activity count in the API: programs are always expandable.
      children: expanded.includes(key)
        ? childrenOf(key, activities[programIds.indexOf(program.id)], (list) =>
            list.length
              ? [...list].sort(byName).map((activity) => ({ key: `${key}/a${activity.id}`, kind: "activity", record: activity }))
              : [{ key: `${key}/note`, kind: "note", text: "활동이 없어요." }],
          )
        : [],
    };
  };
  const organizationRow = (organization: Organization, parentKey?: string): Row => {
    const key = parentKey ? `${parentKey}/o${organization.id}` : `o${organization.id}`;
    const adding = draft?.id === undefined && draft?.key === `${key}/new`;
    const hasChildren = organization.childCount + organization.programCount > 0;
    let children: Row[] | undefined;
    if (expanded.includes(key) && (hasChildren || adding))
      children = [
        ...(adding ? [{ key: draft.key, kind: "new" } as Row] : []),
        ...(hasChildren
          ? childrenOf(key, levels[organizationIds.indexOf(organization.id)], ({ organizations, programs }) => [
              ...[...organizations].sort(byName).map((child) => organizationRow(child, key)),
              ...[...programs].sort(byName).map((program) => programRow(program, key)),
            ])
          : []),
      ];
    else if (hasChildren) children = []; // Collapsed: an empty list still shows the expand button.
    return { key, kind: "organization", record: organization, children };
  };
  const tree: Row[] = [...(roots.data ?? [])].sort(byName).map((organization) => organizationRow(organization));
  if (draft?.key === "new") tree.unshift({ key: "new", kind: "new" });

  const editing = (row: Row) => draft?.key === row.key;
  const cancelOnEscape = (e: KeyboardEvent) => e.key === "Escape" && !save.isPending && setDraft(undefined);
  const parentOf = (organization: Organization) => organization.ancestors.at(-1)?.name;

  return (
    <>
      <header className="page-heading">
        <div>
          <span className="eyebrow">ORGANIZATIONS</span>
          <h1>조직도</h1>
          <p>조직 → 하위 조직 → 프로그램 → 활동을 펼쳐 보고, 이름과 상위 조직을 행에서 바로 고치세요.</p>
        </div>
        <Button
          type="primary"
          icon={<PlusOutlined aria-hidden="true" />}
          disabled={!!draft}
          onClick={() => start({ key: "new", kind: "organization", name: "", nameEn: "" })}
        >
          최상위 조직 만들기
        </Button>
      </header>
      <div className="list-toolbar">
        <Input.Search
          aria-label="조직 검색"
          placeholder="조직 이름 검색"
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
            {matches.size ? `맞는 조직 ${matches.size}곳을 펼치고 표시했어요.` : "검색 결과가 없어요."}
          </span>
        )}
      </div>
      {find.error && <Alert className="form-error" type="error" showIcon message="검색하지 못했어요." description={find.error.message} />}
      {roots.isError ? (
        <Alert
          type="error"
          showIcon
          message="조직을 불러오지 못했어요."
          description={roots.error.message}
          action={<Button onClick={() => roots.refetch()}>다시 시도</Button>}
        />
      ) : (
        <Table<Row>
          rowKey="key"
          loading={roots.isLoading}
          dataSource={tree}
          pagination={false}
          scroll={{ x: 1290 }}
          rowClassName={(row) => (row.kind === "organization" && matches?.has(row.record.id) ? "tree-match" : "")}
          expandable={{ expandedRowKeys: expanded, onExpandedRowsChange: (keys) => setExpanded([...keys]) }}
          locale={{ emptyText: "등록된 조직이 없어요." }}
          columns={[
            {
              title: "이름",
              width: 320,
              render: (_, row) => {
                if (row.kind === "note") return <span className="muted">{row.text}</span>;
                const kind = row.kind === "new" ? kindLabels[draft!.kind] : kindLabels[row.kind];
                return (
                  // Inline content keeps the tree indent on the first line when the cell wraps.
                  <>
                    <Tag bordered={false}>{kind}</Tag>
                    {editing(row) ? (
                      <>
                        <Input
                          autoFocus
                          aria-label={`${kind} 이름`}
                          placeholder={`${kind} 이름`}
                          className="tree-input"
                          value={draft!.name}
                          disabled={save.isPending}
                          onChange={(e) => setDraft({ ...draft!, name: e.target.value })}
                          onPressEnter={submit}
                          onKeyDown={cancelOnEscape}
                        />
                        <Input
                          aria-label="영문 이름(선택)"
                          placeholder="영문 이름(선택)"
                          className="tree-input"
                          value={draft!.nameEn}
                          disabled={save.isPending}
                          onChange={(e) => setDraft({ ...draft!, nameEn: e.target.value })}
                          onPressEnter={submit}
                          onKeyDown={cancelOnEscape}
                        />
                        {save.error && (
                          <div className="tree-error" role="alert">
                            {save.error.message}
                          </div>
                        )}
                      </>
                    ) : (
                      row.kind !== "new" && (
                        <>
                          {row.record.name}
                          {row.record.nameEn && <span className="muted name-en">{row.record.nameEn}</span>}
                        </>
                      )
                    )}
                  </>
                );
              },
            },
            {
              title: "상위·운영 조직",
              width: 220,
              render: (_, row) => {
                if (row.kind === "note" || row.kind === "activity") return null;
                if (editing(row))
                  return (
                    <SearchSelect
                      path="organizations"
                      aria-label={draft!.kind === "organization" ? "상위 조직" : "운영 조직"}
                      placeholder={draft!.kind === "organization" ? "없음 (최상위 조직)" : "운영 조직 검색"}
                      value={draft!.parentId}
                      label={draft!.parentName}
                      onChange={(parentId) => setDraft({ ...draft!, parentId })}
                      style={{ width: "100%" }}
                    />
                  );
                if (row.kind === "program") return <span className="muted">{row.record.organization.name}</span>;
                if (row.kind === "organization") return <span className="muted">{parentOf(row.record) ?? "최상위"}</span>;
                return null;
              },
            },
            {
              title: "로고",
              width: 210,
              render: (_, row) =>
                row.kind === "organization" && !editing(row) ? (
                  <LogoField
                    path={`organizations/${row.record.id}/logo`}
                    name={row.record.name}
                    initialLogoUrl={row.record.logoUrl ?? null}
                    stale={[["organizations"], ["programs"]]}
                    size={28}
                  />
                ) : null,
            },
            {
              title: "내용",
              width: 240,
              render: (_, row) => {
                if (row.kind === "organization")
                  return (
                    <span className="muted">
                      하위 조직 {row.record.childCount} · 프로그램 {row.record.programCount}
                    </span>
                  );
                if (row.kind === "new" && draft?.kind === "program")
                  return <span className="field-note">수집 설정은 만든 뒤 프로그램 화면의 ‘수집 설정’에서 입력할 수 있어요.</span>;
                if (row.kind !== "activity") return null;
                const { publicationStatus, officialSource, officialUrl } = row.record;
                return (
                  <Space wrap size={4}>
                    <Tag color={publicationStatus === "PUBLISHED" ? "blue" : undefined}>{statusLabels[publicationStatus]}</Tag>
                    {officialSource ? <Tag color="green">공식 출처</Tag> : <Tag color="orange">웹 검색 출처</Tag>}
                    <a href={officialUrl} target="_blank" rel="noreferrer">
                      공식 안내 <ExportOutlined aria-hidden="true" />
                    </a>
                  </Space>
                );
              },
            },
            {
              title: "",
              width: 300,
              className: "actions",
              render: (_, row) => {
                if (row.kind === "note") return null;
                if (editing(row))
                  return (
                    <Space>
                      <Button type="primary" size="small" loading={save.isPending} onClick={submit}>
                        {draft!.id === undefined ? "만들기" : "저장"}
                      </Button>
                      <Button size="small" disabled={save.isPending} onClick={() => setDraft(undefined)}>
                        취소
                      </Button>
                    </Space>
                  );
                if (row.kind === "new") return null;
                const { id, name, nameEn } = row.record;
                const target = { key: row.key, kind: row.kind, id };
                const deletion = (
                  <Popconfirm
                    title={`‘${name}’ ${kindLabels[row.kind]}을 삭제할까요?`}
                    description={removal[row.kind].note}
                    okText="삭제"
                    cancelText="취소"
                    okButtonProps={{ danger: true }}
                    disabled={!!draft || remove.isPending}
                    onConfirm={() => removeOnce(() => remove.mutateAsync(target))}
                  >
                    <Button
                      size="small"
                      danger
                      disabled={!!draft || remove.isPending}
                      loading={remove.isPending && remove.variables?.key === row.key}
                    >
                      삭제
                    </Button>
                  </Popconfirm>
                );
                const failed = remove.error && remove.variables?.key === row.key && (
                  <div className="tree-error" role="alert">
                    {remove.error.message}
                  </div>
                );
                if (row.kind === "activity")
                  return (
                    <>
                      <Space>
                        <Button size="small" disabled={!!draft} onClick={() => setEditingActivity(row.record)}>
                          이름·설명 편집
                        </Button>
                        {deletion}
                      </Space>
                      {failed}
                    </>
                  );
                if (row.kind === "program")
                  return (
                    <>
                      <Space>
                        <Button
                          size="small"
                          disabled={!!draft}
                          onClick={() =>
                            start({
                              key: row.key,
                              kind: "program",
                              id,
                              name,
                              nameEn: nameEn ?? "",
                              parentId: row.record.organization.id,
                              parentName: row.record.organization.name,
                            })
                          }
                        >
                          편집
                        </Button>
                        {deletion}
                      </Space>
                      {failed}
                    </>
                  );
                const parentId = row.record.parentOrganizationId ?? undefined;
                const under = { key: `${row.key}/new`, name: "", nameEn: "", parentId: id, parentName: name };
                return (
                  <>
                    <Space>
                      <Button
                        size="small"
                        disabled={!!draft}
                        onClick={() =>
                          start({
                            key: row.key,
                            kind: "organization",
                            id,
                            name,
                            nameEn: nameEn ?? "",
                            parentId,
                            parentName: parentOf(row.record),
                          })
                        }
                      >
                        편집
                      </Button>
                      <Button size="small" disabled={!!draft} onClick={() => start({ ...under, kind: "organization" }, row.key)}>
                        하위 조직 추가
                      </Button>
                      <Button size="small" disabled={!!draft} onClick={() => start({ ...under, kind: "program" }, row.key)}>
                        프로그램 추가
                      </Button>
                      {deletion}
                    </Space>
                    {failed}
                  </>
                );
              },
            },
          ]}
        />
      )}
      {editingActivity && <ActivityTextEditor activity={editingActivity} onClose={() => setEditingActivity(undefined)} />}
    </>
  );
}
