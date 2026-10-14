package net.feelix.stagecoach.block;

import net.feelix.stagecoach.StagecoachWood;
import net.feelix.stagecoach.config.StagecoachConfig;
import net.feelix.stagecoach.footprint.Footprint;
import net.feelix.stagecoach.footprint.StagecoachShapes;
import net.feelix.stagecoach.travel.StagecoachService;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class StagecoachBlock extends BlockWithEntity {
    /** The way the coach's front points: away from the player who placed it. */
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;

    private final StagecoachWood wood;

    public StagecoachBlock(StagecoachWood wood, Settings settings) {
        super(settings);
        this.wood = wood;
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
    }

    public StagecoachWood wood() {
        return wood;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Null refuses the placement and keeps the item; the server says why. */
    @Override
    public @Nullable BlockState getPlacementState(ItemPlacementContext ctx) {
        BlockState state = getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing());
        Text refusal = null;
        if (ctx.getWorld() instanceof ServerWorld world) refusal = StagecoachService.placementRefusal(world, ctx.getPlayer(), ctx.getBlockPos(), state);
        if (refusal == null) {
            StagecoachStructure.Refusal blocked = StagecoachStructure.check(ctx, state, null);
            if (blocked == null) return state;
            refusal = blocked.message();
        }
        if (!ctx.getWorld().isClient && ctx.getPlayer() != null) ctx.getPlayer().sendMessage(refusal.copy().formatted(Formatting.RED), true);
        return null;
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.onPlaced(world, pos, state, placer, stack);
        if (world instanceof ServerWorld serverWorld) {
            StagecoachStructure.placeParts(world, pos, state);
            StagecoachService.onPlaced(serverWorld, pos, state, placer instanceof ServerPlayerEntity p ? p : null);
        }
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world instanceof ServerWorld serverWorld && player instanceof ServerPlayerEntity p) StagecoachService.onUse(serverWorld, pos, p);
        return ActionResult.success(world.isClient);
    }

    /** However the controller goes, its parts go silently with it and the coach leaves the network. */
    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (world instanceof ServerWorld serverWorld && !state.isOf(newState.getBlock()) && !StagecoachStructure.isRebuilding()) {
            StagecoachStructure.removeParts(world, pos, state);
            if (world.getBlockEntity(pos) instanceof StagecoachBlockEntity be) {
                StagecoachService.onControllerRemoved(serverWorld, pos, be.getCoachId());
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    /** Admin and structure coaches were never crafted, so they give nothing back. */
    @Override
    public List<ItemStack> getDroppedStacks(BlockState state, LootContextParameterSet.Builder builder) {
        if (builder.getOptional(LootContextParameters.BLOCK_ENTITY) instanceof StagecoachBlockEntity be && be.dropsNothing()) {
            return List.of();
        }
        return super.getDroppedStacks(state, builder);
    }

    /** Blast-proof while only owners may break coaches. */
    @Override
    public float getBlastResistance() {
        return StagecoachConfig.get().onlyOwnerCanBreak ? 3_600_000f : super.getBlastResistance();
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new StagecoachBlockEntity(pos, state);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return StagecoachShapes.shape(state.get(FACING), Footprint.CONTROLLER);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return StagecoachShapes.shape(state.get(FACING), Footprint.CONTROLLER);
    }

    /** The ground under the coach must always render. */
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

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
    }
}
