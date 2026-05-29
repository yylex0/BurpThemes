package burp.arcade;

import javax.swing.JComponent;

interface BurpSuiteModule
{
    String tabTitle();

    JComponent component();

    void themeChanged(BurpTheme theme);

    default void unload()
    {
    }
}
