package net.feelix.stagecoach.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.feelix.stagecoach.Stagecoach;
import net.feelix.stagecoach.client.ClientNet;
import net.feelix.stagecoach.client.scene.Scene;
import net.feelix.stagecoach.client.scene.SceneManager;
import net.feelix.stagecoach.client.scene.SceneRenderer;
import net.feelix.stagecoach.client.ui.RoutePlot;
import net.feelix.stagecoach.client.ui.TravelClock;
import net.feelix.stagecoach.client.ui.WoodUi;
import net.feelix.stagecoach.config.ClientConfig;
import net.feelix.stagecoach.net.Packets;
import net.feelix.stagecoach.travel.TravelMath;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.glfw.GLFW;

public class TravelScreen extends Screen {
    private static final Identifier MAP = Identifier.ofVanilla("textures/map/map_background.png");
    private static final Identifier ORIGIN = Identifier.ofVanilla("textures/map/decorations/blue_banner.png");
    private static final Identifier DESTINATION = Identifier.ofVanilla("textures/map/decorations/red_banner.png");
    private static final Identifier PLAYER = Identifier.ofVanilla("textures/map/decorations/player.png");
    private static final ItemStack CLOCK = new ItemStack(Items.CLOCK);
    private static final int INK = 0xFF4A3A28;
    private static final int INK_FAINT = 0x704A3A28;
    private static final int DOT_STEP = 5;
    /** Entities draw upside down in a GUI without this half-turn about Z, as in vanilla's drawEntity. */
    private static final Quaternionf UPRIGHT = new Quaternionf().rotateZ((float) Math.PI);

    private final Packets.JourneyStart journey;
    private final Scene scene;
    private final Text originLabel;
    private final TravelClock clock = new TravelClock();
    private final Matrix4f scaleMatrix = new Matrix4f();
    private Traveller traveller;
    private RoutePlot plot;
    private int mapX, mapY, mapSize;
    private float[] dotX = new float[0], dotZ = new float[0], dotAt = new float[0];
    private long shownSecond = -1;
    private float progress;
    private Text remaining = Text.empty();

    public TravelScreen(Packets.JourneyStart journey) {
        super(Stagecoach.text("screen.travelling", journey.destName()));
        this.journey = journey;
        this.scene = SceneManager.pick(journey, MinecraftClient.getInstance().world);
        this.originLabel = journey.originName().isEmpty() ? Stagecoach.text("screen.set_off_here") : Text.literal(journey.originName());
    }

    public Scene scene() {
        return scene;
    }

    @Override
    protected void init() {
        addDrawableChild(ButtonWidget.builder(Stagecoach.text("screen.cancel_journey"), b -> client.setScreen(new Abandon(this)))
                .dimensions(width / 2 - 55, height - 28, 110, 20).build());
        if (traveller == null && client.world != null && client.player != null) traveller = new Traveller(client.world, client.player);
        int room = Math.min(height - 112, width - 40);
        mapSize = Math.max(64, Math.min(320, room / 64 * 64)); // a whole multiple of the 64px parchment stays crisp
        mapX = (width - mapSize) / 2;
        mapY = 54 + Math.max(0, (height - 112 - mapSize) / 2);
        double bend = ((journey.seed() >>> 11) % 2001) / 1000.0 - 1.0;
        plot = new RoutePlot(journey.originX(), journey.originZ(), journey.destX(), journey.destZ(), mapSize, 0.2, bend);
        int dots = (int) (plot.length() / DOT_STEP) + 1;
        dotX = new float[dots];
        dotZ = new float[dots];
        dotAt = new float[dots];
        for (int i = 0; i < dots; i++) {
            float at = dots == 1 ? 1 : (float) i / (dots - 1);
            double t = plot.paramAt(at);
            dotX[i] = (float) plot.x(t);
            dotZ[i] = (float) plot.z(t);
            dotAt[i] = at;
        }
    }

