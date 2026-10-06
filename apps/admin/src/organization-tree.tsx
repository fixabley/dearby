import { useEffect, useState, type Key } from "react";
import { Link } from "react-router";
import { useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, App, Button, Input, Select, Space, Table, Tag } from "antd";
import { PlusOutlined } from "@ant-design/icons";
import {
  type Activity,
  type Organization,
  type Program,
  discoveryStatus,
  organizationPath,
  parentCandidates,
} from "./catalog";
import { supabase } from "./supabase";

type Row =
  | {
      key: string;
      kind: "organization";
      record: Organization;
      children?: Row[];
    }
  | { key: string; kind: "program"; record: Program; children?: Row[] }
  | { key: string; kind: "activity"; record: Activity }
  | { key: string; kind: "note"; text: string };
type Draft = {
  key: string;
  name: string;
  description: string;
  parent: string | null;
};
const queryKey = "organization-tree";
const byName = (a: string, b: string) => a.localeCompare(b, "ko");
const pathLabel = (organizations: Organization[], id: string | null) =>
  organizationPath(organizations, id)
    .map((o) => o.name)
    .join(" › ");
const pattern = (term: string) => `%${term.replace(/[\\%_]/g, "\\$&")}%`;

async function rows<T>(
  request: PromiseLike<{ data: T[] | null; error: unknown }>,
) {
  const { data, error } = await request;
  if (error) throw error;
  return data ?? [];
}
function saveError(error: unknown) {
  const text = (error as { message?: string })?.message ?? "";
  if (text.includes("4 levels")) return "조직은 최대 4단계까지 만들 수 있어요.";
  if (text.includes("own ancestor"))
    return "자기 자신이나 하위 조직을 상위 조직으로 지정할 수 없어요.";
  if ((error as { code?: string })?.code === "23503")
    return "활동이 연결된 프로그램은 운영 조직을 바꿀 수 없어요.";
  return "저장하지 못했어요. 필수 값과 연결 상태를 확인해 주세요.";
}

