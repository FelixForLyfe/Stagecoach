package net.feelix.stagecoach.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public final class WoodUi {
    private WoodUi() {}

    // block-atlas sprites, so resource packs apply
    public static final Identifier PANEL_FILL = Identifier.fromNamespaceAndPath("minecraft", "block/spruce_planks");
    public static final Identifier PANEL_FRAME = Identifier.fromNamespaceAndPath("minecraft", "block/dark_oak_log");
    public static final Identifier PANEL_CORNER = Identifier.fromNamespaceAndPath("minecraft", "block/dark_oak_log_top");
    public static final Identifier PLAQUE_FILL = Identifier.fromNamespaceAndPath("minecraft", "block/birch_planks");
    public static final Identifier PLAQUE_EDGE = Identifier.fromNamespaceAndPath("minecraft", "block/stripped_dark_oak_log");
    /* "chain" before 1.21.9 */
    public static final Identifier CHAIN = Identifier.fromNamespaceAndPath("minecraft", "block/iron_chain");
    public static final Identifier INSET_FILL = Identifier.fromNamespaceAndPath("minecraft", "block/dark_oak_planks");
    public static final Identifier HAY = Identifier.fromNamespaceAndPath("minecraft", "block/hay_block_side");
    /** 232,0 enabled, 244,0 disabled, 12x15. */
    // 1.20.2+ split the creative tabs sheet into GUI sprites
    public static final Identifier SCROLLER = Identifier.withDefaultNamespace("container/creative_inventory/scroller");
    public static final Identifier SCROLLER_DISABLED = Identifier.withDefaultNamespace("container/creative_inventory/scroller_disabled");

    public static final int TEXT = 0xFFF4E8C1;
    public static final int TEXT_GOLD = 0xFFE8C97A;
    public static final int TEXT_MUTED = 0xFF9A8F80;
    public static final int TEXT_RED = 0xFFE07A62;
    public static final int INK = 0xFF3B2716;

    private static final int FRAME = 6;

    public static void panel(GuiGraphics ctx, int x, int y, int w, int h) {
        ctx.fill(x + 3, y + 3, x + w + 3, y + h + 3, 0x66000000);
        SpriteBatch.begin(ctx);
        TextureAtlasSprite fill = SpriteBatch.sprite(PANEL_FILL), frame = SpriteBatch.sprite(PANEL_FRAME), corner = SpriteBatch.sprite(PANEL_CORNER);
        SpriteBatch.tile(fill, x + FRAME, y + FRAME, w - 2 * FRAME, h - 2 * FRAME, x, y, 0xFFFFFFFF);
        SpriteBatch.tile(frame, x, y + FRAME, FRAME, h - 2 * FRAME, x, y, 0xFFD8D0C8);
        SpriteBatch.tile(frame, x + w - FRAME, y + FRAME, FRAME, h - 2 * FRAME, x + w - FRAME, y, 0xFFB8B0A8);
        barH(frame, x + FRAME, y, w - 2 * FRAME, FRAME, 0xFFE0D8D0);
        barH(frame, x + FRAME, y + h - FRAME, w - 2 * FRAME, FRAME, 0xFFA8A098);
        // end-grain corners: the ring centre of the log top
        cornerQuad(corner, x, y);
        cornerQuad(corner, x + w - FRAME, y);
        cornerQuad(corner, x, y + h - FRAME);
        cornerQuad(corner, x + w - FRAME, y + h - FRAME);
        SpriteBatch.end();
        ctx.renderOutline(x - 1, y - 1, w + 2, h + 2, 0xFF1A120B);
        ctx.fill(x + FRAME, y + FRAME, x + w - FRAME, y + FRAME + 1, 0x80000000);
        ctx.fill(x + FRAME, y + FRAME, x + FRAME + 1, y + h - FRAME, 0x80000000);
        ctx.fill(x + FRAME, y + h - FRAME - 1, x + w - FRAME, y + h - FRAME, 0x30FFFFFF);
        ctx.fill(x + w - FRAME - 1, y + FRAME, x + w - FRAME, y + h - FRAME, 0x30FFFFFF);
    }

    public static void inset(GuiGraphics ctx, int x, int y, int w, int h) {
        SpriteBatch.begin(ctx);
        SpriteBatch.tile(SpriteBatch.sprite(INSET_FILL), x, y, w, h, x, y, 0xFF8A8078);
        SpriteBatch.end();
        ctx.fill(x, y, x + w, y + 1, 0xA0000000);
        ctx.fill(x, y, x + 1, y + h, 0xA0000000);
        ctx.fill(x, y + h - 1, x + w, y + h, 0x40FFFFFF);
        ctx.fill(x + w - 1, y, x + w, y + h, 0x40FFFFFF);
    }

    public static void plaque(GuiGraphics ctx, int x, int y, int w, int h, boolean chains) {
        ctx.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x55000000);
        SpriteBatch.begin(ctx);
        if (chains) {
            TextureAtlasSprite chain = SpriteBatch.sprite(CHAIN);
            int cy = y - 7;
            SpriteBatch.quad(chain, x + 8, cy, x + 11, y + 1, 6.5f, 0, 9.5f, 8, 0xFFFFFFFF);
            SpriteBatch.quad(chain, x + w - 11, cy, x + w - 8, y + 1, 6.5f, 0, 9.5f, 8, 0xFFFFFFFF);
        }
        SpriteBatch.tile(SpriteBatch.sprite(PLAQUE_EDGE), x, y, w, h, x, y, 0xFFFFFFFF);
        SpriteBatch.tile(SpriteBatch.sprite(PLAQUE_FILL), x + 2, y + 2, w - 4, h - 4, x, y, 0xFFFFFFFF);
        SpriteBatch.end();
        ctx.renderOutline(x - 1, y - 1, w + 2, h + 2, 0xFF1A120B);
    }

    /** Returns the fill width in px. */
    public static int progressBar(GuiGraphics ctx, int x, int y, int w, int h, float progress) {
        int inner = w - 6;
        int fill = Math.round(inner * Math.max(0, Math.min(1, progress)));
        ctx.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x66000000);
        SpriteBatch.begin(ctx);
        TextureAtlasSprite frame = SpriteBatch.sprite(PANEL_FRAME);
        barH(frame, x, y, w, 3, 0xFFE0D8D0);
        barH(frame, x, y + h - 3, w, 3, 0xFFA8A098);
        SpriteBatch.tile(frame, x, y + 3, 3, h - 6, x, y, 0xFFD8D0C8);
        SpriteBatch.tile(frame, x + w - 3, y + 3, 3, h - 6, x, y, 0xFFB8B0A8);
        if (fill > 0) SpriteBatch.tile(SpriteBatch.sprite(HAY), x + 3, y + 3, fill, h - 6, x + 3, y + 3, 0xFFFFFFFF);
        SpriteBatch.end();
        ctx.fill(x + 3 + fill, y + 3, x + w - 3, y + h - 3, 0xFF1C140C);
        if (fill > 0) ctx.fill(x + 3, y + 3, x + 3 + fill, y + 4, 0x50FFFFFF);
        ctx.renderOutline(x - 1, y - 1, w + 2, h + 2, 0xFF1A120B);
        return fill;
    }

    public static void icon(GuiGraphics ctx, ItemStack stack, int x, int y, int size) {
        if (size == 16) {
            ctx.renderItem(stack, x, y);
            return;
        }
        float s = size / 16f;
        ctx.pose().pushMatrix();
        ctx.pose().translate(x, y);
        ctx.pose().scale(s, s);
        ctx.renderItem(stack, 0, 0);
        ctx.pose().popMatrix();
    }

    public static void scrollbar(GuiGraphics ctx, int x, int y, int trackH, float progress, boolean enabled) {
        ctx.fill(x, y, x + 12, y + trackH, 0xFF20160E);
        ctx.renderOutline(x - 1, y - 1, 14, trackH + 2, 0xFF0E0906);
        int ky = y + Math.round((trackH - 15) * Math.max(0, Math.min(1, progress)));
        ctx.blitSprite(RenderPipelines.GUI_TEXTURED, enabled ? SCROLLER : SCROLLER_DISABLED, x, ky, 12, 15);
    }

    private static void cornerQuad(TextureAtlasSprite corner, int x, int y) {
        SpriteBatch.quad(corner, x, y, x + FRAME, y + FRAME, 5, 5, 5 + FRAME, 5 + FRAME, 0xFFFFFFFF);
    }

    /** Each 16px step reads the log with u and v swapped so the grain runs along the bar. */
    private static void barH(TextureAtlasSprite s, float x, float y, float w, float h, int argb) {
        for (float tx = x; tx < x + w; tx += 16f) {
            float x1 = Math.min(tx + 16f, x + w);
            SpriteBatch.quadTurned(s, tx, y, x1, y + h, 0, 0, h, x1 - tx, argb);
        }
    }
}
