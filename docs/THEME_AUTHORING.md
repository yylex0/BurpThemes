# Theme Authoring Guide

This guide is for contributors adding a new BurpTheme visual theme.

## Asset Contract

The checked-in implementation requires three PNG assets and two GIF assets per active theme:

- Wallpaper: `1812x868`
- Avatar: `512x512`
- Detail strip: `640x180`
- Preview GIF: at least `240x100` and at least 8 frames
- Motion GIF: `180x96` and at least 12 frames

Use lowercase, hyphenated filenames:

```text
assets/wallpaper-my-theme.png
assets/avatar-my-theme.png
assets/preview-my-theme.gif
assets/detail-my-theme.png
assets/motion-my-theme.gif
```

`Great Plateau` intentionally keeps the legacy wallpaper name `temple-overworld.png` because it came from the older asset set.

Keep the static wallpaper, avatar, and detail strip resources. The wallpaper is still used by suite-wide background rendering, the avatar is still used by compact icons, the detail strip adds extra card/hero texture, and the GIF assets should be limited to the theme gallery/card experience. Use short local loops with representative first frames so disabled animation or slow image loading still leaves useful previews.

## Registry Entry

Add one enum constant to `src/burp/arcade/BurpTheme.java`.

The current constructor order is:

```text
label, slug, tagline, wallpaperResource, avatarResource, previewResource, detailResource, motionResource,
base, horizon, accent, gold, text, muted, button, buttonText,
imageAlpha, washAlpha
```

Template:

```java
MY_THEME(
    "My Theme",
    "my-theme",
    "short visual tagline",
    "/assets/wallpaper-my-theme.png",
    "/assets/avatar-my-theme.png",
    "/assets/preview-my-theme.gif",
    "/assets/detail-my-theme.png",
    "/assets/motion-my-theme.gif",
    new Color(8, 20, 28),      // base
    new Color(20, 70, 82),     // horizon
    new Color(80, 210, 190),   // accent
    new Color(245, 215, 120),  // gold
    new Color(232, 248, 240),  // text
    new Color(150, 215, 200),  // muted
    new Color(45, 90, 70),     // button
    new Color(252, 244, 196),  // buttonText
    238,                       // imageAlpha
    116                        // washAlpha
)
```

Use a stable slug. Once a theme ships, changing the slug can break saved user preferences.

## Generator Entry

Add the same slug to `tools/generate_theme_assets.py`.

The validator checks that generator slugs, core colors, and active asset paths match the Java registry. The `secondary` generator color maps to the Java `button` color.

The generator has hand-tuned wallpaper/avatar motifs for the shipped themes. New community slugs use a generic ruins-style wallpaper and geometric avatar by default, so a contributor can still generate usable draft assets from only a palette entry.

Minimal entry:

```python
"my-theme": {
    "wallpaper": "wallpaper-my-theme.png",
    "avatar": "avatar-my-theme.png",
    "preview": "preview-my-theme.gif",
    "detail": "detail-my-theme.png",
    "motion": "motion-my-theme.gif",
    "base": (8, 20, 28),
    "horizon": (20, 70, 82),
    "accent": (80, 210, 190),
    "motif": (80, 210, 190),
    "gold": (245, 215, 120),
    "secondary": (45, 90, 70),
    "particle": (150, 215, 200),
    "rune": (80, 210, 190),
    "shadow": (2, 8, 12),
},
```

## Local Checks

Run the validator before building:

```bash
python3 tools/validate_themes.py
python3 tools/validate_themes.py --list-themes
python3 tools/validate_themes.py --list-assets
```

Build the jar:

```bash
./build.sh
```

Inspect packaged assets:

```bash
jar tf dist/arcade-burp-community.jar | sort | grep '^assets/'
```

Current validation parses wallpaper, avatar, preview, detail, and motion resources and should report five active themes and twenty-five active assets. `--list-assets` includes `preview-*.gif`, `detail-*.png`, and `motion-*.gif`; the jar must contain exactly the registered PNG/GIF asset set, and stale preview/detail/motion files are rejected.

## Manual Burp Check

Load `dist/arcade-burp-community.jar` into a fresh Burp process and verify:

- The theme card appears in the `BurpTheme` tab.
- The card preview uses the new wallpaper.
- The theme card uses the registered GIF without blocking Swing repaint.
- The theme card shows the registered detail strip overlay.
- The hero and card previews show the registered motion GIF overlay.
- The avatar renders in the card and header when selected.
- Suite-wide mode applies readable colors to tabs, buttons, tables, trees, text areas, menus, popups, and dialogs.
- Unloading the extension removes the tab and restores Burp UI state.

## Common Validator Failures

- `Generator is missing active theme slug`: add the slug to `tools/generate_theme_assets.py`.
- `Generator lists inactive theme slug`: remove or rename a stale generator entry.
- `Generator ... color ... expected ...`: sync generator core colors with `BurpTheme.java`.
- `Wrong wallpaper size`: resize the wallpaper to `1812x868`.
- `Wrong avatar size`: resize the avatar to `512x512`.
- `Wrong detail size`: resize the detail strip to `640x180`.
- `Wrong motion size`: regenerate the motion GIF at `180x96`.
- `Inactive theme asset remains`: delete a stale `avatar-*`, `detail-*`, `motion-*`, `wallpaper-*`, or legacy theme PNG/GIF from `assets/`.
- `Generator does not list active asset`: sync generator-owned assets or adjust validation if the preview GIF is curated rather than generated.
- `Jar asset set does not match the active theme registry`: make sure build packaging includes every registered wallpaper, avatar, preview, detail, and motion asset, and excludes stale files.
