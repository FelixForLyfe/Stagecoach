package net.feelix.stagecoach.merger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackMergerTest {
    private static final String MCMETA_OK = "{\"pack\":{\"description\":\"x\",\"min_format\":[70,0],\"max_format\":80}}";

    @TempDir
    Path dir;

    private static byte[] zip(Map<String, String> files) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, String> e : files.entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static Map<String, String> theirPack(String mcmeta) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("pack.mcmeta", mcmeta);
        m.put("pack.png", "their icon");
        m.put("assets/minecraft/textures/block/stone.png", "their stone");
        m.put("assets/stagecoach/models/old_leftover.json", "from an older Stagecoach");
        return m;
    }

    @Test
    void keepsTheirFilesAndReplacesOnlyStagecoach() throws Exception {
        byte[] ours = PackMerger.stagecoachPack();
        Path source = dir.resolve("server.zip");
        Files.write(source, zip(theirPack(MCMETA_OK)));
        PackMerger.Result r = PackMerger.merge(ours, source, dir.resolve("out.zip"));
        Map<String, byte[]> out = PackMerger.readZip(Files.readAllBytes(r.output()));
        assertEquals("their stone", new String(out.get("assets/minecraft/textures/block/stone.png"), StandardCharsets.UTF_8));
        assertEquals(MCMETA_OK, new String(out.get("pack.mcmeta"), StandardCharsets.UTF_8), "their pack.mcmeta stays");
        assertEquals("their icon", new String(out.get("pack.png"), StandardCharsets.UTF_8), "their icon stays");
        assertFalse(out.containsKey("assets/stagecoach/models/old_leftover.json"), "the old Stagecoach copy is gone");
        Map<String, byte[]> stagecoach = PackMerger.readZip(ours);
        for (Map.Entry<String, byte[]> e : stagecoach.entrySet()) {
            if (e.getKey().startsWith(PackMerger.OURS)) assertArrayEquals(e.getValue(), out.get(e.getKey()), e.getKey());
        }
        assertEquals(1, r.replaced());
        assertTrue(r.notes().stream().noneMatch(n -> n.contains("doesn't say it supports")), r.notes().toString());
        assertEquals(PackMerger.sha1(Files.readAllBytes(r.output())), r.sha1());
    }

    @Test
    void theSameInputGivesTheSameZip() throws Exception {
        byte[] ours = PackMerger.stagecoachPack();
        Path source = dir.resolve("server.zip");
        Files.write(source, zip(theirPack(MCMETA_OK)));
        String a = PackMerger.merge(ours, source, dir.resolve("a.zip")).sha1();
        String b = PackMerger.merge(ours, source, dir.resolve("b.zip")).sha1();
        assertEquals(a, b, "a stable hash, so clients don't re-download an unchanged pack");
    }

    @Test
    void aPackZippedInsideItsFolderAndAPackFolderBothWork() throws Exception {
        byte[] ours = PackMerger.stagecoachPack();
        Map<String, String> wrapped = new LinkedHashMap<>();
        theirPack(MCMETA_OK).forEach((k, v) -> wrapped.put("MyPack/" + k, v));
        Path zipped = dir.resolve("wrapped.zip");
        Files.write(zipped, zip(wrapped));
        Map<String, byte[]> out = PackMerger.readZip(Files.readAllBytes(PackMerger.merge(ours, zipped, dir.resolve("o1.zip")).output()));
        assertTrue(out.containsKey("pack.mcmeta") && out.containsKey("assets/stagecoach/items/oak_stagecoach.json"));

        Path folder = dir.resolve("folder");
        for (Map.Entry<String, String> e : theirPack(MCMETA_OK).entrySet()) {
            Path p = folder.resolve(e.getKey());
            Files.createDirectories(p.getParent());
            Files.writeString(p, e.getValue());
        }
        out = PackMerger.readZip(Files.readAllBytes(PackMerger.merge(ours, folder, dir.resolve("o2.zip")).output()));
        assertTrue(out.containsKey("assets/minecraft/textures/block/stone.png"));
        assertTrue(out.containsKey("assets/stagecoach/font/ui.json"));
    }

    @Test
    void refusesANonPackAndOverwritingTheInput() throws Exception {
        Path source = dir.resolve("notapack.zip");
        Files.write(source, zip(Map.of("readme.txt", "hi")));
        assertThrows(IOException.class, () -> PackMerger.merge(PackMerger.stagecoachPack(), source, dir.resolve("o.zip")));
        Path pack = dir.resolve("pack.zip");
        Files.write(pack, zip(theirPack(MCMETA_OK)));
        assertThrows(IOException.class, () -> PackMerger.merge(PackMerger.stagecoachPack(), pack, pack));
    }

    @Test
    void warnsWhenTheirPackDoesNotCover1_21_11() {
        List<String> notes = new ArrayList<>();
        PackMerger.checkFormat("{\"pack\":{\"pack_format\":34,\"description\":\"old\"}}".getBytes(StandardCharsets.UTF_8), notes);
        assertEquals(1, notes.size());
        notes.clear();
        PackMerger.checkFormat("{\"pack\":{\"min_format\":75,\"max_format\":[75,0]}}".getBytes(StandardCharsets.UTF_8), notes);
        assertTrue(notes.isEmpty(), notes.toString());
    }

    @Test
    void theCommandLineWritesNextToTheInputByDefault() throws Exception {
        Path source = dir.resolve("My Server Pack.zip");
        Files.write(source, zip(theirPack(MCMETA_OK)));
        assertEquals(0, MergerApp.cli(new String[] {source.toString()}));
        assertTrue(Files.exists(dir.resolve("My Server Pack + Stagecoach.zip")));
        assertEquals(0, MergerApp.cli(new String[] {"--export", dir.resolve("plain.zip").toString()}));
        assertArrayEquals(PackMerger.stagecoachPack(), Files.readAllBytes(dir.resolve("plain.zip")));
    }
}
