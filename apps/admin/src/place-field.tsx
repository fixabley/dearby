import { useQuery } from "@tanstack/react-query";
import { AutoComplete, Button, Input, Space, Tag } from "antd";
import { useEffect, useState } from "react";
import { api } from "./api";

/** 활동 장소 (contract native-v1.md "활동 장소" 2026-10-19): 이름·도로명 주소·좌표. null = 지정 없음. */
export type Place = { name: string; address: string; lat: number; lng: number };
/** One geocoding candidate from `GET /v1/admin/geocode?q=`; `source` names the provider (kakao or nominatim). */
type GeocodeItem = Place & { source: string };

const MIN_QUERY = 2;
const DEBOUNCE_MS = 400;

/** Debounced copy of [value]: updates only after the user pauses typing. */
function useDebounced(value: string, ms: number) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const handle = window.setTimeout(() => setDebounced(value), ms);
    return () => window.clearTimeout(handle);
  }, [value, ms]);
  return debounced;
}

const coordinate = (value: number) => value.toFixed(5);

/**
 * 장소 검색·선택·지우기. Typing a place name or address lists geocoding candidates; picking one fills the place (the name stays
 * editable, the address and coordinates come from the provider). Works as an Ant Form control (`value`/`onChange`).
 */
export function PlaceField({ value, onChange }: { value?: Place | null; onChange?: (value: Place | null) => void }) {
  const [query, setQuery] = useState("");
  const debounced = useDebounced(query.trim(), DEBOUNCE_MS);
  const enabled = debounced.length >= MIN_QUERY;
  const candidates = useQuery({
    queryKey: ["geocode", debounced],
    queryFn: () => api<{ items: GeocodeItem[] }>(`geocode?q=${encodeURIComponent(debounced)}`),
    enabled,
    select: (page) => page.items,
    staleTime: 5 * 60_000,
  });
  const options = (candidates.data ?? []).map((item, index) => ({
    value: String(index),
    item,
    label: (
      <div className="place-option">
        <span className="place-option-name">{item.name}</span>
        <span className="place-option-address muted">{item.address}</span>
        <Tag className="place-option-source">{item.source}</Tag>
      </div>
    ),
  }));
  return (
    <div className="place-field">
      <AutoComplete
        value={query}
        onChange={(next) => setQuery(typeof next === "string" ? next : "")}
        options={options}
        onSelect={(_, option) => {
          const item = (option as { item: GeocodeItem }).item;
          onChange?.({ name: item.name, address: item.address, lat: item.lat, lng: item.lng });
          setQuery("");
        }}
        notFoundContent={enabled ? (candidates.isFetching ? "찾는 중…" : candidates.isError ? "장소 검색에 실패했어요." : "찾은 장소가 없어요.") : null}
        style={{ width: "100%" }}
        aria-label="장소 검색"
      >
        <Input placeholder="장소 이름이나 주소로 검색 (2자 이상)" allowClear />
      </AutoComplete>
      {value ? (
        <div className="place-picked">
          <Input
            value={value.name}
            maxLength={200}
            onChange={(event) => onChange?.({ ...value, name: event.target.value })}
            aria-label="장소 이름"
          />
          <div className="muted">{value.address}</div>
          <Space size={8} align="center">
            <span className="place-coords muted">
              {coordinate(value.lat)}, {coordinate(value.lng)}
            </span>
            <Button size="small" onClick={() => onChange?.(null)}>
              지우기
            </Button>
          </Space>
        </div>
      ) : (
        <p className="field-note">지정한 장소가 없어요. 검색해서 고르면 이름·주소·좌표가 채워져요.</p>
      )}
    </div>
  );
}
