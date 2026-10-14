package net.feelix.stagecoach.gametest;

import com.mojang.authlib.GameProfile;
import net.feelix.stagecoach.StagecoachRegistry;
import net.feelix.stagecoach.StagecoachWood;
import net.feelix.stagecoach.block.StagecoachBlockEntity;
import net.feelix.stagecoach.config.StagecoachConfig;
import net.feelix.stagecoach.footprint.Footprint;
import net.feelix.stagecoach.footprint.StagecoachShapes;
import net.feelix.stagecoach.net.Packets;
import net.feelix.stagecoach.travel.CoachRecord;
import net.feelix.stagecoach.travel.Journey;
import net.feelix.stagecoach.travel.JourneyManager;
import net.feelix.stagecoach.travel.StagecoachService;
import net.feelix.stagecoach.travel.StagecoachState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.c2s.play.SpectatorTeleportC2SPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.UUID;

public class TravelGameTests {
    static final String ROAD = "stagecoach:gametest/road";
    // relative y 1 is the stone floor: the structure sits one block above the structure block
    /** Facing north it fills x 3..5, z 5..10; the hold and return point is at z 4. */
    static final BlockPos A = new BlockPos(4, 2, 8);
    /** 12 blocks east: 2 levels, and the 5 s minimum journey. */
    static final BlockPos B = new BlockPos(16, 2, 8);

    @GameTest(templateName = ROAD)
    public void placingRegistersOwnsAndDiscovers(TestContext ctx) {
        ServerPlayerEntity owner = connect(ctx, "owner");
        CoachRecord a = place(ctx, owner, A, StagecoachWood.OAK);
        StagecoachState s = state(ctx);
        check(ctx, a.isOwner(owner.getUuid()), "the placer owns it");
        check(ctx, a.name.equals("Stagecoach " + a.id), "default name, got " + a.name);
        check(ctx, s.hasDiscovered(owner.getUuid(), a.id), "the placer has it discovered");
        check(ctx, ((StagecoachBlockEntity) ctx.getWorld().getBlockEntity(ctx.getAbsolutePos(A))).getCoachId() == a.id, "the block entity carries the id");
        disconnect(owner);
        ctx.complete();
    }

    @GameTest(templateName = ROAD)
    public void rightClickDiscoversAndBreakingForgetsEverywhere(TestContext ctx) {
        ServerPlayerEntity owner = connect(ctx, "owner"), visitor = connect(ctx, "visitor");
        CoachRecord a = place(ctx, owner, A, StagecoachWood.SPRUCE);
        StagecoachState s = state(ctx);
        check(ctx, !s.hasDiscovered(visitor.getUuid(), a.id), "not discovered before the right-click");
        standAt(ctx, visitor, A.south(3));
        StagecoachService.onUse(ctx.getWorld(), ctx.getAbsolutePos(A), visitor);
        check(ctx, s.hasDiscovered(visitor.getUuid(), a.id), "a right-click discovers it");

        visitor.interactionManager.tryBreakBlock(StagecoachShapes.cellPos(ctx.getAbsolutePos(A), Direction.NORTH, 1));
        check(ctx, s.coach(a.id) != null && ctx.getWorld().getBlockState(ctx.getAbsolutePos(A)).isOf(StagecoachRegistry.BLOCKS.get(StagecoachWood.SPRUCE)),
                "a non-owner must not be able to break it");
        owner.interactionManager.tryBreakBlock(StagecoachShapes.cellPos(ctx.getAbsolutePos(A), Direction.NORTH, 1));
        check(ctx, s.coach(a.id) == null, "breaking removes it from the registry");
        check(ctx, !s.hasDiscovered(visitor.getUuid(), a.id) && !s.hasDiscovered(owner.getUuid(), a.id), "and from every discovered list");
        disconnect(owner);
        disconnect(visitor);
        ctx.complete();
    }

    @GameTest(templateName = ROAD)
    public void renameIsOwnerOnlyTrimmedAndCapped(TestContext ctx) {
        ServerPlayerEntity owner = connect(ctx, "owner"), other = connect(ctx, "other");
        CoachRecord a = place(ctx, owner, A, StagecoachWood.BIRCH);
        standAt(ctx, owner, A.south(3));
        standAt(ctx, other, A.south(3));
        StagecoachService.rename(other, new Packets.SetName(a.id, "Mine Now", false));
        check(ctx, !a.name.equals("Mine Now"), "a non-owner can't rename");
        StagecoachService.rename(owner, new Packets.SetName(a.id, "   The   Twins  ", false));
        check(ctx, a.name.equals("The Twins"), "trimmed and collapsed, got '" + a.name + "'");
        StagecoachService.rename(owner, new Packets.SetName(a.id, "abcdefghijklmnopqrstuvwxy", false));
        check(ctx, a.name.equals("The Twins"), "25 characters must be refused");
        StagecoachService.rename(owner, new Packets.SetName(a.id, "    ", false));
        check(ctx, a.name.equals("The Twins"), "an empty name must be refused");
        disconnect(owner);
        disconnect(other);
        ctx.complete();
    }

