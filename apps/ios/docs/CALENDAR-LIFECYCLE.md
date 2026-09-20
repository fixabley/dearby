# Calendar lifetime ownership

CalendarPreferences owns persisted opt-in, the first prompt, permission requests and app foreground/background state. CalendarPreferencesLifecycleModifier in the Feature api segment bridges ScenePhase and EventKit notifications once at SettingsPresentation. It reports the initial scene phase: preferences assume foreground construction until that initial report; an initially background presentation cannot start authorization or detail work. Inactive permission dialogs keep their generation alive.

Background invalidates pending authorization, suspends attached detail queries and discards personal results. Store-change notifications cannot restart work until active. Active/notification authorization completion enables each attached detail once, without a preliminary resume/refresh followed by cancellation and another refresh.

BusyCalendarSession remains the detail-scoped selected-day query and ephemeral result owner. It checks read permission before/after each query and rejects cancelled/stale generations. It no longer asks for permission: unused consent/cancel/continue and resume APIs were removed. The detail lifecycle modifier only attaches/detaches; the global preferences keep weak references. SettingsViewModel still owns settings sheet presentation, without copying preferences.

`bash apps/ios/tests/run_busy_calendar.sh` exercises the production preferences path for background notifications, late authorization/query/permission responses, inactive dialogs, initial background, one query per notification/resume, cancellation counts and detach. The background-notification assertion failed against the old implementation before the fix. Model tests do not drive real OS permission dialogs or UI taps.
