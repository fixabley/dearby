"use client";
import { BrandSymbol } from "./brand-symbol";
import { Button } from "./button";
import { Icon } from "./icon";
import { Sheet, SheetContent, SheetDescription, SheetTitle } from "./sheet";

/**
 * 홈 화면 웹앱 설치 안내 as a non-modal iOS bottom sheet (contract "웹 리디자인"): Android shows the install button when the
 * browser offers one, iOS Safari the Share → 홈 화면에 추가 steps. It does not take focus or block the page; tapping outside or
 * Escape closes it for now (`onClose`), `나중에` dismisses it for good (`onDismiss`). Platform detection and hiding belong to the caller.
 */
export function InstallPrompt({
  platform,
  onInstall,
  onDismiss,
  onClose,
  separateStorage = false,
}: {
  platform: "android" | "ios";
  onInstall?: () => void;
  onDismiss: () => void;
  onClose: () => void;
  /** iOS 홈 화면 앱이 Safari와 쿠키를 따로 쓰는 것이 확인되면 true. */
  separateStorage?: boolean;
}) {
  return (
    <Sheet open modal={false} onOpenChange={(open) => !open && onClose()}>
      <SheetContent overlay={false} onOpenAutoFocus={(event) => event.preventDefault()}>
        <div className="flex items-start gap-3">
          <BrandSymbol className="shrink-0 text-teal" size={44} />
          <div className="flex flex-col gap-1">
            <SheetTitle>홈 화면에서 바로 열기</SheetTitle>
            {platform === "android" ? (
              <SheetDescription>저장한 명함을 홈 화면에서 바로 열 수 있어요.</SheetDescription>
            ) : (
              <SheetDescription>
                Safari의{" "}
                <span role="img" aria-label="공유" className="inline-flex align-[-2px] text-teal">
                  <Icon name="share" size="inline" />
                </span>{" "}
                공유 버튼(보이지 않으면 ⋯ 메뉴 안)을 누른 뒤 <b className="font-semibold text-label">홈 화면에 추가</b>를 고르세요.
                {separateStorage && <span className="mt-1 block">홈 화면 앱은 Safari와 따로 저장돼요.</span>}
              </SheetDescription>
            )}
          </div>
        </div>
        <div className="mt-4 flex flex-col gap-1">
          {platform === "android" && onInstall && (
            <Button size="lg" onClick={onInstall}>
              홈 화면에 추가
            </Button>
          )}
          <Button variant="plain" size="lg" onClick={onDismiss}>
            나중에
          </Button>
        </div>
      </SheetContent>
    </Sheet>
  );
}
