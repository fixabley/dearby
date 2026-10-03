# Ponytail review — 2026-10-04

Reviewed the native five-tab shell, DemoViewModel, fixed fixtures, card/profile/QR/wallet pages, shared CardContent/CardStack and capture helper. No provider/repository/service wrapper, general navigation engine, extra dependency, empty layer or speculative persistence was introduced. Card rendering is reused across wallet, send and preview; generic timeline/contact/header rendering is shared across screens. The small local route state is limited to the three actual card destinations.

Lean already. Ship.

Separate correctness/accessibility pass: same-ID edit and new creation are distinct; save deduplication and reciprocal groups are covered; wallet index is bounded during group changes; hidden contacts have a slash plus checkbox semantics; scrollable forms keep controls reachable; QR/scanning/sharing are explicit demos. Final build, tests, font-scale check and screenshots are documented in VERIFICATION.md.
