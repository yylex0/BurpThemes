#!/usr/bin/env python3
from __future__ import annotations

from collections import Counter
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MIN_CASES = 110


@dataclass(frozen=True)
class CoverageCase:
    case_id: str
    area: str
    description: str
    path: str
    tokens: tuple[str, ...]


def case(case_id: str, area: str, description: str, path: str, *tokens: str) -> CoverageCase:
    return CoverageCase(case_id, area, description, path, tokens)


ENGINE = "src/burp/arcade/BurpThemeEngine.java"
STORE = "src/burp/arcade/BurpThemeStorePanel.java"
EXTENSION = "src/burp/arcade/BurpThemeExtension.java"
THEME = "src/burp/arcade/BurpTheme.java"
CREST = "src/burp/arcade/ThemeCrestIcon.java"
VALIDATOR = "tools/validate_themes.py"
SMOKE = "tools/smoke_test_extension.py"
RUNTIME_SMOKE = "tools/runtime_smoke_burp.py"
WINDOW_POLISH = "tools/verify_window_polish.py"
BUILD = "build.sh"
README = "README.md"
AUTHORING = "docs/THEME_AUTHORING.md"


CASES: tuple[CoverageCase, ...] = (
    case("window-001", "windows", "Themes JFrame windows", ENGINE, "private int themeWindow(Window window", "window instanceof JFrame"),
    case("window-002", "windows", "Themes JDialog/JWindow popup windows", ENGINE, "private int themePopupWindow(Window window", "JDialog || window instanceof JWindow"),
    case("window-003", "windows", "Installs global AWT listener for late windows", ENGINE, "addAWTEventListener", "AWTEvent.WINDOW_EVENT_MASK"),
    case("window-004", "windows", "Coalesces newly opened window refreshes", ENGINE, "scheduleOpenedWindowThemeRefreshes", "scheduleCoalescedFrameThemeRefresh(window, 650)"),
    case("window-005", "windows", "Coalesces popup refreshes after focus and lazy additions", ENGINE, "scheduleCoalescedPopupThemeRefresh", "pendingPopupRefreshes"),
    case("window-006", "windows", "Coalesces lazy frame refreshes", ENGINE, "scheduleCoalescedFrameThemeRefresh", "pendingFrameRefreshes"),
    case("window-007", "windows", "Uses bounded traversal budget", ENGINE, "MAX_THEME_COMPONENTS_PER_PASS", "25000"),
    case("window-008", "windows", "Prevents runaway traversal depth", ENGINE, "MAX_THEME_DEPTH", "256"),
    case("window-009", "windows", "Throttles focus-driven refreshes", ENGINE, "FOCUS_REFRESH_THROTTLE_NANOS", "scheduleFocusedWindowThemeRefresh"),
    case("window-010", "windows", "Prunes detached component references", ENGINE, "pruneDetachedThemeState", "isDetachedThemeComponent"),
    case("window-011", "windows", "Traverses popup layered and glass panes", ENGINE, "private int themePopupWindow(Window window", "rootPaneContainer.getLayeredPane()", "rootPaneContainer.getGlassPane()"),
    case("window-012", "windows", "Refreshes lightweight popup components added inside frames", ENGINE, "isLazyFrameThemeCandidate", "component instanceof JPopupMenu", "component instanceof JOptionPane", "component instanceof JFileChooser"),
    case("window-013", "windows", "Listens for late-added components", ENGINE, "AWTEvent.CONTAINER_EVENT_MASK", "ContainerEvent.COMPONENT_ADDED"),
    case("window-014", "windows", "Avoids delayed repainting hidden popups", ENGINE, "window.isShowing()", "scheduleCoalescedPopupThemeRefresh"),
    case("window-015", "windows", "Installs asset backgrounds behind popup windows", ENGINE, "private int themePopupWindow(Window window", "boolean wallpaperBacked = installBurpBackground(window, theme)"),
    case("window-016", "windows", "Verifies real top-level popup and dialog transparency", WINDOW_POLISH, "JDialog", "JWindow", "burptheme.background"),
    case("root-001", "root panes", "Themes root pane background", ENGINE, "JRootPane rootPane", "makeTransparent(rootPane"),
    case("root-002", "root panes", "Themes layered pane background", ENGINE, "JLayeredPane layeredPane", "makeTransparent(layeredPane"),
    case("root-003", "root panes", "Traverses layered pane children", ENGINE, "getLayeredPane()", "themeBurpComponent(rootPaneContainer.getLayeredPane()"),
    case("root-004", "root panes", "Traverses glass pane overlays", ENGINE, "getGlassPane()", "themeBurpComponent(rootPaneContainer.getGlassPane()"),
    case("root-005", "root panes", "Themes root title background", ENGINE, "JRootPane.titleBarBackground"),
    case("root-006", "root panes", "Themes inactive title background", ENGINE, "JRootPane.titleBarInactiveBackground"),
    case("root-007", "root panes", "Restores root pane properties", ENGINE, "restoreRootPaneProperties", "originalRootPaneProperties"),
    case("background-001", "wallpapers", "Installs frame wallpaper panel", ENGINE, "installBurpBackground", "BurpBackgroundPanel"),
    case("background-002", "wallpapers", "Resizes wallpaper with layered pane", ENGINE, "resizeBackgroundPanel", "componentResized"),
    case("background-003", "wallpapers", "Preloads background images", ENGINE, "preloadBackgroundImages", "preloadAll"),
    case("background-004", "wallpapers", "Supports dev asset fallback", ENGINE, "burptheme.devAssets", "devAssetsDir"),
    case("background-005", "wallpapers", "Removes wallpaper panels on restore", ENGINE, "installedBackgrounds.clear()"),
    case("background-006", "wallpapers", "Dedupes stale background panels across reloads", ENGINE, "removeDuplicateBurpBackgroundPanels", '"burptheme.background"'),
    case("background-007", "wallpapers", "Avoids repainting unchanged wallpaper bounds", ENGINE, "bounds.equals(backgroundPanel.getBounds())", "return false"),
    case("background-008", "wallpapers", "Keeps wallpaper-backed shells non-opaque", ENGINE, "makeWallpaperGlass", "setOpaque(false)"),
    case("tabs-001", "tabs", "Sets tabbed pane colors", ENGINE, "themeTabbedPane", "setForegroundAt"),
    case("tabs-002", "tabs", "Applies consistent boxed tab UI", ENGINE, "BurpThemeTabbedPaneUI", "tabbedPane.setUI"),
    case("tabs-003", "tabs", "Refreshes selected tab content", ENGINE, "getSelectedComponent()", "themeBurpComponent(selected"),
    case("tabs-004", "tabs", "Stores original tab backgrounds", ENGINE, "tabBackgrounds"),
    case("tabs-005", "tabs", "Stores original tab foregrounds", ENGINE, "tabForegrounds"),
    case("tabs-006", "tabs", "Restores tab listener on unload", ENGINE, "tabThemeListeners.clear()"),
    case("tabs-007", "tabs", "Skips tab retheming while disabled or detached", ENGINE, "extensionUnloaded.get() || !isSuiteTintEnabled()", "source.isDisplayable()"),
    case("tables-001", "tables", "Themes JTable branch", ENGINE, "component instanceof JTable", "themeTable"),
    case("tables-002", "tables", "Themes table background default", ENGINE, '"Table.background"'),
    case("tables-003", "tables", "Themes alternate row color", ENGINE, '"Table.alternateRowColor"'),
    case("tables-004", "tables", "Themes table foreground", ENGINE, '"Table.foreground"'),
    case("tables-005", "tables", "Themes table selection background", ENGINE, '"Table.selectionBackground"'),
    case("tables-006", "tables", "Themes inactive table selection", ENGINE, '"Table.selectionInactiveBackground"'),
    case("tables-007", "tables", "Themes table focus cells", ENGINE, '"Table.focusCellBackground"'),
    case("tables-008", "tables", "Themes table drop lines", ENGINE, '"Table.dropLineColor"'),
    case("tables-009", "tables", "Themes table scroll border", ENGINE, '"Table.scrollPaneBorder"'),
    case("tables-010", "tables", "Themes JTableHeader branch", ENGINE, "component instanceof JTableHeader"),
    case("tables-011", "tables", "Themes table header background", ENGINE, '"TableHeader.background"'),
    case("tables-012", "tables", "Themes table header sort icon", ENGINE, '"TableHeader.sortIconColor"'),
    case("trees-001", "trees", "Themes JTree branch", ENGINE, "component instanceof JTree", "themeTree"),
    case("trees-002", "trees", "Themes tree background", ENGINE, '"Tree.background"'),
    case("trees-003", "trees", "Themes tree selection", ENGINE, '"Tree.selectionBackground"'),
    case("trees-004", "trees", "Themes tree inactive selection", ENGINE, '"Tree.selectionInactiveBackground"'),
    case("trees-005", "trees", "Themes tree drop line", ENGINE, '"Tree.dropLineColor"'),
    case("trees-006", "trees", "Themes tree icons", ENGINE, '"Tree.icon.leafColor"', '"Tree.icon.openColor"'),
    case("trees-007", "trees", "Suppresses tree connector lines", ENGINE, "JTree.lineStyle", "None"),
    case("lists-001", "lists", "Themes JList branch", ENGINE, "component instanceof JList", "themeList"),
    case("lists-002", "lists", "Themes list background", ENGINE, '"List.background"'),
    case("lists-003", "lists", "Themes list foreground", ENGINE, '"List.foreground"'),
    case("lists-004", "lists", "Themes list selection", ENGINE, '"List.selectionBackground"'),
    case("lists-005", "lists", "Themes list inactive selection", ENGINE, '"List.selectionInactiveBackground"'),
    case("text-001", "text editors", "Themes JTextComponent branch", ENGINE, "component instanceof JTextComponent", "themeTextComponent"),
    case("text-002", "text editors", "Themes TextArea background", ENGINE, '"TextArea.background"'),
    case("text-003", "text editors", "Themes TextArea caret", ENGINE, '"TextArea.caretForeground"'),
    case("text-004", "text editors", "Themes TextArea selection", ENGINE, '"TextArea.selectionBackground"'),
    case("text-005", "text editors", "Themes TextField background", ENGINE, '"TextField.background"'),
    case("text-006", "text editors", "Themes TextField caret", ENGINE, '"TextField.caretForeground"'),
    case("text-007", "text editors", "Themes TextPane background", ENGINE, '"TextPane.background"'),
    case("text-008", "text editors", "Themes TextPane selection", ENGINE, '"TextPane.selectionBackground"'),
    case("text-009", "text editors", "Themes EditorPane background", ENGINE, '"EditorPane.background"'),
    case("text-010", "text editors", "Themes EditorPane links", ENGINE, '"EditorPane.linkForeground"'),
    case("text-011", "text editors", "Themes PasswordField background", ENGINE, '"PasswordField.background"'),
    case("text-012", "text editors", "Themes formatted text fields", ENGINE, '"FormattedTextField.background"'),
    case("text-013", "text editors", "Restores text caret/selection colors", ENGINE, "textCaret", "textSelection", "textSelectedText"),
    case("combos-001", "combo boxes", "Themes JComboBox branch", ENGINE, "component instanceof JComboBox", "themeComboBox"),
    case("combos-002", "combo boxes", "Themes combo background", ENGINE, '"ComboBox.background"'),
    case("combos-003", "combo boxes", "Themes combo selection", ENGINE, '"ComboBox.selectionBackground"'),
    case("combos-004", "combo boxes", "Themes combo button colors", ENGINE, '"ComboBox.buttonBackground"', '"ComboBox.buttonArrowColor"'),
    case("combos-005", "combo boxes", "Themes editable combo fields", ENGINE, '"ComboBox.editableBackground"'),
    case("combos-006", "combo boxes", "Themes combo popup", ENGINE, '"ComboBox.popupBackground"'),
    case("buttons-001", "buttons", "Themes AbstractButton branch", ENGINE, "component instanceof AbstractButton", "themeButton"),
    case("buttons-002", "buttons", "Themes button background", ENGINE, '"Button.background"'),
    case("buttons-003", "buttons", "Themes button hover", ENGINE, '"Button.hoverBackground"'),
    case("buttons-004", "buttons", "Themes button pressed", ENGINE, '"Button.pressedBackground"'),
    case("buttons-005", "buttons", "Themes default button", ENGINE, '"Button.default.background"'),
    case("buttons-006", "buttons", "Themes button focus", ENGINE, '"Button.focus"'),
    case("buttons-007", "buttons", "Themes toolbar buttons", ENGINE, '"Button.toolbar.background"'),
    case("buttons-008", "buttons", "Themes toggle button background", ENGINE, '"ToggleButton.background"'),
    case("buttons-009", "buttons", "Themes selected toggle button", ENGINE, '"ToggleButton.selectedBackground"'),
    case("buttons-010", "buttons", "Themes split button", ENGINE, '"SplitButton.background"'),
    case("buttons-011", "buttons", "Themes split button arrows", ENGINE, '"SplitButton.arrowColor"'),
    case("buttons-012", "buttons", "Themes checkbox colors", ENGINE, '"CheckBox.background"', '"CheckBox.icon.checkmarkColor"'),
    case("buttons-013", "buttons", "Themes radio button colors", ENGINE, '"RadioButton.background"', '"RadioButton.icon.centerColor"'),
    case("buttons-014", "buttons", "Themes generic action buttons with translucent custom pills", ENGINE, "themeGenericPillButton", "installPillPainter(button"),
    case("proxy-001", "proxy controls", "Recognizes Proxy Forward action", ENGINE, "PROXY_FORWARD_ACTION", '"forward"'),
    case("proxy-002", "proxy controls", "Recognizes Proxy Drop action", ENGINE, "PROXY_DROP_ACTION", '"drop"'),
    case("proxy-003", "proxy controls", "Tracks proxy action buttons", ENGINE, "proxyActionButtons.add"),
    case("proxy-004", "proxy controls", "Rethemes known proxy buttons", ENGINE, "rethemeKnownProxyActionButtons"),
    case("proxy-005", "proxy controls", "Themes proxy split wrappers", ENGINE, "themeProxySplitWrapper"),
    case("proxy-006", "proxy controls", "Keeps visible proxy focus ring", ENGINE, "focusWidth: 1", "focusColor"),
    case("proxy-007", "proxy controls", "Finds compact Proxy split wrappers with arrow pieces", ENGINE, "containsProxySplitPieces", "containsProxySplitArrowButton"),
    case("proxy-008", "proxy controls", "Prefers compact Proxy split wrappers over wide action clusters", ENGINE, "findNearestProxySplitWrapper", "return compactFallback != null ? compactFallback : actionCluster"),
    case("proxy-009", "proxy controls", "Rounds Proxy split buttons with a custom pill painter instead of square fills", ENGINE, "themeProxySplitButton", "installProxyPillPainter", "ProxyPillButtonUI"),
    case("proxy-010", "proxy controls", "Keeps Proxy split wrapper border-only instead of filled dark glass", ENGINE, "installGlassBorder(jComponent, transparent(theme.button)", "alpha(theme.gold, 58), 999"),
    case("proxy-011", "proxy controls", "Themes non-split Proxy action buttons as pills", ENGINE, "isProxyPillButtonCandidate", "themeProxyPillButton"),
    case("proxy-012", "proxy controls", "Restores custom Proxy button UI and client properties", ENGINE, "buttonUi = button.getUI()", "clearProxyButtonProperties(button)"),
    case("scroll-001", "scrolling", "Themes JScrollPane branch", ENGINE, "component instanceof JScrollPane", "themeScrollPane"),
    case("scroll-002", "scrolling", "Themes ScrollPane background", ENGINE, '"ScrollPane.background"'),
    case("scroll-003", "scrolling", "Themes scroll pane border", ENGINE, '"ScrollPane.border"'),
    case("scroll-004", "scrolling", "Themes JScrollBar branch", ENGINE, "component instanceof JScrollBar"),
    case("scroll-005", "scrolling", "Themes scrollbar thumb", ENGINE, '"ScrollBar.thumb"'),
    case("scroll-006", "scrolling", "Themes scrollbar hover/pressed", ENGINE, '"ScrollBar.hoverThumbColor"', '"ScrollBar.pressedThumbColor"'),
    case("scroll-007", "scrolling", "Themes scrollbar arcs", ENGINE, '"ScrollBar.thumbArc"', '"ScrollBar.trackArc"'),
    case("split-001", "split panes", "Themes JSplitPane shells", ENGINE, "component instanceof JSplitPane"),
    case("split-002", "split panes", "Themes split pane background", ENGINE, '"SplitPane.background"'),
    case("split-003", "split panes", "Themes split pane divider", ENGINE, '"SplitPaneDivider.background"'),
    case("split-004", "split panes", "Themes split pane dragging", ENGINE, '"SplitPaneDivider.draggingColor"'),
    case("menus-001", "menus and popups", "Themes JPopupMenu branch", ENGINE, "component instanceof JPopupMenu"),
    case("menus-002", "menus and popups", "Themes popup menu defaults", ENGINE, '"PopupMenu.background"', '"PopupMenu.separatorColor"'),
    case("menus-003", "menus and popups", "Themes JMenuBar branch", ENGINE, "component instanceof JMenuBar"),
    case("menus-004", "menus and popups", "Themes menu bar defaults", ENGINE, '"MenuBar.background"', '"MenuBar.selectionBackground"'),
    case("menus-005", "menus and popups", "Themes menu item defaults", ENGINE, '"MenuItem.background"', '"MenuItem.selectionBackground"'),
    case("menus-006", "menus and popups", "Themes checkbox/radio menu items", ENGINE, '"CheckBoxMenuItem.background"', '"RadioButtonMenuItem.background"'),
    case("menus-007", "menus and popups", "Does not register a theme switching menu", BUILD, "Theme-switching menu registration should not be present"),
    case("menus-008", "menus and popups", "Refreshes late menu and popup menu components", ENGINE, "component instanceof JMenuBar", "component instanceof JMenuItem", "component instanceof JPopupMenu"),
    case("menus-009", "menus and popups", "Keeps popup menu shells low-alpha over assets", ENGINE, "component instanceof JPopupMenu", "makeWallpaperGlass(component, alpha(theme.base.darker(), 24)"),
    case("menus-010", "menus and popups", "Keeps menu items from painting dark rectangles", ENGINE, "private void themeMenuItem", "button.setOpaque(false)", "button.setContentAreaFilled(false)"),
    case("dialogs-001", "dialogs", "Themes JOptionPane branch", ENGINE, "component instanceof JOptionPane"),
    case("dialogs-002", "dialogs", "Themes option pane background", ENGINE, '"OptionPane.background"'),
    case("dialogs-003", "dialogs", "Themes option pane title panes", ENGINE, '"OptionPane.warningDialog.titlePane.background"', '"OptionPane.errorDialog.titlePane.background"'),
    case("dialogs-004", "dialogs", "Themes JFileChooser branch", ENGINE, "component instanceof JOptionPane || component instanceof JFileChooser"),
    case("dialogs-005", "dialogs", "Themes file chooser list/details", ENGINE, '"FileChooser.listViewBackground"', '"FileChooser.detailsViewBackground"'),
    case("dialogs-006", "dialogs", "Refreshes late option panes and file choosers", ENGINE, "component instanceof JOptionPane", "component instanceof JFileChooser", "scheduleCoalescedPopupThemeRefresh"),
    case("dialogs-007", "dialogs", "Keeps option panes and file choosers as low-alpha glass", ENGINE, "component instanceof JOptionPane || component instanceof JFileChooser", "makeWallpaperGlass(component, alpha(theme.base.darker(), 28)"),
    case("status-001", "status and labels", "Themes JLabel branch", ENGINE, "component instanceof JLabel", "themeLabel"),
    case("status-002", "status and labels", "Themes label defaults", ENGINE, '"Label.foreground"', '"Label.disabledForeground"'),
    case("status-003", "status and labels", "Themes link colors", ENGINE, '"Link.foreground"', '"Component.linkColor"'),
    case("status-004", "status and labels", "Themes status bars", ENGINE, "isStatusBar"),
    case("misc-001", "misc controls", "Themes JToolBar branch", ENGINE, "component instanceof JToolBar", '"ToolBar.background"'),
    case("misc-002", "misc controls", "Themes JToolTip branch", ENGINE, "component instanceof JToolTip", '"ToolTip.background"'),
    case("misc-003", "misc controls", "Themes JProgressBar branch", ENGINE, "component instanceof JProgressBar", '"ProgressBar.background"'),
    case("misc-004", "misc controls", "Themes JSlider branch", ENGINE, "component instanceof JSlider", '"Slider.trackColor"'),
    case("misc-005", "misc controls", "Themes JSpinner branch", ENGINE, "component instanceof JSpinner", '"Spinner.buttonBackground"'),
    case("misc-006", "misc controls", "Themes separators", ENGINE, "component instanceof JSeparator", '"Separator.foreground"'),
    case("misc-007", "misc controls", "Themes desktop panes", ENGINE, "component instanceof JDesktopPane", '"DesktopPane.background"'),
    case("store-001", "theme store", "Uses animated preview panel", STORE, "AnimatedPreviewPanel", "loadPreview"),
    case("store-002", "theme store", "Loads GIF preview resources", STORE, "ImageIcon", "previewResource"),
    case("store-003", "theme store", "Preloads wallpapers and previews", STORE, "preloadWallpapers", "loadPreview(theme)"),
    case("store-004", "theme store", "Shows hero preview", STORE, "heroPreviewPanel", "HERO_PREVIEW_WIDTH"),
    case("store-005", "theme store", "Uses larger theme cards", STORE, "CARD_WIDTH = 324", "CARD_HEIGHT = 314"),
    case("store-006", "theme store", "Theme cards are keyboard activatable", STORE, "KeyEvent.VK_ENTER", "KeyEvent.VK_SPACE"),
    case("store-007", "theme store", "Theme cards expose accessibility action", STORE, "AccessibleAction", "AccessibleRole.PUSH_BUTTON"),
    case("store-008", "theme store", "Shows selected/current state", STORE, "Current", "selectedTheme"),
    case("store-009", "theme store", "Shows palette swatches", STORE, "ColorSwatch", "colorSwatches"),
    case("store-010", "theme store", "Renders responsive wrapped cards", STORE, "CardFlowPanel", "responsiveCardWidth"),
    case("store-011", "theme store", "Keeps theme switching in tab", README, "Theme changes are intentionally kept inside the extension tab"),
    case("store-012", "theme store", "Loads registered detail strip assets", STORE, "loadDetail", "detailResource"),
    case("store-013", "theme store", "Loads registered motion GIF overlays", STORE, "loadMotion", "motionResource"),
    case("store-014", "theme store", "Refreshes card visuals after reapply", STORE, "setSelectedTheme", "actionLabel.setBackground", "previewPanel.repaint()"),
    case("store-015", "theme store", "Keeps extension tab wallpaper visible", STORE, "storeWallpaperAlpha", "Math.min(theme.imageAlpha, 204)"),
    case("visual-001", "visual polish", "Paints wallpaper-backed shells through reusable glass borders", ENGINE, "private void makeWallpaperGlass", "installGlassBorder(jComponent, background, alpha(foreground, 52))"),
    case("visual-002", "visual polish", "Uses rounded translucent glass border painting", ENGINE, "GlassSurfaceBorder extends AbstractBorder", "fillRoundRect", "drawRoundRect"),
    case("visual-003", "visual polish", "Keeps wallpaper-backed text editors transparent over assets", ENGINE, "textComponent.setOpaque(!wallpaperBacked)", "readableSurface(theme, surface, wallpaperBacked)"),
    case("visual-004", "visual polish", "Keeps wallpaper-backed tables transparent over assets", ENGINE, "table.setOpaque(!wallpaperBacked)", "tintSelectionRenderer(component, selected"),
    case("visual-005", "visual polish", "Keeps wallpaper-backed trees transparent over assets", ENGINE, "tree.setOpaque(!wallpaperBacked)", "DefaultTreeCellRenderer"),
    case("visual-006", "visual polish", "Keeps wallpaper-backed lists transparent over assets", ENGINE, "list.setOpaque(!wallpaperBacked)", "List.selectionBackground"),
    case("visual-007", "visual polish", "Removes regular label background blocks", ENGINE, "label.setOpaque(false)", "label.setBackground(transparent(theme.base))"),
    case("visual-008", "visual polish", "Uses readable button selection instead of dark accent text highlights", ENGINE, '"TextArea.selectionBackground", theme.button', '"EditorPane.selectionBackground", theme.button'),
    case("visual-009", "visual polish", "Recognizes Burp message viewers as readable content needing glass treatment", ENGINE, "isReadableContentToken", "message", "viewer"),
    case("visual-010", "visual polish", "Applies local component theme before extension callbacks after suite-wide disable", EXTENSION, "themeEngine.themeLocalComponentTree(module.component(), currentTheme);", "module.themeChanged(currentTheme);"),
    case("visual-011", "visual polish", "Avoids unsupported borders on Swing viewports", ENGINE, "private void installGlassBorder", "component instanceof JViewport", "return;"),
    case("visual-012", "visual polish", "Stops labels and buttons from making whole containers readable dark panels", ENGINE, "child instanceof JTextComponent", "child instanceof JComboBox"),
    case("visual-013", "visual polish", "Keeps tabs translucent instead of full dark blocks", ENGINE, "transparent(theme.base)", "paintTabBackground", "alpha(theme.button, 92)"),
    case("visual-014", "visual polish", "Verifies popup defaults stay translucent", "tools/verify_visual_polish.py", "checkLowAlphaDefault(\"PopupMenu.background\", 40)", "checkLowAlphaDefault(\"ComboBox.popupBackground\", 48)"),
    case("visual-015", "visual polish", "Verifies menu items and popup dialogs avoid opaque fills", "tools/verify_visual_polish.py", "popup menu items must not paint dark rectangles", "option panes must not paint opaque dark shells"),
    case("visual-016", "visual polish", "Build runs top-level window polish verifier when a display is available", BUILD, "WINDOW_POLISH_VERIFIER", "verify_window_polish.py"),
    case("themes-001", "theme registry", "Ships Great Plateau theme", THEME, "GREAT_PLATEAU", "preview-great-plateau.gif"),
    case("themes-002", "theme registry", "Ships snow-region replacement theme", THEME, "Hebra Snowfield", "preview-ancient-slate.gif"),
    case("themes-003", "theme registry", "Ships Korok Forest theme", THEME, "KOROK_FOREST", "preview-korok-forest.gif"),
    case("themes-004", "theme registry", "Ships red Blood Moon theme", THEME, "BLOOD_MOON", "preview-blood-moon.gif"),
    case("themes-005", "theme registry", "Ships new purple Gloom Depths theme", THEME, "GLOOM_DEPTHS", "preview-gloom-depths.gif"),
    case("themes-006", "theme registry", "Persists stable theme slug", THEME, "fromStoredValue", "theme.slug.equals(value)"),
    case("colors-001", "colors", "Validates dark surface readability", VALIDATOR, "text/base.darker", "buttonText/button.darker"),
    case("colors-002", "colors", "Validates accent-filled control readability", VALIDATOR, "base.darker/accent"),
    case("colors-003", "colors", "Uses solid dark button colors for hover styles", ENGINE, 'hoverBackground: " + rgb(theme.button)', 'pressedBackground: " + rgb(theme.button.darker())'),
    case("assets-001", "assets", "Validator parses preview resource", VALIDATOR, '"preview"', "preview.removeprefix"),
    case("assets-002", "assets", "Validator checks GIF signatures", VALIDATOR, "GIF_SIGNATURES", "gif_info"),
    case("assets-003", "assets", "Validator requires multi-frame GIF", VALIDATOR, "frames < 8"),
    case("assets-004", "assets", "Validator rejects stale GIFs", VALIDATOR, "Inactive GIF asset remains"),
    case("assets-005", "assets", "Smoke expects preview assets", SMOKE, 'str(theme["preview"])'),
    case("assets-006", "assets", "Smoke expects detail strip assets", SMOKE, 'str(theme["detail"])'),
    case("assets-007", "assets", "Build checks Gloom enum", BUILD, "GLOOM_DEPTHS"),
    case("assets-008", "assets", "Build checks previewResource", BUILD, "previewResource"),
    case("assets-009", "assets", "Authoring docs include preview GIF contract", AUTHORING, "Preview GIF", "assets/preview-my-theme.gif"),
    case("assets-010", "assets", "Validator parses GIF blocks for exact frame counts", VALIDATOR, "skip_gif_sub_blocks", "unknown GIF block"),
    case("assets-011", "assets", "Validator checks detail strip assets", VALIDATOR, "DETAIL_SIZE", '"detail"'),
    case("assets-012", "assets", "Build checks detailResource", BUILD, "detailResource"),
    case("assets-013", "assets", "Validator checks motion GIF assets", VALIDATOR, "MOTION_SIZE", '"motion"'),
    case("assets-014", "assets", "Build checks motionResource", BUILD, "motionResource"),
    case("assets-015", "assets", "Smoke uses Windows-safe temp asset files", SMOKE, 'TemporaryDirectory(prefix="burptheme-gif-"', 'TemporaryDirectory(prefix="burptheme-png-"'),
    case("restore-001", "restore", "Registers unload handler", EXTENSION, "registerUnloadingHandler", "unloadExtension"),
    case("restore-002", "restore", "Deregisters registrations", EXTENSION, "deregisterRegistrations"),
    case("restore-003", "restore", "Stops theme timers", ENGINE, "stopThemeTimers", "themeTimers.clear()"),
    case("restore-004", "restore", "Removes AWT listener", ENGINE, "removeWindowListener"),
    case("restore-005", "restore", "Restores component state", ENGINE, "ComponentState", "restore(entry.getKey())"),
    case("restore-006", "restore", "Restores UI defaults", ENGINE, "restoreUiDefaults", "originalUiDefaults.clear()"),
    case("restore-007", "restore", "Cleans proxy wrappers", ENGINE, "proxySplitWrappers.clear()"),
    case("restore-008", "restore", "Pre-composites translucent surfaces before assigning opaque component backgrounds", ENGINE, "component.setBackground(background.getAlpha() >= 255 ? opaque(background) : compositeOverThemeSurface(background))"),
    case("restore-009", "restore", "Flushes store GIF and wallpaper caches on unload", EXTENSION, "BurpThemeStorePanel.clearImageCaches", "ThemeCrestIcon.clearAvatarCache"),
    case("restore-010", "restore", "Flushes engine wallpaper cache on unload", EXTENSION, "BurpThemeEngine.clearBackgroundImageCache"),
    case("restore-011", "restore", "Detaches listeners before restored UI refresh", ENGINE, "detachThemeListeners", "refreshRestoredWindowUis"),
    case("restore-013", "restore", "Synchronizes image cache load and clear paths", STORE, "static synchronized ImageIcon loadPreview", "static synchronized void clearImageCaches"),
    case("restore-012", "restore", "Installs theme UI defaults as UIResources", ENGINE, "uiResourceDefaults", "ColorUIResource", "BorderUIResource"),
    case("security-001", "security", "Smoke forbids HTTP handler registration", SMOKE, "registerHttpHandler", "Unexpected traffic-affecting API"),
    case("security-002", "security", "Smoke forbids proxy intercept mutation", SMOKE, "InterceptedRequest", "InterceptedResponse"),
    case("security-003", "security", "Smoke validates classfile target", SMOKE, "max_class_major_for_release", "class_major"),
    case("runtime-001", "runtime smoke", "Uses platform classpath separator", RUNTIME_SMOKE, "os.pathsep.join"),
    case("runtime-002", "runtime smoke", "Uses Windows process group handling", RUNTIME_SMOKE, "CREATE_NEW_PROCESS_GROUP", "CTRL_BREAK_EVENT"),
    case("runtime-003", "runtime smoke", "Avoids full system temp during Burp startup", RUNTIME_SMOKE, "BURPTHEME_RUNTIME_TMP", "-Djava.io.tmpdir"),
    case("runtime-004", "runtime smoke", "Can drive private Xvfb startup dialogs", RUNTIME_SMOKE, "drive_startup_ui", "--no-drive-startup-ui"),
    case("runtime-005", "runtime smoke", "Avoids Community disk project by default", RUNTIME_SMOKE, "--use-project-file", "Community edition may reject"),
    case("runtime-006", "runtime smoke", "Uses workspace temp root by default", RUNTIME_SMOKE, "ROOT / \"build\" / \"runtime-smoke-tmp\""),
    case("runtime-007", "runtime smoke", "Keeps startup full-suite refresh train short", EXTENSION, "STARTUP_REFRESH_DELAYS_MS = {1800}"),
    case("runtime-008", "runtime smoke", "Waits after initialization for delayed refresh stability", RUNTIME_SMOKE, "--post-init-wait", "post_init_failed"),
)


