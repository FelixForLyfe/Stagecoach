package net.feelix.stagecoach.client.ui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.feelix.stagecoach.mixin.GuiGraphicsExtractorAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

public final class SpriteBatch {
    private SpriteBatch() {}

    private static @Nullable GuiGraphicsExtractor graphics;
    private static TextureSetup atlas;

    private static TextureAtlas blocks() {
        return (TextureAtlas) Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
    }

    public static TextureAtlasSprite sprite(Identifier id) {
        return blocks().getSprite(id);
    }

    public static void begin(GuiGraphicsExtractor g) {
        graphics = g;
        TextureAtlas blocks = blocks();
        atlas = TextureSetup.singleTexture(blocks.getTextureView(), blocks.getSampler());
    }

    public static void end() {
        graphics = null;
    }

    /** A whole sprite from any atlas with its own pipeline; works outside begin/end. */
    public static void whole(GuiGraphicsExtractor g, RenderPipeline pipeline, TextureAtlas atlasTexture, TextureAtlasSprite s,
                             float x0, float y0, float x1, float y1, int argb) {
        TextureSetup texture = TextureSetup.singleTexture(atlasTexture.getTextureView(), atlasTexture.getSampler());
        renderState(g).addGuiElement(new Quad(pipeline, texture, new Matrix3x2f(g.pose()), x0, y0, x1, y1,
                s.getU0(), s.getV0(), s.getU1(), s.getV1(), argb, false, null));
    }

    private static GuiRenderState renderState(GuiGraphicsExtractor g) {
        return ((GuiGraphicsExtractorAccessor) g).stagecoach$guiRenderState();
    }

    /** UVs are in the sprite's own 0..16 texel grid, whatever its real resolution. */
    public static void quad(TextureAtlasSprite s, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, int argb) {
        submit(s, x0, y0, x1, y1, u0, v0, u1, v1, argb, false);
    }

    /** The texture turned a quarter: screen x runs along its v. */
    public static void quadTurned(TextureAtlasSprite s, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, int argb) {
        submit(s, x0, y0, x1, y1, u0, v0, u1, v1, argb, true);
    }

    private static void submit(TextureAtlasSprite s, float x0, float y0, float x1, float y1,
                               float u0, float v0, float u1, float v1, int argb, boolean turned) {
        GuiGraphicsExtractor g = graphics;
        if (g == null || x1 <= x0 || y1 <= y0) return;
        // Sprite#getU/V take 0..1 of the sprite; ours are texels 0..16.
        float su0 = s.getU(u0 / 16f), su1 = s.getU(u1 / 16f), sv0 = s.getV(v0 / 16f), sv1 = s.getV(v1 / 16f);
        // no scissor: nothing drawn with this sits inside a clipped region
        renderState(g).addGuiElement(new Quad(RenderPipelines.GUI_TEXTURED, atlas, new Matrix3x2f(g.pose()),
                x0, y0, x1, y1, su0, sv0, su1, sv1, argb, turned, null));
    }

    /** Repeats at 1:1 with the grid anchored at (ax, ay), so a pattern can scroll on its own. */
    public static void tile(TextureAtlasSprite s, float x, float y, float w, float h, float ax, float ay, int argb) {
        if (w <= 0 || h <= 0) return;
        float x1 = x + w, y1 = y + h;
        float startX = ax + (float) Math.floor((x - ax) / 16f) * 16f;
        float startY = ay + (float) Math.floor((y - ay) / 16f) * 16f;
        for (float ty = startY; ty < y1; ty += 16f) {
            float qy0 = Math.max(ty, y), qy1 = Math.min(ty + 16f, y1);
            if (qy1 <= qy0) continue;
            for (float tx = startX; tx < x1; tx += 16f) {
                float qx0 = Math.max(tx, x), qx1 = Math.min(tx + 16f, x1);
                if (qx1 <= qx0) continue;
                quad(s, qx0, qy0, qx1, qy1, qx0 - tx, qy0 - ty, qx1 - tx, qy1 - ty, argb);
            }
        }
    }

    public static int shade(int argb, float f) {
        int r = Math.min(255, (int) (((argb >> 16) & 0xFF) * f));
        int g = Math.min(255, (int) (((argb >> 8) & 0xFF) * f));
        int b = Math.min(255, (int) ((argb & 0xFF) * f));
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    public static int mix(int c0, int c1, float t) {
        int r = (int) (((c0 >> 16) & 0xFF) * (1 - t) + ((c1 >> 16) & 0xFF) * t);
        int g = (int) (((c0 >> 8) & 0xFF) * (1 - t) + ((c1 >> 8) & 0xFF) * t);
        int b = (int) ((c0 & 0xFF) * (1 - t) + (c1 & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Float corners: vanilla's BlitRenderState only takes whole GUI pixels. */
    private record Quad(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2f pose,
                        float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1,
                        int color, boolean turned, @Nullable ScreenRectangle scissorArea) implements GuiElementRenderState {
        @Override
        public void buildVertices(VertexConsumer consumer) {
            if (turned) {
                consumer.addVertexWith2DPose(pose, x0, y0).setUv(u0, v0).setColor(color);
                consumer.addVertexWith2DPose(pose, x0, y1).setUv(u1, v0).setColor(color);
                consumer.addVertexWith2DPose(pose, x1, y1).setUv(u1, v1).setColor(color);
                consumer.addVertexWith2DPose(pose, x1, y0).setUv(u0, v1).setColor(color);
            } else {
                consumer.addVertexWith2DPose(pose, x0, y0).setUv(u0, v0).setColor(color);
                consumer.addVertexWith2DPose(pose, x0, y1).setUv(u0, v1).setColor(color);
                consumer.addVertexWith2DPose(pose, x1, y1).setUv(u1, v1).setColor(color);
                consumer.addVertexWith2DPose(pose, x1, y0).setUv(u1, v0).setColor(color);
            }
        }

        @Override
        public @Nullable ScreenRectangle bounds() {
            int bx0 = (int) Math.floor(x0), by0 = (int) Math.floor(y0);
            ScreenRectangle b = new ScreenRectangle(bx0, by0, (int) Math.ceil(x1) - bx0, (int) Math.ceil(y1) - by0).transformMaxBounds(pose);
            return scissorArea != null ? scissorArea.intersection(b) : b;
        }
    }
}
