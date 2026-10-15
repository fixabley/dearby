import { type CSSProperties, useEffect, useState } from "react";
import { useInfiniteQuery, useQuery } from "@tanstack/react-query";
import { Alert, Button, Select, Table, type TableProps } from "antd";
import { api, type Page } from "./api";

type Params = Record<string, string | number | undefined>;
const query = (params: Params) =>
  new URLSearchParams(
    Object.entries(params).flatMap(([key, value]) => (value === undefined || value === "" ? [] : [[key, String(value)]])),
  ).toString();

/** `{items, nextCursor}` lists: id order, "더 보기" appends the next page. */
export function useCursorList<T>(path: string, params: Params) {
  const list = useInfiniteQuery({
    queryKey: [path, params],
    queryFn: ({ pageParam }) => api<Page<T>>(`${path}?${query({ ...params, cursor: pageParam ?? undefined, size: 20 })}`),
    initialPageParam: null as Page<T>["nextCursor"],
    getNextPageParam: (last) => last.nextCursor ?? undefined,
  });
  return { list, items: list.data?.pages.flatMap((page) => page.items) ?? [] };
}

export function CursorTable<T extends { id: number }>({
  cursor: { list, items },
  ...table
}: { cursor: ReturnType<typeof useCursorList<T>> } & TableProps<T>) {
  if (list.isError)
    return (
      <Alert
        type="error"
        showIcon
        message="목록을 불러오지 못했어요."
        description={list.error.message}
        action={<Button onClick={() => list.refetch()}>다시 시도</Button>}
      />
    );
  return (
    <>
      <Table<T> rowKey="id" dataSource={items} loading={list.isLoading} pagination={false} {...table} />
      {list.hasNextPage && (
        <div className="load-more">
          <Button onClick={() => list.fetchNextPage()} loading={list.isFetchingNextPage}>
            더 보기
          </Button>
        </div>
      )}
    </>
  );
}

/** Server-side name search for an organization or program id. Keeps the chosen label while the query changes. */
export function SearchSelect({
  path,
  params = {},
  value,
  onChange,
  placeholder,
  ...rest
}: {
  path: "organizations" | "programs";
  params?: Params;
  value?: number;
  onChange?: (value?: number) => void;
  placeholder: string;
  style?: CSSProperties;
  "aria-label"?: string;
}) {
  const [search, setSearch] = useState(""),
    [debounced, setDebounced] = useState(""),
    [chosen, setChosen] = useState<{ value: number; label: string }>();
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(search), 250);
    return () => clearTimeout(timer);
  }, [search]);
  const result = useQuery({
    queryKey: [path, "search", debounced, params],
    queryFn: () => api<Page<{ id: number; name: string }>>(`${path}?${query({ ...params, q: debounced, size: 20 })}`),
  });
  const options = (result.data?.items ?? []).map((item) => ({ value: item.id, label: item.name }));
  if (chosen && chosen.value === value && !options.some((o) => o.value === value)) options.unshift(chosen);
  return (
    <Select
      {...rest}
      virtual={false}
      showSearch
      allowClear
      filterOption={false}
      placeholder={placeholder}
      loading={result.isFetching}
      notFoundContent={result.isError ? "불러오지 못했어요" : "결과가 없어요"}
      options={options}
      value={value}
      onSearch={setSearch}
      onChange={(next?: number) => {
        setChosen(options.find((o) => o.value === next));
        setSearch("");
        onChange?.(next);
      }}
    />
  );
}
