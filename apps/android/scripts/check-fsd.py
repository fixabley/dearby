#!/usr/bin/env python3
"""Small source boundary check, not a Kotlin parser or compiler-enforced slicing."""
import argparse
from pathlib import Path
import re

PREFIX = "io.fixabley.dearby"
ROOT = Path(__file__).resolve().parents[1] / "app/src/main/java/io/fixabley/dearby"
LAYERS = {name: i for i, name in enumerate(("app", "pages", "widgets", "features", "entities", "shared"))}
# Native slice entry points; kept in sync with ARCHITECTURE.md, not barrel files.
API = {
    "pages.discovery": {"ui.DiscoveryScreen"},
    "pages.favorites": {"ui.FavoritesScreen"},
    "pages.noticedetail": {"ui.NoticeDetailSheet", "model.NoticeDetailViewModel", "model.NoticeDetailState"},
    "widgets.noticecard": {"ui.NoticeCard", "model.NoticeCardState", "model.NoticeCardViewModel"},
    "widgets.favoriteorganizationcard": {"ui.FavoriteOrganizationCard"},
    "features.favoriteorganization": {
        "model.FavoritesState", "api.FavoriteStore", "api.SharedPreferencesFavoriteStore",
    },
    "features.addtocalendar": {"model.CalendarDraft", "model.applicationCalendarDraft", "model.phaseCalendarDraft"},
    "entities.notice": {"model.NoticeModel", "model.NoticeContext", "model.NoticeApplication", "model.NoticeLocation", "model.NoticePhase", "model.NoticeVenue", "model.VenueCoordinates", "model.NoticeSource", "model.NoticeEvidence", "api.NoticeSource", "api.InMemoryNoticeSource", "api.NoticeRepository"},
    "entities.organization": {"model.OrganizationModel", "api.OrganizationSource", "api.InMemoryOrganizationSource", "api.OrganizationRepository"},
    "entities.noticecatalog": {
        "model.NoticeApplication", "model.NoticePhase", "model.NoticeDetail", "model.NoticeScheduleDetail",
        "model.ResolvedOrganizationRole", "model.NoticeSource", "model.NoticeEvidence", "api.NoticeDetailRepository",
        "model.NoticeLocation", "model.NoticeVenue", "model.VenueCoordinates",
        "model.NoticeCatalog", "model.Notice", "model.Organization", "model.NoticeContext",
        "api.OrganizationSource", "api.InMemoryOrganizationSource", "api.OrganizationRepository",
        "api.CatalogProvider", "api.AssetCatalogProvider", "ui.NoticeClassification",
    },
}


def owner(name):
    parts = name.split(".")
    if parts[0] == "MainActivity":  # Keep the existing manifest component identity.
        return "app"
    if parts[0] in ("app", "shared"):
        return parts[0]
    key = ".".join(parts[:2])
    return key if key in API else None


def check_source(relative_path, text):
    expected_package = PREFIX + ("." + ".".join(relative_path.parts[:-1]) if len(relative_path.parts) > 1 else "")
    errors = []
    # Ignore ordinary comments/strings. This intentionally does not parse Kotlin interpolation.
    code = re.sub(r'/\*.*?\*/|//[^\n]*|""".*?"""|"(?:\\.|[^"\\])*"', "", text, flags=re.S)
    package = re.search(r"^package\s+([\w.]+)", code, re.M)
    if not package or package.group(1) != expected_package:
        errors.append("package does not match its directory")
    source = owner(".".join(relative_path.with_suffix("").parts))
    if source is None:
        return errors + ["unknown layer/slice"]
    layer = source.split(".")[0]
    code = re.sub(r"^package[^\n]*", "", code, flags=re.M)
    references = re.findall(r"\b" + re.escape(PREFIX) + r"\.([\w.*]+)", code)
    for ref in references:
        if ref.split(".")[0] in ("R", "BuildConfig"):
            continue
        target = owner(ref)
        if target is None:
            errors.append(f"unknown dependency: {ref}")
            continue
        target_layer = target.split(".")[0]
        if LAYERS[target_layer] < LAYERS[layer]:
            errors.append(f"upward dependency: {ref}")
        elif layer == target_layer and source != target:
            errors.append(f"same-layer cross-slice dependency: {ref}")
        if source != target and target in API:
            exports = [target + "." + entry for entry in API[target]]
            if not any(ref == entry or ref.startswith(entry + ".") for entry in exports):
                errors.append(f"non-entry-point dependency: {ref}")
        if layer in ("pages", "widgets") and "ui" in relative_path.parts and (
            (target_layer == "features" and ref != "features.addtocalendar.model.CalendarDraft") or ".api." in ref
        ):
            errors.append(f"UI must receive values/callbacks, not state or data providers: {ref}")
    if source == "pages.noticedetail" and any(ref in {"entities.noticecatalog.model.Notice", "entities.noticecatalog.model.NoticeCatalog"} for ref in references):
        errors.append("detail UI must receive NoticeDetail, not raw catalog/notice")
    if layer in ("pages", "widgets") and "ui" in relative_path.parts and re.search(
        r"\b(LocalContext|SharedPreferences|getSharedPreferences|AssetManager)\b", code
    ):
        errors.append("UI directly accesses Android context/storage")
    return errors


