package net.feelix.stagecoach.client;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DefaultParticleType;

/** A warm brown puff kicked up at a wheel: drifts out low, grows a little and fades in about a second. */
public final class RoadDustParticle extends SpriteBillboardParticle {
    private final SpriteProvider sprites;

    private RoadDustParticle(ClientWorld world, double x, double y, double z, double vx, double vy, double vz, SpriteProvider sprites) {
        super(world, x, y, z);
        this.sprites = sprites;
        velocityX = vx;
        velocityY = vy;
        velocityZ = vz;
        velocityMultiplier = 0.86f;
        gravityStrength = -0.01f;
        maxAge = 16 + random.nextInt(8);
        scale = 0.18f + random.nextFloat() * 0.08f;
        float shade = 0.85f + random.nextFloat() * 0.15f;
        setColor(0.72f * shade, 0.58f * shade, 0.42f * shade);
        setSpriteForAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (dead) return;
        setSpriteForAge(sprites);
        float life = (float) age / maxAge;
        alpha = 0.8f * (1f - life * life);
        scale *= 1.02f;
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    public record Factory(SpriteProvider sprites) implements ParticleFactory<DefaultParticleType> {
        @Override
        public Particle createParticle(DefaultParticleType type, ClientWorld world, double x, double y, double z, double vx, double vy, double vz) {
            return new RoadDustParticle(world, x, y, z, vx, vy, vz, sprites);
        }
    }
}
