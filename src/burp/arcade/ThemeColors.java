package burp.arcade;

import java.awt.Color;

final class ThemeColors
{
    private ThemeColors()
    {
    }

    static Color alpha(Color color, int alpha)
    {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    static Color opaque(Color color)
    {
        return alpha(color, 255);
    }

    static Color transparent(Color color)
    {
        return alpha(color, 0);
    }

    static String rgb(Color color)
    {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }
}
