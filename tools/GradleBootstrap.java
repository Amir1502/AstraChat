import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** Source-only bootstrap: downloads the official pinned Gradle distribution, never a script.
 * This is deliberately not represented as the official Gradle Wrapper JAR. */
class GradleBootstrap {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath();
        Properties config = new Properties();
        try (var in = Files.newInputStream(root.resolve("gradle/wrapper/gradle-wrapper.properties"))) { config.load(in); }
        String version = "9.3.1";
        Path home = Path.of(System.getenv().getOrDefault("GRADLE_USER_HOME", System.getProperty("user.home") + "/.gradle"));
        Path cache = home.resolve("astra-bootstrap");
        Files.createDirectories(cache);
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        Path executable = cache.resolve("gradle-" + version + "/bin/gradle" + (windows ? ".bat" : ""));
        try (var channel = FileChannel.open(cache.resolve("install.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = channel.lock()) {
            if (!Files.exists(executable)) {
                Path zip = Files.createTempFile(cache, "gradle-", ".zip");
                try {
                    System.out.println("Downloading pinned Gradle " + version + " from " + URI.create(config.getProperty("distributionUrl")).getHost());
                    String targetUrl = config.getProperty("distributionUrl");
                    while (true) {
                        var conn = (HttpURLConnection) URI.create(targetUrl).toURL().openConnection();
                        conn.setConnectTimeout(15000);
                        conn.setReadTimeout(60000);
                        conn.setInstanceFollowRedirects(false);
                        int status = conn.getResponseCode();
                        if (status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_MOVED_TEMP || status == 307 || status == 308) {
                            String location = conn.getHeaderField("Location");
                            conn.disconnect();
                            if (location == null) throw new IOException("Redirect without Location header");
                            targetUrl = URI.create(targetUrl).resolve(location).toString();
                            continue;
                        }
                        if (status != HttpURLConnection.HTTP_OK) {
                            throw new IOException("HTTP " + status + " downloading " + targetUrl);
                        }
                        try (var input = conn.getInputStream()) { Files.copy(input, zip, StandardCopyOption.REPLACE_EXISTING); }
                        conn.disconnect();
                        break;
                    }
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    try (var in = Files.newInputStream(zip); var checked = new DigestInputStream(in, digest)) { checked.transferTo(OutputStream.nullOutputStream()); }
                    if (!HexFormat.of().formatHex(digest.digest()).equals(config.getProperty("distributionSha256Sum"))) {
                        throw new SecurityException("Gradle distribution SHA-256 mismatch; nothing executed");
                    }
                    Path staging = Files.createTempDirectory(cache, "install-");
                    try (var input = new ZipInputStream(Files.newInputStream(zip))) {
                        ZipEntry entry;
                        while ((entry = input.getNextEntry()) != null) {
                            Path target = staging.resolve(entry.getName()).normalize();
                            if (!target.startsWith(staging)) throw new SecurityException("Unsafe ZIP entry");
                            if (entry.isDirectory()) Files.createDirectories(target);
                            else { Files.createDirectories(target.getParent()); Files.copy(input, target); }
                        }
                    }
                    Files.move(staging.resolve("gradle-" + version), cache.resolve("gradle-" + version));
                    Files.delete(staging);
                    if (!windows && !executable.toFile().setExecutable(true)) throw new IOException("Cannot set executable bit");
                } finally { Files.deleteIfExists(zip); }
            }
        }
        List<String> command = new ArrayList<>();
        if (windows) { command.add("cmd.exe"); command.add("/c"); }
        command.add(executable.toString());
        command.addAll(Arrays.asList(args).subList(1, args.length));
        System.exit(new ProcessBuilder(command).directory(root.toFile()).inheritIO().start().waitFor());
    }
}