    @GameTest(templateName = ROAD)
    public void theListShowsCostTimeAndAffordability(TestContext ctx) {
        ServerPlayerEntity owner = connect(ctx, "owner");
        CoachRecord a = place(ctx, owner, A, StagecoachWood.OAK), b = place(ctx, owner, B, StagecoachWood.OAK);
        owner.setExperienceLevel(1);
        Packets.DestinationList list = StagecoachService.buildList(owner, a);
        check(ctx, list.entries().stream().noneMatch(x -> x.id() == a.id), "the coach itself isn't listed");
        Packets.Entry e = PackGameTests.entry(ctx, owner, a, b);
        check(ctx, e.distance() == 12 && e.cost() == 2 && e.seconds() == 5 && e.owned(), "12 blocks, 2 levels, 5 s, owned: " + e);
        check(ctx, e.status() == Packets.STATUS_CANT_AFFORD, "level 1 can't afford 2");
        owner.setExperienceLevel(2);
        check(ctx, PackGameTests.entry(ctx, owner, a, b).status() == Packets.STATUS_OK, "level 2 can");
        check(ctx, list.canRename(), "the owner gets the rename button");
        disconnect(owner);
        ctx.complete();
    }

    @GameTest(templateName = ROAD, tickLimit = 320)
    public void travelIsRefusedWithoutXpInCombatFarAwayOrUndiscovered(TestContext ctx) {
        ServerPlayerEntity owner = connect(ctx, "owner"), stranger = connect(ctx, "stranger");
        CoachRecord a = place(ctx, owner, A, StagecoachWood.OAK), b = place(ctx, owner, B, StagecoachWood.OAK);
        standAt(ctx, owner, A.south(3));
        owner.setExperienceLevel(1);
        StagecoachService.travel(owner, new Packets.Travel(a.id, b.id));
        check(ctx, !JourneyManager.isTravelling(owner.getUuid()), "no XP -> no journey");

        standAt(ctx, stranger, A.south(3));
        stranger.setExperienceLevel(30);
        StagecoachService.travel(stranger, new Packets.Travel(a.id, b.id));
        check(ctx, !JourneyManager.isTravelling(stranger.getUuid()), "coaches the player never discovered -> no journey");

        StagecoachService.onUse(ctx.getWorld(), ctx.getAbsolutePos(A), stranger);
        StagecoachService.onUse(ctx.getWorld(), ctx.getAbsolutePos(B), stranger);
        stranger.teleport(ctx.getWorld(), stranger.getX() + 40, stranger.getY(), stranger.getZ(), 0, 0);
        StagecoachService.travel(stranger, new Packets.Travel(a.id, b.id));
        check(ctx, !JourneyManager.isTravelling(stranger.getUuid()), "40 blocks from the origin -> no journey");

        // vanilla ignores damage for 60 ticks after joining, so the hit comes after that
        ctx.runAtTick(70, () -> {
            owner.setExperienceLevel(30);
            ZombieEntity zombie = EntityType.ZOMBIE.create(ctx.getWorld());
            check(ctx, owner.damage(ctx.getWorld().getDamageSources().mobAttack(zombie), 1.0f), "the hit must land");
            check(ctx, JourneyManager.combatLockSecondsLeft(owner) == 10, "hit by a mob -> 10 s combat lock, got " + JourneyManager.combatLockSecondsLeft(owner));
            StagecoachService.travel(owner, new Packets.Travel(a.id, b.id));
            check(ctx, !JourneyManager.isTravelling(owner.getUuid()), "combat lock -> no journey");
        });
        // the 10 s lock is over by now
        ctx.runAtTick(70 + 201, () -> {
            check(ctx, JourneyManager.combatLockSecondsLeft(owner) == 0, "lock over after 10 s");
            StagecoachService.travel(owner, new Packets.Travel(a.id, b.id));
            check(ctx, JourneyManager.isTravelling(owner.getUuid()), "no longer locked -> the journey starts");
            JourneyManager.cancel(owner);
            disconnect(owner);
            disconnect(stranger);
            ctx.complete();
        });
    }

