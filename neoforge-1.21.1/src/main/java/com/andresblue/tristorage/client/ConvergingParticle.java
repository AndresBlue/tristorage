package com.andresblue.tristorage.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Bounded, curved particle that converges to the singularity without gravity or trails. */
public final class ConvergingParticle extends TextureSheetParticle {
    private static final float FADE_IN_END = 0.14f;
    private static final float FADE_OUT_START = 0.82f;

    private final SpriteSet sprites;
    private final double tx;
    private final double ty;
    private final double tz;
    private final Vec3 radial;
    private final Vec3 tangent;
    private final Vec3 normal;
    private final double radius;
    private final double turn;
    private final int travel;
    private final float baseScale;
    private final float baseAlpha;

    private ConvergingParticle(ClientLevel level, double x, double y, double z,
                               double tx, double ty, double tz,
                               SpriteSet sprites, boolean end, RandomSource random) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.tx = tx;
        this.ty = ty;
        this.tz = tz;

        Vec3 r = new Vec3(x - tx, y - ty, z - tz);
        radius = Math.max(0.01, r.length());
        radial = r.scale(1.0 / radius);
        Vec3 helper = Math.abs(radial.y) < 0.82 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 base = radial.cross(helper).normalize();
        Vec3 bit = radial.cross(base).normalize();
        double phase = random.nextDouble() * Math.PI * 2;
        tangent = base.scale(Math.cos(phase)).add(bit.scale(Math.sin(phase))).normalize();
        normal = radial.cross(tangent).normalize();

        // Shorter travel = visibly faster spiral into the singularity.
        travel = 72 + random.nextInt(25);
        lifetime = travel;
        turn = (random.nextBoolean() ? 1 : -1) * (Math.PI * (0.4 + random.nextDouble() * 0.3));
        gravity = 0;
        hasPhysics = false;
        baseScale = 0.045f + random.nextFloat() * 0.025f;
        baseAlpha = end
                ? 0.48f + random.nextFloat() * 0.18f
                : 0.42f + random.nextFloat() * 0.18f;
        quadSize = baseScale;
        // Start invisible so the first rendered frame fades in instead of popping.
        alpha = 0.0f;
        setColor(end ? 0.42f : 0.62f, end ? 0.55f : 0.08f, end ? 0.95f : 0.78f);
        setSprite(sprites.get(0, 1));
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        if (++age >= travel) {
            remove();
            return;
        }

        double p = Math.min(1.0, age / (double) travel);
        double e = p * p * (3.0 - 2.0 * p);
        double r = radius * (1.0 - e);
        double a = turn * p;
        Vec3 curve = radial.scale(Math.cos(a)).add(tangent.scale(Math.sin(a)));
        double ripple = Math.sin(p * Math.PI) * Math.sin(a) * radius * 0.04;
        Vec3 v = new Vec3(tx, ty, tz).add(curve.scale(r)).add(normal.scale(ripple));
        setPos(v.x, v.y, v.z);

        quadSize = (float) (baseScale * (0.96 - e * 0.72));
        alpha = baseAlpha * opacityForProgress((float) p);
        setSpriteFromAge(sprites);
    }

    private static float opacityForProgress(float p) {
        if (p < FADE_IN_END) {
            float t = p / FADE_IN_END;
            return t * t * (3.0f - 2.0f * t);
        }
        if (p > FADE_OUT_START) {
            float fade = Math.max(0.0f, (p - FADE_OUT_START) / (1.0f - FADE_OUT_START));
            return 1.0f - Mth.square(fade);
        }
        return 1.0f;
    }

    @Override
    protected int getLightColor(float tint) {
        return 0xF000F0;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final boolean end;

        public Provider(SpriteSet sprites, boolean end) {
            this.sprites = sprites;
            this.end = end;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double tx, double ty, double tz) {
            return new ConvergingParticle(level, x, y, z, tx, ty, tz, sprites, end,
                    RandomSource.create());
        }
    }
}
