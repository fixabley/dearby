"use client";
import { useState, type ReactNode } from "react";
import { type StatusGroup, STATUS_GROUPS, STATUS_GROUP_IDS } from "@/lib/models";
import { categoryLabels, kindCategories } from "@/lib/category";
import { Button } from "@/shared/ui/button";
import { CategoryIcon } from "@/shared/ui/category-icon";
import { Icon } from "@/shared/ui/icon";
import { Sheet, SheetContent, SheetTitle } from "@/shared/ui/sheet";

export type DiscoveryFilters = { groups: StatusGroup[]; categories: string[] };

/**
 * 발견 필터 (contract "필터 시트" 2026-10-26): one `필터` button (the applied count beside it, `필터 2`) that opens the sheet, then the applied
 * filters as chips, each × removing that one. The default (`지원 가능` alone, every 종류) shows no chip.
 */
export function DiscoveryFilterBar({
  kind,
  groups,
  categories,
  onApply,
  className,
}: DiscoveryFilters & { kind: "program" | "event"; onApply: (next: DiscoveryFilters) => void; className?: string }) {
  const [open, setOpen] = useState(false);
  const chosenGroups = groups.length === 1 && groups[0] === "available" ? [] : groups;
  const count = chosenGroups.length + categories.length;
  return (
    <div className={className}>
      <div className="-mx-4 flex items-center gap-2 overflow-x-auto px-4 [scrollbar-width:none]">
        <Button variant="tinted" size="sm" className="rounded-full" aria-haspopup="dialog" aria-expanded={open} onClick={() => setOpen(true)}>
          <Icon name="filter" size="inline" />
          {count ? `필터 ${count}` : "필터"}
        </Button>
        {chosenGroups.map((group) => (
          <Chip key={group} label={STATUS_GROUPS[group].label} onRemove={() => onApply({ groups: groups.filter((g) => g !== group), categories })} />
        ))}
        {categories.map((code) => (
          <Chip
            key={code}
            label={categoryLabels[code]}
            icon={<CategoryIcon code={code} />}
            onRemove={() => onApply({ groups, categories: categories.filter((c) => c !== code) })}
          />
        ))}
      </div>
      <Sheet open={open} onOpenChange={setOpen}>
        <SheetContent aria-describedby={undefined}>
          <FilterSheet
            kind={kind}
            initial={{ groups, categories }}
            onApply={(next) => {
              onApply(next);
              setOpen(false);
            }}
          />
        </SheetContent>
      </Sheet>
    </div>
  );
}

/** One applied filter: a mint capsule with its name and a × button. */
function Chip({ label, icon, onRemove }: { label: string; icon?: ReactNode; onRemove: () => void }) {
  return (
    <span className="inline-flex h-9 shrink-0 items-center gap-1 rounded-full bg-mint pl-3 text-subheadline font-semibold text-teal">
      {icon && <span className="flex">{icon}</span>}
      {label}
      <button type="button" aria-label={`${label} 필터 지우기`} className="grid size-9 place-items-center rounded-full active:opacity-70" onClick={onRemove}>
        <Icon name="close" size="inline" />
      </button>
    </span>
  );
}

/**
 * The sheet's body: native checkboxes in two labelled groups — `활동 상태` and, on the 프로그램 side only, `종류` (the 이벤트 side offers
 * none, `kindCategories`). The draft lives here (the sheet unmounts it when closed) and reaches the page only through `적용`; `초기화` puts
 * the draft back to the default without applying (the page's `applyFilters` turns no 묶음 into `지원 가능`).
 */
function FilterSheet({ kind, initial, onApply }: { kind: "program" | "event"; initial: DiscoveryFilters; onApply: (next: DiscoveryFilters) => void }) {
  const [groups, setGroups] = useState(initial.groups);
  const [categories, setCategories] = useState(initial.categories);
  const codes = kindCategories(kind);
  return (
    <>
      <SheetTitle className="text-center">필터</SheetTitle>
      <div className="mt-3 flex max-h-[60vh] flex-col gap-4 overflow-y-auto">
        <fieldset className="flex flex-col rounded-[14px] bg-fill/60">
          <legend className="mb-1.5 px-1 text-footnote font-semibold text-secondary-label">활동 상태</legend>
          {STATUS_GROUP_IDS.map((id) => (
            <Option
              key={id}
              label={STATUS_GROUPS[id].label}
              checked={groups.includes(id)}
              onChange={(on) => setGroups(STATUS_GROUP_IDS.filter((g) => (g === id ? on : groups.includes(g))))}
            />
          ))}
        </fieldset>
        {codes.length > 0 && (
          <fieldset className="flex flex-col rounded-[14px] bg-fill/60">
            <legend className="mb-1.5 px-1 text-footnote font-semibold text-secondary-label">종류</legend>
            {codes.map((code) => (
              <Option
                key={code}
                label={categoryLabels[code]}
                icon={<CategoryIcon code={code} />}
                checked={categories.includes(code)}
                onChange={(on) => setCategories(codes.filter((c) => (c === code ? on : categories.includes(c))))}
              />
            ))}
          </fieldset>
        )}
      </div>
      <div className="mt-4 flex gap-3">
        <Button
          variant="tinted"
          size="lg"
          onClick={() => {
            setGroups(["available"]);
            setCategories([]);
          }}
        >
          초기화
        </Button>
        <Button size="lg" onClick={() => onApply({ groups, categories })}>
          적용
        </Button>
      </div>
    </>
  );
}

function Option({ label, icon, checked, onChange }: { label: string; icon?: ReactNode; checked: boolean; onChange: (on: boolean) => void }) {
  return (
    <label className="flex min-h-11 cursor-pointer items-center gap-3 px-4">
      <input type="checkbox" className="size-4 accent-teal-fill" checked={checked} onChange={(event) => onChange(event.target.checked)} />
      {icon && <span className="flex text-secondary-label">{icon}</span>}
      <span className="text-body">{label}</span>
    </label>
  );
}
