# Native five-tab evidence

Existing `Dearby_Calendar_Verify` AVD, Android 16, emulator-5554, 1080×2400, 420dpi. Images are actual UiAutomation captures of Compose screens, not reference images used as a screen background. Core activity photos and QR are local image resources; all controls/layout/type/timetables/cards are native Compose.

| Approved reference | Current evidence |
| --- | --- |
| Discovery / detail top, schedule, bottom / applied | `prototype-discovery.png`, `activity-detail-top.png`, `activity-detail-schedule.png`, `activity-detail-bottom.png`, `activity-applied.png` |
| Saved | `selected-saved.png` |
| Profile / guest | `selected-profile.png`, `selected-profile-guest.png` |
| QR show / new card / scan / share | `selected-qr-show.png`, `selected-qr-new-card.png`, `selected-qr-scan.png`, `selected-qr-share-menu.png` |
| Card editor / public card | `selected-card-editor.png`, `selected-shared-card.png` |
| Send picker | `selected-send-card-picker.png` |
| Wallet / reciprocal-only | `selected-wallet.png`, `selected-wallet-reciprocal-only.png` |
| Calendar overlap / zero overlap | `prototype-overlap.png`, `prototype-no-overlap.png` |
| Font scale 1.3 | `large-text-qr.png`, `large-text-card-editor.png`, `large-text-wallet.png`, `large-text-send.png` |

The card editor captures the initial hidden phone slash. Creation/edit tests exercise changes separately. New-card action creates an ID; the pencil action edits the selected ID. Card/wallet fixtures contain fictional people and example.com contacts. Native permission sheets, live camera, OS sharing, clipboard and file saving are intentionally absent.

See `build.log`, `ui-tests.log`, `large-text-ui-tests.log`, `fsd.log`, unit XML and `lint-results.xml` for execution evidence; `ponytail-review.md` covers complexity review. Verification scope/limitations and results are in `../../docs/VERIFICATION.md`.
