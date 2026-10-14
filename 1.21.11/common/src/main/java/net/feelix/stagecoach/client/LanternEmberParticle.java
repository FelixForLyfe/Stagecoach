package net.feelix.stagecoach.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/** A small amber spark that drifts up from a pillar top, flickers and dies out in about a second. */
public final class LanternEmberParticle extends SingleQuadParticle {
    private final SpriteSet sprites;

    private LanternEmberParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, sprites.first());
        this.sprites = sprites;
        xd = vx;
        yd = vy;
        zd = vz;
        friction = 0.92f;
        gravity = -0.02f;
        lifetime = 20 + random.nextInt(8);
        quadSize = 0.06f + random.nextFloat() * 0.04f;
        setColor(1.0f, 0.62f + random.nextFloat() * 0.12f, 0.2f);
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) return;
        setSpriteFromAge(sprites);
        float life = (float) age / lifetime;
        alpha = (1f - life) * (0.75f + 0.25f * random.nextFloat());
    }

    /** Full bright, as embers glow in the dark. */
    @Override
    protected int getLightColor(float tint) {
        return 0xF000F0;
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    public record Factory(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                                       RandomSource random) {
            return new LanternEmberParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
