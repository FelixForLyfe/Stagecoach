package net.feelix.stagecoach.fabric.clientgametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.feelix.stagecoach.StagecoachRegistry;
import net.feelix.stagecoach.StagecoachWood;
import net.feelix.stagecoach.client.screen.DestinationScreen;
import net.feelix.stagecoach.client.screen.NamingScreen;
import net.feelix.stagecoach.client.screen.TravelScreen;
import net.feelix.stagecoach.config.ClientConfig;
import net.feelix.stagecoach.net.Packets;
import net.feelix.stagecoach.travel.CoachRecord;
import net.feelix.stagecoach.travel.StagecoachService;
import net.feelix.stagecoach.travel.StagecoachState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Set;

/** Dev only: screenshots of what a player sees, saved to run/clientgametest/screenshots. */
public class StagecoachClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> client.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0));
        try (TestSingleplayerContext game = context.worldBuilder().create()) {
            TestServerContext server = game.getServer();
            server.runCommand("time set noon");
            server.runCommand("weather clear");
            game.getConnection().waitForChunksRender();

            BlockPos start = server.computeOnServer(s -> player(s).blockPosition());
            BlockPos a = start.north(8), b = start.north(8).east(40);
            server.runOnServer(s -> place(s, a, start));
            context.waitForScreen(NamingScreen.class);
            context.takeScreenshot("stagecoach-naming");
            context.setScreen(() -> null);
            server.runOnServer(s -> place(s, b, start.east(40)));
            context.waitForScreen(NamingScreen.class);
            context.setScreen(() -> null);

            server.runOnServer(s -> player(s).teleportTo(s.overworld(), a.getX() + 7.5, a.getY() + 2, a.getZ() + 0.5,
                    Set.of(), 90, 15, true));
            context.waitTicks(20);
            context.takeScreenshot("stagecoach-coach");

            server.runOnServer(s -> {
                ServerPlayer p = player(s);
                p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(StagecoachRegistry.ITEMS.get(StagecoachWood.CHERRY)));
                // aims about 4 blocks out, inside survival reach
                p.teleportTo(s.overworld(), a.getX() + 0.5, a.getY(), a.getZ() + 16.5, Set.of(), 180, 22, true);
            });
            context.waitTicks(10);
            context.takeScreenshot("stagecoach-ghost");
            server.runOnServer(s -> player(s).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));

            server.runOnServer(s -> {
                ServerPlayer p = player(s);
                p.teleportTo(s.overworld(), a.getX() + 3.5, a.getY(), a.getZ() + 0.5, Set.of(), 90, 0, true);
                StagecoachService.onUse(s.overworld(), a, p);
            });
            context.waitForScreen(DestinationScreen.class);
            context.waitTicks(5);
            context.takeScreenshot("stagecoach-list");
            context.setScreen(() -> null);

            server.runOnServer(s -> {
                long[] ids = ids(s);
                player(s).giveExperienceLevels(10);
                StagecoachService.travel(player(s), new Packets.Travel(ids[0], ids[1]));
            });
            context.waitForScreen(TravelScreen.class);
            context.waitTicks(20);
            context.takeScreenshot("stagecoach-travel-road");
            server.runCommand("time set midnight");
            context.waitTicks(5);
            context.takeScreenshot("stagecoach-travel-night");
            server.runCommand("time set noon");
            showTravelScreenAs(context, true, false);
            context.takeScreenshot("stagecoach-travel-map");
            showTravelScreenAs(context, false, true);
            context.takeScreenshot("stagecoach-travel-minimal");
            showTravelScreenAs(context, false, false);
            context.waitFor(client -> client.gui.screen() == null, 400);
            context.waitTicks(20);
            context.takeScreenshot("stagecoach-arrived");
        }
    }

    private static void showTravelScreenAs(ClientGameTestContext context, boolean simplified, boolean minimal) {
        context.runOnClient(client -> ClientConfig.save(new ClientConfig(simplified, minimal, true, true, true, true, true), new ArrayList<>()));
        context.waitTicks(5);
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    /** Uses a coach on the ground at {@code pos} from {@code from}, facing north, as a player would. */
    private static void place(MinecraftServer server, BlockPos pos, BlockPos from) {
        ServerPlayer p = player(server);
        p.teleportTo(server.overworld(), from.getX() + 0.5, from.getY(), from.getZ() + 0.5, Set.of(), 180, 0, true);
        ItemStack stack = new ItemStack(StagecoachRegistry.ITEMS.get(StagecoachWood.OAK));
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos ground = pos.below();
        p.gameMode.useItemOn(p, server.overworld(), stack, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(ground).add(0, 0.5, 0), Direction.UP, ground, false));
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    /** Oldest first. */
    private static long[] ids(MinecraftServer server) {
        long first = Long.MAX_VALUE, second = Long.MAX_VALUE;
        for (CoachRecord r : StagecoachState.get(server).coaches()) {
            if (r.id < first) {
                second = first;
                first = r.id;
            } else if (r.id < second) {
                second = r.id;
            }
        }
        return new long[] {first, second};
    }
}
