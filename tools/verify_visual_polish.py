#!/usr/bin/env python3
from __future__ import annotations

import os
import shutil
import subprocess
import tempfile
import textwrap
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src"
BURP_JAR = Path(os.environ.get("BURP_JAR", "/usr/share/burpsuite/burpsuite.jar"))


HARNESS = r"""
package burp.arcade;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.JViewport;
import javax.swing.BoxLayout;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.table.TableCellRenderer;

public final class VisualPolishHarness {
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static boolean hasBorderNamed(JComponent component, String name) {
        return component.getBorder() != null
            && component.getBorder().getClass().getSimpleName().contains(name);
    }

    private static boolean sameRgb(Color left, Color right) {
        return left.getRed() == right.getRed()
            && left.getGreen() == right.getGreen()
            && left.getBlue() == right.getBlue();
    }

    private static String rgb(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    private static String flatLafStyle(JComponent component) {
        Object style = component.getClientProperty("FlatLaf.style");
        return style == null ? "" : style.toString().toLowerCase(Locale.ROOT);
    }

    private static boolean hasThemedProxyPalette(JComponent component, BurpTheme theme) {
        String style = flatLafStyle(component);
        return style.contains("arc: 999")
            && style.contains("background: " + rgb(theme.button.darker()))
            && style.contains("foreground: " + rgb(theme.buttonText))
            && style.contains("disabledforeground: " + rgb(theme.muted))
            && style.contains("bordercolor:");
    }

    private static boolean hasCustomThemedUi(AbstractButton button) {
        String uiName = button.getUI() == null
            ? ""
            : button.getUI().getClass().getName().toLowerCase(Locale.ROOT);
        return uiName.contains("burp")
            || uiName.contains("arcade")
            || uiName.contains("flat")
            || uiName.contains("theme");
    }

    private static boolean hasProxyPillPainterMarker(AbstractButton button) {
        return Boolean.TRUE.equals(button.getClientProperty("burptheme.proxyPillButton"))
            && button.getClientProperty("burptheme.proxyPillFill") instanceof Color
            && button.getClientProperty("burptheme.proxyPillOutline") instanceof Color;
    }

    private static boolean hasPillPaintState(AbstractButton button) {
        return button.getClientProperty("burptheme.proxyPillFill") instanceof Color
            && button.getClientProperty("burptheme.proxyPillHoverFill") instanceof Color
            && button.getClientProperty("burptheme.proxyPillPressedFill") instanceof Color
            && button.getClientProperty("burptheme.proxyPillOutline") instanceof Color;
    }

    private static boolean hasCustomPainterMarker(AbstractButton button, BurpTheme theme) {
        Object buttonType = button.getClientProperty("JButton.buttonType");
        String normalizedType = buttonType == null ? "" : buttonType.toString().toLowerCase(Locale.ROOT);
        return hasProxyPillPainterMarker(button)
            || hasBorderNamed(button, "RoundLineBorder")
            || normalizedType.contains("round")
            || hasThemedProxyPalette(button, theme);
    }

    private static Integer integerClientProperty(JComponent component, String name) {
        Object value = component.getClientProperty(name);
        return value instanceof Integer ? (Integer) value : null;
    }

    private static int intField(Object target, String name) {
        if (target == null) {
            return -1;
        }
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(target);
            return value instanceof Integer ? ((Integer) value).intValue() : -1;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return -1;
        }
    }

    private static Color colorField(Object target, String name) {
        if (target == null) {
            return null;
        }
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(target);
            return value instanceof Color ? (Color) value : null;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }

    private static int styleArc(JComponent component) {
        for (String declaration : flatLafStyle(component).split(";")) {
            String[] pieces = declaration.trim().split(":", 2);
            if (pieces.length == 2 && "arc".equals(pieces[0].trim())) {
                try {
                    return Integer.parseInt(pieces[1].trim());
                } catch (NumberFormatException ex) {
                    return -1;
                }
            }
        }
        return -1;
    }

    private static void installDefaults(BurpThemeEngine engine, BurpTheme theme) {
        try {
            Method method = BurpThemeEngine.class.getDeclaredMethod("installColorDefaults", BurpTheme.class);
            method.setAccessible(true);
            method.invoke(engine, theme);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Unable to install themed UI defaults", ex);
        }
    }

    private static void checkLowAlphaDefault(String key, int maximumAlpha) {
        Object value = UIManager.getDefaults().get(key);
        check(value instanceof Color, key + " must be a themed color default");
        check(((Color) value).getAlpha() <= maximumAlpha,
            key + " must stay translucent instead of restoring an opaque dark surface");
    }

    private static boolean hasRoundedProxyShape(AbstractButton button) {
        Border border = button.getBorder();
        int borderArc = intField(border, "arc");
        int arc = Math.max(borderArc, styleArc(button));
        return arc >= 24
            && (border == null || !border.isBorderOpaque())
            && (hasBorderNamed(button, "RoundLineBorder") || hasCustomThemedUi(button));
    }

    private static double linearChannel(int channel) {
        double value = channel / 255.0d;
        return value <= 0.03928d
            ? value / 12.92d
            : Math.pow((value + 0.055d) / 1.055d, 2.4d);
    }

    private static double luminance(Color color) {
        return 0.2126d * linearChannel(color.getRed())
            + 0.7152d * linearChannel(color.getGreen())
            + 0.0722d * linearChannel(color.getBlue());
    }

    private static double contrastRatio(Color foreground, Color background) {
        double first = luminance(foreground);
        double second = luminance(background);
        double lighter = Math.max(first, second);
        double darker = Math.min(first, second);
        return (lighter + 0.05d) / (darker + 0.05d);
    }

    private static void checkProxyButton(AbstractButton button, BurpTheme theme, String label) {
        check(hasCustomThemedUi(button) || hasCustomPainterMarker(button, theme),
            label + " must expose a themed UI/style marker or custom painter");
        check(!button.isOpaque(), label + " must not paint an opaque native rectangle");
        check(!button.isContentAreaFilled(), label + " must not paint a square native content fill");
        check(button.isBorderPainted(), label + " must paint its custom rounded border");
        check(hasRoundedProxyShape(button), label + " must have a strongly rounded split-button shape");
    }

    private static void checkRegularButton(AbstractButton button, String label) {
        check(hasCustomThemedUi(button) && hasPillPaintState(button),
            label + " must use the translucent custom pill painter");
        check(!button.isOpaque(), label + " must not paint an opaque native rectangle");
        check(!button.isContentAreaFilled(), label + " must not paint a square native content fill");
        check(button.isBorderPainted(), label + " must paint its rounded outline");
        check(hasRoundedProxyShape(button), label + " must keep a rounded button silhouette");
        Color fill = (Color) button.getClientProperty("burptheme.proxyPillFill");
        check(fill.getAlpha() <= 120, label + " fill must stay translucent");
    }

    private static void checkReadableForeground(AbstractButton button, double minimumContrast, String label) {
        check(button.getForeground() != null && button.getForeground().getAlpha() >= 180,
            label + " foreground must remain visible");
        check(button.getBackground() != null,
            label + " background must be available for contrast checks");
        check(contrastRatio(button.getForeground(), button.getBackground()) >= minimumContrast,
            label + " foreground must remain readable over the themed background");
    }

    public static void main(String[] args) {
        BurpTheme theme = BurpTheme.GREAT_PLATEAU;
        BurpThemeEngine engine = new BurpThemeEngine(null, new AtomicBoolean(false));
        installDefaults(engine, theme);

        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));

        JLabel heading = new JLabel("Request details");
        heading.setFont(heading.getFont().deriveFont(18.0f));

        JTextArea requestEditor = new JTextArea("GET / HTTP/1.1\nHost: example.test\n");
        requestEditor.setName("request message editor");
        JScrollPane editorScroll = new JScrollPane(requestEditor);
        editorScroll.setName("request message viewer");

        JTable table = new JTable(new Object[][] {{"GET", "/"}}, new Object[] {"Method", "Path"});
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setName("logger viewer");

        JList<String> list = new JList<>(new String[] {"Request", "Response"});
        list.setName("message list");

        JPanel proxyCluster = new JPanel();
        proxyCluster.setName("proxy intercept action controls");
        proxyCluster.setPreferredSize(new Dimension(300, 56));
        proxyCluster.setSize(new Dimension(300, 56));

        JPanel proxyPage = new JPanel();
        proxyPage.setName("proxy intercept screen");
        proxyPage.setPreferredSize(new Dimension(1200, 620));
        proxyPage.setSize(new Dimension(1200, 620));

        JPanel forwardSplit = new JPanel();
        forwardSplit.setPreferredSize(new Dimension(130, 36));
        forwardSplit.setSize(new Dimension(130, 36));
        JButton forward = new JButton("Forward");
        JButton forwardArrow = new JButton("");
        forwardArrow.setPreferredSize(new Dimension(24, 24));
        forward.setEnabled(false);
        forwardSplit.add(forward);
        forwardSplit.add(forwardArrow);

        JPanel dropSplit = new JPanel();
        dropSplit.setPreferredSize(new Dimension(110, 36));
        dropSplit.setSize(new Dimension(110, 36));
        JButton drop = new JButton("Drop");
        JButton dropArrow = new JButton("");
        dropArrow.setPreferredSize(new Dimension(24, 24));
        dropSplit.add(drop);
        dropSplit.add(dropArrow);

        proxyCluster.add(forwardSplit);
        proxyCluster.add(dropSplit);

        JToggleButton intercept = new JToggleButton("Intercept off");
        intercept.setUI(new BasicButtonUI());
        JButton openBrowser = new JButton("Open browser");
        openBrowser.setUI(new BasicButtonUI());
        proxyPage.add(intercept);
        proxyPage.add(proxyCluster);
        proxyPage.add(openBrowser);

        JTabbedPane proxyTabs = new JTabbedPane();
        proxyTabs.setName("proxy tab strip");
        proxyTabs.addTab("Intercept", new JPanel());
        proxyTabs.addTab("HTTP history", new JPanel());
        proxyTabs.addTab("WebSockets history", new JPanel());
        proxyTabs.setSelectedIndex(0);

        JButton regularButton = new JButton("Save");
        JButton disabledButton = new JButton("Disabled");
        disabledButton.setEnabled(false);
        JToggleButton selectedToggle = new JToggleButton("Selected");
        selectedToggle.setSelected(true);

        JPopupMenu popupMenu = new JPopupMenu("Actions");
        JMenuItem popupItem = new JMenuItem("Send to Repeater");
        popupMenu.add(popupItem);

        JOptionPane optionPane = new JOptionPane("Intercept settings", JOptionPane.INFORMATION_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
        JFileChooser fileChooser = new JFileChooser();

        root.add(heading);
        root.add(editorScroll);
        root.add(tableScroll);
        root.add(list);
        root.add(proxyTabs);
        root.add(proxyPage);
        root.add(regularButton);
        root.add(disabledButton);
        root.add(selectedToggle);
        root.add(popupMenu);
        root.add(optionPane);
        root.add(fileChooser);

        engine.themeLocalComponentTree(root, theme);

        check(!heading.isOpaque(), "regular labels must not keep a shaded background");
        check(!requestEditor.isOpaque(), "wallpaper-backed text editors must be transparent");
        check(requestEditor.getBackground().getAlpha() <= 48, "text editor background should be barely tinted");
        check(sameRgb(requestEditor.getSelectionColor(), theme.button), "text selection should use the readable button color");
        check(requestEditor.getSelectionColor().getAlpha() <= 170, "text selection must not become a dark opaque highlight");

        JViewport editorViewport = editorScroll.getViewport();
        check(!editorViewport.isOpaque(), "readable editor viewports must be transparent");
        check(hasBorderNamed(editorScroll, "GlassSurfaceBorder"), "readable editor scroll shell must paint glass");
        Integer editorScrollFillAlpha = integerClientProperty(editorScroll, "burptheme.glassFillAlpha");
        check(editorScrollFillAlpha != null && editorScrollFillAlpha.intValue() <= 16,
            "readable editor scroll shell must not darken the wallpaper");

        check(!table.isOpaque(), "wallpaper-backed tables must be transparent");
        TableCellRenderer tableRenderer = table.getCellRenderer(0, 0);
        Component unselectedCell = tableRenderer.getTableCellRendererComponent(table, "GET", false, false, 0, 0);
        check(!(unselectedCell instanceof JComponent) || !((JComponent) unselectedCell).isOpaque(),
            "unselected table renderer must not leave an opaque block");
        Component selectedCell = tableRenderer.getTableCellRendererComponent(table, "GET", true, false, 0, 0);
        check(selectedCell.getBackground().equals(table.getSelectionBackground()),
            "selected table renderer must use the themed selection background");

        check(!list.isOpaque(), "wallpaper-backed lists must be transparent");
        check(!hasBorderNamed(proxyPage, "GlassSurfaceBorder"), "large Proxy page shell must not paint a dark glass panel");
        check(integerClientProperty(proxyPage, "burptheme.glassFillAlpha") == null,
            "large Proxy page shell must remain transparent instead of stacking glass fills");
        check(hasBorderNamed(proxyCluster, "GlassSurfaceBorder"), "Proxy action cluster must paint a rounded glass wrapper");
        check(!proxyCluster.isOpaque(), "Proxy action cluster must let the glass wrapper show through");

        Integer proxyGlassFillAlpha = integerClientProperty(proxyCluster, "burptheme.glassFillAlpha");
        if (proxyGlassFillAlpha == null) {
            Color proxyGlassFill = colorField(proxyCluster.getBorder(), "fill");
            if (proxyGlassFill != null) {
                proxyGlassFillAlpha = Integer.valueOf(proxyGlassFill.getAlpha());
            }
        }
        if (proxyGlassFillAlpha != null) {
            check(proxyGlassFillAlpha.intValue() == 0,
                "Proxy action cluster glass wrapper must be border-only, not a dark filled strip");
        }

        checkProxyButton(forward, theme, "Proxy Forward button");
        checkProxyButton(forwardArrow, theme, "Proxy Forward arrow button");
        checkProxyButton(drop, theme, "Proxy Drop button");
        checkProxyButton(dropArrow, theme, "Proxy Drop arrow button");
        checkProxyButton(intercept, theme, "Proxy Intercept toggle");
        checkProxyButton(openBrowser, theme, "Proxy Open browser button");
        checkReadableForeground(forward, 3.0d, "disabled Proxy Forward button");
        checkReadableForeground(drop, 4.5d, "enabled Proxy Drop button");
        check(drop.getForeground().equals(theme.buttonText),
            "enabled Proxy Drop text must use the readable theme button text");
        check(!proxyTabs.isOpaque(), "Proxy tabs must be transparent over wallpaper");
        check(proxyTabs.getBackgroundAt(0).getAlpha() <= 100, "selected Proxy tab background must stay lightly translucent");
        check(proxyTabs.getBackgroundAt(1).getAlpha() == 0, "unselected Proxy tabs must not paint dark blocks");
        check(proxyTabs.getUI().getClass().getName().contains("BurpThemeTabbedPaneUI"),
            "Proxy tabs must use the custom translucent tab painter");

        checkRegularButton(regularButton, "regular dialog/action button");
        checkRegularButton(disabledButton, "disabled dialog/action button");
        checkRegularButton(selectedToggle, "selected toggle button");
        checkReadableForeground(regularButton, 4.0d, "regular dialog/action button");
        checkReadableForeground(selectedToggle, 4.0d, "selected toggle button");

        checkLowAlphaDefault("PopupMenu.background", 40);
        checkLowAlphaDefault("OptionPane.background", 44);
        checkLowAlphaDefault("OptionPane.buttonAreaBackground", 32);
        checkLowAlphaDefault("ComboBox.popupBackground", 48);
        checkLowAlphaDefault("MenuItem.background", 24);
        checkLowAlphaDefault("CheckBoxMenuItem.background", 24);
        checkLowAlphaDefault("RadioButtonMenuItem.background", 24);

        check(!popupMenu.isOpaque(), "popup menus must not paint opaque dark shells");
        Integer popupFillAlpha = integerClientProperty(popupMenu, "burptheme.glassFillAlpha");
        check(popupFillAlpha != null && popupFillAlpha.intValue() <= 28,
            "popup menu glass fill must stay low-alpha");
        check(!popupItem.isOpaque(), "popup menu items must not paint dark rectangles");
        check(!popupItem.isContentAreaFilled(), "popup menu items must not keep native content fills");
        check(popupItem.getBackground().getAlpha() <= 24, "popup menu item background must be translucent");

        check(!optionPane.isOpaque(), "option panes must not paint opaque dark shells");
        Integer optionFillAlpha = integerClientProperty(optionPane, "burptheme.glassFillAlpha");
        check(optionFillAlpha != null && optionFillAlpha.intValue() <= 32,
            "option panes must keep a light glass fill over the asset background");

        check(!fileChooser.isOpaque(), "file choosers must not paint opaque dark shells");
        Integer chooserFillAlpha = integerClientProperty(fileChooser, "burptheme.glassFillAlpha");
        check(chooserFillAlpha != null && chooserFillAlpha.intValue() <= 32,
            "file choosers must keep a light glass fill over the asset background");

        System.out.println("Visual polish harness passed.");
    }
}
"""


