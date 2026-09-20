# iOS design simplification

## Display projections and internal exports

Removed the unused card applicationSummary/locationSummary/applicationPeriod and detail descriptionProvenance/applicationSummary/scheduleSummaries/applicationPeriod fields. Views still consume the existing application and schedule projections. NoticeModel, storage codecs, source/evidence fields, primary source URL and all cache/restore semantics are unchanged. NoticeViewModelTests retains actual application row and timeline assertions rather than testing a duplicate unused summary.

NoticeClassificationView, CalendarEventEditor and SettingsView remain in use inside their owning slices but no longer appear in public-api.json. Their public callers are NoticeCardBody/NoticePreviewLabel, CalendarExportPresentation and SettingsPresentation respectively. This removes only unnecessary cross-slice permission, not runtime components.

Validation results are recorded in the iOS role handoff; earlier test runs are not new evidence.
