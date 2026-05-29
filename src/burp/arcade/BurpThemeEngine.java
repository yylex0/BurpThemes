package burp.arcade;

import burp.api.montoya.MontoyaApi;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonModel;
import javax.swing.Icon;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDesktopPane;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JRootPane;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JToolBar;
import javax.swing.JToolTip;
import javax.swing.JTree;
import javax.swing.JViewport;
import javax.swing.JWindow;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.ButtonUI;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.TabbedPaneUI;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicGraphicsUtils;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.text.JTextComponent;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeCellRenderer;
import java.awt.AWTEvent;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.ContainerEvent;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

final class BurpThemeEngine
{
    private static final int MAX_THEME_COMPONENTS_PER_PASS = 25000;
    private static final int MAX_THEME_DEPTH = 256;
    private static final long FOCUS_REFRESH_THROTTLE_NANOS = 700_000_000L;
    private static final String PROXY_FORWARD_ACTION = "forward";
    private static final String PROXY_DROP_ACTION = "drop";
    private static final String PROXY_PILL_BUTTON_PROPERTY = "burptheme.proxyPillButton";
    private static final String PROXY_PILL_FILL_PROPERTY = "burptheme.proxyPillFill";
    private static final String PROXY_PILL_HOVER_FILL_PROPERTY = "burptheme.proxyPillHoverFill";
    private static final String PROXY_PILL_PRESSED_FILL_PROPERTY = "burptheme.proxyPillPressedFill";
    private static final String PROXY_PILL_OUTLINE_PROPERTY = "burptheme.proxyPillOutline";
    private static final String GLASS_FILL_ALPHA_PROPERTY = "burptheme.glassFillAlpha";
    private static final String GLASS_BORDER_ALPHA_PROPERTY = "burptheme.glassBorderAlpha";
    private static final Class<?>[] TABLE_RENDERER_TYPES = {
        Object.class, String.class, Number.class, Integer.class, Long.class, Short.class, Boolean.class, Icon.class
    };

    private final MontoyaApi api;
    private final AtomicBoolean extensionUnloaded;
    private final Map<RootPaneContainer, BurpBackgroundPanel> installedBackgrounds = new IdentityHashMap<>();
    private final Map<Object, Object> originalUiDefaults = new LinkedHashMap<>();
    private final Map<Object, Object> installedUiDefaults = new LinkedHashMap<>();
    private final Map<JRootPane, Map<String, Object>> originalRootPaneProperties = new IdentityHashMap<>();
    private final Map<JTabbedPane, ChangeListener> tabThemeListeners = new IdentityHashMap<>();
    private final Map<AbstractButton, PropertyChangeListener> buttonThemeListeners = new IdentityHashMap<>();
    private final Map<JLayeredPane, ComponentListener> backgroundResizeListeners = new IdentityHashMap<>();
    private final Map<Window, Timer> pendingFrameRefreshes = new IdentityHashMap<>();
    private final Map<Window, Timer> pendingPopupRefreshes = new IdentityHashMap<>();
    private final Map<Window, Long> recentWindowThemeRefreshes = new IdentityHashMap<>();
    private final Map<Component, ComponentState> componentStates = new IdentityHashMap<>();
    private final Set<AbstractButton> proxyActionButtons = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<JComponent> proxySplitWrappers = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Timer> themeTimers = new HashSet<>();
    private final AtomicInteger themeGeneration = new AtomicInteger();

    private Component localRoot;
    private boolean suiteTintEnabled = true;
    private boolean windowListenerInstalled;
    private boolean applyingGlobalTheme;
    private boolean restoringTheme;
    private AWTEventListener windowThemeListener;
    private BurpTheme currentTheme = BurpTheme.DEFAULT_THEME;

    BurpThemeEngine(MontoyaApi api, AtomicBoolean extensionUnloaded)
    {
        this.api = api;
        this.extensionUnloaded = extensionUnloaded;
    }

    void setLocalRoot(Component localRoot)
    {
        this.localRoot = localRoot;
    }

    static void preloadBackgroundImages()
    {
        BurpBackgroundPanel.preloadAll();
    }

    static void clearBackgroundImageCache()
    {
        BurpBackgroundPanel.clearImageCache();
    }

    void setSuiteTintEnabled(boolean enabled)
    {
        suiteTintEnabled = enabled;
    }

    boolean isSuiteTintEnabled()
    {
        return suiteTintEnabled && !extensionUnloaded.get() && !restoringTheme;
    }

    void applyTheme(BurpTheme theme)
    {
        currentTheme = theme;
        themeGeneration.incrementAndGet();
        if (isSuiteTintEnabled())
        {
            applyGlobalBurpTheme(theme);
            scheduleSuiteThemeRefresh(700);
        }
    }

    void themeLocalComponentTree(Component root, BurpTheme theme)
    {
        currentTheme = theme;
        if (!(root instanceof Container))
        {
            return;
        }
        Set<Component> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        themeContainerChildren((Container) root, theme, 0, MAX_THEME_COMPONENTS_PER_PASS, visited, true);
        root.revalidate();
        root.repaint();
    }

    private void applyGlobalBurpTheme(BurpTheme theme)
    {
        if (applyingGlobalTheme || !isSuiteTintEnabled())
        {
            return;
        }
        applyingGlobalTheme = true;
        try
        {
            installWindowListener();
            pruneDetachedThemeState();
            installColorDefaults(theme);
            cleanupLegacyBackgroundWrappers();
            for (Window window : Window.getWindows())
            {
                if (isThemeableWindow(window))
                {
                    int budget = themeWindowBudget(window);
                    int themed = themeWindow(window, theme, budget);
                    if (themed >= budget)
                    {
                        scheduleCoalescedFrameThemeRefresh(window, 240);
                    }
                }
                else if (window != null && window.isShowing() && isThemeablePopupWindow(window))
                {
                    themePopupWindow(window, theme);
                }
            }
            rethemeKnownProxyActionButtons(theme);
        }
        finally
        {
            applyingGlobalTheme = false;
        }
    }

    private int themeWindow(Window window, BurpTheme theme, int budget)
    {
        if (!(window instanceof JFrame))
        {
            return 0;
        }
        boolean wallpaperBacked = installBurpBackground(window, theme);
        int themed = 0;
        Set<Component> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        if (window instanceof RootPaneContainer)
        {
            RootPaneContainer rootPaneContainer = (RootPaneContainer) window;
            themeRootPaneContainer(rootPaneContainer, theme);
            if (themed < budget)
            {
                themed += themeBurpComponent(rootPaneContainer.getContentPane(), theme, 0, budget - themed, visited, wallpaperBacked);
            }
            if (themed < budget)
            {
                themed += themeBurpComponent(rootPaneContainer.getLayeredPane(), theme, 0, budget - themed, visited, wallpaperBacked);
            }
            if (themed < budget)
            {
                themed += themeBurpComponent(rootPaneContainer.getGlassPane(), theme, 0, budget - themed, visited, wallpaperBacked);
            }
        }
        if (themed < budget && window instanceof JFrame && ((JFrame) window).getJMenuBar() != null)
        {
            themed += themeBurpComponent(((JFrame) window).getJMenuBar(), theme, 0, budget - themed, visited, wallpaperBacked);
        }
        window.revalidate();
        window.repaint();
        return themed;
    }

    private int themePopupWindow(Window window, BurpTheme theme)
    {
        if (!(window instanceof JDialog || window instanceof JWindow))
        {
            return 0;
        }
        boolean wallpaperBacked = installBurpBackground(window, theme);
        int themed = 0;
        Set<Component> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        if (window instanceof RootPaneContainer)
        {
            RootPaneContainer rootPaneContainer = (RootPaneContainer) window;
            JRootPane rootPane = rootPaneContainer.getRootPane();
            if (rootPane != null)
            {
                rememberComponentState(rootPane);
                makeTransparent(rootPane, transparent(theme.base), theme.text);
                themeRootPaneTitleBar(rootPane, theme);
            }
            Container contentPane = rootPaneContainer.getContentPane();
            if (contentPane instanceof JComponent)
            {
                rememberComponentState(contentPane);
                makeTransparent(contentPane, transparent(theme.base), theme.text);
                themed += themeBurpComponent(contentPane, theme, 0, MAX_THEME_COMPONENTS_PER_PASS - themed, visited, wallpaperBacked);
            }
            else if (contentPane != null && themed < MAX_THEME_COMPONENTS_PER_PASS)
            {
                themed += themeBurpComponent(contentPane, theme, 0, MAX_THEME_COMPONENTS_PER_PASS - themed, visited, wallpaperBacked);
            }
            JLayeredPane layeredPane = rootPaneContainer.getLayeredPane();
            if (layeredPane != null && themed < MAX_THEME_COMPONENTS_PER_PASS)
            {
                themed += themeBurpComponent(layeredPane, theme, 0, MAX_THEME_COMPONENTS_PER_PASS - themed, visited, wallpaperBacked);
            }
            Component glassPane = rootPaneContainer.getGlassPane();
            if (glassPane != null && themed < MAX_THEME_COMPONENTS_PER_PASS)
            {
                themed += themeBurpComponent(glassPane, theme, 0, MAX_THEME_COMPONENTS_PER_PASS - themed, visited, wallpaperBacked);
            }
        }
        else if (window instanceof Container)
        {
            themed += themeContainerChildren((Container) window, theme, 0, MAX_THEME_COMPONENTS_PER_PASS, visited, wallpaperBacked);
        }
        window.revalidate();
        window.repaint();
        return themed;
    }

    private boolean isThemeableWindow(Window window)
    {
        return window instanceof JFrame;
    }

    private boolean isThemeablePopupWindow(Window window)
    {
        return window instanceof JDialog || window instanceof JWindow;
    }

    private boolean canThemeFrameNow(Window window)
    {
        return isThemeableWindow(window);
    }

