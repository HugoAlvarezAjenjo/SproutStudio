# SproutStudio 🎨

A friendly, fully offline PlantUML studio for macOS, built with Kotlin + Compose for Desktop.

- **Quick Preview**: double-click a `.puml` in Finder and see the diagram instantly. It
  refreshes by itself when the file changes, even if you edit it elsewhere.
- **Project mode**: open a folder, get a file tree, tabs, a smart editor and a preview panel
  you can show and hide (⌘P).
- **100% offline**: PlantUML runs inside the app with its pure-Java layout engine (Smetana).
  No Graphviz, no server, no network calls. Remote `!include`s are refused.

## Run it

Requires JDK 21 (Amazon Corretto 21 works).

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew run                 # dev run (welcome screen)
./gradlew test                # unit + render + UI snapshot tests
```

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
rule). Local builds default to 1.0.0; override with `-PappVersion=1.2.0`.

Installing the downloaded, unsigned app on another Mac: drag it to Applications, then right-click →
Open the first time (or `xattr -dr com.apple.quarantine /Applications/SproutStudio.app`). Nothing else
needs to be installed: the app ships its own Java runtime.

## App icon

Put your logo at `src/main/resources/icon.png` (square PNG, ideally 1024×1024, transparent
background). It is used for the Dock in `./gradlew run`, the window icon, and — converted to
`.icns` automatically by `./gradlew createDistributable` — the `.app` icon in Finder and the Dock.
No file there → default icon.

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
| ⇧⌘P, ⇧⌘A, Shift Shift | command palette: every action, templates, the project's diagrams and recent projects |
| ⌘-, ⌘= / ⇧⌘-, ⇧⌘= | fold / unfold the block at the caret / all blocks (or click the ▾ ▸ chevrons in the gutter) |
| ⏎ / ⇧⏎ (or ⌘G / ⇧⌘G) | next / previous match; Esc closes the bar |
| ⌘S, ⇧⌘S | save, save as (autosave is on: File → Save Automatically) |
| ⌘N, ⌘O, ⇧⌘O | new diagram, open folder, open diagram |
| ⌘P, ⌘1 | show / hide the preview, the project panel |
| ⌘6 | show / hide the Problems panel |
| ⌘0, ⌘K | show / hide the Commit panel, jump to the commit message |
| ⌥⌘1 / ⌥⌘2 / ⌥⌘3 | editor only / editor and preview / preview only (also the three buttons at the right of the tab bar) |
| ⌥⌘Y | refresh the file tree |

Preview: mouse wheel to zoom at the pointer, drag with the left or middle button to pan (⇧+wheel or a sideways trackpad swipe also pans), double-click to fit. Export to SVG or PNG, or copy
the picture to the clipboard (📋) to paste straight into a doc.

The sun/moon button in the preview toolbar (or View -> Dark Diagram Preview) recolours the diagram
dark. It is independent of the editor theme, and exports/copies always keep the original colours.

## Problems

A Problems panel at the bottom of the editor (⌘6, View → Show Problems, or click the problem count
in the status bar) lists every error PlantUML finds — across all diagram blocks in the file, not just
the one in the preview, and every error within a block, not just the first. Each row shows the message
and its location ("Diagram 2 · line 7" when the file has several diagrams, "line 7" when it has one);
clicking it jumps the editor to that line and switches the preview to that diagram. The 1-line header
always shows: a green "No problems" when the file is clean, a red count when it isn't. It stays collapsed
by default and remembers its state.

## Git

Built in (JGit, no `git` install needed), local only: nothing is ever pushed.

- Gutter bars against the last commit: green added, blue modified, grey wedge where lines were deleted.
  Click a bar for the committed text of that block, with Rollback (puts it back in the editor; Undo in the
  toast) and Show Diff. ⌥⌘Z (Git → Rollback Lines) does the same for the block at the caret.
- Project tree colours: blue modified, green added, red unversioned, olive ignored.
- Branch button in the status bar: switch branch, New Branch… (from the current commit), delete a merged branch.
  Unsaved editor text is saved first; if a switch would overwrite uncommitted changes it is refused and nothing is lost.
- Commit panel (⌘0 or the second stripe button): tick the files, write a message, Commit (⌘⏎).
  Right-click a file for Rollback (restores the last committed version; a new file only goes back to unversioned).
  No repository yet? The panel offers "Create Git Repository".
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
- Create Git Repository can add a starter .gitignore (.DS_Store, temp files, IDE folders).

## Layout

```
src/main/kotlin/es/hugoalvarezajenjo/sproutstudio/
  Main.kt                 entry point, Finder open-file events, window routing
  render/                 in-process PlantUML (Smetana, sandboxed includes, offline guard)
  lang/                   diagram-type detection, completion engine, syntax highlighter
  editor/                 CodeEditor composable + pure edit operations
  preview/                live preview pane, zoom/pan view, export/clipboard
  model/                  documents, project state, app windows, recents
  ui/                     theme, components, project & quick-preview windows
docs/REQUIREMENTS.md      the v1 contract
samples/                  example diagrams
```
