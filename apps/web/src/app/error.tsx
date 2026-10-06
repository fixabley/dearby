"use client";
import { Failure } from "@/shared/ui/states";
export default function ErrorPage({ reset }: { reset: () => void }) {
  return <Failure message="화면을 불러오지 못했어요." retry={reset} />;
}
