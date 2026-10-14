package net.feelix.stagecoach.client.ui;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.feelix.stagecoach.travel.Landscape;
import net.minecraft.client.color.world.FoliageColors;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public final class RoadScene {
    private RoadScene() {}

    private static final Identifier GRASS = new Identifier("minecraft", "block/grass_block_top");
    private static final Identifier MOSS = new Identifier("minecraft", "block/moss_block");
    private static final Identifier PATH = new Identifier("minecraft", "block/dirt_path_top");
    private static final Identifier COARSE = new Identifier("minecraft", "block/coarse_dirt");
    private static final Identifier GRAVEL = new Identifier("minecraft", "block/gravel");
    private static final Identifier OAK_LOG = new Identifier("minecraft", "block/oak_log");
    private static final Identifier SPRUCE_LOG = new Identifier("minecraft", "block/spruce_log");
    private static final Identifier BIRCH_LOG = new Identifier("minecraft", "block/birch_log");
    private static final Identifier OAK_LEAVES = new Identifier("minecraft", "block/oak_leaves");
    private static final Identifier SPRUCE_LEAVES = new Identifier("minecraft", "block/spruce_leaves");
    private static final Identifier BIRCH_LEAVES = new Identifier("minecraft", "block/birch_leaves");
    private static final Identifier PLANKS = new Identifier("minecraft", "block/oak_planks");
    private static final Identifier SUN = new Identifier("minecraft", "textures/environment/sun.png");
    private static final Identifier MOON = new Identifier("minecraft", "textures/environment/moon_phases.png");

    private static final int GRASS_TINT = 0xFF91BD59; // plains grass
    private static final int[] OAK_CROWN = {18, 22, 20, 12};
    private static final int[] SPRUCE_CROWN = {20, 16, 13, 9, 5};
    private static final int[] BIRCH_CROWN = {14, 18, 18, 14, 8};
    public static final float ROAD_SPEED = 1.6f;      // GUI px per tick for the nearest layer

    public static int roadTop(int h) {
        return (int) (h * 0.70f);
    }

    public static int roadBottom(int h) {
        return roadTop(h) + Math.max(26, (int) (h * 0.11f));
    }

    /** {@code skyAngle} 0 is noon, 0.5 midnight; {@code ticks} drives the scrolling. */
    public static void draw(DrawContext ctx, int w, int h, long seed, float ticks, float skyAngle, int moonPhase) {
        float cos = MathHelper.cos(skyAngle * MathHelper.TAU);
        float day = MathHelper.clamp(cos * 2f + 0.5f, 0f, 1f);
        float dusk = MathHelper.clamp((1f - Math.abs(cos) - 0.55f) / 0.45f, 0f, 1f) * (1f - Math.abs(day - 0.5f));
        float light = 0.32f + 0.68f * day;
        int horizon = (int) (h * 0.50f);

        int top = SpriteBatch.mix(SpriteBatch.mix(0xFF070B1E, 0xFF4F86D6, day), 0xFF30406E, dusk * 0.7f);
        int bottom = SpriteBatch.mix(SpriteBatch.mix(0xFF1B2646, 0xFFA8CCF0, day), 0xFFF09A55, dusk);
        ctx.fillGradient(0, 0, w, horizon + (int) (h * 0.12f), top, bottom);
        drawSunAndMoon(ctx, w, horizon, skyAngle, moonPhase);

        float offset = ticks * ROAD_SPEED;
        int haze = SpriteBatch.mix(0xFF7EA38A, bottom, 0.5f);
        int farBase = horizon + (int) (h * 0.10f), nearBase = horizon + (int) (h * 0.17f);
        int roadTop = roadTop(h), roadBottom = roadBottom(h);

        SpriteBatch.begin(ctx);
        hills(Landscape.LAYER_FAR_HILLS, SpriteBatch.sprite(GRASS), seed, offset * 0.12f, w, farBase, h * 0.07f, h * 0.20f, 150,
                roadTop, SpriteBatch.shade(haze, light));
        hills(Landscape.LAYER_NEAR_HILLS, SpriteBatch.sprite(MOSS), seed, offset * 0.3f, w, nearBase, h * 0.03f, h * 0.12f, 85,
                roadTop, SpriteBatch.shade(0xFFB4BCA4, light));
        int meadowTop = nearBase - 2;
        SpriteBatch.tile(SpriteBatch.sprite(GRASS), 0, meadowTop, w, roadTop - meadowTop, -offset * 0.55f, meadowTop,
                SpriteBatch.shade(GRASS_TINT, light * 0.92f));
        trees(seed, offset * 0.55f, w, roadTop - 4, light);
        road(seed, offset, w, h, roadTop, roadBottom, light);
        SpriteBatch.end();

        ctx.fill(0, roadTop, w, roadTop + 1, 0x50000000);
        ctx.fill(0, roadBottom - 1, w, roadBottom, 0x40000000);
        if (day < 1f) ctx.fill(0, 0, w, h, ((int) ((1f - day) * 70) << 24) | 0x0A1030); // a blue night wash
    }

    private static void drawSunAndMoon(DrawContext ctx, int w, int horizon, float skyAngle, int moonPhase) {
        RenderSystem.enableBlend();
        // additive, as the sky renders them
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        for (int body = 0; body < 2; body++) {
            float a = (skyAngle + body * 0.5f) * MathHelper.TAU;
            float up = MathHelper.cos(a);
            if (up < -0.15f) continue;
            int size = 44; // the disc is the middle quarter of the texture, as in the real sky
            // the arc peaks right of centre, clear of the sign
            int x = (int) (w * (0.72f + 0.26f * MathHelper.sin(a))) - size / 2;
            int y = (int) (horizon - up * horizon * 0.75f) - size / 2;
            if (body == 0) {
                ctx.drawTexture(SUN, x, y, 0, 0, size, size, size, size);
            } else {
                int col = moonPhase % 4, row = moonPhase / 4 % 2;
                ctx.drawTexture(MOON, x, y, col * size, row * size, size, size, size * 4, size * 2);
            }
        }
        RenderSystem.defaultBlendFunc();
    }

    /** 4px columns, each textured from its world position so the grain scrolls with it. */
    private static void hills(int layer, Sprite s, long seed, float offset, int w, int base, float minH, float maxH, double wavelength,
                              int bottom, int tint) {
        int firstCol = (int) Math.floor(offset / 4f);
        for (int col = firstCol; col * 4 - offset < w; col++) {
            float x0 = col * 4 - offset;
            double hgt = minH + Landscape.hill(seed, layer, col * 4.0, wavelength) * (maxH - minH);
            float yTop = (float) Math.floor(base - hgt);
            int u = Math.floorMod(col * 4, 16);
            for (float ty = (float) Math.floor(yTop / 16f) * 16f; ty < bottom; ty += 16f) {
                float y0 = Math.max(ty, yTop), y1 = Math.min(ty + 16f, bottom);
                if (y1 > y0) SpriteBatch.quad(s, x0, y0, x0 + 4, y1, u, y0 - ty, u + 4, y1 - ty, tint);
            }
        }
    }

    private static void trees(long seed, float offset, int w, int ground, float light) {
        int first = (int) Math.floor((offset - 40) / Landscape.TREE_CELL);
        int last = (int) Math.ceil((offset + w + 40) / Landscape.TREE_CELL);
        Sprite oakLog = SpriteBatch.sprite(OAK_LOG), spruceLog = SpriteBatch.sprite(SPRUCE_LOG), birchLog = SpriteBatch.sprite(BIRCH_LOG);
        Sprite oakLeaves = SpriteBatch.sprite(OAK_LEAVES), spruceLeaves = SpriteBatch.sprite(SPRUCE_LEAVES), birchLeaves = SpriteBatch.sprite(BIRCH_LEAVES);
        int bark = SpriteBatch.shade(0xFFFFFFFF, light);
        for (long cell = first; cell <= last; cell++) {
            if (!Landscape.hasTree(seed, cell)) continue;
            float cx = cell * Landscape.TREE_CELL + Landscape.treeOffset(seed, cell) - offset;
            int height = Landscape.treeHeight(seed, cell);
            int g = ground + (int) (Landscape.unit(seed, Landscape.LAYER_TREES + 50, cell) * 5);
            switch (Landscape.treeKind(seed, cell)) {
                case 0 -> { // oak: short trunk, round crown
                    int trunk = (int) (height * 0.42f);
                    SpriteBatch.tile(oakLog, cx - 2, g - trunk, 4, trunk, cx - 2, g, bark);
                    int leaf = SpriteBatch.shade(0xFF000000 | FoliageColors.getDefaultColor(), light);
                    crown(oakLeaves, cx, g - trunk + 3, OAK_CROWN, 6, leaf);
                }
                case 1 -> { // spruce: trunk to the top, stepped tiers
                    SpriteBatch.tile(spruceLog, cx - 1.5f, g - height, 3, height, cx, g, bark);
                    int leaf = SpriteBatch.shade(0xFF000000 | FoliageColors.getSpruceColor(), light);
                    crown(spruceLeaves, cx, g - 5, SPRUCE_CROWN, (height - 5) / 5, leaf);
                }
                default -> { // birch: tall pale trunk, narrow crown
                    int trunk = (int) (height * 0.55f);
                    SpriteBatch.tile(birchLog, cx - 1.5f, g - trunk, 3, trunk, cx, g, bark);
                    int leaf = SpriteBatch.shade(0xFF000000 | FoliageColors.getBirchColor(), light);
                    crown(birchLeaves, cx, g - trunk + 4, BIRCH_CROWN, 6, leaf);
                }
            }
        }
    }

    private static void crown(Sprite s, float cx, float bottomY, int[] widths, int rowH, int tint) {
        float y = bottomY;
        for (int width : widths) {
            y -= rowH;
            SpriteBatch.tile(s, cx - width / 2f, y, width, rowH + 1, cx, bottomY, tint);
        }
    }

    private static void road(long seed, float offset, int w, int h, int roadTop, int roadBottom, float light) {
        Sprite path = SpriteBatch.sprite(PATH), coarse = SpriteBatch.sprite(COARSE), gravel = SpriteBatch.sprite(GRAVEL);
        int ground = SpriteBatch.shade(0xFFFFFFFF, light);
        long firstTile = (long) Math.floor(offset / 16f);
        for (long tile = firstTile; tile * 16 - offset < w; tile++) {
            float x0 = tile * 16 - offset;
            SpriteBatch.tile(path, x0, roadTop, 16, roadBottom - roadTop, x0, roadTop, ground);
            // worn patches laid thinly over the path, not as hard squares
            int kind = Landscape.roadTile(seed, tile);
            if (kind != 0) {
                int wear = (kind == 1 ? 0x70000000 : 0x55000000) | (ground & 0xFFFFFF);
                SpriteBatch.tile(kind == 1 ? coarse : gravel, x0, roadTop + 2, 16, roadBottom - roadTop - 4, x0, roadTop, wear);
            }
        }
        SpriteBatch.tile(SpriteBatch.sprite(GRASS), 0, roadBottom, w, h - roadBottom, -offset, roadBottom, SpriteBatch.shade(GRASS_TINT, light * 0.85f));
        Sprite planks = SpriteBatch.sprite(PLANKS), log = SpriteBatch.sprite(OAK_LOG);
        long firstPost = (long) Math.floor((offset - 20) / Landscape.POST_CELL);
        for (long cell = firstPost; cell * Landscape.POST_CELL - offset < w + 20; cell++) {
            int kind = Landscape.roadside(seed, cell);
            if (kind == 0) continue;
            float x = cell * Landscape.POST_CELL + Landscape.roadsideOffset(seed, cell) - offset;
            if (kind == 1) {
                SpriteBatch.tile(planks, x, roadTop - 11, 3, 12, x, roadTop, ground);
                SpriteBatch.tile(planks, x - 5, roadTop - 8, 13, 2, x, roadTop, SpriteBatch.shade(0xFFD8C8B0, light));
            } else {
                SpriteBatch.tile(log, x, roadTop - 19, 3, 20, x, roadTop, ground);
                SpriteBatch.tile(planks, x - 6, roadTop - 18, 15, 7, x - 6, roadTop - 18, ground);
            }
        }
    }
}
