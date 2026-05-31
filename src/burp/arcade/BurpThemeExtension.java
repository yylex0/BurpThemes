package burp.arcade;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.Registration;
import burp.api.montoya.persistence.PersistedObject;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class BurpThemeExtension implements BurpExtension
{
    private static final int[] STARTUP_REFRESH_DELAYS_MS = {1800};
    private static final String PREF_THEME_ID = "selectedThemeId";
    private static final String PREF_THEME_LEGACY_LABEL = "selectedTheme";
    private static final String PREF_SUITE_WIDE = "suiteWideThemeEnabledV2";

    private final AtomicBoolean extensionUnloaded = new AtomicBoolean(true);
    private final List<BurpSuiteModule> modules = new ArrayList<>();
    private final List<Registration> registrations = new ArrayList<>();

    private MontoyaApi api;
    private BurpThemeEngine themeEngine;
    private BurpTheme currentTheme = BurpTheme.DEFAULT_THEME;
    private boolean suiteWideThemeEnabled = true;

    @Override
    public void initialize(MontoyaApi montoyaApi)
    {
        api = montoyaApi;
        extensionUnloaded.set(false);
        montoyaApi.extension().setName("BurpTheme");
        loadPreferences();
        BurpThemeStorePanel.preloadWallpapers();
        BurpThemeEngine.preloadBackgroundImages();

        themeEngine = new BurpThemeEngine(montoyaApi, extensionUnloaded);
        themeEngine.setSuiteTintEnabled(suiteWideThemeEnabled);
        montoyaApi.extension().registerUnloadingHandler(this::unloadExtension);

        BurpThemeStorePanel themeStore = createThemeStorePanel();
        themeEngine.setLocalRoot(themeStore.component());
        registerModule(themeStore);
        selectTheme(currentTheme);
        scheduleStartupRefreshes();
        montoyaApi.logging().logToOutput("BurpTheme loaded");
    }

    private BurpThemeStorePanel createThemeStorePanel()
    {
        final BurpThemeStorePanel[] panel = new BurpThemeStorePanel[1];
        runOnEdtAndWait(() -> panel[0] = new BurpThemeStorePanel(this::selectThemeFromUser, this::setSuiteWideThemeEnabled, this::reapplyTheme, suiteWideThemeEnabled));
        return panel[0];
    }

    private void registerModule(BurpSuiteModule module)
    {
        JComponent component = module.component();
        api.userInterface().applyThemeToComponent(component);
        Registration registration = api.userInterface().registerSuiteTab(module.tabTitle(), component);
        modules.add(module);
        registrations.add(registration);
        runOnEdtAndWait(() -> {
            try
            {
                module.themeChanged(currentTheme);
            }
            catch (RuntimeException exception)
            {
                logError("BurpTheme module theme initialization failed", exception);
            }
        });
    }

    private void selectTheme(BurpTheme theme)
    {
        currentTheme = theme;
        savePreferences();
        runOnEdtAndWait(() -> {
            for (BurpSuiteModule module : modules)
            {
                try
                {
                    themeEngine.themeLocalComponentTree(module.component(), theme);
                    module.themeChanged(theme);
                }
                catch (RuntimeException exception)
                {
                    logError("BurpTheme module theme update failed", exception);
                }
            }
            try
            {
                themeEngine.applyTheme(theme);
            }
            catch (RuntimeException exception)
            {
                logError("BurpTheme suite theme update failed", exception);
            }
        });
    }

    private void selectThemeFromUser(BurpTheme theme)
    {
        if (!suiteWideThemeEnabled)
        {
            suiteWideThemeEnabled = true;
            savePreferences();
            themeEngine.setSuiteTintEnabled(true);
            syncSuiteWideControls(true);
        }
        selectTheme(theme);
    }

    private void reapplyTheme()
    {
        if (!suiteWideThemeEnabled)
        {
            setSuiteWideThemeEnabled(true);
            return;
        }
        selectTheme(currentTheme);
    }

    private void setSuiteWideThemeEnabled(boolean enabled)
    {
        suiteWideThemeEnabled = enabled;
        savePreferences();
        themeEngine.setSuiteTintEnabled(enabled);
        syncSuiteWideControls(enabled);
        if (enabled)
        {
            reapplyTheme();
            return;
        }
        runOnEdtAndWait(() -> {
            themeEngine.restore(true);
            for (BurpSuiteModule module : modules)
            {
                try
                {
                    themeEngine.themeLocalComponentTree(module.component(), currentTheme);
                    module.themeChanged(currentTheme);
                }
                catch (RuntimeException exception)
                {
                    logError("BurpTheme local theme restore failed", exception);
                }
            }
        });
    }

    private void syncSuiteWideControls(boolean enabled)
    {
        runOnEdtAndWait(() -> {
            for (BurpSuiteModule module : modules)
            {
                if (module instanceof BurpThemeStorePanel)
                {
                    ((BurpThemeStorePanel) module).suiteWideChanged(enabled);
                }
            }
        });
    }

    private void loadPreferences()
    {
        try
        {
            PersistedObject data = api.persistence().extensionData();
            String themeId = data.getString(PREF_THEME_ID);
            if (themeId == null)
            {
                themeId = data.getString(PREF_THEME_LEGACY_LABEL);
            }
            if (themeId != null)
            {
                currentTheme = BurpTheme.fromStoredValue(themeId);
            }
            Boolean suiteWide = data.getBoolean(PREF_SUITE_WIDE);
            if (suiteWide != null)
            {
                suiteWideThemeEnabled = suiteWide.booleanValue();
            }
        }
        catch (RuntimeException | LinkageError exception)
        {
            logError("BurpTheme preference load failed", exception);
        }
    }

    private void savePreferences()
    {
        if (api == null)
        {
            return;
        }
        try
        {
            PersistedObject data = api.persistence().extensionData();
            data.setString(PREF_THEME_ID, currentTheme.slug);
            data.setBoolean(PREF_SUITE_WIDE, suiteWideThemeEnabled);
        }
        catch (RuntimeException | LinkageError exception)
        {
            logError("BurpTheme preference save failed", exception);
        }
    }

    private void scheduleStartupRefreshes()
    {
        for (int delay : STARTUP_REFRESH_DELAYS_MS)
        {
            themeEngine.scheduleSuiteThemeRefresh(delay);
        }
    }

    private void deregisterRegistrations()
    {
        for (Registration registration : new ArrayList<>(registrations))
        {
            try
            {
                if (registration.isRegistered())
                {
                    registration.deregister();
                }
            }
            catch (RuntimeException exception)
            {
                logError("BurpTheme registration deregistration failed", exception);
            }
        }
        registrations.clear();
    }

    private void logError(String message, Throwable exception)
    {
        if (api != null)
        {
            api.logging().logToError(message + ": " + exception);
        }
    }

    private void runOnEdtAndWait(Runnable action)
    {
        if (SwingUtilities.isEventDispatchThread())
        {
            action.run();
            return;
        }
        try
        {
            SwingUtilities.invokeAndWait(action);
        }
        catch (Exception exception)
        {
            throw new IllegalStateException("BurpTheme EDT task failed", exception);
        }
    }

    private void unloadExtension()
    {
        if (!extensionUnloaded.compareAndSet(false, true))
        {
            return;
        }
        try
        {
            for (BurpSuiteModule module : new ArrayList<>(modules))
            {
                try
                {
                    module.unload();
                }
                catch (RuntimeException exception)
                {
                    logError("BurpTheme module unload failed", exception);
                }
            }
            deregisterRegistrations();
        }
        finally
        {
            try
            {
                if (themeEngine != null)
                {
                    themeEngine.restore(true);
                }
            }
            catch (RuntimeException exception)
            {
                logError("BurpTheme restore failed during unload", exception);
            }
            modules.clear();
            BurpThemeStorePanel.clearImageCaches();
            BurpThemeEngine.clearBackgroundImageCache();
            ThemeCrestIcon.clearAvatarCache();
        }
    }
}
