package net.feelix.stagecoach.gametest;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.feelix.stagecoach.StagecoachRegistry;
import net.feelix.stagecoach.StagecoachWood;
import net.feelix.stagecoach.block.StagecoachBlock;
import net.feelix.stagecoach.block.StagecoachPartBlock;
import net.feelix.stagecoach.block.StagecoachStructure;
import net.feelix.stagecoach.config.StagecoachConfig;
import net.feelix.stagecoach.footprint.Footprint;
import net.feelix.stagecoach.footprint.StagecoachFootprintData;
import net.feelix.stagecoach.footprint.StagecoachShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class StagecoachGameTests {
    static final String EMPTY_STRUCTURE = "stagecoach:gametest/empty";

    /** Every facing's footprint stays inside the 8x8x8 structure from here. */
    private static final BlockPos CONTROLLER = new BlockPos(4, 1, 4);

    @GameTest(structure = EMPTY_STRUCTURE)
    public void placesAndCollidesFacingNorth(GameTestHelper ctx) {
        placesAndCollides(ctx, Direction.NORTH, StagecoachWood.OAK);
    }

    @GameTest(structure = EMPTY_STRUCTURE)
    public void placesAndCollidesFacingEast(GameTestHelper ctx) {
        placesAndCollides(ctx, Direction.EAST, StagecoachWood.SPRUCE);
    }

    @GameTest(structure = EMPTY_STRUCTURE)
    public void placesAndCollidesFacingSouth(GameTestHelper ctx) {
        placesAndCollides(ctx, Direction.SOUTH, StagecoachWood.DARK_OAK);
    }

    @GameTest(structure = EMPTY_STRUCTURE)
    public void placesAndCollidesFacingWest(GameTestHelper ctx) {
        placesAndCollides(ctx, Direction.WEST, StagecoachWood.WARPED);
    }

    private void placesAndCollides(GameTestHelper ctx, Direction facing, StagecoachWood wood) {
        ServerLevel level = ctx.getLevel();
        floor(ctx);
        BlockPos c = ctx.absolutePos(CONTROLLER);
        ItemStack stack = new ItemStack(item(wood), 2);
        place(level, player(ctx, "place-" + facing), stack, c, facing);

        expectIntact(ctx, c, facing, wood);
        if (stack.getCount() != 1) ctx.fail("placement should use exactly one item, left " + stack.getCount());

        // a cell must never cull a neighbour's face or dim light like a full cube
        for (int i = 0; i < Footprint.CELL_COUNT; i++) {
            BlockPos p = StagecoachShapes.cellPos(c, facing, i);
            BlockState s = level.getBlockState(p);
            if (s.canOcclude() || !s.getOcclusionShape().isEmpty() || s.getLightDampening() != 0 || s.isRedstoneConductor(level, p)) {
                ctx.fail("cell " + i + " culls or blocks light like a full cube");
            }
        }

        expectSolidFromEverySide(ctx, c, facing);
        expectStandableTop(ctx, c, facing);

        // lighting needs a moment to settle before the sky light under the coach matches open air
        ctx.runAfterDelay(20, () -> {
            int under = level.getBrightness(LightLayer.SKY, c);
            int open = level.getBrightness(LightLayer.SKY, ctx.absolutePos(new BlockPos(0, 1, 0)));
            if (under != open) ctx.fail("the coach blocks sky light: " + under + " under it vs " + open + " in the open");
            ctx.succeed();
        });
    }

    /** The front walk must stop at the shaft tip (2.81 blocks out): proof the shaft is solid. */
    private static void expectSolidFromEverySide(GameTestHelper ctx, BlockPos c, Direction facing) {
        ServerLevel level = ctx.getLevel();
        Vec3 centre = Vec3.atBottomCenterOf(c);
        AABB column = new AABB(c.getX(), c.getY(), c.getZ(), c.getX() + 1, c.getY() + 3, c.getZ() + 1);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            Vec3 out = Vec3.atLowerCornerOf(d.getUnitVec3i());
            Vec3 side = Vec3.atLowerCornerOf(d.getClockWise().getUnitVec3i());
            for (double lateral : new double[] {-0.35, 0, 0.35}) {
                Zombie probe = probe(level, centre.add(out.scale(3.6)).add(side.scale(lateral)));
                probe.move(MoverType.SELF, out.scale(-3.6));
                String where = "facing " + facing + ", walking in from " + d + " at lateral " + lateral;
                if (probe.getBoundingBox().intersects(column)) ctx.fail("walked through the coach: " + where);
                if (!level.noCollision(probe, probe.getBoundingBox().deflate(1.0e-3))) ctx.fail("ended inside collision: " + where);
                double dist = probe.position().subtract(centre).dot(out);
                if (d == facing && lateral == 0 && dist < 3.0) ctx.fail("the shaft is not solid: stopped " + dist + " out, " + where);
                probe.discard();
            }
        }
    }

    /** The probe sits on the rotation axis, so the top it lands on is the same in every facing. */
    private static void expectStandableTop(GameTestHelper ctx, BlockPos c, Direction facing) {
        ServerLevel level = ctx.getLevel();
        double half = EntityTypes.ZOMBIE.getWidth() / 2 * 16; // pixels
        int top = 0;
        for (int i = 0; i < Footprint.CELL_COUNT; i++) {
            int[] cell = StagecoachFootprintData.CELLS[i];
            if (cell[0] != 0 || cell[2] != 0) continue;
            for (int[] b : StagecoachFootprintData.BOXES[i]) {
                if (b[0] < 8 + half && b[3] > 8 - half && b[2] < 8 + half && b[5] > 8 - half) top = Math.max(top, cell[1] * 16 + b[4]);
            }
        }
        double expected = top / 16.0;
        for (double along : new double[] {1.0, -1.0}) {
            Zombie probe = probe(level, Vec3.atBottomCenterOf(c).add(0, 5, 0));
            probe.move(MoverType.SELF, new Vec3(0, -6, 0));
            double landed = probe.getY() - c.getY();
            if (Math.abs(landed - expected) > 1.0e-6) {
                ctx.fail("landed at +" + landed + " on the coach (facing " + facing + "), collision data says +" + expected);
            }
            probe.setOnGround(true);
            probe.move(MoverType.SELF, Vec3.atLowerCornerOf(facing.getUnitVec3i()).scale(along));
            probe.move(MoverType.SELF, new Vec3(0, -1, 0));
            double after = probe.getY() - c.getY();
            if (after < 1.0) ctx.fail("dropped through the coach walking " + along + " along it (facing " + facing + "): +" + after);
            probe.discard();
        }
    }

    @GameTest(structure = EMPTY_STRUCTURE)
    public void refusedAgainstAWall(GameTestHelper ctx) {
        floor(ctx);
        BlockPos c = ctx.absolutePos(CONTROLLER);
        ctx.getLevel().setBlockAndUpdate(StagecoachShapes.cellPos(c, Direction.NORTH, indexOf(0, 1, -2)), Blocks.STONE.defaultBlockState());
        expectRefused(ctx, c, Direction.NORTH, c.below());
        ctx.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE)
    public void refusedWhenAnEntityIsInTheWay(GameTestHelper ctx) {
        floor(ctx);
        BlockPos c = ctx.absolutePos(CONTROLLER);
        // world coordinates on purpose: 1.20.1's getRelativePos turns 180 degrees even in unrotated tests
        BlockPos cell = StagecoachShapes.cellPos(c, Direction.NORTH, indexOf(0, 0, -2));
        Pig pig = EntityTypes.PIG.create(ctx.getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        pig.snapTo(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5, 0, 0);
        pig.setNoAi(true);
        ctx.getLevel().addFreshEntity(pig);
        expectRefused(ctx, c, Direction.NORTH, c.below());
        pig.discard();
        ctx.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE)
    public void refusedAtTheBuildLimit(GameTestHelper ctx) {
        ServerLevel level = ctx.getLevel();
        BlockPos anchor = ctx.absolutePos(CONTROLLER);
        BlockPos c = new BlockPos(anchor.getX(), level.getMaxY(), anchor.getZ()); // parts would be at topY and above
        level.setBlockAndUpdate(c.below(), Blocks.STONE.defaultBlockState());
        try {
            expectRefused(ctx, c, Direction.NORTH, c.below());
        } finally {
            level.setBlockAndUpdate(c.below(), Blocks.AIR.defaultBlockState());
        }
        ctx.succeed();
    }

    private static void expectRefused(GameTestHelper ctx, BlockPos c, Direction facing, BlockPos ground) {
        ServerLevel level = ctx.getLevel();
        ItemStack stack = new ItemStack(item(StagecoachWood.BIRCH), 1);
        place(level, player(ctx, "refused"), stack, c, facing);
        if (stack.getCount() != 1) ctx.fail("a refused placement used the item");
        for (int i = 0; i < Footprint.CELL_COUNT; i++) {
            BlockState s = level.getBlockState(StagecoachShapes.cellPos(c, facing, i));
            if (s.getBlock() instanceof StagecoachBlock || s.is(StagecoachRegistry.PART)) {
                ctx.fail("a refused placement left cell " + i + " behind");
            }
        }
    }

    @GameTest(structure = EMPTY_STRUCTURE)
    public void survivalBreakingAMiddlePartDropsOneItem(GameTestHelper ctx) {
        ServerPlayer breaker = player(ctx, "survival-breaker");
        BlockPos c = placeNorth(ctx, StagecoachWood.JUNGLE, breaker);
        breaker.gameMode.destroyBlock(StagecoachShapes.cellPos(c, Direction.NORTH, indexOf(0, 1, 0)));
        expectGone(ctx, c, Direction.NORTH);
        expectItems(ctx, c, StagecoachWood.JUNGLE, 1);
        ctx.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE)
    public void survivalBreakingTheControllerDropsOneItem(GameTestHelper ctx) {
        ServerPlayer owner = player(ctx, "controller-breaker");
        BlockPos c = placeNorth(ctx, StagecoachWood.ACACIA, owner);
        owner.gameMode.destroyBlock(c);
        expectGone(ctx, c, Direction.NORTH);
        expectItems(ctx, c, StagecoachWood.ACACIA, 1);
        ctx.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE)
    public void creativeBreakingDropsNothing(GameTestHelper ctx) {
        ServerPlayer breaker = player(ctx, "creative-breaker");
        BlockPos c = placeNorth(ctx, StagecoachWood.MANGROVE, breaker);
        breaker.setGameMode(GameType.CREATIVE);
        breaker.gameMode.destroyBlock(StagecoachShapes.cellPos(c, Direction.NORTH, indexOf(0, 2, -1)));
        expectGone(ctx, c, Direction.NORTH);
        expectItems(ctx, c, StagecoachWood.MANGROVE, 0);
        ctx.succeed();
    }

    /** Needs onlyOwnerCanBreak off: protected coaches are blast-proof. */
    @GameTest(structure = EMPTY_STRUCTURE)
    public void anExplosionRemovesEverythingAndDropsOneItem(GameTestHelper ctx) {
        BlockPos c = placeNorth(ctx, StagecoachWood.CRIMSON);
        Vec3 at = Vec3.atCenterOf(StagecoachShapes.cellPos(c, Direction.NORTH, indexOf(0, 1, 1)));
        // synchronous until the finally, so switching the global config is safe
        StagecoachConfig before = StagecoachConfig.get();
        StagecoachConfig.set(StagecoachConfig.parse("{\"onlyOwnerCanBreak\": false}", new java.util.ArrayList<>()));
        try {
            ctx.getLevel().explode(null, at.x, at.y, at.z, 4.0f, Level.ExplosionInteraction.TNT);
        } finally {
            StagecoachConfig.set(before);
        }
        expectGone(ctx, c, Direction.NORTH);
        expectItems(ctx, c, StagecoachWood.CRIMSON, 1);
        ctx.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE)
    public void explosionsCannotBreakAProtectedCoach(GameTestHelper ctx) {
        BlockPos c = placeNorth(ctx, StagecoachWood.WARPED);
        Vec3 at = Vec3.atCenterOf(StagecoachShapes.cellPos(c, Direction.NORTH, indexOf(0, 1, 1)));
        ctx.getLevel().explode(null, at.x, at.y, at.z, 4.0f, Level.ExplosionInteraction.TNT);
        expectIntact(ctx, c, Direction.NORTH, StagecoachWood.WARPED);
        expectItems(ctx, c, StagecoachWood.WARPED, 0);
        ctx.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE)
    public void pistonsCannotMoveIt(GameTestHelper ctx) {
        BlockPos c = placeNorth(ctx, StagecoachWood.BAMBOO);
        ServerLevel level = ctx.getLevel();
        BlockPos part = StagecoachShapes.cellPos(c, Direction.NORTH, indexOf(1, 1, 0)); // east side of the body
        BlockPos piston = part.east();
        level.setBlockAndUpdate(piston, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.WEST));
        level.setBlockAndUpdate(piston.east(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        ctx.runAfterDelay(4, () -> {
            if (level.getBlockState(piston).getValue(PistonBaseBlock.EXTENDED)) ctx.fail("the piston extended into the coach");
            expectIntact(ctx, c, Direction.NORTH, StagecoachWood.BAMBOO);
            ctx.succeed();
        });
    }

    @GameTest(structure = EMPTY_STRUCTURE)
    public void anOrphanedPartRemovesItselfWithoutDropping(GameTestHelper ctx) {
        ServerLevel level = ctx.getLevel();
        BlockPos c = ctx.absolutePos(CONTROLLER);
        BlockState controller = StagecoachRegistry.BLOCKS.get(StagecoachWood.CHERRY).defaultBlockState();
        BlockPos p = StagecoachShapes.cellPos(c, Direction.NORTH, indexOf(0, 1, 0));
        BlockState part = StagecoachStructure.partState(controller, indexOf(0, 1, 0));
        level.setBlockAndUpdate(p, part); // no controller at c
        level.getBlockState(p).randomTick(level, p, level.getRandom());
        if (!level.getBlockState(p).isAir()) ctx.fail("orphaned part survived its random tick");
        expectItems(ctx, c, StagecoachWood.CHERRY, 0);
        ctx.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE)
    public void everyWoodCraftsItsOwnStagecoach(GameTestHelper ctx) {
        ServerLevel level = ctx.getLevel();
        for (StagecoachWood wood : StagecoachWood.values()) {
            net.minecraft.world.item.Item planks = wood.planks().asItem();
            net.minecraft.world.item.Item[] pattern = {
                    planks, net.minecraft.world.item.Items.CHEST, planks,
                    net.minecraft.world.item.Items.LEAD, net.minecraft.world.item.Items.DIAMOND_BLOCK, net.minecraft.world.item.Items.LEAD,
                    net.minecraft.world.item.Items.DIAMOND_BLOCK, net.minecraft.world.item.Items.GOLD_BLOCK, net.minecraft.world.item.Items.DIAMOND_BLOCK};
            var grid = net.minecraft.world.item.crafting.CraftingInput.of(3, 3,
                    java.util.Arrays.stream(pattern).map(ItemStack::new).toList());
            ItemStack out = level.recipeAccess().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, grid, level)
                    .map(r -> r.value().assemble(grid)).orElse(ItemStack.EMPTY);
            if (!out.is(item(wood)) || out.getCount() != 1) ctx.fail(wood + " planks crafted " + out);
        }
        ctx.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE)
    public void aRefusalSaysWhatIsInTheWay(GameTestHelper ctx) {
        floor(ctx);
        ServerLevel level = ctx.getLevel();
        BlockPos c = ctx.absolutePos(CONTROLLER);
        ServerPlayer p = player(ctx, "why");
        p.snapTo(c.getX() + 0.5, c.getY(), c.getZ() + 8.5, Direction.NORTH.toYRot(), 0);
        ItemStack stack = new ItemStack(item(StagecoachWood.OAK));
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(c.below()).add(0, 0.5, 0), Direction.UP, c.below(), false);
        BlockState controller = StagecoachRegistry.BLOCKS.get(StagecoachWood.OAK).defaultBlockState().setValue(StagecoachBlock.FACING, Direction.NORTH);
        if (StagecoachStructure.check(new net.minecraft.world.item.context.BlockPlaceContext(p, InteractionHand.MAIN_HAND, stack, hit), controller, null) != null) {
            ctx.fail("an empty spot must be accepted");
        }
        BlockPos wall = StagecoachShapes.cellPos(c, Direction.NORTH, indexOf(0, 1, -2));
        level.setBlockAndUpdate(wall, Blocks.BOOKSHELF.defaultBlockState());
        StagecoachStructure.Refusal r = StagecoachStructure.check(new net.minecraft.world.item.context.BlockPlaceContext(p, InteractionHand.MAIN_HAND, stack, hit), controller, null);
        if (r == null || r.reason() != StagecoachStructure.Reason.BLOCKED || !r.pos().equals(wall)
                || !r.what().getString().equals(Blocks.BOOKSHELF.getName().getString())) {
            ctx.fail("expected BLOCKED by the bookshelf at " + wall + ", got " + r);
        }
        boolean[] blocked = new boolean[Footprint.CELL_COUNT];
        StagecoachStructure.check(new net.minecraft.world.item.context.BlockPlaceContext(p, InteractionHand.MAIN_HAND, stack, hit), controller, blocked);
        int n = 0;
        for (boolean b : blocked) if (b) n++;
        if (n != 1 || !blocked[indexOf(0, 1, -2)]) ctx.fail("the preview must mark exactly the blocked cell, marked " + n);
        ctx.succeed();
    }

    private static BlockPos placeNorth(GameTestHelper ctx, StagecoachWood wood) {
        return placeNorth(ctx, wood, player(ctx, "placer"));
    }

    private static BlockPos placeNorth(GameTestHelper ctx, StagecoachWood wood, ServerPlayer placer) {
        floor(ctx);
        BlockPos c = ctx.absolutePos(CONTROLLER);
        place(ctx.getLevel(), placer, new ItemStack(item(wood), 1), c, Direction.NORTH);
        expectIntact(ctx, c, Direction.NORTH, wood);
        return c;
    }

    /** The survival path: the player faces {@code facing} and right-clicks the block below c. */
    private static void place(ServerLevel level, ServerPlayer player, ItemStack stack, BlockPos c, Direction facing) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Vec3 stand = Vec3.atBottomCenterOf(c).add(Vec3.atLowerCornerOf(facing.getOpposite().getUnitVec3i()).scale(6));
        player.snapTo(stand.x, stand.y, stand.z, facing.toYRot(), 0);
        BlockPos ground = c.below();
        player.gameMode.useItemOn(player, level, stack, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(ground).add(0, 0.5, 0), Direction.UP, ground, false));
    }

    /** A fresh survival fake player each time, so mode and held items never leak between tests. */
    private static ServerPlayer player(GameTestHelper ctx, String name) {
        return net.feelix.stagecoach.platform.Platform.get().fakePlayer(ctx.getLevel(), new GameProfile(UUID.randomUUID(), ("sc-" + name).substring(0, Math.min(16, 3 + name.length()))));
    }

    private static Zombie probe(ServerLevel level, Vec3 at) {
        Zombie probe = EntityTypes.ZOMBIE.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        probe.snapTo(at.x, at.y, at.z, 0, 0);
        probe.setOnGround(true);
        return probe;
    }

    private static void floor(GameTestHelper ctx) {
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) ctx.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
    }

    private static Item item(StagecoachWood wood) {
        return StagecoachRegistry.ITEMS.get(wood);
    }

    /** Fails loudly if the model no longer has this NORTH-frame cell. */
    private static int indexOf(int dx, int dy, int dz) {
        for (int i = 0; i < Footprint.CELL_COUNT; i++) {
            int[] cell = StagecoachFootprintData.CELLS[i];
            if (cell[0] == dx && cell[1] == dy && cell[2] == dz) return i;
        }
        throw new IllegalStateException("no footprint cell at " + dx + "," + dy + "," + dz);
    }

    private static void expectIntact(GameTestHelper ctx, BlockPos c, Direction facing, StagecoachWood wood) {
        ServerLevel level = ctx.getLevel();
        BlockState s = level.getBlockState(c);
        if (!(s.getBlock() instanceof StagecoachBlock b) || b.wood() != wood || s.getValue(StagecoachBlock.FACING) != facing) {
            ctx.fail("expected a " + wood + " controller facing " + facing + " at " + c + ", found " + s);
            return;
        }
        for (int i = 1; i < Footprint.CELL_COUNT; i++) {
            BlockState p = level.getBlockState(StagecoachShapes.cellPos(c, facing, i));
            if (!p.is(StagecoachRegistry.PART) || p.getValue(StagecoachPartBlock.PART) != i
                    || p.getValue(StagecoachPartBlock.FACING) != facing || p.getValue(StagecoachPartBlock.WOOD) != wood) {
                ctx.fail("cell " + i + " (facing " + facing + ") is wrong: " + p);
            }
        }
    }

    private static void expectGone(GameTestHelper ctx, BlockPos c, Direction facing) {
        for (int i = 0; i < Footprint.CELL_COUNT; i++) {
            BlockState s = ctx.getLevel().getBlockState(StagecoachShapes.cellPos(c, facing, i));
            if (s.getBlock() instanceof StagecoachBlock || s.is(StagecoachRegistry.PART)) {
                ctx.fail("cell " + i + " was left behind: " + s);
            }
        }
    }

    private static void expectItems(GameTestHelper ctx, BlockPos c, StagecoachWood wood, int expected) {
        int found = ctx.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(c).inflate(8), e -> e.getItem().is(item(wood)))
                .stream().mapToInt(e -> e.getItem().getCount()).sum();
        if (found != expected) ctx.fail("expected " + expected + " " + wood + " stagecoach item(s), found " + found);
    }
}
