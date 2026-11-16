# Repository instructions

## Package Manager

- Always use `pnpm` instead of `npm` for installing, updating, or removing dependencies.
- Use `pnpm install` instead of `npm install`.
- Use `pnpm run <script>` or `pnpm <script>` to execute package scripts.
- Use `pnpm dlx` instead of `npx` when appropriate.
- Prefer `pnpm-lock.yaml` for dependency locking.
- Do not switch package managers unless explicitly instructed by the user.

## Git Naming

- Write commit messages using Conventional Commits: `<type>(<scope>): <short description>` or `<type>: <short description>` when no existing scope fits. Use a standard type such as `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, or `revert`.
- Name branches with a semantic prefix and a short kebab-case description, such as `feat/app-selection`, `fix/session-lock`, or `docs/commit-conventions`. Use an existing project-specific branch convention when one is documented.

## Architecture

- Organize new application code by **feature first**, so a feature's UI, domain rules, and data access stay together. Prefer a cohesive feature module with a small public interface over adding unrelated files to global `ui`, `domain`, or `data` packages.
- Keep shared code only for behavior or concepts used by multiple features. A feature should depend on another feature through its public interface, not reach into its internal implementation.
- Apply this structure incrementally. When changing an existing feature, follow its current organization unless the task includes moving that feature; avoid broad package moves as incidental cleanup.
- Keep domain documentation aligned with feature ownership: use the root glossary for shared vocabulary and add context-level docs only when a feature has a distinct domain model.

## Android verification

- After installing an Android build, launch it on the target device and confirm the main screen opens without a crash before reporting success.
- When verifying uninstall behavior, first confirm the launch check passed, then uninstall the same package from the same target device and confirm Android reports success. Warn the user first if uninstalling would remove app data they asked to preserve.

## Agent skills

### Issue tracker

Issues are tracked in GitHub Issues for `Jeanspkdz/jp_freedom`. See `docs/agents/issue-tracker.md`.

### Triage labels

Use the five default triage labels. See `docs/agents/triage-labels.md`.

### Domain docs

Use the single-context glossary and ADR layout. See `docs/agents/domain.md`.