    @Override
    public void tick() {
        if (traveller != null) {
            traveller.age++;
            traveller.limbAnimator.updateLimbs(0.85f, 0.4f); // a steady walk
        }
    }

    @Override
    public boolean shouldPause() {
        return false; // the journey runs on the server's clock; a paused integrated server would stop it
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            client.setScreen(new Abandon(this));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** The road replaces the vanilla blur; the map and minimal screens keep it. */
    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ClientWorld world = client.world;
        if (world == null) return;
        ClientConfig cfg = ClientConfig.get();
        double elapsed = clock.elapsed(System.nanoTime(), world.getTime() - journey.startTime() + delta);
        progress = (float) MathHelper.clamp(elapsed / journey.durationTicks(), 0, 1);
        if (cfg.roadScene()) {
            SceneRenderer.draw(ctx, width, height, scene, journey.seed(), (float) elapsed, world.getSkyAngle(delta), world.getMoonPhase());
            return;
        }
        super.renderBackground(ctx, mouseX, mouseY, delta);
        if (cfg.mapScreen()) drawMap(ctx, progress);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ClientWorld world = client.world;
        if (world == null) return;
        long left = Math.max(0, journey.startTime() + journey.durationTicks() - world.getTime());
        long second = (left + 19) / 20;
        if (second != shownSecond) {
            shownSecond = second;
            remaining = second == 0 ? Stagecoach.text("screen.arriving") : Stagecoach.text("screen.remaining", TravelMath.clock(left));
        }
        int signW = Math.max(160, Math.max(textRenderer.getWidth(title), textRenderer.getWidth(remaining)) + 44);
        int signX = (width - signW) / 2;
        WoodUi.plaque(ctx, signX, 16, signW, 30, true);
        WoodUi.icon(ctx, CLOCK, signX + 6, 23, 16);
        ctx.drawText(textRenderer, title, signX + 26 + (signW - 30 - textRenderer.getWidth(title)) / 2, 21, WoodUi.INK, false);
        ctx.drawText(textRenderer, remaining, signX + 26 + (signW - 30 - textRenderer.getWidth(remaining)) / 2, 33, 0xFF6B4A2B, false);

        int barW = Math.min(300, width - 60);
        int barX = (width - barW) / 2, barY = height - 44;
        int fillW = WoodUi.progressBar(ctx, barX, barY, barW, 10, progress);
        if (ClientConfig.get().roadScene() && scene.coach() && traveller != null) drawTraveller(ctx, barX + 3 + fillW, barY - 2, 20, delta);
    }

