"use client";
import { BrandSymbol } from "./brand-symbol";
import { Button } from "./button";
import { Icon } from "./icon";
import { Sheet, SheetContent, SheetDescription, SheetTitle } from "./sheet";

const share = (
  <span role="img" aria-label="공유" className="inline-flex align-[-2px] text-teal">
    <Icon name="share" size="inline" />
  </span>
);
const strong = (text: string) => <b className="font-semibold text-label">{text}</b>;

/** The steps for a browser that adds to the home screen from its own menu. */
export type ShortcutSteps = "ios-safari" | "ios-chrome" | "chrome" | "samsung";
const steps: Record<ShortcutSteps, React.ReactNode> = {
  "ios-safari": (
    <>
      Safari의 {share} 공유 버튼(보이지 않으면 ⋯ 메뉴 안)을 누른 뒤 {strong("홈 화면에 추가")}를 고르세요.
    </>
  ),
  "ios-chrome": (
    <>
      Chrome 주소창 오른쪽 {share} 공유 버튼을 누른 뒤 {strong("홈 화면에 추가")}를 고르세요. 홈 화면에서 목록이 비어 보이면
      명함을 Safari에서 저장해 주세요.
    </>
  ),
  chrome: <>Chrome 오른쪽 위 ⋮ 메뉴에서 {strong("홈 화면에 추가")}를 고르세요.</>,
  samsung: (
    <>
      삼성 인터넷 아래쪽 ≡ 메뉴에서 {strong("현재 페이지 추가")} › {strong("홈 화면")}을 고르세요.
    </>
  ),
};

/**
 * 홈 화면 바로가기 안내 as a non-modal iOS bottom sheet (contract "웹 리디자인"): the install button when the browser offers one
 * (`onInstall`), otherwise the browser's own menu steps. It does not take focus or block the page; tapping outside or Escape
 * closes it for now (`onClose`), `다시 보지 않기` dismisses it for good (`onDismiss`). Detection and hiding belong to the caller.
 */
export function InstallPrompt({
  steps: kind,
  onInstall,
  onDismiss,
  onClose,
}: {
  /** Shown when there is no install button. */
  steps?: ShortcutSteps;
  onInstall?: () => void;
  onDismiss: () => void;
  onClose: () => void;
}) {
  return (
    <Sheet open modal={false} onOpenChange={(open) => !open && onClose()}>
      <SheetContent overlay={false} onOpenAutoFocus={(event) => event.preventDefault()}>
        <div className="flex items-start gap-3">
          <BrandSymbol className="shrink-0 text-teal" size={44} />
          <div className="flex flex-col gap-1">
            <SheetTitle>홈 화면에서 바로 열기</SheetTitle>
            <SheetDescription>
              {onInstall || !kind ? "저장한 명함을 홈 화면에서 바로 열 수 있어요." : steps[kind]}
            </SheetDescription>
          </div>
        </div>
        <div className="mt-4 flex flex-col gap-1">
          {onInstall && (
            <Button size="lg" onClick={onInstall}>
              홈 화면에 추가
            </Button>
          )}
          <Button variant="plain" size="lg" onClick={onDismiss}>
            다시 보지 않기
          </Button>
        </div>
      </SheetContent>
    </Sheet>
  );
}
