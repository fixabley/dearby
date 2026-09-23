"use client";
import {
  createContext,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react";
import { blankSaved, parseSaved, storageKey, type Saved } from "./storage";
const Context = createContext<{
  saved: Saved;
  ready: boolean;
  message: string;
  toggle: (kind: keyof Saved, id: string) => void;
} | null>(null);
export function SavedProvider({ children }: { children: ReactNode }) {
  const [saved, setSaved] = useState(blankSaved),
    [ready, setReady] = useState(false),
    [message, setMessage] = useState("");
  useEffect(() => {
    function load() {
      try {
        setSaved(parseSaved(localStorage.getItem(storageKey)));
        setMessage("");
      } catch {
        setMessage(
          "저장 정보를 불러오지 못했습니다. 이번 화면에서 다시 저장할 수 있습니다.",
        );
      }
      setReady(true);
    }
    load();
    const sync = (e: StorageEvent) => {
      if (e.key === storageKey) load();
    };
    window.addEventListener("storage", sync);
    return () => window.removeEventListener("storage", sync);
  }, []);
  function toggle(kind: keyof Saved, id: string) {
    const next = {
      ...saved,
      [kind]: saved[kind].includes(id)
        ? saved[kind].filter((x) => x !== id)
        : [...saved[kind], id],
    };
    setSaved(next);
    try {
      localStorage.setItem(storageKey, JSON.stringify(next));
      setMessage(
        next[kind].includes(id)
          ? "스크랩했습니다. 이 브라우저에 저장됩니다."
          : "스크랩을 해제했습니다.",
      );
    } catch {
      setMessage(
        "브라우저 저장에 실패했습니다. 현재 화면에만 반영되며 새로고침하면 사라집니다.",
      );
    }
  }
  return (
    <Context.Provider value={{ saved, ready, message, toggle }}>
      {children}
    </Context.Provider>
  );
}
export function useSaved() {
  const value = useContext(Context);
  if (!value) throw new Error("SavedProvider required");
  return value;
}
export function SaveButton({
  kind,
  id,
  compact = false,
}: {
  kind: keyof Saved;
  id: string;
  compact?: boolean;
}) {
  const { saved, ready, toggle } = useSaved();
  const on = saved[kind].includes(id);
  return (
    <button
      className={`save-button ${on ? "is-saved" : ""} ${compact ? "compact" : ""}`}
      disabled={!ready}
      aria-pressed={on}
      aria-label={`${kind === "programs" ? "프로그램" : "조직"} 스크랩${on ? " 해제" : ""}`}
      onClick={() => toggle(kind, id)}
    >
      <span aria-hidden="true">{on ? "♥" : "♡"}</span>
      {!compact &&
        `${kind === "programs" ? "프로그램" : "조직"} ${on ? "저장됨" : "스크랩"}`}
    </button>
  );
}
