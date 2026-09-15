# Notice detail and calendar composition

NoticeDetailPage owns the List, navigation title and sheet presentation. The noticeDetail Widget owns NoticeDetailViewModel/State, combining NoticeRepository, OrganizationRepository and the single favorite owner. Notice-only application/schedule/place projections live in Entities; resolved saved-target identity lives in the saveOrganization Feature. Entity NoticeSourceSection retains quality messages, source link and footer.

The Widget composes application export, selected-day overlap and exact venue-map Features. CalendarExportPresentation owns editor preparation/dismissal/failure; VenueMapPresentation owns URL opening/failure; BusyCalendarLifecycleModifier owns attach/detach, scene transitions and EventKit change refresh. Features do not import sibling features or page/widget State.

The date mapper, calendar editor request, busy query/cancellation session, EventKit read provider and snapshot store are unchanged. CalendarPreferences is an identical move. Existing standalone/cache, calendar draft/sourceURL, busy consent/lifecycle/privacy, map failure and detail/timezone/timeline tests pass on the final branch. The [execution report](evidence/two-layer-composition/README.md) records the Simulator input limitation and does not claim real calendar edits or tab transitions.