    private void installColorDefaults(BurpTheme theme)
    {
        Color shell = transparent(theme.base);
        Color surface = alpha(theme.base.darker(), 36);
        Color panel = transparent(theme.base);
        Color panelAlt = alpha(theme.horizon.darker(), 42);
        Color control = alpha(theme.horizon.darker(), 68);
        Color textSurface = alpha(theme.base.darker(), 48);
        Color textSurfaceAlt = alpha(theme.horizon.darker(), 52);
        Color textControl = alpha(theme.horizon.darker(), 68);
        Color selection = alpha(theme.button, 150);
        Color border = alpha(theme.accent, 44);
        Object[] defaults = {
            "Component.arc", Integer.valueOf(12),
            "Button.arc", Integer.valueOf(12),
            "ToggleButton.arc", Integer.valueOf(12),
            "ComboBox.arc", Integer.valueOf(12),
            "TextComponent.arc", Integer.valueOf(10),
            "ProgressBar.arc", Integer.valueOf(999),
            "control", theme.horizon.darker(),
            "info", theme.horizon.darker(),
            "RootPane.background", shell,
            "TitlePane.background", theme.base,
            "TitlePane.foreground", theme.text,
            "TitlePane.inactiveBackground", theme.base.darker(),
            "TitlePane.inactiveForeground", theme.muted,
            "nimbusBase", theme.horizon,
            "nimbusBlueGrey", theme.base,
            "nimbusFocus", alpha(theme.accent, 130),
            "Panel.background", shell,
            "Viewport.background", shell,
            "ScrollPane.background", shell,
            "ScrollPane.border", BorderFactory.createLineBorder(border),
            "SplitPane.background", shell,
            "SplitPane.border", BorderFactory.createEmptyBorder(),
            "SplitPaneDivider.background", shell,
            "SplitPaneDivider.border", BorderFactory.createLineBorder(alpha(theme.base.darker(), 130)),
            "SplitPaneDivider.draggingColor", alpha(theme.accent, 100),
            "TabbedPane.background", shell,
            "TabbedPane.contentAreaColor", shell,
            "TabbedPane.tabAreaBackground", shell,
            "TabbedPane.selected", alpha(theme.button, 98),
            "TabbedPane.selectedBackground", alpha(theme.button, 98),
            "TabbedPane.hoverColor", alpha(theme.accent, 28),
            "TabbedPane.focusColor", alpha(theme.accent, 62),
            "TabbedPane.underlineColor", theme.gold,
            "TabbedPane.inactiveUnderlineColor", alpha(theme.accent, 72),
            "TabbedPane.disabledUnderlineColor", alpha(theme.muted, 50),
            "TabbedPane.tabSeparatorColor", alpha(theme.accent, 52),
            "TabbedPane.tabSelectionHeight", Integer.valueOf(2),
            "TabbedPane.contentSeparatorColor", alpha(theme.accent, 44),
            "TabbedPane.showContentSeparator", Boolean.TRUE,
            "TabbedPane.hasFullBorder", Boolean.FALSE,
            "TabbedPane.tabArc", Integer.valueOf(8),
            "TabbedPane.cardTabArc", Integer.valueOf(8),
            "TabbedPane.foreground", theme.text,
            "TabbedPane.selectedForeground", theme.gold,
            "TabbedPane.inactiveForeground", theme.text,
            "TabbedPane.disabledForeground", theme.muted,
            "TabbedPane.selectHighlight", alpha(theme.gold, 90),
            "TabbedPane.unselectedBackground", transparent(theme.base),
            "TabbedPane.focus", alpha(theme.accent, 62),
            "TabbedPane.highlight", alpha(theme.gold, 42),
            "TabbedPane.light", alpha(theme.text, 22),
            "TabbedPane.shadow", alpha(theme.base.darker(), 42),
            "TabbedPane.darkShadow", alpha(theme.base.darker(), 70),
            // BasicTabbedPaneUI fills the tab + content rects with an opaque slab unless these
            // are false (they are otherwise undefined under FlatLaf) -> that slab is the "black
            // behind tab text". lightHighlight backs paintText's highlight path.
            "TabbedPane.tabsOpaque", Boolean.FALSE,
            "TabbedPane.contentOpaque", Boolean.FALSE,
            "TabbedPane.lightHighlight", alpha(theme.text, 22),
            // --- Global base tokens: without these, uncategorized Swing/AWT components fall back
            // to FlatLaf's stock dark grey. Background-like tokens use low dark frosts (never
            // alpha-0) so an opaque consumer reads as themed dark, not black/garbage.
            "background", surface,
            "foreground", theme.text,
            "text", textSurface,
            "textText", theme.text,
            "textHighlight", selection,
            "textHighlightText", theme.buttonText,
            "textInactiveText", theme.muted,
            "controlText", theme.text,
            "controlHighlight", alpha(theme.gold, 70),
            "controlShadow", alpha(theme.base.darker(), 160),
            "controlDkShadow", alpha(theme.base.darker(), 210),
            "controlLtHighlight", alpha(theme.text, 22),
            "window", theme.base,
            "windowText", theme.text,
            "menu", alpha(theme.base.darker(), 24),
            "menuText", theme.text,
            "infoText", theme.text,
            "activeCaption", theme.base,
            "activeCaptionText", theme.text,
            "inactiveCaption", theme.base.darker(),
            "inactiveCaptionText", theme.muted,
            "Separator.height", Integer.valueOf(1),
            // Window caption (FlatLaf decorations) hover/close states
            "TitlePane.buttonHoverBackground", alpha(theme.accent, 70),
            "TitlePane.buttonPressedBackground", alpha(theme.accent, 112),
            "TitlePane.closeHoverBackground", alpha(theme.gold, 150),
            "TitlePane.closePressedBackground", opaque(theme.button),
            "TitlePane.closeHoverForeground", theme.buttonText,
            "TitlePane.closePressedForeground", theme.buttonText,
            // Menu selection underline/accent (otherwise FlatLaf blue) + popup padding
            "MenuItem.underlineSelectionColor", theme.gold,
            "MenuItem.underlineSelectionBackground", alpha(theme.button, 150),
            "Menu.underlineSelectionColor", theme.gold,
            "CheckBoxMenuItem.underlineSelectionColor", theme.gold,
            "RadioButtonMenuItem.underlineSelectionColor", theme.gold,
            "MenuBar.underlineSelectionColor", theme.gold,
            "PopupMenu.borderInsets", new java.awt.Insets(4, 1, 4, 1),
            // ScrollBar track hover/press (thumb states already themed)
            "ScrollBar.hoverTrackColor", alpha(theme.accent, 36),
            "ScrollBar.pressedTrackColor", alpha(theme.accent, 60),
            "ScrollBar.width", Integer.valueOf(11),
            // Slider ticks + SplitPane one-touch arrows
            "Slider.tickColor", alpha(theme.text, 120),
            "SplitPaneDivider.oneTouchArrowColor", theme.text,
            "SplitPaneDivider.oneTouchHoverArrowColor", theme.gold,
            "SplitPaneDivider.oneTouchPressedArrowColor", theme.buttonText,
            "SplitPane.oneTouchButtonColor", control,
            // Table grid as UIManager keys (kept in sync with programmatic setGridColor)
            "Table.gridColor", alpha(theme.accent, 28),
            "Table.showVerticalLines", Boolean.TRUE,
            "Table.showHorizontalLines", Boolean.TRUE,
            "ToolBar.separatorColor", alpha(theme.accent, 120),
            "ToolBar.background", shell,
            "ToolBar.foreground", theme.text,
            "ToolBar.border", BorderFactory.createMatteBorder(0, 0, 1, 0, border),
            "ToolBar.dockingBackground", shell,
            "ToolBar.floatingBackground", panel,
            "Table.background", textSurface,
            "Table.alternateRowColor", textSurface,
            "Table.foreground", theme.text,
            "Table.selectionBackground", selection,
            "Table.selectionForeground", theme.buttonText,
            "Table.selectionInactiveBackground", alpha(theme.accent, 48),
            "Table.selectionInactiveForeground", theme.text,
            "Table.focusCellBackground", textSurface,
            "Table.focusCellForeground", theme.text,
            "Table.dropCellBackground", selection,
            "Table.dropCellForeground", theme.buttonText,
            "Table.dropLineColor", theme.accent,
            "Table.scrollPaneBorder", BorderFactory.createLineBorder(border),
            "TableHeader.background", textSurfaceAlt,
            "TableHeader.foreground", theme.text,
            "TableHeader.cellBorder", BorderFactory.createMatteBorder(0, 0, 1, 1, border),
            "TableHeader.separatorColor", border,
            "TableHeader.hoverBackground", alpha(theme.accent, 48),
            "TableHeader.pressedBackground", alpha(theme.accent, 76),
            "TableHeader.bottomSeparatorColor", border,
            "TableHeader.sortIconColor", theme.gold,
            "Tree.background", textSurface,
            "Tree.foreground", theme.text,
            "Tree.textBackground", textSurface,
            "Tree.textForeground", theme.text,
            "Tree.selectionBackground", selection,
            "Tree.selectionForeground", theme.buttonText,
            "Tree.selectionInactiveBackground", alpha(theme.accent, 48),
            "Tree.selectionInactiveForeground", theme.text,
            "Tree.selectionBorderColor", theme.accent,
            "Tree.hash", transparent(theme.accent),
            "Tree.line", transparent(theme.accent),
            "Tree.dropLineColor", theme.accent,
            "Tree.icon.leafColor", theme.gold,
            "Tree.icon.closedColor", theme.accent,
            "Tree.icon.openColor", theme.gold,
            "Tree.icon.expandedColor", theme.text,
            "Tree.icon.collapsedColor", theme.text,
            "List.background", textSurface,
            "List.foreground", theme.text,
            "List.selectionBackground", selection,
            "List.selectionForeground", theme.buttonText,
            "List.selectionInactiveBackground", alpha(theme.accent, 48),
            "List.selectionInactiveForeground", theme.text,
            "TextArea.background", textSurface,
            "TextArea.foreground", theme.text,
            "TextArea.inactiveBackground", textSurface,
            "TextArea.inactiveForeground", theme.muted,
            "TextArea.disabledBackground", textSurface,
            "TextArea.disabledForeground", theme.muted,
            "TextArea.caretForeground", theme.accent,
            "TextArea.selectionBackground", theme.button,
            "TextArea.selectionForeground", theme.buttonText,
            "TextArea.border", BorderFactory.createLineBorder(border),
            "TextArea.placeholderForeground", theme.muted,
            "TextField.background", textSurface,
            "TextField.foreground", theme.text,
            "TextField.inactiveBackground", textSurface,
            "TextField.inactiveForeground", theme.muted,
            "TextField.disabledBackground", textSurface,
            "TextField.disabledForeground", theme.muted,
            "TextField.caretForeground", theme.accent,
            "TextField.selectionBackground", theme.button,
            "TextField.selectionForeground", theme.buttonText,
            "TextField.border", BorderFactory.createLineBorder(border),
            "TextField.placeholderForeground", theme.muted,
            "TextPane.background", textSurface,
            "TextPane.foreground", theme.text,
            "TextPane.inactiveBackground", textSurface,
            "TextPane.inactiveForeground", theme.muted,
            "TextPane.disabledBackground", textSurface,
            "TextPane.disabledForeground", theme.muted,
            "TextPane.caretForeground", theme.accent,
            "TextPane.selectionBackground", theme.button,
            "TextPane.selectionForeground", theme.buttonText,
            "TextPane.border", BorderFactory.createLineBorder(border),
            "TextPane.placeholderForeground", theme.muted,
            "EditorPane.background", textSurface,
            "EditorPane.foreground", theme.text,
            "EditorPane.inactiveBackground", textSurface,
            "EditorPane.inactiveForeground", theme.muted,
            "EditorPane.disabledBackground", textSurface,
            "EditorPane.disabledForeground", theme.muted,
            "EditorPane.caretForeground", theme.accent,
            "EditorPane.selectionBackground", theme.button,
            "EditorPane.selectionForeground", theme.buttonText,
            "EditorPane.border", BorderFactory.createLineBorder(border),
            "EditorPane.linkForeground", theme.gold,
            "EditorPane.visitedLinkForeground", theme.muted,
            "EditorPane.placeholderForeground", theme.muted,
            "PasswordField.background", textSurface,
            "PasswordField.foreground", theme.text,
            "PasswordField.inactiveBackground", textSurface,
            "PasswordField.inactiveForeground", theme.muted,
            "PasswordField.disabledBackground", textSurface,
            "PasswordField.disabledForeground", theme.muted,
            "PasswordField.caretForeground", theme.accent,
            "PasswordField.selectionBackground", theme.button,
            "PasswordField.selectionForeground", theme.buttonText,
            "PasswordField.border", BorderFactory.createLineBorder(border),
            "PasswordField.placeholderForeground", theme.muted,
            "FormattedTextField.background", textSurface,
            "FormattedTextField.foreground", theme.text,
            "FormattedTextField.inactiveBackground", textSurface,
            "FormattedTextField.inactiveForeground", theme.muted,
            "FormattedTextField.disabledBackground", textSurface,
            "FormattedTextField.disabledForeground", theme.muted,
            "FormattedTextField.caretForeground", theme.accent,
            "FormattedTextField.selectionBackground", theme.button,
            "FormattedTextField.selectionForeground", theme.buttonText,
            "FormattedTextField.border", BorderFactory.createLineBorder(border),
            "FormattedTextField.placeholderForeground", theme.muted,
            "ComboBox.background", textControl,
            "ComboBox.foreground", theme.text,
            "ComboBox.selectionBackground", selection,
            "ComboBox.selectionForeground", theme.buttonText,
            "ComboBox.buttonBackground", textControl,
            "ComboBox.buttonForeground", theme.text,
            "ComboBox.buttonHoverBackground", alpha(theme.accent, 54),
            "ComboBox.buttonPressedBackground", alpha(theme.accent, 88),
            "ComboBox.buttonDarkShadow", theme.base.darker(),
            "ComboBox.buttonHighlight", alpha(theme.gold, 90),
            "ComboBox.buttonShadow", theme.base,
            "ComboBox.disabledBackground", textSurface,
            "ComboBox.disabledForeground", theme.muted,
            "ComboBox.border", BorderFactory.createLineBorder(border),
            "ComboBox.editableBackground", textSurface,
            "ComboBox.popupBackground", alpha(theme.base.darker(), 44),
            "ComboBox.placeholderForeground", theme.muted,
            "ComboBox.buttonArrowColor", theme.text,
            "ComboBox.buttonDisabledArrowColor", theme.muted,
            "ComboBox.buttonHoverArrowColor", theme.gold,
            "ComboBox.buttonPressedArrowColor", theme.buttonText,
            "Button.background", opaque(theme.horizon.darker()),
            "Button.foreground", theme.buttonText,
            "Button.disabledText", alpha(theme.text, 150),
            "Button.disabledForeground", alpha(theme.text, 150),
            "Button.disabledBackground", opaque(theme.horizon.darker()),
            "Button.disabledSelectedBackground", opaque(theme.button.darker()),
            "Button.disabledSelectedForeground", alpha(theme.text, 150),
            "Button.hoverBackground", alpha(theme.accent, 70),
            "Button.pressedBackground", alpha(theme.accent, 112),
            "Button.selectedBackground", opaque(theme.button),
            "Button.default.background", opaque(theme.button),
            "Button.default.foreground", theme.buttonText,
            "Button.default.hoverBackground", alpha(theme.accent, 82),
            "Button.default.pressedBackground", alpha(theme.accent, 120),
            "Button.borderColor", alpha(theme.accent, 120),
            "Button.disabledBorderColor", alpha(theme.accent, 72),
            "Button.default.borderColor", alpha(theme.gold, 140),
            "Button.focus", alpha(theme.accent, 120),
            "Button.highlight", alpha(theme.gold, 70),
            "Button.shadow", alpha(theme.base.darker(), 160),
            "Button.darkShadow", alpha(theme.base.darker(), 210),
            "Button.select", theme.horizon,
            "Button.toolbar.background", opaque(theme.horizon.darker()),
            "Button.toolbar.foreground", theme.buttonText,
            "Button.toolbar.hoverBackground", alpha(theme.accent, 64),
            "Button.toolbar.pressedBackground", alpha(theme.accent, 104),
            "Button.toolbar.selectedBackground", opaque(theme.button),
            "Button.toolbar.disabledBackground", opaque(theme.horizon.darker()),
            "Button.toolbar.disabledSelectedBackground", opaque(theme.button.darker()),
            "Button.toolbar.disabledSelectedForeground", alpha(theme.text, 140),
            "Button.toolbar.disabledForeground", alpha(theme.text, 140),
            "Button.toolbar.borderColor", alpha(theme.accent, 92),
            "SplitButton.background", opaque(theme.horizon.darker()),
            "SplitButton.foreground", theme.buttonText,
            "SplitButton.disabledBackground", opaque(theme.horizon.darker()),
            "SplitButton.disabledForeground", alpha(theme.text, 150),
            "SplitButton.disabledSelectedBackground", opaque(theme.button.darker()),
            "SplitButton.disabledSelectedForeground", alpha(theme.text, 150),
            "SplitButton.hoverBackground", alpha(theme.accent, 70),
            "SplitButton.pressedBackground", alpha(theme.accent, 112),
            "SplitButton.selectedBackground", opaque(theme.button),
            "SplitButton.borderColor", alpha(theme.accent, 122),
            "SplitButton.disabledBorderColor", alpha(theme.accent, 72),
            "SplitButton.separatorColor", alpha(theme.accent, 95),
            "SplitButton.arrowColor", theme.buttonText,
            "SplitButton.disabledArrowColor", alpha(theme.text, 150),
            "ToggleButton.background", opaque(theme.horizon.darker()),
            "ToggleButton.foreground", theme.buttonText,
            "ToggleButton.disabledText", alpha(theme.text, 150),
            "ToggleButton.disabledBackground", opaque(theme.horizon.darker()),
            "ToggleButton.disabledSelectedBackground", opaque(theme.button.darker()),
            "ToggleButton.disabledSelectedForeground", alpha(theme.text, 150),
            "ToggleButton.hoverBackground", alpha(theme.accent, 70),
            "ToggleButton.pressedBackground", alpha(theme.accent, 112),
            "ToggleButton.selectedBackground", opaque(theme.button),
            "ToggleButton.selectedForeground", theme.buttonText,
            "ToggleButton.borderColor", alpha(theme.accent, 120),
            "ToggleButton.disabledBorderColor", alpha(theme.accent, 72),
            "ToggleButton.select", theme.horizon,
            "CheckBox.background", shell,
            "CheckBox.foreground", theme.text,
            "CheckBox.disabledText", theme.muted,
            "CheckBox.icon.background", alpha(theme.base.darker(), 170),
            "CheckBox.icon.borderColor", border,
            "CheckBox.icon.selectedBackground", theme.button,
            "CheckBox.icon.selectedBorderColor", alpha(theme.gold, 130),
            "CheckBox.icon.checkmarkColor", theme.buttonText,
            "CheckBox.icon.focusedBorderColor", theme.accent,
            "RadioButton.background", shell,
            "RadioButton.foreground", theme.text,
            "RadioButton.disabledText", theme.muted,
            "RadioButton.icon.background", alpha(theme.base.darker(), 170),
            "RadioButton.icon.borderColor", border,
            "RadioButton.icon.selectedBackground", theme.button,
            "RadioButton.icon.selectedBorderColor", alpha(theme.gold, 130),
            "RadioButton.icon.centerColor", theme.buttonText,
            "RadioButton.icon.focusedBorderColor", theme.accent,
            "RadioButton.icon.disabledBackground", alpha(theme.base.darker(), 130),
            "RadioButton.icon.disabledBorderColor", alpha(theme.muted, 90),
            "RadioButton.icon.disabledSelectedBackground", alpha(theme.button, 155),
            "RadioButton.icon.disabledSelectedBorderColor", alpha(theme.gold, 95),
            "RadioButton.icon.disabledCenterColor", alpha(theme.buttonText, 150),
            "RadioButton.icon.hoverBackground", alpha(theme.accent, 38),
            "RadioButton.icon.pressedBackground", alpha(theme.accent, 62),
            "Label.foreground", theme.text,
            "Label.disabledForeground", theme.muted,
            "TitledBorder.titleColor", theme.text,
            "TitledBorder.border", BorderFactory.createLineBorder(border),
            "Link.foreground", theme.gold,
            "Link.visitedForeground", theme.muted,
            "Component.linkColor", theme.gold,
            "Component.visitedLinkColor", theme.muted,
            "ToolTip.background", panelAlt,
            "ToolTip.foreground", theme.text,
            "ToolTip.border", BorderFactory.createLineBorder(border),
            "OptionPane.background", alpha(theme.base.darker(), 40),
            "OptionPane.foreground", theme.text,
            "OptionPane.messageForeground", theme.text,
            "OptionPane.buttonAreaBackground", alpha(theme.base.darker(), 28),
            "OptionPane.warningDialog.titlePane.background", alpha(theme.base.darker(), 92),
            "OptionPane.warningDialog.titlePane.foreground", theme.text,
            "OptionPane.errorDialog.titlePane.background", alpha(theme.base.darker(), 92),
            "OptionPane.errorDialog.titlePane.foreground", theme.text,
            "PopupMenu.background", alpha(theme.base.darker(), 36),
            "PopupMenu.foreground", theme.text,
            "PopupMenu.borderColor", border,
            "PopupMenu.separatorColor", alpha(theme.accent, 120),
            "ProgressBar.background", surface,
            "ProgressBar.foreground", theme.accent,
            "ProgressBar.selectionBackground", theme.text,
            "ProgressBar.selectionForeground", theme.base.darker(),
            "Slider.background", shell,
            "Slider.foreground", theme.accent,
            "Slider.trackColor", alpha(theme.accent, 70),
            "Slider.thumbColor", theme.accent,
            "Slider.hoverThumbColor", theme.gold,
            "Slider.pressedThumbColor", theme.button,
            "Slider.focusedColor", alpha(theme.accent, 110),
            "Slider.disabledTrackColor", alpha(theme.muted, 55),
            "Slider.disabledThumbColor", alpha(theme.muted, 120),
            "Spinner.background", surface,
            "Spinner.foreground", theme.text,
            "Spinner.disabledBackground", surface,
            "Spinner.buttonBackground", control,
            "Spinner.buttonArrowColor", theme.text,
            "Spinner.buttonDisabledArrowColor", theme.muted,
            "Spinner.buttonHoverBackground", alpha(theme.accent, 54),
            "Spinner.buttonPressedBackground", alpha(theme.accent, 88),
            "Spinner.buttonSeparatorColor", border,
            "ScrollBar.background", surface,
            "ScrollBar.foreground", alpha(theme.accent, 165),
            "ScrollBar.thumb", alpha(theme.accent, 165),
            "ScrollBar.thumbDarkShadow", theme.base.darker(),
            "ScrollBar.thumbHighlight", alpha(theme.gold, 80),
            "ScrollBar.thumbShadow", theme.base,
            "ScrollBar.track", surface,
            "ScrollBar.trackHighlight", alpha(theme.accent, 55),
            "ScrollBar.thumbArc", Integer.valueOf(999),
            "ScrollBar.trackArc", Integer.valueOf(999),
            "ScrollBar.hoverThumbColor", alpha(theme.accent, 170),
            "ScrollBar.pressedThumbColor", alpha(theme.gold, 185),
            "MenuBar.background", shell,
            "MenuBar.foreground", theme.text,
            "MenuBar.border", BorderFactory.createMatteBorder(0, 0, 1, 0, border),
            "MenuBar.hoverBackground", alpha(theme.accent, 48),
            "MenuBar.selectionBackground", alpha(theme.accent, 76),
            "MenuBar.selectionForeground", theme.buttonText,
            "Menu.background", alpha(theme.base.darker(), 24),
            "Menu.foreground", theme.text,
            "Menu.selectionBackground", selection,
            "Menu.selectionForeground", theme.buttonText,
            "Menu.acceleratorForeground", theme.muted,
            "Menu.acceleratorSelectionForeground", theme.buttonText,
            "MenuItem.background", alpha(theme.base.darker(), 20),
            "MenuItem.foreground", theme.text,
            "MenuItem.disabledForeground", theme.muted,
            "MenuItem.acceleratorForeground", theme.muted,
            "MenuItem.acceleratorSelectionForeground", theme.buttonText,
            "MenuItem.selectionBackground", selection,
            "MenuItem.selectionForeground", theme.buttonText,
            "MenuItem.arrowColor", theme.text,
            "MenuItem.disabledArrowColor", theme.muted,
            "MenuItem.checkBackground", alpha(theme.button, 210),
            "MenuItem.checkForeground", theme.buttonText,
            "CheckBoxMenuItem.background", alpha(theme.base.darker(), 20),
            "CheckBoxMenuItem.foreground", theme.text,
            "CheckBoxMenuItem.disabledForeground", theme.muted,
            "CheckBoxMenuItem.selectionBackground", selection,
            "CheckBoxMenuItem.selectionForeground", theme.buttonText,
            "CheckBoxMenuItem.checkmarkColor", theme.buttonText,
            "CheckBoxMenuItem.disabledCheckmarkColor", theme.muted,
            "RadioButtonMenuItem.background", alpha(theme.base.darker(), 20),
            "RadioButtonMenuItem.foreground", theme.text,
            "RadioButtonMenuItem.disabledForeground", theme.muted,
            "RadioButtonMenuItem.selectionBackground", selection,
            "RadioButtonMenuItem.selectionForeground", theme.buttonText,
            "RadioButtonMenuItem.checkmarkColor", theme.buttonText,
            "RadioButtonMenuItem.disabledCheckmarkColor", theme.muted,
            "Separator.foreground", alpha(theme.accent, 150),
            "Separator.background", shell,
            "Component.borderColor", border,
            "Component.disabledBorderColor", alpha(theme.muted, 70),
            "Component.focusedBorderColor", alpha(theme.accent, 145),
            "Component.hoverBorderColor", alpha(theme.gold, 90),
            "Component.focusColor", alpha(theme.accent, 75),
            "FileChooser.background", panel,
            "FileChooser.foreground", theme.text,
            "FileChooser.listViewBackground", surface,
            "FileChooser.listViewForeground", theme.text,
            "FileChooser.detailsViewBackground", surface,
            "FileChooser.detailsViewForeground", theme.text,
            "Desktop.background", theme.base,
            "DesktopPane.background", shell,
            "InternalFrame.background", panel,
            "InternalFrame.foreground", theme.text,
            "InternalFrame.activeTitleBackground", panelAlt,
            "InternalFrame.activeTitleForeground", theme.text,
            "InternalFrame.inactiveTitleBackground", surface,
            "InternalFrame.inactiveTitleForeground", theme.muted
        };
        defaults = uiResourceDefaults(frameScopedDefaults(defaults));
        rememberOriginalUiDefaults(defaults);
        rememberInstalledUiDefaults(defaults);
        UIManager.getDefaults().putDefaults(defaults);
    }

    private Object[] uiResourceDefaults(Object[] defaults)
    {
        Object[] converted = defaults.clone();
        for (int index = 1; index < converted.length; index += 2)
        {
            converted[index] = uiResourceValue(converted[index]);
        }
        return converted;
    }

    private Object uiResourceValue(Object value)
    {
        if (value instanceof Color && !(value instanceof ColorUIResource))
        {
            return new ColorUIResource((Color) value);
        }
        if (value instanceof Border && !(value instanceof javax.swing.plaf.UIResource))
        {
            return new BorderUIResource((Border) value);
        }
        return value;
    }

    private Object[] frameScopedDefaults(Object[] defaults)
    {
        List<Object> filtered = new ArrayList<>();
        for (int index = 0; index < defaults.length; index += 2)
        {
            Object key = defaults[index];
            if (!isDialogSensitiveUiKey(key == null ? "" : key.toString()))
            {
                filtered.add(key);
                filtered.add(defaults[index + 1]);
            }
        }
        return filtered.toArray(new Object[0]);
    }

    private boolean isDialogSensitiveUiKey(String key)
    {
        return key.startsWith("RootPane.")
        || key.startsWith("TitlePane.")
        || key.startsWith("FileChooser.")
        || "Panel.background".equals(key)
        || "Viewport.background".equals(key);
    }

    private void rememberOriginalUiDefaults(Object[] defaults)
    {
        for (int index = 0; index < defaults.length; index += 2)
        {
            Object key = defaults[index];
            if (!originalUiDefaults.containsKey(key))
            {
                originalUiDefaults.put(key, UIManager.getDefaults().get(key));
            }
        }
    }

    private void rememberInstalledUiDefaults(Object[] defaults)
    {
        installedUiDefaults.clear();
        for (int index = 0; index < defaults.length; index += 2)
        {
            installedUiDefaults.put(defaults[index], defaults[index + 1]);
        }
    }

    private void restoreUiDefaults()
    {
        for (Map.Entry<Object, Object> entry : originalUiDefaults.entrySet())
        {
            Object key = entry.getKey();
            Object installedValue = installedUiDefaults.get(key);
            Object currentValue = UIManager.getDefaults().get(key);
            if (installedValue != null && !Objects.equals(currentValue, installedValue))
            {
                continue;
            }
            if (entry.getValue() == null)
            {
                UIManager.getDefaults().remove(key);
            }
            else
            {
                UIManager.getDefaults().put(key, entry.getValue());
            }
        }
        originalUiDefaults.clear();
        installedUiDefaults.clear();
    }

