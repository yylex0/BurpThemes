package burp.arcade;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.JViewport;
import javax.accessibility.AccessibleAction;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleState;
import javax.accessibility.AccessibleStateSet;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.KeyEvent;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

final class BurpThemeStorePanel implements BurpSuiteModule
{
    private static final int CARD_WIDTH = 324;
    private static final int CARD_HEIGHT = 314;
    private static final int CARD_MIN_WIDTH = 250;
    private static final int CARD_PREVIEW_HEIGHT = 146;
    private static final int HERO_PREVIEW_WIDTH = 430;
    private static final int HERO_PREVIEW_HEIGHT = 182;
    private static final Map<String, BufferedImage> WALLPAPERS = new LinkedHashMap<>();
    private static final Map<String, ImageIcon> PREVIEWS = new LinkedHashMap<>();
    private static final Map<String, BufferedImage> DETAILS = new LinkedHashMap<>();
    private static final Map<String, ImageIcon> MOTIONS = new LinkedHashMap<>();

    private final Consumer<BurpTheme> themeSelectionHandler;
    private final Consumer<Boolean> suiteWideHandler;
    private final Runnable reapplyHandler;
    private final WallpaperPanel rootPanel;
    private final Map<BurpTheme, ThemeCard> themeCards = new LinkedHashMap<>();
    private final boolean initialSuiteWideEnabled;

    private JLabel crestLabel;
    private JLabel titleLabel;
    private JLabel statusLabel;
    private JLabel activeTaglineLabel;
    private JLabel themesLabel;
    private AnimatedPreviewPanel heroPreviewPanel;
    private JCheckBox suiteWideCheckBox;
    private JButton refreshButton;
    private BurpTheme currentTheme = BurpTheme.GREAT_PLATEAU;
    private boolean suiteWideEnabled;
    private boolean syncingSuiteWide;

    BurpThemeStorePanel(Consumer<BurpTheme> themeSelectionHandler, Consumer<Boolean> suiteWideHandler, Runnable reapplyHandler, boolean initialSuiteWideEnabled)
    {
        this.themeSelectionHandler = themeSelectionHandler;
        this.suiteWideHandler = suiteWideHandler;
        this.reapplyHandler = reapplyHandler;
        this.initialSuiteWideEnabled = initialSuiteWideEnabled;
        suiteWideEnabled = initialSuiteWideEnabled;
        rootPanel = buildUi();
        themeChanged(currentTheme);
    }

    @Override
    public String tabTitle()
    {
        return "BurpTheme";
    }

    @Override
    public JComponent component()
    {
        return rootPanel;
    }

    @Override
    public void themeChanged(BurpTheme theme)
    {
        currentTheme = theme;
        rootPanel.theme = theme;
        rootPanel.revalidate();
        rootPanel.repaint();
        if (titleLabel != null)
        {
            titleLabel.setForeground(theme.text);
        }
        if (statusLabel != null)
        {
            updateStatusLabel();
            statusLabel.setForeground(theme.muted);
        }
        if (activeTaglineLabel != null)
        {
            activeTaglineLabel.setText(theme.tagline);
            activeTaglineLabel.setForeground(ThemeColors.alpha(theme.gold, 220));
        }
        if (themesLabel != null)
        {
            themesLabel.setForeground(theme.text);
        }
        if (heroPreviewPanel != null)
        {
            heroPreviewPanel.setTheme(theme);
        }
        if (crestLabel != null)
        {
            crestLabel.setIcon(new ThemeCrestIcon(theme, 56));
            crestLabel.repaint();
        }
        if (suiteWideCheckBox != null)
        {
            styleSuiteWideCheckBox();
        }
        if (refreshButton != null)
        {
            styleThemeButton(refreshButton);
        }
        refreshThemeCards(theme);
    }

    void suiteWideChanged(boolean enabled)
    {
        suiteWideEnabled = enabled;
        if (suiteWideCheckBox != null && suiteWideCheckBox.isSelected() != enabled)
        {
            syncingSuiteWide = true;
            try
            {
                suiteWideCheckBox.setSelected(enabled);
            }
            finally
            {
                syncingSuiteWide = false;
            }
        }
        updateStatusLabel();
        refreshThemeCards(currentTheme);
    }

    private void updateStatusLabel()
    {
        if (statusLabel == null)
        {
            return;
        }
        statusLabel.setText(currentTheme.label + " active | suite-wide " + (suiteWideEnabled ? "on" : "off"));
    }

    private WallpaperPanel buildUi()
    {
        WallpaperPanel root = new WallpaperPanel();
        root.setLayout(new BorderLayout());
        root.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        JScrollPane scrollPane = new JScrollPane(buildThemeStudio(), ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setOpaque(false);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(24);
        root.add(scrollPane, BorderLayout.CENTER);
        return root;
    }

    private JPanel buildThemeStudio()
    {
        themeCards.clear();
        JPanel studio = new StudioPanel(new BorderLayout(16, 16));
        studio.setOpaque(false);
        studio.add(buildThemeHeader(), BorderLayout.NORTH);
        studio.add(buildThemeGrid(), BorderLayout.CENTER);
        return studio;
    }

    private JPanel buildThemeHeader()
    {
        JPanel header = glassPanel(new BorderLayout(18, 12));
        header.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));

