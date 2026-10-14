package net.feelix.stagecoach.client;

import net.feelix.stagecoach.StagecoachRegistry;
import net.feelix.stagecoach.StagecoachSounds;
import net.feelix.stagecoach.config.ClientConfig;
import net.feelix.stagecoach.net.Packets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** Discovery and placement feedback, spawned here so each player's own particle and sound settings decide. */
public final class CoachEffects {
    private CoachEffects() {}

    /** Offsets from the controller block's centre with the coach facing north (its front towards -z). */
    private static final float[][] WHEELS = {{-1.2f, -1.3f}, {1.2f, -1.3f}, {-1.2f, 1.5f}, {1.2f, 1.5f}};
    private static final float[][] PILLARS = {{-1.25f, -0.8f}, {1.25f, -0.8f}, {-1.25f, 2.3f}, {1.25f, 2.3f}};
    private static final float PILLAR_TOP = 2.3f;

    public static void play(Minecraft client, Packets.StopEffect effect) {
        ClientLevel level = client.level;
        if (level == null || client.player == null) return;
        ClientConfig cfg = ClientConfig.get();
        if (effect.kind() == Packets.EFFECT_DISCOVER) {
            if (cfg.discoveryEffects && cfg.discoverySound) level.playLocalSound(client.player, StagecoachSounds.STOP_DISCOVER, SoundSource.PLAYERS, 1f, 1f);
            else level.playLocalSound(client.player, SoundEvents.HORSE_AMBIENT, SoundSource.NEUTRAL, 1f, 1f);
            if (!cfg.discoveryEffects) return;
            dust(level, effect, count(client, 16), 0.07f);
            embers(level, effect, count(client, 8));
            client.getToastManager().addToast(new DiscoveryToast(effect.name()));
        } else if (cfg.placementEffects) {
            double x = effect.pos().getX() + 0.5, y = effect.pos().getY() + 0.5, z = effect.pos().getZ() + 0.5;
            level.playLocalSound(x, y, z, StagecoachSounds.COACH_PLACE, SoundSource.BLOCKS, 1f, 1f, false);
            dust(level, effect, count(client, 10), 0.05f);
        }
    }

    /** Decreased halves the count, Minimal shows none. */
    private static int count(Minecraft client, int full) {
        ParticleStatus mode = client.options.particles().get();
        return mode == ParticleStatus.MINIMAL ? 0 : mode == ParticleStatus.DECREASED ? full / 2 : full;
    }

    private static void dust(ClientLevel level, Packets.StopEffect e, int n, float speed) {
        RandomSource random = level.getRandom();
        for (int i = 0; i < n; i++) {
            float[] w = WHEELS[i & 3];
            double ox = w[0] + (random.nextFloat() - 0.5f) * 0.5f, oz = w[1] + (random.nextFloat() - 0.5f) * 0.5f;
            double len = Math.max(0.1, Math.sqrt(ox * ox + oz * oz));
            spawn(level, StagecoachRegistry.ROAD_DUST, e, ox, 0.1 + random.nextFloat() * 0.15, oz, ox / len * speed,
                    0.01 + random.nextFloat() * 0.02, oz / len * speed);
        }
    }

    private static void embers(ClientLevel level, Packets.StopEffect e, int n) {
        RandomSource random = level.getRandom();
        for (int i = 0; i < n; i++) {
            float[] p = PILLARS[i & 3];
            spawn(level, StagecoachRegistry.LANTERN_EMBER, e, p[0] + (random.nextFloat() - 0.5f) * 0.2f, PILLAR_TOP,
                    p[1] + (random.nextFloat() - 0.5f) * 0.2f, (random.nextFloat() - 0.5f) * 0.02, 0.03 + random.nextFloat() * 0.03,
                    (random.nextFloat() - 0.5f) * 0.02);
        }
    }

    /** Turns an offset (and its velocity) from the north-facing frame to the coach's real facing. */
    private static void spawn(ClientLevel level, ParticleOptions type, Packets.StopEffect e, double ox, double oy, double oz,
                              double vx, double vy, double vz) {
        Direction facing = e.facing();
        double rx, rz, rvx, rvz;
        switch (facing) {
            case SOUTH -> { rx = -ox; rz = -oz; rvx = -vx; rvz = -vz; }
            case EAST -> { rx = -oz; rz = ox; rvx = -vz; rvz = vx; }
            case WEST -> { rx = oz; rz = -ox; rvx = vz; rvz = -vx; }
            default -> { rx = ox; rz = oz; rvx = vx; rvz = vz; }
        }
        level.addParticle(type, true, false, e.pos().getX() + 0.5 + rx, e.pos().getY() + oy, e.pos().getZ() + 0.5 + rz, rvx, vy, rvz);
    }
}