FORBIDDEN_TOKENS: tuple[tuple[str, str, str], ...] = (
    (EXTENSION, "STARTUP_REFRESH_DELAYS_MS = {100, 350, 900, 1800, 3500, 8000, 15000, 25000}", "old long startup refresh train"),
    (ENGINE, "SwingUtilities.updateComponentTreeUI(component);", "live Burp-owned proxy wrapper UI refresh"),
    (ENGINE, "scheduleCoalescedFrameThemeRefresh(window, 220);", "full-frame refresh on every tab selection"),
    (ENGINE, "themeVisiblePopupWindows(currentTheme);", "visible popup double-pass during scheduled suite refresh"),
    (ENGINE, 'selectionInactiveBackground: " + rgb(alpha(theme.accent, 70))', "alpha-losing inactive selection style"),
    (ENGINE, 'hoverBackground: " + rgb(alpha(theme.accent', "alpha-losing button hover style"),
    (ENGINE, "textComponent.setBackground(alpha(", "translucent opaque text component background"),
    (ENGINE, "table.setBackground(alpha(", "translucent opaque table background"),
    (ENGINE, "tree.setBackground(alpha(", "translucent opaque tree background"),
    (ENGINE, "list.setBackground(alpha(", "translucent opaque list background"),
    (ENGINE, "comboBox.setBackground(alpha(", "translucent opaque combo box background"),
    (ENGINE, "boolean shadedHeading", "opaque shaded label heading backgrounds"),
    (STORE, "selectedTheme == selected", "theme card no-op refresh skip"),
    (STORE, "actionLabel.setOpaque(true)", "opaque action badge background"),
    (STORE, "g2.fillRect(0, Math.max(0, getHeight() - 4), getWidth(), 4)", "full-width square selected-card underline"),
    (ENGINE, "makeSurface(component, alpha(theme.base.darker(), wallpaperBacked ?", "opaque wallpaper-backed fallback surface"),
    (ENGINE, "|| child instanceof AbstractButton)", "buttons causing dark readable container overlays"),
    (ENGINE, "|| child instanceof JLabel", "labels causing dark readable container overlays"),
    (ENGINE, "makeSurface(rootPane, opaque(theme.base.darker()))", "opaque popup root pane surface"),
    (ENGINE, "makeSurface(contentPane, opaque(theme.base.darker()))", "opaque popup content pane surface"),
    (ENGINE, '"ComboBox.popupBackground", opaque(theme.base.darker())', "opaque combo popup default"),
    (ENGINE, '"PopupMenu.background", opaque(theme.base.darker())', "opaque popup menu default"),
    (ENGINE, "button.setBackground(opaque(theme.base.darker()))", "opaque dark menu/button background"),
    (ENGINE, "makeSurface(component, alpha(theme.base.darker(), 242)", "opaque option/file chooser popup surface"),
    (RUNTIME_SMOKE, 'f"{args.extension_jar}:{args.burp_jar}"', "hard-coded Unix classpath separator"),
)


