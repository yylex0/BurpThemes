# BurpTheme

> A heavily customized theming engine for Burp Suite — wallpaper backgrounds, frosted-glass surfaces, and five hand-tuned themes that restyle the *entire* suite, not just the accent colors.

BurpTheme is a Java extension (Montoya API) that adds a dedicated theme gallery to Burp and applies a deep, consistent look across the whole Swing UI — Proxy, Repeater, Target, Intruder, tables, HTTP editors, menus, popups, tooltips, dialogs, and the window chrome. It runs on both Burp Suite **Community** and **Professional** (there is no edition-specific code; it themes Pro-only tools automatically).

It is built for testers who spend long sessions in Burp and want a more readable, immersive, and consistent workspace — and as a small, maintainable Montoya codebase that other extension modules can build on.

**Author:** Raghav Vivekanandan @ Ashtaksha Labs

## Overview

BurpTheme adds a top-level Burp tab named `BurpTheme`. From that tab, users can select a theme, reapply the theme to newly rendered Burp panels, and enable or disable suite-wide styling. Theme changes are intentionally kept inside the extension tab so there is one clear control surface.

The extension focuses on presentation and workflow comfort. It does not intercept, inspect, store, replay, or modify HTTP traffic. It uses Burp's public Montoya extension API for lifecycle and tab registration, and standard Swing APIs for UI rendering.

## Key Capabilities

- A theme-gallery tab (`BurpTheme`) with animated, wallpaper-backed cards for one-click theme switching.
- Deep suite-wide theming: tabs, tables (cells **and** headers), trees, lists, text/HTTP editors, buttons, menus, popups, tooltips, dialogs, scroll bars, toggle switches, split panes, and the window title bar.
- Wallpaper backgrounds rendered behind the suite with frosted-glass surfaces over them, while dense-text working areas (editors, tables) stay clean and readable.
- Gradient-shaded "pill" buttons with theme-contrasting borders and clear hover/pressed states.
- Five hand-tuned themes, each with its own wallpaper, avatar crest, animated GIF preview, detail strip, motion overlay, and color palette.
- Works in Burp **Community and Professional** — Pro-only tools are themed automatically by a live component-tree pass.
- Stable by design: no flicker/blink loops, coalesced refresh passes, and a clean unload path that restores Burp's original UI defaults, component state, listeners, timers, and wallpaper panels.
- Selected theme and suite-wide toggle persist across restarts via Montoya extension storage.
- A small module interface for building additional extension tabs/tools on top of the same engine.

## Included Themes

BurpTheme ships with five heavily themed visual packs. The suite uses translucent glass panels over theme-specific artwork; each theme varies the wallpaper, avatar crest, animated preview, detail strip, motion overlay, accent/gold colors, and atmosphere. **Korok Forest loads by default** — switch any time from the `BurpTheme` tab.

| Theme | Slug | Mood |
| --- | --- | --- |
| **Korok Forest** _(default)_ | `korok-forest` | moss canopy calm — green |
| **Great Plateau** | `great-plateau` | wildlight plateau dusk — teal & gold |
| **Hebra Snowfield** | `ancient-slate` | snow-blue alpine dusk — blue |
| **Blood Moon** | `blood-moon` | crimson ruins eclipse — red |
| **Gloom Depths** | `gloom-depths` | violet depths pulse — purple |

Each theme's assets follow the `*-<slug>` naming in `assets/` — a wallpaper plus `avatar-`, `preview-` (GIF), `detail-`, and `motion-` (GIF) files. Hebra Snowfield keeps the stable slug `ancient-slate` for saved-preference compatibility. Each theme also defines its base, horizon, accent, gold, text, muted-text, button, and button-text colors plus wallpaper opacity.

## Operational Scope

Burp Suite does not provide a formal API for completely replacing the appearance of every built-in tool. BurpTheme therefore uses a best-effort Swing theming pass.

Expected behavior:

- Standard Swing controls should receive the selected theme.
- Secondary frames and dialog windows should receive the selected theme when they open or gain focus.
- Newly rendered panels may need a short delay before the theme pass completes.
- The `Reapply theme` action can be used after opening tools that render lazily.

Known boundaries:

- Some Burp-owned custom renderers may draw their own colors.
- Some selected rows, split buttons, or editor internals may retain small native paint artifacts.
- Suite-wide mode themes visible Swing windows in the Burp JVM. If several Java-based extensions are loaded, their Swing windows may also be styled while suite-wide mode is enabled.
- The extension does not change Burp's HTTP processing, project data, scan behavior, proxy behavior, or request/response contents.

## Requirements

