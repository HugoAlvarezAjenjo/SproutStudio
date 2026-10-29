# Changelog

## 1.4.0

An Outline panel, menus that match the app and do more, and one window on Windows and Linux.

### Outline
- **A new Outline panel** (⌘7, Ctrl+7 on Windows and Linux) lists what the open file declares: participants,
  classes, packages, procedures. Classes sit inside the packages that hold them, and each level folds.
- A file with several diagrams gets one section per diagram, headed with its number and `title`.
- Clicking an entry selects its name in the editor (Back returns), and the entry at the caret is highlighted.

### Windows and Linux
- **One running app:** double-clicking a `.puml` while SproutStudio is open opens it in that app instead of
  starting another one. If the file is already open, or you launch the app again, its window comes to the
  front. macOS already worked this way.

### Menus
- **Every right-click menu follows the app theme**, the text fields' Cut/Copy/Paste included, with icons,
  shortcuts, separators between groups and destructive entries in red. Arrow keys, Enter, → and ←/Esc
  work as in a native menu.
- **Editor:** a menu of its own. Go to Declaration, Find Usages and Rename Symbol on a symbol; Comment,
  Reformat Code, Fold and Unfold; Find, Replace and Find in Files; Rollback Lines on a change, Compare
  with HEAD and Show History; Copy Diagram Image and Export; then Cut, Copy, Paste and Select All.
  Right-click moves the caret to the clicked spot, unless it lands inside the selection.
- **Project tree:**
  - Files: Duplicate, Copy Relative Path (for `!include`), Rollback… and Add to .gitignore.
  - Export submenu: copy the image or export to SVG, PNG or PDF without opening the file. An open tab's
    unsaved text is what gets exported.
  - Folders: Expand All and Collapse All below that folder, Open in Terminal, and Add to .gitignore.
  - The project folder: Refresh Files and Open in Terminal.
  - Git entries only appear in a repository.
- **Tabs and the Commit panel:** the same look, with icons and groups. Rollback sits apart at the end.
- **Preview toolbar:** the theme picker and the Export button open the same menus. The theme picker
  ticks the theme in use and scrolls through the full list.

### Fixes
- Dragging a selection above or below the editor scrolls the text and keeps selecting. Before, the
  selection stopped at the visible lines.

## 1.3.0

SproutStudio on Linux, a theme menu for diagrams and soft wrap.

### Linux
- **`.deb` package** for Ubuntu, Kubuntu and other Debian-based systems, with a menu entry, icon and
  `.puml` file association, plus a portable `.tar.gz` for distributions without dpkg.
- The dock or panel shows the SproutStudio icon and groups the window with its menu entry.
- Move to Trash uses the desktop's trash, and Open Containing Folder opens the file manager with the file
  selected (Nautilus, Dolphin, Nemo, Thunar).
- Shortcuts use Ctrl and Alt, as on Windows. CI runs the tests on Ubuntu, and releases publish the `.deb`
  and the `.tar.gz`.

### Preview
- **Diagram theme menu:** the palette button in the preview toolbar lists PlantUML's bundled themes and
  writes, replaces or removes the shown diagram's `!theme` line, as one undo step.
- Completion of `!theme` names now lists every theme PlantUML ships (adds `spacelab-white` and `sunlust`).

### Editor
- **Soft wrap** (View → Soft Wrap, ⌥Z / Alt+Z, also in Settings): long lines continue on the next row
  instead of scrolling sideways. The file is not changed. Line numbers and fold chevrons go on the first
  row of each line; the current-line highlight covers all its rows.

### Fixes
- macOS: in full screen on an external monitor, the tab bar and the layout buttons respond to clicks
  again. They sat under the strip macOS keeps for the menu bar.
- Files with a `@startuml` inside another diagram, or a block without `@end…`, are split into diagrams
  the same way PlantUML does, so "1 of N", completion and the theme menu all point at the right one.

## 1.2.0

SproutStudio on Windows, and an editor that types like VS Code.

### Windows
- **Windows installer (.msi):** per-user install with Start menu entry, desktop shortcut and `.puml`
  file associations. Shortcuts use Ctrl and Alt, and tooltips name them that way.
- **CRLF files** open normally and are saved back with their own line endings.
- **File → Exit (Ctrl+Q)** quits like ⌘Q on macOS: autosave is flushed and every open project comes back
  on the next launch.
- "Show in Explorer" opens the folder with the file selected; texts name File Explorer and the Recycle Bin.
- Renaming a file by changing only the case of its name (`a.puml` → `A.puml`) works.
- CI runs the tests on Windows, and releases publish the `.msi` next to the `.dmg`.

### Editor
- **Brackets and quotes close themselves:** typing `{`, `(`, `[` or `"` adds the closer, typing the
  closer steps over it, Backspace on an empty pair removes both, and typing one over a selection wraps it.
  Like VS Code, only the closers it added are stepped over.
- **Enter between `{` and `}`** puts the closing brace on its own line, with the caret indented between
  ([#5](https://github.com/HugoAlvarezAjenjo/SproutStudio/issues/5)).
- **Keywords are coloured in any case** (`Participant`, `CLASS`)
  ([#4](https://github.com/HugoAlvarezAjenjo/SproutStudio/issues/4)).

### Tabs
- **Drag tabs to reorder them;** the bar scrolls by itself when you hold a tab at its edge. The order is
  kept with the session.
- **Middle-click closes a tab.**
- **Right-click menu:** Close, Close Other Tabs, Close Tabs to the Left / Right, Close All, Copy Path and
  Show in Finder / Explorer. Closing several asks once about the unsaved ones.

### App
- The home screen shows the app logo and the real version; new app icon.
- New samples (`kitchen.puml`, `architecture.puml` and the `common/` library) that show includes,
  functions, procedures, variables and the standard library.

### Under the hood
- UI tests use Compose's `runComposeUiTest` v2; no compiler or Gradle 10 deprecation warnings left.

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
