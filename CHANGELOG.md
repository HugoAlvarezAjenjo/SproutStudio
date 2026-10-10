# Changelog

## 1.1.0

The editor grows up: real undo, project-wide search, navigation and refactoring.

### Editor
- **Undo and redo (⌘Z / ⇧⌘Z) for every edit.** Each command (reformat, move, comment, duplicate,
  completion, palette actions) is its own step; typing is grouped like IntelliJ. Undo restores the caret
  and selection too.
- **Go to Declaration (⌘B or ⌘-click) and Find Usages (⌥F7)** for participants, classes, `!procedure`,
  `!function`, `!define` and `$variables`. ⌘B follows local `!include`s to find declarations in other
  files, and ⌘-click on an `!include` opens that file. Back / Forward with ⌥⌘← / ⌥⌘→.
- **Rename Symbol (⇧F6).** Renames every usage at once, also in the included files and in the diagrams
  that include the file. Names that would break the diagram are refused with the reason.
- **Reformat Code (⌥⌘L)** for the file or the selection: block indentation, spaces around arrows,
  member visibility in classes, trailing spaces and blank lines. Only whitespace PlantUML ignores, so the
  diagram never changes.
- **Block comment (⌥⌘/)** with `/' '/`, next to the line comment (⌘/).
- **Move lines up / down (⌥↑ / ⌥↓).**
- **Completion** matches initials (`sp` → `Sequence.puml`) and completes local paths after `!include`,
  folder by folder.

### Project
- **Find in Files (⇧⌘F):** search every diagram with match case, whole words and regex; results grouped
  by file, click to jump. Unsaved tabs are searched as they are.
- **Restore session:** on launch, the projects you had open come back with their tabs, active tab and
  caret. Can be turned off in Settings → General.

### App
- **Settings (⌘,):** theme, language, diagram preview, editor font size, autosave, session restore and
  recent projects.
- **Spanish translation;** the language follows the system or can be chosen in Settings, and switches at
  once in every window.
- **New home screen.**

### Under the hood
- MVVM re-architecture in layers (app, core, domain, services, platform, features), enforced by
  `ArchitectureTest`.
- UI tests wait for the UI instead of sleeping, to keep CI stable.

## 1.0.0

First release: Quick Preview from Finder, project mode with file tree, tabs, smart editor and live
preview, offline C4/AWS/Azure stdlib, templates, Problems panel, export to SVG/PNG/PDF and built-in Git.
