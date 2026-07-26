# api-mimico
Api of the game Mimico

## Verification

Backend baseline verification requires JDK 17 or newer:

```bash
./mvnw test
```

The test resources use an in-memory H2 datasource, disable Flyway for the test context, and use fake local-only JWT/Redis settings. Do not use production secrets in test configuration.

Current baseline notes:

- Running `./mvnw test` with Java 11 fails before tests with `release version 17 not supported`; use JDK 17 or newer.
- With JDK 21, the suite reaches real tests after the baseline test resources and Mockito mock-maker configuration are applied.
- The current remaining failures are classified as legacy/spec-alignment follow-ups, not baseline infrastructure blockers:
  - `ReconnectionServiceTest.handleDisconnect_pausesMatchAndStoresInRedis` expects `3600L`, while accepted V1 specs require a 60-second reconnection window and current implementation uses `90L`.
  - `ReconnectionServiceTest.forfeitMatch_setsWinnerAndFinishesMatch` uses an incomplete fixture where `match.table` is null.
