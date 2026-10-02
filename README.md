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

## App icon

Put your logo at `src/main/resources/icon.png` (square PNG, ideally 1024×1024, transparent
background). It is used for the Dock in `./gradlew run`, the window icon, and — converted to
`.icns` automatically by `./gradlew createDistributable` — the `.app` icon in Finder and the Dock.
No file there → default icon.

## Editor tricks

| Keys | What it does |
|---|---|
| typing | suggestions for the current diagram type and your own participants/classes |
| ⌃Space | show suggestions now |
| `@st` + ⏎ | pick a ready-made diagram template |
| ⏎ | smart indent (`alt`, `loop`, `if`, `{` … indent; `end`, `else`, `}` snap back) |
| ⇥ / ⇧⇥ | indent / outdent lines |
| ⌘/ | comment / uncomment lines |
| ⌘D | duplicate line |
| ⌘F, ⌘R | find, find & replace (Cc match case, W whole words, .* regex with `$1` groups) |
| ⏎ / ⇧⏎ (or ⌘G / ⇧⌘G) | next / previous match; Esc closes the bar |
| ⌘S, ⇧⌘S | save, save as (autosave is on: File → Save Automatically) |
| ⌘N, ⌘O, ⇧⌘O | new diagram, open folder, open diagram |
| ⌘P, ⌘1 | show / hide the preview, the project panel |
| ⌥⌘Y | refresh the file tree |

Preview: scroll to pan, ⌘+scroll to zoom, double-click to fit. Export to SVG or PNG, or copy
the picture to the clipboard (📋) to paste straight into a doc.

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
