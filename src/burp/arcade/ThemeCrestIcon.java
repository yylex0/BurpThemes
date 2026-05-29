package burp.arcade;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

final class ThemeCrestIcon implements Icon
{
    private static final Map<String, BufferedImage> AVATARS = new LinkedHashMap<>();

    private final BufferedImage image;

    ThemeCrestIcon(BurpTheme theme, int size)
    {
        image = loadAvatar(theme, size);
        if (image == null)
        {
            throw new IllegalStateException("BurpTheme avatar render failed");
        }
    }

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y)
    {
        graphics.drawImage(image, x, y, null);
    }

    @Override
    public int getIconWidth()
    {
        return image.getWidth();
    }

    @Override
    public int getIconHeight()
    {
        return image.getHeight();
    }

    private BufferedImage render(BurpTheme theme, int size)
    {
        BufferedImage icon = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = icon.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int pad = Math.max(3, size / 12);
        int inner = size - pad * 2;
        g2.setPaint(new GradientPaint(0, 0, alpha(theme.horizon, 230), size, size, alpha(theme.base, 240)));
        g2.fillOval(pad, pad, inner, inner);
        g2.setStroke(new BasicStroke(Math.max(1.3f, size / 22.0f)));
        g2.setColor(alpha(theme.gold, 215));
        g2.drawOval(pad + 1, pad + 1, inner - 2, inner - 2);
        g2.setColor(alpha(theme.accent, 185));
        int cx = size / 2;
        int cy = size / 2;
        if (theme == BurpTheme.ANCIENT_SLATE)
        {
            for (int angle = 0; angle < 180; angle += 45)
            {
                int x = (int) (Math.cos(Math.toRadians(angle)) * inner * 0.32d);
                int y = (int) (Math.sin(Math.toRadians(angle)) * inner * 0.32d);
                g2.drawLine(cx - x, cy - y, cx + x, cy + y);
            }
            Path2D ice = new Path2D.Double();
            ice.moveTo(cx, cy - inner * 0.30d);
            ice.lineTo(cx + inner * 0.17d, cy);
            ice.lineTo(cx, cy + inner * 0.30d);
            ice.lineTo(cx - inner * 0.17d, cy);
            ice.closePath();
            g2.fill(ice);
            g2.setColor(alpha(theme.gold, 180));
            g2.fillOval(cx - inner / 12, cy - inner / 12, inner / 6, inner / 6);
        }
        else if (theme == BurpTheme.KOROK_FOREST)
        {
            Path2D leaf = new Path2D.Double();
            leaf.moveTo(cx, pad + 5);
            leaf.curveTo(size - pad, cy - 3, cx + 6, size - pad, cx, size - pad - 3);
            leaf.curveTo(cx - 6, size - pad, pad, cy - 3, cx, pad + 5);
            g2.fill(leaf);
            g2.setColor(alpha(theme.base, 185));
            g2.drawLine(cx, pad + 9, cx, size - pad - 8);
        }
        else if (theme == BurpTheme.BLOOD_MOON)
        {
            g2.setColor(alpha(theme.accent, 210));
            g2.fillOval(cx - inner / 4, cy - inner / 4, inner / 2, inner / 2);
            g2.setColor(alpha(theme.gold, 175));
            g2.fillOval(cx - inner / 8, cy - inner / 5, inner / 4, inner / 4);
        }
        else if (theme == BurpTheme.GLOOM_DEPTHS)
        {
            Path2D shard = new Path2D.Double();
            shard.moveTo(cx, cy - inner * 0.34d);
            shard.lineTo(cx + inner * 0.28d, cy - inner * 0.05d);
            shard.lineTo(cx + inner * 0.12d, cy + inner * 0.32d);
            shard.lineTo(cx, cy + inner * 0.20d);
            shard.lineTo(cx - inner * 0.12d, cy + inner * 0.32d);
            shard.lineTo(cx - inner * 0.28d, cy - inner * 0.05d);
            shard.closePath();
            g2.fill(shard);
            g2.setColor(alpha(theme.gold, 190));
            g2.fillOval(cx - inner / 10, cy - inner / 10, inner / 5, inner / 5);
            g2.setColor(alpha(theme.accent, 150));
            g2.drawArc(cx - inner / 3, cy, inner * 2 / 3, inner / 3, 205, 130);
        }
        else
        {
            Path2D mountain = new Path2D.Double();
            mountain.moveTo(pad + inner * 0.18d, size - pad - inner * 0.25d);
            mountain.lineTo(cx, pad + inner * 0.25d);
            mountain.lineTo(size - pad - inner * 0.16d, size - pad - inner * 0.25d);
            mountain.closePath();
            g2.fill(mountain);
            g2.setColor(alpha(theme.gold, 185));
            g2.fillOval(size - pad - inner / 4, pad + inner / 6, inner / 5, inner / 5);
        }
        g2.dispose();
        return icon;
    }

    private BufferedImage loadAvatar(BurpTheme theme, int size)
    {
        BufferedImage source = avatarSource(theme);
        if (source == null)
        {
            return render(theme, size);
        }
        BufferedImage badge = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = badge.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int shadowPad = Math.max(1, size / 18);
        int ring = Math.max(1, size / 18);
        int avatarPad = Math.max(2, size / 12);
        int avatarSize = Math.max(1, size - avatarPad * 2);
        int arc = Math.max(4, size / 5);
        g2.setColor(alpha(theme.base.darker(), 150));
        g2.fillRoundRect(shadowPad, shadowPad + Math.max(1, size / 22), size - shadowPad * 2, size - shadowPad * 2, arc, arc);
        g2.setColor(alpha(theme.gold, 220));
        g2.fillRoundRect(0, 0, size, size, arc, arc);
        g2.setColor(alpha(theme.accent, 190));
        g2.fillRoundRect(ring, ring, size - ring * 2, size - ring * 2, arc, arc);
        g2.setClip(new RoundRectangle2D.Double(avatarPad, avatarPad, avatarSize, avatarSize, Math.max(3, arc - avatarPad), Math.max(3, arc - avatarPad)));
        g2.drawImage(source, avatarPad, avatarPad, avatarSize, avatarSize, null);
        g2.setClip(null);
        g2.setColor(alpha(theme.gold, 185));
        g2.setStroke(new BasicStroke(Math.max(1.0f, size / 24.0f)));
        g2.drawRoundRect(ring, ring, size - ring * 2 - 1, size - ring * 2 - 1, arc, arc);
        g2.dispose();
        return badge;
    }

    private BufferedImage avatarSource(BurpTheme theme)
    {
        BufferedImage existing = AVATARS.get(theme.avatarResource);
        if (existing != null)
        {
            return existing;
        }
        try
        {
            java.net.URL resource = ThemeCrestIcon.class.getResource(theme.avatarResource);
            if (resource == null && theme.avatarResource.startsWith("/"))
            {
                resource = ThemeCrestIcon.class.getClassLoader().getResource(theme.avatarResource.substring(1));
            }
            BufferedImage loaded = resource == null ? null : ImageIO.read(resource);
            if (loaded == null && Boolean.getBoolean("burptheme.devAssets"))
            {
                loaded = readRelativeFileFallbackImage(theme);
            }
            if (loaded != null)
            {
                AVATARS.put(theme.avatarResource, loaded);
            }
            return loaded;
        }
        catch (IOException | IllegalArgumentException exception)
        {
            return null;
        }
    }

    private BufferedImage readRelativeFileFallbackImage(BurpTheme theme) throws IOException
    {
        String name = theme.avatarResource.substring(theme.avatarResource.lastIndexOf('/') + 1);
        File candidate = new File(devAssetsDir(), name);
        return candidate.isFile() ? ImageIO.read(candidate) : null;
    }

    static void clearAvatarCache()
    {
        for (BufferedImage avatar : AVATARS.values())
        {
            avatar.flush();
        }
        AVATARS.clear();
    }

    private File devAssetsDir()
    {
        String configured = System.getProperty("burptheme.devAssetsDir");
        return configured == null || configured.trim().isEmpty() ? new File("assets") : new File(configured);
    }

    private Color alpha(Color color, int alpha)
    {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }
}
