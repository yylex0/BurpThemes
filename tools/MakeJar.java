import java.io.BufferedOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;

/**
 * Minimal jar packager for environments where the JDK ships without jar.exe
 * or the jdk.jartool module (e.g. Burp Suite's trimmed bundled runtime).
 *
 * Usage: java MakeJar <outJar> <manifestFile> <contentRoot> [contentRoot ...]
 *
 * Each contentRoot is walked recursively; every regular file becomes a jar
 * entry whose name is its path relative to that root, using '/' separators.
 * An existing META-INF/MANIFEST.MF inside a content root is ignored in favor
 * of the supplied manifestFile.
 */
public final class MakeJar
{
    public static void main(String[] args) throws Exception
    {
        if (args.length < 3)
        {
            System.err.println("Usage: MakeJar <outJar> <manifestFile> <contentRoot> [contentRoot ...]");
            System.exit(2);
        }
        Path outJar = Paths.get(args[0]);
        Path manifestFile = Paths.get(args[1]);

        Manifest manifest = new Manifest();
        try (InputStream in = Files.newInputStream(manifestFile))
        {
            manifest.read(in);
        }

        if (outJar.toAbsolutePath().getParent() != null)
        {
            Files.createDirectories(outJar.toAbsolutePath().getParent());
        }

        TreeMap<String, Path> entries = new TreeMap<>();
        for (int i = 2; i < args.length; i++)
        {
            Path root = Paths.get(args[i]);
            if (!Files.isDirectory(root))
            {
                continue;
            }
            try (Stream<Path> stream = Files.walk(root))
            {
                stream.filter(Files::isRegularFile).forEach(p -> {
                    String name = root.relativize(p).toString().replace(File.separatorChar, '/');
                    if (name.equalsIgnoreCase("META-INF/MANIFEST.MF"))
                    {
                        return;
                    }
                    entries.put(name, p);
                });
            }
        }

        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(outJar));
             JarOutputStream jos = new JarOutputStream(out, manifest))
        {
            Set<String> dirs = new HashSet<>();
            for (Map.Entry<String, Path> entry : entries.entrySet())
            {
                String name = entry.getKey();
                int idx = 0;
                while ((idx = name.indexOf('/', idx)) >= 0)
                {
                    String dir = name.substring(0, idx + 1);
                    if (dirs.add(dir))
                    {
                        jos.putNextEntry(new JarEntry(dir));
                        jos.closeEntry();
                    }
                    idx++;
                }
                jos.putNextEntry(new JarEntry(name));
                jos.write(Files.readAllBytes(entry.getValue()));
                jos.closeEntry();
            }
        }

        System.out.println("Wrote " + outJar.toAbsolutePath() + " (" + entries.size() + " entries)");
    }
}