    @GameTest(templateName = ROAD, tickLimit = 300)
    public void aSurvivalJourneyFreezesPaysAndArrivesInSurvival(TestContext ctx) {
        ServerPlayerEntity p = connect(ctx, "traveller");
        CoachRecord a = place(ctx, p, A, StagecoachWood.OAK), b = place(ctx, p, B, StagecoachWood.DARK_OAK);
        standAt(ctx, p, A.south(3));
        p.setExperienceLevel(5);
        StagecoachService.travel(p, new Packets.Travel(a.id, b.id));
        Journey j = JourneyManager.journeyOf(p);
        check(ctx, j != null && JourneyManager.isTravelling(p.getUuid()), "the journey started");
        check(ctx, p.interactionManager.getGameMode() == GameMode.SPECTATOR, "on the road = spectator");
        check(ctx, p.experienceLevel == 3, "paid 2 levels, has " + p.experienceLevel);
        check(ctx, j.durationTicks == 100, "12 blocks -> the 5 s floor = 100 ticks, got " + j.durationTicks);
        check(ctx, j.savedMode == GameMode.SURVIVAL, "survival saved");

        // a modified client wanders off; the server pulls them back
        p.teleport(ctx.getWorld(), p.getX() + 9, p.getY() + 5, p.getZ() - 7, 0, 0);
        PigEntity pig = ctx.spawnEntity(EntityType.PIG, new BlockPos(12, 2, 20));
        p.setCameraEntity(pig);
        check(ctx, p.getCameraEntity() == p, "can't attach the camera to another entity on the road");
        ctx.runAtTick(3, () -> check(ctx, p.getPos().squaredDistanceTo(j.holdPoint) < 0.01, "held at the origin"));

        ctx.runAtTick(j.durationTicks + 6, () -> {
            check(ctx, !JourneyManager.isTravelling(p.getUuid()) && JourneyManager.journeyOf(p) == null, "journey over, record gone");
            check(ctx, p.interactionManager.getGameMode() == GameMode.SURVIVAL, "arrived in survival, got " + p.interactionManager.getGameMode());
            check(ctx, !p.getAbilities().allowFlying && !p.getAbilities().flying, "survival abilities (no flight)");
            check(ctx, p.experienceLevel == 3, "no refund on arrival");
            BlockPos bAbs = ctx.getAbsolutePos(B);
            check(ctx, p.getBlockPos().getSquaredDistance(bAbs.north(4)) <= 36, "put down in front of the destination, at " + p.getBlockPos());
            check(ctx, ctx.getWorld().isSpaceEmpty(p), "not inside any block or the coach");
            pig.discard();
            disconnect(p);
            ctx.complete();
        });
    }

    @GameTest(templateName = ROAD, tickLimit = 300)
    public void aCreativeJourneyIsFreeAndArrivesInCreativeStillFlying(TestContext ctx) {
        ServerPlayerEntity p = connect(ctx, "creative");
        p.changeGameMode(GameMode.CREATIVE);
        p.getAbilities().flying = true;
        CoachRecord a = place(ctx, p, A, StagecoachWood.CHERRY), b = place(ctx, p, B, StagecoachWood.BAMBOO);
        standAt(ctx, p, A.south(3));
        p.setExperienceLevel(0);
        StagecoachService.travel(p, new Packets.Travel(a.id, b.id));
        check(ctx, JourneyManager.isTravelling(p.getUuid()), "creative can travel with 0 levels");
        ctx.runAtTick(110, () -> {
            check(ctx, p.interactionManager.getGameMode() == GameMode.CREATIVE, "arrived in creative");
            check(ctx, p.getAbilities().allowFlying && p.getAbilities().flying, "creative flight restored as it was");
            disconnect(p);
            ctx.complete();
        });
    }

    @GameTest(templateName = ROAD)
    public void cancellingRefundsAndReturnsToTheOrigin(TestContext ctx) {
        ServerPlayerEntity p = connect(ctx, "canceller");
        Journey j = startJourney(ctx, p, 7);
        JourneyManager.cancel(p);
        expectBackAtOrigin(ctx, p, GameMode.SURVIVAL, 7);
        check(ctx, JourneyManager.journeyOf(p) == null && j.xpPaid == 2, "record gone");
        disconnect(p);
        ctx.complete();
    }

    @GameTest(templateName = ROAD)
    public void breakingTheDestinationMidJourneyCancelsWithARefund(TestContext ctx) {
        ServerPlayerEntity p = connect(ctx, "dest-gone");
        startJourney(ctx, p, 7);
        // not a player: no owner check
        ctx.getWorld().setBlockState(ctx.getAbsolutePos(B), Blocks.AIR.getDefaultState());
        ctx.runAtTick(3, () -> {
            expectBackAtOrigin(ctx, p, GameMode.SURVIVAL, 7);
            disconnect(p);
            ctx.complete();
        });
    }

    @GameTest(templateName = ROAD, tickLimit = 300)
    public void breakingTheOriginMidJourneyDoesNotStopIt(TestContext ctx) {
        ServerPlayerEntity p = connect(ctx, "origin-gone");
        Journey j = startJourney(ctx, p, 7);
        ctx.getWorld().setBlockState(ctx.getAbsolutePos(A), Blocks.AIR.getDefaultState());
        ctx.runAtTick(5, () -> check(ctx, JourneyManager.isTravelling(p.getUuid()), "still on the road"));
        ctx.runAtTick(j.durationTicks + 6, () -> {
            check(ctx, p.interactionManager.getGameMode() == GameMode.SURVIVAL && p.experienceLevel == 5, "arrived, paid");
            check(ctx, p.getBlockPos().getSquaredDistance(ctx.getAbsolutePos(B).north(4)) <= 36, "at the destination");
            disconnect(p);
            ctx.complete();
        });
    }

    @GameTest(templateName = ROAD)
    public void anOutsideGameModeChangeWinsAndCancels(TestContext ctx) {
        ServerPlayerEntity p = connect(ctx, "gamemode");
        startJourney(ctx, p, 7);
        p.changeGameMode(GameMode.ADVENTURE); // what /gamemode does
        ctx.runAtTick(3, () -> {
            expectBackAtOrigin(ctx, p, GameMode.ADVENTURE, 7);
            disconnect(p);
            ctx.complete();
        });
    }

