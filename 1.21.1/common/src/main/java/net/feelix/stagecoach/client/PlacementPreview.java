package net.feelix.stagecoach.client;

import net.feelix.stagecoach.StagecoachItem;
import net.feelix.stagecoach.block.StagecoachBlock;
import net.feelix.stagecoach.block.StagecoachBlockEntity;
import net.feelix.stagecoach.block.StagecoachStructure;
import net.feelix.stagecoach.config.ClientConfig;
import net.feelix.stagecoach.footprint.Footprint;
import net.feelix.stagecoach.footprint.StagecoachShapes;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.debug.DebugRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** Claims are server-only, so a green ghost can still be refused there. */
public final class PlacementPreview {
    private PlacementPreview() {}

    private static final boolean[] BLOCKED = new boolean[Footprint.CELL_COUNT];
    private static GhostRenderer ghostRenderer;
    /** Replaced only when the spot, facing or wood changes. */
    private static StagecoachBlockEntity ghost;

    private static boolean active, valid;
    private static BlockPos origin = BlockPos.ORIGIN;
    private static Direction facing = Direction.NORTH;

    // each loader calls render once a frame, after the world's block entities

    /** Once a tick, not a frame: the rule checks 45 cells and their entities. */
    public static void update(MinecraftClient client) {
        active = false;
        if (!ClientConfig.get().showPlacementPreview) return;
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || player.isSpectator()) return;
        Hand hand = player.getMainHandStack().getItem() instanceof StagecoachItem ? Hand.MAIN_HAND
                : player.getOffHandStack().getItem() instanceof StagecoachItem ? Hand.OFF_HAND : null;
        if (hand == null || !(client.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        ItemStack stack = player.getStackInHand(hand);
        StagecoachItem item = (StagecoachItem) stack.getItem();
        ItemPlacementContext ctx = item.getPlacementContext(new ItemPlacementContext(player, hand, stack, hit));
        if (ctx == null) return;
        facing = ctx.getHorizontalPlayerFacing();
        BlockState controller = item.getBlock().getDefaultState().with(StagecoachBlock.FACING, facing);
        boolean fits = StagecoachStructure.check(ctx, controller, BLOCKED) == null;
        if (!ctx.canPlace()) BLOCKED[Footprint.CONTROLLER] = true;
        valid = fits && ctx.canPlace();
        BlockPos pos = ctx.getBlockPos();
        if (ghost == null || !ghost.getPos().equals(pos) || ghost.getCachedState() != controller) {
            ghost = new StagecoachBlockEntity(pos.toImmutable(), controller);
        }
        origin = ghost.getPos();
        active = true;
    }

    /** {@code matrices} is the world's stack; {@code cam} the camera position. */
    public static void render(MatrixStack matrices, Vec3d cam, float tickDelta, VertexConsumerProvider consumers) {
        if (!active || consumers == null) return;
        if (ghostRenderer == null) ghostRenderer = new GhostRenderer();
        matrices.push();
        matrices.translate(origin.getX() - cam.x, origin.getY() - cam.y, origin.getZ() - cam.z);
        ghostRenderer.tint = valid ? GhostRenderer.FITS : GhostRenderer.BLOCKED;
        ghostRenderer.render(ghost, tickDelta, matrices, consumers, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
        matrices.pop();
        if (!valid) {
            for (int i = 0; i < Footprint.CELL_COUNT; i++) {
                if (BLOCKED[i]) {
                    DebugRenderer.drawBox(matrices, consumers, StagecoachShapes.cellPos(origin, facing, i), 0.002f, 1.0f, 0.15f, 0.15f, 0.3f);
                }
            }
        }
    }
}