export function OrganizationTree() {
  const client = useQueryClient();
  const { message } = App.useApp();
  const [expanded, setExpanded] = useState<Key[]>([]),
    [search, setSearch] = useState(""),
    [draft, setDraft] = useState<Draft>(),
    [saving, setSaving] = useState(false),
    [error, setError] = useState("");
  const organizations = useQuery({
    queryKey: [queryKey, "organizations"],
    queryFn: () =>
      rows<Organization>(supabase!.from("catalog_organizations").select("*")),
  });
  const all = organizations.data ?? [];
  // Before the hierarchy migration rows have no parent_id: show a flat list and never send it.
  const nested = all.some((o) => "parent_id" in o);
  const expandedIds = (prefix: string) =>
    expanded
      .map(String)
      .filter((k) => k.startsWith(prefix))
      .map((k) => k.slice(prefix.length));
  // Children are fetched only for expanded rows; child organizations come from the eager list above.
  const programs = useQueries({
    queries: expandedIds("org:").map((id) => ({
      queryKey: [queryKey, "programs", id],
      queryFn: () =>
        rows<Program>(
          supabase!
            .from("catalog_programs")
            .select("*")
            .eq("organization_id", id),
        ),
    })),
  });
  const activities = useQueries({
    queries: expandedIds("program:").map((id) => ({
      queryKey: [queryKey, "activities", id],
      queryFn: () =>
        rows<Activity>(
          supabase!.from("catalog_activities").select("*").eq("program_id", id),
        ),
    })),
  });
  const matches = useQuery({
    queryKey: [queryKey, "search", search],
    enabled: !!search,
    queryFn: async () => {
      const [programRows, activityRows] = await Promise.all([
        rows<Pick<Program, "organization_id">>(
          supabase!
            .from("catalog_programs")
            .select("organization_id")
            .ilike("title", pattern(search)),
        ),
        rows<Pick<Activity, "program_id" | "organization_id">>(
          supabase!
            .from("catalog_activities")
            .select("program_id,organization_id")
            .ilike("title", pattern(search)),
        ),
      ]);
      return { programRows, activityRows };
    },
  });
  // Expand every ancestor of a matching child organization, program or activity.
  useEffect(() => {
    if (!search || !matches.data || !organizations.data) return;
    const term = search.toLocaleLowerCase();
    const keys = new Set<string>();
    const openPath = (id: string, includeSelf: boolean) =>
      organizationPath(organizations.data, id)
        .slice(0, includeSelf ? undefined : -1)
        .forEach((o) => keys.add(`org:${o.id}`));
    organizations.data
      .filter((o) => o.name.toLocaleLowerCase().includes(term))
      .forEach((o) => openPath(o.id, false));
    matches.data.programRows.forEach((p) => openPath(p.organization_id, true));
    matches.data.activityRows.forEach((a) => {
      openPath(a.organization_id, true);
      keys.add(`program:${a.program_id}`);
    });
    setExpanded((current) => [...new Set([...current.map(String), ...keys])]);
  }, [search, matches.data, organizations.data]);

  const childrenOf = (
    loaded: { data?: unknown[]; isError: boolean } | undefined,
    build: () => Row[],
    key: string,
  ): Row[] => {
    if (!loaded) return []; // Collapsed: an empty list still shows the expand button.
    if (loaded.isError)
      return [
        {
          key: `${key}:note`,
          kind: "note",
          text: "하위 항목을 불러오지 못했어요.",
        },
      ];
    if (!loaded.data)
      return [{ key: `${key}:note`, kind: "note", text: "불러오는 중…" }];
    const built = build();
    return built.length
      ? built
      : [{ key: `${key}:note`, kind: "note", text: "하위 항목이 없습니다." }];
  };
  const programRow = (program: Program): Row => {
    const key = `program:${program.id}`;
    const loaded = activities[expandedIds("program:").indexOf(program.id)];
    return {
      key,
      kind: "program",
      record: program,
      children: childrenOf(
        loaded,
        () =>
          [...(loaded.data as Activity[])]
            .sort((a, b) => byName(a.title, b.title))
            .map((activity) => ({
              key: `activity:${activity.id}`,
              kind: "activity",
              record: activity,
            })),
        key,
      ),
    };
  };
  const organizationRow = (organization: Organization): Row => {
    const key = `org:${organization.id}`;
    const loaded = programs[expandedIds("org:").indexOf(organization.id)];
    return {
      key,
      kind: "organization",
      record: organization,
      children: childrenOf(
        loaded,
        () => [
          ...all
            .filter((o) => o.parent_id === organization.id)
            .sort((a, b) => byName(a.name, b.name))
            .map(organizationRow),
          ...[...(loaded.data as Program[])]
            .sort((a, b) => byName(a.title, b.title))
            .map(programRow),
        ],
        key,
      ),
    };
  };
  const tree = all
    .filter((o) => !o.parent_id)
    .sort((a, b) => byName(a.name, b.name))
    .map(organizationRow);
  const creating = draft?.key === "new";
  if (creating)
    tree.unshift({
      key: "new",
      kind: "organization",
      record: { id: "", name: "", description: "", parent_id: null },
    });

  function edit(row: Row) {
    setError("");
    if (row.kind === "organization" || row.kind === "program")
      setDraft({
        key: row.key,
        description: row.record.description,
        ...(row.kind === "organization"
          ? { name: row.record.name, parent: row.record.parent_id ?? null }
          : { name: row.record.title, parent: row.record.organization_id }),
      });
  }
  async function save(row: Row) {
    if (!draft || (row.kind !== "organization" && row.kind !== "program"))
      return;
    if (!draft.name.trim()) return setError("이름을 입력해 주세요.");
    if (row.kind === "program" && !draft.parent)
      return setError("운영 조직을 선택해 주세요.");
    setSaving(true);
    try {
      const values: Record<string, unknown> =
        row.kind === "organization"
          ? {
              name: draft.name.trim(),
              description: draft.description,
              ...(nested && { parent_id: draft.parent }),
            }
          : {
              title: draft.name.trim(),
              description: draft.description,
              organization_id: draft.parent,
            };
      const table =
        row.kind === "organization"
          ? "catalog_organizations"
          : "catalog_programs";
      const { data, error } = creating
        ? await supabase!.from(table).insert(values).select()
        : await supabase!
            .from(table)
            .update(values)
            .eq("id", row.record.id)
            .select();
      if (error) throw error;
      if (!data?.length) throw new Error();
      setDraft(undefined);
      setError("");
      if (creating && draft.parent)
        setExpanded((current) => [
          ...new Set([
            ...current.map(String),
            ...organizationPath(all, draft.parent).map((o) => `org:${o.id}`),
          ]),
        ]);
      message.success("저장했어요.");
      await client.invalidateQueries({ queryKey: [queryKey] });
    } catch (e) {
      setError(saveError(e));
    } finally {
      setSaving(false);
    }
  }
  const editing = (row: Row) => draft?.key === row.key;

  return (
    <>
      <header className="page-heading">
        <div>
          <span className="eyebrow">ORGANIZATIONS</span>
          <h1>조직 관리</h1>
          <p>
            조직 → 하위 조직 → 프로그램 → 활동을 펼쳐 보고, 이름·소개·상위
            조직을 행에서 바로 고치세요.
          </p>
        </div>
        <Button
          type="primary"
          icon={<PlusOutlined aria-hidden="true" />}
          disabled={!!draft}
          onClick={() => {
            setError("");
            setDraft({ key: "new", name: "", description: "", parent: null });
          }}
        >
          조직 만들기
        </Button>
      </header>
      <div className="list-toolbar">
        <Input.Search
          aria-label="조직·프로그램·활동 검색"
          placeholder="이름으로 검색하면 맞는 항목의 상위가 펼쳐져요"
          allowClear
          onSearch={(value) => setSearch(value.trim())}
          style={{ maxWidth: 420 }}
        />
      </div>
      {error && (
        <Alert
          type="error"
          showIcon
          message={error}
          style={{ marginBottom: 12 }}
        />
      )}
      {matches.isError && (
        <Alert
          type="error"
          message="검색하지 못했어요."
          style={{ marginBottom: 12 }}
        />
      )}
      {organizations.isError ? (
        <Alert
          type="error"
          message="조직을 불러오지 못했어요."
          action={
            <Button onClick={() => organizations.refetch()}>다시 시도</Button>
          }
        />
      ) : (
        <Table<Row>
          rowKey="key"
          loading={organizations.isLoading}
          dataSource={tree}
          pagination={false}
          scroll={{ x: 900 }}
          rowClassName={(row) => {
            const name =
              row.kind === "organization"
                ? row.record.name
                : row.kind === "note"
                  ? ""
                  : row.record.title;
            return search &&
              name.toLocaleLowerCase().includes(search.toLocaleLowerCase())
              ? "tree-match"
              : "";
          }}
          expandable={{
            expandedRowKeys: expanded,
            onExpandedRowsChange: (keys) => setExpanded([...keys]),
          }}
          locale={{ emptyText: "등록된 조직이 없습니다." }}
          columns={[
            {
              title: "이름",
              width: 360,
              render: (_, row) => {
                if (row.kind === "note")
                  return <span className="muted">{row.text}</span>;
                const kind = {
                  organization: "조직",
                  program: "프로그램",
                  activity: "활동",
                }[row.kind];
                const name =
                  row.kind === "organization"
                    ? row.record.name
                    : row.record.title;
                return (
                  // Inline content keeps the tree indent on the first line when the cell wraps.
                  <>
                    <Tag bordered={false}>{kind}</Tag>
                    {editing(row) ? (
                      <Input
                        aria-label={`${kind} 이름`}
                        style={{ width: 180 }}
                        value={draft!.name}
                        onChange={(e) =>
                          setDraft({ ...draft!, name: e.target.value })
                        }
                      />
                    ) : (
                      name
                    )}
                  </>
                );
              },
            },
            {
              title: "상위 조직",
              width: 240,
              render: (_, row) => {
                if (row.kind === "note" || row.kind === "activity") return null;
                const id =
                  row.kind === "organization"
                    ? row.record.parent_id
                    : row.record.organization_id;
                if (!editing(row) || (row.kind === "organization" && !nested))
                  return (
                    <span className="muted">
                      {id ? all.find((o) => o.id === id)?.name : "최상위"}
                    </span>
                  );
                const candidates =
                  row.kind === "organization"
                    ? parentCandidates(all, row.record.id || undefined)
                    : all;
                return (
                  <Select
                    virtual={false}
                    showSearch
                    optionFilterProp="label"
                    aria-label={
                      row.kind === "organization" ? "상위 조직" : "운영 조직"
                    }
                    style={{ width: "100%" }}
                    value={
                      draft!.parent ??
                      (row.kind === "organization" ? "" : undefined)
                    }
                    onChange={(value) =>
                      setDraft({ ...draft!, parent: value || null })
                    }
                    options={[
                      ...(row.kind === "organization"
                        ? [{ value: "", label: "최상위" }]
                        : []),
                      ...candidates
                        .map((o) => ({
                          value: o.id,
                          label: pathLabel(all, o.id),
                        }))
                        .sort((a, b) => byName(a.label, b.label)),
                    ]}
                  />
                );
              },
            },
            {
              title: "소개",
              render: (_, row) => {
                if (row.kind === "note") return null;
                if (row.kind === "activity")
                  return (
                    <>
                      <Tag color={discoveryStatus(row.record).color}>
                        {discoveryStatus(row.record).label}
                      </Tag>
                      <span className="muted">{row.record.summary}</span>
                    </>
                  );
                return editing(row) ? (
                  <Input.TextArea
                    aria-label="소개"
                    autoSize={{ minRows: 1, maxRows: 4 }}
                    value={draft!.description}
                    onChange={(e) =>
                      setDraft({ ...draft!, description: e.target.value })
                    }
                  />
                ) : (
                  row.record.description
                );
              },
            },
            {
              title: "",
              width: 150,
              render: (_, row) => {
                if (row.kind === "note") return null;
                if (row.kind === "activity")
                  return (
                    <Link to={`/activities/${row.record.id}`}>활동 편집</Link>
                  );
                return editing(row) ? (
                  <Space>
                    <Button
                      type="primary"
                      size="small"
                      loading={saving}
                      onClick={() => save(row)}
                    >
                      완료
                    </Button>
                    <Button
                      size="small"
                      disabled={saving}
                      onClick={() => {
                        setDraft(undefined);
                        setError("");
                      }}
                    >
                      취소
                    </Button>
                  </Space>
                ) : (
                  <Button
                    size="small"
                    disabled={!!draft}
                    onClick={() => edit(row)}
                  >
                    편집
                  </Button>
                );
              },
            },
          ]}
        />
      )}
    </>
  );
}
