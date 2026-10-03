# Opt-in local API instrumentation

`LocalApiIntegrationTest` is test-only and skipped unless `localApi=true` is explicitly provided. Use an isolated actual Dearby backend with a private test mail sink, never production accounts. Configure debug with `-PdearbyApiUrl=http://10.0.2.2:PORT`; install APK and test APK. The default app has no URL/test user/data.

1. Run `LocalApiIntegrationTest#requestChallenge` with `-e localApi true`. This uses the product HTTP client to request the dedicated `android@example.test` challenge and saves the challenge ID privately.
2. Read the authorized host test inbox **programmatically without printing it**. Pipe only its code into `adb shell "run-as com.dearby.nativeapp sh -c 'cat > files/integration-otp'"`. Do not put the OTP in command text, logs, or Git.
3. Run `#authenticatedProfilePublicationAndImport` with the same opt-in. It uses actual product repositories and token vault, then deletes the private OTP file. Public test IDs are in private `files/integration-public-ids`; these IDs are not credentials.
4. With an authorized second test account, pass `-e recipientId <public-profile-id>` to `#deliverToOtherPlatformAndVerifyServerWallet`. The request is durably seeded then consumed by the product repository; exact HTTP replay must return the same receipt ID.
5. Once the other platform has sent back, run `#reciprocalAfterOtherPlatformSends`. It asserts actual server-derived reciprocal; running it beforehand correctly fails.

No inbox HTTP endpoint or OTP response is added. Tests are not a production login bypass. Results/evidence/limits are recorded in VERIFICATION.md. The temporary integration server is coordinator-owned; the worker does not shut it down.
