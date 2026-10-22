/**
 * Sticky bottom action bar (contract "웹 리디자인" › 활동 상세·공개 명함): translucent with a blur and a top hairline, content width,
 * safe-area bottom padding. Pages leave room for it with bottom padding.
 */
export function BottomBar({ children }: { children: React.ReactNode }) {
  return (
    <div className="fixed inset-x-0 bottom-0 z-30 border-t border-separator bg-bar backdrop-blur-xl backdrop-saturate-200">
      <div className="mx-auto flex max-w-[720px] flex-col items-center gap-1.5 px-4 pt-3 pb-[max(12px,env(safe-area-inset-bottom))]">
        {children}
      </div>
    </div>
  );
}
