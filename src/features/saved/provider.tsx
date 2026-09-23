"use client";
import {
  createContext,
  useContext,
  useEffect,
  useState,
  useCallback,
  type ReactNode,
} from "react";
import { blankSaved, parseSaved, storageKey, type Saved } from "./storage";
type StorageIssue = "corrupt" | "read" | "write" | null;
const Context = createContext<{
  saved: Saved;
  ready: boolean;
  message: string;
  issue: StorageIssue;
  toggle: (kind: keyof Saved, id: string) => void;
  retry: () => void;
  reset: () => void;
} | null>(null);
export function SavedProvider({ children }: { children: ReactNode }) {
  const [saved, setSaved] = useState(blankSaved),
    [ready, setReady] = useState(false),
    [message, setMessage] = useState(""),
    [issue, setIssue] = useState<StorageIssue>(null);
  const load = useCallback(() => {
    let raw: string | null;
    try {
      raw = localStorage.getItem(storageKey);
    } catch {
      setIssue("read");
      setMessage(
        "브라우저 저장소에 접근할 수 없습니다. 접근 설정을 확인하고 다시 시도해 주세요.",
      );
      setReady(true);
      return;
    }
    try {
      setSaved(parseSaved(raw));
      setIssue(null);
      setMessage("");
    } catch {
      setSaved(blankSaved);
      setIssue("corrupt");
      setMessage(
        "저장 정보가 손상되어 불러오지 못했습니다. 원본은 보존되어 있습니다. 다시 시도하거나 손상된 스크랩을 초기화해 주세요.",
      );
    }
    setReady(true);
  }, []);
  useEffect(() => {
    // Hydrate browser-owned storage once after the identical server/client first render.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    load();
    const sync = (e: StorageEvent) => {
      if (e.key === storageKey || e.key === null) load();
    };
    window.addEventListener("storage", sync);
    return () => window.removeEventListener("storage", sync);
  }, [load]);
  function persist(next: Saved, success: string) {
    try {
      localStorage.setItem(storageKey, JSON.stringify(next));
      setIssue(null);
      setMessage(success);
    } catch {
      setIssue("write");
      setMessage(
        "브라우저 저장에 실패했습니다. 현재 화면에만 반영되며 새로고침하면 사라집니다. 저장을 다시 시도할 수 있습니다.",
      );
    }
  }
  function toggle(kind: keyof Saved, id: string) {
    if (!ready || issue === "corrupt" || issue === "read") return;
    const next = {
      ...saved,
      [kind]: saved[kind].includes(id)
        ? saved[kind].filter((x) => x !== id)
        : [...saved[kind], id],
    };
    setSaved(next);
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
      setSaved(blankSaved);
      setIssue(null);
      setMessage("손상된 스크랩을 초기화했습니다. 새로 저장할 수 있습니다.");
    } catch {
      setIssue("read");
      setMessage(
        "초기화하지 못했습니다. 브라우저 저장소 접근 설정을 확인해 주세요.",
      );
    }
  }
  function retry() {
    if (issue === "write")
      persist(saved, "현재 스크랩을 브라우저에 저장했습니다.");
    else load();
  }
  return (
    <Context.Provider
      value={{ saved, ready, message, issue, toggle, retry, reset }}
    >
      {children}
    </Context.Provider>
  );
}
export function useSaved() {
  const value = useContext(Context);
  if (!value) throw new Error("SavedProvider required");
  return value;
}
