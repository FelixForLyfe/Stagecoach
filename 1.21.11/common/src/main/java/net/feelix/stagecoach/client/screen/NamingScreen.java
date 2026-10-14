package net.feelix.stagecoach.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.client.ClientNet;
import net.feelix.stagecoach.client.ui.WoodUi;
import net.feelix.stagecoach.net.Packets;
import net.feelix.stagecoach.travel.CoachNames;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/** Closing without confirming keeps the name the server already gave it. */
public class NamingScreen extends Screen {
    private static final ItemStack NAME_TAG = new ItemStack(Items.NAME_TAG);
    private static final int W = 200, H = 112;

    private final Packets.OpenNaming data;
    private final @Nullable Screen parent;
    private final Component hint;
    private EditBox field;
    private Button confirm;
    private int px, py;

    /** {@code parent} is the list when renaming from it, so Cancel goes back there. */
    public NamingScreen(Packets.OpenNaming data, @Nullable Screen parent) {
        super(Stagecoach.text(data.rename() ? "screen.rename.title" : "screen.name.title"));
        this.data = data;
        this.parent = parent;
        this.hint = Stagecoach.text("screen.name.hint", data.maxLength());
    }

    @Override
    protected void init() {
        px = (width - W) / 2;
        py = (height - H) / 2;
        field = new EditBox(font, px + 16, py + 42, W - 32, 18, hint);
        field.setMaxLength(data.maxLength() * 2); // code points can take two chars; the real limit is checked below
        field.setValue(data.name());
        field.setResponder(s -> confirm.active = CoachNames.clean(s, data.maxLength()) != null);
        addRenderableWidget(field);
        setInitialFocus(field);
        addRenderableWidget(Button.builder(Stagecoach.text("screen.cancel"), b -> onClose())
                .bounds(px + 16, py + H - 30, W / 2 - 20, 20).build());
        confirm = addRenderableWidget(Button.builder(Stagecoach.text("screen.confirm"), b -> confirm())
                .bounds(px + W / 2 + 4, py + H - 30, W / 2 - 20, 20).build());
        confirm.active = CoachNames.clean(field.getValue(), data.maxLength()) != null;
    }

    private void confirm() {
        String name = CoachNames.clean(field.getValue(), data.maxLength());
        if (name == null) return;
        ClientNet.setName(data.coachId(), name, parent instanceof DestinationScreen);
        onClose();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
            confirm();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        WoodUi.panel(ctx, px, py, W, H);
        int plaqueW = Math.max(120, font.width(title) + 40);
        int plaqueX = px + (W - plaqueW) / 2;
        WoodUi.plaque(ctx, plaqueX, py + 12, plaqueW, 20, true);
        WoodUi.icon(ctx, NAME_TAG, plaqueX + 5, py + 16, 12);
        ctx.drawString(font, title, plaqueX + 20 + (plaqueW - 24 - font.width(title)) / 2, py + 18, WoodUi.INK, false);
        ctx.drawString(font, hint, px + 17, py + 64, WoodUi.TEXT_MUTED);
        super.render(ctx, mouseX, mouseY, delta);
    }
}
