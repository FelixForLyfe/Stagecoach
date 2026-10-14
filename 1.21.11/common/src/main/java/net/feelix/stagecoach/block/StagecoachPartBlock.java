package net.feelix.stagecoach.block;

import net.feelix.stagecoach.StagecoachRegistry;
import net.feelix.stagecoach.StagecoachWood;
import net.feelix.stagecoach.config.StagecoachConfig;
import net.feelix.stagecoach.footprint.Footprint;
import net.feelix.stagecoach.footprint.StagecoachShapes;
import net.feelix.stagecoach.travel.StagecoachService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** No block entity: PART, FACING and WOOD say everything; WOOD only feeds pick-block and particles. */
public class StagecoachPartBlock extends Block {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<StagecoachWood> WOOD = EnumProperty.create("wood", StagecoachWood.class);
    public static final IntegerProperty PART = IntegerProperty.create("part", 1, Footprint.CELL_COUNT - 1);

    public StagecoachPartBlock(Properties settings) {
        super(settings);
        registerDefaultState(getStateDefinition().any()
                .setValue(FACING, Direction.NORTH).setValue(WOOD, StagecoachWood.OAK).setValue(PART, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WOOD, PART);
    }

    /** Breaks the controller instead, so vanilla's own removeBlock finds air and drops nothing more. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockState broken = super.playerWillDestroy(level, pos, state, player);
        if (!level.isClientSide() && StagecoachStructure.hasController(level, pos, state)) {
            level.destroyBlock(StagecoachStructure.controllerPos(pos, state), !player.isCreative(), player);
        }
        return broken;
    }

    /** Any other loss takes the controller with it; a no-op when the controller is clearing the parts. */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        // runs after this cell is gone; the controller's clean-up never lands here (no neighbour updates)
        if (StagecoachStructure.hasController(level, pos, state)) {
            level.destroyBlock(StagecoachStructure.controllerPos(pos, state), true);
        }
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel serverWorld && player instanceof ServerPlayer p
                && StagecoachStructure.hasController(level, pos, state)) {
            StagecoachService.onUse(serverWorld, StagecoachStructure.controllerPos(pos, state), p);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public float getExplosionResistance() {
        return StagecoachConfig.get().onlyOwnerCanBreak ? 3_600_000f : super.getExplosionResistance();
    }

    /** A part whose controller is gone (an external edit) removes itself. */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos controller = StagecoachStructure.controllerPos(pos, state);
        if (level.hasChunkAt(controller) && !StagecoachStructure.hasController(level, pos, state)) {
            level.removeBlock(pos, false);
        }
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(StagecoachRegistry.BLOCKS.get(state.getValue(WOOD)));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return StagecoachShapes.shape(state.getValue(FACING), state.getValue(PART));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return StagecoachShapes.shape(state.getValue(FACING), state.getValue(PART));
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state) {
        return Shapes.empty();
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0f;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return true;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    /** Index and facing no longer line up after a mirror; the controller lays its parts again when it registers. */
    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
