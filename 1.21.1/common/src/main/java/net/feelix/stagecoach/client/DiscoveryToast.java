package net.feelix.stagecoach.client;

import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.client.ui.WoodUi;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

/** A small wooden road sign: the stop's name over "Stop discovered". */
final class DiscoveryToast implements Toast {
    private static final ItemStack SADDLE = new ItemStack(Items.SADDLE);
    private static final long SHOWN_MS = 3000;

    private final String name;
    private final Text subtitle = Stagecoach.text("toast.discovered");

    DiscoveryToast(String name) {
        this.name = name;
    }

    @Override
    public Visibility draw(DrawContext ctx, ToastManager manager, long startTime) {
        WoodUi.plaque(ctx, 0, 0, getWidth(), getHeight(), false);
        WoodUi.icon(ctx, SADDLE, 6, 8, 16);
        String shown = manager.getClient().textRenderer.trimToWidth(name, getWidth() - 34);
        ctx.drawText(manager.getClient().textRenderer, shown, 28, 7, WoodUi.INK, false);
        ctx.drawText(manager.getClient().textRenderer, subtitle, 28, 18, 0xFF6B4A2B, false);
        return startTime >= SHOWN_MS * manager.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
    }
}
