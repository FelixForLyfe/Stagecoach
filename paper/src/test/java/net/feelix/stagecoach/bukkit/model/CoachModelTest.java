package net.feelix.stagecoach.bukkit.model;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoachModelTest {
    private static final Path MOD = Path.of("../Stagecoach/common/src/main");

    @Test
    void generatedFromTheModsCurrentModel() throws Exception {
        byte[] geo = Files.readAllBytes(MOD.resolve("resources/assets/stagecoach/geo/block/stagecoach.geo.json"));
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(geo));
        assertEquals(sha, CoachModelData.MODEL_SHA1, "the mod's model changed: run python tools/generate_model.py");
        assertEquals(99, CoachModelData.CUBES.length);
    }

    @Test
    void cellsAreTheModsFootprintInTheModsOrder() throws Exception {
        String src = Files.readString(MOD.resolve("java/net/feelix/stagecoach/footprint/StagecoachFootprintData.java"));
        String cells = src.substring(src.indexOf("CELLS = {"), src.indexOf("};", src.indexOf("CELLS = {")));
        Matcher m = Pattern.compile("\\{(-?\\d+), (-?\\d+), (-?\\d+)}").matcher(cells);
        List<int[]> mod = new ArrayList<>();
        while (m.find()) mod.add(new int[] {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))});
        assertEquals(mod.size(), CoachModel.CELL_COUNT);
        for (int i = 0; i < mod.size(); i++) assertArrayEquals(mod.get(i), CoachModel.offset(i, 0), "cell " + i);
        assertArrayEquals(new int[] {0, 0, 0}, CoachModel.offset(CoachModel.CONTROLLER, 3));
        assertTrue(CoachModel.barrier(CoachModel.CONTROLLER), "the controller cell must hold a barrier");
    }

    @Test
    void everyCubeFitsInsideTheFootprintInAllFourFacings() {
        for (int turns = 0; turns < 4; turns++) {
            Set<String> cells = new HashSet<>();
            for (int i = 0; i < CoachModel.CELL_COUNT; i++) {
                int[] o = CoachModel.offset(i, turns);
                cells.add(o[0] + "," + o[1] + "," + o[2]);
            }
            for (int c = 0; c < CoachModelData.CUBES.length; c++) {
                Matrix4f mat = CoachModel.matrix(CoachModelData.CUBES[c], turns);
                assertTrue(mat.determinant() > 0, "cube " + c + " is mirrored");
                // The generator's own rule: every cell the cube's bounding box overlaps is part of the footprint.
                // The display stands at the controller's bottom centre: +0.5 in x and z to reach block space.
                float[] lo = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
                float[] hi = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
                for (int k = 0; k < 8; k++) {
                    Vector3f p = mat.transformPosition(new Vector3f(k & 1, (k >> 1) & 1, (k >> 2) & 1));
                    float[] v = {p.x + 0.5f, p.y, p.z + 0.5f};
                    for (int a = 0; a < 3; a++) {
                        lo[a] = Math.min(lo[a], v[a]);
                        hi[a] = Math.max(hi[a], v[a]);
                    }
                }
                for (int x = (int) Math.floor(lo[0] + 1e-4); x <= (int) Math.floor(hi[0] - 1e-4); x++) {
                    for (int y = (int) Math.floor(lo[1] + 1e-4); y <= (int) Math.floor(hi[1] - 1e-4); y++) {
                        for (int z = (int) Math.floor(lo[2] + 1e-4); z <= (int) Math.floor(hi[2] - 1e-4); z++) {
                            assertTrue(cells.contains(x + "," + y + "," + z), "turns " + turns + ", cube " + c
                                    + " reaches cell " + x + "," + y + "," + z + " outside the footprint");
                        }
                    }
                }
            }
        }
    }

    @Test
    void oneTurnIsMinusNinetyDegreesAboutYForCellsAndModelAlike() {
        Matrix4f r = new Matrix4f().rotationY((float) Math.toRadians(-90));
        for (int i = 0; i < CoachModel.CELL_COUNT; i++) {
            int[] o = CoachModel.offset(i, 0), t = CoachModel.offset(i, 1);
            Vector3f v = r.transformPosition(new Vector3f(o[0], o[1], o[2]));
            assertArrayEquals(new int[] {Math.round(v.x), Math.round(v.y), Math.round(v.z)}, t, "cell " + i);
        }
        assertArrayEquals(new int[] {0, -1}, CoachModel.front(0));
        assertArrayEquals(new int[] {1, 0}, CoachModel.front(1));
        assertEquals(180f, CoachModel.frontYaw(0));
        assertEquals(270f, CoachModel.frontYaw(1));
        assertEquals(0f, CoachModel.frontYaw(2));
        assertEquals(90f, CoachModel.frontYaw(3));
    }
}
