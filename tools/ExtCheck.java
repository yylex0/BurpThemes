/**
 * Smoke check: confirms the built extension's entry-point class loads against
 * the real Montoya API on the classpath, implements BurpExtension, and can see
 * its packaged theme assets. Run with both burpsuite.jar and the built jar on
 * the classpath. Does not start Burp.
 */
public final class ExtCheck
{
    public static void main(String[] args) throws Exception
    {
        Class<?> ext = Class.forName("burp.arcade.BurpThemeExtension");
        Class<?> iface = Class.forName("burp.api.montoya.BurpExtension");
        System.out.println("entry class loaded: " + ext.getName());
        System.out.println("implements BurpExtension: " + iface.isAssignableFrom(ext));
        ext.getDeclaredConstructor().newInstance();
        System.out.println("no-arg construct: ok");

        String[] assets = {
            "assets/temple-overworld.png",
            "assets/avatar-great-plateau.png",
            "assets/preview-great-plateau.gif",
            "assets/detail-great-plateau.png",
            "assets/motion-great-plateau.gif"
        };
        int found = 0;
        for (String a : assets)
        {
            boolean present = ext.getClassLoader().getResourceAsStream(a) != null;
            if (present) { found++; } else { System.out.println("MISSING asset: " + a); }
        }
        System.out.println("sample assets present: " + found + "/" + assets.length);
    }
}
