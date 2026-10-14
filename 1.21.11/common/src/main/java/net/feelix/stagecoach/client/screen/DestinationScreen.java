package net.feelix.stagecoach.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.client.ClientNet;
import net.feelix.stagecoach.client.ui.WoodUi;
import net.feelix.stagecoach.net.Packets;
import net.feelix.stagecoach.travel.TravelMath;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Everything shown comes from the server; Travel only sends two ids. */
public class DestinationScreen extends Screen {
    private static final ItemStack MAP = new ItemStack(Items.FILLED_MAP);
    private static final ItemStack BOTTLE = new ItemStack(Items.EXPERIENCE_BOTTLE);
    private static final ItemStack CLOCK = new ItemStack(Items.CLOCK);
    private static final ItemStack SADDLE = new ItemStack(Items.SADDLE);
    private static final ItemStack COMPASS = new ItemStack(Items.COMPASS);
    private static final ItemStack NAME_TAG = new ItemStack(Items.NAME_TAG);
    private static final ItemStack SPYGLASS = new ItemStack(Items.SPYGLASS);
    private static final int ROW_H = 24;

    /** Text worked out once, so drawing a frame allocates nothing. */
    private record Row(Packets.Entry entry, String name, String distance, String cost, String time, int distanceW, int costW, List<Component> tooltip) {}

    private final Packets.DestinationList data;
    private final List<Packets.Entry> entries;
    private final ItemStack fareIcon;
    private final Component balance;
    private final List<Row> rows = new ArrayList<>();
    private final List<Row> shown = new ArrayList<>();
    private EditBox search;
    private Button travel, rename;
    private long selected = -1;
    private double scroll;
    private boolean draggingBar;
    private long lastClickTime;
    private int px, py, pw, ph, listX, listY, listW, listH, barX;
    private List<FormattedCharSequence> emptyHint = List.of();

    public DestinationScreen(Packets.DestinationList data) {
        super(Component.literal(data.name()));
        this.data = data;
        this.entries = new ArrayList<>(data.entries());
        this.fareIcon = data.itemFare() ? new ItemStack(BuiltInRegistries.ITEM.getValue(data.fareItem())) : BOTTLE;
        if (data.free()) balance = Stagecoach.text("screen.free");
        else if (data.itemFare()) balance = Stagecoach.text("screen.items", data.balance());
        else balance = Stagecoach.text("screen.levels", data.balance());
    }

    @Override
    protected void init() {
        pw = Math.min(220, width - 8);
        ph = Math.min(200, height - 8);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        listX = px + 10;
        listY = py + 54;
        barX = px + pw - 24;
        listW = barX - 3 - listX;
        listH = ph - 54 - 36;
        buildRows();
        emptyHint = font.split(Stagecoach.text("screen.empty.hint"), listW - 12);

        String query = search == null ? "" : search.getValue();
        search = new EditBox(font, px + 28, py + 35, pw - 38, 14, Stagecoach.text("screen.search"));
        search.setHint(Stagecoach.text("screen.search").withStyle(ChatFormatting.GRAY));
        search.setValue(query);
        search.setResponder(s -> refilter());
        addRenderableWidget(search);

        if (data.canRename()) {
            rename = addRenderableWidget(Button.builder(Component.empty(), b -> minecraft.setScreen(
                            new NamingScreen(new Packets.OpenNaming(data.coachId(), data.name(), true, data.maxNameLength()), this)))
                    .bounds(px + pw - 32, py + 9, 20, 20)
                    .tooltip(Tooltip.create(Stagecoach.text("tooltip.rename"))).build());
        }
        addRenderableWidget(Button.builder(Stagecoach.text("screen.close"), b -> onClose())
                .bounds(px + pw - 136, py + ph - 29, 56, 20).build());
        travel = addRenderableWidget(Button.builder(Stagecoach.text("screen.travel"), b -> travelSelected())
                .bounds(px + pw - 76, py + ph - 29, 66, 20).build());
        refilter();
    }

    private void buildRows() {
        rows.clear();
        addRows(entries);
    }

    private void addRows(List<Packets.Entry> more) {
        String free = Stagecoach.text("screen.free").getString();
        for (Packets.Entry e : more) {
            String distance = compact(e.distance());
            String cost = data.free() || e.cost() == 0 ? free : String.valueOf(e.cost());
            rows.add(new Row(e, font.plainSubstrByWidth(e.name(), listW - 26), distance, cost, TravelMath.clock(e.seconds() * 20L),
                    small(distance), small(cost), tooltip(e)));
        }
    }

    /** A later page of this list: the rows and the search grow as it arrives. */
    public void append(Packets.ListMore more) {
        if (more.coachId() != data.coachId()) return;
        entries.addAll(more.entries());
        if (search == null) return;
        addRows(more.entries());
        refilter();
    }