    @GameTest(templateName = ROAD)
    public void dyingOnTheRoadRestoresModeAndXpAtRespawn(TestContext ctx) {
        ServerPlayerEntity p = connect(ctx, "dies");
        startJourney(ctx, p, 7);
        p.kill();
        ServerPlayerEntity respawned = ctx.getWorld().getServer().getPlayerManager().respawnPlayer(p, false, net.minecraft.entity.Entity.RemovalReason.KILLED);
        check(ctx, respawned.interactionManager.getGameMode() == GameMode.SURVIVAL, "respawned in survival, got " + respawned.interactionManager.getGameMode());
        check(ctx, respawned.experienceLevel == 7, "refunded after death, has " + respawned.experienceLevel);
        check(ctx, !JourneyManager.isTravelling(respawned.getUuid()) && JourneyManager.journeyOf(respawned) == null, "journey over");
        disconnect(respawned);
        ctx.complete();
    }

    /** The real vanilla path: onDisconnected, then PlayerManager#remove saves the file. */
    @GameTest(templateName = ROAD)
    public void disconnectingMidJourneyRestoresBeforeTheSaveAndRefunds(TestContext ctx) {
        ServerPlayerEntity p = connect(ctx, "quitter");
        GameProfile profile = p.getGameProfile();
        startJourney(ctx, p, 7);
        p.networkHandler.onDisconnected(new net.minecraft.network.DisconnectionInfo(Text.literal("test")));
        check(ctx, JourneyManager.journeyOf(p) == null && !JourneyManager.isTravelling(p.getUuid()), "journey ended on disconnect");
        // what was saved must already be survival, refunded and at the origin
        ServerPlayerEntity back = connect(ctx, profile);
        expectBackAtOrigin(ctx, back, GameMode.SURVIVAL, 7);
        disconnect(back);
        ctx.complete();
    }

    /** Fabric fires DISCONNECT on the Netty thread, so leaving is handled in PlayerManager#remove. */
    @GameTest(templateName = ROAD)
    public void removingATravellerEndsTheJourneyBeforeTheirFileIsSaved(TestContext ctx) {
        ServerPlayerEntity p = connect(ctx, "removed");
        GameProfile profile = p.getGameProfile();
        startJourney(ctx, p, 7);
        ctx.getWorld().getServer().getPlayerManager().remove(p);
        check(ctx, JourneyManager.journeyOf(p) == null && !JourneyManager.isTravelling(p.getUuid()), "removal must end the journey");
        ServerPlayerEntity back = connect(ctx, profile);
        expectBackAtOrigin(ctx, back, GameMode.SURVIVAL, 7);
        disconnect(back);
        ctx.complete();
    }

    /** No handler of ours runs in a crash; the saved file still says spectator. */
    @GameTest(templateName = ROAD)
    public void aCrashMidJourneyIsUndoneAtTheNextLogin(TestContext ctx) {
        ServerPlayerEntity p = connect(ctx, "crash");
        GameProfile profile = p.getGameProfile();
        startJourney(ctx, p, 7);
        crash(ctx, p);
        ServerPlayerEntity back = connect(ctx, profile);
        expectBackAtOrigin(ctx, back, GameMode.SURVIVAL, 7);
        check(ctx, JourneyManager.journeyOf(back) == null, "the leftover record is deleted");
        disconnect(back);
        ctx.complete();
    }

    @GameTest(templateName = ROAD)
    public void aCrashIsUndoneFromThePlayerFileAloneIfTheStateLostIt(TestContext ctx) {
        ServerPlayerEntity p = connect(ctx, "crash-mirror");
        GameProfile profile = p.getGameProfile();
        startJourney(ctx, p, 7);
        crash(ctx, p);
        state(ctx).removeJourney(profile.getId());
        ServerPlayerEntity back = connect(ctx, profile);
        expectBackAtOrigin(ctx, back, GameMode.SURVIVAL, 7);
        disconnect(back);
        ctx.complete();
    }

    @GameTest(templateName = ROAD)
    public void theNetworkStateSurvivesSaveAndLoad(TestContext ctx) {
        ServerPlayerEntity p = connect(ctx, "persist");
        Journey j = startJourney(ctx, p, 7);
        StagecoachState s = state(ctx);
        CoachRecord origin = s.coach(j.originId);
        s.rename(origin, "Riverrun");
        var registries = ctx.getWorld().getRegistryManager();
        StagecoachState copy = StagecoachState.fromNbt(s.writeNbt(new net.minecraft.nbt.NbtCompound(), registries), registries);
        CoachRecord o2 = copy.coach(j.originId);
        check(ctx, o2 != null && o2.name.equals("Riverrun") && o2.isOwner(p.getUuid()) && o2.pos.equals(origin.pos)
                && o2.facing == origin.facing && o2.dimension == origin.dimension, "coach record round-trips");
        check(ctx, copy.hasDiscovered(p.getUuid(), j.originId) && copy.hasDiscovered(p.getUuid(), j.destId), "discovered list round-trips");
        Journey j2 = copy.journey(p.getUuid());
        check(ctx, j2 != null && j2.savedMode == GameMode.SURVIVAL && j2.xpLevel == 7 && j2.xpPaid == 2 && j2.destId == j.destId
                && j2.startTime == j.startTime && j2.durationTicks == j.durationTicks && j2.phase == Journey.Phase.TRAVELLING, "journey round-trips");
        JourneyManager.cancel(p);
        disconnect(p);
        ctx.complete();
    }

