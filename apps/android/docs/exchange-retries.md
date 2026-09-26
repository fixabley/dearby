# Delivery retries and local IDs

2026-09-27: pending outgoing request bodies are stored in Room before HTTP. A repeated attempt with the same sender/card/recipient/context reuses its requestId even after repository recreation. Only a well-formed server success removes the pending record. UI reciprocal stays server-derived. Changing the request content starts a new exchange; a previous ambiguous exchange may still have been delivered, and the UI never declares it failed delivery or success without confirmation.

JVM `ambiguousDeliveryRetryKeepsRequestIdAfterRepositoryRecreation` runs a real loopback HTTP server: initial 503, second 201, byte-identical request body, pending record retained then removed. Other import regression tests cover missing/conflicting results, only-selected success removal and preserving failure/nonselection IDs. AUTH 401 now returns to login and clears in-memory account projections; login/logout and all mutation requests remain serialized.

App restart test uses repository recreation and a retained DAO; the separate actual Room reopen instrumentation proves on-disk ID preservation. This is not evidence of production backend delivery or two-account E2E.

Follow-up review fixed the A-ambiguous/B/A case: pending keys now include a SHA-256 fingerprint of the immutable card/recipient/context intent in addition to account ID. Sending B cannot replace A's unresolved request. Profile-cache clearing on logout/login leaves account-scoped unresolved intents intact for safe reauthentication retries; no other account can see or use them. A loopback A503→B201→A201 regression verifies byte-identical A requests across repository recreation and profile-cache clear.
