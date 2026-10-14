package net.feelix.stagecoach.gametest;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.StagecoachRegistry;
import net.feelix.stagecoach.StagecoachWood;
import net.feelix.stagecoach.advancement.StagecoachTriggers;
import net.feelix.stagecoach.api.CreatedBy;
import net.feelix.stagecoach.api.JourneyOptions;
import net.feelix.stagecoach.api.JourneyResult;
import net.feelix.stagecoach.api.StagecoachAPI;
import net.feelix.stagecoach.block.StagecoachBlock;
import net.feelix.stagecoach.block.StagecoachBlockEntity;
import net.feelix.stagecoach.block.StagecoachPartBlock;
import net.feelix.stagecoach.data.StagecoachData;
import net.feelix.stagecoach.data.TravelRule;
import net.feelix.stagecoach.footprint.Footprint;
import net.feelix.stagecoach.footprint.StagecoachShapes;
import net.feelix.stagecoach.net.Packets;
import net.feelix.stagecoach.travel.CoachRecord;
import net.feelix.stagecoach.travel.Journey;
import net.feelix.stagecoach.travel.JourneyManager;
import net.feelix.stagecoach.travel.PendingCoaches;
import net.feelix.stagecoach.travel.StagecoachService;
import net.feelix.stagecoach.travel.StagecoachState;
import net.feelix.stagecoach.worldgen.StructureInjections;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.TagValueInput;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

public class PackGameTests {
    /** Copies go here: the same distance from A and B, further south. */
    private static final BlockPos C = new BlockPos(4, 2, 19);
    private static final BlockPos D = new BlockPos(16, 2, 19);

    @GameTest(structure = ROAD)
    public void aNetworkSavedByOneOneZeroLoadsWithDefaults(GameTestHelper ctx) {
        UUID owner = UUID.randomUUID();
        CompoundTag v1 = new CompoundTag();
        v1.putLong("nextId", 3);
        ListTag coaches = new ListTag();
        coaches.add(oldRecord(1, "Old Mill", owner));
        coaches.add(oldRecord(2, "Dispenser Stop", null));
        v1.put("coaches", coaches);
        CompoundTag discovered = new CompoundTag();
        ListTag ids = new ListTag();
        ids.add(LongTag.valueOf(1));
        ids.add(LongTag.valueOf(2));
        discovered.put(owner.toString(), ids);
        v1.put("discovered", discovered);

        StagecoachState loaded = StagecoachState.fromNbt(v1);
        check(ctx, loaded.loadedVersion() == 1, "no dataVersion is version 1");
        CoachRecord mill = loaded.coach(1), dispenser = loaded.coach(2);
        check(ctx, mill != null && mill.name.equals("Old Mill") && mill.isOwner(owner), "name and owner kept");
        check(ctx, dispenser != null && dispenser.owner == null, "an ownerless 1.1.0 coach stays ownerless");
        check(ctx, mill.wood == null && !mill.isPublic && !mill.hidden && !mill.locked && !mill.free && mill.costMultiplier == 1.0
                && mill.scene == null && mill.createdBy == CreatedBy.PLAYER, "new fields take their defaults");
        check(ctx, loaded.hasDiscovered(owner, 1) && loaded.hasDiscovered(owner, 2), "discoveries kept");
        CompoundTag saved = loaded.writeNbt(new CompoundTag());
        check(ctx, saved.getIntOr("dataVersion", 0) == StagecoachState.DATA_VERSION, "written back as version " + StagecoachState.DATA_VERSION);
        CoachRecord again = StagecoachState.fromNbt(saved).coach(1);
        check(ctx, again != null && again.name.equals("Old Mill") && again.isOwner(owner), "and reads back the same");
        ctx.succeed();
    }

    private static CompoundTag oldRecord(long id, String name, UUID owner) {
        CompoundTag n = new CompoundTag();
        n.putLong("id", id);
        n.putString("name", name);
        n.putLong("pos", new BlockPos(100 * (int) id, 64, 0).asLong());
        n.putString("dim", "minecraft:overworld");
        n.putString("facing", "north");
        if (owner != null) n.store("owner", UUIDUtil.CODEC, owner);
        return n;
    }