    @GameTest(templateName = ROAD)
    public void spectatorTeleportsAreBlockedOnlyForTravellers(TestContext ctx) {
        ServerPlayerEntity traveller = connect(ctx, "locked"), plain = connect(ctx, "free-spec"), target = connect(ctx, "target");
        standAt(ctx, target, new BlockPos(20, 2, 20));
        startJourney(ctx, traveller, 7);
        Vec3d before = traveller.getPos();
        traveller.networkHandler.onSpectatorTeleport(new SpectatorTeleportC2SPacket(target.getUuid()));
        check(ctx, traveller.getPos().squaredDistanceTo(before) < 0.01, "a traveller's spectator teleport is dropped");

        plain.changeGameMode(GameMode.SPECTATOR);
        standAt(ctx, plain, new BlockPos(2, 2, 20));
        plain.networkHandler.onSpectatorTeleport(new SpectatorTeleportC2SPacket(target.getUuid()));
        check(ctx, plain.getPos().squaredDistanceTo(target.getPos()) < 0.01, "an ordinary spectator can still teleport");
        disconnect(traveller);
        disconnect(plain);
        disconnect(target);
        ctx.complete();
    }

    /** Cancel gives back the very same stacks, not just the same count. */
    @GameTest(templateName = ROAD)
    public void anItemFareIsPaidAndRefundedInItems(TestContext ctx) {
        ServerPlayerEntity p = connect(ctx, "items");
        CoachRecord a = place(ctx, p, A, StagecoachWood.OAK), b = place(ctx, p, B, StagecoachWood.OAK);
        standAt(ctx, p, A.south(3));
        p.setExperienceLevel(0);
        p.getInventory().clear();
        p.getInventory().insertStack(new ItemStack(Items.EMERALD, 10));
        withConfig("{\"itemCostEnabled\": true, \"costItem\": \"minecraft:emerald\"}", () -> {
            check(ctx, PackGameTests.entry(ctx, p, a, b).cost() == 2, "12 blocks = 1 + ceil(12/1000) = 2 emeralds");
            StagecoachService.travel(p, new Packets.Travel(a.id, b.id));
            check(ctx, JourneyManager.isTravelling(p.getUuid()), "paid in emeralds with 0 levels");
            check(ctx, p.getInventory().count(Items.EMERALD) == 8, "2 emeralds taken, has " + p.getInventory().count(Items.EMERALD));
            JourneyManager.cancel(p);
        });
        check(ctx, p.getInventory().count(Items.EMERALD) == 10, "both back after cancelling, has " + p.getInventory().count(Items.EMERALD));
        disconnect(p);
        ctx.complete();
    }

    /** An unknown id falls back to XP, never to free travel. */
    @GameTest(templateName = ROAD)
    public void costItemTakesTagsAndFallsBackToXpForAnUnknownId(TestContext ctx) {
        ServerPlayerEntity p = connect(ctx, "tags");
        CoachRecord a = place(ctx, p, A, StagecoachWood.OAK), b = place(ctx, p, B, StagecoachWood.OAK);
        standAt(ctx, p, A.south(3));
        p.getInventory().clear();
        p.getInventory().insertStack(new ItemStack(Items.BIRCH_LOG, 5));
        p.setExperienceLevel(5);
        withConfig("{\"itemCostEnabled\": true, \"costItem\": \"#minecraft:logs\"}", () -> {
            StagecoachService.travel(p, new Packets.Travel(a.id, b.id));
            check(ctx, p.getInventory().count(Items.BIRCH_LOG) == 3 && p.experienceLevel == 5, "paid 2 logs through the tag");
            JourneyManager.cancel(p);
        });
        withConfig("{\"itemCostEnabled\": true, \"costItem\": \"nosuchmod:nothing\"}", () -> {
            StagecoachService.travel(p, new Packets.Travel(a.id, b.id));
            check(ctx, p.experienceLevel == 3 && p.getInventory().count(Items.BIRCH_LOG) == 5, "an unknown item costs XP instead");
            JourneyManager.cancel(p);
        });
        disconnect(p);
        ctx.complete();
    }