        crestLabel = new JLabel(new ThemeCrestIcon(currentTheme, 56));
        crestLabel.setOpaque(false);

        JPanel copy = new JPanel(new BorderLayout(0, 6));
        copy.setOpaque(false);
        titleLabel = new JLabel("BurpTheme Vault");
        titleLabel.setFont(uiFont("Label.font", Font.BOLD, 10.0f));
        statusLabel = new JLabel();
        statusLabel.setFont(uiFont("Label.font", Font.PLAIN, 0.0f));
        activeTaglineLabel = new JLabel(currentTheme.tagline);
        activeTaglineLabel.setFont(uiFont("Label.font", Font.BOLD, -1.0f));
        copy.add(titleLabel, BorderLayout.NORTH);
        copy.add(statusLabel, BorderLayout.CENTER);
        copy.add(activeTaglineLabel, BorderLayout.SOUTH);

        JPanel identity = new JPanel(new BorderLayout(14, 0));
        identity.setOpaque(false);
        identity.add(crestLabel, BorderLayout.WEST);
        identity.add(copy, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);
        suiteWideCheckBox = new JCheckBox("Suite-wide");
        suiteWideCheckBox.setOpaque(false);
        suiteWideCheckBox.setSelected(initialSuiteWideEnabled);
        suiteWideCheckBox.setToolTipText("Apply the selected theme to visible Burp Suite windows.");
        suiteWideCheckBox.addActionListener(event -> {
            if (!syncingSuiteWide)
            {
                suiteWideHandler.accept(suiteWideCheckBox.isSelected());
            }
        });
        refreshButton = themeButton("Reapply");
        refreshButton.setToolTipText("Reapply the selected theme to windows Burp opened later.");
        refreshButton.addActionListener(event -> reapplyHandler.run());
        actions.add(suiteWideCheckBox);
        actions.add(refreshButton);

        heroPreviewPanel = new AnimatedPreviewPanel(currentTheme, HERO_PREVIEW_WIDTH, HERO_PREVIEW_HEIGHT, true);
        heroPreviewPanel.setPreferredSize(new Dimension(HERO_PREVIEW_WIDTH, HERO_PREVIEW_HEIGHT));

