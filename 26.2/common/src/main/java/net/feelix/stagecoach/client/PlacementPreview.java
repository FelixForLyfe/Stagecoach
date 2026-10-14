package net.feelix.stagecoach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.feelix.stagecoach.StagecoachItem;
import net.feelix.stagecoach.block.StagecoachBlock;
import net.feelix.stagecoach.block.StagecoachBlockEntity;
import net.feelix.stagecoach.block.StagecoachStructure;
import net.feelix.stagecoach.config.ClientConfig;
import net.feelix.stagecoach.footprint.Footprint;
import net.feelix.stagecoach.footprint.StagecoachShapes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class PlacementPreview {
    private PlacementPreview() {}

    private static final boolean[] BLOCKED = new boolean[Footprint.CELL_COUNT];
    private static GhostRenderer ghostRenderer;
    private static StagecoachRenderState ghostState;
    /** Replaced only when the spot, facing or wood changes. */
    private static StagecoachBlockEntity ghost;

    private static boolean active, valid;
    private static BlockPos origin = BlockPos.ZERO;
    private static Direction facing = Direction.NORTH;

    // each loader calls update every client tick and submit once a frame

    /** Once a tick, not a frame: the rule checks 45 cells and their entities. */
    public static void update(Minecraft client) {
        active = false;
        if (!ClientConfig.get().showPlacementPreview) return;
        LocalPlayer player = client.player;
        if (player == null || client.level == null || player.isSpectator()) return;
        InteractionHand hand = player.getMainHandItem().getItem() instanceof StagecoachItem ? InteractionHand.MAIN_HAND
                : player.getOffhandItem().getItem() instanceof StagecoachItem ? InteractionHand.OFF_HAND : null;
        if (hand == null || !(client.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        ItemStack stack = player.getItemInHand(hand);
        StagecoachItem item = (StagecoachItem) stack.getItem();
        BlockPlaceContext ctx = item.updatePlacementContext(new BlockPlaceContext(player, hand, stack, hit));
        if (ctx == null) return;
        facing = ctx.getHorizontalDirection();
        BlockState controller = item.getBlock().defaultBlockState().setValue(StagecoachBlock.FACING, facing);
        boolean fits = StagecoachStructure.check(ctx, controller, BLOCKED) == null;
        if (!ctx.canPlace()) BLOCKED[Footprint.CONTROLLER] = true;
        valid = fits && ctx.canPlace();
        BlockPos pos = ctx.getClickedPos();
        if (ghost == null || !ghost.getBlockPos().equals(pos) || ghost.getBlockState() != controller) {
            ghost = new StagecoachBlockEntity(pos.immutable(), controller);
        }
        origin = ghost.getBlockPos();
        active = true;
    }

    /** The block-entity renderer factory's context, so the ghost renderer can be built from it. */
    private static BlockEntityRendererProvider.Context rendererContext;

    public static void setRendererContext(BlockEntityRendererProvider.Context context) {
        rendererContext = context;
        ghostRenderer = null;
    }

    /** Submitted like any block entity, camera-relative; the blocked cells go in as custom quads. */
    public static void submit(PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, float partialTick) {
        Minecraft client = Minecraft.getInstance();
        if (!active || rendererContext == null || client.level == null) return;
        if (ghostRenderer == null) ghostRenderer = new GhostRenderer(rendererContext);
        if (ghost.getLevel() != client.level) ghost.setLevel(client.level);
        Vec3 cam = camera.pos;

        ghostRenderer.tint = valid ? GhostRenderer.FITS : GhostRenderer.BLOCKED;
        if (ghostState == null) ghostState = ghostRenderer.createRenderState();
        StagecoachRenderState state = ghostState;
        ghostRenderer.extractRenderState(ghost, state, partialTick, cam, null);
        state.lightCoords = 0xF000F0; // full bright, as vanilla uses for a block entity with no level
        poseStack.pushPose();
        poseStack.translate(origin.getX() - cam.x, origin.getY() - cam.y, origin.getZ() - cam.z);
        ghostRenderer.submit(state, poseStack, collector, camera);
        poseStack.popPose();

        if (valid) return;
        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, buffer) -> {
            for (int i = 0; i < Footprint.CELL_COUNT; i++) {
                if (BLOCKED[i]) box(pose, buffer, StagecoachShapes.cellPos(origin, facing, i));
            }
        });
        poseStack.popPose();
    }

    /** 0.002 larger than the block so it never z-fights with it. */
    private static void box(PoseStack.Pose pose, VertexConsumer b, BlockPos p) {
        float e = 0.002f;
        float x0 = p.getX() - e, y0 = p.getY() - e, z0 = p.getZ() - e, x1 = p.getX() + 1 + e, y1 = p.getY() + 1 + e, z1 = p.getZ() + 1 + e;
        float[][] faces = {
                {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1}, {x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0},
                {x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0}, {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1},
                {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0}, {x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1}};
        for (float[] f : faces) {
            for (int v = 0; v < 12; v += 3) b.addVertex(pose, f[v], f[v + 1], f[v + 2]).setColor(1.0f, 0.15f, 0.15f, 0.3f);
        }
    }
}
