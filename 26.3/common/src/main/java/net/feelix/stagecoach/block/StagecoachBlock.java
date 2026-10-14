package net.feelix.stagecoach.block;

import net.feelix.stagecoach.StagecoachWood;
import net.feelix.stagecoach.config.StagecoachConfig;
import net.feelix.stagecoach.footprint.Footprint;
import net.feelix.stagecoach.footprint.StagecoachShapes;
import net.feelix.stagecoach.travel.StagecoachService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class StagecoachBlock extends BaseEntityBlock {
    /** The way the coach's front points: away from the player who placed it. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    private final StagecoachWood wood;

    public StagecoachBlock(StagecoachWood wood, Properties settings) {
        super(settings);
        this.wood = wood;
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH));
    }

    public StagecoachWood wood() {
        return wood;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Null refuses the placement and keeps the item; the server says why. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState state = defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
        Component refusal = null;
        if (ctx.getLevel() instanceof ServerLevel level) refusal = StagecoachService.placementRefusal(level, ctx.getPlayer(), ctx.getClickedPos(), state);
        if (refusal == null) {
            StagecoachStructure.Refusal blocked = StagecoachStructure.check(ctx, state, null);
            if (blocked == null) return state;
            refusal = blocked.message();
        }
        if (!ctx.getLevel().isClientSide() && ctx.getPlayer() != null) ctx.getPlayer().sendOverlayMessage(refusal.copy().withStyle(ChatFormatting.RED));
        return null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverWorld) {
            StagecoachStructure.placeParts(level, pos, state);
            StagecoachService.onPlaced(serverWorld, pos, state, placer instanceof ServerPlayer p ? p : null);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel serverWorld && player instanceof ServerPlayer p) StagecoachService.onUse(serverWorld, pos, p);
        return InteractionResult.SUCCESS;
    }

    /** Admin and structure coaches were never crafted, so they give nothing back. */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof StagecoachBlockEntity be && be.dropsNothing()) {
            return List.of();
        }
        return super.getDrops(state, builder);
    }

    /** Blast-proof while only owners may break coaches. */
    @Override
    public float getExplosionResistance() {
        return StagecoachConfig.get().onlyOwnerCanBreak ? 3_600_000f : super.getExplosionResistance();
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE; // the block entity draws the whole coach
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StagecoachBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return StagecoachShapes.shape(state.getValue(FACING), Footprint.CONTROLLER);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return StagecoachShapes.shape(state.getValue(FACING), Footprint.CONTROLLER);
    }

    /** The ground under the coach must always render. */
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

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
