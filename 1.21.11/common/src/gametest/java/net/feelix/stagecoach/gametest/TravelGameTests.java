package net.feelix.stagecoach.gametest;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.UUID;

public class TravelGameTests {
    static final String ROAD = "stagecoach:gametest/road";
    // relative y 1 is the stone floor: the structure sits one block above the structure block
    /** Facing north it fills x 3..5, z 5..10; the hold and return point is at z 4. */
    static final BlockPos A = new BlockPos(4, 2, 8);
    /** 12 blocks east: 2 levels, and the 5 s minimum journey. */
    static final BlockPos B = new BlockPos(16, 2, 8);

    @GameTest(structure = ROAD)
    public void placingRegistersOwnsAndDiscovers(GameTestHelper ctx) {
        ServerPlayer owner = connect(ctx, "owner");
        CoachRecord a = place(ctx, owner, A, StagecoachWood.OAK);
        StagecoachState s = state(ctx);
        check(ctx, a.isOwner(owner.getUUID()), "the placer owns it");
        check(ctx, a.name.equals("Stagecoach " + a.id), "default name, got " + a.name);
        check(ctx, s.hasDiscovered(owner.getUUID(), a.id), "the placer has it discovered");
        check(ctx, ((StagecoachBlockEntity) ctx.getLevel().getBlockEntity(ctx.absolutePos(A))).getCoachId() == a.id, "the block entity carries the id");
        disconnect(owner);
        ctx.succeed();
    }

    @GameTest(structure = ROAD)
    public void rightClickDiscoversAndBreakingForgetsEverywhere(GameTestHelper ctx) {
        ServerPlayer owner = connect(ctx, "owner"), visitor = connect(ctx, "visitor");
        CoachRecord a = place(ctx, owner, A, StagecoachWood.SPRUCE);
        StagecoachState s = state(ctx);
        check(ctx, !s.hasDiscovered(visitor.getUUID(), a.id), "not discovered before the right-click");
        standAt(ctx, visitor, A.south(3));
        StagecoachService.onUse(ctx.getLevel(), ctx.absolutePos(A), visitor);
        check(ctx, s.hasDiscovered(visitor.getUUID(), a.id), "a right-click discovers it");

        visitor.gameMode.destroyBlock(StagecoachShapes.cellPos(ctx.absolutePos(A), Direction.NORTH, 1));
        check(ctx, s.coach(a.id) != null && ctx.getLevel().getBlockState(ctx.absolutePos(A)).is(StagecoachRegistry.BLOCKS.get(StagecoachWood.SPRUCE)),
                "a non-owner must not be able to break it");
        owner.gameMode.destroyBlock(StagecoachShapes.cellPos(ctx.absolutePos(A), Direction.NORTH, 1));
        check(ctx, s.coach(a.id) == null, "breaking removes it from the registry");
        check(ctx, !s.hasDiscovered(visitor.getUUID(), a.id) && !s.hasDiscovered(owner.getUUID(), a.id), "and from every discovered list");
        disconnect(owner);
        disconnect(visitor);
        ctx.succeed();
    }

    @GameTest(structure = ROAD)
    public void renameIsOwnerOnlyTrimmedAndCapped(GameTestHelper ctx) {
        ServerPlayer owner = connect(ctx, "owner"), other = connect(ctx, "other");
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
        ctx.succeed();
    }

    @GameTest(structure = ROAD)
    public void theListShowsCostTimeAndAffordability(GameTestHelper ctx) {
        ServerPlayer owner = connect(ctx, "owner");
        CoachRecord a = place(ctx, owner, A, StagecoachWood.OAK), b = place(ctx, owner, B, StagecoachWood.OAK);
        owner.setExperienceLevels(1);
        Packets.DestinationList list = StagecoachService.buildList(owner, a);
        check(ctx, list.entries().stream().noneMatch(x -> x.id() == a.id), "the coach itself isn't listed");
        Packets.Entry e = PackGameTests.entry(ctx, owner, a, b);
        check(ctx, e.distance() == 12 && e.cost() == 2 && e.seconds() == 5 && e.owned(), "12 blocks, 2 levels, 5 s, owned: " + e);
        check(ctx, e.status() == Packets.STATUS_CANT_AFFORD, "level 1 can't afford 2");
        owner.setExperienceLevels(2);
        check(ctx, PackGameTests.entry(ctx, owner, a, b).status() == Packets.STATUS_OK, "level 2 can");
        check(ctx, list.canRename(), "the owner gets the rename button");
        disconnect(owner);
        ctx.succeed();
    }

