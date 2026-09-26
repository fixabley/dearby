# Native integration harness

Run from repository root after `apps/dearby-api` dependencies and build are ready:

```sh
NODE_ENV=test node scripts/native-integration.mjs
```

Use the API package's Node 24 LTS version. The command starts an actual API on a random loopback port with an isolated temporary SQLite database. It prints only the API origin, private test inbox directory and two permitted test account names. Production email/credentials are never used. Only `ios@example.test` and `android@example.test` can receive a test challenge; all other addresses fail closed.

After requesting a challenge from a native app, automation can read the corresponding private inbox JSON and populate the code field without printing its contents. Do not log or commit inbox contents or session tokens. This local sink proves neither actual email delivery nor operating HTTPS. Debug iOS can use the loopback origin; Android Emulator can use the same port on `10.0.2.2`. Clients append `/v1` themselves.

SIGINT/SIGTERM closes the server and removes its isolated DB/inbox. Screenshots and test reports must distinguish UI-driven flows, API-client tests and real device evidence. Production email and deployed links remain tracked in #42/#43.
