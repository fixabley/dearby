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
    "widgets.notice.noticecard": {"NoticeCard", "NoticeCardState", "NoticeCardViewModel"},
    "widgets.organization.favoriteorganizationcard": {"FavoriteOrganizationCard", "FavoriteOrganizationCardState", "FavoriteNoticeState", "FavoriteOrganizationCardViewModel"},
    "features.favoriteorganization": {
        "model.FavoritesState", "api.FavoriteStore", "api.SharedPreferencesFavoriteStore",
    },
    "features.addtocalendar": {"model.CalendarDraft", "model.applicationCalendarDraft", "model.phaseCalendarDraft"},
    "entities.notice": {"model.NoticeModel", "model.NoticeContext", "model.NoticeApplication", "model.NoticeLocation", "model.NoticePhase", "model.NoticeVenue", "model.VenueCoordinates", "model.NoticeSource", "model.NoticeEvidence", "api.NoticeSource", "api.InMemoryNoticeSource", "api.NoticeRepository", "api.NoticeStorageCodec", "api.NoticeRecord", "api.NoticeDao", "api.RoomNoticeStore", "api.StoredNoticeSource", "ui.NoticeClassification"},
    "entities.organization": {"model.OrganizationModel", "api.OrganizationSource", "api.InMemoryOrganizationSource", "api.OrganizationRepository", "api.OrganizationRecord", "api.OrganizationDao", "api.RoomOrganizationStore", "api.StoredOrganizationSource"},

}


def owner(name):
    parts = name.split(".")
    if parts[0] == "MainActivity":  # Keep the existing manifest component identity.
        return "app"
    if parts[0] in ("app", "shared"):
        return parts[0]
    key = ".".join(parts[:3] if parts[0] == "widgets" else parts[:2])
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
    rendering = layer in ("pages", "widgets") and bool(re.search(r"@(?:androidx\.compose\.runtime\.)?Composable\b", code))
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
        if rendering and (
            (target_layer == "features" and ref != "features.addtocalendar.model.CalendarDraft") or ".api." in ref
        ):
            errors.append(f"UI must receive values/callbacks, not state or data providers: {ref}")
    if rendering and any(ref in {"entities.notice.model.NoticeModel", "entities.organization.model.OrganizationModel"} for ref in references):
        errors.append("rendering UI must receive State, not raw domain models")
    if rendering and re.search(
        r"\b(LocalContext|SharedPreferences|getSharedPreferences|AssetManager|Intent|startActivity|[A-Za-z]+ViewModel|[A-Za-z]+Repository)\b", code
    ):
        errors.append("rendering UI directly accesses ViewModel/repository or Android side effects")
    return errors


def self_test():
    API["widgets.notice.fixture"] = {"OtherCard"}
    cases = [
        ("pages/discovery/ui/Example.kt", "import io.fixabley.dearby.app.DearbyApp", False),
        ("pages/discovery/ui/Example.kt", "import io.fixabley.dearby.pages.noticedetail.ui.NoticeDetailSheet", False),
        ("widgets/notice/noticecard/Example.kt", "import io.fixabley.dearby.widgets.organization.favoriteorganizationcard.FavoriteOrganizationCard", False),
        ("entities/notice/model/Example.kt", "import io.fixabley.dearby.features.favoriteorganization.model.FavoritesState", False),
        ("widgets/notice/noticecard/Example.kt", "import io.fixabley.dearby.features.favoriteorganization.model.FavoritesState as State", False),
        ("widgets/notice/noticecard/Example.kt", "import android.content.SharedPreferences", False),
        ("app/Example.kt", "import io.fixabley.dearby.pages.noticedetail.ui.NoticeIdentity", False),
        ("pages/discovery/ui/Example.kt", "fun bad() = io.fixabley.dearby.pages.favorites.ui.FavoritesScreen()", False),
        ("app/Example.kt", "import io.fixabley.dearby.features.favoriteorganization.model.FavoritesState", True),
        ("widgets/notice/noticecard/Example.kt", "import io.fixabley.dearby.entities.notice.ui.NoticeClassification", True),
        ("entities/notice/model/Example.kt", "import io.fixabley.dearby.entities.organization.model.OrganizationModel", False),
        ("entities/organization/model/Example.kt", "import io.fixabley.dearby.entities.notice.model.NoticeModel", False),
        ("features/addtocalendar/model/Example.kt", "import io.fixabley.dearby.pages.noticedetail.model.NoticeDetailState", False),
        ("widgets/notice/noticecard/Example.kt", "import io.fixabley.dearby.entities.notice.api.NoticeRepository", True),
        ("widgets/notice/noticecard/Example.kt", "import io.fixabley.dearby.entities.organization.model.OrganizationModel", False),
        ("widgets/notice/noticecard/Example.kt", "import io.fixabley.dearby.widgets.notice.fixture.OtherCard", False),
        ("widgets/notice/noticecard/Example.kt", "val model: NoticeCardViewModel? = null", False),
        ("widgets/notice/noticecard/Example.kt", "import android.content.Intent", False),
        ("shared/ui/Example.kt", "import io.fixabley.dearby.shared.ui.theme.DearbyTheme", True),
        ("pages/noticedetail/ui/Example.kt", "import io.fixabley.dearby.features.addtocalendar.model.CalendarDraft", True),
        ("pages/noticedetail/ui/Example.kt", "import io.fixabley.dearby.features.addtocalendar.model.applicationCalendarDraft", False),
        ("pages/noticedetail/ui/Example.kt", "import io.fixabley.dearby.entities.notice.api.NoticeRepository", False),
        ("pages/noticedetail/ui/Example.kt", "import io.fixabley.dearby.entities.notice.model.NoticeModel", False),
        ("pages/noticedetail/ui/Example.kt", "import io.fixabley.dearby.pages.noticedetail.model.NoticeDetailState", True),
    ]
    for filename, snippet, allowed in cases:
        path = Path(filename)
        package = PREFIX + "." + ".".join(path.parts[:-1])
        if filename.startswith(("pages/", "widgets/")) and not (allowed and "NoticeRepository" in snippet):
            snippet += "\n@Composable fun Render() {}"
        errors = check_source(path, f"package {package}\n{snippet}\n")
        assert (not errors) == allowed, (filename, snippet, errors)
    del API["widgets.notice.fixture"]
    print(f"Boundary self-test: {len(cases)} cases passed (18 forbidden, 6 allowed)")


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
