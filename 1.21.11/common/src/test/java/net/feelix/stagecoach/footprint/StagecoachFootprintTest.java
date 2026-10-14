package net.feelix.stagecoach.footprint;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StagecoachFootprintTest {
    // run from the root (see fabric/build.gradle)
    private static final Path GEO = Path.of("common/src/main/resources/assets/stagecoach/geckolib/models/block/stagecoach.geo.json");

    /** A model edit without regenerating must not reach a jar with stale collision. */
    @Test
    void generatedDataMatchesTheModelFile() throws Exception {
        byte[] sha = MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(GEO));
        assertEquals(StagecoachFootprintData.MODEL_SHA1, HexFormat.of().formatHex(sha),
                "geo.json changed - run: python tools/generate_footprint.py");
    }

    @Test
    void dataIsWellFormed() {
        int[][] cells = StagecoachFootprintData.CELLS;
        assertArrayEquals(new int[] {0, 0, 0}, cells[Footprint.CONTROLLER], "index 0 must be the controller");
        assertEquals(cells.length, StagecoachFootprintData.BOXES.length);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < cells.length; i++) {
            assertTrue(seen.add(cells[i][0] + "," + cells[i][1] + "," + cells[i][2]), "duplicate cell " + i);
            assertTrue(cells[i][1] >= 0, "nothing may sit below the controller's floor (cell " + i + ")");
            if (i != Footprint.CONTROLLER) assertTrue(StagecoachFootprintData.BOXES[i].length > 0, "empty part cell " + i);
            for (int[] b : StagecoachFootprintData.BOXES[i]) {
                for (int a = 0; a < 3; a++) {
                    assertTrue(0 <= b[a] && b[a] < b[a + 3] && b[a + 3] <= 16, "bad box in cell " + i);
                }
            }
        }
    }

    @Test
    void fourQuarterTurnsAreIdentity() {
        for (int i = 0; i < Footprint.CELL_COUNT; i++) {
            assertArrayEquals(Footprint.offset(i, 0), Footprint.offset(i, 4));
            for (int[] b : StagecoachFootprintData.BOXES[i]) assertArrayEquals(b, Footprint.rotateBox(b, 4));
        }
    }

    /** Each facing's collision must equal the NORTH collision turned by the renderer's yaw. */
    @Test
    void collisionTurnsExactlyLikeTheRenderedModel() {
        for (int t = 0; t < 4; t++) {
            double yaw = Math.toRadians(Footprint.renderYawDegrees(t) - Footprint.renderYawDegrees(0));
            double cos = Math.cos(yaw), sin = Math.sin(yaw);
            for (int i = 0; i < Footprint.CELL_COUNT; i++) {
                int[] north = StagecoachFootprintData.CELLS[i];
                int[] off = Footprint.offset(i, t);
                int[][] turned = Footprint.boxes(i, t);
                int[][] src = StagecoachFootprintData.BOXES[i];
                for (int k = 0; k < src.length; k++) {
                    double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
                    for (int cx : new int[] {src[k][0], src[k][3]}) {
                        for (int cz : new int[] {src[k][2], src[k][5]}) {
                            // block-centre-relative pixels
                            double x = north[0] * 16 + cx - 8, z = north[2] * 16 + cz - 8;
                            double rx = x * cos + z * sin, rz = -x * sin + z * cos;
                            minX = Math.min(minX, rx); maxX = Math.max(maxX, rx);
                            minZ = Math.min(minZ, rz); maxZ = Math.max(maxZ, rz);
                        }
                    }
                    int[] b = turned[k];
                    String where = "facing " + t + " cell " + i + " box " + k;
                    assertEquals(minX, off[0] * 16 + b[0] - 8, 1e-9, where);
                    assertEquals(maxX, off[0] * 16 + b[3] - 8, 1e-9, where);
                    assertEquals(minZ, off[2] * 16 + b[2] - 8, 1e-9, where);
                    assertEquals(maxZ, off[2] * 16 + b[5] - 8, 1e-9, where);
                    assertEquals(north[1], off[1], where);
                    assertEquals(src[k][1], b[1], where);
                    assertEquals(src[k][4], b[4], where);
                }
            }
        }
    }

    /** Must match GeckoLib's own rotateBlock table: NORTH 0, EAST 270, SOUTH 180, WEST 90. */
    @Test
    void facingYawAgreesWithGeckoLibsTable() {
        int[] gecko = {0, 270, 180, 90}; // NORTH, EAST, SOUTH, WEST
        for (int t = 0; t < 4; t++) {
            float ours = Footprint.renderYawDegrees(t) - StagecoachFootprintData.BASE_YAW_DEGREES;
            assertEquals(gecko[t], Math.floorMod(Math.round(ours), 360), "turns " + t);
        }
    }
}
