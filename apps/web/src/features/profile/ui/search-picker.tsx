"use client";
import { useEffect, useId, useState } from "react";
import { Icon } from "@/shared/ui/icon";
import { Input } from "@/shared/ui/input";

export type Pick = { id: number; name: string; hint?: string };

/**
 * 선택 창 검색 (contract "이력" › 등록된 활동 정보에서 검색해 고르고, 없으면 직접 입력): one text field whose typed value is the direct
 * input, with a debounced result list under it; choosing a result sets the id and the name. A chosen id is shown as a chip with
 * `지우기` so the person can type a name instead.
 */
export function SearchPicker({
  label,
  placeholder,
  value,
  pickedId,
  search,
  onChange,
  required,
}: {
  label: string;
  placeholder?: string;
  value: string;
  pickedId?: number;
  search: (q: string) => Promise<Pick[]>;
  onChange: (next: { id?: number; name: string }) => void;
  required?: boolean;
}) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<Pick[]>([]);
  const [open, setOpen] = useState(false);
  const listId = useId();
  useEffect(() => {
    const q = query.trim();
    if (q.length < 1 || pickedId !== undefined) return;
    let cancelled = false;
    const timer = setTimeout(() => {
      search(q).then(
        (found) => {
          if (!cancelled) setResults(found);
        },
        () => {
          if (!cancelled) setResults([]);
        },
      );
    }, 250);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [query, pickedId, search]);
  // Results belong to the current typed query only; a cleared field or a picked id shows none.
  const visible = pickedId === undefined && query.trim().length > 0 ? results : [];
  if (pickedId !== undefined)
    return (
      <div className="flex flex-col gap-1">
        <span className="text-footnote text-secondary-label">{label}</span>
        <span className="flex min-h-9 items-center gap-2">
          <span className="rounded-full bg-mint px-2.5 py-1 text-subheadline font-semibold text-teal">{value}</span>
          <button type="button" className="inline-flex min-h-9 items-center px-2 text-footnote text-teal" onClick={() => onChange({ name: "" })}>
            지우기
          </button>
        </span>
      </div>
    );
  return (
    <label className="relative flex flex-col gap-1">
      <span className="text-footnote text-secondary-label">
        {label}
        {required && <span className="text-danger"> *</span>}
      </span>
      <Input
        type="search"
        value={value}
        placeholder={placeholder}
        autoComplete="off"
        role="combobox"
        aria-expanded={open && visible.length > 0}
        aria-controls={listId}
        onChange={(event) => {
          onChange({ name: event.target.value });
          setQuery(event.target.value);
          setOpen(true);
        }}
        onFocus={() => setOpen(true)}
        onBlur={() => setTimeout(() => setOpen(false), 150)}
      />
      {open && visible.length > 0 && (
        <ul id={listId} role="listbox" className="absolute top-full left-0 z-20 mt-1 max-h-56 w-full overflow-y-auto rounded-control bg-card py-1 shadow-[0_8px_32px_rgb(0_0_0/0.18)]">
          {visible.map((item) => (
            <li key={item.id} role="option" aria-selected="false">
              <button
                type="button"
                className="flex min-h-11 w-full items-center gap-2 px-3 text-left text-subheadline active:bg-fill"
                onMouseDown={(event) => event.preventDefault()}
                onClick={() => {
                  onChange({ id: item.id, name: item.name });
                  setOpen(false);
                  setQuery("");
                }}
              >
                <Icon name="search" size="inline" tone="secondary" />
                <span className="flex-1 truncate">{item.name}</span>
                {item.hint && <span className="truncate text-footnote text-secondary-label">{item.hint}</span>}
              </button>
            </li>
          ))}
        </ul>
      )}
    </label>
  );
}
