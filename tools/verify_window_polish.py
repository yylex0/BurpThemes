#!/usr/bin/env python3
from __future__ import annotations

import os
import shutil
import subprocess
import tempfile
import textwrap
from pathlib import Path

from runtime_smoke_burp import start_xvfb, terminate


ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src"
BURP_JAR = Path(os.environ.get("BURP_JAR", "/usr/share/burpsuite/burpsuite.jar"))


HARNESS = r"""
package burp.arcade;

import java.awt.Component;
import java.awt.Container;
import java.awt.Color;
import java.awt.Window;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JWindow;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;

public final class WindowPolishHarness {
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static int invokeThemeWindow(BurpThemeEngine engine, Window window) {
        try {
            Method method = BurpThemeEngine.class.getDeclaredMethod("themeWindow", Window.class, BurpTheme.class, int.class);
            method.setAccessible(true);
            return ((Integer) method.invoke(engine, window, BurpTheme.GREAT_PLATEAU, Integer.valueOf(25000))).intValue();
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Unable to invoke themeWindow", ex);
        }
    }

    private static int invokeThemePopupWindow(BurpThemeEngine engine, Window window) {
        try {
            Method method = BurpThemeEngine.class.getDeclaredMethod("themePopupWindow", Window.class, BurpTheme.class);
            method.setAccessible(true);
            return ((Integer) method.invoke(engine, window, BurpTheme.GREAT_PLATEAU)).intValue();
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Unable to invoke themePopupWindow", ex);
        }
    }

    private static void installDefaults(BurpThemeEngine engine) {
        try {
            Method method = BurpThemeEngine.class.getDeclaredMethod("installColorDefaults", BurpTheme.class);
            method.setAccessible(true);
            method.invoke(engine, BurpTheme.GREAT_PLATEAU);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Unable to install defaults", ex);
        }
    }

    private static boolean hasBackgroundPanel(JLayeredPane layeredPane) {
        if (layeredPane == null) {
            return false;
        }
        for (Component child : layeredPane.getComponents()) {
            if (child instanceof JComponent
                && Boolean.TRUE.equals(((JComponent) child).getClientProperty("burptheme.background"))) {
                return true;
            }
        }
        return false;
    }

    private static int glassFillAlpha(JComponent component) {
        Object value = component.getClientProperty("burptheme.glassFillAlpha");
        return value instanceof Integer ? ((Integer) value).intValue() : -1;
    }

    private static void checkRootPaneContainer(RootPaneContainer container, String label) {
        JRootPane rootPane = container.getRootPane();
        check(rootPane != null, label + " must have a root pane");
        check(!rootPane.isOpaque(), label + " root pane must be transparent");
        check(rootPane.getBackground() == null || rootPane.getBackground().getAlpha() == 0,
            label + " root pane background must not be dark-filled");
        Color title = (Color) rootPane.getClientProperty("JRootPane.titleBarBackground");
        Color inactive = (Color) rootPane.getClientProperty("JRootPane.titleBarInactiveBackground");
        check(title != null && title.getAlpha() <= 120, label + " title bar must stay translucent");
        check(inactive != null && inactive.getAlpha() <= 90, label + " inactive title bar must stay translucent");

        Container content = container.getContentPane();
        check(content instanceof JComponent, label + " content pane must be a JComponent");
        JComponent contentComponent = (JComponent) content;
        check(!contentComponent.isOpaque(), label + " content pane must be transparent over the asset");
        int fillAlpha = glassFillAlpha(contentComponent);
        int allowedBackgroundAlpha = fillAlpha >= 0 ? Math.min(32, fillAlpha) : 0;
        check(contentComponent.getBackground() == null || contentComponent.getBackground().getAlpha() <= allowedBackgroundAlpha,
            label + " content pane background must not be a dark fill");
        check(hasBackgroundPanel(container.getLayeredPane()), label + " must install a Burp asset background panel");
    }

    private static void checkButton(AbstractButton button, String label) {
        check(!button.isOpaque(), label + " must not paint an opaque button rectangle");
        check(!button.isContentAreaFilled(), label + " must not keep the native content fill");
        check(button.getClientProperty("burptheme.proxyPillFill") instanceof Color,
            label + " must use the custom translucent pill painter");
        Color fill = (Color) button.getClientProperty("burptheme.proxyPillFill");
        check(fill.getAlpha() <= 120, label + " fill must remain translucent");
    }

    private static void checkPopupContent(Container container, String label) {
        for (Component child : container.getComponents()) {
            if (child instanceof AbstractButton) {
                checkButton((AbstractButton) child, label + " button");
            }
            if (child instanceof JOptionPane) {
                JOptionPane optionPane = (JOptionPane) child;
                check(!optionPane.isOpaque(), label + " option pane must not be opaque dark");
                check(glassFillAlpha(optionPane) <= 32, label + " option pane glass fill must stay low-alpha");
            }
            if (child instanceof Container) {
                checkPopupContent((Container) child, label);
            }
        }
    }

    private static void runOnEdt() {
        BurpThemeEngine engine = new BurpThemeEngine(null, new AtomicBoolean(false));
        installDefaults(engine);
        engine.themeLocalComponentTree(new JPanel(), BurpTheme.GREAT_PLATEAU);

        JFrame frame = new JFrame("Frame polish");
        JPanel frameContent = new JPanel();
        JButton frameButton = new JButton("Frame button");
        frameContent.add(new JLabel("Frame"));
        frameContent.add(frameButton);
        frame.setContentPane(frameContent);
        frame.pack();

        JDialog dialog = new JDialog((JFrame) null, "Dialog polish");
        JPanel dialogContent = new JPanel();
        JButton dialogButton = new JButton("Dialog button");
        dialogContent.add(new JLabel("Dialog"));
        dialogContent.add(dialogButton);
        dialog.setContentPane(dialogContent);
        dialog.pack();

        JWindow window = new JWindow();
        JPanel windowContent = new JPanel();
        JButton windowButton = new JButton("Window button");
        windowContent.add(new JLabel("Window"));
        windowContent.add(windowButton);
        window.setContentPane(windowContent);
        window.pack();

        JDialog optionDialog = new JDialog((JFrame) null, "Option polish");
        JOptionPane optionPane = new JOptionPane("Popup message", JOptionPane.INFORMATION_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
        optionDialog.setContentPane(optionPane);
        optionDialog.pack();

        try {
            invokeThemeWindow(engine, frame);
            invokeThemePopupWindow(engine, dialog);
            invokeThemePopupWindow(engine, window);
            invokeThemePopupWindow(engine, optionDialog);

            checkRootPaneContainer(frame, "JFrame");
            checkRootPaneContainer(dialog, "JDialog");
            checkRootPaneContainer(window, "JWindow");
            checkRootPaneContainer(optionDialog, "JOptionPane dialog");
            checkButton(frameButton, "frame action button");
            checkButton(dialogButton, "dialog action button");
            checkButton(windowButton, "window action button");
            checkPopupContent(optionDialog.getContentPane(), "option dialog");
        } finally {
            optionDialog.dispose();
            window.dispose();
            dialog.dispose();
            frame.dispose();
        }
    }

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(WindowPolishHarness::runOnEdt);
        System.out.println("Window polish harness passed.");
    }
}
"""