    /** The refund share applies to the player's own Cancel only. */
    @GameTest(templateName = ROAD)
    public void cancellingCanKeepPartOfTheFare(TestContext ctx) {
        ServerPlayerEntity p = connect(ctx, "half");
        withConfig("{\"cancelRefundPercent\": 50, \"baseCost\": 4}", () -> {
            CoachRecord a = place(ctx, p, A, StagecoachWood.OAK), b = place(ctx, p, B, StagecoachWood.OAK);
            standAt(ctx, p, A.south(3));
            p.setExperienceLevel(9);
            StagecoachService.travel(p, new Packets.Travel(a.id, b.id));
            check(ctx, p.experienceLevel == 4, "paid 5 levels (4 + 1), has " + p.experienceLevel);
            JourneyManager.cancel(p);
            check(ctx, p.experienceLevel == 6, "half of 5, rounded down, comes back: 4 + 2, has " + p.experienceLevel);
        });
        disconnect(p);
        ctx.complete();
    }

    @GameTest(templateName = ROAD)
    public void aStopBeyondMaxDistanceIsListedAndRefused(TestContext ctx) {
        ServerPlayerEntity p = connect(ctx, "too-far");
        CoachRecord a = place(ctx, p, A, StagecoachWood.OAK), b = place(ctx, p, B, StagecoachWood.OAK);
        standAt(ctx, p, A.south(3));
        p.setExperienceLevel(30);
        withConfig("{\"maxDistance\": 10}", () -> {
            check(ctx, PackGameTests.entry(ctx, p, a, b).status() == Packets.STATUS_TOO_FAR, "12 blocks > 10");
            StagecoachService.travel(p, new Packets.Travel(a.id, b.id));
            check(ctx, !JourneyManager.isTravelling(p.getUuid()) && p.experienceLevel == 30, "refused, nothing paid");
        });
        withConfig("{\"maxDistance\": 12}", () ->
                check(ctx, PackGameTests.entry(ctx, p, a, b).status() == Packets.STATUS_OK, "12 blocks <= 12"));
        disconnect(p);
        ctx.complete();
    }

    @GameTest(templateName = ROAD)
    public void instantTravelArrivesAtOnceAndTheCooldownHolds(TestContext ctx) {
        ServerPlayerEntity p = connect(ctx, "instant");
        CoachRecord a = place(ctx, p, A, StagecoachWood.OAK), b = place(ctx, p, B, StagecoachWood.OAK);
        standAt(ctx, p, A.south(3));
        p.setExperienceLevel(10);
        withConfig("{\"instantTravel\": true, \"travelCooldownSeconds\": 30}", () -> {
            StagecoachService.travel(p, new Packets.Travel(a.id, b.id));
            check(ctx, !JourneyManager.isTravelling(p.getUuid()) && JourneyManager.journeyOf(p) == null, "no journey record");
            check(ctx, p.interactionManager.getGameMode() == GameMode.SURVIVAL && p.experienceLevel == 8, "survival, paid 2");
            check(ctx, p.getBlockPos().getSquaredDistance(ctx.getAbsolutePos(B).north(4)) <= 36, "at the destination, at " + p.getBlockPos());
            check(ctx, JourneyManager.cooldownSecondsLeft(p) == 30, "30 s cooldown, got " + JourneyManager.cooldownSecondsLeft(p));
            StagecoachService.travel(p, new Packets.Travel(b.id, a.id));
            check(ctx, p.experienceLevel == 8 && p.getBlockPos().getSquaredDistance(ctx.getAbsolutePos(B).north(4)) <= 36, "refused during the cooldown");
        });
        disconnect(p);
        ctx.complete();
    }

    @GameTest(templateName = ROAD)
    public void withDiscoveryOffEveryCoachIsKnown(TestContext ctx) {
        ServerPlayerEntity owner = connect(ctx, "owner"), stranger = connect(ctx, "stranger");
        CoachRecord a = place(ctx, owner, A, StagecoachWood.OAK), b = place(ctx, owner, B, StagecoachWood.OAK);
        standAt(ctx, stranger, A.south(3));
        stranger.setExperienceLevel(10);
        withConfig("{\"discoveryRequired\": false}", () -> {
            check(ctx, StagecoachService.buildList(stranger, a).entries().stream().anyMatch(e -> e.id() == b.id), "the other coach is listed");
            StagecoachService.travel(stranger, new Packets.Travel(a.id, b.id));
            check(ctx, JourneyManager.isTravelling(stranger.getUuid()), "and reachable");
            JourneyManager.cancel(stranger);
        });
        disconnect(owner);
        disconnect(stranger);
        ctx.complete();
    }

    @GameTest(templateName = ROAD)
    public void placementLimitsRefuseAndKeepTheItem(TestContext ctx) {
        ServerPlayerEntity p = connect(ctx, "limits");
        withConfig("{\"maxCoachesPerPlayer\": 1}", () -> {
            place(ctx, p, A, StagecoachWood.OAK);
            check(ctx, !tryPlace(ctx, p, B, StagecoachWood.OAK), "a second coach is over the limit");
            check(ctx, p.getMainHandStack().isOf(StagecoachRegistry.ITEMS.get(StagecoachWood.OAK)), "the item is kept");
        });
        withConfig("{\"blockedDimensions\": [\"minecraft:overworld\"]}", () ->
                check(ctx, !tryPlace(ctx, p, B, StagecoachWood.SPRUCE), "no coaches in a blocked dimension"));
        disconnect(p);
        ctx.complete();
    }

