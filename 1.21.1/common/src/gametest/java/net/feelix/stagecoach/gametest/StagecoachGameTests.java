package net.feelix.stagecoach.gametest;

import com.mojang.authlib.GameProfile;
import net.feelix.stagecoach.StagecoachRegistry;
import net.feelix.stagecoach.StagecoachWood;
import net.feelix.stagecoach.block.StagecoachBlock;
import net.feelix.stagecoach.block.StagecoachPartBlock;
import net.feelix.stagecoach.block.StagecoachStructure;
import net.feelix.stagecoach.config.StagecoachConfig;
import net.feelix.stagecoach.footprint.Footprint;
import net.feelix.stagecoach.footprint.StagecoachFootprintData;
import net.feelix.stagecoach.footprint.StagecoachShapes;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PistonBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.LightType;
import net.minecraft.world.World;

import java.util.UUID;

public class StagecoachGameTests {
    static final String EMPTY_STRUCTURE = "stagecoach:gametest/empty";

    /** Every facing's footprint stays inside the 8x8x8 structure from here. */
    private static final BlockPos CONTROLLER = new BlockPos(4, 1, 4);

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void placesAndCollidesFacingNorth(TestContext ctx) {
        placesAndCollides(ctx, Direction.NORTH, StagecoachWood.OAK);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void placesAndCollidesFacingEast(TestContext ctx) {
        placesAndCollides(ctx, Direction.EAST, StagecoachWood.SPRUCE);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void placesAndCollidesFacingSouth(TestContext ctx) {
        placesAndCollides(ctx, Direction.SOUTH, StagecoachWood.DARK_OAK);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void placesAndCollidesFacingWest(TestContext ctx) {
        placesAndCollides(ctx, Direction.WEST, StagecoachWood.WARPED);
    }

    private void placesAndCollides(TestContext ctx, Direction facing, StagecoachWood wood) {
        ServerWorld world = ctx.getWorld();
        floor(ctx);
        BlockPos c = ctx.getAbsolutePos(CONTROLLER);
        ItemStack stack = new ItemStack(item(wood), 2);
        place(world, player(ctx, "place-" + facing), stack, c, facing);

        expectIntact(ctx, c, facing, wood);
        if (stack.getCount() != 1) ctx.throwGameTestException("placement should use exactly one item, left " + stack.getCount());

        // a cell must never cull a neighbour's face or dim light like a full cube
        for (int i = 0; i < Footprint.CELL_COUNT; i++) {
            BlockPos p = StagecoachShapes.cellPos(c, facing, i);
            BlockState s = world.getBlockState(p);
            if (s.isOpaque() || !s.getCullingShape(world, p).isEmpty() || s.getOpacity(world, p) != 0 || s.isSolidBlock(world, p)) {
                ctx.throwGameTestException("cell " + i + " culls or blocks light like a full cube");
            }
        }

        expectSolidFromEverySide(ctx, c, facing);
        expectStandableTop(ctx, c, facing);

        // lighting needs a moment to settle before the sky light under the coach matches open air
        ctx.waitAndRun(20, () -> {
            int under = world.getLightLevel(LightType.SKY, c);
            int open = world.getLightLevel(LightType.SKY, ctx.getAbsolutePos(new BlockPos(0, 1, 0)));
            if (under != open) ctx.throwGameTestException("the coach blocks sky light: " + under + " under it vs " + open + " in the open");
            ctx.complete();
        });
    }

    /** The front walk must stop at the shaft tip (2.81 blocks out): proof the shaft is solid. */
    private static void expectSolidFromEverySide(TestContext ctx, BlockPos c, Direction facing) {
        ServerWorld world = ctx.getWorld();
        Vec3d centre = Vec3d.ofBottomCenter(c);
        Box column = new Box(c.getX(), c.getY(), c.getZ(), c.getX() + 1, c.getY() + 3, c.getZ() + 1);
        for (Direction d : Direction.Type.HORIZONTAL) {
            Vec3d out = Vec3d.of(d.getVector());
            Vec3d side = Vec3d.of(d.rotateYClockwise().getVector());
            for (double lateral : new double[] {-0.35, 0, 0.35}) {
                ZombieEntity probe = probe(world, centre.add(out.multiply(3.6)).add(side.multiply(lateral)));
                probe.move(MovementType.SELF, out.multiply(-3.6));
                String where = "facing " + facing + ", walking in from " + d + " at lateral " + lateral;
                if (probe.getBoundingBox().intersects(column)) ctx.throwGameTestException("walked through the coach: " + where);
                if (!world.isSpaceEmpty(probe, probe.getBoundingBox().contract(1.0e-3))) ctx.throwGameTestException("ended inside collision: " + where);
                double dist = probe.getPos().subtract(centre).dotProduct(out);
                if (d == facing && lateral == 0 && dist < 3.0) ctx.throwGameTestException("the shaft is not solid: stopped " + dist + " out, " + where);
                probe.discard();
            }
        }
    }

    /** The probe sits on the rotation axis, so the top it lands on is the same in every facing. */
    private static void expectStandableTop(TestContext ctx, BlockPos c, Direction facing) {
        ServerWorld world = ctx.getWorld();
        double half = EntityType.ZOMBIE.getWidth() / 2 * 16; // pixels
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
            ZombieEntity probe = probe(world, Vec3d.ofBottomCenter(c).add(0, 5, 0));
            probe.move(MovementType.SELF, new Vec3d(0, -6, 0));
            double landed = probe.getY() - c.getY();
            if (Math.abs(landed - expected) > 1.0e-6) {
                ctx.throwGameTestException("landed at +" + landed + " on the coach (facing " + facing + "), collision data says +" + expected);
            }
            probe.setOnGround(true);
            probe.move(MovementType.SELF, Vec3d.of(facing.getVector()).multiply(along));
            probe.move(MovementType.SELF, new Vec3d(0, -1, 0));
            double after = probe.getY() - c.getY();
            if (after < 1.0) ctx.throwGameTestException("dropped through the coach walking " + along + " along it (facing " + facing + "): +" + after);
            probe.discard();
        }
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void refusedAgainstAWall(TestContext ctx) {
        floor(ctx);
        BlockPos c = ctx.getAbsolutePos(CONTROLLER);
        ctx.getWorld().setBlockState(StagecoachShapes.cellPos(c, Direction.NORTH, indexOf(0, 1, -2)), Blocks.STONE.getDefaultState());
        expectRefused(ctx, c, Direction.NORTH, c.down());
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void refusedWhenAnEntityIsInTheWay(TestContext ctx) {
        floor(ctx);
        BlockPos c = ctx.getAbsolutePos(CONTROLLER);
        // world coordinates on purpose: 1.20.1's getRelativePos turns 180 degrees even in unrotated tests
        BlockPos cell = StagecoachShapes.cellPos(c, Direction.NORTH, indexOf(0, 0, -2));
        PigEntity pig = EntityType.PIG.create(ctx.getWorld());
        pig.refreshPositionAndAngles(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5, 0, 0);
        pig.setAiDisabled(true);
        ctx.getWorld().spawnEntity(pig);
        expectRefused(ctx, c, Direction.NORTH, c.down());
        pig.discard();
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void refusedAtTheBuildLimit(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos anchor = ctx.getAbsolutePos(CONTROLLER);
        // parts would be at topY and above
        BlockPos c = new BlockPos(anchor.getX(), world.getTopY() - 1, anchor.getZ());
        world.setBlockState(c.down(), Blocks.STONE.getDefaultState());
        try {
            expectRefused(ctx, c, Direction.NORTH, c.down());
        } finally {
            world.setBlockState(c.down(), Blocks.AIR.getDefaultState());
        }
        ctx.complete();
    }

    private static void expectRefused(TestContext ctx, BlockPos c, Direction facing, BlockPos ground) {
        ServerWorld world = ctx.getWorld();
        ItemStack stack = new ItemStack(item(StagecoachWood.BIRCH), 1);
        place(world, player(ctx, "refused"), stack, c, facing);
        if (stack.getCount() != 1) ctx.throwGameTestException("a refused placement used the item");
        for (int i = 0; i < Footprint.CELL_COUNT; i++) {
            BlockState s = world.getBlockState(StagecoachShapes.cellPos(c, facing, i));
            if (s.getBlock() instanceof StagecoachBlock || s.isOf(StagecoachRegistry.PART)) {
                ctx.throwGameTestException("a refused placement left cell " + i + " behind");
            }
        }
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void survivalBreakingAMiddlePartDropsOneItem(TestContext ctx) {
        ServerPlayerEntity breaker = player(ctx, "survival-breaker");
        BlockPos c = placeNorth(ctx, StagecoachWood.JUNGLE, breaker);
        breaker.interactionManager.tryBreakBlock(StagecoachShapes.cellPos(c, Direction.NORTH, indexOf(0, 1, 0)));
        expectGone(ctx, c, Direction.NORTH);
        expectItems(ctx, c, StagecoachWood.JUNGLE, 1);
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void survivalBreakingTheControllerDropsOneItem(TestContext ctx) {
        ServerPlayerEntity owner = player(ctx, "controller-breaker");
        BlockPos c = placeNorth(ctx, StagecoachWood.ACACIA, owner);
        owner.interactionManager.tryBreakBlock(c);
        expectGone(ctx, c, Direction.NORTH);
        expectItems(ctx, c, StagecoachWood.ACACIA, 1);
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void creativeBreakingDropsNothing(TestContext ctx) {
        ServerPlayerEntity breaker = player(ctx, "creative-breaker");
        BlockPos c = placeNorth(ctx, StagecoachWood.MANGROVE, breaker);
        breaker.changeGameMode(GameMode.CREATIVE);
        breaker.interactionManager.tryBreakBlock(StagecoachShapes.cellPos(c, Direction.NORTH, indexOf(0, 2, -1)));
        expectGone(ctx, c, Direction.NORTH);
        expectItems(ctx, c, StagecoachWood.MANGROVE, 0);
        ctx.complete();
    }

    /** Needs onlyOwnerCanBreak off: protected coaches are blast-proof. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void anExplosionRemovesEverythingAndDropsOneItem(TestContext ctx) {
        BlockPos c = placeNorth(ctx, StagecoachWood.CRIMSON);
        Vec3d at = Vec3d.ofCenter(StagecoachShapes.cellPos(c, Direction.NORTH, indexOf(0, 1, 1)));
        // synchronous until the finally, so switching the global config is safe
        StagecoachConfig before = StagecoachConfig.get();
        StagecoachConfig.set(StagecoachConfig.parse("{\"onlyOwnerCanBreak\": false}", new java.util.ArrayList<>()));
        try {
            ctx.getWorld().createExplosion(null, at.x, at.y, at.z, 4.0f, World.ExplosionSourceType.TNT);
        } finally {
            StagecoachConfig.set(before);
        }
        expectGone(ctx, c, Direction.NORTH);
        expectItems(ctx, c, StagecoachWood.CRIMSON, 1);
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void explosionsCannotBreakAProtectedCoach(TestContext ctx) {
        BlockPos c = placeNorth(ctx, StagecoachWood.WARPED);
        Vec3d at = Vec3d.ofCenter(StagecoachShapes.cellPos(c, Direction.NORTH, indexOf(0, 1, 1)));
        ctx.getWorld().createExplosion(null, at.x, at.y, at.z, 4.0f, World.ExplosionSourceType.TNT);
        expectIntact(ctx, c, Direction.NORTH, StagecoachWood.WARPED);
        expectItems(ctx, c, StagecoachWood.WARPED, 0);
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void pistonsCannotMoveIt(TestContext ctx) {
        BlockPos c = placeNorth(ctx, StagecoachWood.BAMBOO);
        ServerWorld world = ctx.getWorld();
        BlockPos part = StagecoachShapes.cellPos(c, Direction.NORTH, indexOf(1, 1, 0)); // east side of the body
        BlockPos piston = part.east();
        world.setBlockState(piston, Blocks.PISTON.getDefaultState().with(PistonBlock.FACING, Direction.WEST));
        world.setBlockState(piston.east(), Blocks.REDSTONE_BLOCK.getDefaultState());
        ctx.waitAndRun(4, () -> {
            if (world.getBlockState(piston).get(PistonBlock.EXTENDED)) ctx.throwGameTestException("the piston extended into the coach");
            expectIntact(ctx, c, Direction.NORTH, StagecoachWood.BAMBOO);
            ctx.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void anOrphanedPartRemovesItselfWithoutDropping(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos c = ctx.getAbsolutePos(CONTROLLER);
        BlockState controller = StagecoachRegistry.BLOCKS.get(StagecoachWood.CHERRY).getDefaultState();
        BlockPos p = StagecoachShapes.cellPos(c, Direction.NORTH, indexOf(0, 1, 0));
        BlockState part = StagecoachStructure.partState(controller, indexOf(0, 1, 0));
        world.setBlockState(p, part); // no controller at c
        world.getBlockState(p).randomTick(world, p, world.getRandom());
        if (!world.getBlockState(p).isAir()) ctx.throwGameTestException("orphaned part survived its random tick");
        expectItems(ctx, c, StagecoachWood.CHERRY, 0);
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void everyWoodCraftsItsOwnStagecoach(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        for (StagecoachWood wood : StagecoachWood.values()) {
            net.minecraft.item.Item planks = wood.planks().asItem();
            net.minecraft.item.Item[] pattern = {
                    planks, net.minecraft.item.Items.CHEST, planks,
                    net.minecraft.item.Items.LEAD, net.minecraft.item.Items.DIAMOND_BLOCK, net.minecraft.item.Items.LEAD,
                    net.minecraft.item.Items.DIAMOND_BLOCK, net.minecraft.item.Items.GOLD_BLOCK, net.minecraft.item.Items.DIAMOND_BLOCK};
            var grid = net.minecraft.recipe.input.CraftingRecipeInput.create(3, 3,
                    java.util.Arrays.stream(pattern).map(ItemStack::new).toList());
            ItemStack out = world.getRecipeManager().getFirstMatch(net.minecraft.recipe.RecipeType.CRAFTING, grid, world)
                    .map(r -> r.value().craft(grid, world.getRegistryManager())).orElse(ItemStack.EMPTY);
            if (!out.isOf(item(wood)) || out.getCount() != 1) ctx.throwGameTestException(wood + " planks crafted " + out);
        }
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aRefusalSaysWhatIsInTheWay(TestContext ctx) {
        floor(ctx);
        ServerWorld world = ctx.getWorld();
        BlockPos c = ctx.getAbsolutePos(CONTROLLER);
        ServerPlayerEntity p = player(ctx, "why");
        p.refreshPositionAndAngles(c.getX() + 0.5, c.getY(), c.getZ() + 8.5, Direction.NORTH.asRotation(), 0);
        ItemStack stack = new ItemStack(item(StagecoachWood.OAK));
        p.setStackInHand(Hand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(c.down()).add(0, 0.5, 0), Direction.UP, c.down(), false);
        BlockState controller = StagecoachRegistry.BLOCKS.get(StagecoachWood.OAK).getDefaultState().with(StagecoachBlock.FACING, Direction.NORTH);
        if (StagecoachStructure.check(new net.minecraft.item.ItemPlacementContext(p, Hand.MAIN_HAND, stack, hit), controller, null) != null) {
            ctx.throwGameTestException("an empty spot must be accepted");
        }
        BlockPos wall = StagecoachShapes.cellPos(c, Direction.NORTH, indexOf(0, 1, -2));
        world.setBlockState(wall, Blocks.BOOKSHELF.getDefaultState());
        StagecoachStructure.Refusal r = StagecoachStructure.check(new net.minecraft.item.ItemPlacementContext(p, Hand.MAIN_HAND, stack, hit), controller, null);
        if (r == null || r.reason() != StagecoachStructure.Reason.BLOCKED || !r.pos().equals(wall)
                || !r.what().getString().equals(Blocks.BOOKSHELF.getName().getString())) {
            ctx.throwGameTestException("expected BLOCKED by the bookshelf at " + wall + ", got " + r);
        }
        boolean[] blocked = new boolean[Footprint.CELL_COUNT];
        StagecoachStructure.check(new net.minecraft.item.ItemPlacementContext(p, Hand.MAIN_HAND, stack, hit), controller, blocked);
        int n = 0;
        for (boolean b : blocked) if (b) n++;
        if (n != 1 || !blocked[indexOf(0, 1, -2)]) ctx.throwGameTestException("the preview must mark exactly the blocked cell, marked " + n);
        ctx.complete();
    }

    private static BlockPos placeNorth(TestContext ctx, StagecoachWood wood) {
        return placeNorth(ctx, wood, player(ctx, "placer"));
    }

    private static BlockPos placeNorth(TestContext ctx, StagecoachWood wood, ServerPlayerEntity placer) {
        floor(ctx);
        BlockPos c = ctx.getAbsolutePos(CONTROLLER);
        place(ctx.getWorld(), placer, new ItemStack(item(wood), 1), c, Direction.NORTH);
        expectIntact(ctx, c, Direction.NORTH, wood);
        return c;
    }

    /** The survival path: the player faces {@code facing} and right-clicks the block below c. */
    private static void place(ServerWorld world, ServerPlayerEntity player, ItemStack stack, BlockPos c, Direction facing) {
        player.setStackInHand(Hand.MAIN_HAND, stack);
        Vec3d stand = Vec3d.ofBottomCenter(c).add(Vec3d.of(facing.getOpposite().getVector()).multiply(6));
        player.refreshPositionAndAngles(stand.x, stand.y, stand.z, facing.asRotation(), 0);
        BlockPos ground = c.down();
        player.interactionManager.interactBlock(player, world, stack, Hand.MAIN_HAND,
                new BlockHitResult(Vec3d.ofCenter(ground).add(0, 0.5, 0), Direction.UP, ground, false));
    }

    /** A fresh survival fake player each time, so mode and held items never leak between tests. */
    private static ServerPlayerEntity player(TestContext ctx, String name) {
        return net.feelix.stagecoach.platform.Platform.get().fakePlayer(ctx.getWorld(), new GameProfile(UUID.randomUUID(), ("sc-" + name).substring(0, Math.min(16, 3 + name.length()))));
    }

    private static ZombieEntity probe(ServerWorld world, Vec3d at) {
        ZombieEntity probe = EntityType.ZOMBIE.create(world);
        probe.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
        probe.setOnGround(true);
        return probe;
    }

    private static void floor(TestContext ctx) {
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) ctx.setBlockState(new BlockPos(x, 0, z), Blocks.STONE);
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

    private static void expectIntact(TestContext ctx, BlockPos c, Direction facing, StagecoachWood wood) {
        ServerWorld world = ctx.getWorld();
        BlockState s = world.getBlockState(c);
        if (!(s.getBlock() instanceof StagecoachBlock b) || b.wood() != wood || s.get(StagecoachBlock.FACING) != facing) {
            ctx.throwGameTestException("expected a " + wood + " controller facing " + facing + " at " + c + ", found " + s);
            return;
        }
        for (int i = 1; i < Footprint.CELL_COUNT; i++) {
            BlockState p = world.getBlockState(StagecoachShapes.cellPos(c, facing, i));
            if (!p.isOf(StagecoachRegistry.PART) || p.get(StagecoachPartBlock.PART) != i
                    || p.get(StagecoachPartBlock.FACING) != facing || p.get(StagecoachPartBlock.WOOD) != wood) {
                ctx.throwGameTestException("cell " + i + " (facing " + facing + ") is wrong: " + p);
            }
        }
    }

    private static void expectGone(TestContext ctx, BlockPos c, Direction facing) {
        for (int i = 0; i < Footprint.CELL_COUNT; i++) {
            BlockState s = ctx.getWorld().getBlockState(StagecoachShapes.cellPos(c, facing, i));
            if (s.getBlock() instanceof StagecoachBlock || s.isOf(StagecoachRegistry.PART)) {
                ctx.throwGameTestException("cell " + i + " was left behind: " + s);
            }
        }
    }

    private static void expectItems(TestContext ctx, BlockPos c, StagecoachWood wood, int expected) {
        int found = ctx.getWorld().getEntitiesByClass(ItemEntity.class, new Box(c).expand(8), e -> e.getStack().isOf(item(wood)))
                .stream().mapToInt(e -> e.getStack().getCount()).sum();
        if (found != expected) ctx.throwGameTestException("expected " + expected + " " + wood + " stagecoach item(s), found " + found);
    }
}