    @GameTest(structure = ROAD, maxTicks = 320)
    public void travelIsRefusedWithoutXpInCombatFarAwayOrUndiscovered(GameTestHelper ctx) {
        ServerPlayer owner = connect(ctx, "owner"), stranger = connect(ctx, "stranger");
        CoachRecord a = place(ctx, owner, A, StagecoachWood.OAK), b = place(ctx, owner, B, StagecoachWood.OAK);
        standAt(ctx, owner, A.south(3));
        owner.setExperienceLevels(1);
        StagecoachService.travel(owner, new Packets.Travel(a.id, b.id));
        check(ctx, !JourneyManager.isTravelling(owner.getUUID()), "no XP -> no journey");

        standAt(ctx, stranger, A.south(3));
        stranger.setExperienceLevels(30);
        StagecoachService.travel(stranger, new Packets.Travel(a.id, b.id));
        check(ctx, !JourneyManager.isTravelling(stranger.getUUID()), "coaches the player never discovered -> no journey");

        StagecoachService.onUse(ctx.getLevel(), ctx.absolutePos(A), stranger);
        StagecoachService.onUse(ctx.getLevel(), ctx.absolutePos(B), stranger);
        stranger.teleportTo(ctx.getLevel(), stranger.getX() + 40, stranger.getY(), stranger.getZ(), java.util.Set.of(), 0, 0, true);
        StagecoachService.travel(stranger, new Packets.Travel(a.id, b.id));
        check(ctx, !JourneyManager.isTravelling(stranger.getUUID()), "40 blocks from the origin -> no journey");

        // vanilla ignores damage for 60 ticks after joining, so the hit comes after that
        ctx.runAtTickTime(70, () -> {
            owner.setExperienceLevels(30);
            Zombie zombie = EntityType.ZOMBIE.create(ctx.getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            check(ctx, owner.hurtServer(ctx.getLevel(), ctx.getLevel().damageSources().mobAttack(zombie), 1.0f), "the hit must land");
            check(ctx, JourneyManager.combatLockSecondsLeft(owner) == 10, "hit by a mob -> 10 s combat lock, got " + JourneyManager.combatLockSecondsLeft(owner));
            StagecoachService.travel(owner, new Packets.Travel(a.id, b.id));
            check(ctx, !JourneyManager.isTravelling(owner.getUUID()), "combat lock -> no journey");
        });
        // the 10 s lock is over by now
        ctx.runAtTickTime(70 + 201, () -> {
            check(ctx, JourneyManager.combatLockSecondsLeft(owner) == 0, "lock over after 10 s");
            StagecoachService.travel(owner, new Packets.Travel(a.id, b.id));
            check(ctx, JourneyManager.isTravelling(owner.getUUID()), "no longer locked -> the journey starts");
            JourneyManager.cancel(owner);
            disconnect(owner);
            disconnect(stranger);
            ctx.succeed();
        });
    }

    @GameTest(structure = ROAD, maxTicks = 300)
    public void aSurvivalJourneyFreezesPaysAndArrivesInSurvival(GameTestHelper ctx) {
        ServerPlayer p = connect(ctx, "traveller");
        CoachRecord a = place(ctx, p, A, StagecoachWood.OAK), b = place(ctx, p, B, StagecoachWood.DARK_OAK);
        standAt(ctx, p, A.south(3));
        p.setExperienceLevels(5);
        StagecoachService.travel(p, new Packets.Travel(a.id, b.id));
        Journey j = JourneyManager.journeyOf(p);
        check(ctx, j != null && JourneyManager.isTravelling(p.getUUID()), "the journey started");
        check(ctx, p.gameMode.getGameModeForPlayer() == GameType.SPECTATOR, "on the road = spectator");
        check(ctx, p.experienceLevel == 3, "paid 2 levels, has " + p.experienceLevel);
        check(ctx, j.durationTicks == 100, "12 blocks -> the 5 s floor = 100 ticks, got " + j.durationTicks);
        check(ctx, j.savedMode == GameType.SURVIVAL, "survival saved");

        // a modified client wanders off; the server pulls them back
        p.teleportTo(ctx.getLevel(), p.getX() + 9, p.getY() + 5, p.getZ() - 7, java.util.Set.of(), 0, 0, true);
        Pig pig = ctx.spawn(EntityType.PIG, new BlockPos(12, 2, 20));
        p.setCamera(pig);
        check(ctx, p.getCamera() == p, "can't attach the camera to another entity on the road");
        ctx.runAtTickTime(3, () -> check(ctx, p.position().distanceToSqr(j.holdPoint) < 0.01, "held at the origin"));

        ctx.runAtTickTime(j.durationTicks + 6, () -> {
            check(ctx, !JourneyManager.isTravelling(p.getUUID()) && JourneyManager.journeyOf(p) == null, "journey over, record gone");
            check(ctx, p.gameMode.getGameModeForPlayer() == GameType.SURVIVAL, "arrived in survival, got " + p.gameMode.getGameModeForPlayer());
            check(ctx, !p.getAbilities().mayfly && !p.getAbilities().flying, "survival abilities (no flight), got allowFlying="
                    + p.getAbilities().mayfly + " flying=" + p.getAbilities().flying);
            check(ctx, p.experienceLevel == 3, "no refund on arrival");
            BlockPos bAbs = ctx.absolutePos(B);
            check(ctx, p.blockPosition().distSqr(bAbs.north(4)) <= 36, "put down in front of the destination, at " + p.blockPosition());
            check(ctx, ctx.getLevel().noCollision(p), "not inside any block or the coach");
            pig.discard();
            disconnect(p);
            ctx.succeed();
        });
    }

    @GameTest(structure = ROAD, maxTicks = 300)
    public void aCreativeJourneyIsFreeAndArrivesInCreativeStillFlying(GameTestHelper ctx) {
        ServerPlayer p = connect(ctx, "creative");
        p.setGameMode(GameType.CREATIVE);
        p.getAbilities().flying = true;
        CoachRecord a = place(ctx, p, A, StagecoachWood.CHERRY), b = place(ctx, p, B, StagecoachWood.BAMBOO);
        standAt(ctx, p, A.south(3));
        p.setExperienceLevels(0);
        StagecoachService.travel(p, new Packets.Travel(a.id, b.id));
        check(ctx, JourneyManager.isTravelling(p.getUUID()), "creative can travel with 0 levels");
        ctx.runAtTickTime(110, () -> {
            check(ctx, p.gameMode.getGameModeForPlayer() == GameType.CREATIVE, "arrived in creative");
            check(ctx, p.getAbilities().mayfly && p.getAbilities().flying, "creative flight restored as it was");
            disconnect(p);
            ctx.succeed();
        });
    }

    @GameTest(structure = ROAD)
    public void cancellingRefundsAndReturnsToTheOrigin(GameTestHelper ctx) {
        ServerPlayer p = connect(ctx, "canceller");
        Journey j = startJourney(ctx, p, 7);
        JourneyManager.cancel(p);
        expectBackAtOrigin(ctx, p, GameType.SURVIVAL, 7);
        check(ctx, JourneyManager.journeyOf(p) == null && j.xpPaid == 2, "record gone");
        disconnect(p);
        ctx.succeed();
    }

    @GameTest(structure = ROAD)
    public void breakingTheDestinationMidJourneyCancelsWithARefund(GameTestHelper ctx) {
        ServerPlayer p = connect(ctx, "dest-gone");
        startJourney(ctx, p, 7);
        // not a player: no owner check
        ctx.getLevel().setBlockAndUpdate(ctx.absolutePos(B), Blocks.AIR.defaultBlockState());
        ctx.runAtTickTime(3, () -> {
            expectBackAtOrigin(ctx, p, GameType.SURVIVAL, 7);
            disconnect(p);
            ctx.succeed();
        });
    }

    @GameTest(structure = ROAD, maxTicks = 300)
    public void breakingTheOriginMidJourneyDoesNotStopIt(GameTestHelper ctx) {
        ServerPlayer p = connect(ctx, "origin-gone");
        Journey j = startJourney(ctx, p, 7);
        ctx.getLevel().setBlockAndUpdate(ctx.absolutePos(A), Blocks.AIR.defaultBlockState());
        ctx.runAtTickTime(5, () -> check(ctx, JourneyManager.isTravelling(p.getUUID()), "still on the road"));
        ctx.runAtTickTime(j.durationTicks + 6, () -> {
            check(ctx, p.gameMode.getGameModeForPlayer() == GameType.SURVIVAL && p.experienceLevel == 5, "arrived, paid");
            check(ctx, p.blockPosition().distSqr(ctx.absolutePos(B).north(4)) <= 36, "at the destination");
            disconnect(p);
            ctx.succeed();
        });
    }

    @GameTest(structure = ROAD)
    public void anOutsideGameModeChangeWinsAndCancels(GameTestHelper ctx) {
        ServerPlayer p = connect(ctx, "gamemode");
        startJourney(ctx, p, 7);
        p.setGameMode(GameType.ADVENTURE); // what /gamemode does
        ctx.runAtTickTime(3, () -> {
            expectBackAtOrigin(ctx, p, GameType.ADVENTURE, 7);
            disconnect(p);
            ctx.succeed();
        });
    }

    @GameTest(structure = ROAD)
    public void dyingOnTheRoadRestoresModeAndXpAtRespawn(GameTestHelper ctx) {
        ServerPlayer p = connect(ctx, "dies");
        startJourney(ctx, p, 7);
        p.kill(p.level());
        ServerPlayer respawned = ctx.getLevel().getServer().getPlayerList().respawn(p, false, net.minecraft.world.entity.Entity.RemovalReason.KILLED);
        check(ctx, respawned.gameMode.getGameModeForPlayer() == GameType.SURVIVAL, "respawned in survival, got " + respawned.gameMode.getGameModeForPlayer());
        check(ctx, respawned.experienceLevel == 7, "refunded after death, has " + respawned.experienceLevel);
        check(ctx, !JourneyManager.isTravelling(respawned.getUUID()) && JourneyManager.journeyOf(respawned) == null, "journey over");
        disconnect(respawned);
        ctx.succeed();
    }

    /** The real vanilla path: onDisconnected, then PlayerManager#remove saves the file. */
    @GameTest(structure = ROAD)
    public void disconnectingMidJourneyRestoresBeforeTheSaveAndRefunds(GameTestHelper ctx) {
        ServerPlayer p = connect(ctx, "quitter");
        GameProfile profile = p.getGameProfile();
        startJourney(ctx, p, 7);
        p.connection.onDisconnect(new net.minecraft.network.DisconnectionDetails(Component.literal("test")));
        check(ctx, JourneyManager.journeyOf(p) == null && !JourneyManager.isTravelling(p.getUUID()), "journey ended on disconnect");
        // what was saved must already be survival, refunded and at the origin
        ServerPlayer back = connect(ctx, profile);
        expectBackAtOrigin(ctx, back, GameType.SURVIVAL, 7);
        disconnect(back);
        ctx.succeed();
    }

    /** Fabric fires DISCONNECT on the Netty thread, so leaving is handled in PlayerManager#remove. */
    @GameTest(structure = ROAD)
    public void removingATravellerEndsTheJourneyBeforeTheirFileIsSaved(GameTestHelper ctx) {
        ServerPlayer p = connect(ctx, "removed");
        GameProfile profile = p.getGameProfile();
        startJourney(ctx, p, 7);
        ctx.getLevel().getServer().getPlayerList().remove(p);
        check(ctx, JourneyManager.journeyOf(p) == null && !JourneyManager.isTravelling(p.getUUID()), "removal must end the journey");
        ServerPlayer back = connect(ctx, profile);
        expectBackAtOrigin(ctx, back, GameType.SURVIVAL, 7);
        disconnect(back);
        ctx.succeed();
    }

    /** No handler of ours runs in a crash; the saved file still says spectator. */
    @GameTest(structure = ROAD)
    public void aCrashMidJourneyIsUndoneAtTheNextLogin(GameTestHelper ctx) {
        ServerPlayer p = connect(ctx, "crash");
        GameProfile profile = p.getGameProfile();
        startJourney(ctx, p, 7);
        crash(ctx, p);
        ServerPlayer back = connect(ctx, profile);
        expectBackAtOrigin(ctx, back, GameType.SURVIVAL, 7);
        check(ctx, JourneyManager.journeyOf(back) == null, "the leftover record is deleted");
        disconnect(back);
        ctx.succeed();
    }

    @GameTest(structure = ROAD)
    public void aCrashIsUndoneFromThePlayerFileAloneIfTheStateLostIt(GameTestHelper ctx) {
        ServerPlayer p = connect(ctx, "crash-mirror");
        GameProfile profile = p.getGameProfile();
        startJourney(ctx, p, 7);
        crash(ctx, p);
        state(ctx).removeJourney(profile.id());
        ServerPlayer back = connect(ctx, profile);
        expectBackAtOrigin(ctx, back, GameType.SURVIVAL, 7);
        disconnect(back);
        ctx.succeed();
    }

    @GameTest(structure = ROAD)
    public void theNetworkStateSurvivesSaveAndLoad(GameTestHelper ctx) {
        ServerPlayer p = connect(ctx, "persist");
        Journey j = startJourney(ctx, p, 7);
        StagecoachState s = state(ctx);
        CoachRecord origin = s.coach(j.originId);
        s.rename(origin, "Riverrun");
        StagecoachState copy = StagecoachState.fromNbt(s.writeNbt(new net.minecraft.nbt.CompoundTag()));
        CoachRecord o2 = copy.coach(j.originId);
        check(ctx, o2 != null && o2.name.equals("Riverrun") && o2.isOwner(p.getUUID()) && o2.pos.equals(origin.pos)
                && o2.facing == origin.facing && o2.dimension == origin.dimension, "coach record round-trips");
        check(ctx, copy.hasDiscovered(p.getUUID(), j.originId) && copy.hasDiscovered(p.getUUID(), j.destId), "discovered list round-trips");
        Journey j2 = copy.journey(p.getUUID());
        check(ctx, j2 != null && j2.savedMode == GameType.SURVIVAL && j2.xpLevel == 7 && j2.xpPaid == 2 && j2.destId == j.destId
                && j2.startTime == j.startTime && j2.durationTicks == j.durationTicks && j2.phase == Journey.Phase.TRAVELLING, "journey round-trips");
        JourneyManager.cancel(p);
        disconnect(p);
        ctx.succeed();
    }

    @GameTest(structure = ROAD)
    public void spectatorTeleportsAreBlockedOnlyForTravellers(GameTestHelper ctx) {
        ServerPlayer traveller = connect(ctx, "locked"), plain = connect(ctx, "free-spec"), target = connect(ctx, "target");
        standAt(ctx, target, new BlockPos(20, 2, 20));
        startJourney(ctx, traveller, 7);
        Vec3 before = traveller.position();
        traveller.connection.handleTeleportToEntityPacket(new ServerboundTeleportToEntityPacket(target.getUUID()));
        check(ctx, traveller.position().distanceToSqr(before) < 0.01, "a traveller's spectator teleport is dropped");

        plain.setGameMode(GameType.SPECTATOR);
        standAt(ctx, plain, new BlockPos(2, 2, 20));
        plain.connection.handleTeleportToEntityPacket(new ServerboundTeleportToEntityPacket(target.getUUID()));
        check(ctx, plain.position().distanceToSqr(target.position()) < 0.01, "an ordinary spectator can still teleport");
        disconnect(traveller);
        disconnect(plain);
        disconnect(target);
        ctx.succeed();
    }

    /** Cancel gives back the very same stacks, not just the same count. */
    @GameTest(structure = ROAD)
    public void anItemFareIsPaidAndRefundedInItems(GameTestHelper ctx) {
        ServerPlayer p = connect(ctx, "items");
        CoachRecord a = place(ctx, p, A, StagecoachWood.OAK), b = place(ctx, p, B, StagecoachWood.OAK);
        standAt(ctx, p, A.south(3));
        p.setExperienceLevels(0);
        p.getInventory().clearContent();
        p.getInventory().add(new ItemStack(Items.EMERALD, 10));
        withConfig("{\"itemCostEnabled\": true, \"costItem\": \"minecraft:emerald\"}", () -> {
            check(ctx, PackGameTests.entry(ctx, p, a, b).cost() == 2, "12 blocks = 1 + ceil(12/1000) = 2 emeralds");
            StagecoachService.travel(p, new Packets.Travel(a.id, b.id));
            check(ctx, JourneyManager.isTravelling(p.getUUID()), "paid in emeralds with 0 levels");
            check(ctx, p.getInventory().countItem(Items.EMERALD) == 8, "2 emeralds taken, has " + p.getInventory().countItem(Items.EMERALD));
            JourneyManager.cancel(p);
        });
        check(ctx, p.getInventory().countItem(Items.EMERALD) == 10, "both back after cancelling, has " + p.getInventory().countItem(Items.EMERALD));
        disconnect(p);
        ctx.succeed();
    }

    /** An unknown id falls back to XP, never to free travel. */
    @GameTest(structure = ROAD)
    public void costItemTakesTagsAndFallsBackToXpForAnUnknownId(GameTestHelper ctx) {
        ServerPlayer p = connect(ctx, "tags");
        CoachRecord a = place(ctx, p, A, StagecoachWood.OAK), b = place(ctx, p, B, StagecoachWood.OAK);
        standAt(ctx, p, A.south(3));
        p.getInventory().clearContent();
        p.getInventory().add(new ItemStack(Items.BIRCH_LOG, 5));
        p.setExperienceLevels(5);
        withConfig("{\"itemCostEnabled\": true, \"costItem\": \"#minecraft:logs\"}", () -> {
            StagecoachService.travel(p, new Packets.Travel(a.id, b.id));
            check(ctx, p.getInventory().countItem(Items.BIRCH_LOG) == 3 && p.experienceLevel == 5, "paid 2 logs through the tag");
            JourneyManager.cancel(p);
        });
        withConfig("{\"itemCostEnabled\": true, \"costItem\": \"nosuchmod:nothing\"}", () -> {
            StagecoachService.travel(p, new Packets.Travel(a.id, b.id));
            check(ctx, p.experienceLevel == 3 && p.getInventory().countItem(Items.BIRCH_LOG) == 5, "an unknown item costs XP instead");
            JourneyManager.cancel(p);
        });
        disconnect(p);
        ctx.succeed();
    }

    /** The refund share applies to the player's own Cancel only. */
    @GameTest(structure = ROAD)
    public void cancellingCanKeepPartOfTheFare(GameTestHelper ctx) {
        ServerPlayer p = connect(ctx, "half");
        withConfig("{\"cancelRefundPercent\": 50, \"baseCost\": 4}", () -> {
            CoachRecord a = place(ctx, p, A, StagecoachWood.OAK), b = place(ctx, p, B, StagecoachWood.OAK);
            standAt(ctx, p, A.south(3));
            p.setExperienceLevels(9);
            StagecoachService.travel(p, new Packets.Travel(a.id, b.id));
            check(ctx, p.experienceLevel == 4, "paid 5 levels (4 + 1), has " + p.experienceLevel);
            JourneyManager.cancel(p);
            check(ctx, p.experienceLevel == 6, "half of 5, rounded down, comes back: 4 + 2, has " + p.experienceLevel);
        });
        disconnect(p);
        ctx.succeed();
    }

    @GameTest(structure = ROAD)
    public void aStopBeyondMaxDistanceIsListedAndRefused(GameTestHelper ctx) {
        ServerPlayer p = connect(ctx, "too-far");
        CoachRecord a = place(ctx, p, A, StagecoachWood.OAK), b = place(ctx, p, B, StagecoachWood.OAK);
        standAt(ctx, p, A.south(3));
        p.setExperienceLevels(30);
        withConfig("{\"maxDistance\": 10}", () -> {
            check(ctx, PackGameTests.entry(ctx, p, a, b).status() == Packets.STATUS_TOO_FAR, "12 blocks > 10");
            StagecoachService.travel(p, new Packets.Travel(a.id, b.id));
            check(ctx, !JourneyManager.isTravelling(p.getUUID()) && p.experienceLevel == 30, "refused, nothing paid");
        });
        withConfig("{\"maxDistance\": 12}", () ->
                check(ctx, PackGameTests.entry(ctx, p, a, b).status() == Packets.STATUS_OK, "12 blocks <= 12"));
        disconnect(p);
        ctx.succeed();
    }

    @GameTest(structure = ROAD)
    public void instantTravelArrivesAtOnceAndTheCooldownHolds(GameTestHelper ctx) {
        ServerPlayer p = connect(ctx, "instant");
        CoachRecord a = place(ctx, p, A, StagecoachWood.OAK), b = place(ctx, p, B, StagecoachWood.OAK);
        standAt(ctx, p, A.south(3));
        p.setExperienceLevels(10);
        withConfig("{\"instantTravel\": true, \"travelCooldownSeconds\": 30}", () -> {
            StagecoachService.travel(p, new Packets.Travel(a.id, b.id));
            check(ctx, !JourneyManager.isTravelling(p.getUUID()) && JourneyManager.journeyOf(p) == null, "no journey record");
            check(ctx, p.gameMode.getGameModeForPlayer() == GameType.SURVIVAL && p.experienceLevel == 8, "survival, paid 2");
            check(ctx, p.blockPosition().distSqr(ctx.absolutePos(B).north(4)) <= 36, "at the destination, at " + p.blockPosition());
            check(ctx, JourneyManager.cooldownSecondsLeft(p) == 30, "30 s cooldown, got " + JourneyManager.cooldownSecondsLeft(p));
            StagecoachService.travel(p, new Packets.Travel(b.id, a.id));
            check(ctx, p.experienceLevel == 8 && p.blockPosition().distSqr(ctx.absolutePos(B).north(4)) <= 36, "refused during the cooldown");
        });
        disconnect(p);
        ctx.succeed();
    }

    @GameTest(structure = ROAD)
    public void withDiscoveryOffEveryCoachIsKnown(GameTestHelper ctx) {
        ServerPlayer owner = connect(ctx, "owner"), stranger = connect(ctx, "stranger");
        CoachRecord a = place(ctx, owner, A, StagecoachWood.OAK), b = place(ctx, owner, B, StagecoachWood.OAK);
        standAt(ctx, stranger, A.south(3));
        stranger.setExperienceLevels(10);
        withConfig("{\"discoveryRequired\": false}", () -> {
            check(ctx, StagecoachService.buildList(stranger, a).entries().stream().anyMatch(e -> e.id() == b.id), "the other coach is listed");
            StagecoachService.travel(stranger, new Packets.Travel(a.id, b.id));
            check(ctx, JourneyManager.isTravelling(stranger.getUUID()), "and reachable");
            JourneyManager.cancel(stranger);
        });
        disconnect(owner);
        disconnect(stranger);
        ctx.succeed();
    }

    @GameTest(structure = ROAD)
    public void placementLimitsRefuseAndKeepTheItem(GameTestHelper ctx) {
        ServerPlayer p = connect(ctx, "limits");
        withConfig("{\"maxCoachesPerPlayer\": 1}", () -> {
            place(ctx, p, A, StagecoachWood.OAK);
            check(ctx, !tryPlace(ctx, p, B, StagecoachWood.OAK), "a second coach is over the limit");
            check(ctx, p.getMainHandItem().is(StagecoachRegistry.ITEMS.get(StagecoachWood.OAK)), "the item is kept");
        });
        withConfig("{\"blockedDimensions\": [\"minecraft:overworld\"]}", () ->
                check(ctx, !tryPlace(ctx, p, B, StagecoachWood.SPRUCE), "no coaches in a blocked dimension"));
        disconnect(p);
        ctx.succeed();
    }

    @GameTest(structure = ROAD)
    public void creativePaysWhenFreeTravelIsOff(GameTestHelper ctx) {
        ServerPlayer p = connect(ctx, "creative-pays");
        CoachRecord a = place(ctx, p, A, StagecoachWood.OAK), b = place(ctx, p, B, StagecoachWood.OAK);
        p.setGameMode(GameType.CREATIVE);
        standAt(ctx, p, A.south(3));
        p.setExperienceLevels(5);
        withConfig("{\"creativeTravelsFree\": false}", () -> {
            StagecoachService.travel(p, new Packets.Travel(a.id, b.id));
            check(ctx, p.experienceLevel == 3, "creative paid 2, has " + p.experienceLevel);
            JourneyManager.cancel(p);
        });
        disconnect(p);
        ctx.succeed();
    }

    @GameTest(structure = ROAD)
    public void aCrashWithAnItemFareReturnsTheItemsOnce(GameTestHelper ctx) {
        ServerPlayer p = connect(ctx, "crash-items");
        GameProfile profile = p.getGameProfile();
        CoachRecord a = place(ctx, p, A, StagecoachWood.OAK), b = place(ctx, p, B, StagecoachWood.OAK);
        standAt(ctx, p, A.south(3));
        p.getInventory().clearContent();
        p.getInventory().add(new ItemStack(Items.EMERALD, 10));
        withConfig("{\"itemCostEnabled\": true}", () -> StagecoachService.travel(p, new Packets.Travel(a.id, b.id)));
        check(ctx, p.getInventory().countItem(Items.EMERALD) == 8, "paid 2");
        crash(ctx, p);
        ServerPlayer back = connect(ctx, profile);
        check(ctx, back.getInventory().countItem(Items.EMERALD) == 10, "10 again after recovery, has " + back.getInventory().countItem(Items.EMERALD));
        check(ctx, back.gameMode.getGameModeForPlayer() == GameType.SURVIVAL, "survival again");
        disconnect(back);
        ctx.succeed();
    }

    /** The file was saved with the items back, but the state file still had the journey. */
    @GameTest(structure = ROAD)
    public void aRecordWithoutTheMirrorNeverReturnsItemsTwice(GameTestHelper ctx) {
        ServerPlayer p = connect(ctx, "no-dupe");
        GameProfile profile = p.getGameProfile();
        CoachRecord a = place(ctx, p, A, StagecoachWood.OAK), b = place(ctx, p, B, StagecoachWood.OAK);
        standAt(ctx, p, A.south(3));
        p.getInventory().clearContent();
        p.getInventory().add(new ItemStack(Items.EMERALD, 10));
        withConfig("{\"itemCostEnabled\": true}", () -> StagecoachService.travel(p, new Packets.Travel(a.id, b.id)));
        Journey j = JourneyManager.journeyOf(p);
        JourneyManager.cancel(p);
        disconnect(p); // saved with all 10 and no mirror
        state(ctx).putJourney(j); // ...but the state file never heard the journey was over
        ServerPlayer back = connect(ctx, profile);
        check(ctx, back.getInventory().countItem(Items.EMERALD) == 10, "still exactly 10, has " + back.getInventory().countItem(Items.EMERALD));
        check(ctx, JourneyManager.journeyOf(back) == null, "the stale record is cleared");
        disconnect(back);
        ctx.succeed();
    }

    /** Places A and B, gives {@code levels} and sets off from A to B (cost 2). */
    static Journey startJourney(GameTestHelper ctx, ServerPlayer p, int levels) {
        CoachRecord a = place(ctx, p, A, StagecoachWood.OAK), b = place(ctx, p, B, StagecoachWood.ACACIA);
        standAt(ctx, p, A.south(3));
        p.setExperienceLevels(levels);
        StagecoachService.travel(p, new Packets.Travel(a.id, b.id));
        Journey j = JourneyManager.journeyOf(p);
        check(ctx, j != null && p.gameMode.getGameModeForPlayer() == GameType.SPECTATOR, "journey should have started");
        check(ctx, p.experienceLevel == levels - 2, "paid 2 levels");
        return j;
    }

    static void expectBackAtOrigin(GameTestHelper ctx, ServerPlayer p, GameType mode, int levels) {
        check(ctx, p.gameMode.getGameModeForPlayer() == mode, "mode should be " + mode + ", got " + p.gameMode.getGameModeForPlayer());
        check(ctx, p.experienceLevel == levels, "refunded to " + levels + ", has " + p.experienceLevel);
        check(ctx, !JourneyManager.isTravelling(p.getUUID()) && JourneyManager.journeyOf(p) == null, "journey over");
        BlockPos front = ctx.absolutePos(A).north(4);
        check(ctx, p.blockPosition().distSqr(front) <= 36, "back in front of the origin, at " + p.blockPosition());
        check(ctx, ctx.getLevel().noCollision(p), "not stuck in a block or the coach");
    }

    static CoachRecord place(GameTestHelper ctx, ServerPlayer owner, BlockPos rel, StagecoachWood wood) {
        ServerLevel level = ctx.getLevel();
        BlockPos c = ctx.absolutePos(rel);
        ItemStack stack = new ItemStack(StagecoachRegistry.ITEMS.get(wood));
        owner.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Vec3 keep = owner.position();
        owner.snapTo(c.getX() + 0.5, c.getY(), c.getZ() + 8.5, Direction.NORTH.toYRot(), 0);
        var result = owner.gameMode.useItemOn(owner, level, stack, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(c.below()).add(0, 0.5, 0), Direction.UP, c.below(), false));
        owner.snapTo(keep.x, keep.y, keep.z, 0, 0);
        if (!(level.getBlockEntity(c) instanceof StagecoachBlockEntity be) || state(ctx).coach(be.getCoachId()) == null) {
            throw new IllegalStateException("placing the " + wood + " coach at " + rel + " failed: " + result + ", block "
                    + level.getBlockState(c) + ", below " + level.getBlockState(c.below()) + ", mode " + owner.gameMode.getGameModeForPlayer()
                    + ", entities " + level.getEntities((Entity) null, new net.minecraft.world.phys.AABB(c).inflate(4)));
        }
        for (int i = 0; i < Footprint.CELL_COUNT; i++) {
            if (level.getBlockState(StagecoachShapes.cellPos(c, Direction.NORTH, i)).isAir()) throw new IllegalStateException("cell " + i + " missing");
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

    static boolean tryPlace(GameTestHelper ctx, ServerPlayer owner, BlockPos rel, StagecoachWood wood) {
        ServerLevel level = ctx.getLevel();
        BlockPos c = ctx.absolutePos(rel);
        ItemStack stack = new ItemStack(StagecoachRegistry.ITEMS.get(wood));
        owner.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Vec3 keep = owner.position();
        owner.snapTo(c.getX() + 0.5, c.getY(), c.getZ() + 8.5, Direction.NORTH.toYRot(), 0);
        owner.gameMode.useItemOn(owner, level, stack, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(c.below()).add(0, 0.5, 0), Direction.UP, c.below(), false));
        owner.snapTo(keep.x, keep.y, keep.z, 0, 0);
        return level.getBlockEntity(c) instanceof StagecoachBlockEntity;
    }

    static void standAt(GameTestHelper ctx, ServerPlayer p, BlockPos rel) {
        BlockPos abs = ctx.absolutePos(rel);
        p.teleportTo(ctx.getLevel(), abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, java.util.Set.of(), 0, 0, true);
    }

    /** Vanilla's own mock-player recipe, without its always-creative override. */
    static ServerPlayer connect(GameTestHelper ctx, String name) {
        String n = ("sc-" + name).length() > 16 ? ("sc-" + name).substring(0, 16) : "sc-" + name;
        ServerPlayer p = connect(ctx, new GameProfile(UUID.randomUUID(), n));
        p.setGameMode(GameType.SURVIVAL); // the GameTest world's default mode is creative
        standAt(ctx, p, new BlockPos(12, 21, 12)); // parked in the air above our own area, out of every footprint
        return p;
    }

    static ServerPlayer connect(GameTestHelper ctx, GameProfile profile) {
        MinecraftServer server = ctx.getLevel().getServer();
        // the saved player file must be loaded before placeNewPlayer, as the real login does it
        var data = net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
        ServerPlayer p = new ServerPlayer(server, ctx.getLevel(), profile, data.clientInformation());
        try (var reporter = new net.minecraft.util.ProblemReporter.ScopedCollector(p.problemPath(), com.mojang.logging.LogUtils.getLogger())) {
            server.getPlayerList().loadPlayerData(p.nameAndId())
                    .map(tag -> net.minecraft.world.level.storage.TagValueInput.create(reporter, server.registryAccess(), tag))
                    .ifPresent(p::load);
        }
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, p, data);
        return p;
    }

    static void disconnect(ServerPlayer p) {
        if (p.level().getServer().getPlayerList().getPlayer(p.getUUID()) == p) p.connection.onDisconnect(new net.minecraft.network.DisconnectionDetails(Component.literal("test over")));
    }

    /** Leaves only what a crash would: the player file as last saved and the state file. */
    static void crash(GameTestHelper ctx, ServerPlayer p) {
        JourneyManager.simulateCrash(p.getUUID());
        ctx.getLevel().getServer().getPlayerList().remove(p); // a plain vanilla save and removal
    }

    static StagecoachState state(GameTestHelper ctx) {
        return StagecoachState.get(ctx.getLevel().getServer());
    }

    static void check(GameTestHelper ctx, boolean ok, String message) {
        if (!ok) ctx.fail(message);
    }
}