    private void installWindowListener()
    {
        if (windowListenerInstalled)
        {
            return;
        }
        windowListenerInstalled = true;
        windowThemeListener = event -> {
            if (!isSuiteTintEnabled() || applyingGlobalTheme)
            {
                return;
            }
            if (event instanceof WindowEvent)
            {
                WindowEvent windowEvent = (WindowEvent) event;
                int eventId = windowEvent.getID();
                Window opened = windowEvent.getWindow();
                if (eventId == WindowEvent.WINDOW_CLOSED)
                {
                    releaseWindowState(opened);
                    return;
                }
                if (eventId != WindowEvent.WINDOW_OPENED)
                {
                    // Focus/activation no longer triggers a full-frame re-theme: that was a
                    // periodic-blink source. Newly shown windows fire WINDOW_OPENED and new
                    // content is caught by the COMPONENT_ADDED branch below.
                    return;
                }
                if (isThemeableWindow(opened))
                {
                    if (eventId == WindowEvent.WINDOW_OPENED)
                    {
                        scheduleOpenedWindowThemeRefreshes(opened);
                    }
                    else
                    {
                        scheduleFocusedWindowThemeRefresh(opened);
                    }
                }
                else if (isThemeablePopupWindow(opened))
                {
                    if (eventId == WindowEvent.WINDOW_OPENED)
                    {
                        scheduleOpenedPopupThemeRefreshes(opened);
                    }
                    else
                    {
                        scheduleCoalescedPopupThemeRefresh(opened, 80);
                    }
                }
                return;
            }
            if (event instanceof ContainerEvent)
            {
                ContainerEvent containerEvent = (ContainerEvent) event;
                if (containerEvent.getID() == ContainerEvent.COMPONENT_ADDED && isLazyFrameThemeCandidate(containerEvent.getChild()))
                {
                    Window window = SwingUtilities.getWindowAncestor(containerEvent.getChild());
                    if (isThemeableWindow(window))
                    {
                        scheduleCoalescedFrameThemeRefresh(window, 90);
                    }
                    else if (isThemeablePopupWindow(window))
                    {
                        scheduleCoalescedPopupThemeRefresh(window, 120);
                    }
                }
            }
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(windowThemeListener, AWTEvent.WINDOW_EVENT_MASK | AWTEvent.WINDOW_FOCUS_EVENT_MASK | AWTEvent.CONTAINER_EVENT_MASK);
    }

    private void releaseWindowState(Window window)
    {
        if (window == null)
        {
            return;
        }
        synchronized (themeTimers)
        {
            Timer timer = pendingFrameRefreshes.remove(window);
            Timer popupTimer = pendingPopupRefreshes.remove(window);
            recentWindowThemeRefreshes.remove(window);
            if (timer != null)
            {
                timer.stop();
                themeTimers.remove(timer);
            }
            if (popupTimer != null)
            {
                popupTimer.stop();
                themeTimers.remove(popupTimer);
            }
        }
        if (window instanceof RootPaneContainer)
        {
            RootPaneContainer rootPaneContainer = (RootPaneContainer) window;
            installedBackgrounds.remove(rootPaneContainer);
            originalRootPaneProperties.remove(rootPaneContainer.getRootPane());
        }
        for (Map.Entry<Component, ComponentState> entry : new ArrayList<>(componentStates.entrySet()))
        {
            if (isComponentOwnedByWindow(entry.getKey(), window))
            {
                componentStates.remove(entry.getKey());
            }
        }
        for (Map.Entry<JLayeredPane, ComponentListener> entry : new ArrayList<>(backgroundResizeListeners.entrySet()))
        {
            if (isComponentOwnedByWindow(entry.getKey(), window))
            {
                entry.getKey().removeComponentListener(entry.getValue());
                entry.getKey().putClientProperty("burptheme.backgroundPanel", null);
                backgroundResizeListeners.remove(entry.getKey());
            }
        }
        for (Map.Entry<JTabbedPane, ChangeListener> entry : new ArrayList<>(tabThemeListeners.entrySet()))
        {
            if (isComponentOwnedByWindow(entry.getKey(), window))
            {
                entry.getKey().removeChangeListener(entry.getValue());
                entry.getKey().putClientProperty("burptheme.tabListener", null);
                tabThemeListeners.remove(entry.getKey());
            }
        }
        for (Map.Entry<AbstractButton, PropertyChangeListener> entry : new ArrayList<>(buttonThemeListeners.entrySet()))
        {
            if (isComponentOwnedByWindow(entry.getKey(), window))
            {
                entry.getKey().removePropertyChangeListener("enabled", entry.getValue());
                entry.getKey().removePropertyChangeListener("selected", entry.getValue());
                entry.getKey().putClientProperty("burptheme.buttonListener", null);
                buttonThemeListeners.remove(entry.getKey());
            }
        }
        proxyActionButtons.removeIf(button -> isComponentOwnedByWindow(button, window));
        for (JComponent wrapper : new ArrayList<>(proxySplitWrappers))
        {
            if (isComponentOwnedByWindow(wrapper, window))
            {
                wrapper.putClientProperty("burptheme.proxySplitGeneration", null);
                proxySplitWrappers.remove(wrapper);
            }
        }
    }

    private void pruneDetachedThemeState()
    {
        for (Component component : new ArrayList<>(componentStates.keySet()))
        {
            if (isDetachedThemeComponent(component))
            {
                componentStates.remove(component);
            }
        }
        for (Map.Entry<JTabbedPane, ChangeListener> entry : new ArrayList<>(tabThemeListeners.entrySet()))
        {
            JTabbedPane tabbedPane = entry.getKey();
            if (isDetachedThemeComponent(tabbedPane))
            {
                tabbedPane.removeChangeListener(entry.getValue());
                tabbedPane.putClientProperty("burptheme.tabListener", null);
                tabThemeListeners.remove(tabbedPane);
            }
        }
        for (Map.Entry<AbstractButton, PropertyChangeListener> entry : new ArrayList<>(buttonThemeListeners.entrySet()))
        {
            AbstractButton button = entry.getKey();
            if (isDetachedThemeComponent(button))
            {
                button.removePropertyChangeListener("enabled", entry.getValue());
                button.removePropertyChangeListener("selected", entry.getValue());
                button.putClientProperty("burptheme.buttonListener", null);
                buttonThemeListeners.remove(button);
            }
        }
        proxyActionButtons.removeIf(this::isDetachedThemeComponent);
        for (JComponent wrapper : new ArrayList<>(proxySplitWrappers))
        {
            if (isDetachedThemeComponent(wrapper))
            {
                wrapper.putClientProperty("burptheme.proxySplitGeneration", null);
                proxySplitWrappers.remove(wrapper);
            }
        }
    }

    private boolean isDetachedThemeComponent(Component component)
    {
        return component != null
        && component != localRoot
        && component.getParent() == null
        && !component.isDisplayable();
    }

    private boolean isComponentOwnedByWindow(Component component, Window window)
    {
        return component == window || (component != null && window instanceof Container && SwingUtilities.isDescendingFrom(component, (Container) window));
    }

    private void removeWindowListener()
    {
        if (windowThemeListener != null)
        {
            Toolkit.getDefaultToolkit().removeAWTEventListener(windowThemeListener);
            windowThemeListener = null;
        }
        windowListenerInstalled = false;
    }

    void scheduleSuiteThemeRefresh(int delayMillis)
    {
        int generation = themeGeneration.get();
        scheduleThemeTimer(delayMillis, () -> {
            if (generation != themeGeneration.get() || !isSuiteTintEnabled())
            {
                return;
            }
            applyGlobalBurpTheme(currentTheme);
        });
    }

    private void scheduleOpenedWindowThemeRefreshes(Window window)
    {
        recentWindowThemeRefreshes.put(window, Long.valueOf(System.nanoTime()));
        scheduleWindowThemeRefresh(window, 0);
        scheduleCoalescedFrameThemeRefresh(window, 650);
    }

    private void scheduleFocusedWindowThemeRefresh(Window window)
    {
        if (window == null)
        {
            return;
        }
        long now = System.nanoTime();
        Long lastRefresh = recentWindowThemeRefreshes.get(window);
        if (lastRefresh != null && now - lastRefresh.longValue() < FOCUS_REFRESH_THROTTLE_NANOS)
        {
            return;
        }
        recentWindowThemeRefreshes.put(window, Long.valueOf(now));
        scheduleCoalescedFrameThemeRefresh(window, 140);
    }

    private void scheduleOpenedPopupThemeRefreshes(Window window)
    {
        schedulePopupThemeRefresh(window, 0);
        scheduleCoalescedPopupThemeRefresh(window, 520);
    }

    private void scheduleWindowThemeRefresh(Window window, int delayMillis)
    {
        int generation = themeGeneration.get();
        scheduleThemeTimer(delayMillis, () -> {
            if (generation == themeGeneration.get() && window != null && window.isDisplayable() && isSuiteTintEnabled() && canThemeFrameNow(window))
            {
                boolean wasApplying = applyingGlobalTheme;
                applyingGlobalTheme = true;
                try
                {
                    themeWindow(window, currentTheme, themeWindowBudget(window));
                }
                finally
                {
                    applyingGlobalTheme = wasApplying;
                }
            }
        });
    }

    private void schedulePopupThemeRefresh(Window window, int delayMillis)
    {
        int generation = themeGeneration.get();
        scheduleThemeTimer(delayMillis, () -> {
            if (generation == themeGeneration.get() && window != null && window.isShowing() && isSuiteTintEnabled() && isThemeablePopupWindow(window))
            {
                boolean wasApplying = applyingGlobalTheme;
                applyingGlobalTheme = true;
                try
                {
                    themePopupWindow(window, currentTheme);
                }
                finally
                {
                    applyingGlobalTheme = wasApplying;
                }
            }
        });
    }

    private void scheduleCoalescedFrameThemeRefresh(Window window, int delayMillis)
    {
        if (!(window instanceof JFrame))
        {
            return;
        }
        synchronized (themeTimers)
        {
            // Coalesce add-storms into the already-pending pass instead of resetting the
            // timer on every COMPONENT_ADDED (which would keep blinking deferred forever).
            if (pendingFrameRefreshes.containsKey(window))
            {
                return;
            }
        }
        int generation = themeGeneration.get();
        Timer timer = new Timer(delayMillis, null);
        timer.addActionListener(event -> {
            synchronized (themeTimers)
            {
                pendingFrameRefreshes.remove(window);
                themeTimers.remove(timer);
            }
            if (generation == themeGeneration.get() && window.isDisplayable() && isSuiteTintEnabled() && canThemeFrameNow(window))
            {
                boolean wasApplying = applyingGlobalTheme;
                applyingGlobalTheme = true;
                try
                {
                    themeWindow(window, currentTheme, themeWindowBudget(window));
                }
                catch (RuntimeException exception)
                {
                    logThemeRefreshFailure(exception);
                }
                finally
                {
                    applyingGlobalTheme = wasApplying;
                }
            }
        });
        timer.setRepeats(false);
        synchronized (themeTimers)
        {
            themeTimers.add(timer);
            pendingFrameRefreshes.put(window, timer);
        }
        timer.start();
    }

    private void scheduleCoalescedPopupThemeRefresh(Window window, int delayMillis)
    {
        if (!isThemeablePopupWindow(window))
        {
            return;
        }
        synchronized (themeTimers)
        {
            if (pendingPopupRefreshes.containsKey(window))
            {
                return;
            }
        }
        int generation = themeGeneration.get();
        Timer timer = new Timer(delayMillis, null);
        timer.addActionListener(event -> {
            synchronized (themeTimers)
            {
                pendingPopupRefreshes.remove(window);
                themeTimers.remove(timer);
            }
            if (generation == themeGeneration.get() && window.isShowing() && isSuiteTintEnabled() && isThemeablePopupWindow(window))
            {
                boolean wasApplying = applyingGlobalTheme;
                applyingGlobalTheme = true;
                try
                {
                    themePopupWindow(window, currentTheme);
                }
                catch (RuntimeException exception)
                {
                    logThemeRefreshFailure(exception);
                }
                finally
                {
                    applyingGlobalTheme = wasApplying;
                }
            }
        });
        timer.setRepeats(false);
        synchronized (themeTimers)
        {
            themeTimers.add(timer);
            pendingPopupRefreshes.put(window, timer);
        }
        timer.start();
    }

    private void scheduleThemeTimer(int delayMillis, Runnable runnable)
    {
        Timer timer = new Timer(delayMillis, null);
        timer.addActionListener(event -> {
            synchronized (themeTimers)
            {
                themeTimers.remove(timer);
            }
            try
            {
                runnable.run();
            }
            catch (RuntimeException exception)
            {
                logThemeRefreshFailure(exception);
            }
        });
        timer.setRepeats(false);
        synchronized (themeTimers)
        {
            themeTimers.add(timer);
        }
        timer.start();
    }

    private void logThemeRefreshFailure(RuntimeException exception)
    {
        if (api != null)
        {
            api.logging().logToError("BurpTheme theme refresh failed: " + exception);
        }
    }

    private void stopThemeTimers()
    {
        synchronized (themeTimers)
        {
            for (Timer timer : new ArrayList<>(themeTimers))
            {
                timer.stop();
            }
            themeTimers.clear();
            pendingFrameRefreshes.clear();
            pendingPopupRefreshes.clear();
            recentWindowThemeRefreshes.clear();
        }
    }

    private boolean isLazyFrameThemeCandidate(Component component)
    {
        if (component == null || component == localRoot || isLocalThemeComponent(component))
        {
            return false;
        }
        if (component instanceof JTabbedPane
        || component instanceof JScrollPane
        || component instanceof JViewport
        || component instanceof JSplitPane
        || component instanceof JTextComponent
        || component instanceof JTable
        || component instanceof JTableHeader
        || component instanceof JTree
        || component instanceof JList
        || component instanceof JComboBox
        || component instanceof AbstractButton
        || component instanceof JToolBar
        || component instanceof JPopupMenu
        || component instanceof JMenuBar
        || component instanceof JMenu
        || component instanceof JMenuItem
        || component instanceof JOptionPane
        || component instanceof JFileChooser
        || component instanceof JRootPane
        || component instanceof JLayeredPane
        || component instanceof JToolTip
        || component instanceof JProgressBar
        || component instanceof JSlider
        || component instanceof JSpinner
        || component instanceof JSeparator)
        {
            return true;
        }
        String className = component.getClass().getName().toLowerCase(Locale.ROOT);
        return className.contains("burp")
        || className.contains("proxy")
        || className.contains("repeater")
        || className.contains("message")
        || className.contains("editor")
        || className.contains("syntax");
    }

    private int themeWindowBudget(Window window)
    {
        return window instanceof JFrame ? MAX_THEME_COMPONENTS_PER_PASS : 0;
    }

    private boolean installBurpBackground(Window window, BurpTheme theme)
    {
        if (!(window instanceof RootPaneContainer))
        {
            return false;
        }
        RootPaneContainer rootPaneContainer = (RootPaneContainer) window;
        JLayeredPane layeredPane = rootPaneContainer.getLayeredPane();
        if (layeredPane == null)
        {
            return false;
        }
        BurpBackgroundPanel backgroundPanel = installedBackgrounds.get(rootPaneContainer);
        if (backgroundPanel == null || backgroundPanel.getParent() != layeredPane)
        {
            backgroundPanel = reusableBurpBackgroundPanel(layeredPane);
        }
        removeDuplicateBurpBackgroundPanels(layeredPane, backgroundPanel);
        boolean themeChanged = backgroundPanel != null && backgroundPanel.theme != theme;
        if (backgroundPanel == null)
        {
            backgroundPanel = new BurpBackgroundPanel(theme);
            backgroundPanel.putClientProperty("burptheme.background", Boolean.TRUE);
            layeredPane.add(backgroundPanel, Integer.valueOf(Integer.MIN_VALUE));
            installBackgroundResizeListener(layeredPane, backgroundPanel);
        }
        else
        {
            backgroundPanel.theme = theme;
            if (backgroundPanel.getParent() != layeredPane)
            {
                layeredPane.add(backgroundPanel, Integer.valueOf(Integer.MIN_VALUE));
                installBackgroundResizeListener(layeredPane, backgroundPanel);
            }
        }
        installedBackgrounds.put(rootPaneContainer, backgroundPanel);
        boolean resized = resizeBackgroundPanel(layeredPane, backgroundPanel);
        if (JLayeredPane.getLayer(backgroundPanel) != Integer.MIN_VALUE)
        {
            layeredPane.setLayer(backgroundPanel, Integer.MIN_VALUE);
        }
        if (themeChanged || resized)
        {
            backgroundPanel.revalidate();
            backgroundPanel.repaint();
        }
        return true;
    }

    private BurpBackgroundPanel reusableBurpBackgroundPanel(JLayeredPane layeredPane)
    {
        for (Component component : layeredPane.getComponents())
        {
            if (component instanceof BurpBackgroundPanel)
            {
                return (BurpBackgroundPanel) component;
            }
        }
        return null;
    }

    private void removeDuplicateBurpBackgroundPanels(JLayeredPane layeredPane, Component keep)
    {
        for (Component component : layeredPane.getComponents())
        {
            if (component != keep && isBurpThemeBackgroundPanel(component))
            {
                layeredPane.remove(component);
            }
        }
    }

    private boolean isBurpThemeBackgroundPanel(Component component)
    {
        if (component instanceof BurpBackgroundPanel)
        {
            return true;
        }
        if (component instanceof JComponent && Boolean.TRUE.equals(((JComponent) component).getClientProperty("burptheme.background")))
        {
            return true;
        }
        String className = component == null ? "" : component.getClass().getName();
        return "burp.arcade.BurpThemeEngine$BurpBackgroundPanel".equals(className)
        || "burp.arcade.ArcadeBurpExtension$BurpBackgroundPanel".equals(className);
    }

    private void installBackgroundResizeListener(JLayeredPane layeredPane, BurpBackgroundPanel backgroundPanel)
    {
        ComponentListener existing = backgroundResizeListeners.get(layeredPane);
        if (existing != null && layeredPane.getClientProperty("burptheme.backgroundPanel") == backgroundPanel)
        {
            return;
        }
        if (existing != null)
        {
            layeredPane.removeComponentListener(existing);
            backgroundResizeListeners.remove(layeredPane);
        }
        ComponentListener listener = new ComponentAdapter()
        {
            @Override
            public void componentResized(ComponentEvent event)
            {
                resizeBackgroundPanel(layeredPane, backgroundPanel);
            }

            @Override
            public void componentMoved(ComponentEvent event)
            {
                resizeBackgroundPanel(layeredPane, backgroundPanel);
            }
        };
        backgroundResizeListeners.put(layeredPane, listener);
        layeredPane.putClientProperty("burptheme.backgroundPanel", backgroundPanel);
        layeredPane.addComponentListener(listener);
    }

    private boolean resizeBackgroundPanel(JLayeredPane layeredPane, BurpBackgroundPanel backgroundPanel)
    {
        Rectangle bounds = new Rectangle(0, 0, Math.max(1, layeredPane.getWidth()), Math.max(1, layeredPane.getHeight()));
        if (bounds.equals(backgroundPanel.getBounds()))
        {
            return false;
        }
        backgroundPanel.setBounds(bounds);
        backgroundPanel.revalidate();
        backgroundPanel.repaint();
        return true;
    }

    private void cleanupLegacyBackgroundWrappers()
    {
        for (Window window : Window.getWindows())
        {
            if (!(window instanceof RootPaneContainer))
            {
                continue;
            }
            RootPaneContainer rootPaneContainer = (RootPaneContainer) window;
            Container contentPane = rootPaneContainer.getContentPane();
            JLayeredPane layeredPane = rootPaneContainer.getLayeredPane();
            if (layeredPane != null)
            {
                removeDuplicateBurpBackgroundPanels(layeredPane, installedBackgrounds.get(rootPaneContainer));
            }
            if (isLegacyBurpThemeBackground(contentPane) && contentPane.getComponentCount() == 1)
            {
                Component original = contentPane.getComponent(0);
                if (original instanceof Container)
                {
                    rootPaneContainer.setContentPane((Container) original);
                    window.revalidate();
                    window.repaint();
                }
            }
        }
    }

    private boolean isLegacyBurpThemeBackground(Component component)
    {
        return component != null
        && component instanceof JComponent
        && "burp.arcade.ArcadeBurpExtension$BurpBackgroundPanel".equals(component.getClass().getName())
        && !Boolean.TRUE.equals(((JComponent) component).getClientProperty("burptheme.background"));
    }

    private void themeRootPaneContainer(RootPaneContainer rootPaneContainer, BurpTheme theme)
    {
        JRootPane rootPane = rootPaneContainer.getRootPane();
        if (rootPane != null)
        {
            rememberComponentState(rootPane);
            makeTransparent(rootPane, transparent(theme.base), theme.text);
            themeRootPaneTitleBar(rootPane, theme);
        }
        JLayeredPane layeredPane = rootPaneContainer.getLayeredPane();
        if (layeredPane != null)
        {
            rememberComponentState(layeredPane);
            makeTransparent(layeredPane, transparent(theme.base), theme.text);
        }
        Container contentPane = rootPaneContainer.getContentPane();
        if (contentPane instanceof JComponent)
        {
            rememberComponentState(contentPane);
            makeTransparent(contentPane, transparent(theme.base), theme.text);
        }
    }

    private void themeRootPaneTitleBar(JRootPane rootPane, BurpTheme theme)
    {
        Color titleBackground = alpha(theme.base.darker(), 116);
        Color inactiveBackground = alpha(theme.base.darker(), 86);
        putRootPaneThemeProperty(rootPane, "JRootPane.titleBarBackground", titleBackground);
        putRootPaneThemeProperty(rootPane, "JRootPane.titleBarForeground", theme.text);
        putRootPaneThemeProperty(rootPane, "JRootPane.titleBarInactiveBackground", inactiveBackground);
        putRootPaneThemeProperty(rootPane, "JRootPane.titleBarInactiveForeground", theme.muted);
        putRootPaneThemeProperty(rootPane, "JRootPane.menuBarEmbedded", Boolean.FALSE);
        putRootPaneThemeProperty(rootPane, "FlatLaf.fullWindowContent", Boolean.FALSE);
    }

    private void putRootPaneThemeProperty(JRootPane rootPane, String key, Object value)
    {
        Map<String, Object> properties = originalRootPaneProperties.computeIfAbsent(rootPane, ignored -> new LinkedHashMap<>());
        if (!properties.containsKey(key))
        {
            properties.put(key, rootPane.getClientProperty(key));
        }
        rootPane.putClientProperty(key, value);
    }

    void restore(boolean waitForEdt)
    {
        restoringTheme = true;
        themeGeneration.incrementAndGet();
        stopThemeTimers();
        removeWindowListener();
        Runnable restore = () -> {
            try
            {
                restoreUiDefaults();
                cleanupLegacyBackgroundWrappers();
                for (Window window : Window.getWindows())
                {
                    if (window instanceof RootPaneContainer)
                    {
                        JLayeredPane layeredPane = ((RootPaneContainer) window).getLayeredPane();
                        if (layeredPane != null)
                        {
                            removeDuplicateBurpBackgroundPanels(layeredPane, null);
                            layeredPane.putClientProperty("burptheme.backgroundPanel", null);
                            layeredPane.revalidate();
                            layeredPane.repaint();
                        }
                    }
                }
                installedBackgrounds.clear();
                restoreRootPaneProperties();
                detachThemeListeners();
                for (Map.Entry<Component, ComponentState> entry : new ArrayList<>(componentStates.entrySet()))
                {
                    try
                    {
                        entry.getValue().restore(entry.getKey());
                    }
                    catch (RuntimeException exception)
                    {
                        // Keep unload deterministic even if a Burp-owned custom
                        // component rejects one restored paint property.
                    }
                }
                componentStates.clear();
                refreshRestoredWindowUis();
                proxyActionButtons.clear();
                for (JComponent wrapper : new ArrayList<>(proxySplitWrappers))
                {
                    wrapper.putClientProperty("burptheme.proxySplitGeneration", null);
                }
                proxySplitWrappers.clear();
                for (Window window : Window.getWindows())
                {
                    if (isThemeableWindow(window) || isThemeablePopupWindow(window))
                    {
                        window.revalidate();
                        window.repaint();
                    }
                }
            }
            finally
            {
                restoringTheme = false;
            }
        };
        if (SwingUtilities.isEventDispatchThread())
        {
            restore.run();
        }
        else if (waitForEdt)
        {
            try
            {
                SwingUtilities.invokeAndWait(restore);
            }
            catch (Exception exception)
            {
                if (api != null)
                {
                    api.logging().logToError("BurpTheme synchronous restore failed: " + exception);
                }
                restoringTheme = false;
            }
        }
        else
        {
            SwingUtilities.invokeLater(restore);
        }
    }

    private void detachThemeListeners()
    {
        for (Map.Entry<JLayeredPane, ComponentListener> entry : new ArrayList<>(backgroundResizeListeners.entrySet()))
        {
            entry.getKey().removeComponentListener(entry.getValue());
        }
        backgroundResizeListeners.clear();
        for (Map.Entry<JTabbedPane, ChangeListener> entry : new ArrayList<>(tabThemeListeners.entrySet()))
        {
            entry.getKey().removeChangeListener(entry.getValue());
            entry.getKey().putClientProperty("burptheme.tabListener", null);
        }
        tabThemeListeners.clear();
        for (Map.Entry<AbstractButton, PropertyChangeListener> entry : new ArrayList<>(buttonThemeListeners.entrySet()))
        {
            entry.getKey().removePropertyChangeListener("enabled", entry.getValue());
            entry.getKey().removePropertyChangeListener("selected", entry.getValue());
            entry.getKey().putClientProperty("burptheme.buttonListener", null);
        }
        buttonThemeListeners.clear();
    }

    private void restoreRootPaneProperties()
    {
        for (Map.Entry<JRootPane, Map<String, Object>> entry : new ArrayList<>(originalRootPaneProperties.entrySet()))
        {
            JRootPane rootPane = entry.getKey();
            for (Map.Entry<String, Object> property : entry.getValue().entrySet())
            {
                rootPane.putClientProperty(property.getKey(), property.getValue());
            }
        }
        originalRootPaneProperties.clear();
    }

    private void refreshRestoredWindowUis()
    {
        for (Window window : Window.getWindows())
        {
            if (isThemeableWindow(window) || isThemeablePopupWindow(window))
            {
                SwingUtilities.updateComponentTreeUI(window);
                window.invalidate();
                window.validate();
                window.repaint();
            }
        }
    }

    private int themeBurpComponent(Component component, BurpTheme theme, int depth, int budget)
    {
        Set<Component> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        return themeBurpComponent(component, theme, depth, budget, visited, true);
    }

    private int themeBurpComponent(Component component, BurpTheme theme, int depth, int budget, Set<Component> visited, boolean wallpaperBacked)
    {
        if (component == null || component == localRoot || budget <= 0 || depth > MAX_THEME_DEPTH || visited.contains(component))
        {
            return 0;
        }
        visited.add(component);
        rememberComponentState(component);
        int themed = 1;
        boolean proxyContext = wallpaperBacked && hasLikelyProxyActionContext(component);
        Color surface = alpha(theme.base.darker(), wallpaperBacked ? (proxyContext ? 24 : 42) : 244);
        Color shell = transparent(theme.base);
        Color panelAlt = alpha(theme.horizon.darker(), wallpaperBacked ? (proxyContext ? 28 : 46) : 244);
        Color control = alpha(theme.horizon.darker(), proxyContext ? 58 : 82);
        Color selection = alpha(theme.button, wallpaperBacked ? 150 : 255);

        if (component instanceof BurpBackgroundPanel)
        {
            themed += themeContainerChildren((Container) component, theme, depth + 1, budget - themed, visited, wallpaperBacked);
            component.repaint();
            return themed;
        }
        if (component instanceof JTabbedPane)
        {
            themeTabbedPane((JTabbedPane) component, theme);
        }
        else if (component instanceof JScrollPane)
        {
            themeScrollPane((JScrollPane) component, theme, surface, alpha(theme.accent, proxyContext ? 30 : 38), wallpaperBacked);
        }
        else if (component instanceof Container && isProxySplitWrapperComponent((Container) component))
        {
            themeProxySplitWrapperDirect((Container) component, theme);
        }
        else if (isStructuralShell(component))
        {
            themeStructuralShell(component, theme, wallpaperBacked);
        }
        else if (component instanceof JTextComponent)
        {
            themeTextComponent((JTextComponent) component, theme, surface, wallpaperBacked);
        }
        else if (component instanceof JTable)
        {
            themeTable((JTable) component, theme, surface, panelAlt, selection, wallpaperBacked);
        }
        else if (component instanceof JTableHeader)
        {
            component.setBackground(wallpaperBacked ? panelAlt : opaque(panelAlt));
            component.setForeground(theme.text);
        }
        else if (component instanceof JTree)
        {
            themeTree((JTree) component, theme, surface, selection, wallpaperBacked);
        }
        else if (component instanceof JList)
        {
            themeList((JList<?>) component, theme, surface, selection, wallpaperBacked);
        }
        else if (component instanceof JComboBox)
        {
            themeComboBox((JComboBox<?>) component, theme, control, selection, wallpaperBacked);
        }
        else if (component instanceof AbstractButton)
        {
            themeButton((AbstractButton) component, theme, shell, wallpaperBacked);
        }
        else if (component instanceof JProgressBar)
        {
            JProgressBar progressBar = (JProgressBar) component;
            progressBar.setOpaque(false);
            progressBar.setBackground(alpha(theme.base.darker(), 36));
            progressBar.setForeground(theme.accent);
        }
        else if (component instanceof JScrollBar)
        {
            JScrollBar scrollBar = (JScrollBar) component;
            scrollBar.setOpaque(false);
            scrollBar.setBackground(alpha(theme.base.darker(), 0));
            scrollBar.setForeground(alpha(theme.accent, 185));
            scrollBar.repaint();
        }
        else if (component instanceof JSlider)
        {
            component.setBackground(shell);
            component.setForeground(theme.accent);
        }
        else if (component instanceof JSpinner)
        {
            JSpinner spinner = (JSpinner) component;
            spinner.setOpaque(!wallpaperBacked);
            spinner.setBackground(wallpaperBacked ? alpha(theme.base.darker(), 40) : opaque(surface));
            spinner.setForeground(theme.text);
        }
        else if (component instanceof JToolTip)
        {
            if (component instanceof JComponent)
            {
                ((JComponent) component).setOpaque(!wallpaperBacked);
            }
            component.setBackground(wallpaperBacked ? alpha(theme.horizon.darker(), 96) : opaque(theme.horizon.darker()));
            component.setForeground(theme.text);
        }
        else if (component instanceof JPopupMenu)
        {
            if (wallpaperBacked)
            {
                makeWallpaperGlass(component, alpha(theme.base.darker(), 24), theme.text);
            }
            else
            {
                makeSurface(component, alpha(theme.base.darker(), 210), theme.text);
            }
        }
        else if (component instanceof JMenuBar)
        {
            if (wallpaperBacked)
            {
                makeWallpaperGlass(component, alpha(theme.base.darker(), 0), theme.text);
            }
            else
            {
                makeSurface(component, alpha(theme.base.darker(), 255), theme.text);
            }
        }
        else if (component instanceof JMenu || component instanceof JMenuItem)
        {
            if (component instanceof AbstractButton)
            {
                themeMenuItem((AbstractButton) component, theme);
            }
            else
            {
                component.setBackground(alpha(theme.base.darker(), wallpaperBacked ? 20 : 210));
                component.setForeground(theme.text);
            }
        }
        else if (component instanceof JSeparator)
        {
            component.setBackground(shell);
            component.setForeground(theme.accent);
        }
        else if (component instanceof JLabel)
        {
            themeLabel((JLabel) component, theme);
        }
        else if (component instanceof JOptionPane || component instanceof JFileChooser)
        {
            if (wallpaperBacked)
            {
                makeWallpaperGlass(component, alpha(theme.base.darker(), 28), theme.text);
            }
            else
            {
                makeSurface(component, alpha(theme.base.darker(), 218), theme.text);
            }
        }
        else if (component instanceof JRootPane || component instanceof JLayeredPane || component instanceof JDesktopPane)
        {
            makeTransparent(component, shell, theme.text);
        }
        else if (isStatusBar(component))
        {
            makeWallpaperGlass(component, alpha(theme.base.darker(), wallpaperBacked ? 0 : 24), theme.text);
            component.setForeground(theme.text);
        }
        else if (component instanceof JComponent)
        {
            if (wallpaperBacked && component instanceof Container && ((Container) component).getComponentCount() > 0)
            {
                if (containsReadableContent((Container) component, 0))
                {
                    makeWallpaperGlass(component, alpha(theme.base.darker(), proxyContext ? 0 : 16), theme.text);
                }
                else
                {
                    makeTransparent(component, shell, theme.text);
                }
            }
            else
            {
                if (wallpaperBacked)
                {
                    if (proxyContext)
                    {
                        makeTransparent(component, shell, theme.text);
                    }
                    else
                    {
                        makeWallpaperGlass(component, alpha(theme.base.darker(), 0), theme.text);
                    }
                }
                else
                {
                    makeSurface(component, alpha(theme.base.darker(), 236), theme.text);
                }
            }
        }
        else
        {
            component.setBackground(shell);
            component.setForeground(theme.text);
        }

        if (component instanceof Container && themed < budget)
        {
            themed += themeContainerChildren((Container) component, theme, depth + 1, budget - themed, visited, wallpaperBacked);
        }
        if (!(component instanceof Container) || isLocalThemeComponent(component))
        {
            component.repaint();
        }
        return themed;
    }

    private int themeContainerChildren(Container container, BurpTheme theme, int depth, int budget, Set<Component> visited, boolean wallpaperBacked)
    {
        int themed = 0;
        for (Component child : container.getComponents())
        {
            if (themed >= budget)
            {
                break;
            }
            themed += themeBurpComponent(child, theme, depth, budget - themed, visited, wallpaperBacked);
        }
        return themed;
    }

    private void rememberComponentState(Component component)
    {
        if (component != null && !componentStates.containsKey(component))
        {
            componentStates.put(component, new ComponentState(component, isSuiteTintEnabled()));
        }
    }

    private void themeStructuralShell(Component component, BurpTheme theme, boolean wallpaperBacked)
    {
        boolean proxyContext = wallpaperBacked && hasLikelyProxyActionContext(component);
        if ("GlassPanel".equals(component.getClass().getSimpleName()))
        {
            makeTransparent(component, transparent(theme.base), theme.text);
            return;
        }
        if (component instanceof JViewport)
        {
            JViewport viewport = (JViewport) component;
            if (hasReadableViewportView(viewport))
            {
                if (wallpaperBacked)
                {
                    makeWallpaperGlass(component, alpha(theme.base.darker(), proxyContext ? 0 : 18), theme.text);
                }
                else
                {
                    makeSurface(component, alpha(theme.base.darker(), 244), theme.text);
                }
                return;
            }
        }
        if (wallpaperBacked)
        {
            if (proxyContext && component instanceof JPanel && !isProxySplitWrapperComponent((Container) component))
            {
                makeTransparent(component, transparent(theme.base), theme.text);
                return;
            }
            if (isFrameRootShell(component) || component instanceof JSplitPane)
            {
                makeTransparent(component, transparent(theme.base), theme.text);
                return;
            }
            int opacity = component instanceof JToolBar ? 0 : 0;
            if (component instanceof JViewport)
            {
                opacity = 0;
            }
            else if (component instanceof Container && containsReadableContent((Container) component, 0))
            {
                opacity = 18;
            }
            if (proxyContext)
            {
                opacity = 0;
                if (component instanceof Container && containsReadableContent((Container) component, 0))
                {
                    opacity = 0;
                }
            }
            makeWallpaperGlass(component, alpha(theme.base.darker(), opacity), theme.text);
            return;
        }
        int opacity = 232;
        if (component instanceof JToolBar)
        {
            opacity = 220;
        }
        else if (component instanceof JSplitPane)
        {
            opacity = 214;
        }
        makeSurface(component, alpha(theme.base.darker(), opacity), theme.text);
    }

    private boolean containsReadableContent(Container container, int depth)
    {
        if (depth > 2)
        {
            return false;
        }
        if (isReadableContentToken(container))
        {
            return true;
        }
        for (Component child : container.getComponents())
        {
            if (child instanceof JTextComponent
            || child instanceof JTable
            || child instanceof JTree
            || child instanceof JList
            || child instanceof JComboBox)
            {
                return true;
            }
            if (isReadableContentToken(child))
            {
                return true;
            }
            if (child instanceof Container && containsReadableContent((Container) child, depth + 1))
            {
                return true;
            }
        }
        return false;
    }

    private boolean isFrameRootShell(Component component)
    {
        Container parent = component == null ? null : component.getParent();
        return parent instanceof JRootPane || parent instanceof JLayeredPane;
    }

    private boolean hasReadableViewportView(JViewport viewport)
    {
        Component view = viewport.getView();
        return view instanceof JTextComponent
        || view instanceof JTable
        || view instanceof JTree
        || view instanceof JList
        || isReadableContentToken(view)
        || (view instanceof Container && containsReadableContent((Container) view, 0));
    }

    private boolean isReadableContentToken(Component component)
    {
        if (component == null)
        {
            return false;
        }
        String className = component.getClass().getName().toLowerCase(Locale.ROOT);
        String name = component.getName() == null ? "" : component.getName().toLowerCase(Locale.ROOT);
        return containsReadableContentToken(className) || containsReadableContentToken(name);
    }

    private boolean containsReadableContentToken(String value)
    {
        return value.contains("editor")
        || value.contains("message")
        || value.contains("viewer")
        || value.contains("request")
        || value.contains("response")
        || value.contains("inspector")
        || value.contains("logger")
        || value.contains("repeater")
        || value.contains("intruder");
    }

    private void themeScrollPane(JScrollPane scrollPane, BurpTheme theme, Color surface, Color border, boolean wallpaperBacked)
    {
        boolean proxyContext = wallpaperBacked && hasLikelyProxyActionContext(scrollPane);
        scrollPane.setBorder(BorderFactory.createLineBorder(border));
        if (wallpaperBacked)
        {
            makeWallpaperGlass(scrollPane, alpha(theme.base.darker(), proxyContext ? 0 : 16), theme.text);
        }
        else
        {
            makeSurface(scrollPane, alpha(theme.base.darker(), 212), theme.text);
        }
        JViewport viewport = scrollPane.getViewport();
        if (viewport != null)
        {
            rememberComponentState(viewport);
            if (wallpaperBacked && proxyContext)
            {
                makeTransparent(viewport, transparent(theme.base), theme.text);
            }
            else if (wallpaperBacked && hasReadableViewportView(viewport))
            {
                makeWallpaperGlass(viewport, alpha(theme.base.darker(), 0), theme.text);
            }
            else
            {
                makeSurface(viewport, surface, theme.text);
            }
        }
        JViewport columnHeader = scrollPane.getColumnHeader();
        if (columnHeader != null)
        {
            rememberComponentState(columnHeader);
            if (wallpaperBacked)
            {
                makeWallpaperGlass(columnHeader, alpha(theme.horizon.darker(), proxyContext ? 0 : 18), theme.text);
            }
            else
            {
                makeSurface(columnHeader, alpha(theme.horizon.darker(), 220), theme.text);
            }
        }
        JViewport rowHeader = scrollPane.getRowHeader();
        if (rowHeader != null)
        {
            rememberComponentState(rowHeader);
            if (wallpaperBacked)
            {
                makeWallpaperGlass(rowHeader, alpha(theme.horizon.darker(), proxyContext ? 0 : 18), theme.text);
            }
            else
            {
                makeSurface(rowHeader, alpha(theme.horizon.darker(), 220), theme.text);
            }
        }
        Component[] corners = {
            scrollPane.getCorner(JScrollPane.UPPER_LEFT_CORNER),
            scrollPane.getCorner(JScrollPane.UPPER_RIGHT_CORNER),
            scrollPane.getCorner(JScrollPane.LOWER_LEFT_CORNER),
            scrollPane.getCorner(JScrollPane.LOWER_RIGHT_CORNER)
        };
        for (Component corner : corners)
        {
            if (corner != null)
            {
                rememberComponentState(corner);
                if (corner instanceof JComponent)
                {
                    ((JComponent) corner).setOpaque(!wallpaperBacked);
                }
                corner.setBackground(wallpaperBacked ? transparent(theme.base) : opaque(theme.base.darker()));
                corner.setForeground(theme.text);
            }
        }
    }

    private void themeTextComponent(JTextComponent textComponent, BurpTheme theme, Color surface, boolean wallpaperBacked)
    {
        textComponent.setOpaque(!wallpaperBacked);
        boolean proxyContext = wallpaperBacked && hasLikelyProxyActionContext(textComponent);
        Color background = proxyContext ? alpha(theme.base.darker(), 32) : readableSurface(theme, surface, wallpaperBacked);
        Color selection = alpha(theme.button, wallpaperBacked ? 150 : 255);
        textComponent.setBackground(background);
        textComponent.setForeground(textComponent.isEnabled() ? theme.text : theme.muted);
        textComponent.setCaretColor(theme.accent);
        textComponent.setSelectionColor(selection);
        textComponent.setSelectedTextColor(theme.buttonText);
        textComponent.setDisabledTextColor(theme.muted);
        if (textComponent instanceof JComponent)
        {
            ((JComponent) textComponent).putClientProperty("FlatLaf.style", textComponentStyle(theme, background, selection));
        }
    }

    private void themeTable(JTable table, BurpTheme theme, Color surface, Color panelAlt, Color selection, boolean wallpaperBacked)
    {
        boolean proxyContext = wallpaperBacked && hasLikelyProxyActionContext(table);
        Color background = proxyContext ? alpha(theme.base.darker(), 32) : readableSurface(theme, surface, wallpaperBacked);
        table.setOpaque(!wallpaperBacked);
        table.setBackground(background);
        table.setForeground(theme.text);
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(false);
        table.setGridColor(alpha(theme.accent, 28));
        table.setIntercellSpacing(new Dimension(1, 1));
        table.setSelectionBackground(selection);
        table.setSelectionForeground(theme.buttonText);
        table.putClientProperty("FlatLaf.style", "selectionBackground: " + rgb(selection)
        + "; selectionForeground: " + rgb(theme.buttonText)
        + "; selectionInactiveBackground: " + rgb(theme.button.darker())
        + "; selectionInactiveForeground: " + rgb(theme.buttonText)
        + "; focusCellBackground: " + rgb(background)
        + "; focusCellForeground: " + rgb(theme.text));
        wrapTableRenderers(table);
        Component editor = table.getEditorComponent();
        if (editor != null)
        {
            rememberComponentState(editor);
            editor.setBackground(background);
            editor.setForeground(theme.text);
        }
        JTableHeader header = table.getTableHeader();
        if (header != null)
        {
            rememberComponentState(header);
            header.setOpaque(!wallpaperBacked);
            header.setBackground(wallpaperBacked ? panelAlt : opaque(panelAlt));
            header.setForeground(theme.text);
        }
    }

    private void wrapTableRenderers(JTable table)
    {
        for (Class<?> rendererType : TABLE_RENDERER_TYPES)
        {
            TableCellRenderer renderer = table.getDefaultRenderer(rendererType);
            if (renderer != null && !(renderer instanceof BurpThemeTableRenderer))
            {
                table.setDefaultRenderer(rendererType, new BurpThemeTableRenderer(renderer));
            }
        }
    }

    private void themeTree(JTree tree, BurpTheme theme, Color surface, Color selection, boolean wallpaperBacked)
    {
        boolean proxyContext = wallpaperBacked && hasLikelyProxyActionContext(tree);
        Color background = proxyContext ? alpha(theme.base.darker(), 32) : readableSurface(theme, surface, wallpaperBacked);
        tree.setOpaque(!wallpaperBacked);
        tree.setBackground(background);
        tree.setForeground(theme.text);
        tree.putClientProperty("FlatLaf.style", "selectionBackground: " + rgb(selection)
        + "; selectionForeground: " + rgb(theme.buttonText)
        + "; selectionInactiveBackground: " + rgb(theme.button.darker())
        + "; selectionInactiveForeground: " + rgb(theme.buttonText));
        if (tree.getCellRenderer() != null && !(tree.getCellRenderer() instanceof BurpThemeTreeRenderer))
        {
            tree.setCellRenderer(new BurpThemeTreeRenderer(tree.getCellRenderer()));
        }
        tree.putClientProperty("JTree.lineStyle", "None");
        if (tree.getCellRenderer() instanceof DefaultTreeCellRenderer)
        {
            DefaultTreeCellRenderer renderer = (DefaultTreeCellRenderer) tree.getCellRenderer();
            renderer.setBackgroundNonSelectionColor(background);
            renderer.setBackgroundSelectionColor(selection);
            renderer.setTextNonSelectionColor(theme.text);
            renderer.setTextSelectionColor(theme.buttonText);
            renderer.setBorderSelectionColor(theme.accent);
        }
    }

    private void themeList(JList<?> list, BurpTheme theme, Color surface, Color selection, boolean wallpaperBacked)
    {
        boolean proxyContext = wallpaperBacked && hasLikelyProxyActionContext(list);
        Color background = proxyContext ? alpha(theme.base.darker(), 32) : readableSurface(theme, surface, wallpaperBacked);
        list.setOpaque(!wallpaperBacked);
        list.setBackground(background);
        list.setForeground(theme.text);
        list.setSelectionBackground(selection);
        list.setSelectionForeground(theme.buttonText);
        list.putClientProperty("FlatLaf.style", "selectionBackground: " + rgb(selection)
        + "; selectionForeground: " + rgb(theme.buttonText)
        + "; selectionInactiveBackground: " + rgb(theme.button.darker())
        + "; selectionInactiveForeground: " + rgb(theme.buttonText));
    }

    private void themeComboBox(JComboBox<?> comboBox, BurpTheme theme, Color control, Color selection, boolean wallpaperBacked)
    {
        Color background = wallpaperBacked
                ? alpha(theme.button, comboBox.isEnabled() ? 54 : 34)
                : opaque(comboBox.isEnabled() ? control : theme.base.darker());
        comboBox.setOpaque(!wallpaperBacked);
        comboBox.setBackground(background);
        comboBox.setForeground(comboBox.isEnabled() ? theme.text : alpha(theme.muted, 175));
        comboBox.putClientProperty("FlatLaf.style", comboBoxStyle(theme, background, selection));
        Component editor = comboBox.isEditable() && comboBox.getEditor() != null ? comboBox.getEditor().getEditorComponent() : null;
        if (editor != null)
        {
            rememberComponentState(editor);
            if (editor instanceof JComponent)
            {
                ((JComponent) editor).setOpaque(!wallpaperBacked);
            }
            editor.setBackground(wallpaperBacked ? alpha(theme.base.darker(), 32) : opaque(theme.base.darker()));
            editor.setForeground(theme.text);
        }
        for (Component child : comboBox.getComponents())
        {
            rememberComponentState(child);
            if (child instanceof JComponent)
            {
                ((JComponent) child).setOpaque(!wallpaperBacked);
            }
            child.setBackground(wallpaperBacked
                    ? alpha(theme.button, comboBox.isEnabled() ? 44 : 28)
                    : opaque(comboBox.isEnabled() ? control : theme.base.darker()));
            child.setForeground(comboBox.isEnabled() ? theme.text : alpha(theme.muted, 175));
        }
    }

    private Color readableSurface(BurpTheme theme, Color surface, boolean wallpaperBacked)
    {
        if (!wallpaperBacked)
        {
            return opaque(surface);
        }
        int alpha = Math.max(28, surface.getAlpha());
        return alpha(theme.base.darker(), alpha);
    }

    private String textComponentStyle(BurpTheme theme, Color background, Color selection)
    {
        return "arc: 10"
        + "; background: " + rgb(background)
        + "; foreground: " + rgb(theme.text)
        + "; disabledBackground: " + rgb(background)
        + "; disabledForeground: " + rgb(theme.muted)
        + "; inactiveBackground: " + rgb(background)
        + "; inactiveForeground: " + rgb(theme.muted)
        + "; caretForeground: " + rgb(theme.accent)
        + "; selectionBackground: " + rgb(selection)
        + "; selectionForeground: " + rgb(theme.buttonText)
        + "; borderColor: " + rgb(alpha(theme.accent, 108))
        + "; focusedBorderColor: " + rgb(theme.gold)
        + "; focusWidth: 1";
    }

    private String comboBoxStyle(BurpTheme theme, Color background, Color selection)
    {
        return "arc: 12"
        + "; background: " + rgb(background)
        + "; foreground: " + rgb(theme.text)
        + "; disabledBackground: " + rgb(theme.base.darker())
        + "; disabledForeground: " + rgb(theme.muted)
        + "; selectionBackground: " + rgb(selection)
        + "; selectionForeground: " + rgb(theme.buttonText)
        + "; buttonBackground: " + rgb(background)
        + "; buttonArrowColor: " + rgb(theme.text)
        + "; buttonDisabledArrowColor: " + rgb(theme.muted)
        + "; buttonHoverBackground: " + rgb(theme.button)
        + "; buttonPressedBackground: " + rgb(theme.button.darker())
        + "; borderColor: " + rgb(alpha(theme.accent, 116))
        + "; focusedBorderColor: " + rgb(theme.gold)
        + "; focusWidth: 1";
    }

    private void themeButton(AbstractButton button, BurpTheme theme, Color shell, boolean wallpaperBacked)
    {
        if (button instanceof JMenu || button instanceof JMenuItem)
        {
            themeMenuItem(button, theme);
            return;
        }
        installButtonThemeListener(button);
        if (isProxySplitActionButton(button))
        {
            themeProxySplitAction(button, theme);
            return;
        }
        if (hasLikelyProxyActionContext(button) && isProxySplitArrowButton(button))
        {
            themeProxySplitButton(button, theme, alpha(theme.button, 96), theme.buttonText, alpha(theme.accent, 150));
            return;
        }
        if (isProxyPillButtonCandidate(button))
        {
            themeProxyPillButton(button, theme);
            return;
        }
        if (button instanceof JCheckBox || button.getClass().getName().contains("RadioButton"))
        {
            button.setOpaque(false);
            button.setContentAreaFilled(false);
            button.setBorderPainted(false);
            button.setBackground(shell);
            button.setForeground(button.isEnabled() ? theme.text : alpha(theme.muted, 160));
            return;
        }
        boolean enabled = button.isEnabled();
        boolean selected = button.isSelected();
        boolean compact = isCompactActionButton(button);
        if (wallpaperBacked)
        {
            themeGenericPillButton(button, theme, compact);
            return;
        }
        Color background = enabled ? (selected ? alpha(theme.button, 238) : alpha(theme.horizon.darker(), 218)) : alpha(theme.horizon.darker(), 190);
        Color foreground = enabled ? theme.buttonText : alpha(theme.text, 150);
        Color border = enabled ? (selected ? alpha(theme.gold, 150) : alpha(theme.accent, 122)) : alpha(theme.accent, 72);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(true);
        button.setFocusPainted(true);
        button.setRolloverEnabled(true);
        button.setBackground(opaque(background));
        button.setForeground(foreground);
        int verticalPad = compact ? 3 : 5;
        int horizontalPad = compact ? 7 : 12;
        if (!isLocalThemeComponent(button))
        {
            if (button.getClientProperty("JButton.buttonType") == null)
            {
                button.putClientProperty("JButton.buttonType", "roundRect");
            }
            button.putClientProperty("FlatLaf.style", buttonStyle(theme, compact));
            button.setMargin(new Insets(verticalPad, horizontalPad, verticalPad, horizontalPad));
            button.revalidate();
            button.repaint();
            return;
        }
        if (!(button.getBorder() instanceof javax.swing.plaf.UIResource))
        {
            return;
        }
        button.setBorder(new RoundLineBorder(border, compact ? 10 : 12, new Insets(verticalPad, horizontalPad, verticalPad, horizontalPad)));
        button.revalidate();
        button.repaint();
    }

    private void themeGenericPillButton(AbstractButton button, BurpTheme theme, boolean compact)
    {
        boolean enabled = button.isEnabled();
        boolean selected = button.isSelected();
        Color fill = enabled ? alpha(selected ? theme.button : theme.button.darker(), selected ? 200 : 150) : alpha(theme.button.darker(), 92);
        Color hoverFill = enabled ? alpha(theme.button, selected ? 215 : 182) : fill;
        Color pressedFill = enabled ? alpha(darken(theme.button, 0.10d), 210) : fill;
        Color foreground = enabled ? theme.buttonText : alpha(theme.text, 176);
        Color outline = enabled ? alpha(selected ? theme.gold : lighten(theme.accent, 0.32d), selected ? 185 : 150) : alpha(theme.accent, 80);
        Insets margin = new Insets(compact ? 4 : 6, compact ? 10 : 14, compact ? 4 : 6, compact ? 10 : 14);
        installPillPainter(button, fill, hoverFill, pressedFill, outline, foreground, margin, false);
    }

    private boolean isProxySplitActionButton(AbstractButton button)
    {
        String normalized = normalizedButtonText(button);
        return isProxyActionText(normalized)
                && (findProxyActionCluster(button) != null || hasLikelyProxyActionContext(button));
    }

    private boolean isProxyActionText(String normalized)
    {
        return PROXY_FORWARD_ACTION.equals(normalized) || PROXY_DROP_ACTION.equals(normalized);
    }

    private boolean isProxyPillButtonCandidate(AbstractButton button)
    {
        if (button instanceof JCheckBox || button.getClass().getName().contains("RadioButton"))
        {
            return false;
        }
        String normalized = normalizedButtonText(button);
        if (isKnownProxyPillText(normalized))
        {
            return true;
        }
        return hasLikelyProxyActionContext(button) && (!normalized.isEmpty() || button.getIcon() != null);
    }

    private boolean isKnownProxyPillText(String normalized)
    {
        return normalized.contains("intercept")
        || "open browser".equals(normalized)
        || "learn more".equals(normalized)
        || "open browser".equals(normalized.replace("...", "").trim());
    }

    private String normalizedButtonText(AbstractButton button)
    {
        String text = button.getText();
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    private Container findProxyActionCluster(AbstractButton button)
    {
        int depth = 0;
        for (Container current = button.getParent(); current != null && depth < 7; current = current.getParent(), depth++)
        {
            if (current instanceof JRootPane || current instanceof JLayeredPane || current instanceof JFrame)
            {
                return null;
            }
            if (isProxyActionCluster(current))
            {
                return current;
            }
        }
        return null;
    }

    private boolean isProxyActionCluster(Container container)
    {
        Dimension size = container.getSize();
        Dimension preferred = container.getPreferredSize();
        int width = Math.max(size.width, preferred.width);
        int height = Math.max(size.height, preferred.height);
        if (height > 96 || width > 520)
        {
            return false;
        }
        return containsProxyActionButton(container, PROXY_FORWARD_ACTION, 0)
                && containsProxyActionButton(container, PROXY_DROP_ACTION, 0);
    }

    private boolean containsProxyActionButton(Container container, String action, int depth)
    {
        if (depth > 4)
        {
            return false;
        }
        for (Component child : container.getComponents())
        {
            if (child instanceof AbstractButton && action.equals(normalizedButtonText((AbstractButton) child)))
            {
                return true;
            }
            if (child instanceof Container && containsProxyActionButton((Container) child, action, depth + 1))
            {
                return true;
            }
        }
        return false;
    }

    private void themeProxySplitAction(AbstractButton button, BurpTheme theme)
    {
        proxyActionButtons.add(button);
        Color background = alpha(theme.button, 72);
        Color foreground = theme.buttonText;
        Color border = alpha(theme.accent, 150);

        Container splitWrapper = findNearestProxySplitWrapper(button);
        if (splitWrapper != null)
        {
            refreshProxySplitWrapperUiIfNeeded(splitWrapper, theme);
            themeProxySplitWrapper(splitWrapper, theme, background, foreground);
            themeProxySplitWrapperButtons(splitWrapper, theme, background, foreground, border, null, 0);
        }

        if (button.isDisplayable())
        {
            themeProxySplitButton(button, theme, background, foreground, border);
        }

        Container actionCluster = findProxyActionCluster(button);
        if (actionCluster != null && actionCluster != splitWrapper)
        {
            themeProxySplitWrapper(actionCluster, theme, transparent(theme.base), foreground);
        }
    }

    private void themeMenuItem(AbstractButton button, BurpTheme theme)
    {
        Color background = alpha(theme.base.darker(), 20);
        Color selection = alpha(theme.button, 150);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setBackground(background);
        button.setForeground(button.isEnabled() ? theme.text : theme.muted);
        button.putClientProperty("FlatLaf.style", "background: " + rgb(background)
        + "; foreground: " + rgb(theme.text)
        + "; disabledForeground: " + rgb(theme.muted)
        + "; selectionBackground: " + rgb(selection)
        + "; selectionForeground: " + rgb(theme.buttonText)
        + "; acceleratorForeground: " + rgb(theme.muted)
        + "; acceleratorSelectionForeground: " + rgb(theme.buttonText)
        + "; arrowColor: " + rgb(theme.text)
        + "; disabledArrowColor: " + rgb(theme.muted)
        + "; checkBackground: " + rgb(selection)
        + "; checkForeground: " + rgb(theme.buttonText));
    }

    private void themeProxyPillButton(AbstractButton button, BurpTheme theme)
    {
        rememberComponentState(button);
        boolean enabled = button.isEnabled();
        boolean selected = button.isSelected();
        Color fill = enabled ? alpha(selected ? theme.button : theme.button.darker(), selected ? 205 : 158) : alpha(theme.button.darker(), 96);
        Color hoverFill = enabled ? alpha(theme.button, selected ? 220 : 188) : fill;
        Color pressedFill = enabled ? alpha(darken(theme.button, 0.10d), 212) : fill;
        Color foreground = enabled ? theme.buttonText : alpha(theme.text, 188);
        Color outline = enabled ? alpha(selected ? theme.gold : lighten(theme.accent, 0.32d), selected ? 195 : 158) : alpha(theme.accent, 96);
        installProxyPillPainter(button, fill, hoverFill, pressedFill, outline, foreground, new Insets(5, 14, 5, 14));
    }

    private void rethemeKnownProxyActionButtons(BurpTheme theme)
    {
        for (AbstractButton button : new ArrayList<>(proxyActionButtons))
        {
            if (button == null || !button.isDisplayable())
            {
                proxyActionButtons.remove(button);
                continue;
            }
            if (isProxySplitActionButton(button))
            {
                themeProxySplitAction(button, theme);
            }
            else
            {
                proxyActionButtons.remove(button);
            }
        }
    }

    private void themeProxySplitWrapperDirect(Container splitWrapper, BurpTheme theme)
    {
        Color background = alpha(theme.button, 42);
        Color foreground = theme.buttonText;
        Color border = alpha(theme.accent, 150);
        refreshProxySplitWrapperUiIfNeeded(splitWrapper, theme);
        themeProxySplitWrapper(splitWrapper, theme, background, foreground);
        themeProxySplitWrapperButtons(splitWrapper, theme, background, foreground, border, null, 0);
    }

    private Container findNearestProxySplitWrapper(AbstractButton button)
    {
        Container actionCluster = findProxyActionCluster(button);
        Container compactFallback = null;
        int depth = 0;
        for (Container current = button.getParent(); current != null && depth < 7; current = current.getParent(), depth++)
        {
            if (isProxySplitWrapperShape(current))
            {
                return current;
            }
            if (compactFallback == null && isCompactProxyButtonContainer(current) && containsProxySplitPieces(current, button))
            {
                compactFallback = current;
            }
            if (current instanceof JRootPane || current instanceof JLayeredPane || current instanceof JFrame)
            {
                return compactFallback != null ? compactFallback : actionCluster;
            }
        }
        return compactFallback != null ? compactFallback : actionCluster;
    }

    private boolean isProxySplitWrapperComponent(Container container)
    {
        return (isProxySplitWrapperShape(container) || isCompactProxyButtonContainer(container)) && isProxyActionCluster(container);
    }

    private boolean isProxySplitWrapperShape(Container container)
    {
        if (container == null)
        {
            return false;
        }
        String className = container.getClass().getName().toLowerCase(Locale.ROOT);
        if (className.contains("splitbutton") || className.equals("burp.zpdj"))
        {
            return true;
        }
        if (container instanceof JComboBox && ((JComboBox<?>) container).getUI() != null)
        {
            String uiName = ((JComboBox<?>) container).getUI().getClass().getName().toLowerCase(Locale.ROOT);
            return uiName.contains("burpsplitbutton") || uiName.contains("splitbutton");
        }
        return false;
    }

    private boolean isCompactProxyButtonContainer(Container container)
    {
        if (container == null || container instanceof JRootPane || container instanceof JLayeredPane || container instanceof JFrame)
        {
            return false;
        }
        Dimension size = container.getSize();
        Dimension preferred = container.getPreferredSize();
        int width = Math.max(size.width, preferred.width);
        int height = Math.max(size.height, preferred.height);
        return width <= 360 && height <= 78;
    }

    private boolean containsProxySplitPieces(Container container, AbstractButton actionButton)
    {
        return SwingUtilities.isDescendingFrom(actionButton, container)
        && containsProxySplitArrowButton(container, 0);
    }

    private boolean containsProxySplitArrowButton(Container container, int depth)
    {
        if (depth > 4)
        {
            return false;
        }
        for (Component child : container.getComponents())
        {
            if (child instanceof AbstractButton && isProxySplitArrowButton((AbstractButton) child))
            {
                return true;
            }
            if (child instanceof Container && containsProxySplitArrowButton((Container) child, depth + 1))
            {
                return true;
            }
        }
        return false;
    }

    private void themeProxySplitWrapperButtons(Container container, BurpTheme theme, Color background, Color foreground, Color border, AbstractButton primary, int depth)
    {
        if (depth > 2)
        {
            return;
        }
        for (Component child : container.getComponents())
        {
            if (child instanceof AbstractButton)
            {
                AbstractButton childButton = (AbstractButton) child;
                if (childButton == primary || isProxySplitArrowButton(childButton) || isProxySplitActionLabel(childButton))
                {
                    themeProxySplitButton(childButton, theme, background, foreground, border);
                }
            }
            else if (child instanceof Container)
            {
                themeProxySplitWrapper((Container) child, theme, background, foreground);
                themeProxySplitWrapperButtons((Container) child, theme, background, foreground, border, primary, depth + 1);
            }
        }
    }

    private boolean isProxySplitActionLabel(AbstractButton button)
    {
        String normalized = normalizedButtonText(button);
        return PROXY_FORWARD_ACTION.equals(normalized) || PROXY_DROP_ACTION.equals(normalized);
    }

    private boolean isProxySplitArrowButton(AbstractButton button)
    {
        String normalized = normalizedButtonText(button);
        Dimension preferred = button.getPreferredSize();
        return normalized.isEmpty() && preferred.width <= 42 && preferred.height <= 42;
    }

    private void themeProxySplitWrapper(Component component, BurpTheme theme, Color background, Color foreground)
    {
        rememberComponentState(component);
        component.setBackground(background);
        component.setForeground(foreground);
        if (component instanceof JComponent)
        {
            JComponent jComponent = (JComponent) component;
            jComponent.setOpaque(false);
            jComponent.putClientProperty("FlatLaf.style", proxySplitStyle(theme));
            installGlassBorder(jComponent, transparent(theme.button), alpha(theme.gold, 58), 999);
            jComponent.revalidate();
        }
        component.repaint();
    }

    private void refreshProxySplitWrapperUiIfNeeded(Container splitWrapper, BurpTheme theme)
    {
        if (!(splitWrapper instanceof JComponent))
        {
            return;
        }
        JComponent component = (JComponent) splitWrapper;
        Integer generation = Integer.valueOf(themeGeneration.get());
        if (generation.equals(component.getClientProperty("burptheme.proxySplitGeneration")))
        {
            return;
        }
        rememberComponentState(component);
        proxySplitWrappers.add(component);
        component.putClientProperty("FlatLaf.style", proxySplitStyle(theme));
        component.putClientProperty("burptheme.proxySplitGeneration", generation);
        component.revalidate();
        component.repaint();
    }

    private void themeProxySplitButton(AbstractButton button, BurpTheme theme, Color background, Color foreground, Color border)
    {
        rememberComponentState(button);
        boolean enabled = button.isEnabled();
        boolean arrow = isProxySplitArrowButton(button);
        Color fill = enabled ? alpha(theme.button.darker(), arrow ? 142 : 160) : alpha(theme.button.darker(), 90);
        Color hoverFill = enabled ? alpha(theme.button, 190) : fill;
        Color pressedFill = enabled ? alpha(darken(theme.button, 0.10d), 212) : fill;
        Color effectiveForeground = enabled ? foreground : alpha(theme.text, 184);
        Color effectiveBorder = enabled ? alpha(lighten(theme.accent, 0.28d), 168) : alpha(theme.accent, 86);
        installProxyPillPainter(button, fill, hoverFill, pressedFill, effectiveBorder, effectiveForeground, new Insets(4, arrow ? 10 : 14, 4, arrow ? 10 : 14));
    }

    private void installProxyPillPainter(AbstractButton button, Color fill, Color hoverFill, Color pressedFill, Color outline, Color foreground, Insets margin)
    {
        installPillPainter(button, fill, hoverFill, pressedFill, outline, foreground, margin, true);
    }

    private void installPillPainter(AbstractButton button, Color fill, Color hoverFill, Color pressedFill, Color outline, Color foreground, Insets margin, boolean proxyMarker)
    {
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(true);
        button.setFocusPainted(true);
        button.setRolloverEnabled(true);
        button.setBackground(fill);
        button.setForeground(foreground);
        button.setMargin(margin);
        button.putClientProperty("JButton.buttonType", "roundRect");
        button.putClientProperty(PROXY_PILL_BUTTON_PROPERTY, proxyMarker ? Boolean.TRUE : null);
        button.putClientProperty(PROXY_PILL_FILL_PROPERTY, fill);
        button.putClientProperty(PROXY_PILL_HOVER_FILL_PROPERTY, hoverFill);
        button.putClientProperty(PROXY_PILL_PRESSED_FILL_PROPERTY, pressedFill);
        button.putClientProperty(PROXY_PILL_OUTLINE_PROPERTY, outline);
        button.putClientProperty("FlatLaf.style", proxySplitStyle(currentTheme));
        if (!(button.getUI() instanceof ProxyPillButtonUI))
        {
            button.setUI(new ProxyPillButtonUI());
        }
        button.setBorder(new RoundLineBorder(outline, 999, margin));
        button.revalidate();
        button.repaint();
    }

    private static void clearProxyButtonProperties(AbstractButton button)
    {
        button.putClientProperty(PROXY_PILL_BUTTON_PROPERTY, null);
        button.putClientProperty(PROXY_PILL_FILL_PROPERTY, null);
        button.putClientProperty(PROXY_PILL_HOVER_FILL_PROPERTY, null);
        button.putClientProperty(PROXY_PILL_PRESSED_FILL_PROPERTY, null);
        button.putClientProperty(PROXY_PILL_OUTLINE_PROPERTY, null);
    }

    private String proxySplitStyle(BurpTheme theme)
    {
        String background = rgb(theme.button.darker());
        String foreground = rgb(theme.buttonText);
        String disabledBackground = rgb(theme.base.darker());
        String disabledForeground = rgb(theme.muted);
        String border = rgb(alpha(theme.accent, 150));
        String disabledBorder = rgb(alpha(theme.muted, 120));
        return "arc: 999"
        + "; background: " + background
        + "; foreground: " + foreground
        + "; disabledBackground: " + disabledBackground
        + "; disabledForeground: " + disabledForeground
        + "; disabledSelectedBackground: " + disabledBackground
        + "; disabledSelectedForeground: " + disabledForeground
        + "; selectedBackground: " + background
        + "; selectedForeground: " + foreground
        + "; hoverBackground: " + rgb(theme.button)
        + "; pressedBackground: " + rgb(theme.button.darker())
        + "; borderColor: " + border
        + "; disabledBorderColor: " + disabledBorder
        + "; separatorColor: " + border
        + "; arrowColor: " + foreground
        + "; disabledArrowColor: " + disabledForeground
        + "; focusWidth: 1"
        + "; focusColor: " + rgb(theme.gold);
    }

    private void installButtonThemeListener(AbstractButton button)
    {
        if (Boolean.TRUE.equals(button.getClientProperty("burptheme.buttonListener")))
        {
            return;
        }
        PropertyChangeListener listener = event -> {
            String property = event.getPropertyName();
            if (!("enabled".equals(property) || "selected".equals(property)) || !isSuiteTintEnabled())
            {
                return;
            }
            int generation = themeGeneration.get();
            SwingUtilities.invokeLater(() -> {
                if (generation == themeGeneration.get() && isSuiteTintEnabled() && button.isDisplayable())
                {
                    themeButton(button, currentTheme, transparent(currentTheme.base), true);
                    button.revalidate();
                    button.repaint();
                }
            });
        };
        button.putClientProperty("burptheme.buttonListener", Boolean.TRUE);
        button.addPropertyChangeListener("enabled", listener);
        button.addPropertyChangeListener("selected", listener);
        buttonThemeListeners.put(button, listener);
    }

    private boolean isCompactActionButton(AbstractButton button)
    {
        String text = button.getText();
        return button.getParent() instanceof JToolBar
        || text == null
        || text.trim().isEmpty()
        || button.getPreferredSize().height <= 24;
    }

    private String buttonStyle(BurpTheme theme, boolean compact)
    {
        int arc = compact ? 10 : 12;
        return "arc: " + arc
        + "; background: " + rgb(theme.horizon.darker())
        + "; foreground: " + rgb(theme.buttonText)
        + "; disabledBackground: " + rgb(theme.base.darker())
        + "; disabledSelectedBackground: " + rgb(theme.base.darker())
        + "; disabledSelectedForeground: " + rgb(theme.muted)
        + "; disabledForeground: " + rgb(theme.muted)
        + "; borderColor: " + rgb(alpha(theme.accent, 122))
        + "; disabledBorderColor: " + rgb(alpha(theme.accent, 72))
        + "; selectedBackground: " + rgb(theme.button)
        + "; selectedForeground: " + rgb(theme.buttonText)
        + "; arrowColor: " + rgb(theme.buttonText)
        + "; disabledArrowColor: " + rgb(theme.muted)
        + "; separatorColor: " + rgb(alpha(theme.accent, 95))
        + "; hoverBackground: " + rgb(theme.button)
        + "; pressedBackground: " + rgb(theme.button.darker())
        + "; focusColor: " + rgb(alpha(theme.gold, 190))
        + "; focusWidth: 1";
    }

    private String rgb(Color color)
    {
        Color resolved = color.getAlpha() >= 255 ? color : compositeOverThemeSurface(color);
        return String.format("#%02x%02x%02x", resolved.getRed(), resolved.getGreen(), resolved.getBlue());
    }

    private Color compositeOverThemeSurface(Color color)
    {
        double alpha = color.getAlpha() / 255.0d;
        Color backdrop = currentTheme.base.darker();
        int red = (int) Math.round(color.getRed() * alpha + backdrop.getRed() * (1.0d - alpha));
        int green = (int) Math.round(color.getGreen() * alpha + backdrop.getGreen() * (1.0d - alpha));
        int blue = (int) Math.round(color.getBlue() * alpha + backdrop.getBlue() * (1.0d - alpha));
        return new Color(red, green, blue);
    }

    private void themeLabel(JLabel label, BurpTheme theme)
    {
        if (isCountBadgeLabel(label))
        {
            label.setOpaque(true);
            label.setBackground(opaque(theme.button));
            label.setForeground(theme.buttonText);
            label.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(alpha(theme.gold, 120)),
            BorderFactory.createEmptyBorder(1, 5, 1, 5)
            ));
            label.repaint();
            return;
        }
        Font font = label.getFont();
        label.setOpaque(false);
        label.setBackground(transparent(theme.base));
        if (font != null && font.isBold() && isLocalThemeComponent(label) && font.getSize() >= 14)
        {
            label.setForeground(alpha(theme.gold, 210));
        }
        else if (font != null && font.isBold() && font.getSize() >= 18)
        {
            label.setForeground(alpha(theme.text, 230));
        }
        else
        {
            label.setForeground(label.isEnabled() ? theme.text : theme.muted);
        }
    }

