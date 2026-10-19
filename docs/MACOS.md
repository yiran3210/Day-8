# Day 8 / 星期八 — macOS build notes

A **macOS build candidate** for the Java 25 / JavaFX / SQLite desktop app. Existing Windows packaging remains unchanged.

## Files added / changed

- `src/main/resources/icons/agenda-icon.icns` — macOS application icon, derived from the existing 1024px PNG.
- `scripts/package-macos.sh` — native macOS packaging script, creates a self-contained `.app` and ZIP.
- `.github/workflows/build-macos.yml` — macOS Apple Silicon and Intel CI jobs.
- `Database.java` — fixes launch-override precedence; uses a macOS user-data directory by default.

**Default macOS data location:** `~/Library/Application Support/Day 8/agenda.db`.
Use **Window → Data Location...** to select another folder; this takes effect after restarting. The Windows fallback for an existing `F:/AgendaData/agenda.db` is retained **only on Windows**. Windows and Mac databases **do not automatically synchronize**.

## Build on GitHub Actions (no Mac required)

1. Start from the latest stable source, create a branch: `git switch -c feature/macos-support`.
2. Copy the added/modified files into your project, and commit/push:

   ```powershell
   git add .github/workflows/build-macos.yml scripts/package-macos.sh src/main/resources/icons/agenda-icon.icns src/main/java/dev/personalagenda/data/Database.java docs/MACOS.md
   git commit -m "build: support macOS Apple Silicon and Intel app images"
   git push -u origin feature/macos-support
   ```

3. Open **GitHub → Actions → Build Day 8 for macOS**. Pushing this branch triggers a run automatically; you do not need the workflow on `main` initially for the `push` trigger.
4. Wait until both `macOS arm64` and `macOS x64` jobs are green. Under the workflow run's **Artifacts**, download `Day8-macos-arm64` and/or `Day8-macos-x64`.
5. GitHub Actions artifact downloads contain the generated `Day8-v0.2.0-macos-*.zip` files. Extract the inner application archive on the corresponding Mac to obtain `Day 8.app`.

If Actions is disabled for the repository, enable it in **Settings → Actions → General**. Mac hosted runner minutes may be billed depending on repository visibility and account allowances.

## Build locally on a Mac

Requires **JDK 25 with jmods**, Maven, Python 3 (for reading the Maven version), and Xcode command-line utilities for `jpackage` tooling.

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
bash ./scripts/package-macos.sh
```

The build selects the JavaFX classifier automatically: **`mac-aarch64` for Apple Silicon**, **`mac` for Intel**. Build on each architecture; these are not universal binaries. The script checks for the native graphics JAR before packaging and uses macOS `ditto` to preserve the `.app` structure.

## First launch and limitations

- **Not signed / not notarized.** This is a testable build, not a frictionless signed Mac release. Apple's Gatekeeper may block it. Use **System Settings → Privacy & Security → Open Anyway** after attempting to open the app, if you trust the source. Don't disable Gatekeeper globally.
- **No GUI validation yet.** GitHub Actions verifies compilation and packaging, but not interactive JavaFX functionality. On a real Mac, test Tasks/Steps, Day Plan, Mini Widget, Focus Start/Pause/Resume/Stop, Data Location, and Insights. Check that the Dock Quit action doesn't leave unexpected open sessions (unfinished sessions should be recovered).
- **No data files are bundled.** An empty Mac database is created on first run. If you intentionally migrate an existing database, close Day 8 and back it up first; ensure any SQLite WAL transactions are checkpointed before copying.
- **Do not move the existing `v0.2.0` Git tag.** This package changes cross-platform behavior. Verify the builds on the feature branch, then merge and consider a subsequent release, e.g. `v0.2.1`, for officially published macOS files.
