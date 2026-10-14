package net.feelix.stagecoach.devclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class Harness {
    private Harness() {}

    private static final boolean ON = Boolean.getBoolean("stagecoach.harness");
    private static final ConcurrentLinkedQueue<String> COMMANDS = new ConcurrentLinkedQueue<>();
    private static String lastScreen = "";
    private static int screenShotIn = -1;
    private static int autoShots;
    private static int wait;

    public static void onMessage(String text) {
        if (ON && text.startsWith("#")) COMMANDS.add(text.substring(1).strip());
    }

    public static void tick(Minecraft mc) {
        if (!ON) return;
        String screen = mc.screen == null ? "none" : mc.screen.getClass().getSimpleName();
        if (!screen.equals(lastScreen)) {
            lastScreen = screen;
            System.out.println("[harness] screen " + screen);
            screenShotIn = 15; // let the new screen render first
            if (screen.equals("DisconnectedScreen")) {
                // the server went away: an invisible client must never linger
                System.out.println("[harness] disconnected - quitting");
                mc.stop();
                return;
            }
        }
        if (screenShotIn >= 0 && screenShotIn-- == 0) shot(mc, String.format("auto_%02d_%s", autoShots++, screen));
        if (wait > 0) {
            wait--;
            return;
        }
        String cmd = COMMANDS.poll();
        if (cmd != null) run(mc, cmd);
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), 1, msg -> {});
        System.out.println("[harness] screenshot " + name);
    }

    private static void run(Minecraft mc, String cmd) {
        String[] a = cmd.split(" ", 2);
        String arg = a.length > 1 ? a[1] : "";
        System.out.println("[harness] " + cmd);
        try {
            switch (a[0]) {
                case "shot" -> shot(mc, arg);
                case "wait" -> wait = Integer.parseInt(arg);
                case "look" -> {
                    String[] p = arg.split(" ");
                    mc.player.setYRot(Float.parseFloat(p[0]));
                    mc.player.setXRot(Float.parseFloat(p[1]));
                }
                case "use" -> {
                    String[] p = arg.split(" ");
                    BlockPos pos = new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
                    Direction face = Direction.valueOf(p[3].toUpperCase(Locale.ROOT));
                    Vec3 at = Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
                    mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(at, face, pos, false));
                }
                case "click" -> {
                    AbstractButton b = find(mc.screen, AbstractButton.class, arg);
                    if (b == null) System.out.println("[harness] no button with '" + arg + "' on " + lastScreen);
                    else b.keyPressed(new KeyEvent(257, 0, 0)); // Enter presses the button
                }
                case "type" -> {
                    EditBox box = find(mc.screen, EditBox.class, null);
                    if (box == null) System.out.println("[harness] no text box on " + lastScreen);
                    else box.setValue(arg);
                }
                case "esc" -> {
                    if (mc.screen != null) mc.screen.keyPressed(new KeyEvent(256, 0, 0));
                }
                case "buttons" -> {
                    List<String> labels = new ArrayList<>();
                    for (GuiEventListener l : all(mc.screen)) {
                        if (l instanceof AbstractButton b) labels.add(b.getMessage().getString());
                    }
                    System.out.println("[harness] buttons on " + lastScreen + ": " + labels);
                }
                case "quit" -> mc.stop();
                default -> System.out.println("[harness] unknown command " + cmd);
            }
        } catch (RuntimeException e) {
            System.out.println("[harness] failed: " + cmd + " - " + e);
        }
    }

    private static <T> T find(Screen screen, Class<T> type, String label) {
        for (GuiEventListener l : all(screen)) {
            if (!type.isInstance(l)) continue;
            if (label == null) return type.cast(l);
            if (l instanceof AbstractButton b
                    && b.getMessage().getString().toLowerCase(Locale.ROOT).contains(label.toLowerCase(Locale.ROOT))) {
                return type.cast(l);
            }
        }
        return null;
    }

    private static List<GuiEventListener> all(Screen screen) {
        List<GuiEventListener> out = new ArrayList<>();
        if (screen != null) collect(screen.children(), out);
        return out;
    }

    private static void collect(List<? extends GuiEventListener> in, List<GuiEventListener> out) {
        for (GuiEventListener l : in) {
            out.add(l);
            if (l instanceof ContainerEventHandler c) collect(c.children(), out);
        }
    }
}