    private boolean hasLikelyProxyActionContext(Component component)
    {
        int depth = 0;
        for (Component current = component; current != null && depth < 12; current = current.getParent(), depth++)
        {
            String className = current.getClass().getName().toLowerCase(Locale.ROOT);
            String name = current.getName() == null ? "" : current.getName().toLowerCase(Locale.ROOT);
            if (className.contains("proxy")
            || className.contains("intercept")
            || name.contains("proxy")
            || name.contains("intercept"))
            {
                return true;
            }
        }
        return false;
    }

    private boolean isCountBadgeLabel(JLabel label)
    {
        String text = label.getText();
        if (text == null || !text.trim().matches("\\d{1,3}"))
        {
            return false;
        }
        Dimension preferred = label.getPreferredSize();
        return preferred.width <= 42
        && preferred.height <= 28
        && (hasAncestorNamed(label, "inspector") || hasNearbyInspectorText(label));
    }

    private boolean hasAncestorNamed(Component component, String token)
    {
        String needle = token.toLowerCase(Locale.ROOT);
        for (Component current = component; current != null; current = current.getParent())
        {
            String className = current.getClass().getName().toLowerCase(Locale.ROOT);
            String name = current.getName() == null ? "" : current.getName().toLowerCase(Locale.ROOT);
            if (className.contains(needle) || name.contains(needle))
            {
                return true;
            }
        }
        return false;
    }

