<h1 align=center>SproutStudio</h1>

<p align=center>A friendly, fully offline PlantUML studio</p>

<p align=center>
  <a href="https://github.com/HugoAlvarezAjenjo/SproutStudio/actions/workflows/ci.yml"><img alt="CI" src="https://github.com/HugoAlvarezAjenjo/SproutStudio/actions/workflows/ci.yml/badge.svg"></a>
  <a href="https://github.com/HugoAlvarezAjenjo/SproutStudio/actions/workflows/release.yml"><img alt="Release" src="https://github.com/HugoAlvarezAjenjo/SproutStudio/actions/workflows/release.yml/badge.svg"></a>
  <a href="https://github.com/HugoAlvarezAjenjo/SproutStudio/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/HugoAlvarezAjenjo/SproutStudio?sort=semver"></a>
</p>

<p align=center>
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Compose Multiplatform" src="https://img.shields.io/badge/Compose_for_Desktop-1.12-4285F4?logo=jetpackcompose&logoColor=white">
  <img alt="JDK 21" src="https://img.shields.io/badge/JDK-21-ED8B00?logo=openjdk&logoColor=white">
  <img alt="Gradle" src="https://img.shields.io/badge/Gradle-9-02303A?logo=gradle&logoColor=white">
  <img alt="PlantUML" src="https://img.shields.io/badge/PlantUML-1.2026.8-A7C83B">
  <img alt="JGit" src="https://img.shields.io/badge/JGit-7.8-F05032?logo=git&logoColor=white">
  <img alt="macOS" src="https://img.shields.io/badge/platform-macOS-000000?logo=apple&logoColor=white">
</p>

## Features

- **Quick Preview**: double-click a `.puml` in Finder and see the diagram instantly. It refreshes by
  itself when the file changes, even if you edit it elsewhere.
- **Project mode**: open a folder and get a file tree, tabs, a smart editor and a live preview, as
  editor only, editor + preview, or preview only.
- **Smart editor**: syntax highlighting, completion that knows the diagram type, your own participants,
  `!theme` names and colours, smart indent, code folding, find & replace with regex, and a command palette
  (Shift Shift) with every action.
- **Templates**: a gallery (⇧⌘N) and a side panel with ready-made UML, C4, mindmap, Gantt, JSON and
  wireframe diagrams, each with a live thumbnail.
- **Problems panel**: every PlantUML error in the file, across all diagrams, marked in red in the editor;
  click one to jump to it.
- **Preview**: zoom and pan, multi-diagram files, an optional dark diagram independent of the editor theme,
  export to SVG/PNG or copy to the clipboard.
- **Git built in** (local only): change bars in the gutter with per-block rollback, coloured file tree,
  branches, a Commit panel with amend, undo and stash, file history, and a diagram-vs-HEAD comparison.
- **100% offline**: PlantUML runs inside the app with its pure-Java layout engine (Smetana). No Graphviz,
  no server, no network calls. Remote `!include`s are refused.
- **Self-contained**: the app ships its own Java runtime and Git library; nothing else to install.

## Develop

Requires JDK 21 (Amazon Corretto 21 works). Gradle comes with the wrapper.

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew run                 # dev run (welcome screen)
./gradlew test                # unit, render and UI tests (UI screenshots land in build/ui-snapshots)
```

Put a square PNG logo at `src/main/resources/icon.png` (1024×1024 ideally) to use it as the window, Dock
and `.app` icon; it is converted to `.icns` on macOS builds.

## Install it as a Mac app (enables double-click in Finder)

```bash
./gradlew createDistributable
cp -R build/compose/binaries/main/app/SproutStudio.app /Applications/
```

Then in Finder: right-click any `.puml` → Get Info → "Open with: SproutStudio" → "Change All…".
The app is unsigned, so the first launch needs right-click → Open.

For a `.dmg` installer: `./gradlew packageDmg`.

## Releases

CI (`.github/workflows/ci.yml`) runs the tests on every push and pull request; it publishes nothing.
To publish a version, push a tag:

```bash
git tag v1.2.0
git push origin v1.2.0
```

`.github/workflows/release.yml` then tests, builds `SproutStudio-1.2.0.dmg` (version taken from the
tag) and attaches it to a GitHub Release. Versions must be `MAJOR.MINOR.PATCH` with MAJOR ≥ 1 (macOS
rule). Local builds default to 1.0.0; override with `-PappVersion=1.2.0`. Both workflows run on
Apple Silicon runners, so the `.dmg` is native arm64.

Installing the downloaded, unsigned app on another Mac: drag it to Applications, then right-click →
Open the first time (or `xattr -dr com.apple.quarantine /Applications/SproutStudio.app`). Nothing else
needs to be installed: the app ships its own Java runtime.

## Editor tricks

| Keys | What it does |
|---|---|
| typing | suggestions for the current diagram type, your own participants/classes, `!theme` names, and colours after `#` |
| ⌃Space | show suggestions now |
| `@st` + ⏎ | pick a ready-made diagram template |
| left stripe (grid icon) | the Templates panel — the same catalogue as a narrow side list, click a row to insert |
| ⇧⌘N | the template gallery — ready-made diagrams and blocks with live thumbnails (also File → New from Template…) |
| ⏎ | smart indent (`alt`, `loop`, `if`, `{` … indent; `end`, `else`, `}` snap back) |
| ⇥ / ⇧⇥ | indent / outdent lines |
| ⌘/ | comment / uncomment lines |
| ⌘D | duplicate line |
| ⌘F, ⌘R | find, find & replace (Cc match case, W whole words, .* regex with `$1` groups) |
| ⏎ / ⇧⏎ (or ⌘G / ⇧⌘G) | next / previous match; Esc closes the bar |
| ⇧⌘P, ⇧⌘A, Shift Shift | command palette: every action, templates, the project's diagrams and recent projects |
| ⌘-, ⌘= / ⇧⌘-, ⇧⌘= | fold / unfold the block at the caret / all blocks (or click the ▾ ▸ chevrons in the gutter) |
| ⌘S, ⇧⌘S | save, save as (autosave is on: File → Save Automatically) |
| ⌘N, ⌘O, ⇧⌘O | new diagram, open folder, open diagram |
| ⌘P, ⌘1 | show / hide the preview, the project panel |
| ⌘6 | show / hide the Problems panel |
| ⌘0, ⌘K | show / hide the Commit panel, jump to the commit message |
| ⌥⌘Z | roll back the changed block at the caret to the last commit |
| ⌥⌘1 / ⌥⌘2 / ⌥⌘3 | editor only / editor and preview / preview only (also the three buttons at the right of the tab bar) |
| ⌥⌘Y | refresh the file tree |