def self_test():
    cases = [
        ("pages/discovery/ui/Example.kt", "import io.fixabley.dearby.app.DearbyApp", False),
        ("pages/discovery/ui/Example.kt", "import io.fixabley.dearby.pages.noticedetail.ui.NoticeDetailSheet", False),
        ("widgets/noticecard/ui/Example.kt", "import io.fixabley.dearby.widgets.favoriteorganizationcard.ui.FavoriteOrganizationCard", False),
        ("entities/noticecatalog/model/Example.kt", "import io.fixabley.dearby.features.favoriteorganization.model.FavoritesState", False),
        ("widgets/noticecard/ui/Example.kt", "import io.fixabley.dearby.features.favoriteorganization.model.FavoritesState as State", False),
        ("widgets/noticecard/ui/Example.kt", "import android.content.SharedPreferences", False),
        ("app/Example.kt", "import io.fixabley.dearby.pages.noticedetail.ui.NoticeIdentity", False),
        ("pages/discovery/ui/Example.kt", "fun bad() = io.fixabley.dearby.pages.favorites.ui.FavoritesScreen()", False),
        ("app/Example.kt", "import io.fixabley.dearby.features.favoriteorganization.model.FavoritesState", True),
        ("widgets/noticecard/ui/Example.kt", "import io.fixabley.dearby.entities.noticecatalog.ui.NoticeClassification", True),
        ("shared/ui/Example.kt", "import io.fixabley.dearby.shared.ui.theme.DearbyTheme", True),
        ("pages/noticedetail/ui/Example.kt", "import io.fixabley.dearby.features.addtocalendar.model.CalendarDraft", True),
        ("pages/noticedetail/ui/Example.kt", "import io.fixabley.dearby.features.addtocalendar.model.applicationCalendarDraft", False),
        ("pages/noticedetail/ui/Example.kt", "import io.fixabley.dearby.entities.noticecatalog.model.Notice", False),
        ("pages/noticedetail/ui/Example.kt", "import io.fixabley.dearby.entities.noticecatalog.model.NoticeCatalog", False),
        ("pages/noticedetail/ui/Example.kt", "import io.fixabley.dearby.entities.noticecatalog.model.NoticeDetail", True),
    ]
    for filename, snippet, allowed in cases:
        path = Path(filename)
        package = PREFIX + "." + ".".join(path.parts[:-1])
        errors = check_source(path, f"package {package}\n{snippet}\n")
        assert (not errors) == allowed, (filename, snippet, errors)
    print(f"Boundary self-test: {len(cases)} cases passed (11 forbidden, 5 allowed)")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        self_test()
    failures = []
    files = sorted(ROOT.rglob("*.kt"))
    if not files:
        raise SystemExit("No production Kotlin sources found")
    for path in files:
        for error in check_source(path.relative_to(ROOT), path.read_text()):
            failures.append(f"{path.relative_to(ROOT)}: {error}")
    if failures:
        raise SystemExit("\n".join(failures))
    print(f"FSD boundaries: {len(files)} Kotlin files passed")