    private boolean hasNearbyInspectorText(Component component)
    {
        Container current = component.getParent();
        for (int depth = 0; current != null && depth < 4; depth++, current = current.getParent())
        {
            if (containsInspectorLabel(current, Collections.newSetFromMap(new IdentityHashMap<>()), 0))
            {
                return true;
            }
        }
        return false;
    }

    private boolean containsInspectorLabel(Container container, Set<Component> visited, int depth)
    {
        if (container == null || depth > 8 || visited.contains(container))
        {
            return false;
        }
        visited.add(container);
        for (Component child : container.getComponents())
        {
            if (child instanceof JLabel)
            {
                String text = ((JLabel) child).getText();
                if (text != null)
                {
                    String normalized = text.toLowerCase(Locale.ROOT);
                    if (normalized.contains("request attributes")
                    || normalized.contains("request headers")
                    || normalized.contains("response headers"))
                    {
                        return true;
                    }
                }
            }
            if (child instanceof Container && containsInspectorLabel((Container) child, visited, depth + 1))
            {
                return true;
            }
        }
        return false;
    }

    private void themeTabbedPane(JTabbedPane tabbedPane, BurpTheme theme)
    {
        tabbedPane.setOpaque(false);
        tabbedPane.setBackground(transparent(theme.base));
        tabbedPane.setForeground(theme.text);
        if (!(tabbedPane.getUI() instanceof BurpThemeTabbedPaneUI)
        || ((BurpThemeTabbedPaneUI) tabbedPane.getUI()).theme != theme)
        {
            tabbedPane.setUI(new BurpThemeTabbedPaneUI(theme));
        }
        for (int index = 0; index < tabbedPane.getTabCount(); index++)
        {
            boolean selected = index == tabbedPane.getSelectedIndex();
            tabbedPane.setForegroundAt(index, selected ? alpha(theme.gold, 218) : alpha(theme.text, 225));
            tabbedPane.setBackgroundAt(index, selected ? alpha(theme.button, 92) : transparent(theme.base));
            Component tabComponent = tabbedPane.getTabComponentAt(index);
            if (tabComponent != null)
            {
                rememberComponentState(tabComponent);
                if (tabComponent instanceof JComponent)
                {
                    ((JComponent) tabComponent).setOpaque(false);
                }
                tabComponent.setForeground(selected ? theme.gold : theme.text);
                tabComponent.setBackground(selected ? alpha(theme.button, 92) : transparent(theme.base));
            }
        }
        if (!Boolean.TRUE.equals(tabbedPane.getClientProperty("burptheme.tabListener")))
        {
            tabbedPane.putClientProperty("burptheme.tabListener", Boolean.TRUE);
            ChangeListener listener = event -> {
                if (extensionUnloaded.get() || !isSuiteTintEnabled())
                {
                    return;
                }
                JTabbedPane source = (JTabbedPane) event.getSource();
                if (!source.isDisplayable())
                {
                    return;
                }
                themeTabbedPane(source, currentTheme);
                Component selected = source.getSelectedComponent();
                if (selected instanceof JComponent
                && !Boolean.TRUE.equals(((JComponent) selected).getClientProperty("burptheme.tabContentThemed")))
                {
                    // Theme each tab's content the first time it becomes visible, then leave it
                    // alone. Re-walking the whole subtree on every tab click caused a flash;
                    // later additions are handled by the COMPONENT_ADDED listener and the
                    // global theme pass (which reaches inactive tab children too).
                    ((JComponent) selected).putClientProperty("burptheme.tabContentThemed", Boolean.TRUE);
                    Window window = SwingUtilities.getWindowAncestor(source);
                    boolean selectedWallpaperBacked = window instanceof JFrame;
                    Set<Component> visited = Collections.newSetFromMap(new IdentityHashMap<>());
                    boolean wasApplying = applyingGlobalTheme;
                    applyingGlobalTheme = true;
                    try
                    {
                        themeBurpComponent(selected, currentTheme, 0, MAX_THEME_COMPONENTS_PER_PASS, visited, selectedWallpaperBacked);
                    }
                    finally
                    {
                        applyingGlobalTheme = wasApplying;
                    }
                    source.repaint();
                }
            };
            tabThemeListeners.put(tabbedPane, listener);
            tabbedPane.addChangeListener(listener);
        }
    }