    @GameTest(templateName = ROAD)
    public void creativePaysWhenFreeTravelIsOff(TestContext ctx) {
        ServerPlayerEntity p = connect(ctx, "creative-pays");
        CoachRecord a = place(ctx, p, A, StagecoachWood.OAK), b = place(ctx, p, B, StagecoachWood.OAK);
        p.changeGameMode(GameMode.CREATIVE);
        standAt(ctx, p, A.south(3));
        p.setExperienceLevel(5);
        withConfig("{\"creativeTravelsFree\": false}", () -> {
            StagecoachService.travel(p, new Packets.Travel(a.id, b.id));
            check(ctx, p.experienceLevel == 3, "creative paid 2, has " + p.experienceLevel);
            JourneyManager.cancel(p);
        });
        disconnect(p);
        ctx.complete();
    }

    @GameTest(templateName = ROAD)
    public void aCrashWithAnItemFareReturnsTheItemsOnce(TestContext ctx) {
        ServerPlayerEntity p = connect(ctx, "crash-items");
        GameProfile profile = p.getGameProfile();
        CoachRecord a = place(ctx, p, A, StagecoachWood.OAK), b = place(ctx, p, B, StagecoachWood.OAK);
        standAt(ctx, p, A.south(3));
        p.getInventory().clear();
        p.getInventory().insertStack(new ItemStack(Items.EMERALD, 10));
        withConfig("{\"itemCostEnabled\": true}", () -> StagecoachService.travel(p, new Packets.Travel(a.id, b.id)));
        check(ctx, p.getInventory().count(Items.EMERALD) == 8, "paid 2");
        crash(ctx, p);
        ServerPlayerEntity back = connect(ctx, profile);
        check(ctx, back.getInventory().count(Items.EMERALD) == 10, "10 again after recovery, has " + back.getInventory().count(Items.EMERALD));
        check(ctx, back.interactionManager.getGameMode() == GameMode.SURVIVAL, "survival again");
        disconnect(back);
        ctx.complete();
    }

    /** The file was saved with the items back, but the state file still had the journey. */
    @GameTest(templateName = ROAD)
    public void aRecordWithoutTheMirrorNeverReturnsItemsTwice(TestContext ctx) {
        ServerPlayerEntity p = connect(ctx, "no-dupe");
        GameProfile profile = p.getGameProfile();
        CoachRecord a = place(ctx, p, A, StagecoachWood.OAK), b = place(ctx, p, B, StagecoachWood.OAK);
        standAt(ctx, p, A.south(3));
        p.getInventory().clear();
        p.getInventory().insertStack(new ItemStack(Items.EMERALD, 10));
        withConfig("{\"itemCostEnabled\": true}", () -> StagecoachService.travel(p, new Packets.Travel(a.id, b.id)));
        Journey j = JourneyManager.journeyOf(p);
        JourneyManager.cancel(p);
        disconnect(p); // saved with all 10 and no mirror
        state(ctx).putJourney(j); // ...but the state file never heard the journey was over
        ServerPlayerEntity back = connect(ctx, profile);
        check(ctx, back.getInventory().count(Items.EMERALD) == 10, "still exactly 10, has " + back.getInventory().count(Items.EMERALD));
        check(ctx, JourneyManager.journeyOf(back) == null, "the stale record is cleared");
        disconnect(back);
        ctx.complete();
    }

    /** Places A and B, gives {@code levels} and sets off from A to B (cost 2). */
    static Journey startJourney(TestContext ctx, ServerPlayerEntity p, int levels) {
        CoachRecord a = place(ctx, p, A, StagecoachWood.OAK), b = place(ctx, p, B, StagecoachWood.ACACIA);
        standAt(ctx, p, A.south(3));
        p.setExperienceLevel(levels);
        StagecoachService.travel(p, new Packets.Travel(a.id, b.id));
        Journey j = JourneyManager.journeyOf(p);
        check(ctx, j != null && p.interactionManager.getGameMode() == GameMode.SPECTATOR, "journey should have started");
        check(ctx, p.experienceLevel == levels - 2, "paid 2 levels");
        return j;
    }

    static void expectBackAtOrigin(TestContext ctx, ServerPlayerEntity p, GameMode mode, int levels) {
        check(ctx, p.interactionManager.getGameMode() == mode, "mode should be " + mode + ", got " + p.interactionManager.getGameMode());
        check(ctx, p.experienceLevel == levels, "refunded to " + levels + ", has " + p.experienceLevel);
        check(ctx, !JourneyManager.isTravelling(p.getUuid()) && JourneyManager.journeyOf(p) == null, "journey over");
        BlockPos front = ctx.getAbsolutePos(A).north(4);
        check(ctx, p.getBlockPos().getSquaredDistance(front) <= 36, "back in front of the origin, at " + p.getBlockPos());
        check(ctx, ctx.getWorld().isSpaceEmpty(p), "not stuck in a block or the coach");
    }