    @GameTest(structure = ROAD)
    public void stopCreateMakesAnOwnerlessStopOnlyOperatorsBreak(GameTestHelper ctx) {
        BlockPos abs = ctx.absolutePos(A);
        check(ctx, run(ctx, "stagecoach stop create \"Market Cross\" " + abs.getX() + " " + abs.getY() + " " + abs.getZ() + " north spruce") == 1,
                "the command succeeds");
        CoachRecord r = state(ctx).at(ctx.getLevel().dimension(), abs);
        check(ctx, r != null && r.name.equals("Market Cross") && r.owner == null && r.createdBy == CreatedBy.ADMIN && r.wood == StagecoachWood.SPRUCE,
                "an ownerless admin stop, got " + r);
        check(ctx, ctx.getLevel().getBlockEntity(abs) instanceof StagecoachBlockEntity be && be.dropsNothing(), "it never drops an item");
        ServerPlayer p = connect(ctx, "not-op");
        p.gameMode.destroyBlock(StagecoachShapes.cellPos(abs, Direction.NORTH, 1));
        check(ctx, state(ctx).coach(r.id) != null, "a player who isn't an operator can't take it down");
        disconnect(p);
        ctx.succeed();
    }

    @GameTest(structure = ROAD)
    public void anAmbiguousNameIsRefusedButTheIdWorks(GameTestHelper ctx) {
        BlockPos a = ctx.absolutePos(A), b = ctx.absolutePos(B);
        run(ctx, "stagecoach stop create Twin " + a.getX() + " " + a.getY() + " " + a.getZ() + " north oak");
        run(ctx, "stagecoach stop create Twin " + b.getX() + " " + b.getY() + " " + b.getZ() + " north oak");
        check(ctx, run(ctx, "stagecoach stop info Twin") == 0, "two stops called Twin: refused");
        CoachRecord first = state(ctx).at(ctx.getLevel().dimension(), a);
        check(ctx, run(ctx, "stagecoach stop info " + first.id) == 1, "by id it works");
        check(ctx, run(ctx, "stagecoach stop info \"#" + first.id + "\"") == 1, "and by #id");
        ctx.succeed();
    }

