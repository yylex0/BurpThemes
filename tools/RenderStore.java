package burp.arcade;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JFrame;

/**
 * Off-screen render harness: builds the real BurpThemeStorePanel with no-op
 * handlers, then paints it once per theme to a PNG so the store UI can be
 * visually verified without launching Burp. Requires a desktop session (not
 * headless) because it realizes peers via JFrame.pack() without showing.
 *
 * Usage: java -cp <builtJar>;<here> burp.arcade.RenderStore <outDir> [w] [h]
 */
public final class RenderStore
{
    public static void main(String[] args) throws Exception
    {
        File outDir = new File(args.length > 0 ? args[0] : "build/shots");
        int w = args.length > 1 ? Integer.parseInt(args[1]) : 1320;
        int h = args.length > 2 ? Integer.parseInt(args[2]) : 920;
        outDir.mkdirs();

        BurpThemeStorePanel.preloadWallpapers();
        BurpThemeStorePanel panel = new BurpThemeStorePanel(t -> {}, b -> {}, () -> {}, true);
        JComponent comp = panel.component();

        JFrame frame = new JFrame();
        frame.setUndecorated(true);
        frame.getContentPane().add(comp);
        frame.setSize(w, h);
        frame.pack();
        frame.setSize(w, h);
        frame.validate();

        for (BurpTheme theme : BurpTheme.values())
        {
            panel.themeChanged(theme);
            frame.validate();
            layoutDeep(comp);
            Thread.sleep(200);
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            comp.printAll(g);
            g.dispose();
            File out = new File(outDir, "store-" + theme.name().toLowerCase() + ".png");
            ImageIO.write(img, "png", out);
            System.out.println("wrote " + out.getAbsolutePath());
        }
        frame.dispose();
        System.exit(0);
    }

    private static void layoutDeep(java.awt.Component c)
    {
        c.doLayout();
        if (c instanceof java.awt.Container)
        {
            for (java.awt.Component child : ((java.awt.Container) c).getComponents())
            {
                layoutDeep(child);
            }
        }
    }
}
