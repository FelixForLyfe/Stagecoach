package net.feelix.stagecoach.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/** A warm brown puff kicked up at a wheel: drifts out low, grows a little and fades in about a second. */
public final class RoadDustParticle extends SingleQuadParticle {
    private final SpriteSet sprites;

    private RoadDustParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, sprites.first());
        this.sprites = sprites;
        xd = vx;
        yd = vy;
        zd = vz;
        friction = 0.86f;
        gravity = -0.01f;
        lifetime = 16 + random.nextInt(8);
        quadSize = 0.18f + random.nextFloat() * 0.08f;
        float shade = 0.85f + random.nextFloat() * 0.15f;
        setColor(0.72f * shade, 0.58f * shade, 0.42f * shade);
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) return;
        setSpriteFromAge(sprites);
        float life = (float) age / lifetime;
        alpha = 0.8f * (1f - life * life);
        quadSize *= 1.02f;
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    public record Factory(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                                       RandomSource random) {
            return new RoadDustParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
