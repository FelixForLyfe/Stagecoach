package net.feelix.stagecoach.client;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;

/** A small amber spark that drifts up from a pillar top, flickers and dies out in about a second. */
public final class LanternEmberParticle extends SpriteBillboardParticle {
    private final SpriteProvider sprites;

    private LanternEmberParticle(ClientWorld world, double x, double y, double z, double vx, double vy, double vz, SpriteProvider sprites) {
        super(world, x, y, z);
        this.sprites = sprites;
        velocityX = vx;
        velocityY = vy;
        velocityZ = vz;
        velocityMultiplier = 0.92f;
        gravityStrength = -0.02f;
        maxAge = 20 + random.nextInt(8);
        scale = 0.06f + random.nextFloat() * 0.04f;
        setColor(1.0f, 0.62f + random.nextFloat() * 0.12f, 0.2f);
        setSpriteForAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (dead) return;
        setSpriteForAge(sprites);
        float life = (float) age / maxAge;
        alpha = (1f - life) * (0.75f + 0.25f * random.nextFloat());
    }

    /** Full bright, as embers glow in the dark. */
    @Override
    protected int getBrightness(float tint) {
        return 0xF000F0;
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    public record Factory(SpriteProvider sprites) implements ParticleFactory<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientWorld world, double x, double y, double z, double vx, double vy, double vz) {
            return new LanternEmberParticle(world, x, y, z, vx, vy, vz, sprites);
        }
    }
}
