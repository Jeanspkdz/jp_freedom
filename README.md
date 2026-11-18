# Tally

## Repository layout

- `apps/android`: standalone Android Gradle project. Run `./gradlew` from this directory.
- `apps/chrome-extension`: Chrome extension package and its policy code.
- `services/sync-api`: sync API package, Neon configuration, and migrations.
- `docs`: product-wide contracts and decisions.

Each project owns its build tools and dependencies. Run `pnpm install` from
`services/sync-api` for the API. Run extension commands from
`apps/chrome-extension`. The Android Gradle wrapper runs from `apps/android`.
