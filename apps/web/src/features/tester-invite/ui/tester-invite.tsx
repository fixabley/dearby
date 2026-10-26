"use client";
import { useState } from "react";
import { track } from "@/shared/lib/analytics";
import { Button } from "@/shared/ui/button";
import { Sheet, SheetClose, SheetContent, SheetDescription, SheetTitle } from "@/shared/ui/sheet";
import {
  androidOffered,
  iosOffered,
  offered,
  PLAY_GROUP_URL,
  PLAY_TEST_URL,
  TESTFLIGHT_URL,
  type TesterPlatform,
  type TesterTarget,
  useTesterPlatform,
} from "../model/links";

const label = "비공개 테스트로 앱 받기";
const pitch = "지금은 비공개 테스트 중이에요. 앱을 설치하면 명함을 계정에 보관하고 맞교환할 수 있어요.";

/**
 * 비공개 테스트 초대 (contract "명함 교환" › 받는 사람(웹)): someone who opened a card QR without the app is pointed to the test
 * build. iOS goes straight to TestFlight (same tab, so Safari hands it to the TestFlight app); Android and desktop open a sheet
 * with the steps. `card` is the caption + button under the save CTAs; `line` is the one-liner on `/saved` and in the sign-in
 * sheet. Nothing renders on the server, before the platform is known, or without a link for this platform.
 */
export function TesterInvite({ variant }: { variant: "card" | "line" }) {
  const platform = useTesterPlatform();
  const [open, setOpen] = useState(false);
  if (!platform || !offered(platform)) return null;
  const direct = platform === "ios";
  const button =
    direct ? (
      <Button asChild variant="plain" size="sm">
        <a href={TESTFLIGHT_URL} onClick={() => tap(platform, "testflight")}>
          {label}
        </a>
      </Button>
    ) : (
      <Button variant="plain" size="sm" onClick={() => setOpen(true)}>
        {label}
      </Button>
    );
  return (
    <>
      {variant === "card" ? (
        <div className="flex flex-col items-center">
          <p className="text-center text-caption break-keep text-secondary-label">{pitch}</p>
          {button}
        </div>
      ) : (
        <p className="flex flex-wrap items-center justify-center gap-x-1 text-footnote text-secondary-label">
          아직 앱이 없다면
          {button}
        </p>
      )}
      {!direct && (
        <Sheet open={open} onOpenChange={setOpen}>
          <SheetContent>
            <div className="grid grid-cols-[1fr_auto_1fr] items-center">
              <span />
              <SheetTitle className="text-center">{label}</SheetTitle>
              <SheetClose className="-mr-2 inline-flex min-h-11 items-center justify-self-end px-2 text-body text-teal">닫기</SheetClose>
            </div>
            <SheetDescription className="mt-2 text-center break-keep">{pitch}</SheetDescription>
            <div className="mt-4 flex flex-col gap-5">
              {platform === "desktop" && iosOffered && (
                <Steps title="iPhone">
                  <Step href={TESTFLIGHT_URL!} onTap={() => tap(platform, "testflight")}>
                    TestFlight에서 받기
                  </Step>
                </Steps>
              )}
              {androidOffered && (
                <Steps title={platform === "desktop" ? "Android" : undefined} footer="가입 뒤 반영까지 몇 분 걸릴 수 있어요.">
                  {PLAY_GROUP_URL && (
                    <Step href={PLAY_GROUP_URL} onTap={() => tap(platform, "play_group")}>
                      Google 그룹 가입
                    </Step>
                  )}
                  <Step href={PLAY_TEST_URL!} onTap={() => tap(platform, "play_test")}>
                    Play에서 테스트 참여·설치
                  </Step>
                </Steps>
              )}
            </div>
          </SheetContent>
        </Sheet>
      )}
    </>
  );
}

function tap(platform: TesterPlatform, target: TesterTarget) {
  track("tester_cta_tap", { platform, target });
}

/** Numbered steps, each a full-width tinted link button. */
function Steps({ title, footer, children }: { title?: string; footer?: string; children: React.ReactNode }) {
  return (
    <section className="flex flex-col gap-2">
      {title && <h3 className="px-1 text-footnote font-semibold text-secondary-label">{title}</h3>}
      <ol className="flex flex-col gap-2 [counter-reset:step]">{children}</ol>
      {footer && <p className="px-1 text-footnote text-secondary-label">{footer}</p>}
    </section>
  );
}

function Step({ href, onTap, children }: { href: string; onTap: () => void; children: React.ReactNode }) {
  return (
    <li className="flex items-center gap-3 [counter-increment:step]">
      <span
        aria-hidden="true"
        className="grid size-7 shrink-0 place-items-center rounded-full bg-mint text-footnote font-semibold text-teal before:content-[counter(step)]"
      />
      <Button asChild variant="tinted" size="lg" className="flex-1">
        <a href={href} target="_blank" rel="noopener" onClick={onTap}>
          {children}
        </a>
      </Button>
    </li>
  );
}
