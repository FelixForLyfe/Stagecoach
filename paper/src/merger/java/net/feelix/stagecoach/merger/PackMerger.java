package net.feelix.stagecoach.merger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class PackMerger {
    private PackMerger() {}

    /** Only our files live here, so nothing of anyone else's pack is overwritten. */
    public static final String OURS = "assets/stagecoach/";
    /** Minecraft 1.21.11. */
    public static final int PACK_FORMAT = 75;

    public record Result(Path output, int added, int replaced, int kept, String sha1, List<String> notes) {}

    public static byte[] stagecoachPack() throws IOException {
        try (InputStream in = PackMerger.class.getResourceAsStream("/stagecoach-pack.zip")) {
            if (in == null) throw new IOException("stagecoach-pack.zip is missing from this jar");
            return in.readAllBytes();
        }
    }

    public static Result merge(byte[] stagecoach, Path source, Path output) throws IOException {
        if (source.toAbsolutePath().normalize().equals(output.toAbsolutePath().normalize())) {
            throw new IOException("The output must be a new file, not the pack you're adding to.");
        }
        Map<String, byte[]> theirs = Files.isDirectory(source) ? readFolder(source) : readZip(Files.readAllBytes(source));
        Map<String, byte[]> ours = readZip(stagecoach);
        List<String> notes = new ArrayList<>();
        if (!theirs.containsKey("pack.mcmeta")) {
            throw new IOException(source.getFileName() + " isn't a resource pack: it has no pack.mcmeta at its top level.");
        }
        checkFormat(theirs.get("pack.mcmeta"), notes);

        int replaced = 0, kept = 0, added = 0;
        Map<String, byte[]> out = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> e : theirs.entrySet()) {
            if (e.getKey().startsWith(OURS)) {
                replaced++;
                continue;
            }
            out.put(e.getKey(), e.getValue());
            kept++;
        }
        for (Map.Entry<String, byte[]> e : ours.entrySet()) {
            if (!e.getKey().startsWith(OURS)) continue; // their pack.mcmeta and pack.png stay theirs
            out.put(e.getKey(), e.getValue());
            added++;
        }
        if (replaced > 0) notes.add("An older copy of Stagecoach (" + replaced + " files) was in the pack and has been replaced.");

        Files.createDirectories(output.toAbsolutePath().getParent());
        byte[] zip = writeZip(out);
        Files.write(output, zip);
        return new Result(output, added, replaced, kept, sha1(zip), notes);
    }

    /** For servers that send several packs. */
    public static Result export(byte[] stagecoach, Path output) throws IOException {
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.write(output, stagecoach);
        return new Result(output, readZip(stagecoach).size(), 0, 0, sha1(stagecoach), List.of());
    }

    /** Read with patterns: this jar runs on a bare JVM with no JSON library. */
    static void checkFormat(byte[] mcmeta, List<String> notes) {
        String text = new String(mcmeta, StandardCharsets.UTF_8);
        Integer min = number(text, "min_format"), max = number(text, "max_format"), legacy = number(text, "pack_format");
        boolean covers = min != null && max != null ? min <= PACK_FORMAT && PACK_FORMAT <= max
                : legacy != null && legacy == PACK_FORMAT;
        if (!covers) {
            notes.add("Your pack.mcmeta doesn't say it supports Minecraft 1.21.11 (resource pack format " + PACK_FORMAT
                    + "), so players see the pack marked as made for another version. To fix it, set \"min_format\" to "
                    + PACK_FORMAT + " or lower and \"max_format\" to " + PACK_FORMAT + " or higher in pack.mcmeta.");
        }
    }

    /** The first number of {@code "key": 75} or {@code "key": [75, 0]}, or null. */
    private static Integer number(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\\[?\\s*(\\d+)").matcher(json);
        return m.find() ? Integer.valueOf(m.group(1)) : null;
    }

    static Map<String, byte[]> readZip(byte[] zip) throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new java.io.ByteArrayInputStream(zip))) {
            for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
                if (e.isDirectory()) continue;
                files.put(e.getName().replace('\\', '/'), in.readAllBytes());
            }
        }
        return unwrap(files);
    }

    private static Map<String, byte[]> readFolder(Path dir) throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.filter(Files::isRegularFile).sorted().toList()) {
                files.put(dir.relativize(p).toString().replace('\\', '/'), Files.readAllBytes(p));
            }
        }
        return unwrap(files);
    }

    /** "MyPack/pack.mcmeta" (a pack zipped with its folder) becomes "pack.mcmeta". */
    private static Map<String, byte[]> unwrap(Map<String, byte[]> files) {
        if (files.containsKey("pack.mcmeta")) return files;
        String prefix = null;
        for (String name : files.keySet()) {
            if (name.endsWith("/pack.mcmeta") && name.indexOf('/') == name.length() - "/pack.mcmeta".length()) {
                prefix = name.substring(0, name.length() - "pack.mcmeta".length());
                break;
            }
        }
        if (prefix == null) return files;
        Map<String, byte[]> out = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            if (e.getKey().startsWith(prefix)) out.put(e.getKey().substring(prefix.length()), e.getValue());
        }
        return out;
    }

    private static byte[] writeZip(Map<String, byte[]> files) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> e : files.entrySet()) {
                ZipEntry entry = new ZipEntry(e.getKey());
                entry.setTime(0L); // same bytes in, same zip out: the SHA-1 only changes when the content does
                zip.putNextEntry(entry);
                zip.write(e.getValue());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    static String sha1(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
