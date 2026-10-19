import { type QueryClient, useQuery } from "@tanstack/react-query";
import { Select, Tag, Tooltip } from "antd";
import { api } from "./api";

/** 태그 사전 항목 (contract native-v1.md "태그(얻을 수 있는 것)" 2026-10-19): 한글 이름과 선택 영문 이름. */
export type TagItem = { id: number; name: string; nameEn: string | null };
/** A form value: ids of existing tags, or the raw text an operator typed for a tag that does not exist yet. */
export type TagValue = number | string;

export const tagsQueryKey = ["tags"] as const;
export function useTags() {
  return useQuery({ queryKey: tagsQueryKey, queryFn: () => api<{ items: TagItem[] }>("tags"), select: (page) => page.items });
}

/** The server merges names that differ only by spaces; match the same way here so a typed duplicate reuses the existing tag. */
const normalize = (text: string) => text.replace(/\s+/g, "").toLowerCase();
const matches = (tag: TagItem, text: string) => {
  const needle = normalize(text);
  return needle.length > 0 && (normalize(tag.name).includes(needle) || (tag.nameEn ? normalize(tag.nameEn).includes(needle) : false));
};
const sameName = (tag: TagItem, text: string) => normalize(tag.name) === normalize(text) || (tag.nameEn ? normalize(tag.nameEn) === normalize(text) : false);

/**
 * 검색형 다중 선택 + 직접 입력. Existing tags are offered by Korean/English name; a typed name that is not in the dictionary stays as
 * text until `resolveTagIds` creates it on save. Works as an Ant Form control (`value`/`onChange`).
 */
export function TagSelect({ value, onChange, placeholder = "태그 검색 또는 새 이름 입력" }: { value?: TagValue[]; onChange?: (value: TagValue[]) => void; placeholder?: string }) {
  const tags = useTags();
  const options = (tags.data ?? []).map((tag) => ({ value: tag.id, label: tag.nameEn ? `${tag.name} · ${tag.nameEn}` : tag.name, tag }));
  return (
    <Select<TagValue[]>
      mode="tags"
      value={value}
      onChange={(next) => {
        // A typed name that equals an existing tag (ignoring spaces) becomes that tag's id right away.
        const resolved = next.map((entry) => (typeof entry === "string" ? (tags.data?.find((tag) => sameName(tag, entry))?.id ?? entry.trim()) : entry));
        onChange?.(Array.from(new Set(resolved.filter((entry) => entry !== ""))));
      }}
      options={options}
      loading={tags.isLoading}
      placeholder={placeholder}
      tokenSeparators={[","]}
      filterOption={(input, option) => (option?.tag ? matches(option.tag, input) : true)}
      optionFilterProp="label"
      style={{ width: "100%" }}
      aria-label="태그"
    />
  );
}

/**
 * Turns form values into tag ids for the API: existing ids pass through, typed names are created with `POST /v1/admin/tags {name}`
 * (the server returns the existing tag when the name matches ignoring spaces). The dictionary query is refreshed afterwards.
 */
export async function resolveTagIds(values: TagValue[] | undefined, client: QueryClient): Promise<number[]> {
  const ids: number[] = [];
  let created = false;
  for (const entry of values ?? []) {
    if (typeof entry === "number") {
      ids.push(entry);
      continue;
    }
    const name = entry.trim();
    if (!name) continue;
    const tag = await api<TagItem>("tags", "POST", { name });
    ids.push(tag.id);
    created = true;
  }
  if (created) await client.invalidateQueries({ queryKey: tagsQueryKey });
  return Array.from(new Set(ids));
}

/** Chips for a row: the first `max` tags and `+n` with the rest in a tooltip. Renders nothing without tags. */
export function TagChips({ tags, max = 3 }: { tags?: TagItem[] | null; max?: number }) {
  if (!tags?.length) return null;
  const shown = tags.slice(0, max);
  const rest = tags.slice(max);
  return (
    <span className="tag-chips">
      {shown.map((tag) => (
        <Tag key={tag.id} color="cyan">
          {tag.name}
        </Tag>
      ))}
      {rest.length > 0 && (
        <Tooltip title={rest.map((tag) => tag.name).join(", ")}>
          <Tag>+{rest.length}</Tag>
        </Tooltip>
      )}
    </span>
  );
}
