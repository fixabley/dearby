"use client";
import {
  createContext,
  useContext,
  useSyncExternalStore,
  useState,
  type ReactNode,
} from "react";
import { blankSaved, parseSaved, storageKey, type Saved } from "./storage";
type StorageIssue = "corrupt" | "read" | "write" | null;
type Snapshot = { saved: Saved; ready: boolean; message: string; issue: StorageIssue };
const serverSnapshot: Snapshot = { saved: blankSaved, ready: false, message: "", issue: null };
const Context = createContext<ReturnType<typeof createSavedStore> | null>(null);
function createSavedStore() {
  let snapshot = serverSnapshot;
  const listeners = new Set<() => void>();
  function update(patch: Partial<Snapshot>) {
    snapshot = { ...snapshot, ...patch };
    listeners.forEach((listener) => listener());
  }
  function load() {
    let raw: string | null;
    try {
      raw = localStorage.getItem(storageKey);
    } catch {
      update({ issue: "read", message: "브라우저 저장소에 접근할 수 없습니다. 접근 설정을 확인하고 다시 시도해 주세요.", ready: true });
      return;
    }
    try {
      update({ saved: parseSaved(raw), issue: null, message: "" });
    } catch {
      update({ saved: blankSaved, issue: "corrupt", message: "저장 정보가 손상되어 불러오지 못했습니다. 원본은 보존되어 있습니다. 다시 시도하거나 손상된 스크랩을 초기화해 주세요." });
    }
    update({ ready: true });
  }
  const sync = (e: StorageEvent) => {
    if (e.key === storageKey || e.key === null) load();
  };
  function subscribe(listener: () => void) {
    listeners.add(listener);
    if (listeners.size === 1) {
      window.addEventListener("storage", sync);
      load();
    }
    return () => {
      listeners.delete(listener);
      if (!listeners.size) window.removeEventListener("storage", sync);
    };
  }
  function persist(next: Saved, success: string) {
    try {
      localStorage.setItem(storageKey, JSON.stringify(next));
      update({ issue: null, message: success });
    } catch {
      update({ issue: "write", message: "브라우저 저장에 실패했습니다. 현재 화면에만 반영되며 새로고침하면 사라집니다. 저장을 다시 시도할 수 있습니다." });
    }
  }
  function toggle(kind: keyof Saved, id: string) {
    const { saved, ready, issue } = snapshot;
    if (!ready || issue === "corrupt" || issue === "read") return;
    const next = {
      ...saved,
      [kind]: saved[kind].includes(id)
        ? saved[kind].filter((x) => x !== id)
        : [...saved[kind], id],
    };
    update({ saved: next });
    persist(
      next,
      next[kind].includes(id)
        ? "스크랩했습니다. 이 브라우저에 저장됩니다."
        : "스크랩을 해제했습니다.",
    );
  }
  function reset() {
    try {
      localStorage.removeItem(storageKey);
      update({ saved: blankSaved, issue: null, message: "손상된 스크랩을 초기화했습니다. 새로 저장할 수 있습니다." });
    } catch {
      update({ issue: "read", message: "초기화하지 못했습니다. 브라우저 저장소 접근 설정을 확인해 주세요." });
    }
  }
  function retry() {
    const { saved, issue } = snapshot;
    if (issue === "write")
      persist(saved, "현재 스크랩을 브라우저에 저장했습니다.");
    else load();
  }
  return { subscribe, getSnapshot: () => snapshot, toggle, retry, reset };
}
export function SavedProvider({ children }: { children: ReactNode }) {
  const [store] = useState(createSavedStore);
  return <Context.Provider value={store}>{children}</Context.Provider>;
}
export function useSaved() {
  const store = useContext(Context);
  if (!store) throw new Error("SavedProvider required");
  // Every consumer, including a late Suspense boundary, hydrates the server value.
  const snapshot = useSyncExternalStore(store.subscribe, store.getSnapshot, () => serverSnapshot);
  return { ...snapshot, toggle: store.toggle, retry: store.retry, reset: store.reset };
}
