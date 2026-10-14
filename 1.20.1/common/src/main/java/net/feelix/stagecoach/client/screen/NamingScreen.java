package net.feelix.stagecoach.client.screen;

import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.client.ClientNet;
import net.feelix.stagecoach.client.ui.WoodUi;
import net.feelix.stagecoach.net.Packets;
import net.feelix.stagecoach.travel.CoachNames;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/** Closing without confirming keeps the name the server already gave it. */
public class NamingScreen extends Screen {
    private static final ItemStack NAME_TAG = new ItemStack(Items.NAME_TAG);
    private static final int W = 200, H = 112;

    private final Packets.OpenNaming data;
    private final @Nullable Screen parent;
    private final Text hint;
    private TextFieldWidget field;
    private ButtonWidget confirm;
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
        field = new TextFieldWidget(textRenderer, px + 16, py + 42, W - 32, 18, hint);
        field.setMaxLength(data.maxLength() * 2); // code points can take two chars; the real limit is checked below
        field.setText(data.name());
        field.setChangedListener(s -> confirm.active = CoachNames.clean(s, data.maxLength()) != null);
        addDrawableChild(field);
        setInitialFocus(field);
        addDrawableChild(ButtonWidget.builder(Stagecoach.text("screen.cancel"), b -> close())
                .dimensions(px + 16, py + H - 30, W / 2 - 20, 20).build());
        confirm = addDrawableChild(ButtonWidget.builder(Stagecoach.text("screen.confirm"), b -> confirm())
                .dimensions(px + W / 2 + 4, py + H - 30, W / 2 - 20, 20).build());
        confirm.active = CoachNames.clean(field.getText(), data.maxLength()) != null;
    }

    private void confirm() {
        String name = CoachNames.clean(field.getText(), data.maxLength());
        if (name == null) return;
        ClientNet.setName(data.coachId(), name, parent instanceof DestinationScreen);
        close();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            confirm();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);
        WoodUi.panel(ctx, px, py, W, H);
        int plaqueW = Math.max(120, textRenderer.getWidth(title) + 40);
        int plaqueX = px + (W - plaqueW) / 2;
        WoodUi.plaque(ctx, plaqueX, py + 12, plaqueW, 20, true);
        WoodUi.icon(ctx, NAME_TAG, plaqueX + 5, py + 16, 12);
        ctx.drawText(textRenderer, title, plaqueX + 20 + (plaqueW - 24 - textRenderer.getWidth(title)) / 2, py + 18, WoodUi.INK, false);
        ctx.drawTextWithShadow(textRenderer, hint, px + 17, py + 64, WoodUi.TEXT_MUTED);
        super.render(ctx, mouseX, mouseY, delta);
    }
}