    private boolean isLocalThemeComponent(Component component)
    {
        return localRoot != null && (component == localRoot || SwingUtilities.isDescendingFrom(component, localRoot));
    }

    private boolean isStructuralShell(Component component)
    {
        return component instanceof JViewport
        || component instanceof JSplitPane
        || component instanceof JPanel
        || component instanceof JToolBar;
    }

    private boolean isStatusBar(Component component)
    {
        String simpleName = component.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        return simpleName.contains("statusbar") || simpleName.equals("statuspanel");
    }

    private void makeTransparent(Component component, Color background, Color foreground)
    {
        if (component instanceof JComponent)
        {
            JComponent jComponent = (JComponent) component;
            jComponent.setOpaque(false);
            removeGlassBorder(jComponent);
        }
        component.setBackground(background);
        component.setForeground(foreground);
    }

    private void makeWallpaperGlass(Component component, Color background, Color foreground)
    {
        if (component instanceof JComponent)
        {
            JComponent jComponent = (JComponent) component;
            jComponent.setOpaque(false);
            installGlassBorder(jComponent, background, alpha(foreground, 52));
        }
        component.setBackground(background);
        component.setForeground(foreground);
    }

    private void makeSurface(Component component, Color background, Color foreground)
    {
        if (component instanceof JComponent)
        {
            JComponent jComponent = (JComponent) component;
            jComponent.setOpaque(true);
            removeGlassBorder(jComponent);
        }
        component.setBackground(background.getAlpha() >= 255 ? opaque(background) : compositeOverThemeSurface(background));
        component.setForeground(foreground);
    }