    private void refilter() {
        String q = search.getValue().trim().toLowerCase(Locale.ROOT);
        shown.clear();
        for (Row r : rows) if (q.isEmpty() || r.entry().name().toLowerCase(Locale.ROOT).contains(q)) shown.add(r);
        scroll = Mth.clamp(scroll, 0, maxScroll());
        Packets.Entry sel = selectedEntry();
        if (sel == null) selected = -1;
        travel.active = sel != null && sel.status() == Packets.STATUS_OK;
    }

    private Packets.Entry selectedEntry() {
        for (Row r : shown) if (r.entry().id() == selected) return r.entry();
        return null;
    }

    private double maxScroll() {
        return Math.max(0, shown.size() * ROW_H - listH);
    }

    private void travelSelected() {
        Packets.Entry e = selectedEntry();
        if (e != null && e.status() == Packets.STATUS_OK) ClientNet.travel(data.coachId(), e.id());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int button = event.button();
        if (button == InputConstants.MOUSE_BUTTON_LEFT && mx >= barX && mx < barX + 12 && my >= listY && my < listY + listH && maxScroll() > 0) {
            draggingBar = true;
            dragTo(my);
            return true;
        }
        if (button == InputConstants.MOUSE_BUTTON_LEFT && mx >= listX && mx < listX + listW && my >= listY && my < listY + listH) {
            int i = (int) ((my - listY + scroll) / ROW_H);
            if (i >= 0 && i < shown.size() && shown.get(i).entry().status() == Packets.STATUS_OK) {
                long now = System.currentTimeMillis();
                long id = shown.get(i).entry().id();
                boolean twice = selected == id && now - lastClickTime < 300;
                selected = id;
                lastClickTime = now;
                travel.active = true;
                if (twice) travelSelected();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingBar) {
            dragTo(event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingBar = false;
        return super.mouseReleased(event);
    }

    private void dragTo(double my) {
        scroll = Mth.clamp((my - listY - 7.5) / (listH - 15.0), 0, 1) * maxScroll();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double amount) {
        scroll = Mth.clamp(scroll - amount * ROW_H, 0, maxScroll());
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        if ((keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) && travel.active) {
            travelSelected();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        WoodUi.panel(ctx, px, py, pw, ph);

        int plaqueW = Math.min(pw - 60, Math.max(110, font.width(title) + 34));
        int plaqueX = px + (pw - plaqueW) / 2;
        WoodUi.plaque(ctx, plaqueX, py + 10, plaqueW, 19, true);
        WoodUi.icon(ctx, SADDLE, plaqueX + 5, py + 13, 12);
        String name = font.plainSubstrByWidth(title.getString(), plaqueW - 26);
        ctx.drawString(font, name, plaqueX + 20 + (plaqueW - 24 - font.width(name)) / 2, py + 16, WoodUi.INK, false);

        WoodUi.icon(ctx, SPYGLASS, px + 10, py + 34, 14);
        WoodUi.inset(ctx, listX - 1, listY - 1, listW + 2, listH + 2);

        Row hovered = null;
        ctx.enableScissor(listX, listY, listX + listW, listY + listH);
        int first = (int) (scroll / ROW_H);
        for (int i = first; i < shown.size() && i * ROW_H - scroll < listH; i++) {
            Row r = shown.get(i);
            int y = listY + i * ROW_H - (int) scroll;
            boolean hover = mouseX >= listX && mouseX < listX + listW && mouseY >= Math.max(y, listY) && mouseY < Math.min(y + ROW_H, listY + listH);
            if (hover) hovered = r;
            drawRow(ctx, r, listX, y, hover);
        }
        ctx.disableScissor();
        if (shown.isEmpty()) {
            Component line = rows.isEmpty() ? Stagecoach.text("screen.empty") : Stagecoach.text("screen.no_match");
            ctx.drawCenteredString(font, line, listX + listW / 2, listY + listH / 2 - 10, WoodUi.TEXT_MUTED);
            if (rows.isEmpty()) {
                int ly = listY + listH / 2 + 2;
                for (FormattedCharSequence l : emptyHint) {
                    ctx.drawCenteredString(font, l, listX + listW / 2, ly, WoodUi.TEXT_MUTED);
                    ly += 10;
                }
            }
        }
        WoodUi.scrollbar(ctx, barX, listY, listH, maxScroll() > 0 ? (float) (scroll / maxScroll()) : 0, maxScroll() > 0);

        WoodUi.icon(ctx, fareIcon, px + 10, py + ph - 26, 12);
        ctx.drawString(font, balance, px + 24, py + ph - 23, WoodUi.TEXT_GOLD);

        super.render(ctx, mouseX, mouseY, delta);
        if (rename != null) WoodUi.icon(ctx, NAME_TAG, rename.getX() + 2, rename.getY() + 2, 16);
        if (hovered != null) ctx.setComponentTooltipForNextFrame(font, hovered.tooltip(), mouseX, mouseY);
    }

    private void drawRow(GuiGraphics ctx, Row r, int x, int y, boolean hover) {
        Packets.Entry e = r.entry();
        boolean ok = e.status() == Packets.STATUS_OK;
        if (e.id() == selected) {
            ctx.fill(x, y, x + listW, y + ROW_H, 0x50E8C97A);
            ctx.renderOutline(x, y, listW, ROW_H, 0xC0E8C97A);
        } else if (hover && ok) {
            ctx.fill(x, y, x + listW, y + ROW_H, 0x22FFF0D0);
        }
        ctx.fill(x + 2, y + ROW_H - 1, x + listW - 2, y + ROW_H, 0x30000000); // a seam between boards
        WoodUi.icon(ctx, e.owned() ? SADDLE : COMPASS, x + 3, y + 4, 16);
        ctx.drawString(font, r.name(), x + 22, y + 3, ok ? WoodUi.TEXT : WoodUi.TEXT_MUTED);

        int lx = x + 22, ly = y + 13;
        WoodUi.icon(ctx, MAP, lx, ly, 9);
        drawSmall(ctx, r.distance(), lx + 10, ly + 1, e.status() == Packets.STATUS_TOO_FAR ? WoodUi.TEXT_RED : ok ? WoodUi.TEXT : WoodUi.TEXT_MUTED);
        lx += 12 + r.distanceW() + 5;
        WoodUi.icon(ctx, fareIcon, lx, ly, 9);
        drawSmall(ctx, r.cost(), lx + 10, ly + 1, e.status() == Packets.STATUS_CANT_AFFORD ? WoodUi.TEXT_RED : ok ? WoodUi.TEXT_GOLD : WoodUi.TEXT_MUTED);
        lx += 12 + r.costW() + 5;
        WoodUi.icon(ctx, CLOCK, lx, ly, 9);
        drawSmall(ctx, r.time(), lx + 10, ly + 1, ok ? WoodUi.TEXT : WoodUi.TEXT_MUTED);
        if (!ok) ctx.fill(x, y, x + listW, y + ROW_H, 0x38000000);
    }

    /** Second-line text at 3/4 size so three numbers fit beside their icons. */
    private void drawSmall(GuiGraphics ctx, String s, int x, int y, int color) {
        ctx.pose().pushMatrix();
        ctx.pose().translate(x, y);
        ctx.pose().scale(0.75f, 0.75f);
        ctx.drawString(font, s, 0, 0, color);
        ctx.pose().popMatrix();
    }

    private int small(String s) {
        return (int) Math.ceil(font.width(s) * 0.75f);
    }

    private static String compact(int blocks) {
        return blocks < 10_000 ? String.valueOf(blocks) : String.format(Locale.ROOT, "%.1fk", blocks / 1000.0);
    }

    private List<Component> tooltip(Packets.Entry e) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(e.name()).withStyle(ChatFormatting.GOLD));
        lines.add(Stagecoach.text("tooltip.coords", e.pos().getX(), e.pos().getY(), e.pos().getZ()).withStyle(ChatFormatting.GRAY));
        lines.add(Stagecoach.text("tooltip.distance", e.distance()).withStyle(ChatFormatting.GRAY));
        lines.add(Stagecoach.text(e.owned() ? "tooltip.yours" : "tooltip.discovered").withStyle(ChatFormatting.DARK_GRAY));
        if (e.status() == Packets.STATUS_CANT_AFFORD) {
            Component price = data.itemFare() ? Stagecoach.text("tooltip.price_items", e.cost(), fareIcon.getHoverName()) : Stagecoach.text("tooltip.price_levels", e.cost());
            lines.add(Stagecoach.text("tooltip.cant_afford", price).withStyle(ChatFormatting.RED));
        }
        if (e.status() == Packets.STATUS_TOO_FAR) lines.add(Stagecoach.text("tooltip.too_far", data.maxDistance()).withStyle(ChatFormatting.RED));
        if (e.status() == Packets.STATUS_OTHER_DIMENSION) {
            lines.add(Stagecoach.text("tooltip.other_dimension", e.dimension().toString()).withStyle(ChatFormatting.RED));
        }
        if (e.status() == Packets.STATUS_LOCKED) {
            lines.add((e.lockMessage() != null ? e.lockMessage().copy() : Stagecoach.text("error.locked")).withStyle(ChatFormatting.RED));
        }
        return lines;
    }
}
