package net.feelix.stagecoach.client.scene;

import net.feelix.stagecoach.client.ui.RoadScene;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.level.MoonPhase;

public final class SceneRenderer {
    private SceneRenderer() {}

    public static void draw(GuiGraphicsExtractor ctx, int w, int h, Scene scene, long seed, float ticks, float skyAngle, MoonPhase moon) {
        if (scene.isRoad()) {
            RoadScene.draw(ctx, w, h, seed, ticks, skyAngle, moon);
            return;
        }
        if (scene.image() != null) {
            image(ctx, w, h, scene);
            return;
        }
        ctx.fillGradient(0, 0, w, h, scene.skyTop(), scene.skyBottom());
        for (Scene.Layer layer : scene.layers()) layer(ctx, w, h, layer, ticks);
    }

    private static void layer(GuiGraphicsExtractor ctx, int w, int h, Scene.Layer layer, float ticks) {
        SceneManager.Texture texture = SceneManager.texture(layer.texture());
        if (texture == null) return;
        int tw = Math.max(1, Math.round(texture.width() * layer.scale())), th = Math.max(1, Math.round(texture.height() * layer.scale()));
        int top = (int) (layer.y() * h);
        float offset = (ticks * RoadScene.ROAD_SPEED * layer.speed()) % tw;
        if (offset < 0) offset += tw;
        for (int x = -(int) offset; x < w; x += tw) {
            ctx.blit(RenderPipelines.GUI_TEXTURED, texture.id(), x, top, 0, 0, tw, th, texture.width(), texture.height(), texture.width(), texture.height());
        }
        if (layer.fill() != null && top + th < h) ctx.fill(0, top + th, w, h, layer.fill());
    }

    /** Cover fills the screen and crops; contain shows the whole image on black. */
    private static void image(GuiGraphicsExtractor ctx, int w, int h, Scene scene) {
        SceneManager.Texture texture = SceneManager.texture(scene.image());
        ctx.fill(0, 0, w, h, 0xFF000000);
        if (texture == null) return;
        float sx = (float) w / texture.width(), sy = (float) h / texture.height();
        float scale = scene.cover() ? Math.max(sx, sy) : Math.min(sx, sy);
        int dw = Math.round(texture.width() * scale), dh = Math.round(texture.height() * scale);
        ctx.blit(RenderPipelines.GUI_TEXTURED, texture.id(), (w - dw) / 2, (h - dh) / 2, 0, 0, dw, dh, texture.width(), texture.height(), texture.width(), texture.height());
    }
}
