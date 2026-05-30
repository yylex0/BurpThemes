package burp.arcade;

import java.awt.Color;

enum BurpTheme
{
    KOROK_FOREST("Korok Forest", "korok-forest", "moss canopy calm", "/assets/wallpaper-korok-forest.png", "/assets/avatar-korok-forest.png", "/assets/preview-korok-forest.gif", "/assets/detail-korok-forest.png", "/assets/motion-korok-forest.gif", new Color(6, 24, 16), new Color(32, 82, 49), new Color(116, 205, 120), new Color(231, 196, 89), new Color(231, 248, 218), new Color(157, 210, 151), new Color(42, 91, 50), new Color(255, 245, 190), 228, 112),
    GREAT_PLATEAU("Great Plateau", "great-plateau", "wildlight plateau dusk", "/assets/temple-overworld.png", "/assets/avatar-great-plateau.png", "/assets/preview-great-plateau.gif", "/assets/detail-great-plateau.png", "/assets/motion-great-plateau.gif", new Color(8, 27, 35), new Color(24, 83, 82), new Color(105, 209, 187), new Color(245, 218, 127), new Color(226, 247, 221), new Color(150, 217, 201), new Color(51, 95, 55), new Color(252, 244, 196), 248, 112),
    ANCIENT_SLATE("Hebra Snowfield", "ancient-slate", "snow-blue alpine dusk", "/assets/wallpaper-ancient-slate.png", "/assets/avatar-ancient-slate.png", "/assets/preview-ancient-slate.gif", "/assets/detail-ancient-slate.png", "/assets/motion-ancient-slate.gif", new Color(5, 18, 36), new Color(29, 78, 120), new Color(118, 207, 255), new Color(247, 210, 128), new Color(239, 250, 255), new Color(176, 216, 232), new Color(27, 80, 126), new Color(248, 252, 255), 226, 136),
    BLOOD_MOON("Blood Moon", "blood-moon", "crimson ruins eclipse", "/assets/wallpaper-blood-moon.png", "/assets/avatar-blood-moon.png", "/assets/preview-blood-moon.gif", "/assets/detail-blood-moon.png", "/assets/motion-blood-moon.gif", new Color(12, 1, 8), new Color(82, 6, 27), new Color(236, 28, 50), new Color(214, 93, 42), new Color(255, 216, 207), new Color(217, 125, 133), new Color(101, 15, 35), new Color(255, 228, 210), 248, 84),
    GLOOM_DEPTHS("Gloom Depths", "gloom-depths", "violet depths pulse", "/assets/wallpaper-gloom-depths.png", "/assets/avatar-gloom-depths.png", "/assets/preview-gloom-depths.gif", "/assets/detail-gloom-depths.png", "/assets/motion-gloom-depths.gif", new Color(14, 6, 28), new Color(54, 22, 88), new Color(185, 91, 255), new Color(167, 227, 153), new Color(241, 228, 255), new Color(185, 154, 214), new Color(70, 28, 109), new Color(255, 240, 210), 238, 108);

    static final BurpTheme DEFAULT_THEME = KOROK_FOREST;

    final String label;
    final String slug;
    final String tagline;
    final String backgroundResource;
    final String avatarResource;
    final String previewResource;
    final String detailResource;
    final String motionResource;
    final Color base;
    final Color horizon;
    final Color accent;
    final Color gold;
    final Color text;
    final Color muted;
    final Color button;
    final Color buttonText;
    final int imageAlpha;
    final int washAlpha;

    BurpTheme(String label, String slug, String tagline, String backgroundResource, String avatarResource, String previewResource, String detailResource, String motionResource, Color base, Color horizon, Color accent, Color gold, Color text, Color muted, Color button, Color buttonText, int imageAlpha, int washAlpha)
    {
        this.label = label;
        this.slug = slug;
        this.tagline = tagline;
        this.backgroundResource = backgroundResource;
        this.avatarResource = avatarResource;
        this.previewResource = previewResource;
        this.detailResource = detailResource;
        this.motionResource = motionResource;
        this.base = base;
        this.horizon = horizon;
        this.accent = accent;
        this.gold = gold;
        this.text = text;
        this.muted = muted;
        this.button = button;
        this.buttonText = buttonText;
        this.imageAlpha = imageAlpha;
        this.washAlpha = washAlpha;
    }

    static BurpTheme fromLabel(String label)
    {
        return fromStoredValue(label);
    }

    static BurpTheme fromStoredValue(String value)
    {
        for (BurpTheme theme : values())
        {
            if (theme.slug.equals(value) || theme.label.equals(value) || theme.name().equals(value))
            {
                return theme;
            }
        }
        return DEFAULT_THEME;
    }
}
