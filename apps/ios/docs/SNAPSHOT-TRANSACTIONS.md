# Snapshot transaction boundary

`SwiftDataSnapshotStore.withSnapshot` stages snapshot metadata and both entity L2 caches on the MainActor without suspension. Source promotions call `persistCache`; inside this transaction they join its final explicit save. The caller receives its candidate only after that save succeeds. Outside a snapshot transaction, cache promotion saves immediately as before.

The caller must keep candidate repositories and presentation private until return. Candidate L1 entries may exist during staging; they are discarded on failure. Existing published repositories and presentation remain untouched until commit. A thrown read, composition or save error rolls back the context, preserving the last valid manifest and L2 records. Pending context changes and nested transactions are rejected before mutation.

This store owns disk metadata/transactions and does not need a Widget or favorites dependency for that work. It is synchronous bundled-mock composition; future asynchronous source/cancellation policies are outside this change.

`SnapshotTransactionChecks`, run by `tests/run_standalone.sh`, checks deferred promotion, one final save, read/save rollback, no-op digests, pending/reentrant rejection and replacement of both L2 slices. App presentation replacement and detail lifetime are separately tested at the composition boundary.
