package net.feelix.stagecoach.gametest;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.feelix.stagecoach.StagecoachWood;
import net.feelix.stagecoach.api.EndReason;
import net.feelix.stagecoach.api.event.DestinationListEvent;
import net.feelix.stagecoach.api.event.JourneyEndedEvent;
import net.feelix.stagecoach.api.event.JourneyPreStartEvent;
import net.feelix.stagecoach.api.event.StopDiscoverEvent;
import net.feelix.stagecoach.net.Packets;
import net.feelix.stagecoach.travel.CoachRecord;
import net.feelix.stagecoach.travel.JourneyManager;
import net.feelix.stagecoach.travel.StagecoachService;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static net.feelix.stagecoach.gametest.TravelGameTests.A;
import static net.feelix.stagecoach.gametest.TravelGameTests.B;
import static net.feelix.stagecoach.gametest.TravelGameTests.ROAD;
import static net.feelix.stagecoach.gametest.TravelGameTests.check;
import static net.feelix.stagecoach.gametest.TravelGameTests.connect;
import static net.feelix.stagecoach.gametest.TravelGameTests.crash;
import static net.feelix.stagecoach.gametest.TravelGameTests.disconnect;
import static net.feelix.stagecoach.gametest.TravelGameTests.place;
import static net.feelix.stagecoach.gametest.TravelGameTests.standAt;
import static net.feelix.stagecoach.gametest.TravelGameTests.state;
import static net.feelix.stagecoach.gametest.TravelGameTests.withConfig;

/** Each loader registers the listeners below once; they only act for the test players whose names say so. */
public class EventGameTests {
    private static final Map<UUID, List<EndReason>> ENDED = new ConcurrentHashMap<>();

    public static void onPreStart(JourneyPreStartEvent e) {
        String name = e.getPlayer().getName().getString();
        if (name.startsWith("sc-refuse")) {
            e.setFailMessage(Component.literal("No road today"));
            e.setCanceled(true);
        }
        if (name.startsWith("sc-discount")) e.setCost(0);
    }

    public static void onList(DestinationListEvent e) {
        if (e.getPlayer().getName().getString().startsWith("sc-hide")) e.getDestinations().removeIf(d -> d.stop().name().equals("Secret"));
    }

    public static void onEnded(JourneyEndedEvent e) {
        ENDED.computeIfAbsent(e.getPlayer().getUUID(), k -> new CopyOnWriteArrayList<>()).add(e.getReason());
    }

    public static void onDiscover(StopDiscoverEvent e) {
        if (e.getPlayer().getName().getString().startsWith("sc-blind")) e.setCanceled(true);
    }

    @GameTest(structure = ROAD)
    public void preStartCanRefuseAJourney(GameTestHelper ctx) {
        ServerPlayer p = connect(ctx, "refuse");
        CoachRecord a = place(ctx, p, A, StagecoachWood.OAK), b = place(ctx, p, B, StagecoachWood.OAK);
        standAt(ctx, p, A.south(3));
        p.setExperienceLevels(10);
        StagecoachService.travel(p, new Packets.Travel(a.id, b.id));
        check(ctx, !JourneyManager.isTravelling(p.getUUID()) && p.experienceLevel == 10, "refused, nothing paid");
        disconnect(p);
        ctx.succeed();
    }

    @GameTest(structure = ROAD)
    public void preStartCanChangeTheFare(GameTestHelper ctx) {
        ServerPlayer p = connect(ctx, "discount");
        CoachRecord a = place(ctx, p, A, StagecoachWood.OAK), b = place(ctx, p, B, StagecoachWood.OAK);
        standAt(ctx, p, A.south(3));
        p.setExperienceLevels(0);
        StagecoachService.travel(p, new Packets.Travel(a.id, b.id));
        check(ctx, JourneyManager.isTravelling(p.getUUID()), "a listener made it free, so a player with no levels sets off");
        JourneyManager.cancel(p);
        disconnect(p);
        ctx.succeed();
    }

    @GameTest(structure = ROAD)
    public void destinationListCanHideAStopFromOnePlayer(GameTestHelper ctx) {
        ServerPlayer owner = connect(ctx, "list-owner"), hider = connect(ctx, "hide");
        CoachRecord a = place(ctx, owner, A, StagecoachWood.OAK), b = place(ctx, owner, B, StagecoachWood.OAK);
        state(ctx).rename(b, "Secret");
        state(ctx).discover(hider.getUUID(), a.id);
        state(ctx).discover(hider.getUUID(), b.id);
        check(ctx, StagecoachService.buildList(hider, a).entries().stream().noneMatch(e -> e.id() == b.id), "hidden from this player");
        check(ctx, StagecoachService.buildList(owner, a).entries().stream().anyMatch(e -> e.id() == b.id), "but not from the owner");
        standAt(ctx, hider, A.south(3));
        hider.setExperienceLevels(10);
        StagecoachService.travel(hider, new Packets.Travel(a.id, b.id));
        check(ctx, !JourneyManager.isTravelling(hider.getUUID()), "and they can't travel there");
        disconnect(owner);
        disconnect(hider);
        ctx.succeed();
    }

    @GameTest(structure = ROAD)
    public void journeyEndedFiresOncePerJourney(GameTestHelper ctx) {
        ServerPlayer p = connect(ctx, "ended");
        GameProfile profile = p.getGameProfile();
        CoachRecord a = place(ctx, p, A, StagecoachWood.OAK), b = place(ctx, p, B, StagecoachWood.OAK);
        standAt(ctx, p, A.south(3));
        p.setExperienceLevels(10);
        StagecoachService.travel(p, new Packets.Travel(a.id, b.id));
        JourneyManager.cancel(p);
        check(ctx, ENDED.getOrDefault(p.getUUID(), List.of()).equals(List.of(EndReason.CANCELLED)), "one CANCELLED: " + ENDED.get(p.getUUID()));
        StagecoachService.travel(p, new Packets.Travel(a.id, b.id));
        crash(ctx, p);
        ServerPlayer back = connect(ctx, profile);
        check(ctx, ENDED.get(p.getUUID()).equals(List.of(EndReason.CANCELLED, EndReason.RECOVERED)), "RECOVERED at login: " + ENDED.get(p.getUUID()));
        standAt(ctx, back, A.south(3));
        withConfig("{\"instantTravel\": true}", () -> StagecoachService.travel(back, new Packets.Travel(a.id, b.id)));
        check(ctx, ENDED.get(p.getUUID()).equals(List.of(EndReason.CANCELLED, EndReason.RECOVERED, EndReason.COMPLETED)),
                "COMPLETED for the instant one: " + ENDED.get(p.getUUID()));
        disconnect(back);
        ctx.succeed();
    }

    @GameTest(structure = ROAD)
    public void stopDiscoverCanBeCancelled(GameTestHelper ctx) {
        ServerPlayer owner = connect(ctx, "disc-owner"), blind = connect(ctx, "blind");
        CoachRecord a = place(ctx, owner, A, StagecoachWood.OAK);
        standAt(ctx, blind, A.south(3));
        StagecoachService.onUse(ctx.getLevel(), ctx.absolutePos(A), blind);
        check(ctx, !state(ctx).hasDiscovered(blind.getUUID(), a.id), "a listener kept it undiscovered");
        disconnect(owner);
        disconnect(blind);
        ctx.succeed();
    }
}