Preview: mouse wheel to zoom at the pointer, drag with the left or middle button to pan (⇧+wheel or a
sideways trackpad swipe also pans), double-click to fit. Export to SVG or PNG, or copy the picture to the
clipboard (📋) to paste straight into a doc.

The sun/moon button in the preview toolbar (or View → Dark Diagram Preview) recolours the diagram
dark. It is independent of the editor theme, and exports/copies always keep the original colours.

## Problems

A Problems panel at the bottom of the editor (⌘6, View → Show Problems, or click the problem count
in the status bar) lists every error PlantUML finds: across all diagram blocks in the file, and every
bad line within a block, not just the first one PlantUML stops at. The same lines are marked in red in
the editor. Each row shows the message and where it is ("Diagram 2 · line 7", or just "line 7" in a
single-diagram file); clicking it jumps to that line and switches the preview to that diagram. The
header bar always shows "No problems" or the error count; the list stays collapsed until you open it,
and remembers its state.

## Git

Built in (JGit, no `git` install needed), local only: nothing is ever pushed.

- Gutter bars against the last commit: green added, blue modified, grey wedge where lines were deleted.
  Click a bar for an inline diff of just that block, with Rollback (puts it back in the editor; Undo in
  the toast). ⌥⌘Z (Git → Rollback Lines) does the same for the block at the caret.
- Project tree colours: blue modified, green added, red unversioned, olive ignored.
- Branch button in the status bar: switch branch, New Branch… (from the current commit), delete a merged branch.
  Unsaved editor text is saved first; if a switch would overwrite uncommitted changes it is refused and nothing is lost.
- Commit panel (⌘0 or the second stripe button): tick the files, write a message, Commit (⌘⏎).
  Right-click a file for Rollback (restores the last committed version; a new file only goes back to unversioned).
  No repository yet? The panel offers "Create Git Repository", optionally with a starter `.gitignore`.
- Git → Compare Diagram with HEAD (or double-click a modified file in the Commit panel):
  the committed diagram and the current one side by side, sharing zoom and pan.
  The same window has a Text tab: a unified diff with old/new line numbers (Git → Show Diff with HEAD).
- Amend: tick "Amend" next to Commit to replace the last commit (starts from its message; no files = just reword).
- Undo Last Commit (the ↶ next to "Last: …", or the Git menu): the commit goes away, its changes come back
  to the list with its message, nothing on disk changes.
- History (Git → Show History for Current File, or right-click a file in the tree): every commit of the file;
  see what each one changed, or that version against today's, as diagrams or text.
- Stash (box icon in the Commit panel): put uncommitted edits aside and Pop them back later. When a branch
  switch is blocked by local changes, the branch menu offers "Stash changes and switch".

## Layout

```
src/main/kotlin/es/hugoalvarezajenjo/sproutstudio/
  Main.kt                 entry point, Finder open-file events, window routing
  render/                 in-process PlantUML (Smetana, sandboxed includes, offline guard, problem collection)
  lang/                   diagram-type detection, completion engine, syntax highlighter, templates
  editor/                 CodeEditor composable, folding, find & replace, pure edit operations, rollback
  preview/                live preview pane, zoom/pan view, export/clipboard
  git/                    JGit wrapper, repository state, line diffs
  model/                  documents, project state, app windows, preferences, recents
  ui/                     theme, components, project & quick-preview windows, Commit/Problems/Templates panels
src/test/                 unit, render and off-screen UI tests
samples/                  example diagrams
.github/workflows/        CI (tests) and tag-driven releases
```