    private void installGlassBorder(JComponent component, Color fill, Color border)
    {
        installGlassBorder(component, fill, border, 10);
    }

    private void installGlassBorder(JComponent component, Color fill, Color border, int arc)
    {
        if (component instanceof JViewport)
        {
            component.putClientProperty(GLASS_FILL_ALPHA_PROPERTY, Integer.valueOf(fill.getAlpha()));
            component.putClientProperty(GLASS_BORDER_ALPHA_PROPERTY, Integer.valueOf(border.getAlpha()));
            return;
        }
        Border current = component.getBorder();
        Border delegate = current instanceof GlassSurfaceBorder ? ((GlassSurfaceBorder) current).delegate : current;
        component.putClientProperty(GLASS_FILL_ALPHA_PROPERTY, Integer.valueOf(fill.getAlpha()));
        component.putClientProperty(GLASS_BORDER_ALPHA_PROPERTY, Integer.valueOf(border.getAlpha()));
        component.setBorder(new GlassSurfaceBorder(delegate, fill, border, arc));
    }

    private void removeGlassBorder(JComponent component)
    {
        Border current = component.getBorder();
        if (current instanceof GlassSurfaceBorder)
        {
            component.setBorder(((GlassSurfaceBorder) current).delegate);
        }
        component.putClientProperty(GLASS_FILL_ALPHA_PROPERTY, null);
        component.putClientProperty(GLASS_BORDER_ALPHA_PROPERTY, null);
    }

    private Color transparent(Color color)
    {
        return alpha(color, 0);
    }

    private Color opaque(Color color)
    {
        return alpha(color, 255);
    }

