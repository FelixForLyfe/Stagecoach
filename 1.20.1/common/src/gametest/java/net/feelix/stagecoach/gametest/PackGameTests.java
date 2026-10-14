package net.feelix.stagecoach.gametest;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
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
import net.minecraft.advancement.Advancement;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtLong;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.Stats;
import net.minecraft.structure.PoolStructurePiece;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureStart;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.pool.StructurePool;
import net.minecraft.structure.pool.StructurePoolElement;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureKeys;

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

    @GameTest(templateName = ROAD)
    public void aNetworkSavedByOneOneZeroLoadsWithDefaults(TestContext ctx) {
        UUID owner = UUID.randomUUID();
        NbtCompound v1 = new NbtCompound();
        v1.putLong("nextId", 3);
        NbtList coaches = new NbtList();
        coaches.add(oldRecord(1, "Old Mill", owner));
        coaches.add(oldRecord(2, "Dispenser Stop", null));
        v1.put("coaches", coaches);
        NbtCompound discovered = new NbtCompound();
        NbtList ids = new NbtList();
        ids.add(NbtLong.of(1));
        ids.add(NbtLong.of(2));
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
        NbtCompound saved = loaded.writeNbt(new NbtCompound());
        check(ctx, saved.getInt("dataVersion") == StagecoachState.DATA_VERSION, "written back as version " + StagecoachState.DATA_VERSION);
        CoachRecord again = StagecoachState.fromNbt(saved).coach(1);
        check(ctx, again != null && again.name.equals("Old Mill") && again.isOwner(owner), "and reads back the same");
        ctx.complete();
    }

    private static NbtCompound oldRecord(long id, String name, UUID owner) {
        NbtCompound n = new NbtCompound();
        n.putLong("id", id);
        n.putString("name", name);
        n.putLong("pos", new BlockPos(100 * (int) id, 64, 0).asLong());
        n.putString("dim", "minecraft:overworld");
        n.putString("facing", "north");
        if (owner != null) n.putUuid("owner", owner);
        return n;
    }

    @GameTest(templateName = ROAD)
    public void stopCreateMakesAnOwnerlessStopOnlyOperatorsBreak(TestContext ctx) {
        BlockPos abs = ctx.getAbsolutePos(A);
        check(ctx, run(ctx, "stagecoach stop create \"Market Cross\" " + abs.getX() + " " + abs.getY() + " " + abs.getZ() + " north spruce") == 1,
                "the command succeeds");
        CoachRecord r = state(ctx).at(ctx.getWorld().getRegistryKey(), abs);
        check(ctx, r != null && r.name.equals("Market Cross") && r.owner == null && r.createdBy == CreatedBy.ADMIN && r.wood == StagecoachWood.SPRUCE,
                "an ownerless admin stop, got " + r);
        check(ctx, ctx.getWorld().getBlockEntity(abs) instanceof StagecoachBlockEntity be && be.dropsNothing(), "it never drops an item");
        ServerPlayerEntity p = connect(ctx, "not-op");
        p.interactionManager.tryBreakBlock(StagecoachShapes.cellPos(abs, Direction.NORTH, 1));
        check(ctx, state(ctx).coach(r.id) != null, "a player who isn't an operator can't take it down");
        disconnect(p);
        ctx.complete();
    }

    @GameTest(templateName = ROAD)
    public void anAmbiguousNameIsRefusedButTheIdWorks(TestContext ctx) {
        BlockPos a = ctx.getAbsolutePos(A), b = ctx.getAbsolutePos(B);
        run(ctx, "stagecoach stop create Twin " + a.getX() + " " + a.getY() + " " + a.getZ() + " north oak");
        run(ctx, "stagecoach stop create Twin " + b.getX() + " " + b.getY() + " " + b.getZ() + " north oak");
        check(ctx, run(ctx, "stagecoach stop info Twin") == 0, "two stops called Twin: refused");
        CoachRecord first = state(ctx).at(ctx.getWorld().getRegistryKey(), a);
        check(ctx, run(ctx, "stagecoach stop info " + first.id) == 1, "by id it works");
        check(ctx, run(ctx, "stagecoach stop info \"#" + first.id + "\"") == 1, "and by #id");
        ctx.complete();
    }

    @GameTest(templateName = ROAD, tickLimit = 100)
    public void structureCopiesBecomeSeparateNamedStops(TestContext ctx) {
        ServerPlayerEntity p = connect(ctx, "builder");
        CoachRecord original = place(ctx, p, A, StagecoachWood.BIRCH);
        StagecoachBlockEntity be = (StagecoachBlockEntity) ctx.getWorld().getBlockEntity(ctx.getAbsolutePos(A));
        NbtCompound nbt = be.createNbt();
        String name = "Riverford" + Math.abs(ctx.getWorld().getRandom().nextInt());
        nbt.putString(PendingCoaches.TEMPLATE_NAME, name);
        NbtList flags = new NbtList();
        flags.add(net.minecraft.nbt.NbtString.of("public"));
        nbt.put(PendingCoaches.TEMPLATE_FLAGS, flags);
        be.readNbt(nbt);
        StructureTemplate template = save(ctx, A);
        paste(ctx, template, C, BlockMirror.NONE);
        paste(ctx, template, D, BlockMirror.NONE);
        ctx.waitAndRun(3, () -> {
            CoachRecord c = state(ctx).at(ctx.getWorld().getRegistryKey(), ctx.getAbsolutePos(C));
            CoachRecord d = state(ctx).at(ctx.getWorld().getRegistryKey(), ctx.getAbsolutePos(D));
            check(ctx, c != null && d != null, "both copies are stops");
            check(ctx, c.id != d.id && c.id != original.id && d.id != original.id, "each with its own id");
            check(ctx, c.owner == null && c.createdBy == CreatedBy.STRUCTURE && c.isPublic, "ownerless, from a structure, public as the template says");
            check(ctx, (c.name + "|" + d.name).equals(name + "|" + name + " (2)") || (c.name + "|" + d.name).equals(name + " (2)|" + name),
                    "the template name, made unique: " + c.name + " and " + d.name);
            check(ctx, state(ctx).coach(original.id) != null && state(ctx).coach(original.id).isOwner(p.getUuid()), "the original is untouched");
            check(ctx, ((StagecoachBlockEntity) ctx.getWorld().getBlockEntity(ctx.getAbsolutePos(C))).template() == null, "template data used up");
            disconnect(p);
            ctx.complete();
        });
    }

    @GameTest(templateName = ROAD, tickLimit = 100)
    public void aMirroredTemplateComesOutWhole(TestContext ctx) {
        ServerPlayerEntity p = connect(ctx, "mirror");
        place(ctx, p, A, StagecoachWood.CHERRY);
        StructureTemplate template = save(ctx, A);
        paste(ctx, template, D, BlockMirror.LEFT_RIGHT);
        ctx.waitAndRun(3, () -> {
            BlockPos found = null;
            for (BlockPos q : BlockPos.iterate(ctx.getAbsolutePos(D).add(-3, 0, -6), ctx.getAbsolutePos(D).add(3, 0, 6))) {
                if (ctx.getWorld().getBlockState(q).getBlock() instanceof StagecoachBlock) found = q.toImmutable();
            }
            check(ctx, found != null, "the mirrored controller exists");
            BlockState controller = ctx.getWorld().getBlockState(found);
            Direction facing = controller.get(StagecoachBlock.FACING);
            for (int i = 1; i < Footprint.CELL_COUNT; i++) {
                BlockState s = ctx.getWorld().getBlockState(StagecoachShapes.cellPos(found, facing, i));
                check(ctx, s.isOf(StagecoachRegistry.PART) && s.get(StagecoachPartBlock.PART) == i && s.get(StagecoachPartBlock.FACING) == facing,
                        "cell " + i + " holds the right part after mirroring, got " + s);
            }
            check(ctx, state(ctx).at(ctx.getWorld().getRegistryKey(), found) != null, "and it is a stop");
            disconnect(p);
            ctx.complete();
        });
    }

    @GameTest(templateName = ROAD, tickLimit = 100)
    public void aSetblockControllerBecomesAStopWithItsParts(TestContext ctx) {
        BlockPos abs = ctx.getAbsolutePos(A);
        ctx.getWorld().setBlockState(abs, StagecoachRegistry.BLOCKS.get(StagecoachWood.OAK).getDefaultState());
        ctx.waitAndRun(3, () -> {
            CoachRecord r = state(ctx).at(ctx.getWorld().getRegistryKey(), abs);
            check(ctx, r != null && r.owner == null && r.createdBy == CreatedBy.STRUCTURE, "registered as an ownerless stop");
            check(ctx, r.name.matches(".+ Stop( \\(\\d+\\))?"), "named from autoNameFormat, got " + r.name);
            for (int i = 1; i < Footprint.CELL_COUNT; i++) {
                check(ctx, ctx.getWorld().getBlockState(StagecoachShapes.cellPos(abs, Direction.NORTH, i)).isOf(StagecoachRegistry.PART), "part " + i + " laid");
            }
            ctx.complete();
        });
    }

    @GameTest(templateName = ROAD, tickLimit = 100)
    public void aThemedCoachTakesItsThemesWoodAndAName(TestContext ctx) {
        BlockPos abs = ctx.getAbsolutePos(A);
        ctx.getWorld().setBlockState(abs, StagecoachRegistry.BLOCKS.get(StagecoachWood.OAK).getDefaultState());
        StagecoachBlockEntity be = (StagecoachBlockEntity) ctx.getWorld().getBlockEntity(abs);
        NbtCompound nbt = be.createNbt();
        nbt.putString(PendingCoaches.TEMPLATE_THEME, "stagecoach:taiga");
        be.readNbt(nbt);
        ctx.waitAndRun(3, () -> {
            CoachRecord r = state(ctx).at(ctx.getWorld().getRegistryKey(), abs);
            check(ctx, r != null, "the themed coach is a stop");
            check(ctx, r.wood == StagecoachWood.SPRUCE && ctx.getWorld().getBlockState(abs).isOf(StagecoachRegistry.BLOCKS.get(StagecoachWood.SPRUCE)),
                    "the taiga theme makes it spruce, got " + r.wood + " / " + ctx.getWorld().getBlockState(abs));
            List<String> prefixes = StagecoachData.namePool("stagecoach:northern").words().get("prefix");
            check(ctx, prefixes.stream().anyMatch(r.name::startsWith), "a name from the northern pool, got " + r.name);
            check(ctx, r.locked, "generated coaches are locked by default");
            check(ctx, ((StagecoachBlockEntity) ctx.getWorld().getBlockEntity(abs)).dropsNothing(), "and drop nothing");
            ctx.complete();
        });
    }

    @GameTest(templateName = ROAD, tickLimit = 100)
    public void aThemedCoachInABlockedDimensionLeavesNoStop(TestContext ctx) {
        BlockPos abs = ctx.getAbsolutePos(A);
        withConfig("{\"blockedDimensions\": [\"minecraft:overworld\"]}", () -> {
            ctx.getWorld().setBlockState(abs, StagecoachRegistry.BLOCKS.get(StagecoachWood.OAK).getDefaultState());
            StagecoachBlockEntity be = (StagecoachBlockEntity) ctx.getWorld().getBlockEntity(abs);
            NbtCompound nbt = be.createNbt();
            nbt.putString(PendingCoaches.TEMPLATE_THEME, "stagecoach:plains");
            be.readNbt(nbt);
            PendingCoaches.check(ctx.getWorld(), abs);
            check(ctx, ctx.getWorld().getBlockState(abs).isAir(), "the generated coach is taken away");
            check(ctx, state(ctx).at(ctx.getWorld().getRegistryKey(), abs) == null, "and is no stop");
        });
        ctx.complete();
    }

    @GameTest(templateName = ROAD)
    public void lockedStopsSurviveTheirOwnerAndExplosions(TestContext ctx) {
        ServerPlayerEntity p = connect(ctx, "locked");
        CoachRecord a = place(ctx, p, A, StagecoachWood.OAK);
        a.locked = true;
        p.interactionManager.tryBreakBlock(StagecoachShapes.cellPos(ctx.getAbsolutePos(A), Direction.NORTH, 1));
        check(ctx, state(ctx).coach(a.id) != null, "even the owner can't take a locked stop down");
        withConfig("{\"onlyOwnerCanBreak\": false}", () -> {
            BlockPos c = ctx.getAbsolutePos(A);
            ctx.getWorld().createExplosion(null, c.getX() + 0.5, c.getY() + 1, c.getZ() - 1.5, 4f, World.ExplosionSourceType.TNT);
            check(ctx, state(ctx).coach(a.id) != null && ctx.getWorld().getBlockState(c).getBlock() instanceof StagecoachBlock,
                    "an explosion leaves a locked coach alone");
        });
        disconnect(p);
        ctx.complete();
    }

    @GameTest(templateName = ROAD)
    public void hiddenStopsAreNeverDestinationsButCanBeLeft(TestContext ctx) {
        ServerPlayerEntity p = connect(ctx, "hidden");
        CoachRecord a = place(ctx, p, A, StagecoachWood.OAK), b = place(ctx, p, B, StagecoachWood.OAK);
        b.hidden = true;
        check(ctx, StagecoachService.buildList(p, a).entries().stream().noneMatch(e -> e.id() == b.id), "B is not offered from A");
        standAt(ctx, p, A.south(3));
        p.setExperienceLevel(10);
        StagecoachService.travel(p, new Packets.Travel(a.id, b.id));
        check(ctx, !JourneyManager.isTravelling(p.getUuid()), "and a crafted request can't go there");
        check(ctx, StagecoachService.buildList(p, b).entries().stream().anyMatch(e -> e.id() == a.id), "but from B, A is listed");
        disconnect(p);
        ctx.complete();
    }

    @GameTest(templateName = ROAD)
    public void publicStopsAreKnownWithoutDiscovery(TestContext ctx) {
        ServerPlayerEntity owner = connect(ctx, "pub-owner"), visitor = connect(ctx, "pub-visitor");
        CoachRecord a = place(ctx, owner, A, StagecoachWood.OAK), b = place(ctx, owner, B, StagecoachWood.OAK);
        state(ctx).discover(visitor.getUuid(), a.id);
        check(ctx, StagecoachService.buildList(visitor, a).entries().stream().noneMatch(e -> e.id() == b.id), "B unknown to the visitor");
        b.isPublic = true;
        check(ctx, StagecoachService.buildList(visitor, a).entries().stream().anyMatch(e -> e.id() == b.id), "public B is listed without discovering it");
        disconnect(owner);
        disconnect(visitor);
        ctx.complete();
    }

    @GameTest(templateName = ROAD)
    public void theListedFareIsTheChargedFare(TestContext ctx) {
        ServerPlayerEntity p = connect(ctx, "fares");
        CoachRecord a = place(ctx, p, A, StagecoachWood.OAK), b = place(ctx, p, B, StagecoachWood.OAK);
        standAt(ctx, p, A.south(3));
        b.costMultiplier = 3;
        int listed = entry(ctx, p, a, b).cost();
        check(ctx, listed == 6, "2 levels x 3 = 6, listed " + listed);
        p.setExperienceLevel(20);
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
        ctx.complete();
    }

    @GameTest(templateName = ROAD)
    public void aTravelRuleLocksAStopUntilItsRequirementIsMet(TestContext ctx) {
        ServerPlayerEntity p = connect(ctx, "rules");
        CoachRecord a = place(ctx, p, A, StagecoachWood.OAK), b = place(ctx, p, B, StagecoachWood.OAK);
        StagecoachData.Snapshot before = StagecoachData.get();
        TravelRule rule = TravelRule.parse(JsonParser.parseString("{\"applies_to\": {\"to_stops\": [\"#" + b.id + "\"]},"
                + " \"requirements\": {\"min_level\": 10}, \"deny_message\": {\"text\": \"Not yet\"}, \"cost_multiplier\": 2}").getAsJsonObject());
        StagecoachData.use(new StagecoachData.Snapshot(before.themes(), before.namePools(), Map.of("gametest:level_ten", rule), before.injections()));
        try {
            standAt(ctx, p, A.south(3));
            p.setExperienceLevel(5);
            Packets.Entry entry = entry(ctx, p, a, b);
            check(ctx, entry.status() == Packets.STATUS_LOCKED && entry.lockMessage() != null && entry.lockMessage().getString().equals("Not yet"),
                    "locked with the rule's message");
            StagecoachService.travel(p, new Packets.Travel(a.id, b.id));
            check(ctx, !JourneyManager.isTravelling(p.getUuid()) && p.experienceLevel == 5, "and refused");
            p.setExperienceLevel(12);
            entry = entry(ctx, p, a, b);
            check(ctx, entry.status() == Packets.STATUS_OK && entry.cost() == 4, "open at level 12, at twice the fare: " + entry.cost());
        } finally {
            StagecoachData.use(before);
        }
        disconnect(p);
        ctx.complete();
    }

    @GameTest(templateName = ROAD)
    public void journeySendCarriesAPlayerFromWhereTheyStand(TestContext ctx) {
        ServerPlayerEntity owner = connect(ctx, "send-owner"), rider = connect(ctx, "send-rider");
        CoachRecord b = place(ctx, owner, B, StagecoachWood.OAK);
        standAt(ctx, rider, new BlockPos(4, 2, 20));
        BlockPos start = rider.getBlockPos();
        check(ctx, run(ctx, "stagecoach journey send " + rider.getName().getString() + " " + b.id + " free") >= 1, "the command sends them");
        Journey j = JourneyManager.journeyOf(rider);
        check(ctx, j != null && !j.fromCoach() && JourneyManager.isTravelling(rider.getUuid()), "on the road, from no coach");
        check(ctx, j.xpPaid == 0 && j.itemsPaid.isEmpty(), "free means nothing paid");
        check(ctx, run(ctx, "stagecoach journey cancel " + rider.getName().getString()) == 1, "an admin cancel");
        check(ctx, rider.getBlockPos().getSquaredDistance(start) <= 36 && rider.interactionManager.getGameMode() == GameMode.SURVIVAL,
                "back where they stood, at " + rider.getBlockPos());
        disconnect(owner);
        disconnect(rider);
        ctx.complete();
    }

    @GameTest(templateName = ROAD)
    public void aSentJourneyRecoversAfterACrash(TestContext ctx) {
        ServerPlayerEntity owner = connect(ctx, "crash-owner"), rider = connect(ctx, "crash-rider");
        GameProfile profile = rider.getGameProfile();
        CoachRecord b = place(ctx, owner, B, StagecoachWood.OAK);
        standAt(ctx, rider, new BlockPos(4, 2, 20));
        BlockPos start = rider.getBlockPos();
        rider.setExperienceLevel(9);
        run(ctx, "stagecoach journey send " + rider.getName().getString() + " " + b.id);
        check(ctx, JourneyManager.isTravelling(rider.getUuid()) && rider.experienceLevel < 9, "sent and paid");
        crash(ctx, rider);
        ServerPlayerEntity back = connect(ctx, profile);
        check(ctx, back.interactionManager.getGameMode() == GameMode.SURVIVAL && back.experienceLevel == 9, "mode and fare restored");
        check(ctx, back.getBlockPos().getSquaredDistance(start) <= 36, "at the spot they were sent from, at " + back.getBlockPos());
        disconnect(owner);
        disconnect(back);
        ctx.complete();
    }

    @GameTest(templateName = ROAD)
    public void theApiReportsWhyAJourneyCanNotStart(TestContext ctx) {
        ServerPlayerEntity owner = connect(ctx, "api-owner"), other = connect(ctx, "api-other");
        CoachRecord a = place(ctx, owner, A, StagecoachWood.OAK), b = place(ctx, owner, B, StagecoachWood.OAK);
        StagecoachAPI api = StagecoachAPI.get();
        check(ctx, api.getStops(ctx.getWorld()).size() >= 2, "the API lists the stops");
        check(ctx, api.getStop(ctx.getWorld().getServer(), a.id).isPresent(), "and finds one by id");
        check(ctx, api.startJourney(owner, b.view(), JourneyOptions.NORMAL) == JourneyResult.OUT_OF_RANGE, "far from any stop");
        check(ctx, api.startJourney(other, b.view(), new JourneyOptions(false, false, true, false, false)) == JourneyResult.NOT_DISCOVERED,
                "an unknown destination");
        standAt(ctx, owner, A.south(3));
        owner.setExperienceLevel(0);
        check(ctx, api.startJourney(owner, b.view(), JourneyOptions.NORMAL) == JourneyResult.NOT_ENOUGH_LEVELS, "no levels");
        owner.setExperienceLevel(10);
        check(ctx, api.startJourney(owner, b.view(), JourneyOptions.NORMAL) == JourneyResult.STARTED && api.isTravelling(owner), "started");
        check(ctx, api.startJourney(owner, b.view(), JourneyOptions.NORMAL) == JourneyResult.ALREADY_TRAVELLING, "only one at a time");
        api.cancelJourney(owner);
        check(ctx, !api.isTravelling(owner) && owner.experienceLevel == 10, "cancelled with a full refund");
        disconnect(owner);
        disconnect(other);
        ctx.complete();
    }

    @GameTest(templateName = ROAD)
    public void finishedJourneysCountInStatsAndAdvancements(TestContext ctx) {
        ServerPlayerEntity p = connect(ctx, "stats");
        CoachRecord a = place(ctx, p, A, StagecoachWood.OAK), b = place(ctx, p, B, StagecoachWood.OAK);
        standAt(ctx, p, A.south(3));
        p.setExperienceLevel(10);
        withConfig("{\"instantTravel\": true}", () -> StagecoachService.travel(p, new Packets.Travel(a.id, b.id)));
        check(ctx, p.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(StagecoachTriggers.JOURNEYS_COMPLETED)) == 1, "one journey counted");
        check(ctx, p.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(StagecoachTriggers.BLOCKS_TRAVELLED)) == 12, "12 blocks counted");
        check(ctx, p.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(StagecoachTriggers.COACHES_PLACED)) == 2, "two coaches placed");
        MinecraftServer server = ctx.getWorld().getServer();
        Advancement first = server.getAdvancementLoader().get(Stagecoach.id("main/first_journey"));
        check(ctx, first != null && p.getAdvancementTracker().getProgress(first).isDone(), "the built-in First Journey advancement");
        disconnect(p);
        ctx.complete();
    }

    @GameTest(templateName = ROAD)
    public void exportAndImportRebuildTheNetwork(TestContext ctx) {
        BlockPos a = ctx.getAbsolutePos(A), b = ctx.getAbsolutePos(B);
        String id = Integer.toString(Math.abs(ctx.getWorld().getRandom().nextInt())), northName = "Northgate" + id, southName = "Southgate" + id;
        run(ctx, "stagecoach stop create " + northName + " " + a.getX() + " " + a.getY() + " " + a.getZ() + " north oak");
        run(ctx, "stagecoach stop create " + southName + " " + b.getX() + " " + b.getY() + " " + b.getZ() + " north acacia");
        CoachRecord north = state(ctx).at(ctx.getWorld().getRegistryKey(), a);
        north.locked = true;
        String file = "gametest_" + id;
        check(ctx, run(ctx, "stagecoach export " + file) >= 2, "exported");
        run(ctx, "stagecoach stop remove " + north.id);
        run(ctx, "stagecoach stop remove " + state(ctx).at(ctx.getWorld().getRegistryKey(), b).id);
        check(ctx, state(ctx).at(ctx.getWorld().getRegistryKey(), a) == null, "removed");
        run(ctx, "stagecoach import " + file);
        CoachRecord n = state(ctx).at(ctx.getWorld().getRegistryKey(), a), s = state(ctx).at(ctx.getWorld().getRegistryKey(), b);
        check(ctx, n != null && n.name.equals(northName) && n.locked && n.wood == StagecoachWood.OAK, "Northgate is back, locked: " + n);
        check(ctx, s != null && s.name.equals(southName) && s.wood == StagecoachWood.ACACIA, "Southgate is back in acacia: " + s);
        ctx.complete();
    }

    @GameTest(templateName = ROAD)
    public void keepCoachLeavesAnOutOfServiceDecoration(TestContext ctx) {
        BlockPos a = ctx.getAbsolutePos(A);
        run(ctx, "stagecoach stop create Relic " + a.getX() + " " + a.getY() + " " + a.getZ() + " north oak");
        CoachRecord r = state(ctx).at(ctx.getWorld().getRegistryKey(), a);
        run(ctx, "stagecoach stop remove " + r.id + " keepCoach");
        check(ctx, state(ctx).coach(r.id) == null, "no longer a stop");
        check(ctx, ctx.getWorld().getBlockEntity(a) instanceof StagecoachBlockEntity be && be.isDecoration(), "the blocks stay as decoration");
        check(ctx, PendingCoaches.check(ctx.getWorld(), a) == null && state(ctx).at(ctx.getWorld().getRegistryKey(), a) == null,
                "and it never registers again");
        ctx.complete();
    }

    @GameTest(templateName = ROAD)
    public void theCoachStopIsTriedFirstUntilTheVillageHasOne(TestContext ctx) {
        MinecraftServer server = ctx.getWorld().getServer();
        StructurePool plains = server.getRegistryManager().get(RegistryKeys.TEMPLATE_POOL).get(new Identifier("minecraft", "village/plains/houses"));
        check(ctx, stops(plains.getElementIndicesInRandomOrder(Random.create(1))) == 0, "outside a village's assembly the pool is untouched");
        List<Object> placed = new ArrayList<>();
        StructureInjections.assembling(placed);
        try {
            List<StructurePoolElement> offered = plains.getElementIndicesInRandomOrder(Random.create(1));
            int shapes = stops(offered);
            boolean first = shapes >= 2;
            for (int i = 0; i < shapes; i++) first &= StructureInjections.isStopPiece(offered.get(i));
            check(ctx, first, "a village without a stop tries every shape first, got " + shapes);
            StructureInjections.apply(server);
            StructureInjections.apply(server);
            offered = plains.getElementIndicesInRandomOrder(Random.create(1));
            check(ctx, stops(offered) == shapes, "applying again doesn't offer them twice: " + stops(offered));
            placed.add(new PoolStructurePiece(server.getStructureTemplateManager(), offered.get(0), BlockPos.ORIGIN, 0,
                    BlockRotation.NONE, new BlockBox(0, 0, 0, 1, 1, 1)));
            check(ctx, stops(plains.getElementIndicesInRandomOrder(Random.create(1))) == 0, "a village that has its stop gets no second");
            placed.clear();
            withConfig("{\"naturalGeneration\": false}", () -> {
                StructureInjections.apply(server);
                check(ctx, stops(plains.getElementIndicesInRandomOrder(Random.create(1))) == 0, "naturalGeneration off offers nothing");
            });
        } finally {
            StructureInjections.assembling(null);
            StructureInjections.apply(server);
        }
        ctx.complete();
    }

    @GameTest(templateName = ROAD, tickLimit = 400)
    public void everyVanillaVillageGetsExactlyOneStop(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        MinecraftServer server = world.getServer();
        ServerChunkManager chunks = world.getChunkManager();
        Registry<Structure> structures = server.getRegistryManager().get(RegistryKeys.STRUCTURE);
        for (RegistryKey<Structure> key : List.of(StructureKeys.VILLAGE_PLAINS, StructureKeys.VILLAGE_DESERT, StructureKeys.VILLAGE_SAVANNA,
                StructureKeys.VILLAGE_SNOWY, StructureKeys.VILLAGE_TAIGA)) {
            Structure village = structures.get(key);
            int wrong = 0;
            int built = 0;
            for (int i = 0; i < 20; i++) {
                StructureStart start = village.createStructureStart(server.getRegistryManager(), chunks.getChunkGenerator(),
                        chunks.getChunkGenerator().getBiomeSource(), chunks.getNoiseConfig(), server.getStructureTemplateManager(),
                        world.getSeed() + i, new ChunkPos(4000 + i * 50, 4000), 0, world, biome -> true);
                if (!start.hasChildren()) continue;
                built++;
                int stops = 0;
                for (StructurePiece piece : start.getChildren()) {
                    if (piece instanceof PoolStructurePiece pool && StructureInjections.isStopPiece(pool.getPoolElement())) stops++;
                }
                if (stops != 1) wrong++;
            }
            check(ctx, built > 0 && wrong == 0, key.getValue() + ": " + wrong + " of " + built + " villages without exactly one stop");
        }
        ctx.complete();
    }

    @GameTest(templateName = ROAD, tickLimit = 100)
    public void anInjectionsWoodBeatsTheThemes(TestContext ctx) {
        BlockPos abs = ctx.getAbsolutePos(A);
        ctx.getWorld().setBlockState(abs, StagecoachRegistry.BLOCKS.get(StagecoachWood.OAK).getDefaultState());
        StagecoachBlockEntity be = (StagecoachBlockEntity) ctx.getWorld().getBlockEntity(abs);
        NbtCompound nbt = be.createNbt();
        nbt.putString(PendingCoaches.TEMPLATE_THEME, "stagecoach:taiga");
        nbt.putString(PendingCoaches.TEMPLATE_WOOD, "acacia");
        be.readNbt(nbt);
        ctx.waitAndRun(3, () -> {
            CoachRecord r = state(ctx).at(ctx.getWorld().getRegistryKey(), abs);
            check(ctx, r != null && r.wood == StagecoachWood.ACACIA, "the village's wood wins over the theme's, got " + (r == null ? null : r.wood));
            ctx.complete();
        });
    }

    /** Other tests share the world, and their public stops show up in every list. */
    static Packets.Entry entry(TestContext ctx, ServerPlayerEntity p, CoachRecord from, CoachRecord to) {
        for (Packets.Entry e : StagecoachService.buildList(p, from).entries()) if (e.id() == to.id) return e;
        ctx.throwGameTestException("stop " + to.id + " is not in the list from " + from.id);
        return null;
    }

    private static int stops(List<StructurePoolElement> elements) {
        int n = 0;
        for (StructurePoolElement e : elements) if (StructureInjections.isStopPiece(e)) n++;
        return n;
    }

    private static StructureTemplate save(TestContext ctx, BlockPos controllerRel) {
        StructureTemplate template = new StructureTemplate();
        template.saveFromWorld(ctx.getWorld(), ctx.getAbsolutePos(controllerRel).add(-1, 0, -3), new Vec3i(3, 3, 6), false, null);
        return template;
    }

    /** {@code controllerRel} is where the copy's controller lands when there's no mirror. */
    private static void paste(TestContext ctx, StructureTemplate template, BlockPos controllerRel, BlockMirror mirror) {
        BlockPos corner = ctx.getAbsolutePos(controllerRel).add(-1, 0, -3);
        StructurePlacementData data = new StructurePlacementData().setMirror(mirror).setPosition(new BlockPos(1, 0, 3));
        template.place(ctx.getWorld(), corner, corner, data, Random.create(), 2);
    }

    private static int run(TestContext ctx, String command) {
        ServerWorld world = ctx.getWorld();
        ServerCommandSource source = world.getServer().getCommandSource().withWorld(world).withSilent();
        return world.getServer().getCommandManager().executeWithPrefix(source, command);
    }
}
