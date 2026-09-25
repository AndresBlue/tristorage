package com.andresblue.tristorage.client;

import com.andresblue.tristorage.TriStorageMod;
import com.andresblue.tristorage.block.AntennaMount;
import com.andresblue.tristorage.blockentity.LinkerBlockEntity;
import com.andresblue.tristorage.blockentity.StorageCoreBlockEntity;
import com.andresblue.tristorage.storage.StorageNetwork;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Client-only miniature item projections orbiting an active dimensional singularity. */
public final class LinkerBlockEntityRenderer implements BlockEntityRenderer<LinkerBlockEntity> {
    private static final int MAX_ORBIT_ITEMS = 8;
    private static final double SINGULARITY_DISTANCE = 1.03125;
    private static final int SINGULARITY_FRAMES = 8;
    private static final double SINGULARITY_FRAME_TICKS = 4.0;
    private static final int PARTICLE_DIRECTIONS = 48;
    /** One mote every 5 ticks (~2× denser than the original 10-tick cadence). */
    private static final long PARTICLE_INTERVAL_TICKS = 5L;
    private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));
    private static final ResourceLocation SINGULARITY_TEXTURE = TriStorageMod.id(
            "textures/block/dimensional_singularity_billboard_animated.png");
    private static final RenderType SINGULARITY_LAYER =
            RenderType.entityCutoutNoCull(SINGULARITY_TEXTURE);

    private final ItemRenderer itemRenderer;
    private final Map<BlockPos, CachedOrbit> orbitCache = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<BlockPos, CachedOrbit> eldest) {
            return size() > 256;
        }
    };
    private final Map<BlockPos, OrbitVisualState> orbitVisualStates =
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<BlockPos, OrbitVisualState> eldest) {
                    return size() > 256;
                }
            };
    private final Map<BlockPos, EmitterState> emitterStates =
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<BlockPos, EmitterState> eldest) {
                    return size() > 256;
                }
            };
    private ClientLevel emitterWorld;

    public LinkerBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(LinkerBlockEntity linker, float tickDelta, PoseStack matrices,
                       MultiBufferSource vertexConsumers, int light, int overlay) {
        if (!(linker.getLevel() instanceof ClientLevel world)) {
            return;
        }
        AntennaMount mount = linker.antennaMount();
        Direction direction = mount.direction();
        if (direction == null) {
            return;
        }

        Vec3 axis = new Vec3(direction.getStepX(), direction.getStepY(),
                direction.getStepZ());
        Vec3 reference = direction.getAxis().isVertical()
                ? new Vec3(1.0, 0.0, 0.0)
                : new Vec3(0.0, 1.0, 0.0);
        Vec3 tangent = axis.cross(reference).normalize();
        Vec3 bitangent = axis.cross(tangent).normalize();
        Vec3 center = new Vec3(0.5, 0.5, 0.5).add(axis.scale(SINGULARITY_DISTANCE));
        double time = world.getGameTime() + tickDelta;
        renderSingularityBillboard(center, time, matrices, vertexConsumers);
        emitSingularityParticle(world, linker.getBlockPos(), center);

        List<ItemStack> orbitItems = orbitItemsForFrame(linker, orbitPool(linker), time);
        if (orbitItems.isEmpty()) {
            return;
        }

        for (int index = 0; index < orbitItems.size(); index++) {
            boolean absorbed = index >= Math.max(1, orbitItems.size() / 2);
            double phase = index * 2.173;
            double radius;
            double scale;
            double angle;

            if (absorbed) {
                double cycle = positiveModulo(time * 0.012 + index * 0.271, 1.0);
                double eased = cycle * cycle * (3.0 - 2.0 * cycle);
                radius = 0.52 * (1.0 - eased) + 0.018;
                double appear = Math.min(1.0, cycle / 0.10);
                scale = 0.095 * appear * Math.max(0.08, 1.0 - eased);
                angle = phase + cycle * Math.PI * 5.0;
            } else {
                radius = 0.31 + Math.sin(time * 0.045 + phase) * 0.035;
                scale = 0.078;
                angle = phase + time * (index % 2 == 0 ? 0.055 : -0.048);
            }

            double axialWobble = Math.sin(time * 0.037 + phase) * 0.055;
            Vec3 position = center
                    .add(tangent.scale(Math.cos(angle) * radius))
                    .add(bitangent.scale(Math.sin(angle) * radius))
                    .add(axis.scale(axialWobble));

            matrices.pushPose();
            matrices.translate(position.x, position.y, position.z);
            matrices.mulPose(Minecraft.getInstance()
                    .getEntityRenderDispatcher().cameraOrientation());
            matrices.mulPose(Axis.ZP.rotationDegrees(
                    (float) (time * 1.8 + index * 41.0)));
            matrices.scale((float) scale, (float) scale, (float) scale);
            itemRenderer.renderStatic(orbitItems.get(index), ItemDisplayContext.GUI,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    matrices, vertexConsumers, linker.getLevel(),
                    linker.getBlockPos().hashCode() + index);
            matrices.popPose();
        }
    }

    @Override
    public boolean shouldRenderOffScreen(LinkerBlockEntity blockEntity) {
        return true;
    }

    private static double positiveModulo(double value, double modulus) {
        double result = value % modulus;
        return result < 0.0 ? result + modulus : result;
    }

    /** Draws one opaque cutout that faces the local player's camera. */
    private static void renderSingularityBillboard(Vec3 center, double time,
                                                   PoseStack matrices,
                                                   MultiBufferSource vertexConsumers) {
        matrices.pushPose();
        matrices.translate(center.x, center.y, center.z);
        matrices.mulPose(Minecraft.getInstance()
                .getEntityRenderDispatcher().cameraOrientation());
        matrices.mulPose(Axis.YP.rotationDegrees(180.0f));
        float pulse = 0.38f + (float) Math.sin(time * 0.16) * 0.008f;
        matrices.scale(pulse, pulse, pulse);

        PoseStack.Pose entry = matrices.last();
        VertexConsumer vertices = vertexConsumers.getBuffer(SINGULARITY_LAYER);
        int frame = Math.floorMod((int) (time / SINGULARITY_FRAME_TICKS),
                SINGULARITY_FRAMES);
        float u0 = frame / (float) SINGULARITY_FRAMES;
        float u1 = (frame + 1) / (float) SINGULARITY_FRAMES;
        billboardVertex(vertices, entry, -0.5f, -0.5f, 0.0f, u0, 1.0f);
        billboardVertex(vertices, entry, 0.5f, -0.5f, 0.0f, u1, 1.0f);
        billboardVertex(vertices, entry, 0.5f, 0.5f, 0.0f, u1, 0.0f);
        billboardVertex(vertices, entry, -0.5f, 0.5f, 0.0f, u0, 0.0f);
        matrices.popPose();
    }

    private static void billboardVertex(VertexConsumer vertices, PoseStack.Pose entry,
                                        float x, float y, float z, float u, float v) {
        vertices.addVertex(entry, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(entry, 0.0f, 0.0f, 1.0f);
    }

    /**
     * Emits from a deterministic Fibonacci sphere instead of relying on the
     * block random-display callback. This gives every linker one controlled
     * cadence, evenly covers every direction, and prevents several render or
     * random-tick callbacks from creating a trail of near-identical motes.
     */
    private void emitSingularityParticle(ClientLevel world, BlockPos linkerPos,
                                         Vec3 localCenter) {
        if (emitterWorld != world) {
            emitterWorld = world;
            emitterStates.clear();
            orbitCache.clear();
            orbitVisualStates.clear();
        }

        BlockPos key = linkerPos.immutable();
        long now = world.getGameTime();
        EmitterState state = emitterStates.get(key);
        if (state == null) {
            int seed = Math.floorMod(key.hashCode(), PARTICLE_DIRECTIONS);
            long stagger = Math.floorMod(key.asLong(), PARTICLE_INTERVAL_TICKS);
            state = new EmitterState(now + stagger, seed);
            emitterStates.put(key, state);
        }
        if (now < state.nextEmissionTick) {
            return;
        }

        // A time jump means the client stopped rendering this linker. Resume
        // with one particle; never try to catch up on missed emissions.
        state.nextEmissionTick = now + PARTICLE_INTERVAL_TICKS;
        int sample = Math.floorMod(state.sequence++, PARTICLE_DIRECTIONS);
        int directionIndex = Math.floorMod(sample + state.directionOffset,
                PARTICLE_DIRECTIONS);
        double vertical = 1.0 - 2.0 * ((directionIndex + 0.5) / PARTICLE_DIRECTIONS);
        double horizontal = Math.sqrt(Math.max(0.0, 1.0 - vertical * vertical));
        double azimuth = directionIndex * GOLDEN_ANGLE + state.directionOffset * 0.173;
        double radius = 0.62 + 0.16 * (0.5 + 0.5 *
                Math.sin(directionIndex * 1.731 + state.directionOffset));
        Vec3 offset = new Vec3(
                Math.cos(azimuth) * horizontal * radius,
                vertical * radius,
                Math.sin(azimuth) * horizontal * radius);
        Vec3 target = new Vec3(linkerPos.getX(), linkerPos.getY(), linkerPos.getZ())
                .add(localCenter);

        var particleType = sample % 3 == 0
                ? TriStorageMod.CONVERGING_END_PARTICLE.get()
                : TriStorageMod.CONVERGING_PORTAL_PARTICLE.get();
        world.addParticle(particleType,
                target.x + offset.x, target.y + offset.y, target.z + offset.z,
                target.x, target.y, target.z);
    }

    /**
     * Reads the same client-side core snapshot that powers the storage UI. No
     * fabricated placeholder stacks are used: an empty or disconnected system
     * has no orbiting items. The small cache avoids walking the network once per
     * render frame while still reacting immediately to a core revision change.
     */
    private List<ItemStack> orbitPool(LinkerBlockEntity linker) {
        if (linker.getLevel() == null) {
            return List.of();
        }
        BlockPos linkerPos = linker.getBlockPos().immutable();
        long now = linker.getLevel().getGameTime();
        CachedOrbit cached = orbitCache.get(linkerPos);
        if (cached != null) {
            if (cached.corePos() == null) {
                if (now < cached.nextProbeTick()) {
                    return cached.items();
                }
            } else {
                BlockEntity entity = linker.getLevel().getBlockEntity(cached.corePos());
                if (entity instanceof StorageCoreBlockEntity core
                        && core.orbitSnapshotRevision() == cached.revision()) {
                    return cached.items();
                }
            }
        }

        StorageCoreBlockEntity core = StorageNetwork.findCore(linker.getLevel(), linkerPos);
        if (core == null) {
            orbitCache.put(linkerPos, new CachedOrbit(
                    null, -1, List.of(), now + 10));
            orbitVisualStates.remove(linkerPos);
            return List.of();
        }

        List<ItemStack> pool = new ArrayList<>();
        for (ItemStack source : core.orbitItemSnapshot()) {
            if (source.isEmpty()) {
                continue;
            }
            pool.add(source.copyWithCount(1));
        }
        orbitCache.put(linkerPos, new CachedOrbit(core.getBlockPos().immutable(),
                core.orbitSnapshotRevision(), List.copyOf(pool), now + 10));
        return pool;
    }

    /**
     * Keeps every projected stack stable for its entire flight. Absorbed slots
     * select a replacement only at the cycle boundary, when their previous
     * item is at zero scale in the singularity. Candidates already visible in
     * another slot are excluded whenever the storage pool has enough variety.
     */
    private List<ItemStack> orbitItemsForFrame(LinkerBlockEntity linker,
                                               List<ItemStack> pool, double time) {
        BlockPos linkerPos = linker.getBlockPos().immutable();
        if (pool.isEmpty()) {
            orbitVisualStates.remove(linkerPos);
            return List.of();
        }

        OrbitVisualState state = orbitVisualStates.computeIfAbsent(
                linkerPos, ignored -> new OrbitVisualState(linkerPos.asLong()));
        int targetSize = Math.min(MAX_ORBIT_ITEMS, pool.size());
        while (state.items.size() > targetSize) {
            state.items.remove(state.items.size() - 1);
        }
        for (int index = 0; index < state.items.size(); index++) {
            ItemStack current = state.items.get(index);
            if (!containsItem(pool, current)) {
                state.items.set(index, chooseReplacement(pool, state, index, current));
            }
        }
        while (state.items.size() < targetSize) {
            int index = state.items.size();
            state.items.add(chooseReplacement(pool, state, index, ItemStack.EMPTY));
            state.absorptionCycles[index] = Long.MIN_VALUE;
        }

        int absorbedStart = Math.max(1, state.items.size() / 2);
        for (int index = absorbedStart; index < state.items.size(); index++) {
            double cycleTime = time * 0.012 + index * 0.271;
            long cycleNumber = (long) Math.floor(cycleTime);
            long previousCycle = state.absorptionCycles[index];
            if (previousCycle == Long.MIN_VALUE) {
                state.absorptionCycles[index] = cycleNumber;
            } else if (cycleNumber != previousCycle) {
                ItemStack previous = state.items.get(index);
                state.items.set(index, chooseReplacement(pool, state, index, previous));
                state.absorptionCycles[index] = cycleNumber;
            }
        }
        return state.items;
    }

    private static ItemStack chooseReplacement(List<ItemStack> pool,
                                               OrbitVisualState state, int slot,
                                               ItemStack previous) {
        if (pool.isEmpty()) {
            return ItemStack.EMPTY;
        }
        long mixed = mix64(state.seed + state.replacementSequence++
                * 0x9E3779B97F4A7C15L + slot * 0xC2B2AE3D27D4EB4FL);
        int start = Math.floorMod((int) (mixed ^ (mixed >>> 32)), pool.size());

        // Preferred path: a genuinely new projection, different from both
        // the consumed item and every item still visible around it.
        for (int offset = 0; offset < pool.size(); offset++) {
            ItemStack candidate = pool.get((start + offset) % pool.size());
            if (!sameItem(candidate, previous)
                    && !isAssignedElsewhere(state.items, candidate, slot)) {
                return singleCopy(candidate);
            }
        }
        // A small storage may not contain enough unique types for all slots.
        // Still avoid repeating the consumed item whenever possible.
        for (int offset = 0; offset < pool.size(); offset++) {
            ItemStack candidate = pool.get((start + offset) % pool.size());
            if (!sameItem(candidate, previous)) {
                return singleCopy(candidate);
            }
        }
        return singleCopy(pool.get(start));
    }

    private static boolean containsItem(List<ItemStack> stacks, ItemStack target) {
        for (ItemStack stack : stacks) {
            if (sameItem(stack, target)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAssignedElsewhere(List<ItemStack> assigned,
                                               ItemStack candidate, int slot) {
        for (int index = 0; index < assigned.size(); index++) {
            if (index != slot && sameItem(assigned.get(index), candidate)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameItem(ItemStack left, ItemStack right) {
        return !left.isEmpty() && !right.isEmpty()
                && ItemStack.isSameItemSameComponents(left, right);
    }

    private static ItemStack singleCopy(ItemStack source) {
        return source.copyWithCount(1);
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private record CachedOrbit(BlockPos corePos, int revision, List<ItemStack> items,
                               long nextProbeTick) {
    }

    private static final class OrbitVisualState {
        private final long seed;
        private final List<ItemStack> items = new ArrayList<>(MAX_ORBIT_ITEMS);
        private final long[] absorptionCycles = new long[MAX_ORBIT_ITEMS];
        private long replacementSequence;

        private OrbitVisualState(long seed) {
            this.seed = seed;
            Arrays.fill(absorptionCycles, Long.MIN_VALUE);
        }
    }

    private static final class EmitterState {
        private long nextEmissionTick;
        private int sequence;
        private final int directionOffset;

        private EmitterState(long nextEmissionTick, int directionOffset) {
            this.nextEmissionTick = nextEmissionTick;
            this.directionOffset = directionOffset;
        }
    }
}