    /** Vanilla's drawEntity with the real partial tick, so the walk is smooth. */
    private void drawTraveller(DrawContext ctx, int x, int y, int size, float delta) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 50);
        ctx.getMatrices().multiplyPositionMatrix(scaleMatrix.scaling(size, size, -size));
        ctx.getMatrices().multiply(UPRIGHT);
        DiffuseLighting.method_34742(); // the entity-in-GUI lighting vanilla uses in the inventory
        EntityRenderDispatcher dispatcher = MinecraftClient.getInstance().getEntityRenderDispatcher();
        dispatcher.setRenderShadows(false);
        dispatcher.render(traveller, 0, 0, 0, 0, delta, ctx.getMatrices(), ctx.getVertexConsumers(), 0xF000F0);
        ctx.draw();
        dispatcher.setRenderShadows(true);
        ctx.getMatrices().pop();
        DiffuseLighting.enableGuiDepthLighting();
        RenderSystem.enableBlend();
    }

    private void drawMap(DrawContext ctx, float progress) {
        ctx.drawTexture(MAP, mapX, mapY, mapSize, mapSize, 0, 0, 64, 64, 64, 64);
        int[] stops = journey.stops();
        if (ClientConfig.get().showOtherStopsOnMap) {
            for (int i = 0; i + 1 < stops.length; i += 2) {
                double x = plot.px(stops[i]), z = plot.pz(stops[i + 1]);
                if (!plot.inside(x, z, 6)) continue;
                int sx = mapX + (int) x, sz = mapY + (int) z;
                ctx.fill(sx - 1, sz - 1, sx + 2, sz + 2, INK_FAINT);
            }
        }
        for (int i = 0; i < dotX.length; i++) {
            int sx = mapX + Math.round(dotX[i]), sz = mapY + Math.round(dotZ[i]);
            ctx.fill(sx, sz, sx + 2, sz + 2, dotAt[i] <= progress ? INK : INK_FAINT);
        }
        drawIcon(ctx, ORIGIN, plot.x(0), plot.z(0), 0);
        drawIcon(ctx, DESTINATION, plot.x(1), plot.z(1), 0);
        label(ctx, originLabel.getString(), plot.x(0), plot.z(0), true);
        label(ctx, journey.destName(), plot.x(1), plot.z(1), false);
        double t = plot.paramAt(progress);
        drawIcon(ctx, PLAYER, plot.x(t), plot.z(t), (float) plot.heading(t)); // the player's own map marker
    }

    /** One 8px map decoration, drawn 16px wide and centred on (x, z). */
    private void drawIcon(DrawContext ctx, Identifier icon, double x, double z, float degrees) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(mapX + x, mapY + z, 0);
        if (degrees != 0) ctx.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(degrees));
        ctx.drawTexture(icon, -8, -8, 16, 16, 0, 0, 8, 8, 8, 8);
        ctx.getMatrices().pop();
    }

    /** Above the start or below the destination, kept inside the parchment. */
    private void label(DrawContext ctx, String name, double x, double z, boolean above) {
        String text = textRenderer.trimToWidth(name, mapSize - 16);
        int w = textRenderer.getWidth(text);
        int lx = MathHelper.clamp((int) x - w / 2, 6, mapSize - 6 - w);
        int lz = above ? (int) z - 20 : (int) z + 10;
        if (lz < 6 || lz > mapSize - 14) lz = above ? (int) z + 10 : (int) z - 20;
        ctx.drawText(textRenderer, text, mapX + lx, mapY + lz, INK, false);
    }

    /** Yes asks the server to cancel; the server closes the screen once it has. */
    public static final class Abandon extends ConfirmScreen {
        public Abandon(TravelScreen parent) {
            super(yes -> {
                if (yes) ClientNet.cancel();
                MinecraftClient.getInstance().setScreen(parent);
            }, Stagecoach.text("screen.abandon.title"), message(parent.journey.refundPercent()),
                    Stagecoach.text("screen.abandon.yes"), Stagecoach.text("screen.abandon.no"));
        }

        private static Text message(int refundPercent) {
            return refundPercent < 100 ? Stagecoach.text("screen.abandon.message_partial", refundPercent)
                    : Stagecoach.text("screen.abandon.message");
        }

        @Override
        public boolean shouldPause() {
            return false;
        }
    }

    /** Never a spectator (that renders as a floating head) and no name tag. */
    private static final class Traveller extends OtherClientPlayerEntity {
        Traveller(ClientWorld world, AbstractClientPlayerEntity real) {
            super(world, real.getGameProfile());
            dataTracker.set(PLAYER_MODEL_PARTS, real.getDataTracker().get(PLAYER_MODEL_PARTS));
            setPosition(real.getX(), world.getBottomY() - 500, real.getZ()); // far from the camera: no name label
            float yaw = 90f; // faces +X on screen once the half-turn about Z is applied
            setYaw(yaw);
            bodyYaw = headYaw = prevBodyYaw = prevHeadYaw = yaw;
            setPitch(0);
        }

        @Override
        public boolean isSpectator() {
            return false;
        }

        @Override
        public boolean isInvisible() {
            return false;
        }

        @Override
        public boolean shouldRenderName() {
            return false;
        }
    }
}
