package com.andresblue.tristorage.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.phys.Vec3;

/** A small accretion mote that spirals smoothly into an explicit target. */
public final class ConvergingPortalParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final double targetX;
    private final double targetY;
    private final double targetZ;
    private final Vec3 radialDirection;
    private final Vec3 tangentDirection;
    private final Vec3 orbitNormal;
    private final double initialRadius;
    private final double turnRadians;
    private final int travelAge;
    private final float baseScale;
    private final float baseAlpha;

    private ConvergingPortalParticle(ClientLevel world, double x, double y, double z,
                                     double targetX, double targetY, double targetZ,
                                     SpriteSet sprites, boolean endPortal) {
        super(world, x, y, z, 0.0, 0.0, 0.0);
        this.sprites = sprites;
        this.targetX = targetX;
        this.targetY = targetY;
        this.targetZ = targetZ;

        Vec3 radial = new Vec3(x - targetX, y - targetY, z - targetZ);
        this.initialRadius = Math.max(0.001, radial.length());
        this.radialDirection = radial.scale(1.0 / this.initialRadius);
        Vec3 helper = Math.abs(this.radialDirection.y) < 0.82
                ? new Vec3(0.0, 1.0, 0.0)
                : new Vec3(1.0, 0.0, 0.0);
        Vec3 baseTangent = this.radialDirection.cross(helper).normalize();
        Vec3 baseBitangent = this.radialDirection.cross(baseTangent).normalize();
        double tangentPhase = world.random.nextDouble() * Math.PI * 2.0;
        this.tangentDirection = baseTangent.scale(Math.cos(tangentPhase))
                .add(baseBitangent.scale(Math.sin(tangentPhase))).normalize();
        this.orbitNormal = this.radialDirection.cross(this.tangentDirection)
                .normalize();

        // Long enough to read each mote as one persistent object, while the
        // renderer controls a small, fixed population around the singularity.
        this.travelAge = 116 + world.random.nextInt(33);
        this.lifetime = this.travelAge;
        double turns = 0.45 + world.random.nextDouble() * 0.25;
        this.turnRadians = Math.PI * 2.0 * turns
                * (world.random.nextBoolean() ? 1.0 : -1.0);
        this.gravity = 0.0f;
        this.friction = 1.0f;
        this.baseScale = 0.075f + world.random.nextFloat() * 0.040f;
        this.baseAlpha = endPortal
                ? 0.52f + world.random.nextFloat() * 0.16f
                : 0.46f + world.random.nextFloat() * 0.18f;
        this.quadSize = this.baseScale;
        this.alpha = this.baseAlpha;
        if (endPortal) {
            this.setColor(0.44f, 0.62f, 0.92f);
        } else {
            this.setColor(0.54f, 0.08f, 0.76f);
        }
        this.setSprite(sprites.get(0, 1));
    }

    @Override
    public void tick() {
        // Vanilla interpolates between prevPos and the current position every
        // frame. Keeping prevPos at the original spawn point caused the old
        // implementation to streak/reset toward its origin on every tick.
        xo = x;
        yo = y;
        zo = z;
        if (++age >= travelAge) {
            remove();
            return;
        }
        double progress = Math.min(1.0, age / (double) travelAge);
        // Gravity-like acceleration: linger around the outer shell, then get
        // pulled faster as the mote approaches the singularity.
        double absorbed = progress * progress;
        double radius = initialRadius * (1.0 - absorbed);
        double angle = turnRadians * progress;
        Vec3 curvedDirection = radialDirection.scale(Math.cos(angle))
                .add(tangentDirection.scale(Math.sin(angle)));
        double verticalRipple = Math.sin(progress * Math.PI)
                * Math.sin(angle * 0.65) * initialRadius * 0.055;
        Vec3 position = new Vec3(targetX, targetY, targetZ)
                .add(curvedDirection.scale(radius))
                .add(orbitNormal.scale(verticalRipple));
        setPos(position.x, position.y, position.z);

        quadSize = (float) (baseScale * (0.96 - absorbed * 0.72));
        double fadeProgress = Math.max(0.0, (progress - 0.80) / 0.20);
        double fade = 1.0 - fadeProgress * fadeProgress * (3.0 - 2.0 * fadeProgress);
        alpha = (float) (baseAlpha * fade);
        // SpriteProvider#getSprite(age, maxAge) maps the final boundary to
        // index == frameCount. Clamp before asking for a frame so a particle
        // can never address one past the eight generic portal sprites.
        int safeAge = Math.min(age, travelAge - 1);
        setSprite(sprites.get(safeAge, travelAge));
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static final class Factory implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Factory(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType parameters, ClientLevel world,
                                       double x, double y, double z,
                                       double targetX, double targetY, double targetZ) {
            return new ConvergingPortalParticle(world, x, y, z,
                    targetX, targetY, targetZ, sprites, false);
        }
    }

    public static final class EndFactory implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public EndFactory(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType parameters, ClientLevel world,
                                       double x, double y, double z,
                                       double targetX, double targetY, double targetZ) {
            return new ConvergingPortalParticle(world, x, y, z,
                    targetX, targetY, targetZ, sprites, true);
        }
    }
}
