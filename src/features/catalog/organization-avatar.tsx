"use client";

import Image from "next/image";
import { useState } from "react";
import type { Organization } from "./organizations";

export function OrganizationAvatar({ organization, large = false }: {
  organization: Organization;
  large?: boolean;
}) {
  const [failedLogo, setFailedLogo] = useState<string>();
  const logo = organization.logo !== failedLogo ? organization.logo : undefined;
  return (
    <span
      className={`avatar${large ? " large" : ""}${logo ? " organization-logo" : ""}`}
      style={{ background: logo ? "#fff" : organization.color }}
      aria-hidden="true"
    >
      {logo ? (
        <Image src={logo} alt="" width={44} height={44} unoptimized
          onError={() => setFailedLogo(logo)} />
      ) : organization.initial}
    </span>
  );
}
