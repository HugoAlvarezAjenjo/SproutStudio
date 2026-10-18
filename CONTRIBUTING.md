# Contributing

How the code is organised, how it is tested and how releases are cut. User documentation lives in the
[wiki](https://github.com/HugoAlvarezAjenjo/SproutStudio/wiki).

## Build and run

Requires JDK 21 (Amazon Corretto 21 works). Gradle comes with the wrapper.

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # macOS
./gradlew run                 # dev run (welcome screen)
./gradlew test                # unit, render and UI tests (UI screenshots land in build/ui-snapshots)
./gradlew createDistributable # the app, in build/compose/binaries/main/app/
```

The app icon is `src/main/resources/icon.png` (square, 1024×1024 ideally). Builds convert it to `.icns`
on macOS and `.ico` on Windows.

## Architecture

MVVM in layers; dependencies only point down. `docs/architecture.puml` draws the layers and
`docs/dependencies.puml` the main classes (`docs/dependencies.puml` is drawn by hand, so update it when
you move things).

```
src/main/kotlin/es/hugoalvarezajenjo/sproutstudio/
  app/                    main(), AppGraph (wires everything by hand), WindowManager
  core/                   dispatchers, LatestWinsQueue, ViewModel base, settings store
  domain/                 plain Kotlin: PlantUML language (completion, navigation, rename, formatting, folding), undo history, search, sessions, line diffs
  services/
    render/               PlantUML in-process behind RenderService: one queued worker, offline guard
    git/                  GitService/GitRepository on JGit, typed GitError
    files/                ProjectFiles: tree scan, create/rename/move/trash, typed FileError
    persistence/          DocumentStore (the only writer of diagram files) and autosave settings
  platform/               native dialogs, Trash, Finder, clipboard (DesktopPlatform)
  features/               one folder per feature: its ViewModel + its Composables
    workspace/            project window, WorkspaceViewModel, layout, tabs, status bar, autosave
    document/ editor/ preview/ problems/ explorer/ git/ templates/ palette/ quickpreview/ search/ settings/
  ui/designsystem/        theme, colours, shared components
src/test/                 same layout: unit, ViewModel, render, off-screen UI and architecture tests
samples/                  example diagrams
.github/workflows/        CI (tests) and tag-driven releases
```

`ArchitectureTest` enforces the rules, so a build fails if you break them:

- `domain` is plain Kotlin: no Compose, coroutines, JGit, Swing or other layers. The exceptions are a
  few value types (`TextRange`, `TextFieldValue`) and JGit's diff algorithm.
- `core` depends on nothing else in the app.
- `services` never touch the UI; `platform` and `ui/designsystem` know no feature.
- `features` don't reach up to `app`, and only `main()` reads the global graph (`AppGraph.default`).
- Every file is in a layer, with one public class (named after the file) or one public screen
  Composable per file.
- UI texts go through `tr(...)` (inside Composables) or `text(...)` (outside), never `stringResource`
  directly, so the language can change without a restart. `TranslationsTest` checks every string exists
  in `values/` and `values-es/`.

## Testing

`./gradlew test` runs everything. The kinds of test:

- **Unit and ViewModel tests** for `domain`, `services` and each feature's ViewModel.
- **Render tests** that run real PlantUML in-process.
- **UI tests** with `runComposeUiTest` (the v2 API, on `StandardTestDispatcher`).
- **Off-screen snapshots** (`UiSnapshotTest`, `GitSnapshotTest`, `DiagramClipTest`) that render screens
  to `build/ui-snapshots/*.png`. Look at them when you change the UI; the wiki's screenshots come from
  there.

Lessons from making the suite pass on CI, on both systems:

- **Off-screen scenes go through `Offscreen`.** It creates, renders and closes the
  `ImageComposeScene` on the Swing thread, with `coroutineContext = Dispatchers.Main`. Rendering from the
  test thread, or leaving effects on `Dispatchers.Unconfined`, causes "multithreaded access to
  SnapshotStateObserver" at random.
- **Don't open global mutable snapshots from ViewModels** (`Snapshot.withMutableSnapshot`): it flushes
  pending state notifications on the calling thread. To update several fields together, keep them in one
  state value.
- **An `UncaughtExceptionsBeforeTest` is not that test's fault.** `runTest` reports coroutine exceptions
  left behind by earlier tests; the real one is under "Suppressed".
- **No fixed waits for things that take real time.** Poll the condition with a timeout (`waitUntil`, or
  a loop up to 5 s). Fixed delays are fine only to check that something does *not* happen, with a wide
  margin.
- **Git test repositories come from `TestRepos`**, which turns off `core.autocrlf` so Windows checkouts
  match the expected text.
- **Write the failing test first.** For a bug, reproduce it, check the test fails with the user's error,
  then fix it.

When CI fails, the log prints the full stack, causes included. The HTML report is uploaded as the
`test-report-<os>` artifact of the run (kept 7 days).

## Releases

CI (`.github/workflows/ci.yml`) runs the tests on macOS and Windows on every push and pull request; it
publishes nothing.

To publish a version:

1. Move the "Unreleased" section of `CHANGELOG.md` to the new version and set the default version in
   `build.gradle.kts`.
2. Push `main` and wait for CI to pass on both systems.
3. Tag and push the tag:

   ```bash
   git tag v1.3.0
   git push origin v1.3.0
   ```

`.github/workflows/release.yml` then tests on macOS and Windows (a failure on one doesn't cancel the
other), builds `SproutStudio-1.3.0.dmg` (Apple Silicon) and `SproutStudio-1.3.0.msi` (x64) with the
version taken from the tag, and attaches both to one GitHub Release. Nothing is published unless both
builds pass. The release notes are that version's section of `CHANGELOG.md`, or GitHub's generated notes
if it has none.

Versions must be `MAJOR.MINOR.PATCH` with MAJOR ≥ 1 (a macOS packaging rule). Local builds use the
default version; override it with `-PappVersion=1.3.0`.

If the release fails before a Release is created, nobody has downloaded anything, so the tag can be
moved: fix, commit, then

```bash
git tag -f v1.3.0
git push origin :refs/tags/v1.3.0   # delete the old tag on GitHub
git push origin v1.3.0
```

Once a Release is published, don't move its tag; ship a patch version instead.
