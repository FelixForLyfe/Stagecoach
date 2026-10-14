package net.feelix.stagecoach.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class TravelScreen extends Screen {
    private static final Identifier MAP = Identifier.withDefaultNamespace("textures/map/map_background.png");
    private static final Identifier ORIGIN = Identifier.withDefaultNamespace("textures/map/decorations/blue_banner.png");
    private static final Identifier DESTINATION = Identifier.withDefaultNamespace("textures/map/decorations/red_banner.png");
    private static final Identifier PLAYER = Identifier.withDefaultNamespace("textures/map/decorations/player.png");
    private static final ItemStack CLOCK = new ItemStack(Items.CLOCK);
    private static final int INK = 0xFF4A3A28;
    private static final int INK_FAINT = 0x704A3A28;
    private static final int DOT_STEP = 5;
    /** Entities draw upside down in a GUI without this half-turn about Z, as in vanilla's inventory. */
    private static final Quaternionf UPRIGHT = new Quaternionf().rotateZ((float) Math.PI);

    private final Packets.JourneyStart journey;
    private final Scene scene;
    private final Component originLabel;
    private final TravelClock clock = new TravelClock();
    private final Vector3f feet = new Vector3f();
    private Traveller traveller;
    private RoutePlot plot;
    private int mapX, mapY, mapSize;
    private float[] dotX = new float[0], dotZ = new float[0], dotAt = new float[0];
    private long shownSecond = -1;
    private Component remaining = Component.empty();

    public TravelScreen(Packets.JourneyStart journey) {
        super(Stagecoach.text("screen.travelling", journey.destName()));
        this.journey = journey;
        this.scene = SceneManager.pick(journey, Minecraft.getInstance().level);
        this.originLabel = journey.originName().isEmpty() ? Stagecoach.text("screen.set_off_here") : Component.literal(journey.originName());
    }

    public Scene scene() {
        return scene;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Stagecoach.text("screen.cancel_journey"), b -> minecraft.setScreen(new Abandon(this)))
                .bounds(width / 2 - 55, height - 28, 110, 20).build());
        if (traveller == null && minecraft.level != null && minecraft.player != null) traveller = new Traveller(minecraft.level, minecraft.player);
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
            traveller.tickCount++;
            traveller.walkAnimation.update(0.85f, 0.4f, 1.0f); // a steady walk
        }
    }

    /** The road scene fills the whole screen, so no vanilla blur under it. */
    @Override
    public void renderBackground(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        if (!ClientConfig.get().roadScene()) super.renderBackground(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false; // the journey runs on the server's clock; a paused integrated server would stop it
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            minecraft.setScreen(new Abandon(this));
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        ClientLevel level = minecraft.level;
        if (level == null) return;
        double elapsed = clock.elapsed(System.nanoTime(), level.getGameTime() - journey.startTime() + delta);
        float progress = (float) Mth.clamp(elapsed / journey.durationTicks(), 0, 1);
        ClientConfig cfg = ClientConfig.get();
        if (cfg.roadScene()) {
            EnvironmentAttributeProbe sky = minecraft.gameRenderer.getMainCamera().attributeProbe();
            float skyAngle = sky.getValue(EnvironmentAttributes.SUN_ANGLE, delta) / 360f; // degrees, 0 = noon
            SceneRenderer.draw(ctx, width, height, scene, journey.seed(), (float) elapsed, skyAngle, sky.getValue(EnvironmentAttributes.MOON_PHASE, delta));
        } else if (cfg.mapScreen()) {
            drawMap(ctx, progress);
        }

        long left = Math.max(0, journey.startTime() + journey.durationTicks() - level.getGameTime());
        long second = (left + 19) / 20;
        if (second != shownSecond) {
            shownSecond = second;
            remaining = second == 0 ? Stagecoach.text("screen.arriving") : Stagecoach.text("screen.remaining", TravelMath.clock(left));
        }
        int signW = Math.max(160, Math.max(font.width(title), font.width(remaining)) + 44);
        int signX = (width - signW) / 2;
        WoodUi.plaque(ctx, signX, 16, signW, 30, true);
        WoodUi.icon(ctx, CLOCK, signX + 6, 23, 16);
        ctx.drawString(font, title, signX + 26 + (signW - 30 - font.width(title)) / 2, 21, WoodUi.INK, false);
        ctx.drawString(font, remaining, signX + 26 + (signW - 30 - font.width(remaining)) / 2, 33, 0xFF6B4A2B, false);

        int barW = Math.min(300, width - 60);
        int barX = (width - barW) / 2, barY = height - 44;
        int fillW = WoodUi.progressBar(ctx, barX, barY, barW, 10, progress);
        if (cfg.roadScene() && scene.coach() && traveller != null) drawTraveller(ctx, barX + 3 + fillW, barY - 2, 20, delta);
        super.render(ctx, mouseX, mouseY, delta);
    }

    /** Drawn like the inventory draws the player, with the real partial tick so the walk is smooth. */
    private void drawTraveller(GuiGraphics ctx, int x, int y, int size, float delta) {
        EntityRenderState state = minecraft.getEntityRenderDispatcher().getRenderer(traveller).createRenderState(traveller, delta);
        state.lightCoords = 0xF000F0;
        state.shadowPieces.clear();
        state.outlineColor = 0;
        float h = traveller.getBbHeight();
        int top = y - Math.round(h * size) - 2;
        // the box centre is the figure's middle, so lift the feet by half the height, as vanilla does
        ctx.submitEntityRenderState(state, size, feet.set(0, h / 2f, 0), UPRIGHT, null, x - size, top, x + size, y + 2);
    }

    private void drawMap(GuiGraphics ctx, float progress) {
        ctx.blit(RenderPipelines.GUI_TEXTURED, MAP, mapX, mapY, 0, 0, mapSize, mapSize, 64, 64, 64, 64);
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
    private void drawIcon(GuiGraphics ctx, Identifier icon, double x, double z, float degrees) {
        ctx.pose().pushMatrix();
        ctx.pose().translate((float) (mapX + x), (float) (mapY + z));
        if (degrees != 0) ctx.pose().rotate((float) Math.toRadians(degrees));
        ctx.blit(RenderPipelines.GUI_TEXTURED, icon, -8, -8, 0, 0, 16, 16, 8, 8, 8, 8);
        ctx.pose().popMatrix();
    }

    /** Above the start or below the destination, kept inside the parchment. */
    private void label(GuiGraphics ctx, String name, double x, double z, boolean above) {
        String text = font.plainSubstrByWidth(name, mapSize - 16);
        int w = font.width(text);
        int lx = Mth.clamp((int) x - w / 2, 6, mapSize - 6 - w);
        int lz = above ? (int) z - 20 : (int) z + 10;
        if (lz < 6 || lz > mapSize - 14) lz = above ? (int) z + 10 : (int) z - 20;
        ctx.drawString(font, text, mapX + lx, mapY + lz, INK, false);
    }

    /** Yes asks the server to cancel; the server closes the screen once it has. */
    public static final class Abandon extends ConfirmScreen {
        public Abandon(TravelScreen parent) {
            super(yes -> {
                if (yes) ClientNet.cancel();
                Minecraft.getInstance().setScreen(parent);
            }, Stagecoach.text("screen.abandon.title"), message(parent.journey.refundPercent()),
                    Stagecoach.text("screen.abandon.yes"), Stagecoach.text("screen.abandon.no"));
        }

        private static Component message(int refundPercent) {
            return refundPercent < 100 ? Stagecoach.text("screen.abandon.message_partial", refundPercent)
                    : Stagecoach.text("screen.abandon.message");
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    /** Never a spectator (that renders as a floating head) and no name tag. */
    private static final class Traveller extends RemotePlayer {
        Traveller(ClientLevel level, AbstractClientPlayer real) {
            super(level, real.getGameProfile());
            entityData.set(DATA_PLAYER_MODE_CUSTOMISATION, real.getEntityData().get(DATA_PLAYER_MODE_CUSTOMISATION));
            setPos(real.getX(), level.getMinY() - 500, real.getZ()); // far from the camera: no name label
            float yaw = 90f; // faces +X on screen once the half-turn about Z is applied
            setYRot(yaw);
            yBodyRot = yHeadRot = yBodyRotO = yHeadRotO = yaw;
            setXRot(0);
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
        public boolean shouldShowName() {
            return false;
        }
    }
}
