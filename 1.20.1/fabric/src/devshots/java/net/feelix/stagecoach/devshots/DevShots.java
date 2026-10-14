package net.feelix.stagecoach.devshots;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.feelix.stagecoach.StagecoachRegistry;
import net.feelix.stagecoach.StagecoachWood;
import net.feelix.stagecoach.block.StagecoachBlock;
import net.feelix.stagecoach.block.StagecoachBlockEntity;
import net.feelix.stagecoach.client.ClientNet;
import net.feelix.stagecoach.client.StagecoachRenderer;
import net.feelix.stagecoach.client.screen.NamingScreen;
import net.feelix.stagecoach.footprint.Footprint;
import net.feelix.stagecoach.footprint.StagecoachShapes;
import net.feelix.stagecoach.net.Packets;
import net.feelix.stagecoach.travel.JourneyManager;
import net.feelix.stagecoach.travel.StagecoachService;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class DevShots implements ClientModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("stagecoach-devshots");

    private record Coach(BlockPos pos, Direction facing, StagecoachWood wood) {}

    private record Step(String name, Runnable action, int waitAfter, java.util.function.BooleanSupplier until, int timeout) {
        Step(String name, Runnable action, int waitAfter) {
            this(name, action, waitAfter, null, 0);
        }
    }

    private static final List<Coach> COACHES = new CopyOnWriteArrayList<>();
    private static volatile boolean overlay = true;

    private final List<Step> steps = new ArrayList<>();
    private boolean started;
    private int index;
    private int wait;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(DevShots::drawCollision);
    }

    private int waited;

    private void tick(MinecraftClient client) {
        boolean mp = System.getProperty("stagecoach.devshots", "ui").startsWith("mp");
        if (!started && (client.world == null || client.player == null || (!mp && client.getServer() == null))) return;
        if (!started) {
            started = true;
            client.options.pauseOnLostFocus = false;
            client.options.tutorialStep = net.minecraft.client.tutorial.TutorialStep.NONE;
            wait = 100; // let the world settle
            // -Dstagecoach.devshots=coach runs the model shots; the default is the travel UI
            switch (System.getProperty("stagecoach.devshots", "ui")) {
                case "coach" -> plan(client);
                case "crash1" -> planCrash1(client);
                case "crash2" -> planCrash2(client);
                case "mp" -> planMp(client);
                case "compat" -> planCompat(client);
                default -> planUi(client);
            }
            return;
        }
        if (wait-- > 0) return;
        if (index >= steps.size()) {
            LOG.info("[devshots] all steps done, quitting");
            client.scheduleStop();
            return;
        }
        Step step = steps.get(index);
        if (step.until() != null && !step.until().getAsBoolean()) {
            if (++waited < step.timeout()) return;
            LOG.warn("[devshots] TIMEOUT waiting for: {}", step.name());
        }
        waited = 0;
        index++;
        LOG.info("[devshots] step: {}", step.name());
        step.action().run();
        wait = step.waitAfter();
    }

    private void plan(MinecraftClient client) {
        MinecraftServer server = client.getServer();
        ServerWorld world = server.getOverworld();
        BlockPos spawn = client.player.getBlockPos();
        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING, spawn.getX(), spawn.getZ());
        BlockPos base = new BlockPos(spawn.getX(), y, spawn.getZ());
        LOG.info("[devshots] base {}", base);

        // plus one coach whose controller sits on the first block of a chunk section, for the off-screen check
        Direction[] facings = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        StagecoachWood[] rowA = {StagecoachWood.OAK, StagecoachWood.SPRUCE, StagecoachWood.DARK_OAK, StagecoachWood.CHERRY};
        List<Coach> planned = new ArrayList<>();
        for (int i = 0; i < 4; i++) planned.add(new Coach(base.add(i * 10, 0, 0), facings[i], rowA[i]));
        StagecoachWood[] rowB = {StagecoachWood.BIRCH, StagecoachWood.JUNGLE, StagecoachWood.ACACIA, StagecoachWood.MANGROVE,
                StagecoachWood.BAMBOO, StagecoachWood.CRIMSON, StagecoachWood.WARPED};
        for (int i = 0; i < rowB.length; i++) planned.add(new Coach(base.add(i * 5, 0, 14), Direction.NORTH, rowB[i]));
        int sectionZ = Math.floorDiv(base.getZ() + 40, 16) * 16 + 16;
        Coach offscreen = new Coach(new BlockPos(base.getX() + 50, y, sectionZ), Direction.NORTH, StagecoachWood.OAK);
        planned.add(offscreen);

        steps.add(new Step("setup + place", () -> server.execute(() -> {
            world.setTimeOfDay(6000);
            world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
            world.setWeather(24000, 0, false, false);
            ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
            player.changeGameMode(GameMode.CREATIVE);
            player.getAbilities().flying = true;
            player.sendAbilitiesUpdate();
            // out of every footprint
            player.networkHandler.requestTeleport(base.getX() - 20, y + 12, base.getZ() - 20, 0, 0);
            for (Coach c : planned) {
                ItemStack stack = new ItemStack(StagecoachRegistry.ITEMS.get(c.wood()));
                player.setStackInHand(Hand.MAIN_HAND, stack);
                player.setYaw(c.facing().asRotation());
                player.setHeadYaw(c.facing().asRotation());
                BlockPos ground = c.pos().down();
                player.interactionManager.interactBlock(player, world, stack, Hand.MAIN_HAND,
                        new BlockHitResult(Vec3d.ofCenter(ground).add(0, 0.5, 0), Direction.UP, ground, false));
                BlockState s = world.getBlockState(c.pos());
                boolean ok = s.getBlock() instanceof StagecoachBlock b && b.wood() == c.wood() && s.get(StagecoachBlock.FACING) == c.facing();
                LOG.info("[devshots] placed {} {} at {}: {}", c.wood(), c.facing(), c.pos(), ok ? "OK" : "FAILED (" + s + ")");
                if (ok) COACHES.add(c);
            }
        }), 60));

        shot(client, "overview_facings", true, base.add(15, 16, -12), Vec3d.of(base).add(15.5, 0, 3.5), 60);
        for (int i = 0; i < 4; i++) {
            BlockPos c = base.add(i * 10, 0, 0);
            String f = facings[i].asString();
            shot(client, "facing_" + f, true, c.add(5, 3, 5), Vec3d.of(c).add(0.5, 1, 0.5), 40);
            shot(client, "facing_" + f + "_plain", false, c.add(5, 3, 5), Vec3d.of(c).add(0.5, 1, 0.5), 10);
            shot(client, "facing_" + f + "_back", true, c.add(-5, 3, -5), Vec3d.of(c).add(0.5, 1, 0.5), 40);
        }
        // target nudged 0.01 north so the camera's yaw is exactly north
        for (int i = 0; i < 4; i++) {
            BlockPos c = base.add(i * 10, 0, 0);
            shot(client, "topdown_" + facings[i].asString(), false, new Vec3d(c.getX() + 0.5, y + 9, c.getZ() + 0.5),
                    new Vec3d(c.getX() + 0.5, y, c.getZ() + 0.49), 40);
        }
        BlockPos n = base;
        shot(client, "ground_wheel", false, new Vec3d(n.getX() + 3.6, y + 0.35, n.getZ() + 2.6), new Vec3d(n.getX() + 1.7, y + 0.1, n.getZ() + 1.2), 40);
        shot(client, "under_coach", false, new Vec3d(n.getX() + 6.5, y + 0.25, n.getZ() + 0.5), new Vec3d(n.getX() + 0.5, y + 0.2, n.getZ() + 0.5), 40);
        shot(client, "woods", false, base.add(15, 9, 27), Vec3d.of(base).add(15.5, 1, 14.5), 60);

        // a narrow FOV puts the controller's chunk section outside the frustum
        BlockPos oc = offscreen.pos();
        steps.add(new Step("offscreen fov", () -> client.options.getFov().setValue(30), 1));
        Vec3d offEye = new Vec3d(oc.getX() + 0.5, y + 4.5, oc.getZ() - 3.0);
        Vec3d offAt = new Vec3d(oc.getX() + 0.5, y, oc.getZ() - 2.99);
        shot(client, "offscreen", false, offEye, offAt, 60);
        steps.add(frustumCheck(client, oc, "offscreen"));

        // control shot: rendersOutsideBoundingBox off, so the model should vanish here
        steps.add(new Step("control renderer", () -> {
            BlockEntityRendererFactories.register(StagecoachRegistry.BLOCK_ENTITY, ctx -> new StagecoachRenderer() {
                @Override
                public boolean rendersOutsideBoundingBox(StagecoachBlockEntity blockEntity) {
                    return false;
                }
            });
            client.reloadResources();
        }, 240));
        steps.add(new Step("rebuild chunks", () -> client.worldRenderer.reload(), 60));
        steps.add(new Step("control fov", () -> client.options.getFov().setValue(30), 1));
        shot(client, "offscreen_control", false, offEye, offAt, 60);
        steps.add(frustumCheck(client, oc, "offscreen_control"));

        steps.add(new Step("hotbar", () -> server.execute(() -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayerList().get(0);
            StagecoachWood[] woods = StagecoachWood.values();
            for (int i = 0; i < 9; i++) player.getInventory().setStack(i, new ItemStack(StagecoachRegistry.ITEMS.get(woods[i])));
            player.getInventory().selectedSlot = 0;
            player.networkHandler.sendPacket(new UpdateSelectedSlotS2CPacket(0));
        }), 10));
        steps.add(new Step("hud on", () -> client.options.hudHidden = false, 1));
        shot(client, "items", false, base.add(-20, 30, -20), Vec3d.of(base).add(-20, 32, -40), 40);

        steps.add(new Step("flush screenshots", () -> {}, 60));
    }

    private static final long[] IDS = new long[4]; // A, B, the last small one, the far one

    private void planUi(MinecraftClient client) {
        MinecraftServer server = client.getServer();
        ServerWorld world = server.getOverworld();
        BlockPos spawn = client.player.getBlockPos();
        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING, spawn.getX(), spawn.getZ());
        BlockPos base = new BlockPos(spawn.getX(), y, spawn.getZ());
        BlockPos far = base.add(30_000, 0, 0);
        LOG.info("[devshots] ui base {}", base);

        steps.add(new Step("setup + place", () -> server.execute(() -> {
            world.setTimeOfDay(6000);
            world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
            world.setWeather(24000, 0, false, false);
            ServerPlayerEntity p = server.getPlayerManager().getPlayerList().get(0);
            p.changeGameMode(GameMode.SURVIVAL);
            p.setExperienceLevel(30);
            IDS[3] = placeAt(p, world, far, StagecoachWood.DARK_OAK);
            StagecoachService.rename(p, new Packets.SetName(IDS[3], "Far Harbour", false));
            IDS[0] = placeAt(p, world, base, StagecoachWood.OAK);
            IDS[1] = placeAt(p, world, base.add(12, 0, 0), StagecoachWood.SPRUCE);
            StagecoachWood[] more = {StagecoachWood.BIRCH, StagecoachWood.CHERRY, StagecoachWood.MANGROVE, StagecoachWood.WARPED, StagecoachWood.ACACIA};
            for (int i = 0; i < more.length; i++) IDS[2] = placeAt(p, world, base.add(-10 - i * 5, 0, 0), more[i]);
            p.teleport(world, base.getX() + 0.5, y, base.getZ() + 6.5, 180f, 0f);
            LOG.info("[devshots] placed: A {} B {} last {} far {}", IDS[0], IDS[1], IDS[2], IDS[3]);
        }), 80));

        snap(client, "ui_naming", 2);
        steps.add(new Step("type name", () -> {
            Screen s = client.currentScreen;
            LOG.info("[devshots] screen now: {}", s == null ? null : s.getClass().getSimpleName());
            if (s == null) return;
            for (int i = 0; i < 30; i++) s.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0);
            for (char c : "The Twins".toCharArray()) s.charTyped(c, 0);
        }, 5));
        snap(client, "ui_naming_typed", 2);
        steps.add(new Step("confirm name", () -> {
            if (client.currentScreen != null) client.currentScreen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        }, 20));

        steps.add(new Step("open list", () -> server.execute(() ->
                StagecoachService.onUse(world, base, server.getPlayerManager().getPlayerList().get(0))), 20));
        snap(client, "ui_list", 2);
        steps.add(new Step("search far", () -> {
            Screen s = client.currentScreen;
            LOG.info("[devshots] screen now: {}", s == null ? null : s.getClass().getSimpleName());
            if (s == null) return;
            int[] g = listGeometry(s);
            s.mouseClicked(g[0] + 40, g[1] + 41, 0);
            for (char c : "far".toCharArray()) s.charTyped(c, 0);
        }, 5));
        snap(client, "ui_list_search", 2);
        steps.add(new Step("select row", () -> {
            Screen s = client.currentScreen;
            if (s == null) return;
            int[] g = listGeometry(s);
            s.mouseClicked(g[0] + 40, g[1] + 54 + 10, 0);
        }, 5));
        snap(client, "ui_list_selected", 2);
        steps.add(new Step("travel (Enter)", () -> {
            if (client.currentScreen != null) client.currentScreen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        }, 60));
        logState(server, "on the road to Far Harbour (expect SPECTATOR, 23 levels)");

        snap(client, "ui_travel_day", 2);
        timeStep(server, world, 12700, "dusk");
        snap(client, "ui_travel_dusk", 2);
        timeStep(server, world, 18000, "night");
        snap(client, "ui_travel_night", 2);
        timeStep(server, world, 23300, "dawn");
        snap(client, "ui_travel_dawn", 2);
        timeStep(server, world, 6000, "noon");
        steps.add(new Step("gui scale 4", () -> {
            client.options.getGuiScale().setValue(4);
            client.onResolutionChanged();
        }, 20));
        snap(client, "ui_travel_scale4", 2);
        steps.add(new Step("gui scale auto", () -> {
            client.options.getGuiScale().setValue(0);
            client.onResolutionChanged();
        }, 20));

        steps.add(new Step("esc", () -> {
            if (client.currentScreen != null) client.currentScreen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
        }, 10));
        snap(client, "ui_abandon", 2);
        steps.add(new Step("abandon yes", () -> {
            Screen s = client.currentScreen;
            LOG.info("[devshots] screen now: {}", s == null ? null : s.getClass().getSimpleName());
            if (s == null) return;
            for (var child : s.children()) {
                if (child instanceof net.minecraft.client.gui.widget.ButtonWidget b
                        && b.getMessage().getString().equals(net.minecraft.text.Text.translatable("stagecoach.screen.abandon.yes").getString())) {
                    b.onPress();
                    return;
                }
            }
        }, 30));
        logState(server, "after abandoning (expect SURVIVAL, 30 levels, in front of A)");
        snap(client, "ui_after_cancel", 2);

        steps.add(new Step("travel A->B", () -> ClientNet.travel(IDS[0], IDS[1]), 30));
        snap(client, "ui_travel_short", 2);
        steps.add(new Step("wait for arrival", () -> {}, 110));
        logState(server, "after arriving at B (expect SURVIVAL, 28 levels, in front of B)");
        snap(client, "ui_arrived", 2);
        steps.add(new Step("flush screenshots", () -> {}, 40));
    }

    /** Saves the world with the player in spectator, then kills the JVM outright. */
    private void planCrash1(MinecraftClient client) {
        MinecraftServer server = client.getServer();
        ServerWorld world = server.getOverworld();
        BlockPos spawn = client.player.getBlockPos();
        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING, spawn.getX(), spawn.getZ());
        BlockPos base = new BlockPos(spawn.getX(), y, spawn.getZ());
        steps.add(new Step("setup + place", () -> server.execute(() -> {
            ServerPlayerEntity p = server.getPlayerManager().getPlayerList().get(0);
            p.changeGameMode(GameMode.SURVIVAL);
            p.setExperienceLevel(30);
            IDS[3] = placeAt(p, world, base.add(30_000, 0, 0), StagecoachWood.DARK_OAK);
            IDS[0] = placeAt(p, world, base, StagecoachWood.OAK);
            p.teleport(world, base.getX() + 0.5, y, base.getZ() + 6.5, 180f, 0f);
        }), 60));
        steps.add(new Step("close naming", () -> client.setScreen(null), 5));
        steps.add(new Step("travel", () -> ClientNet.travel(IDS[0], IDS[3]), 60));
        logState(server, "crash1 on the road (expect SPECTATOR, 23 levels)");
        steps.add(new Step("save world mid-journey", () -> server.execute(() -> {
            server.getPlayerManager().saveAllPlayerData();
            server.saveAll(false, true, true);
            LOG.info("[devshots] crash1: world saved with the player on the road");
        }), 40));
        steps.add(new Step("kill", () -> {
            LOG.info("[devshots] crash1: killing the JVM now (no shutdown, no disconnect, no save)");
            Runtime.getRuntime().halt(137);
        }, 1));
    }

    /** The same world after the kill: the journey must be undone on join. */
    private void planCrash2(MinecraftClient client) {
        MinecraftServer server = client.getServer();
        logState(server, "crash2 after rejoining (expect SURVIVAL, 30 levels, in front of the origin coach, not travelling)");
        steps.add(new Step("journey record", () -> server.execute(() -> {
            ServerPlayerEntity p = server.getPlayerManager().getPlayerList().get(0);
            LOG.info("[devshots] STATE crash2 journey record left: {}, client journey: {}", JourneyManager.journeyOf(p), ClientNet.journey());
        }), 5));
        snap(client, "crash2_rejoined", 2);
        steps.add(new Step("flush screenshots", () -> {}, 40));
    }

    // the server is killed and restarted from outside, signalled through two flag files

    private void planMp(MinecraftClient client) {
        java.nio.file.Path onRoad = client.runDirectory.toPath().resolve("mp_on_road.flag");
        java.nio.file.Path serverBack = client.runDirectory.toPath().resolve("mp_server_back.flag");
        long[] a = new long[1];
        BlockPos[] coachA = new BlockPos[1];
        steps.add(new Step("setup commands", () -> {
            cmd(client, "gamemode survival");
            cmd(client, "xp set @s 30 levels");
            cmd(client, "clear @s");
            cmd(client, "give @s stagecoach:oak_stagecoach 1");
            cmd(client, "give @s stagecoach:dark_oak_stagecoach 1");
            cmd(client, "time set noon");
            cmd(client, "gamerule doDaylightCycle false");
            cmd(client, "tp @s ~ ~ ~ 180 0");
        }, 40));
        waitFor(client, "items given", () -> countCoaches(client) >= 2, 200);
        steps.add(new Step("place A (north)", () -> {
            BlockPos feet = client.player.getBlockPos();
            coachA[0] = feet.north(4);
            useItemAt(client, StagecoachWood.OAK, coachA[0].down());
        }, 30));
        waitFor(client, "naming screen A", () -> client.currentScreen instanceof NamingScreen, 100);
        typeAndEnter(client, "Riverrun");
        steps.add(new Step("far away", () -> cmd(client, "tp @s ~30000 ~ ~ 180 0"), 120));
        steps.add(new Step("place B (north)", () -> useItemAt(client, StagecoachWood.DARK_OAK, client.player.getBlockPos().north(4).down()), 30));
        waitFor(client, "naming screen B", () -> client.currentScreen instanceof NamingScreen, 100);
        typeAndEnter(client, "Far Harbour");
        steps.add(new Step("back to A", () -> cmd(client, "tp @s ~-30000 ~ ~ 180 0"), 120));
        mpSnapState(client, "mp placed both (expect SURVIVAL, 30 levels)");

        openListAndTravel(client, coachA);
        waitFor(client, "on the road", () -> ClientNet.journey() != null, 100);
        steps.add(new Step("screenshot road", () -> {}, 40));
        snap(client, "mp_on_road", 2);
        mpSnapState(client, "mp on the road (expect SPECTATOR, 23 levels)");
        steps.add(new Step("signal: kill the server now", () -> touch(onRoad), 5));
        waitFor(client, "disconnected by the kill", () -> client.world == null, 1200);
        steps.add(new Step("after kill", () -> LOG.info("[devshots] STATE mp after kill: client journey {} (expect null), screen {}",
                ClientNet.journey(), client.currentScreen == null ? null : client.currentScreen.getClass().getSimpleName()), 5));
        waitFor(client, "server restarted", () -> java.nio.file.Files.exists(serverBack), 6000);
        steps.add(new Step("reconnect", () -> reconnect(client), 20));
        waitFor(client, "back in", () -> client.world != null && client.player != null, 1200);
        steps.add(new Step("settle", () -> {}, 60));
        mpSnapState(client, "mp after the server was killed and restarted (expect SURVIVAL, 30 levels, in front of Riverrun)");
        snap(client, "mp_after_kill", 2);

        openListAndTravel(client, coachA);
        waitFor(client, "on the road again", () -> ClientNet.journey() != null, 100);
        steps.add(new Step("road a moment", () -> {}, 40));
        mpSnapState(client, "mp on the road again (expect SPECTATOR, 23 levels)");
        steps.add(new Step("quit to title", () -> {
            client.world.disconnect();
            client.disconnect();
        }, 60));
        steps.add(new Step("reconnect after quitting", () -> reconnect(client), 20));
        waitFor(client, "back in after quitting", () -> client.world != null && client.player != null, 1200);
        steps.add(new Step("settle", () -> {}, 60));
        mpSnapState(client, "mp after quitting mid-journey and logging back in (expect SURVIVAL, 30 levels, in front of Riverrun)");
        snap(client, "mp_after_quit", 2);
        steps.add(new Step("flush screenshots", () -> {}, 40));
    }

    private void openListAndTravel(MinecraftClient client, BlockPos[] coachA) {
        steps.add(new Step("close screens", () -> client.setScreen(null), 5));
        steps.add(new Step("right-click Riverrun", () -> {
            client.player.setYaw(180f);
            client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND,
                    new BlockHitResult(Vec3d.ofCenter(coachA[0]), Direction.SOUTH, coachA[0], false));
        }, 20));
        waitFor(client, "list open", () -> client.currentScreen instanceof net.feelix.stagecoach.client.screen.DestinationScreen, 100);
        steps.add(new Step("pick Far Harbour + Enter", () -> {
            Screen s = client.currentScreen;
            int[] g = listGeometry(s);
            s.mouseClicked(g[0] + 40, g[1] + 41, 0);
            for (char c : "far".toCharArray()) s.charTyped(c, 0);
            s.mouseClicked(g[0] + 40, g[1] + 54 + 10, 0);
            s.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        }, 10));
    }

    private void waitFor(MinecraftClient client, String what, java.util.function.BooleanSupplier until, int timeoutTicks) {
        steps.add(new Step("wait: " + what, () -> {}, 0, until, timeoutTicks));
    }

    private void typeAndEnter(MinecraftClient client, String text) {
        steps.add(new Step("type " + text, () -> {
            Screen s = client.currentScreen;
            if (s == null) return;
            for (int i = 0; i < 30; i++) s.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0);
            for (char c : text.toCharArray()) s.charTyped(c, 0);
            s.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        }, 20));
    }

    private void mpSnapState(MinecraftClient client, String label) {
        steps.add(new Step("state " + label, () -> {
            if (client.player == null || client.interactionManager == null) {
                LOG.info("[devshots] STATE {}: not in a world", label);
                return;
            }
            LOG.info("[devshots] STATE {}: mode {}, levels {}, pos {}, client journey {}", label,
                    client.interactionManager.getCurrentGameMode(), client.player.experienceLevel, client.player.getBlockPos(),
                    ClientNet.journey() == null ? "none" : ClientNet.journey().destName());
        }, 2));
    }

    private static void cmd(MinecraftClient client, String command) {
        client.player.networkHandler.sendChatCommand(command);
    }

    private static int countCoaches(MinecraftClient client) {
        int n = 0;
        for (int i = 0; i < 9; i++) if (client.player.getInventory().getStack(i).getItem() instanceof net.minecraft.item.BlockItem bi
                && bi.getBlock() instanceof net.feelix.stagecoach.block.StagecoachBlock) n++;
        return n;
    }

    private static void useItemAt(MinecraftClient client, StagecoachWood wood, BlockPos ground) {
        for (int i = 0; i < 9; i++) {
            if (client.player.getInventory().getStack(i).isOf(StagecoachRegistry.ITEMS.get(wood))) client.player.getInventory().selectedSlot = i;
        }
        client.player.setYaw(180f);
        client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND,
                new BlockHitResult(Vec3d.ofCenter(ground).add(0, 0.5, 0), Direction.UP, ground, false));
    }

    private static void reconnect(MinecraftClient client) {
        net.minecraft.client.network.ServerInfo info = new net.minecraft.client.network.ServerInfo("mp", "localhost:25568", false);
        net.minecraft.client.gui.screen.ConnectScreen.connect(new net.minecraft.client.gui.screen.TitleScreen(), client,
                net.minecraft.client.network.ServerAddress.parse("localhost:25568"), info, false);
    }

    private static void touch(java.nio.file.Path p) {
        try {
            java.nio.file.Files.writeString(p, "1");
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // needs the pack's jars in run/devshots/mods (Jade, JEI, FTB, Architectury, Sodium, Iris)

    private void planCompat(MinecraftClient client) {
        MinecraftServer server = client.getServer();
        ServerWorld world = server.getOverworld();
        BlockPos spawn = client.player.getBlockPos();
        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING, spawn.getX(), spawn.getZ());
        BlockPos base = new BlockPos(spawn.getX(), y, spawn.getZ());
        BlockPos coachB = base.add(12, 0, 0);
        BlockPos coachC = new BlockPos(((base.getX() >> 4) << 4) + 8, y, ((base.getZ() - 40) >> 4) << 4);
        steps.add(new Step("setup", () -> server.execute(() -> {
            world.setTimeOfDay(6000);
            world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
            world.setWeather(24000, 0, false, false);
            ServerPlayerEntity p = server.getPlayerManager().getPlayerList().get(0);
            p.changeGameMode(GameMode.SURVIVAL);
            p.setExperienceLevel(30);
            IDS[0] = placeAt(p, world, base, StagecoachWood.OAK);
            // B belongs to someone else, so Jade shows an owner and "not discovered"
            com.mojang.authlib.GameProfile hendry = new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "Hendry");
            server.getUserCache().add(hendry);
            net.fabricmc.fabric.api.entity.FakePlayer other = net.fabricmc.fabric.api.entity.FakePlayer.get(world, hendry);
            IDS[1] = placeAt(other, world, coachB, StagecoachWood.SPRUCE);
            StagecoachService.rename(other, new Packets.SetName(IDS[1], "Hendry's Halt", false));
            // Flat worlds spawn slimes, and a slime in the footprint (rightly) refuses the coach.
            world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
            world.getOtherEntities(null, new net.minecraft.util.math.Box(coachC).expand(8), e -> !(e instanceof net.minecraft.entity.player.PlayerEntity))
                    .forEach(net.minecraft.entity.Entity::discard);
            IDS[2] = placeAt(p, world, coachC, StagecoachWood.DARK_OAK);
            p.teleport(world, base.getX() + 0.5, y, base.getZ() + 9.5, 180f, 0f);
        }), 40));
        steps.add(new Step("close naming", () -> client.setScreen(null), 5));

        look(server, new Vec3d(coachB.getX() + 4.5, y, coachB.getZ() + 0.5), new Vec3d(coachB.getX() + 1.5, y + 1.5, coachB.getZ() + 0.5), 40);
        snap(client, "compat_jade_other", 2);
        look(server, new Vec3d(base.getX() + 4.5, y, base.getZ() + 0.5), new Vec3d(base.getX() + 1.5, y + 1.5, base.getZ() + 0.5), 40);
        snap(client, "compat_jade_own", 2);

        steps.add(new Step("hold a coach", () -> server.execute(() -> server.getPlayerManager().getPlayerList().get(0)
                .setStackInHand(Hand.MAIN_HAND, new ItemStack(StagecoachRegistry.ITEMS.get(StagecoachWood.BIRCH), 3))), 10));
        look(server, new Vec3d(base.getX() - 14.5, y, base.getZ() + 6.5), new Vec3d(base.getX() - 14.5, y - 0.5, base.getZ() + 2.5), 20);
        snap(client, "compat_preview_fits", 2);
        look(server, new Vec3d(base.getX() + 0.5, y, base.getZ() + 7.5), new Vec3d(base.getX() + 0.5, y - 0.5, base.getZ() + 3.5), 20);
        snap(client, "compat_preview_blocked", 2);
        steps.add(new Step("try to place into A", () -> client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND,
                (BlockHitResult) client.crosshairTarget), 15));
        snap(client, "compat_refused_blocked", 2);

        BlockPos claimCorner = new BlockPos((base.getX() >> 4) << 4, y, (((base.getZ() + 48) >> 4) << 4));
        // our team claims a chunk; Hendry (not in it) tries to place a coach reaching into it
        steps.add(new Step("ftb claim own chunk", () -> server.execute(() -> {
            ServerPlayerEntity p = server.getPlayerManager().getPlayerList().get(0);
            p.teleport(world, claimCorner.getX() + 8.5, y, claimCorner.getZ() + 8.5, 0f, 0f);
            int r = server.getCommandManager().executeWithPrefix(p.getCommandSource().withLevel(4), "ftbchunks claim");
            LOG.info("[devshots] STATE ftb: 'ftbchunks claim' -> {} (chunk {})", r, new net.minecraft.util.math.ChunkPos(claimCorner));
        }), 20));
        steps.add(new Step("hendry tries to place into our claim", () -> server.execute(() -> {
            com.mojang.authlib.GameProfile hendry = server.getUserCache().findByName("Hendry").orElseThrow();
            net.fabricmc.fabric.api.entity.FakePlayer other = net.fabricmc.fabric.api.entity.FakePlayer.get(world, hendry);
            // controller 2 blocks outside the chunk, facing south: its front is inside
            BlockPos c = claimCorner.add(8, 0, -2);
            ItemStack stack = new ItemStack(StagecoachRegistry.ITEMS.get(StagecoachWood.CHERRY));
            other.setStackInHand(Hand.MAIN_HAND, stack);
            other.refreshPositionAndAngles(c.getX() + 0.5, y, c.getZ() - 8.5, Direction.SOUTH.asRotation(), 0);
            BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(c.down()).add(0, 0.5, 0), Direction.UP, c.down(), false);
            var why = net.feelix.stagecoach.block.StagecoachStructure.check(new net.minecraft.item.ItemPlacementContext(other, Hand.MAIN_HAND, stack, hit),
                    StagecoachRegistry.BLOCKS.get(StagecoachWood.CHERRY).getDefaultState().with(net.feelix.stagecoach.block.StagecoachBlock.FACING, Direction.SOUTH), null);
            other.interactionManager.interactBlock(other, world, stack, Hand.MAIN_HAND, hit);
            LOG.info("[devshots] STATE ftb: Hendry's coach into our claim -> reason {} ({}), controller placed: {}",
                    why == null ? null : why.reason(), why == null ? "" : why.message().getString(), world.getBlockState(c).getBlock() instanceof net.feelix.stagecoach.block.StagecoachBlock);
            ServerPlayerEntity p = server.getPlayerManager().getPlayerList().get(0);
            ItemStack mine = new ItemStack(StagecoachRegistry.ITEMS.get(StagecoachWood.CHERRY));
            p.setStackInHand(Hand.MAIN_HAND, mine);
            p.refreshPositionAndAngles(c.getX() + 0.5, y, c.getZ() - 8.5, Direction.SOUTH.asRotation(), 0);
            p.interactionManager.interactBlock(p, world, mine, Hand.MAIN_HAND, hit);
            LOG.info("[devshots] STATE ftb: the claim owner's same coach -> controller placed: {}",
                    world.getBlockState(c).getBlock() instanceof net.feelix.stagecoach.block.StagecoachBlock);
        }), 20));
        steps.add(new Step("close naming", () -> client.setScreen(null), 5));
        steps.add(new Step("empty hand", () -> server.execute(() -> server.getPlayerManager().getPlayerList().get(0)
                .setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY)), 5));
        steps.add(new Step("jei recipe", () -> {
            var rt = net.feelix.stagecoach.compat.jei.StagecoachJeiPlugin.runtime();
            LOG.info("[devshots] STATE jei runtime present: {}", rt != null);
            if (rt == null) return;
            rt.getRecipesGui().show(rt.getJeiHelpers().getFocusFactory().createFocus(mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT,
                    mezz.jei.api.constants.VanillaTypes.ITEM_STACK, new ItemStack(StagecoachRegistry.ITEMS.get(StagecoachWood.OAK))));
        }, 20));
        snap(client, "compat_jei_recipe", 2);
        steps.add(new Step("jei info", () -> {
            var rt = net.feelix.stagecoach.compat.jei.StagecoachJeiPlugin.runtime();
            if (rt != null) rt.getRecipesGui().showTypes(java.util.List.of(mezz.jei.api.constants.RecipeTypes.INFORMATION));
        }, 20));
        snap(client, "compat_jei_info", 2);

        // vanilla only opens the creative screen in creative
        steps.add(new Step("creative + flying", () -> server.execute(() -> {
            ServerPlayerEntity p = server.getPlayerManager().getPlayerList().get(0);
            p.changeGameMode(GameMode.CREATIVE);
            p.getAbilities().flying = true;
            p.sendAbilitiesUpdate();
        }), 10));
        steps.add(new Step("creative screen", () -> {
            net.minecraft.item.ItemGroups.updateDisplayContext(client.player.networkHandler.getEnabledFeatures(), false,
                    client.world.getRegistryManager());
            client.setScreen(new net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen(client.player,
                    client.player.networkHandler.getEnabledFeatures(), false));
        }, 10));
        steps.add(new Step("our tab", () -> {
            Screen screen = client.currentScreen;
            if (screen instanceof net.fabricmc.fabric.impl.client.itemgroup.CreativeGuiExtensions pages) pages.fabric_nextPage();
            try {
                java.lang.reflect.Method m = net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen.class
                        .getDeclaredMethod("setSelectedTab", net.minecraft.item.ItemGroup.class);
                m.setAccessible(true);
                m.invoke(screen, net.minecraft.registry.Registries.ITEM_GROUP.get(net.feelix.stagecoach.Stagecoach.id("stagecoaches")));
            } catch (ReflectiveOperationException e) {
                LOG.warn("[devshots] could not select the tab", e);
            }
        }, 10));
        snap(client, "compat_creative_tab", 2);
        steps.add(new Step("close", () -> client.setScreen(null), 5));

        // spectator, so the camera stays put (a creative player on the ground stops flying)
        steps.add(new Step("spectator camera", () -> server.execute(() ->
                server.getPlayerManager().getPlayerList().get(0).changeGameMode(GameMode.SPECTATOR)), 10));
        // the camera stands in the section north of the controller's, so that whole section is culled
        Vec3d offEye = new Vec3d(coachC.getX() + 0.5, y + 4.5, coachC.getZ() - 0.9);
        steps.add(new Step("offscreen fov", () -> client.options.getFov().setValue(50), 1));
        look(server, offEye.subtract(0, client.player.getStandingEyeHeight(), 0), new Vec3d(coachC.getX() + 0.5, y + 1, coachC.getZ() - 3.0), 40);
        steps.add(new Step("hide hud", () -> client.options.hudHidden = true, 5));
        snap(client, "compat_sodium_offscreen", 2);
        steps.add(new Step("control renderer", () -> {
            BlockEntityRendererFactories.register(StagecoachRegistry.BLOCK_ENTITY, ctx -> new StagecoachRenderer() {
                @Override
                public boolean rendersOutsideBoundingBox(StagecoachBlockEntity blockEntity) {
                    return false;
                }
            });
            client.reloadResources();
        }, 240));
        steps.add(new Step("rebuild chunks", () -> client.worldRenderer.reload(), 80));
        snap(client, "compat_sodium_offscreen_control", 2);
        steps.add(new Step("fov back, hud on, survival", () -> {
            client.options.getFov().setValue(70);
            client.options.hudHidden = false;
            server.execute(() -> server.getPlayerManager().getPlayerList().get(0).changeGameMode(GameMode.SURVIVAL));
        }, 5));

        steps.add(new Step("discover B", () -> server.execute(() ->
                StagecoachService.onUse(world, coachB, server.getPlayerManager().getPlayerList().get(0))), 10));
        steps.add(new Step("stand at A", () -> server.execute(() -> server.getPlayerManager().getPlayerList().get(0)
                .teleport(world, base.getX() + 0.5, y, base.getZ() + 6.5, 180f, 0f)), 20));
        steps.add(new Step("travel", () -> ClientNet.travel(IDS[0], IDS[1]), 50));
        snap(client, "compat_travel", 2);
        steps.add(new Step("arrive", () -> {}, 90));
        logState(server, "compat after the trip (expect SURVIVAL, 28 levels, in front of Hendry's Halt)");
        snap(client, "compat_arrived", 2);
        steps.add(new Step("flush screenshots", () -> {}, 40));
    }

    private void look(MinecraftServer server, Vec3d feet, Vec3d target, int settle) {
        steps.add(new Step("look at " + target, () -> server.execute(() -> {
            ServerPlayerEntity p = server.getPlayerManager().getPlayerList().get(0);
            Vec3d eye = feet.add(0, p.getStandingEyeHeight(), 0);
            Vec3d d = target.subtract(eye);
            float yaw = (float) (MathHelper.atan2(d.z, d.x) * 180 / Math.PI) - 90f;
            float pitch = (float) -(MathHelper.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * 180 / Math.PI);
            p.networkHandler.requestTeleport(feet.x, feet.y, feet.z, yaw, pitch);
        }), settle));
    }

    private static long placeAt(ServerPlayerEntity p, ServerWorld world, BlockPos c, StagecoachWood wood) {
        ItemStack stack = new ItemStack(StagecoachRegistry.ITEMS.get(wood));
        p.setStackInHand(Hand.MAIN_HAND, stack);
        p.teleport(world, c.getX() + 0.5, c.getY() + 3, c.getZ() + 9.5, Direction.NORTH.asRotation(), 0);
        p.setYaw(Direction.NORTH.asRotation());
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(c.down()).add(0, 0.5, 0), Direction.UP, c.down(), false);
        var why = net.feelix.stagecoach.block.StagecoachStructure.check(new net.minecraft.item.ItemPlacementContext(p, Hand.MAIN_HAND, stack, hit),
                StagecoachRegistry.BLOCKS.get(wood).getDefaultState().with(StagecoachBlock.FACING, Direction.NORTH), null);
        p.interactionManager.interactBlock(p, world, stack, Hand.MAIN_HAND, hit);
        long id = world.getBlockEntity(c) instanceof StagecoachBlockEntity be ? be.getCoachId() : -1;
        LOG.info("[devshots] placed {} at {} -> id {}{}", wood, c, id, why == null ? "" : " (refused: " + why.message().getString() + ")");
        return id;
    }

    /** Computed the way DestinationScreen#init does. */
    private static int[] listGeometry(Screen s) {
        int pw = Math.min(220, s.width - 8), ph = Math.min(200, s.height - 8);
        return new int[] {(s.width - pw) / 2, (s.height - ph) / 2};
    }

    private void snap(MinecraftClient client, String name, int after) {
        steps.add(new Step("screenshot " + name, () -> ScreenshotRecorder.saveScreenshot(client.runDirectory,
                "stagecoach_" + name + ".png", client.getFramebuffer(), text -> LOG.info("[devshots] {}", text.getString())), after));
    }

    private void timeStep(MinecraftServer server, ServerWorld world, long time, String label) {
        steps.add(new Step("time " + label, () -> server.execute(() -> world.setTimeOfDay(time)), 25));
    }

    private void logState(MinecraftServer server, String label) {
        steps.add(new Step("log " + label, () -> server.execute(() -> {
            ServerPlayerEntity p = server.getPlayerManager().getPlayerList().get(0);
            LOG.info("[devshots] STATE {}: mode {}, levels {}, pos {}, travelling {}", label, p.interactionManager.getGameMode(),
                    p.experienceLevel, p.getBlockPos(), JourneyManager.isTravelling(p.getUuid()));
        }), 2));
    }

    private void shot(MinecraftClient client, String name, boolean withOverlay, BlockPos eye, Vec3d target, int settle) {
        shot(client, name, withOverlay, Vec3d.ofCenter(eye), target, settle);
    }

    private void shot(MinecraftClient client, String name, boolean withOverlay, Vec3d eye, Vec3d target, int settle) {
        steps.add(new Step("camera " + name, () -> {
            overlay = withOverlay;
            if (client.currentScreen != null) client.setScreen(null);
            if (!name.equals("items")) client.options.hudHidden = true;
            Vec3d d = target.subtract(eye);
            float yaw = (float) (MathHelper.atan2(d.z, d.x) * 180 / Math.PI) - 90f;
            float pitch = (float) -(MathHelper.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * 180 / Math.PI);
            double feet = eye.y - client.player.getStandingEyeHeight();
            client.getServer().execute(() -> client.getServer().getPlayerManager().getPlayerList().get(0)
                    .networkHandler.requestTeleport(eye.x, feet, eye.z, yaw, pitch));
        }, settle));
        steps.add(new Step("screenshot " + name, () -> {
            LOG.info("[devshots] {}: wanted eye {}, camera at {}, yaw {} pitch {}", name, eye,
                    client.gameRenderer.getCamera().getPos(), client.player.getYaw(), client.player.getPitch());
            ScreenshotRecorder.saveScreenshot(client.runDirectory, "stagecoach_" + name + ".png", client.getFramebuffer(),
                    text -> LOG.info("[devshots] {}", text.getString()));
        }, 2));
    }

    /** From the world's own outline shapes, not the generated data. */
    private static void drawCollision(WorldRenderContext ctx) {
        if (!overlay || ctx.consumers() == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        Vec3d cam = ctx.camera().getPos();
        VertexConsumer lines = ctx.consumers().getBuffer(RenderLayer.getLines());
        MatrixStack.Entry entry = ctx.matrixStack().peek();
        for (Coach c : COACHES) {
            for (int i = 0; i < Footprint.CELL_COUNT; i++) {
                BlockPos p = StagecoachShapes.cellPos(c.pos(), c.facing(), i);
                BlockState s = mc.world.getBlockState(p);
                double ox = p.getX() - cam.x, oy = p.getY() - cam.y, oz = p.getZ() - cam.z;
                s.getOutlineShape(mc.world, p).forEachEdge((x1, y1, z1, x2, y2, z2) -> {
                    float dx = (float) (x2 - x1), dy = (float) (y2 - y1), dz = (float) (z2 - z1);
                    float len = MathHelper.sqrt(dx * dx + dy * dy + dz * dz);
                    dx /= len; dy /= len; dz /= len;
                    lines.vertex(entry.getPositionMatrix(), (float) (x1 + ox), (float) (y1 + oy), (float) (z1 + oz))
                            .color(1f, 0.1f, 0.1f, 1f).normal(entry.getNormalMatrix(), dx, dy, dz).next();
                    lines.vertex(entry.getPositionMatrix(), (float) (x2 + ox), (float) (y2 + oy), (float) (z2 + oz))
                            .color(1f, 0.1f, 0.1f, 1f).normal(entry.getNormalMatrix(), dx, dy, dz).next();
                });
            }
        }
    }

    private static Step frustumCheck(MinecraftClient client, BlockPos oc, String name) {
        return new Step(name + " frustum check", () -> {
            Frustum frustum = frustum(client.worldRenderer);
            int sx = oc.getX() >> 4 << 4, sy = oc.getY() >> 4 << 4, sz = oc.getZ() >> 4 << 4;
            boolean sectionVisible = frustum.isVisible(new Box(sx, sy, sz, sx + 16, sy + 16, sz + 16));
            boolean frontVisible = frustum.isVisible(new Box(oc.getX(), oc.getY(), oc.getZ() - 2.3, oc.getX() + 1, oc.getY() + 1.5, oc.getZ() - 1.5));
            LOG.info("[devshots] {}: controller section {} in frustum = {}; coach front in frustum = {}",
                    name, new BlockPos(sx, sy, sz), sectionVisible, frontVisible);
            // vanilla draws every block entity in this set each frame, whatever section culling says
            Object be = client.world.getBlockEntity(oc);
            LOG.info("[devshots] {}: controller block entity in WorldRenderer.noCullingBlockEntities = {}",
                    name, noCulling(client.worldRenderer).contains(be));
            client.options.getFov().setValue(70);
        }, 5);
    }

    @SuppressWarnings("unchecked")
    private static java.util.Set<Object> noCulling(WorldRenderer renderer) {
        try {
            Field f = WorldRenderer.class.getDeclaredField("noCullingBlockEntities");
            f.setAccessible(true);
            return (java.util.Set<Object>) f.get(renderer);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Frustum frustum(WorldRenderer renderer) {
        try {
            Field f = WorldRenderer.class.getDeclaredField("frustum");
            f.setAccessible(true);
            return (Frustum) f.get(renderer);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
