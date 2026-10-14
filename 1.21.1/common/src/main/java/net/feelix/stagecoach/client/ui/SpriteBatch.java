package net.feelix.stagecoach.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

public final class SpriteBatch {
    private SpriteBatch() {}

    private static BufferBuilder buffer;
    private static Matrix4f matrix;

    public static Sprite sprite(Identifier id) {
        return MinecraftClient.getInstance().getSpriteAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).apply(id);
    }

    public static void begin(DrawContext ctx) {
        ctx.draw(); // flush anything DrawContext has batched, so draw order is kept
        matrix = ctx.getMatrices().peek().getPositionMatrix();
        RenderSystem.setShaderTexture(0, SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
    }

    public static void end() {
        BuiltBuffer built = buffer.endNullable(); // null when nothing was drawn
        if (built != null) BufferRenderer.drawWithGlobalProgram(built);
        buffer = null;
    }

    /** UVs are in the sprite's own 0..16 texel grid, whatever its real resolution. */
    public static void quad(Sprite s, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, int argb) {
        int a = argb >>> 24, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        float su0 = s.getFrameU(u0 / 16f), su1 = s.getFrameU(u1 / 16f), sv0 = s.getFrameV(v0 / 16f), sv1 = s.getFrameV(v1 / 16f);
        buffer.vertex(matrix, x0, y1, 0).color(r, g, b, a).texture(su0, sv1);
        buffer.vertex(matrix, x1, y1, 0).color(r, g, b, a).texture(su1, sv1);
        buffer.vertex(matrix, x1, y0, 0).color(r, g, b, a).texture(su1, sv0);
        buffer.vertex(matrix, x0, y0, 0).color(r, g, b, a).texture(su0, sv0);
    }

    /** The texture turned a quarter: screen x runs along its v. */
    public static void quadTurned(Sprite s, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, int argb) {
        int a = argb >>> 24, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        float su0 = s.getFrameU(u0 / 16f), su1 = s.getFrameU(u1 / 16f), sv0 = s.getFrameV(v0 / 16f), sv1 = s.getFrameV(v1 / 16f);
        buffer.vertex(matrix, x0, y1, 0).color(r, g, b, a).texture(su1, sv0);
        buffer.vertex(matrix, x1, y1, 0).color(r, g, b, a).texture(su1, sv1);
        buffer.vertex(matrix, x1, y0, 0).color(r, g, b, a).texture(su0, sv1);
        buffer.vertex(matrix, x0, y0, 0).color(r, g, b, a).texture(su0, sv0);
    }

    /** Repeats at 1:1 with the grid anchored at (ax, ay), so a pattern can scroll on its own. */
    public static void tile(Sprite s, float x, float y, float w, float h, float ax, float ay, int argb) {
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
}
