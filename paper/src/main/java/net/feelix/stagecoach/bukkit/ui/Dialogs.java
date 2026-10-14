package net.feelix.stagecoach.bukkit.ui;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.feelix.stagecoach.bukkit.Messages;
import net.feelix.stagecoach.bukkit.StagecoachPlugin;
import net.feelix.stagecoach.bukkit.travel.CoachRecord;
import net.feelix.stagecoach.bukkit.travel.Fare;
import net.feelix.stagecoach.bukkit.travel.Journey;
import net.feelix.stagecoach.bukkit.travel.StagecoachService;
import net.feelix.stagecoach.bukkit.travel.StagecoachService.Denial;
import net.feelix.stagecoach.bukkit.travel.StagecoachService.Entry;
import net.feelix.stagecoach.bukkit.travel.TravelMath;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class Dialogs {
    /** The resource pack's font: road frames U+E100.., bar segments U+E200 (empty) / U+E201 (full). */
    private static final Key UI_FONT = Key.key("stagecoach", "ui");
    public static final int ROAD_FRAMES = 24;
    private static final int BAR_SEGMENTS = 16;
    /** The 108px picture plus 10 empty 9px lines; 12 left a gap and a scrollbar on a 240px GUI. */
    private static final int PICTURE_LINES = 10;
    private static final int MAX_LISTED = 60;
    private static final int WIDE = 256;

    private final StagecoachPlugin plugin;
    /** The travel screen's refresh must not cover "Abandon journey?". */
    private final Map<UUID, Boolean> abandoning = new ConcurrentHashMap<>();

    public Dialogs(StagecoachPlugin plugin) {
        this.plugin = plugin;
    }

    private Messages m() {
        return plugin.messages();
    }

    /** Runs {@code fn} on the server thread, once, within {@code life}. */
    private ActionButton button(Component label, Component tooltip, int width, Duration life,
                                BiConsumer<Player, DialogResponseView> fn) {
        DialogAction action = DialogAction.customClick((view, audience) -> run(audience, p -> fn.accept(p, view)),
                ClickCallback.Options.builder().uses(1).lifetime(life).build());
        ActionButton.Builder b = ActionButton.builder(label).width(width).action(action);
        if (tooltip != null) b.tooltip(tooltip);
        return b.build();
    }

    private ActionButton button(Component label, BiConsumer<Player, DialogResponseView> fn) {
        return button(label, null, 150, Duration.ofMinutes(10), fn);
    }

    private void run(Audience audience, Consumer<Player> fn) {
        if (!(audience instanceof Player p)) return;
        if (plugin.getServer().isPrimaryThread()) fn.accept(p);
        else plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) fn.accept(p);
        });
    }

    private static Dialog dialog(DialogBase base, DialogType type) {
        return Dialog.create(b -> b.empty().base(base).type(type));
    }

    /** Not WAIT_FOR_RESPONSE: 1.21.11's clearDialog never closes the "Waiting for Server" screen. */
    private static DialogBase.Builder base(Component title) {
        return DialogBase.builder(title).canCloseWithEscape(true).pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE);
    }

    private static Component glyph(String chars) {
        return Component.text(chars).font(UI_FONT).color(NamedTextColor.WHITE).shadowColor(ShadowColor.none());
    }

    /** {@code error} shows above the box, if any. */
    public void naming(Player p, CoachRecord r, boolean rename, Component error) {
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.item(plugin.items().create(woodOf(r), 1, m())).showTooltip(false).showDecorations(false).build());
        int maxLength = plugin.settings().maxNameLength();
        body.add(DialogBody.plainMessage(m().c("name-hint", maxLength)));
        if (error != null) body.add(DialogBody.plainMessage(error));
        DialogInput input = DialogInput.text("name", m().c("name-label")).initial(r.name)
                .maxLength(maxLength * 2).width(200).build();
        ActionButton confirm = button(m().c("button-confirm"), (pl, view) -> {
            Denial d = plugin.service().rename(pl, r.id, view.getText("name"));
            if (d == null) {
                if (rename) destinations(pl, r, "");
                else pl.closeDialog();
            } else if (d.retry()) {
                naming(pl, r, rename, m().c(d.key(), d.args()));
            } else {
                pl.closeDialog();
                m().actionBar(pl, d.key(), d.args());
            }
        });
        ActionButton cancel = button(m().c("button-cancel"), (pl, view) -> {
            if (rename) destinations(pl, r, "");
            else {
                pl.closeDialog();
                m().actionBar(pl, "name-kept", r.name);
            }
        });
        p.showDialog(dialog(base(m().c(rename ? "rename-title" : "name-title")).body(body).inputs(List.of(input)).build(),
                DialogType.confirmation(confirm, cancel)));
    }

    public void destinations(Player p, CoachRecord here, String filter) {
        String want = filter == null ? "" : filter.strip();
        List<Entry> all = plugin.service().list(p, here);
        List<Entry> shown = new ArrayList<>();
        for (Entry e : all) {
            if (want.isEmpty() || e.coach().name.toLowerCase(Locale.ROOT).contains(want.toLowerCase(Locale.ROOT))) shown.add(e);
        }
        List<DialogBody> body = new ArrayList<>();
        Fare fare = plugin.fare();
        body.add(DialogBody.plainMessage(plugin.service().travelsFree(p) ? m().c("list-free")
                : fare != null ? m().c("list-items", fare.count(p), fare.name()) : m().c("list-levels", p.getLevel())));
        if (all.isEmpty()) {
            body.add(DialogBody.plainMessage(m().c("list-empty")));
            body.add(DialogBody.plainMessage(m().c("list-empty-hint")));
        } else if (shown.isEmpty()) {
            body.add(DialogBody.plainMessage(m().c("list-no-match")));
        } else if (shown.size() > MAX_LISTED) {
            body.add(DialogBody.plainMessage(m().c("list-too-many", MAX_LISTED, shown.size())));
        }
        List<DialogInput> inputs = new ArrayList<>();
        List<ActionButton> buttons = new ArrayList<>();
        if (!all.isEmpty()) {
            inputs.add(DialogInput.text("search", m().c("list-search")).initial(want).maxLength(32).width(WIDE).build());
            buttons.add(button(m().c("button-search"), null, WIDE, Duration.ofMinutes(10),
                    (pl, view) -> destinations(pl, here, view.getText("search"))));
        }
        for (int i = 0; i < Math.min(MAX_LISTED, shown.size()); i++) {
            Entry e = shown.get(i);
            buttons.add(button(entryLabel(e), entryTooltip(e), WIDE, Duration.ofMinutes(10),
                    (pl, view) -> confirmTravel(pl, here, e, want)));
        }
        if (plugin.service().canRename(p, here)) {
            buttons.add(button(m().c("button-rename"), null, WIDE, Duration.ofMinutes(10), (pl, view) -> naming(pl, here, true, null)));
        }
        ActionButton close = button(m().c("button-close"), (pl, view) -> pl.closeDialog());
        // Paper refuses a multi-action dialog without actions, so an empty list is a notice with Close
        DialogType type = buttons.isEmpty() ? DialogType.notice(close) : DialogType.multiAction(buttons, close, 1);
        p.showDialog(dialog(base(m().c("list-title", here.name)).body(body).inputs(inputs).build(), type));
    }

    private Component entryLabel(Entry e) {
        boolean ok = e.status() == StagecoachService.Status.OK;
        return m().c(ok ? "entry-label" : "entry-label-unavailable", e.coach().name, time(e), cost(e));
    }

    private Component cost(Entry e) {
        Fare fare = plugin.fare();
        if (e.cost() == 0) return m().c("entry-free");
        return fare != null ? m().c("entry-cost-items", e.cost(), fare.name()) : m().c("entry-cost", e.cost());
    }

    private Component time(Entry e) {
        return plugin.settings().instantTravel() ? m().c("entry-instant") : Component.text(TravelMath.clock(e.seconds() * 20L));
    }

    private Component entryTooltip(Entry e) {
        CoachRecord c = e.coach();
        Fare fare = plugin.fare();
        Component status = switch (e.status()) {
            case OTHER_WORLD -> m().c("entry-other-world", c.worldName);
            case TOO_FAR -> m().c("entry-too-far", plugin.settings().maxDistance());
            case CANT_AFFORD -> fare != null ? m().c("entry-no-items", e.cost(), fare.name()) : m().c("entry-no-xp", e.cost());
            case OK -> e.owned() ? m().c("entry-yours") : m().c("entry-discovered");
        };
        return Component.join(net.kyori.adventure.text.JoinConfiguration.newlines(),
                m().c("entry-distance", String.format(Locale.ROOT, "%,d", e.distance())),
                m().c("entry-coords", c.x, c.y, c.z, c.worldName), status);
    }

    private void confirmTravel(Player p, CoachRecord here, Entry e, String filter) {
        CoachRecord dest = e.coach();
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.item(plugin.items().create(woodOf(dest), 1, m())).showTooltip(false).showDecorations(false).build());
        body.add(DialogBody.plainMessage(entryTooltip(e), WIDE));
        body.add(DialogBody.plainMessage(m().c("confirm-journey", time(e), cost(e)), WIDE));
        ActionButton go = button(m().c("button-set-off"), (pl, view) -> {
            Denial d = plugin.service().travel(pl, here.id, dest.id);
            if (d != null) cannotTravel(pl, here, filter, d);
        });
        ActionButton back = button(m().c("button-back"), (pl, view) -> destinations(pl, here, filter));
        p.showDialog(dialog(base(m().c("confirm-title", dest.name)).body(body).build(), DialogType.confirmation(go, back)));
    }

    private void cannotTravel(Player p, CoachRecord here, String filter, Denial d) {
        ActionButton back = button(m().c("button-back"), (pl, view) -> {
            CoachRecord h = plugin.state().coach(here.id);
            if (h != null) destinations(pl, h, filter);
            else pl.closeDialog();
        });
        p.showDialog(dialog(base(m().c("cannot-title")).body(List.of(DialogBody.plainMessage(m().c(d.key(), d.args()), WIDE))).build(),
                DialogType.notice(back)));
    }

    /** Shown again every refresh unless the traveller is looking at "Abandon journey?". */
    public void travelling(Player p, Journey j, long now) {
        if (abandoning.containsKey(p.getUniqueId())) return;
        long elapsed = Math.max(0, now - j.startTime);
        int frame = (int) ((elapsed / 10) % ROAD_FRAMES);
        double progress = Math.min(1.0, (double) elapsed / Math.max(1, j.ticks));
        int full = (int) Math.round(progress * BAR_SEGMENTS);
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < BAR_SEGMENTS; i++) bar.append(i < full ? '' : '');
        List<DialogBody> body = List.of(
                DialogBody.plainMessage(glyph(String.valueOf((char) (0xE100 + frame))).append(Component.text("\n".repeat(PICTURE_LINES))), WIDE),
                DialogBody.plainMessage(glyph(bar.toString()), WIDE),
                DialogBody.plainMessage(m().c("travel-remaining", TravelMath.clock(j.endTime() - now)), WIDE));
        ActionButton cancel = button(m().c("button-cancel-journey"), null, 150, Duration.ofSeconds(30),
                (pl, view) -> abandon(pl));
        p.showDialog(dialog(base(m().c("travel-title", j.destName)).body(body).build(), DialogType.notice(cancel)));
    }

    public void abandon(Player p) {
        if (!plugin.journeys().isTravelling(p.getUniqueId())) {
            p.closeDialog();
            return;
        }
        abandoning.put(p.getUniqueId(), true);
        ActionButton yes = button(m().c("button-abandon"), (pl, view) -> {
            abandoning.remove(pl.getUniqueId());
            plugin.journeys().cancel(pl);
        });
        ActionButton no = button(m().c("button-keep-travelling"), (pl, view) -> {
            abandoning.remove(pl.getUniqueId());
            Journey j = plugin.journeys().journeyOf(pl);
            if (j != null && plugin.journeys().isTravelling(pl.getUniqueId())) travelling(pl, j, plugin.gameTime());
            else pl.closeDialog();
        });
        int percent = plugin.settings().cancelRefundPercent();
        Component message = percent < 100 ? m().c("abandon-message-partial", percent) : m().c("abandon-message");
        p.showDialog(dialog(base(m().c("abandon-title")).body(List.of(DialogBody.plainMessage(message, WIDE))).build(),
                DialogType.confirmation(yes, no)));
    }

    public void journeyEnded(Player p) {
        abandoning.remove(p.getUniqueId());
        p.closeDialog();
    }

    public void forget(UUID player) {
        abandoning.remove(player);
    }

    private static net.feelix.stagecoach.bukkit.Wood woodOf(CoachRecord r) {
        net.feelix.stagecoach.bukkit.Wood w = net.feelix.stagecoach.bukkit.Wood.byId(r.wood);
        return w == null ? net.feelix.stagecoach.bukkit.Wood.OAK : w;
    }
}
