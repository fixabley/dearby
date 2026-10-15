import { useState, type Key } from "react";
import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, App, Button, Input, Space, Table, Tag } from "antd";
import { ExportOutlined, PlusOutlined } from "@ant-design/icons";
import { api } from "./api";
import { type PublicationStatus, statusLabels } from "./activities";
import { SearchSelect, allPages } from "./list";

type Organization = {
  id: number;
  name: string;
  parentOrganizationId: number | null;
  childCount: number;
  programCount: number;
  /** Root first, up to the direct parent. */
  ancestors: { id: number; name: string }[];
};
type Program = { id: number; name: string; organization: { id: number; name: string } };
type Activity = { id: number; name: string; officialUrl: string; publicationStatus: PublicationStatus; officialSource: boolean };
/** Keys are tree paths ("o1/o5/p3"): a search match expands every prefix of its ancestors' path. */
type Row =
  | { key: string; kind: "organization"; record: Organization; children?: Row[] }
  | { key: string; kind: "program"; record: Program; children?: Row[] }
  | { key: string; kind: "activity"; record: Activity }
  | { key: string; kind: "new" }
  | { key: string; kind: "note"; text: string };
/** Inline edit of an existing row (`id`) or a new organization/program row (no `id`). */
type Draft = { key: string; kind: "organization" | "program"; id?: number; name: string; parentId?: number; parentName?: string };
type Level<T> = { data?: T; isError: boolean };

const kindLabels = { organization: "조직", program: "프로그램", activity: "활동" };
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
    [draft, setDraft] = useState<Draft>();
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
      const name = draft.name.trim();
      if (!name) throw new Error("이름을 입력해 주세요.");
      if (draft.kind === "program") {
        if (draft.parentId === undefined) throw new Error("운영 조직을 골라 주세요.");
        return draft.id === undefined
          ? api("programs", "POST", { organizationId: draft.parentId, name })
          : api(`programs/${draft.id}`, "PATCH", { name, organizationId: draft.parentId });
      }
      return draft.id === undefined
        ? api("organizations", "POST", { name, parentOrganizationId: draft.parentId })
        : api(`organizations/${draft.id}`, "PATCH", {
            name,
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
  function start(next: Draft, expand?: string) {
    save.reset();
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
          onClick={() => start({ key: "new", kind: "organization", name: "" })}
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
          scroll={{ x: 1080 }}
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
                          onPressEnter={() => save.mutate(draft!)}
                          onKeyDown={(e) => e.key === "Escape" && !save.isPending && setDraft(undefined)}
                        />
                        {save.error && (
                          <div className="tree-error" role="alert">
                            {save.error.message}
                          </div>
                        )}
                      </>
                    ) : (
                      row.kind !== "new" && row.record.name
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
                if (row.kind === "note" || row.kind === "activity") return null;
                if (editing(row))
                  return (
                    <Space>
                      <Button type="primary" size="small" loading={save.isPending} onClick={() => save.mutate(draft!)}>
                        {draft!.id === undefined ? "만들기" : "저장"}
                      </Button>
                      <Button size="small" disabled={save.isPending} onClick={() => setDraft(undefined)}>
                        취소
                      </Button>
                    </Space>
                  );
                if (row.kind === "new") return null;
                const { id, name } = row.record;
                if (row.kind === "program")
                  return (
                    <Button
                      size="small"
                      disabled={!!draft}
                      onClick={() =>
                        start({ key: row.key, kind: "program", id, name, parentId: row.record.organization.id, parentName: row.record.organization.name })
                      }
                    >
                      편집
                    </Button>
                  );
                const parentId = row.record.parentOrganizationId ?? undefined;
                const under = { key: `${row.key}/new`, name: "", parentId: id, parentName: name };
                return (
                  <Space>
                    <Button
                      size="small"
                      disabled={!!draft}
                      onClick={() => start({ key: row.key, kind: "organization", id, name, parentId, parentName: parentOf(row.record) })}
                    >
                      편집
                    </Button>
                    <Button size="small" disabled={!!draft} onClick={() => start({ ...under, kind: "organization" }, row.key)}>
                      하위 조직 추가
                    </Button>
                    <Button size="small" disabled={!!draft} onClick={() => start({ ...under, kind: "program" }, row.key)}>
                      프로그램 추가
                    </Button>
                  </Space>
                );
              },
            },
          ]}
        />
      )}
    </>
  );
}
