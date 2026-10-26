import Image from "next/image";

/**
 * Sign-in lockup (contract "패스키 로그인" › 앱 동작, 2026-10-25): the wordmark with the catchphrase "대외활동, 매일 맞춤으로" as one image,
 * 96px tall at any text size, light and dark variants (`shared/assets/brand/sign-in-lockup-{light,dark}.png`, 1240×480, copied to
 * `public/brand`). The accessible name is the phrase the image carries.
 */
export function BrandLockup({ className }: { className?: string }) {
  const alt = "Dearby, 대외활동, 매일 맞춤으로";
  return (
    <span className={className}>
      <Image src="/brand/sign-in-lockup-light.png" alt={alt} width={248} height={96} priority className="dark:hidden" />
      <Image src="/brand/sign-in-lockup-dark.png" alt={alt} width={248} height={96} priority className="hidden dark:block" />
    </span>
  );
}
