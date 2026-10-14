package net.feelix.stagecoach.block;

import net.feelix.stagecoach.StagecoachRegistry;
import net.feelix.stagecoach.StagecoachWood;
import net.feelix.stagecoach.config.StagecoachConfig;
import net.feelix.stagecoach.footprint.Footprint;
import net.feelix.stagecoach.footprint.StagecoachShapes;
import net.feelix.stagecoach.travel.StagecoachService;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

/** No block entity: PART, FACING and WOOD say everything; WOOD only feeds pick-block and particles. */
public class StagecoachPartBlock extends Block {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final EnumProperty<StagecoachWood> WOOD = EnumProperty.of("wood", StagecoachWood.class);
    public static final IntProperty PART = IntProperty.of("part", 1, Footprint.CELL_COUNT - 1);

    public StagecoachPartBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState()
                .with(FACING, Direction.NORTH).with(WOOD, StagecoachWood.OAK).with(PART, 1));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, WOOD, PART);
    }

    /** Breaks the controller instead, so vanilla's own removeBlock finds air and drops nothing more. */
    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        BlockState broken = super.onBreak(world, pos, state, player);
        if (!world.isClient && StagecoachStructure.hasController(world, pos, state)) {
            world.breakBlock(StagecoachStructure.controllerPos(pos, state), !player.isCreative(), player);
        }
        return broken;
    }

    /** Any other loss takes the controller with it; a no-op when the controller is clearing the parts. */
    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!world.isClient && !state.isOf(newState.getBlock()) && StagecoachStructure.hasController(world, pos, state)) {
            world.breakBlock(StagecoachStructure.controllerPos(pos, state), true);
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world instanceof ServerWorld serverWorld && player instanceof ServerPlayerEntity p
                && StagecoachStructure.hasController(world, pos, state)) {
            StagecoachService.onUse(serverWorld, StagecoachStructure.controllerPos(pos, state), p);
        }
        return ActionResult.success(world.isClient);
    }

    @Override
    public float getBlastResistance() {
        return StagecoachConfig.get().onlyOwnerCanBreak ? 3_600_000f : super.getBlastResistance();
    }

    /** A part whose controller is gone (an external edit) removes itself. */
    @Override
    public void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        BlockPos controller = StagecoachStructure.controllerPos(pos, state);
        if (world.isChunkLoaded(controller) && !StagecoachStructure.hasController(world, pos, state)) {
            world.removeBlock(pos, false);
        }
    }

    @Override
    public ItemStack getPickStack(WorldView world, BlockPos pos, BlockState state) {
        return new ItemStack(StagecoachRegistry.BLOCKS.get(state.get(WOOD)));
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.INVISIBLE;
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return StagecoachShapes.shape(state.get(FACING), state.get(PART));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return StagecoachShapes.shape(state.get(FACING), state.get(PART));
    }

    @Override
    public VoxelShape getCullingShape(BlockState state, BlockView world, BlockPos pos) {
        return VoxelShapes.empty();
    }

    @Override
    public float getAmbientOcclusionLightLevel(BlockState state, BlockView world, BlockPos pos) {
        return 1.0f;
    }

    @Override
    public boolean isTransparent(BlockState state, BlockView world, BlockPos pos) {
        return true;
    }

    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    /** Index and facing no longer line up after a mirror; the controller lays its parts again when it registers. */
    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
    }
}