def main() -> int:
    if not BURP_JAR.is_file():
        raise SystemExit(f"Burp JAR not found: {BURP_JAR}")

    javac = os.environ.get("JAVAC_BIN") or shutil.which("javac")
    java = os.environ.get("JAVA_BIN") or shutil.which("java")
    if not javac:
        raise SystemExit("javac not found")
    if not java:
        raise SystemExit("java not found")

    sources = sorted(str(path) for path in SRC.rglob("*.java"))
    with tempfile.TemporaryDirectory(prefix="burptheme-visual-") as tmp:
        tmp_path = Path(tmp)
        harness_path = tmp_path / "burp" / "arcade" / "VisualPolishHarness.java"
        classes = tmp_path / "classes"
        harness_path.parent.mkdir(parents=True)
        classes.mkdir()
        harness_path.write_text(textwrap.dedent(HARNESS).lstrip(), encoding="utf-8")

        subprocess.run(
            [
                javac,
                "--release",
                "17",
                "-classpath",
                str(BURP_JAR),
                "-sourcepath",
                os.pathsep.join((str(SRC), str(tmp_path))),
                "-d",
                str(classes),
                *sources,
                str(harness_path),
            ],
            check=True,
        )
        subprocess.run(
            [
                java,
                "-Djava.awt.headless=true",
                "-classpath",
                os.pathsep.join((str(classes), str(BURP_JAR))),
                "burp.arcade.VisualPolishHarness",
            ],
            check=True,
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