def load_sources() -> dict[str, str]:
    sources: dict[str, str] = {}
    for coverage_case in CASES:
        if coverage_case.path not in sources:
            sources[coverage_case.path] = (ROOT / coverage_case.path).read_text(encoding="utf-8")
    for path, _, _ in FORBIDDEN_TOKENS:
        if path not in sources:
            sources[path] = (ROOT / path).read_text(encoding="utf-8")
    return sources


def main() -> int:
    errors: list[str] = []
    if len(CASES) < MIN_CASES:
        errors.append(f"Only {len(CASES)} coverage cases defined; expected at least {MIN_CASES}.")

    seen_ids: set[str] = set()
    for coverage_case in CASES:
        if coverage_case.case_id in seen_ids:
            errors.append(f"Duplicate coverage case id: {coverage_case.case_id}")
        seen_ids.add(coverage_case.case_id)
        if not coverage_case.tokens:
            errors.append(f"{coverage_case.case_id} has no tokens.")

    sources = load_sources()
    for coverage_case in CASES:
        source = sources[coverage_case.path]
        for token in coverage_case.tokens:
            if token not in source:
                errors.append(f"{coverage_case.case_id} missing token in {coverage_case.path}: {token}")
    for path, token, description in FORBIDDEN_TOKENS:
        if token in sources[path]:
            errors.append(f"Forbidden {description} remains in {path}: {token}")

    area_counts = Counter(coverage_case.area for coverage_case in CASES)
    if len(area_counts) < 20:
        errors.append(f"Only {len(area_counts)} coverage areas defined; expected at least 20.")

    if errors:
        for error in errors:
            print(error)
        return 1

    print(f"Verified {len(CASES)} UI coverage checks across {len(area_counts)} areas.")
    for area, count in sorted(area_counts.items()):
        print(f"{area}: {count}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
