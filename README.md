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
  <img alt="Windows" src="https://img.shields.io/badge/platform-Windows-0078D6?logo=windows&logoColor=white">
  <img alt="Linux" src="https://img.shields.io/badge/platform-Linux-FCC624?logo=linux&logoColor=black">
</p>

<p align=center>
  <img alt="SproutStudio editing a sequence diagram with the live preview" src="https://raw.githubusercontent.com/wiki/HugoAlvarezAjenjo/SproutStudio/images/workspace.png" width="820">
</p>

## Features

- **Quick Preview**: double-click a `.puml` and see the diagram at once; it refreshes when the file changes. ([more](https://github.com/HugoAlvarezAjenjo/SproutStudio/wiki/Getting-Started))
- **Project mode**: file tree, tabs, smart editor and live preview in one window. ([more](https://github.com/HugoAlvarezAjenjo/SproutStudio/wiki/Tabs-and-Project-Tree))
- **Smart editor**: completion that knows the diagram type, auto-closing brackets, smart indent, folding, soft wrap, find & replace, real undo. ([more](https://github.com/HugoAlvarezAjenjo/SproutStudio/wiki/Editor))
- **Navigation and refactoring**: Go to Declaration, Find Usages, Rename and Reformat, across `!include`s. ([more](https://github.com/HugoAlvarezAjenjo/SproutStudio/wiki/Navigation-and-Refactoring))
- **C4, AWS and Azure, offline**: PlantUML's standard library ships inside the app. ([more](https://github.com/HugoAlvarezAjenjo/SproutStudio/wiki/Libraries))
- **Preview and export**: zoom, diagram themes, dark diagrams, SVG/PNG/PDF or copy to the clipboard. ([more](https://github.com/HugoAlvarezAjenjo/SproutStudio/wiki/Preview-and-Export))
- **Git built in**: change bars, commit, branches, stash, history and diagram diffs, local only. ([more](https://github.com/HugoAlvarezAjenjo/SproutStudio/wiki/Git))
- **100% offline and self-contained**: no Graphviz, no server, no network calls; the app ships its own Java runtime. ([FAQ](https://github.com/HugoAlvarezAjenjo/SproutStudio/wiki/FAQ))

Everything else, including all the [keyboard shortcuts](https://github.com/HugoAlvarezAjenjo/SproutStudio/wiki/Keyboard-Shortcuts), is in the [wiki](https://github.com/HugoAlvarezAjenjo/SproutStudio/wiki).

## Download

Get the `.dmg` (macOS, Apple Silicon), `.msi` (Windows, x64) or `.deb` / `.tar.gz` (Linux, x64) from the
[latest release](https://github.com/HugoAlvarezAjenjo/SproutStudio/releases/latest). The installers are
not signed, so the first launch needs right-click → Open on macOS or More info → Run anyway on Windows.
See [Installation](https://github.com/HugoAlvarezAjenjo/SproutStudio/wiki/Installation).

## Develop

Requires JDK 21. `./gradlew run` starts the app and `./gradlew test` runs the tests.
Architecture, testing and releases are in [CONTRIBUTING.md](CONTRIBUTING.md).