    @GameTest(structure = ROAD, maxTicks = 100)
    public void structureCopiesBecomeSeparateNamedStops(GameTestHelper ctx) {
        ServerPlayer p = connect(ctx, "builder");
        CoachRecord original = place(ctx, p, A, StagecoachWood.BIRCH);
        StagecoachBlockEntity be = (StagecoachBlockEntity) ctx.getLevel().getBlockEntity(ctx.absolutePos(A));
        CompoundTag nbt = be.saveWithoutMetadata(ctx.getLevel().registryAccess());
        String name = "Riverford" + Math.abs(ctx.getLevel().getRandom().nextInt());
        nbt.putString(PendingCoaches.TEMPLATE_NAME, name);
        ListTag flags = new ListTag();
        flags.add(net.minecraft.nbt.StringTag.valueOf("public"));
        nbt.put(PendingCoaches.TEMPLATE_FLAGS, flags);
        be.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, ctx.getLevel().registryAccess(), nbt));
        StructureTemplate template = save(ctx, A);
        paste(ctx, template, C, Mirror.NONE);
        paste(ctx, template, D, Mirror.NONE);
        ctx.runAfterDelay(3, () -> {
            CoachRecord c = state(ctx).at(ctx.getLevel().dimension(), ctx.absolutePos(C));
            CoachRecord d = state(ctx).at(ctx.getLevel().dimension(), ctx.absolutePos(D));
            check(ctx, c != null && d != null, "both copies are stops");
            check(ctx, c.id != d.id && c.id != original.id && d.id != original.id, "each with its own id");
            check(ctx, c.owner == null && c.createdBy == CreatedBy.STRUCTURE && c.isPublic, "ownerless, from a structure, public as the template says");
            check(ctx, (c.name + "|" + d.name).equals(name + "|" + name + " (2)") || (c.name + "|" + d.name).equals(name + " (2)|" + name),
                    "the template name, made unique: " + c.name + " and " + d.name);
            check(ctx, state(ctx).coach(original.id) != null && state(ctx).coach(original.id).isOwner(p.getUUID()), "the original is untouched");
            check(ctx, ((StagecoachBlockEntity) ctx.getLevel().getBlockEntity(ctx.absolutePos(C))).template() == null, "template data used up");
            disconnect(p);
            ctx.succeed();
        });
    }

    @GameTest(structure = ROAD, maxTicks = 100)
    public void aMirroredTemplateComesOutWhole(GameTestHelper ctx) {
        ServerPlayer p = connect(ctx, "mirror");
        place(ctx, p, A, StagecoachWood.CHERRY);
        StructureTemplate template = save(ctx, A);
        paste(ctx, template, D, Mirror.LEFT_RIGHT);
        ctx.runAfterDelay(3, () -> {
            BlockPos found = null;
            for (BlockPos q : BlockPos.betweenClosed(ctx.absolutePos(D).offset(-3, 0, -6), ctx.absolutePos(D).offset(3, 0, 6))) {
                if (ctx.getLevel().getBlockState(q).getBlock() instanceof StagecoachBlock) found = q.immutable();
            }
            check(ctx, found != null, "the mirrored controller exists");
            BlockState controller = ctx.getLevel().getBlockState(found);
            Direction facing = controller.getValue(StagecoachBlock.FACING);
            for (int i = 1; i < Footprint.CELL_COUNT; i++) {
                BlockState s = ctx.getLevel().getBlockState(StagecoachShapes.cellPos(found, facing, i));
                check(ctx, s.is(StagecoachRegistry.PART) && s.getValue(StagecoachPartBlock.PART) == i && s.getValue(StagecoachPartBlock.FACING) == facing,
                        "cell " + i + " holds the right part after mirroring, got " + s);
            }
            check(ctx, state(ctx).at(ctx.getLevel().dimension(), found) != null, "and it is a stop");
            disconnect(p);
            ctx.succeed();
        });
    }

    @GameTest(structure = ROAD, maxTicks = 100)
    public void aSetblockControllerBecomesAStopWithItsParts(GameTestHelper ctx) {
        BlockPos abs = ctx.absolutePos(A);
        ctx.getLevel().setBlockAndUpdate(abs, StagecoachRegistry.BLOCKS.get(StagecoachWood.OAK).defaultBlockState());
        ctx.runAfterDelay(3, () -> {
            CoachRecord r = state(ctx).at(ctx.getLevel().dimension(), abs);
            check(ctx, r != null && r.owner == null && r.createdBy == CreatedBy.STRUCTURE, "registered as an ownerless stop");
            check(ctx, r.name.matches(".+ Stop( \\(\\d+\\))?"), "named from autoNameFormat, got " + r.name);
            for (int i = 1; i < Footprint.CELL_COUNT; i++) {
                check(ctx, ctx.getLevel().getBlockState(StagecoachShapes.cellPos(abs, Direction.NORTH, i)).is(StagecoachRegistry.PART), "part " + i + " laid");
            }
            ctx.succeed();
        });
    }

    @GameTest(structure = ROAD, maxTicks = 100)
    public void aThemedCoachTakesItsThemesWoodAndAName(GameTestHelper ctx) {
        BlockPos abs = ctx.absolutePos(A);
        ctx.getLevel().setBlockAndUpdate(abs, StagecoachRegistry.BLOCKS.get(StagecoachWood.OAK).defaultBlockState());
        StagecoachBlockEntity be = (StagecoachBlockEntity) ctx.getLevel().getBlockEntity(abs);
        CompoundTag nbt = be.saveWithoutMetadata(ctx.getLevel().registryAccess());
        nbt.putString(PendingCoaches.TEMPLATE_THEME, "stagecoach:taiga");
        be.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, ctx.getLevel().registryAccess(), nbt));
        ctx.runAfterDelay(3, () -> {
            CoachRecord r = state(ctx).at(ctx.getLevel().dimension(), abs);
            check(ctx, r != null, "the themed coach is a stop");
            check(ctx, r.wood == StagecoachWood.SPRUCE && ctx.getLevel().getBlockState(abs).is(StagecoachRegistry.BLOCKS.get(StagecoachWood.SPRUCE)),
                    "the taiga theme makes it spruce, got " + r.wood + " / " + ctx.getLevel().getBlockState(abs));
            List<String> prefixes = StagecoachData.namePool("stagecoach:northern").words().get("prefix");
            check(ctx, prefixes.stream().anyMatch(r.name::startsWith), "a name from the northern pool, got " + r.name);
            check(ctx, r.locked, "generated coaches are locked by default");
            check(ctx, ((StagecoachBlockEntity) ctx.getLevel().getBlockEntity(abs)).dropsNothing(), "and drop nothing");
            ctx.succeed();
        });
    }

    @GameTest(structure = ROAD, maxTicks = 100)
    public void aThemedCoachInABlockedDimensionLeavesNoStop(GameTestHelper ctx) {
        BlockPos abs = ctx.absolutePos(A);
        withConfig("{\"blockedDimensions\": [\"minecraft:overworld\"]}", () -> {
            ctx.getLevel().setBlockAndUpdate(abs, StagecoachRegistry.BLOCKS.get(StagecoachWood.OAK).defaultBlockState());
            StagecoachBlockEntity be = (StagecoachBlockEntity) ctx.getLevel().getBlockEntity(abs);
            CompoundTag nbt = be.saveWithoutMetadata(ctx.getLevel().registryAccess());
            nbt.putString(PendingCoaches.TEMPLATE_THEME, "stagecoach:plains");
            be.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, ctx.getLevel().registryAccess(), nbt));
            PendingCoaches.check(ctx.getLevel(), abs);
            check(ctx, ctx.getLevel().getBlockState(abs).isAir(), "the generated coach is taken away");
            check(ctx, state(ctx).at(ctx.getLevel().dimension(), abs) == null, "and is no stop");
        });
        ctx.succeed();
    }

    @GameTest(structure = ROAD)
    public void lockedStopsSurviveTheirOwnerAndExplosions(GameTestHelper ctx) {
        ServerPlayer p = connect(ctx, "locked");
        CoachRecord a = place(ctx, p, A, StagecoachWood.OAK);
        a.locked = true;
        p.gameMode.destroyBlock(StagecoachShapes.cellPos(ctx.absolutePos(A), Direction.NORTH, 1));
        check(ctx, state(ctx).coach(a.id) != null, "even the owner can't take a locked stop down");
        withConfig("{\"onlyOwnerCanBreak\": false}", () -> {
            BlockPos c = ctx.absolutePos(A);
            ctx.getLevel().explode(null, c.getX() + 0.5, c.getY() + 1, c.getZ() - 1.5, 4f, Level.ExplosionInteraction.TNT);
            check(ctx, state(ctx).coach(a.id) != null && ctx.getLevel().getBlockState(c).getBlock() instanceof StagecoachBlock,
                    "an explosion leaves a locked coach alone");
        });
        disconnect(p);
        ctx.succeed();
    }

    @GameTest(structure = ROAD)
    public void hiddenStopsAreNeverDestinationsButCanBeLeft(GameTestHelper ctx) {
        ServerPlayer p = connect(ctx, "hidden");
        CoachRecord a = place(ctx, p, A, StagecoachWood.OAK), b = place(ctx, p, B, StagecoachWood.OAK);
        b.hidden = true;
        check(ctx, StagecoachService.buildList(p, a).entries().stream().noneMatch(e -> e.id() == b.id), "B is not offered from A");
        standAt(ctx, p, A.south(3));
        p.setExperienceLevels(10);
        StagecoachService.travel(p, new Packets.Travel(a.id, b.id));
        check(ctx, !JourneyManager.isTravelling(p.getUUID()), "and a crafted request can't go there");
        check(ctx, StagecoachService.buildList(p, b).entries().stream().anyMatch(e -> e.id() == a.id), "but from B, A is listed");
        disconnect(p);
        ctx.succeed();
    }

    @GameTest(structure = ROAD)
    public void publicStopsAreKnownWithoutDiscovery(GameTestHelper ctx) {
        ServerPlayer owner = connect(ctx, "pub-owner"), visitor = connect(ctx, "pub-visitor");
        CoachRecord a = place(ctx, owner, A, StagecoachWood.OAK), b = place(ctx, owner, B, StagecoachWood.OAK);
        state(ctx).discover(visitor.getUUID(), a.id);
        check(ctx, StagecoachService.buildList(visitor, a).entries().stream().noneMatch(e -> e.id() == b.id), "B unknown to the visitor");
        b.isPublic = true;
        check(ctx, StagecoachService.buildList(visitor, a).entries().stream().anyMatch(e -> e.id() == b.id), "public B is listed without discovering it");
        disconnect(owner);
        disconnect(visitor);
        ctx.succeed();
    }

    @GameTest(structure = ROAD)
    public void theListedFareIsTheChargedFare(GameTestHelper ctx) {
        ServerPlayer p = connect(ctx, "fares");
        CoachRecord a = place(ctx, p, A, StagecoachWood.OAK), b = place(ctx, p, B, StagecoachWood.OAK);
        standAt(ctx, p, A.south(3));
        b.costMultiplier = 3;
        int listed = entry(ctx, p, a, b).cost();
        check(ctx, listed == 6, "2 levels x 3 = 6, listed " + listed);
        p.setExperienceLevels(20);
        StagecoachService.travel(p, new Packets.Travel(a.id, b.id));
        check(ctx, p.experienceLevel == 20 - listed, "charged exactly the listed " + listed + ", has " + p.experienceLevel);
        JourneyManager.cancel(p);
        b.costMultiplier = 1;
        b.free = true;
        check(ctx, entry(ctx, p, a, b).cost() == 0, "a free stop lists 0");
        withConfig("{\"dimensionCostMultipliers\": {\"minecraft:overworld\": 4}}", () -> {
            b.free = false;
            check(ctx, entry(ctx, p, a, b).cost() == 8, "the overworld multiplier: 2 x 4");
        });
        disconnect(p);
        ctx.succeed();
    }

    @GameTest(structure = ROAD)
    public void aTravelRuleLocksAStopUntilItsRequirementIsMet(GameTestHelper ctx) {
        ServerPlayer p = connect(ctx, "rules");
        CoachRecord a = place(ctx, p, A, StagecoachWood.OAK), b = place(ctx, p, B, StagecoachWood.OAK);
        StagecoachData.Snapshot before = StagecoachData.get();
        TravelRule rule = TravelRule.parse(JsonParser.parseString("{\"applies_to\": {\"to_stops\": [\"#" + b.id + "\"]},"
                + " \"requirements\": {\"min_level\": 10}, \"deny_message\": {\"text\": \"Not yet\"}, \"cost_multiplier\": 2}").getAsJsonObject());
        StagecoachData.use(new StagecoachData.Snapshot(before.themes(), before.namePools(), Map.of("gametest:level_ten", rule), before.injections()));
        try {
            standAt(ctx, p, A.south(3));
            p.setExperienceLevels(5);
            Packets.Entry entry = entry(ctx, p, a, b);
            check(ctx, entry.status() == Packets.STATUS_LOCKED && entry.lockMessage() != null && entry.lockMessage().getString().equals("Not yet"),
                    "locked with the rule's message");
            StagecoachService.travel(p, new Packets.Travel(a.id, b.id));
            check(ctx, !JourneyManager.isTravelling(p.getUUID()) && p.experienceLevel == 5, "and refused");
            p.setExperienceLevels(12);
            entry = entry(ctx, p, a, b);
            check(ctx, entry.status() == Packets.STATUS_OK && entry.cost() == 4, "open at level 12, at twice the fare: " + entry.cost());
        } finally {
            StagecoachData.use(before);
        }
        disconnect(p);
        ctx.succeed();
    }

    @GameTest(structure = ROAD)
    public void journeySendCarriesAPlayerFromWhereTheyStand(GameTestHelper ctx) {
        ServerPlayer owner = connect(ctx, "send-owner"), rider = connect(ctx, "send-rider");
        CoachRecord b = place(ctx, owner, B, StagecoachWood.OAK);
        standAt(ctx, rider, new BlockPos(4, 2, 20));
        BlockPos start = rider.blockPosition();
        check(ctx, run(ctx, "stagecoach journey send " + rider.getName().getString() + " " + b.id + " free") >= 1, "the command sends them");
        Journey j = JourneyManager.journeyOf(rider);
        check(ctx, j != null && !j.fromCoach() && JourneyManager.isTravelling(rider.getUUID()), "on the road, from no coach");
        check(ctx, j.xpPaid == 0 && j.itemsPaid.isEmpty(), "free means nothing paid");
        check(ctx, run(ctx, "stagecoach journey cancel " + rider.getName().getString()) == 1, "an admin cancel");
        check(ctx, rider.blockPosition().distSqr(start) <= 36 && rider.gameMode.getGameModeForPlayer() == GameType.SURVIVAL,
                "back where they stood, at " + rider.blockPosition());
        disconnect(owner);
        disconnect(rider);
        ctx.succeed();
    }

    @GameTest(structure = ROAD)
    public void aSentJourneyRecoversAfterACrash(GameTestHelper ctx) {
        ServerPlayer owner = connect(ctx, "crash-owner"), rider = connect(ctx, "crash-rider");
        GameProfile profile = rider.getGameProfile();
        CoachRecord b = place(ctx, owner, B, StagecoachWood.OAK);
        standAt(ctx, rider, new BlockPos(4, 2, 20));
        BlockPos start = rider.blockPosition();
        rider.setExperienceLevels(9);
        run(ctx, "stagecoach journey send " + rider.getName().getString() + " " + b.id);
        check(ctx, JourneyManager.isTravelling(rider.getUUID()) && rider.experienceLevel < 9, "sent and paid");
        crash(ctx, rider);
        ServerPlayer back = connect(ctx, profile);
        check(ctx, back.gameMode.getGameModeForPlayer() == GameType.SURVIVAL && back.experienceLevel == 9, "mode and fare restored");
        check(ctx, back.blockPosition().distSqr(start) <= 36, "at the spot they were sent from, at " + back.blockPosition());
        disconnect(owner);
        disconnect(back);
        ctx.succeed();
    }

    @GameTest(structure = ROAD)
    public void theApiReportsWhyAJourneyCanNotStart(GameTestHelper ctx) {
        ServerPlayer owner = connect(ctx, "api-owner"), other = connect(ctx, "api-other");
        CoachRecord a = place(ctx, owner, A, StagecoachWood.OAK), b = place(ctx, owner, B, StagecoachWood.OAK);
        StagecoachAPI api = StagecoachAPI.get();
        check(ctx, api.getStops(ctx.getLevel()).size() >= 2, "the API lists the stops");
        check(ctx, api.getStop(ctx.getLevel().getServer(), a.id).isPresent(), "and finds one by id");
        check(ctx, api.startJourney(owner, b.view(), JourneyOptions.NORMAL) == JourneyResult.OUT_OF_RANGE, "far from any stop");
        check(ctx, api.startJourney(other, b.view(), new JourneyOptions(false, false, true, false, false)) == JourneyResult.NOT_DISCOVERED,
                "an unknown destination");
        standAt(ctx, owner, A.south(3));
        owner.setExperienceLevels(0);
        check(ctx, api.startJourney(owner, b.view(), JourneyOptions.NORMAL) == JourneyResult.NOT_ENOUGH_LEVELS, "no levels");
        owner.setExperienceLevels(10);
        check(ctx, api.startJourney(owner, b.view(), JourneyOptions.NORMAL) == JourneyResult.STARTED && api.isTravelling(owner), "started");
        check(ctx, api.startJourney(owner, b.view(), JourneyOptions.NORMAL) == JourneyResult.ALREADY_TRAVELLING, "only one at a time");
        api.cancelJourney(owner);
        check(ctx, !api.isTravelling(owner) && owner.experienceLevel == 10, "cancelled with a full refund");
        disconnect(owner);
        disconnect(other);
        ctx.succeed();
    }

    @GameTest(structure = ROAD)
    public void finishedJourneysCountInStatsAndAdvancements(GameTestHelper ctx) {
        ServerPlayer p = connect(ctx, "stats");
        CoachRecord a = place(ctx, p, A, StagecoachWood.OAK), b = place(ctx, p, B, StagecoachWood.OAK);
        standAt(ctx, p, A.south(3));
        p.setExperienceLevels(10);
        withConfig("{\"instantTravel\": true}", () -> StagecoachService.travel(p, new Packets.Travel(a.id, b.id)));
        check(ctx, p.getStats().getValue(Stats.CUSTOM.get(StagecoachTriggers.JOURNEYS_COMPLETED)) == 1, "one journey counted");
        check(ctx, p.getStats().getValue(Stats.CUSTOM.get(StagecoachTriggers.BLOCKS_TRAVELLED)) == 12, "12 blocks counted");
        check(ctx, p.getStats().getValue(Stats.CUSTOM.get(StagecoachTriggers.COACHES_PLACED)) == 2, "two coaches placed");
        MinecraftServer server = ctx.getLevel().getServer();
        AdvancementHolder first = server.getAdvancements().get(Stagecoach.id("main/first_journey"));
        check(ctx, first != null && p.getAdvancements().getOrStartProgress(first).isDone(), "the built-in First Journey advancement");
        disconnect(p);
        ctx.succeed();
    }

    @GameTest(structure = ROAD)
    public void exportAndImportRebuildTheNetwork(GameTestHelper ctx) {
        BlockPos a = ctx.absolutePos(A), b = ctx.absolutePos(B);
        String id = Integer.toString(Math.abs(ctx.getLevel().getRandom().nextInt())), northName = "Northgate" + id, southName = "Southgate" + id;
        run(ctx, "stagecoach stop create " + northName + " " + a.getX() + " " + a.getY() + " " + a.getZ() + " north oak");
        run(ctx, "stagecoach stop create " + southName + " " + b.getX() + " " + b.getY() + " " + b.getZ() + " north acacia");
        CoachRecord north = state(ctx).at(ctx.getLevel().dimension(), a);
        north.locked = true;
        String file = "gametest_" + id;
        check(ctx, run(ctx, "stagecoach export " + file) >= 2, "exported");
        run(ctx, "stagecoach stop remove " + north.id);
        run(ctx, "stagecoach stop remove " + state(ctx).at(ctx.getLevel().dimension(), b).id);
        check(ctx, state(ctx).at(ctx.getLevel().dimension(), a) == null, "removed");
        run(ctx, "stagecoach import " + file);
        CoachRecord n = state(ctx).at(ctx.getLevel().dimension(), a), s = state(ctx).at(ctx.getLevel().dimension(), b);
        check(ctx, n != null && n.name.equals(northName) && n.locked && n.wood == StagecoachWood.OAK, "Northgate is back, locked: " + n);
        check(ctx, s != null && s.name.equals(southName) && s.wood == StagecoachWood.ACACIA, "Southgate is back in acacia: " + s);
        ctx.succeed();
    }

    @GameTest(structure = ROAD)
    public void keepCoachLeavesAnOutOfServiceDecoration(GameTestHelper ctx) {
        BlockPos a = ctx.absolutePos(A);
        run(ctx, "stagecoach stop create Relic " + a.getX() + " " + a.getY() + " " + a.getZ() + " north oak");
        CoachRecord r = state(ctx).at(ctx.getLevel().dimension(), a);
        run(ctx, "stagecoach stop remove " + r.id + " keepCoach");
        check(ctx, state(ctx).coach(r.id) == null, "no longer a stop");
        check(ctx, ctx.getLevel().getBlockEntity(a) instanceof StagecoachBlockEntity be && be.isDecoration(), "the blocks stay as decoration");
        check(ctx, PendingCoaches.check(ctx.getLevel(), a) == null && state(ctx).at(ctx.getLevel().dimension(), a) == null,
                "and it never registers again");
        ctx.succeed();
    }

    @GameTest(structure = ROAD)
    public void theCoachStopIsTriedFirstUntilTheVillageHasOne(GameTestHelper ctx) {
        MinecraftServer server = ctx.getLevel().getServer();
        StructureTemplatePool plains = server.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL).getValue(Identifier.withDefaultNamespace("village/plains/houses"));
        check(ctx, stops(plains.getShuffledTemplates(RandomSource.create(1))) == 0, "outside a village's assembly the pool is untouched");
        List<Object> placed = new ArrayList<>();
        StructureInjections.assembling(placed);
        try {
            List<StructurePoolElement> offered = plains.getShuffledTemplates(RandomSource.create(1));
            int shapes = stops(offered);
            boolean first = shapes >= 2;
            for (int i = 0; i < shapes; i++) first &= StructureInjections.isStopPiece(offered.get(i));
            check(ctx, first, "a village without a stop tries every shape first, got " + shapes);
            StructureInjections.apply(server);
            StructureInjections.apply(server);
            offered = plains.getShuffledTemplates(RandomSource.create(1));
            check(ctx, stops(offered) == shapes, "applying again doesn't offer them twice: " + stops(offered));
            placed.add(new PoolElementStructurePiece(server.getStructureTemplateManager(), offered.get(0), BlockPos.ZERO, 0,
                    Rotation.NONE, new BoundingBox(0, 0, 0, 1, 1, 1), LiquidSettings.APPLY_WATERLOGGING));
            check(ctx, stops(plains.getShuffledTemplates(RandomSource.create(1))) == 0, "a village that has its stop gets no second");
            placed.clear();
            withConfig("{\"naturalGeneration\": false}", () -> {
                StructureInjections.apply(server);
                check(ctx, stops(plains.getShuffledTemplates(RandomSource.create(1))) == 0, "naturalGeneration off offers nothing");
            });
        } finally {
            StructureInjections.assembling(null);
            StructureInjections.apply(server);
        }
        ctx.succeed();
    }

    @GameTest(structure = ROAD, maxTicks = 400)
    public void everyVanillaVillageGetsExactlyOneStop(GameTestHelper ctx) {
        ServerLevel level = ctx.getLevel();
        MinecraftServer server = level.getServer();
        ServerChunkCache chunks = level.getChunkSource();
        Climate.Sampler sampler = chunks.randomState().createClimateSampler(SamplerContext.builder().enableCaches().build());
        Registry<Structure> structures = server.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        for (ResourceKey<Structure> key : List.of(BuiltinStructures.VILLAGE_PLAINS, BuiltinStructures.VILLAGE_DESERT, BuiltinStructures.VILLAGE_SAVANNA,
                BuiltinStructures.VILLAGE_SNOWY, BuiltinStructures.VILLAGE_TAIGA)) {
            Holder<Structure> village = structures.getOrThrow(key);
            int wrong = 0;
            int built = 0;
            for (int i = 0; i < 20; i++) {
                StructureStart start = village.value().generate(village, level.dimension(), server.registryAccess(), chunks.getGenerator(),
                        chunks.getGenerator().getBiomeSource(), sampler, chunks.randomState(),
                        server.getStructureTemplateManager(),
                        level.getSeed() + i, new ChunkPos(4000 + i * 50, 4000), 0, level, biome -> true);
                if (!start.isValid()) continue;
                built++;
                int stops = 0;
                for (StructurePiece piece : start.getPieces()) {
                    if (piece instanceof PoolElementStructurePiece pool && StructureInjections.isStopPiece(pool.getElement())) stops++;
                }
                if (stops != 1) wrong++;
            }
            check(ctx, built > 0 && wrong == 0, key.identifier() + ": " + wrong + " of " + built + " villages without exactly one stop");
        }
        ctx.succeed();
    }

    @GameTest(structure = ROAD, maxTicks = 100)
    public void anInjectionsWoodBeatsTheThemes(GameTestHelper ctx) {
        BlockPos abs = ctx.absolutePos(A);
        ctx.getLevel().setBlockAndUpdate(abs, StagecoachRegistry.BLOCKS.get(StagecoachWood.OAK).defaultBlockState());
        StagecoachBlockEntity be = (StagecoachBlockEntity) ctx.getLevel().getBlockEntity(abs);
        CompoundTag nbt = be.saveWithoutMetadata(ctx.getLevel().registryAccess());
        nbt.putString(PendingCoaches.TEMPLATE_THEME, "stagecoach:taiga");
        nbt.putString(PendingCoaches.TEMPLATE_WOOD, "acacia");
        be.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, ctx.getLevel().registryAccess(), nbt));
        ctx.runAfterDelay(3, () -> {
            CoachRecord r = state(ctx).at(ctx.getLevel().dimension(), abs);
            check(ctx, r != null && r.wood == StagecoachWood.ACACIA, "the village's wood wins over the theme's, got " + (r == null ? null : r.wood));
            ctx.succeed();
        });
    }

    /** Other tests share the world, and their public stops show up in every list. */
    static Packets.Entry entry(GameTestHelper ctx, ServerPlayer p, CoachRecord from, CoachRecord to) {
        for (Packets.Entry e : StagecoachService.buildList(p, from).entries()) if (e.id() == to.id) return e;
        ctx.fail("stop " + to.id + " is not in the list from " + from.id);
        return null;
    }

    private static int stops(List<StructurePoolElement> elements) {
        int n = 0;
        for (StructurePoolElement e : elements) if (StructureInjections.isStopPiece(e)) n++;
        return n;
    }

    private static StructureTemplate save(GameTestHelper ctx, BlockPos controllerRel) {
        StructureTemplate template = new StructureTemplate();
        template.fillFromWorld(ctx.getLevel(), ctx.absolutePos(controllerRel).offset(-1, 0, -3), new Vec3i(3, 3, 6), false, List.of());
        return template;
    }

    /** {@code controllerRel} is where the copy's controller lands when there's no mirror. */
    private static void paste(GameTestHelper ctx, StructureTemplate template, BlockPos controllerRel, Mirror mirror) {
        BlockPos corner = ctx.absolutePos(controllerRel).offset(-1, 0, -3);
        StructurePlaceSettings data = new StructurePlaceSettings().setMirror(mirror).setRotationPivot(new BlockPos(1, 0, 3));
        template.placeInWorld(ctx.getLevel(), corner, corner, data, RandomSource.create(), 2);
    }

    private static int run(GameTestHelper ctx, String command) {
        ServerLevel level = ctx.getLevel();
        CommandSourceStack source = level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput();
        try {
            return level.getServer().getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException e) {
            return 0;
        }
    }
}
