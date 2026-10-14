package net.feelix.stagecoach.client;

import net.feelix.stagecoach.StagecoachRegistry;
import net.feelix.stagecoach.StagecoachSounds;
import net.feelix.stagecoach.config.ClientConfig;
import net.feelix.stagecoach.net.Packets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

/** Discovery and placement feedback, spawned here so each player's own particle and sound settings decide. */
public final class CoachEffects {
    private CoachEffects() {}

    /** Offsets from the controller block's centre with the coach facing north (its front towards -z). */
    private static final float[][] WHEELS = {{-1.2f, -1.3f}, {1.2f, -1.3f}, {-1.2f, 1.5f}, {1.2f, 1.5f}};
    private static final float[][] PILLARS = {{-1.25f, -0.8f}, {1.25f, -0.8f}, {-1.25f, 2.3f}, {1.25f, 2.3f}};
    private static final float PILLAR_TOP = 2.3f;

    public static void play(MinecraftClient client, Packets.StopEffect effect) {
        ClientWorld world = client.world;
        if (world == null || client.player == null) return;
        ClientConfig cfg = ClientConfig.get();
        if (effect.kind() == Packets.EFFECT_DISCOVER) {
            if (cfg.discoveryEffects && cfg.discoverySound) client.player.playSoundToPlayer(StagecoachSounds.STOP_DISCOVER, SoundCategory.PLAYERS, 1f, 1f);
            else client.player.playSoundToPlayer(SoundEvents.ENTITY_HORSE_AMBIENT, SoundCategory.NEUTRAL, 1f, 1f);
            if (!cfg.discoveryEffects) return;
            dust(world, effect, count(client, 16), 0.07f);
            embers(world, effect, count(client, 8));
            client.getToastManager().add(new DiscoveryToast(effect.name()));
        } else if (cfg.placementEffects) {
            double x = effect.pos().getX() + 0.5, y = effect.pos().getY() + 0.5, z = effect.pos().getZ() + 0.5;
            world.playSound(x, y, z, StagecoachSounds.COACH_PLACE, SoundCategory.BLOCKS, 1f, 1f, false);
            dust(world, effect, count(client, 10), 0.05f);
        }
    }

    /** Decreased halves the count, Minimal shows none. */
    private static int count(MinecraftClient client, int full) {
        ParticlesMode mode = client.options.getParticles().getValue();
        return mode == ParticlesMode.MINIMAL ? 0 : mode == ParticlesMode.DECREASED ? full / 2 : full;
    }

    private static void dust(ClientWorld world, Packets.StopEffect e, int n, float speed) {
        Random random = world.getRandom();
        for (int i = 0; i < n; i++) {
            float[] w = WHEELS[i & 3];
            double ox = w[0] + (random.nextFloat() - 0.5f) * 0.5f, oz = w[1] + (random.nextFloat() - 0.5f) * 0.5f;
            double len = Math.max(0.1, Math.sqrt(ox * ox + oz * oz));
            spawn(world, StagecoachRegistry.ROAD_DUST, e, ox, 0.1 + random.nextFloat() * 0.15, oz, ox / len * speed,
                    0.01 + random.nextFloat() * 0.02, oz / len * speed);
        }
    }

    private static void embers(ClientWorld world, Packets.StopEffect e, int n) {
        Random random = world.getRandom();
        for (int i = 0; i < n; i++) {
            float[] p = PILLARS[i & 3];
            spawn(world, StagecoachRegistry.LANTERN_EMBER, e, p[0] + (random.nextFloat() - 0.5f) * 0.2f, PILLAR_TOP,
                    p[1] + (random.nextFloat() - 0.5f) * 0.2f, (random.nextFloat() - 0.5f) * 0.02, 0.03 + random.nextFloat() * 0.03,
                    (random.nextFloat() - 0.5f) * 0.02);
        }
    }

    /** Turns an offset (and its velocity) from the north-facing frame to the coach's real facing. */
    private static void spawn(ClientWorld world, ParticleEffect type, Packets.StopEffect e, double ox, double oy, double oz,
                              double vx, double vy, double vz) {
        Direction facing = e.facing();
        double rx, rz, rvx, rvz;
        switch (facing) {
            case SOUTH -> { rx = -ox; rz = -oz; rvx = -vx; rvz = -vz; }
            case EAST -> { rx = -oz; rz = ox; rvx = -vz; rvz = vx; }
            case WEST -> { rx = oz; rz = -ox; rvx = vz; rvz = -vx; }
            default -> { rx = ox; rz = oz; rvx = vx; rvz = vz; }
        }
        world.addParticle(type, true, e.pos().getX() + 0.5 + rx, e.pos().getY() + oy, e.pos().getZ() + 0.5 + rz, rvx, vy, rvz);
    }
}