def run_harness(java: str, javac: str, env: dict[str, str]) -> None:
    sources = sorted(str(path) for path in SRC.rglob("*.java"))
    with tempfile.TemporaryDirectory(prefix="burptheme-window-polish-") as tmp:
        tmp_path = Path(tmp)
        harness_path = tmp_path / "burp" / "arcade" / "WindowPolishHarness.java"
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
                "-Djava.awt.headless=false",
                "-classpath",
                os.pathsep.join((str(classes), str(BURP_JAR))),
                "burp.arcade.WindowPolishHarness",
            ],
            env=env,
            check=True,
        )


def main() -> int:
    if not BURP_JAR.is_file():
        raise SystemExit(f"Burp JAR not found: {BURP_JAR}")
    javac = os.environ.get("JAVAC_BIN") or shutil.which("javac")
    java = os.environ.get("JAVA_BIN") or shutil.which("java")
    xvfb = os.environ.get("XVFB_BIN") or shutil.which("Xvfb")
    if not javac:
        raise SystemExit("javac not found")
    if not java:
        raise SystemExit("java not found")

    env = os.environ.copy()
    xvfb_process = None
    with tempfile.TemporaryDirectory(prefix="burptheme-window-xvfb-") as tmp:
        if not env.get("DISPLAY"):
            if not xvfb:
                raise SystemExit("DISPLAY is not set and Xvfb was not found")
            xvfb_process, display = start_xvfb(xvfb, Path(tmp))
            env["DISPLAY"] = display
        try:
            run_harness(java, javac, env)
        finally:
            if xvfb_process is not None:
                terminate(xvfb_process)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
