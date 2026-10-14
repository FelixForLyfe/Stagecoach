package net.feelix.stagecoach.client;

import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.client.ui.WoodUi;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** A small wooden road sign: the stop's name over "Stop discovered". */
final class DiscoveryToast implements Toast {
    private static final ItemStack SADDLE = new ItemStack(Items.SADDLE);
    private static final long SHOWN_MS = 3000;

    private final String name;
    private final Component subtitle = Stagecoach.text("toast.discovered");
    private Visibility visibility = Visibility.SHOW;

    DiscoveryToast(String name) {
        this.name = name;
    }

    @Override
    public Visibility getWantedVisibility() {
        return visibility;
    }

    @Override
    public void update(ToastManager manager, long time) {
        visibility = time >= SHOWN_MS * manager.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
    }

    @Override
    public void render(GuiGraphics ctx, Font font, long time) {
        WoodUi.plaque(ctx, 0, 0, width(), height(), false);
        WoodUi.icon(ctx, SADDLE, 6, 8, 16);
        ctx.drawString(font, font.plainSubstrByWidth(name, width() - 34), 28, 7, WoodUi.INK, false);
        ctx.drawString(font, subtitle, 28, 18, 0xFF6B4A2B, false);
    }
}