    private Color alpha(Color color, int alpha)
    {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private Color mix(Color from, Color to, double amount)
    {
        double t = Math.max(0d, Math.min(1d, amount));
        int r = (int) Math.round(from.getRed() + (to.getRed() - from.getRed()) * t);
        int g = (int) Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * t);
        int b = (int) Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * t);
        return new Color(r, g, b, from.getAlpha());
    }

    private Color lighten(Color color, double amount)
    {
        return mix(color, Color.WHITE, amount);
    }

    private Color darken(Color color, double amount)
    {
        return mix(color, Color.BLACK, amount);
    }

    private static final class GlassSurfaceBorder extends AbstractBorder
    {
        private static final long serialVersionUID = 1L;

        private final transient Border delegate;
        private final Color fill;
        private final Color border;
        private final int arc;

        private GlassSurfaceBorder(Border delegate, Color fill, Color border, int arc)
        {
            this.delegate = delegate;
            this.fill = fill;
            this.border = border;
            this.arc = arc;
        }

        @Override
        public Insets getBorderInsets(Component component)
        {
            return delegate == null ? new Insets(0, 0, 0, 0) : delegate.getBorderInsets(component);
        }

        @Override
        public Insets getBorderInsets(Component component, Insets insets)
        {
            Insets delegateInsets = getBorderInsets(component);
            insets.top = delegateInsets.top;
            insets.left = delegateInsets.left;
            insets.bottom = delegateInsets.bottom;
            insets.right = delegateInsets.right;
            return insets;
        }

        @Override
        public boolean isBorderOpaque()
        {
            return false;
        }

        @Override
        public void paintBorder(Component component, Graphics graphics, int x, int y, int width, int height)
        {
            if (delegate != null)
            {
                delegate.paintBorder(component, graphics, x, y, width, height);
            }
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (fill.getAlpha() > 0)
            {
                g2.setColor(fill);
                g2.fillRoundRect(x, y, Math.max(1, width - 1), Math.max(1, height - 1), arc, arc);
            }
            g2.setColor(border);
            g2.drawRoundRect(x, y, Math.max(1, width - 1), Math.max(1, height - 1), arc, arc);
            g2.dispose();
        }
    }

    private static final class RoundLineBorder extends AbstractBorder
    {
        private static final long serialVersionUID = 1L;

        private final Color color;
        private final int arc;
        private final Insets insets;

        private RoundLineBorder(Color color, int arc, Insets insets)
        {
            this.color = color;
            this.arc = arc;
            this.insets = insets;
        }

        @Override
        public Insets getBorderInsets(Component component)
        {
            return new Insets(insets.top, insets.left, insets.bottom, insets.right);
        }

        @Override
        public Insets getBorderInsets(Component component, Insets target)
        {
            target.top = insets.top;
            target.left = insets.left;
            target.bottom = insets.bottom;
            target.right = insets.right;
            return target;
        }

        @Override
        public boolean isBorderOpaque()
        {
            return false;
        }

        @Override
        public void paintBorder(Component component, Graphics graphics, int x, int y, int width, int height)
        {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.drawRoundRect(x, y, Math.max(1, width - 1), Math.max(1, height - 1), arc, arc);
            g2.dispose();
        }
    }

    private static final class ProxyPillButtonUI extends BasicButtonUI
    {
        @Override
        public void paint(Graphics graphics, JComponent component)
        {
            if (component instanceof AbstractButton)
            {
                AbstractButton button = (AbstractButton) component;
                Graphics2D g2 = (Graphics2D) graphics.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = Math.max(1, component.getWidth());
                int h = Math.max(1, component.getHeight());
                int arc = h;
                Color fill = proxyPillFill(button);
                if (fill != null)
                {
                    // Vertical gradient (lighter top -> darker bottom) gives the pill a raised,
                    // shaded look instead of a flat translucent wash.
                    Color top = shiftBrightness(fill, 30);
                    Color bottom = shiftBrightness(fill, -26);
                    g2.setPaint(new GradientPaint(0f, 0f, top, 0f, h, bottom));
                    g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
                    // Subtle top sheen for a glassy highlight.
                    if (w - arc > 2)
                    {
                        g2.setColor(new Color(255, 255, 255, Math.min(55, fill.getAlpha())));
                        g2.setStroke(new BasicStroke(1f));
                        g2.drawLine(arc / 2, 1, w - arc / 2, 1);
                    }
                }
                Color outline = colorClientProperty(button, PROXY_PILL_OUTLINE_PROPERTY);
                if (outline != null)
                {
                    g2.setColor(outline);
                    g2.setStroke(new BasicStroke(1.3f));
                    g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
                }
                g2.dispose();
            }
            super.paint(graphics, component);
            if (component instanceof AbstractButton)
            {
                paintProxyChevronIfNeeded(graphics, (AbstractButton) component);
            }
        }

        private static Color proxyPillFill(AbstractButton button)
        {
            ButtonModel model = button.getModel();
            if (model != null && button.isEnabled() && model.isPressed() && model.isArmed())
            {
                Color pressed = colorClientProperty(button, PROXY_PILL_PRESSED_FILL_PROPERTY);
                if (pressed != null)
                {
                    return pressed;
                }
            }
            if (model != null && button.isEnabled() && model.isRollover())
            {
                Color hover = colorClientProperty(button, PROXY_PILL_HOVER_FILL_PROPERTY);
                if (hover != null)
                {
                    return hover;
                }
            }
            return colorClientProperty(button, PROXY_PILL_FILL_PROPERTY);
        }

        private static Color colorClientProperty(AbstractButton button, String key)
        {
            Object value = button.getClientProperty(key);
            return value instanceof Color ? (Color) value : null;
        }

        private static Color shiftBrightness(Color color, int delta)
        {
            int r = Math.max(0, Math.min(255, color.getRed() + delta));
            int g = Math.max(0, Math.min(255, color.getGreen() + delta));
            int b = Math.max(0, Math.min(255, color.getBlue() + delta));
            return new Color(r, g, b, color.getAlpha());
        }

        @Override
        protected void paintText(Graphics graphics, AbstractButton button, Rectangle textRect, String text)
        {
            FontMetrics metrics = graphics.getFontMetrics();
            int mnemonicIndex = button.getDisplayedMnemonicIndex();
            Color foreground = button.getForeground();
            graphics.setColor(foreground == null ? Color.WHITE : foreground);
            BasicGraphicsUtils.drawStringUnderlineCharAt(graphics, text, mnemonicIndex, textRect.x, textRect.y + metrics.getAscent());
        }

        private static void paintProxyChevronIfNeeded(Graphics graphics, AbstractButton button)
        {
            String text = button.getText();
            if ((text != null && !text.trim().isEmpty()) || button.getIcon() != null)
            {
                return;
            }
            int width = button.getWidth();
            int height = button.getHeight();
            if (width <= 0 || height <= 0)
            {
                return;
            }
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Color color = button.getForeground();
            g2.setColor(color == null ? Color.WHITE : color);
            int centerX = width / 2;
            int centerY = height / 2;
            g2.drawLine(centerX - 4, centerY - 2, centerX, centerY + 2);
            g2.drawLine(centerX, centerY + 2, centerX + 4, centerY - 2);
            g2.dispose();
        }
    }

    private static final class ComponentState
    {
        private final Color background;
        private final Color foreground;
        private final boolean backgroundUiResource;
        private final boolean foregroundUiResource;
        private final boolean jComponent;
        private final boolean opaque;
        private final Border border;
        private final boolean borderUiResource;
        private final boolean abstractButton;
        private final boolean buttonContentAreaFilled;
        private final boolean buttonBorderPainted;
        private final boolean buttonFocusPainted;
        private final boolean buttonRolloverEnabled;
        private final Insets buttonMargin;
        private final Object buttonType;
        private final ButtonUI buttonUi;
        private final Object flatLafStyle;
        private final Color textCaret;
        private final Color textSelection;
        private final Color textSelectedText;
        private final Color textDisabledText;
        private final boolean tableShowHorizontalLines;
        private final boolean tableShowVerticalLines;
        private final Dimension tableIntercellSpacing;
        private final Color tableGridColor;
        private final Color tableSelectionBackground;
        private final Color tableSelectionForeground;
        private final Map<Class<?>, TableCellRenderer> tableDefaultRenderers;
        private final Color listSelectionBackground;
        private final Color listSelectionForeground;
        private final Object treeLineStyle;
        private final Color treeRendererBackgroundNonSelection;
        private final Color treeRendererBackgroundSelection;
        private final Color treeRendererTextNonSelection;
        private final Color treeRendererTextSelection;
        private final Color treeRendererBorderSelection;
        private final TreeCellRenderer treeCellRenderer;
        private final Color[] tabBackgrounds;
        private final Color[] tabForegrounds;
        private final TabbedPaneUI tabbedPaneUi;
        private final boolean capturedWhileSuiteTinted;

        private ComponentState(Component component, boolean capturedWhileSuiteTinted)
        {
            this.capturedWhileSuiteTinted = capturedWhileSuiteTinted;
            background = component.getBackground();
            foreground = component.getForeground();
            backgroundUiResource = background instanceof javax.swing.plaf.UIResource;
            foregroundUiResource = foreground instanceof javax.swing.plaf.UIResource;
            jComponent = component instanceof JComponent;
            opaque = jComponent && ((JComponent) component).isOpaque();
            border = jComponent ? ((JComponent) component).getBorder() : null;
            borderUiResource = border instanceof javax.swing.plaf.UIResource;
            if (component instanceof AbstractButton)
            {
                AbstractButton button = (AbstractButton) component;
                abstractButton = true;
                buttonContentAreaFilled = button.isContentAreaFilled();
                buttonBorderPainted = button.isBorderPainted();
                buttonFocusPainted = button.isFocusPainted();
                buttonRolloverEnabled = button.isRolloverEnabled();
                buttonMargin = button.getMargin();
                buttonType = button.getClientProperty("JButton.buttonType");
                buttonUi = button.getUI();
            }
            else
            {
                abstractButton = false;
                buttonContentAreaFilled = false;
                buttonBorderPainted = false;
                buttonFocusPainted = false;
                buttonRolloverEnabled = false;
                buttonMargin = null;
                buttonType = null;
                buttonUi = null;
            }
            flatLafStyle = jComponent ? ((JComponent) component).getClientProperty("FlatLaf.style") : null;

            if (component instanceof JTextComponent)
            {
                JTextComponent textComponent = (JTextComponent) component;
                textCaret = textComponent.getCaretColor();
                textSelection = textComponent.getSelectionColor();
                textSelectedText = textComponent.getSelectedTextColor();
                textDisabledText = textComponent.getDisabledTextColor();
            }
            else
            {
                textCaret = null;
                textSelection = null;
                textSelectedText = null;
                textDisabledText = null;
            }

            if (component instanceof JTable)
            {
                JTable table = (JTable) component;
                tableShowHorizontalLines = table.getShowHorizontalLines();
                tableShowVerticalLines = table.getShowVerticalLines();
                tableIntercellSpacing = new Dimension(table.getIntercellSpacing());
                tableGridColor = table.getGridColor();
                tableSelectionBackground = table.getSelectionBackground();
                tableSelectionForeground = table.getSelectionForeground();
                tableDefaultRenderers = new LinkedHashMap<>();
                for (Class<?> rendererType : TABLE_RENDERER_TYPES)
                {
                    tableDefaultRenderers.put(rendererType, table.getDefaultRenderer(rendererType));
                }
            }
            else
            {
                tableShowHorizontalLines = false;
                tableShowVerticalLines = false;
                tableIntercellSpacing = null;
                tableGridColor = null;
                tableSelectionBackground = null;
                tableSelectionForeground = null;
                tableDefaultRenderers = null;
            }

            if (component instanceof JList)
            {
                JList<?> list = (JList<?>) component;
                listSelectionBackground = list.getSelectionBackground();
                listSelectionForeground = list.getSelectionForeground();
            }
            else
            {
                listSelectionBackground = null;
                listSelectionForeground = null;
            }

            if (component instanceof JTree)
            {
                JTree tree = (JTree) component;
                treeLineStyle = tree.getClientProperty("JTree.lineStyle");
                treeCellRenderer = tree.getCellRenderer();
                if (tree.getCellRenderer() instanceof DefaultTreeCellRenderer)
                {
                    DefaultTreeCellRenderer renderer = (DefaultTreeCellRenderer) tree.getCellRenderer();
                    treeRendererBackgroundNonSelection = renderer.getBackgroundNonSelectionColor();
                    treeRendererBackgroundSelection = renderer.getBackgroundSelectionColor();
                    treeRendererTextNonSelection = renderer.getTextNonSelectionColor();
                    treeRendererTextSelection = renderer.getTextSelectionColor();
                    treeRendererBorderSelection = renderer.getBorderSelectionColor();
                }
                else
                {
                    treeRendererBackgroundNonSelection = null;
                    treeRendererBackgroundSelection = null;
                    treeRendererTextNonSelection = null;
                    treeRendererTextSelection = null;
                    treeRendererBorderSelection = null;
                }
            }
            else
            {
                treeLineStyle = null;
                treeCellRenderer = null;
                treeRendererBackgroundNonSelection = null;
                treeRendererBackgroundSelection = null;
                treeRendererTextNonSelection = null;
                treeRendererTextSelection = null;
                treeRendererBorderSelection = null;
            }

            if (component instanceof JTabbedPane)
            {
                JTabbedPane tabbedPane = (JTabbedPane) component;
                tabbedPaneUi = tabbedPane.getUI();
                tabBackgrounds = new Color[tabbedPane.getTabCount()];
                tabForegrounds = new Color[tabbedPane.getTabCount()];
                for (int index = 0; index < tabbedPane.getTabCount(); index++)
                {
                    tabBackgrounds[index] = tabbedPane.getBackgroundAt(index);
                    tabForegrounds[index] = tabbedPane.getForegroundAt(index);
                }
            }
            else
            {
                tabbedPaneUi = null;
                tabBackgrounds = null;
                tabForegrounds = null;
            }
        }

        private void restore(Component component)
        {
            if (capturedWhileSuiteTinted)
            {
                restoreComponentCreatedDuringTheme(component);
                return;
            }
            component.setBackground(background);
            component.setForeground(foreground);
            if (jComponent && component instanceof JComponent)
            {
                JComponent jComponent = (JComponent) component;
                jComponent.setOpaque(opaque);
                jComponent.setBorder(border);
                jComponent.putClientProperty("FlatLaf.style", flatLafStyle);
            }
            if (abstractButton && component instanceof AbstractButton)
            {
                AbstractButton button = (AbstractButton) component;
                button.setContentAreaFilled(buttonContentAreaFilled);
                button.setBorderPainted(buttonBorderPainted);
                button.setFocusPainted(buttonFocusPainted);
                button.setRolloverEnabled(buttonRolloverEnabled);
                if (buttonMargin != null)
                {
                    button.setMargin(buttonMargin);
                }
                if (buttonUi != null)
                {
                    button.setUI(buttonUi);
                }
                button.putClientProperty("JButton.buttonType", buttonType);
                button.putClientProperty("FlatLaf.style", flatLafStyle);
                clearProxyButtonProperties(button);
            }
            if (component instanceof JTextComponent)
            {
                JTextComponent textComponent = (JTextComponent) component;
                if (textCaret != null)
                {
                    textComponent.setCaretColor(textCaret);
                }
                if (textSelection != null)
                {
                    textComponent.setSelectionColor(textSelection);
                }
                if (textSelectedText != null)
                {
                    textComponent.setSelectedTextColor(textSelectedText);
                }
                if (textDisabledText != null)
                {
                    textComponent.setDisabledTextColor(textDisabledText);
                }
            }
            if (component instanceof JTable)
            {
                JTable table = (JTable) component;
                table.setShowHorizontalLines(tableShowHorizontalLines);
                table.setShowVerticalLines(tableShowVerticalLines);
                if (tableIntercellSpacing != null)
                {
                    table.setIntercellSpacing(tableIntercellSpacing);
                }
                if (tableGridColor != null)
                {
                    table.setGridColor(tableGridColor);
                }
                if (tableSelectionBackground != null)
                {
                    table.setSelectionBackground(tableSelectionBackground);
                }
                if (tableSelectionForeground != null)
                {
                    table.setSelectionForeground(tableSelectionForeground);
                }
                if (tableDefaultRenderers != null)
                {
                    for (Map.Entry<Class<?>, TableCellRenderer> rendererEntry : tableDefaultRenderers.entrySet())
                    {
                        table.setDefaultRenderer(rendererEntry.getKey(), rendererEntry.getValue());
                    }
                }
            }
            if (component instanceof JList)
            {
                JList<?> list = (JList<?>) component;
                if (listSelectionBackground != null)
                {
                    list.setSelectionBackground(listSelectionBackground);
                }
                if (listSelectionForeground != null)
                {
                    list.setSelectionForeground(listSelectionForeground);
                }
            }
            if (component instanceof JTree)
            {
                JTree tree = (JTree) component;
                tree.putClientProperty("JTree.lineStyle", treeLineStyle);
                if (treeCellRenderer != null)
                {
                    tree.setCellRenderer(treeCellRenderer);
                }
                if (tree.getCellRenderer() instanceof DefaultTreeCellRenderer && treeRendererTextNonSelection != null)
                {
                    DefaultTreeCellRenderer renderer = (DefaultTreeCellRenderer) tree.getCellRenderer();
                    renderer.setBackgroundNonSelectionColor(treeRendererBackgroundNonSelection);
                    renderer.setBackgroundSelectionColor(treeRendererBackgroundSelection);
                    renderer.setTextNonSelectionColor(treeRendererTextNonSelection);
                    renderer.setTextSelectionColor(treeRendererTextSelection);
                    renderer.setBorderSelectionColor(treeRendererBorderSelection);
                }
            }
            if (component instanceof JTabbedPane && tabBackgrounds != null && tabForegrounds != null)
            {
                JTabbedPane tabbedPane = (JTabbedPane) component;
                if (tabbedPaneUi != null)
                {
                    tabbedPane.setUI(tabbedPaneUi);
                }
                int tabCount = tabbedPane.getTabCount();
                int savedCount = tabBackgrounds.length;
                for (int index = 0; index < tabCount; index++)
                {
                    if (index < savedCount)
                    {
                        tabbedPane.setBackgroundAt(index, tabBackgrounds[index]);
                        tabbedPane.setForegroundAt(index, tabForegrounds[index]);
                    }
                    else
                    {
                        tabbedPane.setBackgroundAt(index, background);
                        tabbedPane.setForegroundAt(index, foreground);
                    }
                }
            }
        }

        private void restoreComponentCreatedDuringTheme(Component component)
        {
            component.setBackground(backgroundUiResource ? null : background);
            component.setForeground(foregroundUiResource ? null : foreground);
            if (jComponent && component instanceof JComponent)
            {
                JComponent jComponent = (JComponent) component;
                jComponent.setOpaque(opaque);
                jComponent.setBorder(borderUiResource ? null : border);
                jComponent.putClientProperty("FlatLaf.style", flatLafStyle);
            }
            if (abstractButton && component instanceof AbstractButton)
            {
                AbstractButton button = (AbstractButton) component;
                button.setContentAreaFilled(buttonContentAreaFilled);
                button.setBorderPainted(buttonBorderPainted);
                button.setFocusPainted(buttonFocusPainted);
                button.setRolloverEnabled(buttonRolloverEnabled);
                if (buttonMargin != null)
                {
                    button.setMargin(buttonMargin);
                }
                if (buttonUi != null)
                {
                    button.setUI(buttonUi);
                }
                button.putClientProperty("JButton.buttonType", buttonType);
                button.putClientProperty("FlatLaf.style", flatLafStyle);
                clearProxyButtonProperties(button);
            }
            if (component instanceof JTextComponent)
            {
                JTextComponent textComponent = (JTextComponent) component;
                if (textCaret != null)
                {
                    textComponent.setCaretColor(textCaret);
                }
                if (textSelection != null)
                {
                    textComponent.setSelectionColor(textSelection);
                }
                if (textSelectedText != null)
                {
                    textComponent.setSelectedTextColor(textSelectedText);
                }
                if (textDisabledText != null)
                {
                    textComponent.setDisabledTextColor(textDisabledText);
                }
            }
            if (component instanceof JTable)
            {
                JTable table = (JTable) component;
                table.setShowHorizontalLines(tableShowHorizontalLines);
                table.setShowVerticalLines(tableShowVerticalLines);
                if (tableIntercellSpacing != null)
                {
                    table.setIntercellSpacing(tableIntercellSpacing);
                }
                if (tableGridColor != null)
                {
                    table.setGridColor(tableGridColor);
                }
                if (tableSelectionBackground != null)
                {
                    table.setSelectionBackground(tableSelectionBackground);
                }
                if (tableSelectionForeground != null)
                {
                    table.setSelectionForeground(tableSelectionForeground);
                }
                if (tableDefaultRenderers != null)
                {
                    for (Map.Entry<Class<?>, TableCellRenderer> rendererEntry : tableDefaultRenderers.entrySet())
                    {
                        table.setDefaultRenderer(rendererEntry.getKey(), rendererEntry.getValue());
                    }
                }
            }
            if (component instanceof JList)
            {
                JList<?> list = (JList<?>) component;
                if (listSelectionBackground != null)
                {
                    list.setSelectionBackground(listSelectionBackground);
                }
                if (listSelectionForeground != null)
                {
                    list.setSelectionForeground(listSelectionForeground);
                }
            }
            if (component instanceof JTree)
            {
                JTree tree = (JTree) component;
                tree.putClientProperty("JTree.lineStyle", treeLineStyle);
                if (treeCellRenderer != null)
                {
                    tree.setCellRenderer(treeCellRenderer);
                }
                if (tree.getCellRenderer() instanceof DefaultTreeCellRenderer && treeRendererTextNonSelection != null)
                {
                    DefaultTreeCellRenderer renderer = (DefaultTreeCellRenderer) tree.getCellRenderer();
                    renderer.setBackgroundNonSelectionColor(treeRendererBackgroundNonSelection);
                    renderer.setBackgroundSelectionColor(treeRendererBackgroundSelection);
                    renderer.setTextNonSelectionColor(treeRendererTextNonSelection);
                    renderer.setTextSelectionColor(treeRendererTextSelection);
                    renderer.setBorderSelectionColor(treeRendererBorderSelection);
                }
            }
            if (component instanceof JTabbedPane && tabBackgrounds != null && tabForegrounds != null)
            {
                JTabbedPane tabbedPane = (JTabbedPane) component;
                if (tabbedPaneUi != null)
                {
                    tabbedPane.setUI(tabbedPaneUi);
                }
                int tabCount = tabbedPane.getTabCount();
                int savedCount = tabBackgrounds.length;
                for (int index = 0; index < tabCount; index++)
                {
                    if (index < savedCount)
                    {
                        tabbedPane.setBackgroundAt(index, tabBackgrounds[index]);
                        tabbedPane.setForegroundAt(index, tabForegrounds[index]);
                    }
                    else
                    {
                        tabbedPane.setBackgroundAt(index, background);
                        tabbedPane.setForegroundAt(index, foreground);
                    }
                }
            }
            component.invalidate();
            component.repaint();
        }
    }

    private final class BurpThemeTableRenderer implements TableCellRenderer
    {
        private final TableCellRenderer delegate;

        private BurpThemeTableRenderer(TableCellRenderer delegate)
        {
            this.delegate = delegate;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus, int row, int column)
        {
            Component component = delegate.getTableCellRendererComponent(table, value, selected, focus, row, column);
            tintSelectionRenderer(component, selected, currentTheme, table.getSelectionBackground(), table.getBackground(), Collections.newSetFromMap(new IdentityHashMap<>()), 0, true);
            return component;
        }
    }

    private final class BurpThemeTreeRenderer implements TreeCellRenderer
    {
        private final TreeCellRenderer delegate;

        private BurpThemeTreeRenderer(TreeCellRenderer delegate)
        {
            this.delegate = delegate;
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean focus)
        {
            Component component = delegate.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, focus);
            Color selectedBackground = selected ? opaque(currentTheme.button) : tree.getBackground();
            tintSelectionRenderer(component, selected, currentTheme, selectedBackground, tree.getBackground(), Collections.newSetFromMap(new IdentityHashMap<>()), 0, true);
            return component;
        }
    }

    private void tintSelectionRenderer(Component component, boolean selected, BurpTheme theme, Color selectedBackground, Color normalBackground, Set<Component> visited, int depth, boolean root)
    {
        if (component == null || depth > 12 || visited.contains(component))
        {
            return;
        }
        visited.add(component);
        if (selected)
        {
            component.setBackground(selectedBackground);
            component.setForeground(theme.buttonText);
            if (component instanceof JComponent)
            {
                ((JComponent) component).setOpaque(root);
            }
        }
        else
        {
            component.setForeground(theme.text);
            component.setBackground(normalBackground);
            if (component instanceof JComponent)
            {
                ((JComponent) component).setOpaque(false);
            }
        }
        if (component instanceof Container)
        {
            for (Component child : ((Container) component).getComponents())
            {
                tintSelectionRenderer(child, selected, theme, selectedBackground, normalBackground, visited, depth + 1, false);
            }
        }
    }
    private static final class BurpThemeTabbedPaneUI extends BasicTabbedPaneUI
    {
        private final BurpTheme theme;

        private BurpThemeTabbedPaneUI(BurpTheme theme)
        {
            this.theme = theme;
        }

        @Override
        protected void paintTabBackground(Graphics graphics, int tabPlacement, int tabIndex, int x, int y, int width, int height, boolean isSelected)
        {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (isSelected)
            {
                g2.setColor(alpha(theme.button, 92));
                g2.fillRoundRect(x + 1, y + 2, Math.max(1, width - 2), Math.max(1, height - 3), 8, 8);
            }
            else
            {
                g2.setColor(alpha(theme.accent, 18));
                g2.drawRoundRect(x + 1, y + 2, Math.max(1, width - 3), Math.max(1, height - 4), 8, 8);
            }
            g2.dispose();
        }

        @Override
        protected void paintTabBorder(Graphics graphics, int tabPlacement, int tabIndex, int x, int y, int width, int height, boolean isSelected)
        {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(isSelected ? alpha(theme.accent, 118) : alpha(theme.accent, 38));
            g2.drawRoundRect(x + 1, y + 2, Math.max(1, width - 3), Math.max(1, height - 4), 8, 8);
            g2.dispose();
        }

        @Override
        protected void paintContentBorder(Graphics graphics, int tabPlacement, int selectedIndex)
        {
            Graphics2D g2 = (Graphics2D) graphics.create();
            Insets insets = tabPane == null ? new Insets(0, 0, 0, 0) : tabPane.getInsets();
            int x = insets.left;
            int y = insets.top;
            int width = tabPane == null ? 0 : tabPane.getWidth() - insets.left - insets.right;
            int height = tabPane == null ? 0 : tabPane.getHeight() - insets.top - insets.bottom;
            switch (tabPlacement)
            {
                case JTabbedPane.LEFT:
                    int leftTabsWidth = calculateTabAreaWidth(tabPlacement, runCount, maxTabWidth);
                    x += leftTabsWidth;
                    width -= leftTabsWidth;
                    break;
                case JTabbedPane.RIGHT:
                    width -= calculateTabAreaWidth(tabPlacement, runCount, maxTabWidth);
                    break;
                case JTabbedPane.BOTTOM:
                    height -= calculateTabAreaHeight(tabPlacement, runCount, maxTabHeight);
                    break;
                case JTabbedPane.TOP:
                default:
                    int topTabsHeight = calculateTabAreaHeight(tabPlacement, runCount, maxTabHeight);
                    y += topTabsHeight;
                    height -= topTabsHeight;
                    break;
            }
            if (width > 0 && height > 0)
            {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(alpha(theme.accent, 38));
                if (tabPlacement == JTabbedPane.LEFT)
                {
                    g2.drawLine(x, y, x, y + height - 1);
                }
                else if (tabPlacement == JTabbedPane.RIGHT)
                {
                    g2.drawLine(x + width - 1, y, x + width - 1, y + height - 1);
                }
                else if (tabPlacement == JTabbedPane.BOTTOM)
                {
                    g2.drawLine(x, y + height - 1, x + width - 1, y + height - 1);
                }
                else
                {
                    g2.drawLine(x, y, x + width - 1, y);
                }
                g2.setColor(alpha(theme.gold, 12));
                g2.drawRoundRect(x, y, width - 1, height - 1, 10, 10);
            }
            g2.dispose();
        }

        @Override
        protected void paintFocusIndicator(Graphics graphics, int tabPlacement, Rectangle[] rects, int tabIndex, Rectangle iconRect, Rectangle textRect, boolean isSelected)
        {
            if (!isSelected)
            {
                return;
            }
            Graphics2D g2 = (Graphics2D) graphics.create();
            Rectangle rect = rects[tabIndex];
            g2.setColor(alpha(theme.gold, 66));
            g2.drawRoundRect(rect.x + 3, rect.y + 4, Math.max(1, rect.width - 7), Math.max(1, rect.height - 8), 7, 7);
            g2.dispose();
        }

        private Color alpha(Color color, int alpha)
        {
            return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
        }
    }

    private static final class BurpBackgroundPanel extends JPanel
    {
        private static final long serialVersionUID = 1L;
        private static final Map<String, BufferedImage> BACKGROUND_IMAGES = new LinkedHashMap<>();
        private BurpTheme theme;

        private BurpBackgroundPanel(BurpTheme theme)
        {
            this.theme = theme;
            setOpaque(true);
        }

        private static void preloadAll()
        {
            for (BurpTheme theme : BurpTheme.values())
            {
                new BurpBackgroundPanel(theme).backgroundImage();
            }
        }

        private static void clearImageCache()
        {
            synchronized (BACKGROUND_IMAGES)
            {
                for (BufferedImage image : BACKGROUND_IMAGES.values())
                {
                    image.flush();
                }
                BACKGROUND_IMAGES.clear();
            }
        }

        @Override
        protected void paintComponent(Graphics graphics)
        {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(theme.base);
            g2.fillRect(0, 0, getWidth(), getHeight());
            BufferedImage image = backgroundImage();
            if (image != null)
            {
                double scale = Math.max(getWidth() / (double) image.getWidth(), getHeight() / (double) image.getHeight());
                int width = (int) Math.ceil(image.getWidth() * scale);
                int height = (int) Math.ceil(image.getHeight() * scale);
                int x = (getWidth() - width) / 2;
                int y = (getHeight() - height) / 2;
                g2.setComposite(AlphaComposite.SrcOver.derive(theme.imageAlpha / 255.0f));
                g2.drawImage(image, x, y, width, height, null);
                g2.setComposite(AlphaComposite.SrcOver);
            }
            else
            {
                g2.setPaint(new GradientPaint(0, 0, theme.base, getWidth(), getHeight(), theme.horizon));
                g2.fillRect(0, 0, getWidth(), getHeight());
            }

            int washAlpha = Math.min(theme.washAlpha, 18);
            g2.setPaint(new GradientPaint(0, 0, alpha(theme.base, washAlpha), getWidth(), getHeight(), alpha(theme.horizon, Math.max(8, washAlpha - 8))));
            g2.fillRect(0, 0, getWidth(), getHeight());
            paintThemeAtmosphere(g2);
            g2.dispose();
        }

        private void paintThemeAtmosphere(Graphics2D g2)
        {
            if (theme == BurpTheme.GREAT_PLATEAU)
            {
                g2.setStroke(new BasicStroke(2.0f));
                g2.setColor(alpha(theme.accent, 30));
                for (int y = 120; y < getHeight(); y += 170)
                {
                    g2.drawArc(-160, y, getWidth() + 320, 210, 190, 155);
                }
                g2.setColor(alpha(theme.gold, 42));
                g2.fillOval(getWidth() - 260, 54, 150, 150);
                return;
            }
            if (theme == BurpTheme.ANCIENT_SLATE)
            {
                g2.setStroke(new BasicStroke(2.0f));
                g2.setColor(alpha(theme.accent, 30));
                for (int y = 92; y < getHeight(); y += 150)
                {
                    g2.drawArc(-120, y, getWidth() + 240, 190, 196, 148);
                }
                g2.setColor(alpha(theme.gold, 26));
                g2.fillOval(getWidth() - 245, 58, 128, 128);
                return;
            }
            if (theme == BurpTheme.KOROK_FOREST)
            {
                g2.setColor(alpha(theme.accent, 36));
                int size = 150;
                for (int x = -80; x < getWidth(); x += 210)
                {
                    g2.fillOval(x, -55, size, size);
                    g2.fillOval(x + 80, 25, size - 28, size - 28);
                }
                g2.setColor(alpha(theme.gold, 28));
                g2.fillOval(getWidth() - 220, 70, 118, 118);
                return;
            }
            if (theme == BurpTheme.BLOOD_MOON)
            {
                g2.setColor(alpha(theme.accent, 78));
                g2.fillOval(getWidth() - 285, 38, 178, 178);
                g2.setColor(alpha(theme.gold, 30));
                g2.fillOval(getWidth() - 236, 80, 82, 82);
                g2.setColor(alpha(theme.accent, 22));
                g2.fillRect(0, 0, getWidth(), getHeight());
                return;
            }
            g2.setColor(alpha(theme.gold, 34));
            g2.fillOval(getWidth() - 250, 54, 150, 150);
        }

        private Color alpha(Color color, int alpha)
        {
            return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
        }

        private BufferedImage backgroundImage()
        {
            synchronized (BACKGROUND_IMAGES)
            {
                BufferedImage existing = BACKGROUND_IMAGES.get(theme.backgroundResource);
                if (existing != null)
                {
                    return existing;
                }
                try
                {
                    BufferedImage loaded = readPackagedImage();
                    if (loaded == null && Boolean.getBoolean("burptheme.devAssets"))
                    {
                        loaded = readRelativeFileFallbackImage();
                    }
                    if (loaded != null)
                    {
                        BACKGROUND_IMAGES.put(theme.backgroundResource, loaded);
                    }
                    return loaded;
                }
                catch (IOException | IllegalArgumentException exception)
                {
                    return null;
                }
            }
        }

        private BufferedImage readPackagedImage() throws IOException
        {
            java.net.URL resource = BurpThemeEngine.class.getResource(theme.backgroundResource);
            if (resource == null && theme.backgroundResource.startsWith("/"))
            {
                resource = BurpThemeEngine.class.getClassLoader().getResource(theme.backgroundResource.substring(1));
            }
            return resource == null ? null : ImageIO.read(resource);
        }

        private BufferedImage readRelativeFileFallbackImage() throws IOException
        {
            String name = theme.backgroundResource.substring(theme.backgroundResource.lastIndexOf('/') + 1);
            File candidate = new File(devAssetsDir(), name);
            return candidate.isFile() ? ImageIO.read(candidate) : null;
        }
    }

    private static File devAssetsDir()
    {
        String configured = System.getProperty("burptheme.devAssetsDir");
        return configured == null || configured.trim().isEmpty() ? new File("assets") : new File(configured);
    }
}