- Burp Suite **Community or Professional** with a Montoya API that exposes `persistence().extensionData()` and suite tab registration.
- Tested against current Burp Suite releases; the extension has no edition-specific code and uses only the public Montoya API.
- Java compiler/runtime compatible with the installed Burp Suite build.
- Java 17 is the default extension classfile target; set `JAVAC_RELEASE=21` only if you intentionally want Java 21-only output.
- Python 3.9+ is required for theme registry validation during builds.
- Pillow is required only when regenerating wallpaper/avatar assets.
- Linux, macOS, or Windows capable of running Burp Suite and Java Swing. `build.sh` requires Bash 4+ plus common Unix tools (`jar`, `unzip`, `strings`, `grep`, `diff`, `find`, and `sort`); use Homebrew Bash, Git Bash, MSYS2, or WSL where the platform default shell does not provide them.

The project is intentionally simple and does not require Maven or Gradle.

## Build

### Windows (PowerShell)

On Windows, build with the bundled `build.ps1`. It compiles against the local Burp Suite install and packages the jar using a small `java.util.jar` helper (Burp's bundled JDK ships `javac` but not `jar.exe`), so no extra tooling is required:

```powershell
.\build.ps1
```

Defaults target a standard Burp Suite install; override the Burp jar and/or JDK location if yours differs:

```powershell
.\build.ps1 -BurpJar "C:\path\to\burpsuite.jar" -JdkBin "C:\path\to\jre\bin"
```

Build output:

```text
dist\arcade-burp-community.jar
```

> Run the script with PowerShell's default execution policy (`.\build.ps1`). Do not pass `-ExecutionPolicy Bypass`.

### Linux / macOS

Build from the repository root:

```bash
./build.sh
```

The default Burp Suite JAR path is:

```text
/usr/share/burpsuite/burpsuite.jar
```

If Burp Suite is installed elsewhere, set `BURP_JAR`:

```bash
BURP_JAR=/absolute/path/to/burpsuite.jar ./build.sh
```

If a specific Java compiler is required, set `JAVAC_BIN`:

```bash
JAVAC_BIN=/absolute/path/to/javac ./build.sh
```

If a different Java release target is required, set `JAVAC_RELEASE`:

```bash
JAVAC_RELEASE=21 ./build.sh
```

If a specific Python 3 interpreter is required for theme validation, set `PYTHON_BIN`:

```bash
PYTHON_BIN=/absolute/path/to/python3 ./build.sh
```

Build output:

```text
dist/arcade-burp-community.jar
```

The filename is retained for compatibility with earlier local installs; the manifest and extension UI identify the extension as `BurpTheme`.

The extension manifest entry point is:

```text
burp.arcade.BurpThemeExtension
```

## Asset Generation

Wallpapers, avatars, animated preview GIFs, detail strips, and motion GIF overlays are checked into `assets/`. The shipped wallpapers and main GIF previews are curated runtime assets; the generator defaults to avatars so curated wallpapers are not overwritten by accident. To regenerate avatars after editing `tools/generate_theme_assets.py`, install Pillow and run:

```bash
python3 -m pip install Pillow
python3 tools/generate_theme_assets.py --asset avatar --overwrite
```

To list the asset paths the generator owns without writing files:

```bash
python3 tools/generate_theme_assets.py --list --asset all
```

To regenerate only one avatar without touching the old high-detail wallpapers:

```bash
python3 tools/generate_theme_assets.py --theme great-plateau --asset avatar --overwrite
```

To regenerate the registered theme detail strips:

```bash
python3 tools/generate_theme_assets.py --asset detail --overwrite
```

To regenerate the smaller tab-only motion GIF overlays:

```bash
python3 tools/generate_theme_assets.py --asset motion --overwrite
```

Existing generated assets are protected by default. Omitting `--overwrite` is a safe way to confirm which checked-in PNG would be replaced:

```bash
python3 tools/generate_theme_assets.py --theme great-plateau --asset avatar
```

During local development, packaged assets are used first. To let the extension fall back to files in the repository `assets/` directory, start Burp from the repository root with:

```bash
java -Dburptheme.devAssets=true -jar /path/to/burpsuite.jar
```

If Burp starts from another directory, provide the asset directory explicitly:

```bash
java -Dburptheme.devAssets=true -Dburptheme.devAssetsDir=/absolute/path/to/BurpThemes/assets -jar /path/to/burpsuite.jar
```

`build.sh` runs `tools/validate_themes.py`, derives the packaged asset list from `BurpTheme.java`, and fails if the jar contains a different wallpaper/avatar/preview/detail/motion set.

To run the theme checks without rebuilding the jar:

```bash
python3 tools/validate_themes.py
```

## Installation In Burp Suite

1. Start Burp Suite.
2. Open `Extensions > Installed`.
3. Click `Add`.
4. Set `Extension type` to `Java`.
5. Select `dist/arcade-burp-community.jar`.
6. Load the extension.
7. Open the new `BurpTheme` top-level tab.

For the cleanest result, load the extension in a fresh Burp process. Swing look-and-feel defaults are process-wide, so a full Burp restart is the most reliable way to clear JVM-wide UI state.

## Usage

### Select A Theme

1. Open the `BurpTheme` tab.
2. Click a theme card.
3. Wait briefly for visible Burp panels and windows to repaint.

### Reapply The Theme

Use `Reapply` from the tab after opening tools that render after startup, such as secondary tool windows or panels that are created only when visited.

### Disable Suite-Wide Styling

`Apply suite-wide` is enabled by default so the selected theme applies across Burp immediately. Disable it from the `BurpTheme` tab to restore BurpTheme's suite-wide changes. The BurpTheme tab itself remains styled so the theme controls stay usable.

### Remove The Extension

1. Open `Extensions > Installed`.
2. Select the BurpTheme extension.
3. Click `Remove`.

On unload, BurpTheme deregisters its tab, stops refresh timers, removes its AWT listener, removes wallpaper panels, restores tracked component state, and restores UI defaults that are still owned by BurpTheme.

## Repository Layout

```text
assets/
  avatar-great-plateau.png
  avatar-ancient-slate.png
  avatar-blood-moon.png
  avatar-gloom-depths.png
  avatar-korok-forest.png
  detail-great-plateau.png
  detail-ancient-slate.png
  detail-blood-moon.png
  detail-gloom-depths.png
  detail-korok-forest.png
  preview-great-plateau.gif
  preview-ancient-slate.gif
  preview-blood-moon.gif
  preview-gloom-depths.gif
  preview-korok-forest.gif
  temple-overworld.png
  wallpaper-ancient-slate.png
  wallpaper-blood-moon.png
  wallpaper-gloom-depths.png
  wallpaper-korok-forest.png

src/burp/arcade/
  ArcadeBurpExtension.java
  BurpSuiteModule.java
  BurpTheme.java
  BurpThemeEngine.java
  BurpThemeExtension.java
  BurpThemeStorePanel.java
  ThemeColors.java
  ThemeCrestIcon.java

build.sh
README.md
docs/THEME_AUTHORING.md
tools/generate_theme_assets.py
tools/verify_ui_coverage.py
tools/smoke_test_extension.py
tools/validate_themes.py
dist/arcade-burp-community.jar
```

## Architecture

### `BurpThemeExtension`

Main Montoya extension entry point.

Responsibilities:

- Sets the extension name.
- Creates the theme engine.
- Creates and registers extension modules.
- Registers the `BurpTheme` suite tab.
- Loads and saves the selected theme and suite-wide preference.
- Applies the selected theme.
- Schedules startup refresh passes.
- Handles unload and restoration.

### `BurpSuiteModule`

Small interface for extension modules.

A module supplies:

- A tab title.
- A Swing component.
- A theme-change callback.
- An optional unload callback.

This keeps feature tabs isolated from the extension entry point.

### `BurpThemeStorePanel`

Swing UI for the `BurpTheme` tab.

Responsibilities:

- Renders the theme selection interface.
- Displays available themes as wallpaper-preview cards.
- Provides card selection, suite-wide toggle, and reapply controls.
- Updates local labels, icons, and backgrounds when the selected theme changes.

### `BurpTheme`

Theme registry.

Each enum entry defines the theme's display name, stable slug, tagline, wallpaper resource, avatar resource, preview GIF resource, detail strip resource, motion GIF resource, colors, and wallpaper opacity settings. The build and smoke gates package and verify the runtime assets from this one registry.

### `BurpThemeEngine`

Suite-wide Swing theming engine.

Responsibilities:

- Installs theme-specific UI defaults.
- Applies bounded recursive styling to visible Swing component trees.
- Installs frame wallpaper panels.
- Handles late-created frames, dialogs, popups, and lazy components.
- Tracks original component state before mutation.
- Restores UI defaults and component state on unload.
- Cleans up listeners and timers.

This is the most sensitive part of the project because it mutates live Swing components. Changes to this file should be reviewed carefully and tested in a fresh Burp process.

### `ThemeCrestIcon`

Draws compact avatar icons used by theme cards.

### `ThemeColors`

Shared color helpers for alpha composition and hex formatting.

## Adding A Theme

For the full contributor workflow, examples, and common validator errors, see `docs/THEME_AUTHORING.md`.

1. Place the wallpaper, avatar, preview GIF, detail strip, and motion GIF in `assets/`.
2. Add a new enum constant in `BurpTheme.java`.
3. Provide the display label, stable slug, tagline, wallpaper resource, avatar resource, preview resource, detail resource, motion resource, colors, and opacity values for the new theme.
4. Add the same slug and asset filenames to `tools/generate_theme_assets.py`; validation requires generator slugs and active registry assets to match.
5. Keep wallpapers at `1812x868` PNG, avatars at `512x512` PNG, detail strips at `640x180` PNG, preview GIFs at least `240x100` with at least 8 frames, and motion GIFs at `180x96` with at least 12 frames unless the code is updated for a new contract.
6. Build the jar with `./build.sh`.
7. Confirm the asset is packaged. `build.sh` validates all shipped wallpaper, avatar, preview, detail, and motion resources, and this command is useful for manual inspection:

```bash
jar tf dist/arcade-burp-community.jar | sort
```

8. Run `python3 tools/validate_themes.py` to catch duplicate slugs, missing assets, stale assets, corrupt PNG/GIF containers, incorrect PNG dimensions, and weak preview GIFs.
9. Load the jar in Burp and test the theme card, animated preview, suite-wide styling, popups, dialogs, and removal behavior.

## Adding A New Extension Module

1. Create a class that implements `BurpSuiteModule`.
2. Keep the module's UI and module-specific state inside that class.
3. Register the module from `BurpThemeExtension.initialize` using `registerModule`.
4. Implement `themeChanged(BurpTheme theme)` so the module follows the active theme.
5. Implement `unload()` for timers, listeners, background work, or Montoya registrations owned by the module.
6. Build and test in a fresh Burp process.

A module should not directly mutate unrelated Burp windows. Suite-wide styling belongs in `BurpThemeEngine`.

## Testing Checklist

Run these checks after source changes:

```bash
./build.sh
python3 tools/validate_themes.py
python3 tools/verify_ui_coverage.py
python3 tools/smoke_test_extension.py
git diff --check
mkdir -p build/burptheme-check
javac --release 17 -Xlint:all -classpath /usr/share/burpsuite/burpsuite.jar -sourcepath src -d build/burptheme-check $(find src -name "*.java" | sort)
jar tf dist/arcade-burp-community.jar | sort
```

`tools/verify_ui_coverage.py` is an executable coverage matrix with 150+ checks across windows, root/layered/glass panes, tabs, tables, trees, text editors, buttons, proxy controls, scroll panes, menus/popups, dialogs, assets, theme-store GIFs, and unload/restore behavior. `tools/smoke_test_extension.py` checks more than the Swing preview path: manifest entrypoint, classfile target, Montoya API classes in the configured Burp jar, packaged assets, stale quick-switch/Twilight artifacts, preference keys, no traffic-mutating source or packaged class registrations, and a compile smoke check. `build.sh` runs both gates against the jar it produces, skipping the duplicate compile step because the build already compiled the sources.

Manual validation in Burp:

- Load the jar in a fresh Burp process.
- Confirm the `BurpTheme` tab appears.
- Select each theme card.
- Confirm every card preview animates smoothly and leaves a useful first frame when animation is paused or unavailable.
- Toggle `Apply suite-wide` off and on.
- Reload Burp and confirm the selected theme and suite-wide setting persist.
- Use `Reapply theme` after opening new Burp tools.
- Open Proxy, Target, Repeater, Intruder, Logger, Dashboard, and Extensions.
- In Proxy Intercept, switch themes while `Forward` and `Drop` are visible and confirm both buttons repaint for enabled and disabled states.
- Start an Intruder attack and confirm the attack window receives the theme.
- Open context menus, combo-box popups, add-extension dialogs, remove-extension confirmations, and close-project prompts.
- Confirm text remains readable on buttons, tables, trees, editors, and popups.
- Remove the extension and confirm the tab disappears and UI state is restored.
- Reload the extension and confirm there are no duplicate wallpaper layers.

## Security And Review Notes

BurpTheme is a user-interface extension. It does not register HTTP handlers and does not process traffic. Its risk surface is Swing UI mutation rather than request/response handling.

Review focus areas:

- EDT safety for Swing operations.
- Bounded recursion through component trees.
- Restoration of UI defaults and component state.
- Cleanup of timers, listeners, and window references.
- Clear module boundaries for new features.
- Packaged asset loading from the jar.

## Attribution

BurpTheme is authored by Raghav Vivekanandan at Ashtaksha Labs.