        header.add(identity, BorderLayout.CENTER);
        header.add(heroPreviewPanel, BorderLayout.EAST);
        header.add(actions, BorderLayout.SOUTH);
        return header;
    }

    private JComponent buildThemeGrid()
    {
        JPanel grid = glassPanel(new BorderLayout(0, 14));
        grid.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));

        themesLabel = new JLabel("Themes");
        themesLabel.setFont(uiFont("Label.font", Font.BOLD, 1.0f));
        themesLabel.setForeground(currentTheme.text);

        JPanel cards = new CardFlowPanel();
        cards.setOpaque(false);
        for (BurpTheme theme : BurpTheme.values())
        {
            cards.add(themeCard(theme));
        }

        grid.add(themesLabel, BorderLayout.NORTH);
        grid.add(cards, BorderLayout.CENTER);
        return grid;
    }

    private JPanel themeCard(BurpTheme theme)
    {
        ThemeCard card = new ThemeCard(theme);
        card.setToolTipText("Apply " + theme.label);
        java.awt.event.MouseAdapter listener = new java.awt.event.MouseAdapter()
        {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event)
            {
                card.requestFocusInWindow();
                selectThemeIfNeeded(theme);
            }

            @Override
            public void mouseEntered(java.awt.event.MouseEvent event)
            {
                card.setHovering(true);
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent event)
            {
                Point cardPoint = SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), card);
                if (!card.contains(cardPoint))
                {
                    card.setHovering(false);
                }
            }
        };
        installCardActivation(card, listener);
        card.registerKeyboardAction(event -> selectThemeIfNeeded(theme), KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_FOCUSED);
        card.registerKeyboardAction(event -> selectThemeIfNeeded(theme), KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), JComponent.WHEN_FOCUSED);
        themeCards.put(theme, card);
        return card;
    }

    private void selectThemeIfNeeded(BurpTheme theme)
    {
        if (theme != currentTheme || !suiteWideEnabled)
        {
            themeSelectionHandler.accept(theme);
        }
    }

    private void installCardActivation(Component component, java.awt.event.MouseAdapter listener)
    {
        component.addMouseListener(listener);
        component.setCursor(new Cursor(Cursor.HAND_CURSOR));
        if (component instanceof Container)
        {
            for (Component child : ((Container) component).getComponents())
            {
                installCardActivation(child, listener);
            }
        }
    }

    private JButton themeButton(String text)
    {
        JButton button = new JButton(text);
        button.setFont(uiFont("Button.font", Font.BOLD, 0.0f));
        styleThemeButton(button);
        return button;
    }

    private void styleThemeButton(JButton button)
    {
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setFocusPainted(true);
        button.putClientProperty("JButton.buttonType", "roundRect");
        button.putClientProperty("FlatLaf.style", "arc: 12"
            + "; background: " + ThemeColors.rgb(currentTheme.button.darker())
            + "; foreground: " + ThemeColors.rgb(currentTheme.buttonText)
            + "; hoverBackground: " + ThemeColors.rgb(currentTheme.button)
            + "; pressedBackground: " + ThemeColors.rgb(currentTheme.button.darker())
            + "; borderColor: " + ThemeColors.rgb(ThemeColors.alpha(currentTheme.gold, 170))
            + "; focusColor: " + ThemeColors.rgb(currentTheme.gold)
            + "; focusWidth: 1");
        button.setBackground(ThemeColors.opaque(currentTheme.button.darker()));
        button.setForeground(currentTheme.buttonText);
        button.setBorder(BorderFactory.createEmptyBorder(7, 13, 7, 13));
        button.repaint();
    }

    private void styleSuiteWideCheckBox()
    {
        suiteWideCheckBox.setFont(uiFont("CheckBox.font", Font.BOLD, 0.0f));
        suiteWideCheckBox.setForeground(currentTheme.text);
        suiteWideCheckBox.setBackground(ThemeColors.transparent(currentTheme.base));
        suiteWideCheckBox.repaint();
    }

    private static Font uiFont(String key, int style, float sizeDelta)
    {
        Font base = UIManager.getFont(key);
        if (base == null)
        {
            base = new Font("Dialog", Font.PLAIN, 13);
        }
        float size = Math.max(10.0f, base.getSize2D() + sizeDelta);
        return base.deriveFont(style, size);
    }

    private JPanel glassPanel(BorderLayout layout)
    {
        JPanel panel = new GlassPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    private void refreshThemeCards(BurpTheme selectedTheme)
    {
        for (Map.Entry<BurpTheme, ThemeCard> entry : themeCards.entrySet())
        {
            BurpTheme theme = entry.getKey();
            ThemeCard card = entry.getValue();
            boolean selected = theme == selectedTheme;
            card.setSelectedTheme(selected);
            card.setToolTipText(selected ? "Currently using " + theme.label : "Apply " + theme.label);
        }
    }

    private static synchronized BufferedImage loadWallpaper(BurpTheme theme)
    {
        BufferedImage existing = WALLPAPERS.get(theme.backgroundResource);
        if (existing != null)
        {
            return existing;
        }
        try
        {
            java.net.URL resource = BurpThemeStorePanel.class.getResource(theme.backgroundResource);
            if (resource == null && theme.backgroundResource.startsWith("/"))
            {
                resource = BurpThemeStorePanel.class.getClassLoader().getResource(theme.backgroundResource.substring(1));
            }
            BufferedImage loaded = resource == null ? null : ImageIO.read(resource);
            if (loaded == null && Boolean.getBoolean("burptheme.devAssets"))
            {
                String name = theme.backgroundResource.substring(theme.backgroundResource.lastIndexOf('/') + 1);
                File candidate = new File(devAssetsDir(), name);
                loaded = candidate.isFile() ? ImageIO.read(candidate) : null;
            }
            if (loaded != null)
            {
                WALLPAPERS.put(theme.backgroundResource, loaded);
            }
            return loaded;
        }
        catch (IOException | IllegalArgumentException exception)
        {
            return null;
        }
    }

    private static synchronized ImageIcon loadPreview(BurpTheme theme)
    {
        ImageIcon existing = PREVIEWS.get(theme.previewResource);
        if (existing != null)
        {
            return existing;
        }
        try
        {
            java.net.URL resource = BurpThemeStorePanel.class.getResource(theme.previewResource);
            if (resource == null && theme.previewResource.startsWith("/"))
            {
                resource = BurpThemeStorePanel.class.getClassLoader().getResource(theme.previewResource.substring(1));
            }
            ImageIcon loaded = resource == null ? null : new ImageIcon(resource);
            if ((loaded == null || loaded.getIconWidth() <= 0) && Boolean.getBoolean("burptheme.devAssets"))
            {
                String name = theme.previewResource.substring(theme.previewResource.lastIndexOf('/') + 1);
                File candidate = new File(devAssetsDir(), name);
                loaded = candidate.isFile() ? new ImageIcon(candidate.getAbsolutePath()) : null;
            }
            if (loaded != null && loaded.getIconWidth() > 0)
            {
                PREVIEWS.put(theme.previewResource, loaded);
                return loaded;
            }
            return null;
        }
        catch (RuntimeException exception)
        {
            return null;
        }
    }

    private static synchronized BufferedImage loadDetail(BurpTheme theme)
    {
        BufferedImage existing = DETAILS.get(theme.detailResource);
        if (existing != null)
        {
            return existing;
        }
        try
        {
            java.net.URL resource = BurpThemeStorePanel.class.getResource(theme.detailResource);
            if (resource == null && theme.detailResource.startsWith("/"))
            {
                resource = BurpThemeStorePanel.class.getClassLoader().getResource(theme.detailResource.substring(1));
            }
            BufferedImage loaded = resource == null ? null : ImageIO.read(resource);
            if (loaded == null && Boolean.getBoolean("burptheme.devAssets"))
            {
                String name = theme.detailResource.substring(theme.detailResource.lastIndexOf('/') + 1);
                File candidate = new File(devAssetsDir(), name);
                loaded = candidate.isFile() ? ImageIO.read(candidate) : null;
            }
            if (loaded != null)
            {
                DETAILS.put(theme.detailResource, loaded);
            }
            return loaded;
        }
        catch (IOException | IllegalArgumentException exception)
        {
            return null;
        }
    }

    private static synchronized ImageIcon loadMotion(BurpTheme theme)
    {
        ImageIcon existing = MOTIONS.get(theme.motionResource);
        if (existing != null)
        {
            return existing;
        }
        try
        {
            java.net.URL resource = BurpThemeStorePanel.class.getResource(theme.motionResource);
            if (resource == null && theme.motionResource.startsWith("/"))
            {
                resource = BurpThemeStorePanel.class.getClassLoader().getResource(theme.motionResource.substring(1));
            }
            ImageIcon loaded = resource == null ? null : new ImageIcon(resource);
            if ((loaded == null || loaded.getIconWidth() <= 0) && Boolean.getBoolean("burptheme.devAssets"))
            {
                String name = theme.motionResource.substring(theme.motionResource.lastIndexOf('/') + 1);
                File candidate = new File(devAssetsDir(), name);
                loaded = candidate.isFile() ? new ImageIcon(candidate.getAbsolutePath()) : null;
            }
            if (loaded != null && loaded.getIconWidth() > 0)
            {
                MOTIONS.put(theme.motionResource, loaded);
                return loaded;
            }
            return null;
        }
        catch (RuntimeException exception)
        {
            return null;
        }
    }

    static synchronized void preloadWallpapers()
    {
        for (BurpTheme theme : BurpTheme.values())
        {
            loadWallpaper(theme);
            loadPreview(theme);
            loadDetail(theme);
            loadMotion(theme);
        }
    }

    static synchronized void clearImageCaches()
    {
        for (BufferedImage wallpaper : WALLPAPERS.values())
        {
            wallpaper.flush();
        }
        WALLPAPERS.clear();
        for (ImageIcon preview : PREVIEWS.values())
        {
            Image image = preview.getImage();
            if (image != null)
            {
                image.flush();
            }
        }
        PREVIEWS.clear();
        for (BufferedImage detail : DETAILS.values())
        {
            detail.flush();
        }
        DETAILS.clear();
        for (ImageIcon motion : MOTIONS.values())
        {
            Image image = motion.getImage();
            if (image != null)
            {
                image.flush();
            }
        }
        MOTIONS.clear();
    }

    private static File devAssetsDir()
    {
        String configured = System.getProperty("burptheme.devAssetsDir");
        return configured == null || configured.trim().isEmpty() ? new File("assets") : new File(configured);
    }

    private final class ThemeCard extends JPanel
    {
        private static final long serialVersionUID = 1L;

        private final BurpTheme theme;
        private final JLabel nameLabel;
        private final JLabel taglineLabel;
        private final JLabel actionLabel;
        private final AnimatedPreviewPanel previewPanel;
        private boolean selectedTheme;
        private boolean hovering;

        private ThemeCard(BurpTheme theme)
        {
            super(new BorderLayout(0, 0));
            this.theme = theme;
            setOpaque(false);
            setFocusable(true);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(CARD_WIDTH, CARD_HEIGHT));
            setMinimumSize(new Dimension(CARD_MIN_WIDTH, CARD_HEIGHT));
            setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
            getAccessibleContext().setAccessibleName(theme.label + " theme");
            getAccessibleContext().setAccessibleDescription("Apply the " + theme.label + " BurpTheme theme.");

            previewPanel = new AnimatedPreviewPanel(theme, CARD_WIDTH, CARD_PREVIEW_HEIGHT, false);
            previewPanel.setLayout(new BorderLayout());
            previewPanel.setOpaque(false);
            previewPanel.setPreferredSize(new Dimension(CARD_WIDTH, CARD_PREVIEW_HEIGHT));
            JLabel iconLabel = new JLabel(new ThemeCrestIcon(theme, 62));
            iconLabel.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
            iconLabel.setOpaque(false);
            previewPanel.add(iconLabel, BorderLayout.WEST);

            JPanel body = new JPanel(new BorderLayout(0, 10));
            body.setOpaque(false);
            body.setBorder(BorderFactory.createEmptyBorder(12, 14, 14, 14));

            JPanel copy = new JPanel(new BorderLayout(0, 4));
            copy.setOpaque(false);
            nameLabel = new JLabel(theme.label);
            nameLabel.setFont(uiFont("Label.font", Font.BOLD, 1.0f));
            taglineLabel = new JLabel(theme.tagline);
            taglineLabel.setFont(uiFont("Label.font", Font.PLAIN, -1.0f));
            copy.add(nameLabel, BorderLayout.NORTH);
            copy.add(taglineLabel, BorderLayout.CENTER);

            actionLabel = new BadgeLabel("Apply");
            actionLabel.setFont(uiFont("Label.font", Font.BOLD, -2.0f));
            actionLabel.setOpaque(false);
            actionLabel.setHorizontalAlignment(SwingConstants.CENTER);
            actionLabel.setPreferredSize(new Dimension(68, 24));
            actionLabel.setMinimumSize(new Dimension(68, 24));
            actionLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            JPanel actionBadge = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            actionBadge.setOpaque(false);
            actionBadge.add(actionLabel);

            JPanel topRow = new JPanel(new BorderLayout(10, 0));
            topRow.setOpaque(false);
            topRow.add(copy, BorderLayout.CENTER);
            topRow.add(actionBadge, BorderLayout.EAST);

            body.add(topRow, BorderLayout.NORTH);
            body.add(colorSwatches(theme), BorderLayout.SOUTH);

            add(previewPanel, BorderLayout.NORTH);
            add(body, BorderLayout.CENTER);
            setSelectedTheme(false);
        }

        @Override
        public Dimension getPreferredSize()
        {
            return new Dimension(responsiveCardWidth(getParent()), CARD_HEIGHT);
        }

        @Override
        public Dimension getMinimumSize()
        {
            return new Dimension(CARD_MIN_WIDTH, CARD_HEIGHT);
        }

        @Override
        public AccessibleContext getAccessibleContext()
        {
            if (accessibleContext == null)
            {
                accessibleContext = new AccessibleThemeCard();
            }
            return accessibleContext;
        }

        @Override
        protected void paintComponent(Graphics graphics)
        {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int arc = 16;
            Color fill = selectedTheme ? theme.horizon.darker() : theme.base.darker();
            int fillAlpha = selectedTheme ? 236 : hovering ? 222 : 202;
            g2.setColor(ThemeColors.alpha(fill, fillAlpha));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
            Color border = selectedTheme ? theme.gold : theme.accent;
            int borderAlpha = selectedTheme ? 220 : hovering || hasFocus() ? 172 : 72;
            g2.setStroke(new BasicStroke(selectedTheme || hasFocus() ? 2.0f : 1.0f));
            g2.setColor(ThemeColors.alpha(border, borderAlpha));
            g2.drawRoundRect(1, 1, Math.max(1, getWidth() - 3), Math.max(1, getHeight() - 3), arc, arc);
            g2.dispose();
            super.paintComponent(graphics);
        }

        private void setHovering(boolean hovering)
        {
            this.hovering = hovering;
            previewPanel.hovering = hovering;
            repaint();
            previewPanel.repaint();
        }

        private void setSelectedTheme(boolean selected)
        {
            selectedTheme = selected;
            previewPanel.selectedTheme = selected;
            nameLabel.setForeground(selected ? theme.buttonText : theme.text);
            taglineLabel.setForeground(selected ? ThemeColors.alpha(theme.buttonText, 208) : theme.muted);
            actionLabel.setText(selected ? "Current" : "Apply");
            actionLabel.setForeground(selected ? theme.buttonText : theme.text);
            actionLabel.setBackground(ThemeColors.alpha(selected ? theme.button : theme.base.darker(), selected ? 226 : 196));
            getAccessibleContext().setAccessibleDescription(selected ? "Current BurpTheme theme." : "Apply the " + theme.label + " BurpTheme theme.");
            revalidate();
            repaint();
            previewPanel.repaint();
        }

        private final class AccessibleThemeCard extends AccessibleJPanel implements AccessibleAction
        {
            private static final long serialVersionUID = 1L;

            @Override
            public AccessibleRole getAccessibleRole()
            {
                return AccessibleRole.PUSH_BUTTON;
            }

            @Override
            public AccessibleAction getAccessibleAction()
            {
                return this;
            }

            @Override
            public int getAccessibleActionCount()
            {
                return 1;
            }

            @Override
            public String getAccessibleActionDescription(int index)
            {
                return index == 0 ? (selectedTheme ? "Current" : "Apply") : null;
            }

            @Override
            public boolean doAccessibleAction(int index)
            {
                if (index != 0)
                {
                    return false;
                }
                selectThemeIfNeeded(theme);
                return true;
            }

            @Override
            public AccessibleStateSet getAccessibleStateSet()
            {
                AccessibleStateSet states = super.getAccessibleStateSet();
                if (selectedTheme)
                {
                    states.add(AccessibleState.SELECTED);
                }
                return states;
            }
        }
    }

    private static int responsiveCardWidth(Container parent)
    {
        if (!(parent instanceof CardFlowPanel) || parent.getWidth() <= 0)
        {
            return CARD_WIDTH;
        }
        FlowLayout layout = (FlowLayout) parent.getLayout();
        Insets insets = parent.getInsets();
        int available = parent.getWidth() - insets.left - insets.right - layout.getHgap() * 2;
        if (available <= 0)
        {
            return CARD_WIDTH;
        }
        int columns = Math.max(1, (available + layout.getHgap()) / (CARD_WIDTH + layout.getHgap()));
        int width = (available - layout.getHgap() * Math.max(0, columns - 1)) / columns;
        return Math.max(CARD_MIN_WIDTH, Math.min(CARD_WIDTH, width));
    }

    private JPanel colorSwatches(BurpTheme theme)
    {
        JPanel swatches = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        swatches.setOpaque(false);
        swatches.add(new ColorSwatch(theme.base));
        swatches.add(new ColorSwatch(theme.horizon));
        swatches.add(new ColorSwatch(theme.accent));
        swatches.add(new ColorSwatch(theme.gold));
        swatches.add(Box.createHorizontalGlue());
        return swatches;
    }

    private static final class ColorSwatch extends JPanel
    {
        private static final long serialVersionUID = 1L;

        private final Color color;

        private ColorSwatch(Color color)
        {
            this.color = color;
            setOpaque(false);
            setPreferredSize(new Dimension(30, 10));
            setMinimumSize(new Dimension(30, 10));
        }

        @Override
        protected void paintComponent(Graphics graphics)
        {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 7, 7);
            g2.setColor(new Color(255, 255, 255, 58));
            g2.drawRoundRect(0, 0, Math.max(1, getWidth() - 1), Math.max(1, getHeight() - 1), 7, 7);
            g2.dispose();
        }
    }

    private static final class AnimatedPreviewPanel extends JPanel
    {
        private static final long serialVersionUID = 1L;

        private BurpTheme theme;
        private final int preferredWidth;
        private final int preferredHeight;
        private final boolean hero;
        private boolean selectedTheme;
        private boolean hovering;

        private AnimatedPreviewPanel(BurpTheme theme, int preferredWidth, int preferredHeight, boolean hero)
        {
            this.theme = theme;
            this.preferredWidth = preferredWidth;
            this.preferredHeight = preferredHeight;
            this.hero = hero;
            setOpaque(false);
        }

        private void setTheme(BurpTheme theme)
        {
            this.theme = theme;
            revalidate();
            repaint();
        }

        @Override
        public Dimension getPreferredSize()
        {
            return new Dimension(preferredWidth, preferredHeight);
        }

        @Override
        protected void paintComponent(Graphics graphics)
        {
            super.paintComponent(graphics);
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            int arc = hero ? 18 : 16;
            Shape clip = new RoundRectangle2D.Double(0, 0, getWidth(), getHeight() + (hero ? 0 : 16), arc, arc);
            g2.setClip(clip);
            ImageIcon preview = loadPreview(theme);
            if (preview != null && preview.getIconWidth() > 0)
            {
                paintCoverImage(g2, preview.getImage(), preview.getIconWidth(), preview.getIconHeight(), getWidth(), getHeight(), this);
            }
            else
            {
                BufferedImage wallpaper = loadWallpaper(theme);
                if (wallpaper != null)
                {
                    paintCoverImage(g2, wallpaper, getWidth(), getHeight());
                }
                else
                {
                    g2.setPaint(new GradientPaint(0, 0, theme.horizon, getWidth(), getHeight(), theme.base));
                    g2.fillRect(0, 0, getWidth(), getHeight());
                }
            }
            int topAlpha = hero ? 50 : selectedTheme ? 58 : hovering ? 74 : 92;
            int bottomAlpha = hero ? 132 : selectedTheme ? 112 : 136;
            g2.setPaint(new GradientPaint(0, 0, ThemeColors.alpha(theme.base.darker(), topAlpha), getWidth(), getHeight(), ThemeColors.alpha(theme.base.darker(), bottomAlpha)));
            g2.fillRect(0, 0, getWidth(), getHeight());
            paintDetailOverlay(g2);
            paintMotionOverlay(g2);
            paintThemeAtmosphere(g2);
            g2.setClip(null);
            g2.setColor(ThemeColors.alpha(selectedTheme ? theme.gold : theme.accent, hero ? 150 : selectedTheme ? 176 : 116));
            g2.setStroke(new BasicStroke(hero ? 2.0f : 1.4f));
            g2.drawRoundRect(1, 1, Math.max(1, getWidth() - 3), Math.max(1, getHeight() - 3), arc, arc);
            if (!hero)
            {
                g2.fillRoundRect(10, Math.max(0, getHeight() - 5), Math.max(1, getWidth() - 20), 3, 6, 6);
            }
            g2.dispose();
        }

        private void paintDetailOverlay(Graphics2D g2)
        {
            BufferedImage detail = loadDetail(theme);
            if (detail == null)
            {
                return;
            }
            int stripHeight = Math.min(getHeight(), hero ? 76 : 52);
            int y = Math.max(0, getHeight() - stripHeight);
            Graphics2D detailGraphics = (Graphics2D) g2.create(0, y, getWidth(), stripHeight);
            detailGraphics.setComposite(AlphaComposite.SrcOver.derive(hero ? 0.52f : selectedTheme ? 0.72f : hovering ? 0.62f : 0.54f));
            paintCoverImage(detailGraphics, detail, getWidth(), stripHeight);
            detailGraphics.dispose();
        }

        private void paintMotionOverlay(Graphics2D g2)
        {
            ImageIcon motion = loadMotion(theme);
            if (motion == null || motion.getIconWidth() <= 0)
            {
                return;
            }
            int boxWidth = Math.min(hero ? 168 : 126, Math.max(58, getWidth() / 3));
            int boxHeight = Math.min(hero ? 90 : 62, Math.max(36, getHeight() / 2));
            int x = Math.max(8, getWidth() - boxWidth - (hero ? 14 : 10));
            int y = Math.max(8, hero ? 14 : 10);
            Graphics2D motionGraphics = (Graphics2D) g2.create(x, y, boxWidth, boxHeight);
            motionGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Shape clip = new RoundRectangle2D.Double(0, 0, boxWidth, boxHeight, hero ? 16 : 12, hero ? 16 : 12);
            motionGraphics.setClip(clip);
            motionGraphics.setComposite(AlphaComposite.SrcOver.derive(hero ? 0.76f : selectedTheme ? 0.72f : hovering ? 0.64f : 0.58f));
            paintCoverImage(motionGraphics, motion.getImage(), motion.getIconWidth(), motion.getIconHeight(), boxWidth, boxHeight, this);
            motionGraphics.setComposite(AlphaComposite.SrcOver);
            motionGraphics.setColor(ThemeColors.alpha(theme.gold, hero ? 144 : 108));
            motionGraphics.setStroke(new BasicStroke(hero ? 1.6f : 1.2f));
            motionGraphics.drawRoundRect(0, 0, Math.max(1, boxWidth - 1), Math.max(1, boxHeight - 1), hero ? 16 : 12, hero ? 16 : 12);
            motionGraphics.dispose();
        }

        private void paintThemeAtmosphere(Graphics2D g2)
        {
            int width = getWidth();
            int height = getHeight();
            g2.setStroke(new BasicStroke(hero ? 2.0f : 1.2f));
            if (theme == BurpTheme.BLOOD_MOON)
            {
                g2.setColor(ThemeColors.alpha(theme.accent, hero ? 54 : 42));
                for (int index = 0; index < 4; index++)
                {
                    int y = 28 + index * Math.max(18, height / 5);
                    g2.drawArc(-width / 4, y, width + width / 2, height / 2, 196, 148);
                }
            }
            else if (theme == BurpTheme.KOROK_FOREST)
            {
                g2.setColor(ThemeColors.alpha(theme.gold, hero ? 42 : 30));
                for (int index = 0; index < 14; index++)
                {
                    int x = (index * 47 + width / 5) % Math.max(1, width);
                    int y = 16 + (index * 31) % Math.max(1, height - 28);
                    g2.fillOval(x, y, hero ? 5 : 4, hero ? 5 : 4);
                }
            }
            else if (theme == BurpTheme.ANCIENT_SLATE)
            {
                g2.setColor(ThemeColors.alpha(theme.text, hero ? 72 : 48));
                for (int index = 0; index < 6; index++)
                {
                    int y = 18 + index * Math.max(14, height / 6);
                    g2.drawLine(18, y, width - 22, y + (index % 2 == 0 ? 7 : -5));
                }
            }
            else if (theme == BurpTheme.GLOOM_DEPTHS)
            {
                g2.setColor(ThemeColors.alpha(theme.accent, hero ? 64 : 46));
                for (int index = 0; index < 5; index++)
                {
                    int x = 36 + index * Math.max(32, width / 6);
                    g2.drawOval(x, height / 3 + (index % 2) * 10, 36 + index * 8, 18 + index * 4);
                }
            }
        }
    }

    private final class GlassPanel extends JPanel
    {
        private static final long serialVersionUID = 1L;

        private GlassPanel(BorderLayout layout)
        {
            super(layout);
        }

        @Override
        protected void paintComponent(Graphics graphics)
        {
            super.paintComponent(graphics);
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(ThemeColors.alpha(currentTheme.base.darker(), 188));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
            g2.setColor(ThemeColors.alpha(currentTheme.accent, 86));
            g2.drawRoundRect(0, 0, Math.max(1, getWidth() - 1), Math.max(1, getHeight() - 1), 14, 14);
            g2.dispose();
        }
    }

    private static final class BadgeLabel extends JLabel
    {
        private static final long serialVersionUID = 1L;

        private BadgeLabel(String text)
        {
            super(text);
        }

        @Override
        protected void paintComponent(Graphics graphics)
        {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color background = getBackground();
            g2.setColor(background == null ? new Color(0, 0, 0, 0) : background);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 999, 999);
            g2.setColor(new Color(255, 255, 255, 44));
            g2.drawRoundRect(0, 0, Math.max(1, getWidth() - 1), Math.max(1, getHeight() - 1), 999, 999);
            g2.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class CardFlowPanel extends JPanel
    {
        private static final long serialVersionUID = 1L;

        private CardFlowPanel()
        {
            super(new FlowLayout(FlowLayout.LEFT, 16, 16));
        }

        @Override
        public Dimension getPreferredSize()
        {
            return wrappedSize();
        }

        @Override
        public Dimension getMinimumSize()
        {
            return wrappedSize();
        }

        private Dimension wrappedSize()
        {
            FlowLayout layout = (FlowLayout) getLayout();
            Insets insets = getInsets();
            int availableWidth = getParent() == null ? getWidth() : getParent().getWidth();
            if (availableWidth <= 0)
            {
                availableWidth = 920;
            }
            int maxRowWidth = Math.max(1, availableWidth - insets.left - insets.right - layout.getHgap() * 2);
            int rowWidth = 0;
            int rowHeight = 0;
            int height = insets.top + layout.getVgap();
            int widest = 0;
            for (Component component : getComponents())
            {
                if (!component.isVisible())
                {
                    continue;
                }
                Dimension size = component.getPreferredSize();
                int itemWidth = size.width;
                if (rowWidth > 0 && rowWidth + layout.getHgap() + itemWidth > maxRowWidth)
                {
                    height += rowHeight + layout.getVgap();
                    widest = Math.max(widest, rowWidth);
                    rowWidth = 0;
                    rowHeight = 0;
                }
                if (rowWidth > 0)
                {
                    rowWidth += layout.getHgap();
                }
                rowWidth += itemWidth;
                rowHeight = Math.max(rowHeight, size.height);
            }
            height += rowHeight + layout.getVgap() + insets.bottom;
            widest = Math.max(widest, rowWidth);
            return new Dimension(Math.max(availableWidth, widest + insets.left + insets.right), height);
        }
    }

    private static final class StudioPanel extends JPanel implements Scrollable
    {
        private static final long serialVersionUID = 1L;

        private StudioPanel(BorderLayout layout)
        {
            super(layout);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize()
        {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
        {
            return 24;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
        {
            return Math.max(96, visibleRect.height - 48);
        }

        @Override
        public boolean getScrollableTracksViewportWidth()
        {
            if (!(getParent() instanceof JViewport))
            {
                return true;
            }
            return ((JViewport) getParent()).getWidth() >= 360;
        }

        @Override
        public boolean getScrollableTracksViewportHeight()
        {
            return false;
        }
    }

    private static final class WallpaperPanel extends JPanel
    {
        private static final long serialVersionUID = 1L;

        private BurpTheme theme = BurpTheme.GREAT_PLATEAU;

        private WallpaperPanel()
        {
            setPreferredSize(new Dimension(900, 620));
        }

        @Override
        protected void paintComponent(Graphics graphics)
        {
            super.paintComponent(graphics);
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setColor(theme.base.darker());
            g2.fillRect(0, 0, getWidth(), getHeight());
            BufferedImage wallpaper = loadWallpaper(theme);
            if (wallpaper != null)
            {
                g2.setComposite(AlphaComposite.SrcOver.derive(storeWallpaperAlpha(theme) / 255.0f));
                paintCoverImage(g2, wallpaper, getWidth(), getHeight());
                g2.setComposite(AlphaComposite.SrcOver);
            }
            else
            {
                paintFallbackBackdrop(g2);
            }
            int washTop = theme == BurpTheme.BLOOD_MOON ? 150 : theme == BurpTheme.KOROK_FOREST ? 168 : 176;
            int washBottom = theme == BurpTheme.BLOOD_MOON ? 126 : theme == BurpTheme.KOROK_FOREST ? 142 : 150;
            g2.setPaint(new GradientPaint(0, 0, ThemeColors.alpha(theme.base.darker(), washTop), getWidth(), getHeight(), ThemeColors.alpha(theme.horizon.darker(), washBottom)));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(ThemeColors.alpha(theme.accent, 18));
            for (int y = 80; y < getHeight(); y += 150)
            {
                g2.drawArc(-160, y, getWidth() + 320, 190, 196, 148);
            }
            g2.dispose();
        }

        private int storeWallpaperAlpha(BurpTheme theme)
        {
            if (theme == BurpTheme.BLOOD_MOON)
            {
                return Math.min(theme.imageAlpha, 204);
            }
            if (theme == BurpTheme.KOROK_FOREST)
            {
                return Math.min(theme.imageAlpha, 188);
            }
            if (theme == BurpTheme.GLOOM_DEPTHS)
            {
                return Math.min(theme.imageAlpha, 192);
            }
            return Math.min(theme.imageAlpha, 184);
        }

        private void paintFallbackBackdrop(Graphics2D g2)
        {
            g2.setPaint(new GradientPaint(0, 0, theme.base, getWidth(), getHeight(), theme.horizon));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setStroke(new BasicStroke(2f));
            g2.setColor(ThemeColors.alpha(theme.accent, 36));
            g2.drawOval(62, Math.max(80, getHeight() - 270), 210, 210);
            Path2D ridge = new Path2D.Double();
            int baseline = getHeight() - 120;
            ridge.moveTo(0, getHeight());
            ridge.lineTo(0, baseline);
            int step = Math.max(90, getWidth() / 9);
            for (int x = 0; x <= getWidth() + step; x += step)
            {
                int peak = baseline - 60 - Math.abs((x * 37) % 95);
                ridge.lineTo(x + step / 2.0d, peak);
                ridge.lineTo(x + step, baseline - Math.abs((x * 19) % 42));
            }
            ridge.lineTo(getWidth(), getHeight());
            ridge.closePath();
            g2.setColor(ThemeColors.alpha(theme.base.darker(), 160));
            g2.fill(ridge);
        }
    }

    private static void paintCoverImage(Graphics2D g2, BufferedImage image, int targetWidth, int targetHeight)
    {
        paintCoverImage(g2, image, image.getWidth(), image.getHeight(), targetWidth, targetHeight, null);
    }

    private static void paintCoverImage(Graphics2D g2, Image image, int sourceWidth, int sourceHeight, int targetWidth, int targetHeight, Component observer)
    {
        double scale = Math.max(targetWidth / (double) sourceWidth, targetHeight / (double) sourceHeight);
        int width = (int) Math.ceil(sourceWidth * scale);
        int height = (int) Math.ceil(sourceHeight * scale);
        int x = (targetWidth - width) / 2;
        int y = (targetHeight - height) / 2;
        g2.drawImage(image, x, y, width, height, observer);
    }
}