    static CoachRecord place(TestContext ctx, ServerPlayerEntity owner, BlockPos rel, StagecoachWood wood) {
        ServerWorld world = ctx.getWorld();
        BlockPos c = ctx.getAbsolutePos(rel);
        ItemStack stack = new ItemStack(StagecoachRegistry.ITEMS.get(wood));
        owner.setStackInHand(Hand.MAIN_HAND, stack);
        Vec3d keep = owner.getPos();
        owner.refreshPositionAndAngles(c.getX() + 0.5, c.getY(), c.getZ() + 8.5, Direction.NORTH.asRotation(), 0);
        var result = owner.interactionManager.interactBlock(owner, world, stack, Hand.MAIN_HAND,
                new BlockHitResult(Vec3d.ofCenter(c.down()).add(0, 0.5, 0), Direction.UP, c.down(), false));
        owner.refreshPositionAndAngles(keep.x, keep.y, keep.z, 0, 0);
        if (!(world.getBlockEntity(c) instanceof StagecoachBlockEntity be) || state(ctx).coach(be.getCoachId()) == null) {
            throw new IllegalStateException("placing the " + wood + " coach at " + rel + " failed: " + result + ", block "
                    + world.getBlockState(c) + ", below " + world.getBlockState(c.down()) + ", mode " + owner.interactionManager.getGameMode()
                    + ", entities " + world.getOtherEntities(null, new net.minecraft.util.math.Box(c).expand(4)));
        }
        for (int i = 0; i < Footprint.CELL_COUNT; i++) {
            if (world.getBlockState(StagecoachShapes.cellPos(c, Direction.NORTH, i)).isAir()) throw new IllegalStateException("cell " + i + " missing");
        }
        return state(ctx).coach(be.getCoachId());
    }

    /** Puts the defaults back afterwards; synchronous, so swapping the global config is safe. */
    static void withConfig(String json, Runnable body) {
        StagecoachConfig.set(StagecoachConfig.parse(json, new ArrayList<>()));
        try {
            body.run();
        } finally {
            StagecoachConfig.set(StagecoachConfig.DEFAULTS);
        }
    }

    static boolean tryPlace(TestContext ctx, ServerPlayerEntity owner, BlockPos rel, StagecoachWood wood) {
        ServerWorld world = ctx.getWorld();
        BlockPos c = ctx.getAbsolutePos(rel);
        ItemStack stack = new ItemStack(StagecoachRegistry.ITEMS.get(wood));
        owner.setStackInHand(Hand.MAIN_HAND, stack);
        Vec3d keep = owner.getPos();
        owner.refreshPositionAndAngles(c.getX() + 0.5, c.getY(), c.getZ() + 8.5, Direction.NORTH.asRotation(), 0);
        owner.interactionManager.interactBlock(owner, world, stack, Hand.MAIN_HAND,
                new BlockHitResult(Vec3d.ofCenter(c.down()).add(0, 0.5, 0), Direction.UP, c.down(), false));
        owner.refreshPositionAndAngles(keep.x, keep.y, keep.z, 0, 0);
        return world.getBlockEntity(c) instanceof StagecoachBlockEntity;
    }

    static void standAt(TestContext ctx, ServerPlayerEntity p, BlockPos rel) {
        BlockPos abs = ctx.getAbsolutePos(rel);
        p.teleport(ctx.getWorld(), abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0, 0);
    }

    /** Vanilla's own mock-player recipe, without its always-creative override. */
    static ServerPlayerEntity connect(TestContext ctx, String name) {
        String n = ("sc-" + name).length() > 16 ? ("sc-" + name).substring(0, 16) : "sc-" + name;
        ServerPlayerEntity p = connect(ctx, new GameProfile(UUID.randomUUID(), n));
        p.changeGameMode(GameMode.SURVIVAL); // the GameTest world's default mode is creative
        standAt(ctx, p, new BlockPos(12, 21, 12)); // parked in the air above our own area, out of every footprint
        return p;
    }

    static ServerPlayerEntity connect(TestContext ctx, GameProfile profile) {
        MinecraftServer server = ctx.getWorld().getServer();
        // vanilla TestContext's own recipe: default client data and an in-memory Netty channel
        var data = net.minecraft.server.network.ConnectedClientData.createDefault(profile, false);
        ServerPlayerEntity p = new ServerPlayerEntity(server, ctx.getWorld(), profile, data.syncedOptions());
        ClientConnection connection = new ClientConnection(NetworkSide.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        server.getPlayerManager().onPlayerConnect(connection, p, data);
        return p;
    }

    static void disconnect(ServerPlayerEntity p) {
        if (p.getServer().getPlayerManager().getPlayer(p.getUuid()) == p) p.networkHandler.onDisconnected(new net.minecraft.network.DisconnectionInfo(Text.literal("test over")));
    }

    /** Leaves only what a crash would: the player file as last saved and the state file. */
    static void crash(TestContext ctx, ServerPlayerEntity p) {
        JourneyManager.simulateCrash(p.getUuid());
        ctx.getWorld().getServer().getPlayerManager().remove(p); // a plain vanilla save and removal
    }

    static StagecoachState state(TestContext ctx) {
        return StagecoachState.get(ctx.getWorld().getServer());
    }

    static void check(TestContext ctx, boolean ok, String message) {
        if (!ok) ctx.throwGameTestException(message);
    }
}
