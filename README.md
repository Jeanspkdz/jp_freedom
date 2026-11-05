# JP Freedom

## Repository layout

- `apps/android`: standalone Android Gradle project. Run `./gradlew` from this directory.
- `apps/chrome-extension`: Chrome extension package and its policy code.
- `services/sync-api`: sync API package, Neon configuration, and migrations.
- `docs`: product-wide contracts and decisions.

Run `pnpm install` from the repository root. The root scripts dispatch to the
workspace packages; each package owns its dependencies and commands.
