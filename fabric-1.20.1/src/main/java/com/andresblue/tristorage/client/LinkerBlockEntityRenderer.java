package com.andresblue.tristorage.client;

import com.andresblue.tristorage.TriStorageMod;
import com.andresblue.tristorage.block.AntennaMount;
import com.andresblue.tristorage.blockentity.LinkerBlockEntity;
import com.andresblue.tristorage.blockentity.StorageCoreBlockEntity;
import com.andresblue.tristorage.storage.StorageNetwork;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Client-only miniature item projections orbiting an active dimensional singularity. */
public final class LinkerBlockEntityRenderer implements BlockEntityRenderer<LinkerBlockEntity> {
    private static final int MAX_ORBIT_ITEMS = 8;
    private static final double SINGULARITY_DISTANCE = 1.03125;
    private static final int SINGULARITY_FRAMES = 8;
    private static final double SINGULARITY_FRAME_TICKS = 4.0;
    private static final int PARTICLE_DIRECTIONS = 48;
    private static final long PARTICLE_INTERVAL_TICKS = 10L;
    private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));
    private static final Identifier SINGULARITY_TEXTURE = new Identifier(
            TriStorageMod.MOD_ID,
            "textures/block/dimensional_singularity_billboard_animated.png");
    private static final RenderLayer SINGULARITY_LAYER =
            RenderLayer.getEntityCutoutNoCull(SINGULARITY_TEXTURE);

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
    private ClientWorld emitterWorld;

    public LinkerBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
        itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(LinkerBlockEntity linker, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        if (!(linker.getWorld() instanceof ClientWorld world)) {
            return;
        }
        AntennaMount mount = linker.antennaMount();
        Direction direction = mount.direction();
        if (direction == null) {
            return;
        }

        Vec3d axis = new Vec3d(direction.getOffsetX(), direction.getOffsetY(),
                direction.getOffsetZ());
        Vec3d reference = direction.getAxis().isVertical()
                ? new Vec3d(1.0, 0.0, 0.0)
                : new Vec3d(0.0, 1.0, 0.0);
        Vec3d tangent = axis.crossProduct(reference).normalize();
        Vec3d bitangent = axis.crossProduct(tangent).normalize();
        Vec3d center = new Vec3d(0.5, 0.5, 0.5).add(axis.multiply(SINGULARITY_DISTANCE));
        double time = world.getTime() + tickDelta;
        renderSingularityBillboard(center, time, matrices, vertexConsumers);
        emitSingularityParticle(world, linker.getPos(), center);

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
            Vec3d position = center
                    .add(tangent.multiply(Math.cos(angle) * radius))
                    .add(bitangent.multiply(Math.sin(angle) * radius))
                    .add(axis.multiply(axialWobble));

            matrices.push();
            matrices.translate(position.x, position.y, position.z);
            matrices.multiply(MinecraftClient.getInstance()
                    .getEntityRenderDispatcher().getRotation());
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(
                    (float) (time * 1.8 + index * 41.0)));
            matrices.scale((float) scale, (float) scale, (float) scale);
            itemRenderer.renderItem(orbitItems.get(index), ModelTransformationMode.GUI,
                    LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV,
                    matrices, vertexConsumers, linker.getWorld(),
                    linker.getPos().hashCode() + index);
            matrices.pop();
        }
    }

    @Override
    public boolean rendersOutsideBoundingBox(LinkerBlockEntity blockEntity) {
        return true;
    }

    private static double positiveModulo(double value, double modulus) {
        double result = value % modulus;
        return result < 0.0 ? result + modulus : result;
    }

    /** Draws one opaque cutout that faces the local player's camera. */
    private static void renderSingularityBillboard(Vec3d center, double time,
                                                   MatrixStack matrices,
                                                   VertexConsumerProvider vertexConsumers) {
        matrices.push();
        matrices.translate(center.x, center.y, center.z);
        matrices.multiply(MinecraftClient.getInstance()
                .getEntityRenderDispatcher().getRotation());
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f));
        float pulse = 0.38f + (float) Math.sin(time * 0.16) * 0.008f;
        matrices.scale(pulse, pulse, pulse);

        MatrixStack.Entry entry = matrices.peek();
        Matrix4f positionMatrix = entry.getPositionMatrix();
        Matrix3f normalMatrix = entry.getNormalMatrix();
        VertexConsumer vertices = vertexConsumers.getBuffer(SINGULARITY_LAYER);
        int frame = Math.floorMod((int) (time / SINGULARITY_FRAME_TICKS),
                SINGULARITY_FRAMES);
        float u0 = frame / (float) SINGULARITY_FRAMES;
        float u1 = (frame + 1) / (float) SINGULARITY_FRAMES;
        billboardVertex(vertices, positionMatrix, normalMatrix,
                -0.5f, -0.5f, 0.0f, u0, 1.0f);
        billboardVertex(vertices, positionMatrix, normalMatrix,
                0.5f, -0.5f, 0.0f, u1, 1.0f);
        billboardVertex(vertices, positionMatrix, normalMatrix,
                0.5f, 0.5f, 0.0f, u1, 0.0f);
        billboardVertex(vertices, positionMatrix, normalMatrix,
                -0.5f, 0.5f, 0.0f, u0, 0.0f);
        matrices.pop();
    }

    private static void billboardVertex(VertexConsumer vertices, Matrix4f positionMatrix,
                                        Matrix3f normalMatrix, float x, float y, float z,
                                        float u, float v) {
        vertices.vertex(positionMatrix, x, y, z)
                .color(255, 255, 255, 255)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(normalMatrix, 0.0f, 0.0f, 1.0f)
                .next();
    }

    /**
     * Emits from a deterministic Fibonacci sphere instead of relying on the
     * block random-display callback. This gives every linker one controlled
     * cadence, evenly covers every direction, and prevents several render or
     * random-tick callbacks from creating a trail of near-identical motes.
     */
    private void emitSingularityParticle(ClientWorld world, BlockPos linkerPos,
                                         Vec3d localCenter) {
        if (emitterWorld != world) {
            emitterWorld = world;
            emitterStates.clear();
            orbitCache.clear();
            orbitVisualStates.clear();
        }

        BlockPos key = linkerPos.toImmutable();
        long now = world.getTime();
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
        Vec3d offset = new Vec3d(
                Math.cos(azimuth) * horizontal * radius,
                vertical * radius,
                Math.sin(azimuth) * horizontal * radius);
        Vec3d target = new Vec3d(linkerPos.getX(), linkerPos.getY(), linkerPos.getZ())
                .add(localCenter);

        var particleType = sample % 3 == 0
                ? TriStorageMod.CONVERGING_END_PARTICLE
                : TriStorageMod.CONVERGING_PORTAL_PARTICLE;
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
        if (linker.getWorld() == null) {
            return List.of();
        }
        BlockPos linkerPos = linker.getPos().toImmutable();
        long now = linker.getWorld().getTime();
        CachedOrbit cached = orbitCache.get(linkerPos);
        if (cached != null) {
            if (cached.corePos() == null) {
                if (now < cached.nextProbeTick()) {
                    return cached.items();
                }
            } else {
                BlockEntity entity = linker.getWorld().getBlockEntity(cached.corePos());
                if (entity instanceof StorageCoreBlockEntity core
                        && core.orbitSnapshotRevision() == cached.revision()) {
                    return cached.items();
                }
            }
        }

        StorageCoreBlockEntity core = StorageNetwork.findCore(linker.getWorld(), linkerPos);
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
            ItemStack copy = source.copy();
            copy.setCount(1);
            pool.add(copy);
        }
        orbitCache.put(linkerPos, new CachedOrbit(core.getPos().toImmutable(),
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
        BlockPos linkerPos = linker.getPos().toImmutable();
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
        return !left.isEmpty() && !right.isEmpty() && ItemStack.canCombine(left, right);
    }

    private static ItemStack singleCopy(ItemStack source) {
        ItemStack copy = source.copy();
        copy.setCount(1);
        return copy;
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private record CachedOrbit(BlockPos corePos, long revision, List<ItemStack> items,
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
