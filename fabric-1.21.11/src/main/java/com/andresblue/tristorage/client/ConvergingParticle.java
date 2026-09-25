package com.andresblue.tristorage.client;

import net.fabricmc.fabric.api.client.particle.v1.FabricSpriteProvider;
import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

/** The long, curved 1.9 portal/end mote trajectory into the singularity. */
public final class ConvergingParticle extends BillboardParticle {
    private final FabricSpriteProvider sprites;
    private final double targetX;
    private final double targetY;
    private final double targetZ;
    private final Vec3d radialDirection;
    private final Vec3d tangentDirection;
    private final Vec3d orbitNormal;
    private final double initialRadius;
    private final double turnRadians;
    private final int travelAge;
    private final float baseScale;
    private final float baseAlpha;

    private ConvergingParticle(ClientWorld world, double x, double y, double z,
                               double targetX, double targetY, double targetZ,
                               FabricSpriteProvider sprites, boolean endPortal,
                               Random random) {
        super(world, x, y, z, sprites.getFirst());
        this.sprites = sprites;
        this.targetX = targetX;
        this.targetY = targetY;
        this.targetZ = targetZ;

        Vec3d radial = new Vec3d(x - targetX, y - targetY, z - targetZ);
        initialRadius = Math.max(0.001, radial.length());
        radialDirection = radial.multiply(1.0 / initialRadius);
        Vec3d helper = Math.abs(radialDirection.y) < 0.82
                ? new Vec3d(0.0, 1.0, 0.0) : new Vec3d(1.0, 0.0, 0.0);
        Vec3d baseTangent = radialDirection.crossProduct(helper).normalize();
        Vec3d baseBitangent = radialDirection.crossProduct(baseTangent).normalize();
        double tangentPhase = random.nextDouble() * Math.PI * 2.0;
        tangentDirection = baseTangent.multiply(Math.cos(tangentPhase))
                .add(baseBitangent.multiply(Math.sin(tangentPhase))).normalize();
        orbitNormal = radialDirection.crossProduct(tangentDirection).normalize();

        travelAge = 116 + random.nextInt(33);
        maxAge = travelAge;
        double turns = 0.45 + random.nextDouble() * 0.25;
        turnRadians = Math.PI * 2.0 * turns * (random.nextBoolean() ? 1.0 : -1.0);
        gravityStrength = 0.0f;
        collidesWithWorld = false;
        baseScale = 0.075f + random.nextFloat() * 0.04f;
        baseAlpha = endPortal
                ? 0.52f + random.nextFloat() * 0.16f
                : 0.46f + random.nextFloat() * 0.18f;
        scale = baseScale;
        alpha = baseAlpha;
        if (endPortal) setColor(0.44f, 0.62f, 0.92f);
        else setColor(0.54f, 0.08f, 0.76f);
        setSprite(sprites.getSprite(0, 1));
    }

    @Override
    public void tick() {
        lastX = x;
        lastY = y;
        lastZ = z;
        if (++age >= travelAge) {
            markDead();
            return;
        }

        double progress = Math.min(1.0, age / (double) travelAge);
        double absorbed = progress * progress;
        double radius = initialRadius * (1.0 - absorbed);
        double angle = turnRadians * progress;
        Vec3d curved = radialDirection.multiply(Math.cos(angle))
                .add(tangentDirection.multiply(Math.sin(angle)));
        double ripple = Math.sin(progress * Math.PI) * Math.sin(angle * 0.65)
                * initialRadius * 0.055;
        Vec3d position = new Vec3d(targetX, targetY, targetZ)
                .add(curved.multiply(radius)).add(orbitNormal.multiply(ripple));
        setPos(position.x, position.y, position.z);
        scale = (float) (baseScale * (0.96 - absorbed * 0.72));
        double fadeProgress = Math.max(0.0, (progress - 0.8) / 0.2);
        double fade = 1.0 - fadeProgress * fadeProgress * (3.0 - 2.0 * fadeProgress);
        alpha = (float) (baseAlpha * fade);
        setSprite(sprites.getSprite(Math.min(age, travelAge - 1), travelAge));
    }

    @Override
    protected int getBrightness(float tint) {
        return 0xF000F0;
    }

    @Override
    protected RenderType getRenderType() {
        return RenderType.PARTICLE_ATLAS_TRANSLUCENT;
    }

    public static final class Factory implements ParticleFactory<SimpleParticleType> {
        private final FabricSpriteProvider sprites;
        public Factory(FabricSpriteProvider sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(SimpleParticleType parameters, ClientWorld world,
                                       double x, double y, double z,
                                       double targetX, double targetY, double targetZ,
                                       Random random) {
            return new ConvergingParticle(world, x, y, z,
                    targetX, targetY, targetZ, sprites, false, random);
        }
    }

    public static final class EndFactory implements ParticleFactory<SimpleParticleType> {
        private final FabricSpriteProvider sprites;
        public EndFactory(FabricSpriteProvider sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(SimpleParticleType parameters, ClientWorld world,
                                       double x, double y, double z,
                                       double targetX, double targetY, double targetZ,
                                       Random random) {
            return new ConvergingParticle(world, x, y, z,
                    targetX, targetY, targetZ, sprites, true, random);
        }
    }
}
