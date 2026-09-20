# iOS design simplification

## Display projections and internal exports

Removed the unused card applicationSummary/locationSummary/applicationPeriod and detail descriptionProvenance/applicationSummary/scheduleSummaries/applicationPeriod fields. Views still consume the existing application and schedule projections. NoticeModel, storage codecs, source/evidence fields, primary source URL and all cache/restore semantics are unchanged. NoticeViewModelTests retains actual application row and timeline assertions rather than testing a duplicate unused summary.

NoticeClassificationView, CalendarEventEditor and SettingsView remain in use inside their owning slices but no longer appear in public-api.json. Their public callers are NoticeCardBody/NoticePreviewLabel, CalendarExportPresentation and SettingsPresentation respectively. This removes only unnecessary cross-slice permission, not runtime components.

Validation results are recorded in the iOS role handoff; earlier test runs are not new evidence.

## Notice card detail action

NoticeDetailsButton is local to widgets/noticeCard/ui. It retains its separate descriptive file, the native SecondaryButton style, full-width icon label, accessibility identifier and the Page-owned route callback. No Feature owns state or policy for this simple action. The type is explicitly registered as pure UI; its former Feature public API entry is removed.

Validation: production architecture and strict lint are run for the move; the final Simulator smoke must verify details opening and unchanged save behavior.
