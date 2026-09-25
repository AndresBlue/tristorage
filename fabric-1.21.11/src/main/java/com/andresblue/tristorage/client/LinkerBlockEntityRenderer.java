package com.andresblue.tristorage.client;

import com.andresblue.tristorage.TriStorageMod;
import com.andresblue.tristorage.blockentity.LinkerBlockEntity;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.SimpleParticleType;
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

/** The complete 1.9 dimensional stand effect, adapted to render-state commands. */
public final class LinkerBlockEntityRenderer
        implements BlockEntityRenderer<LinkerBlockEntity, LinkerRenderState> {
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int MAX_ORBIT_ITEMS = 8;
    private static final double ORBIT_ITEM_SCALE = 0.12;
    private static final double ABSORBED_ITEM_SCALE = 0.14;
    private static final double SINGULARITY_DISTANCE = 1.03125;
    private static final int SINGULARITY_FRAMES = 8;
    private static final double SINGULARITY_FRAME_TICKS = 4.0;
    private static final int PARTICLE_DIRECTIONS = 48;
    private static final long PARTICLE_INTERVAL_TICKS = 10L;
    private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));
    private static final Identifier SINGULARITY_TEXTURE = TriStorageMod.id(
            "textures/block/dimensional_singularity_billboard_animated.png");

    private final ItemModelManager itemModelManager;
    private final Map<BlockPos, OrbitVisualState> orbitVisualStates = boundedMap();
    private final Map<BlockPos, EmitterState> emitterStates = boundedMap();
    private ClientWorld emitterWorld;

    public LinkerBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
        itemModelManager = context.itemModelManager();
    }

    private static <T> Map<BlockPos, T> boundedMap() {
        return new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<BlockPos, T> eldest) {
                return size() > 256;
            }
        };
    }

    @Override
    public LinkerRenderState createRenderState() {
        return new LinkerRenderState();
    }

    @Override
    public void updateRenderState(LinkerBlockEntity linker, LinkerRenderState state,
                                  float tickProgress, Vec3d cameraPos,
                                  ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlay) {
        BlockEntityRenderer.super.updateRenderState(linker, state, tickProgress,
                cameraPos, crumblingOverlay);
        state.antennaDirection = linker.isAntennaActive()
                ? linker.antennaMount().direction() : null;
        state.ticks = (linker.getWorld() == null ? 0L : linker.getWorld().getTime()) + tickProgress;
        state.orbitPool = new ArrayList<>();
        if (!(linker.getWorld() instanceof ClientWorld world) || state.antennaDirection == null) return;

        emitSingularityParticle(world, linker.getPos(), state.antennaDirection);
        int seed = linker.getPos().hashCode();
        for (ItemStack source : linker.orbitPreview()) {
            if (source.isEmpty()) continue;
            ItemStack stack = source.copyWithCount(1);
            ItemRenderState itemState = new ItemRenderState();
            // FIXED is the world-safe counterpart to 1.20.1's GUI billboard here.
            // In 1.21, GUI state may fall back to an oversized/icon representation,
            // while GROUND applies a second reduction that makes these tiny orbiters
            // effectively disappear. FIXED keeps each real item/block model intact.
            itemModelManager.clearAndUpdate(itemState, stack, ItemDisplayContext.FIXED,
                    world, null, seed++);
            if (!itemState.isEmpty()) {
                state.orbitPool.add(new LinkerRenderState.OrbitItem(stack, itemState));
            }
        }
    }

    @Override
    public void render(LinkerRenderState state, MatrixStack matrices,
                       OrderedRenderCommandQueue queue, CameraRenderState cameraState) {
        Direction direction = state.antennaDirection;
        if (direction == null) return;

        Vec3d axis = new Vec3d(direction.getOffsetX(), direction.getOffsetY(), direction.getOffsetZ());
        Vec3d reference = direction.getAxis().isVertical()
                ? new Vec3d(1.0, 0.0, 0.0) : new Vec3d(0.0, 1.0, 0.0);
        Vec3d tangent = axis.crossProduct(reference).normalize();
        Vec3d bitangent = axis.crossProduct(tangent).normalize();
        Vec3d center = new Vec3d(0.5, 0.5, 0.5)
                .add(axis.multiply(SINGULARITY_DISTANCE));

        renderSingularity(center, state.ticks, matrices, queue, cameraState);
        renderOrbitItems(state, center, axis, tangent, bitangent, matrices, queue, cameraState);
    }

    private void renderOrbitItems(LinkerRenderState state, Vec3d center, Vec3d axis,
                                  Vec3d tangent, Vec3d bitangent, MatrixStack matrices,
                                  OrderedRenderCommandQueue queue, CameraRenderState cameraState) {
        List<LinkerRenderState.OrbitItem> items = orbitItemsForFrame(state);
        for (int index = 0; index < items.size(); index++) {
            boolean absorbed = index >= Math.max(1, items.size() / 2);
            double phase = index * 2.173;
            double radius;
            double scale;
            double angle;
            if (absorbed) {
                double cycle = positiveModulo(state.ticks * 0.012 + index * 0.271, 1.0);
                double eased = cycle * cycle * (3.0 - 2.0 * cycle);
                radius = 0.52 * (1.0 - eased) + 0.018;
                double appear = Math.min(1.0, cycle / 0.1);
                scale = ABSORBED_ITEM_SCALE * appear * Math.max(0.08, 1.0 - eased);
                angle = phase + cycle * Math.PI * 5.0;
            } else {
                radius = 0.31 + Math.sin(state.ticks * 0.045 + phase) * 0.035;
                scale = ORBIT_ITEM_SCALE;
                angle = phase + state.ticks * (index % 2 == 0 ? 0.055 : -0.048);
            }
            double wobble = Math.sin(state.ticks * 0.037 + phase) * 0.055;
            Vec3d position = center.add(tangent.multiply(Math.cos(angle) * radius))
                    .add(bitangent.multiply(Math.sin(angle) * radius))
                    .add(axis.multiply(wobble));

            matrices.push();
            matrices.translate(position.x, position.y, position.z);
            matrices.multiply(cameraState.orientation);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(
                    state.ticks * 1.8f + index * 41.0f));
            matrices.scale((float) scale, (float) scale, (float) scale);
            items.get(index).renderState().render(matrices, queue,
                    FULL_BRIGHT, OverlayTexture.DEFAULT_UV, 0);
            matrices.pop();
        }
    }

    private static void renderSingularity(Vec3d center, double time, MatrixStack matrices,
                                          OrderedRenderCommandQueue queue,
                                          CameraRenderState cameraState) {
        int frame = Math.floorMod((int) (time / SINGULARITY_FRAME_TICKS), SINGULARITY_FRAMES);
        float u0 = frame / (float) SINGULARITY_FRAMES;
        float u1 = (frame + 1) / (float) SINGULARITY_FRAMES;
        float pulse = 0.38f + (float) Math.sin(time * 0.16) * 0.008f;

        matrices.push();
        matrices.translate(center.x, center.y, center.z);
        matrices.multiply(cameraState.orientation);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0f));
        matrices.scale(pulse, pulse, pulse);
        queue.submitCustom(matrices, RenderLayers.entityCutoutNoCull(SINGULARITY_TEXTURE),
                (entry, vertex) -> {
                    vertex.vertex(entry, -0.5f, -0.5f, 0.0f).color(255, 255, 255, 255)
                            .texture(u0, 1.0f).overlay(OverlayTexture.DEFAULT_UV)
                            .light(FULL_BRIGHT).normal(entry, 0.0f, 0.0f, 1.0f);
                    vertex.vertex(entry, 0.5f, -0.5f, 0.0f).color(255, 255, 255, 255)
                            .texture(u1, 1.0f).overlay(OverlayTexture.DEFAULT_UV)
                            .light(FULL_BRIGHT).normal(entry, 0.0f, 0.0f, 1.0f);
                    vertex.vertex(entry, 0.5f, 0.5f, 0.0f).color(255, 255, 255, 255)
                            .texture(u1, 0.0f).overlay(OverlayTexture.DEFAULT_UV)
                            .light(FULL_BRIGHT).normal(entry, 0.0f, 0.0f, 1.0f);
                    vertex.vertex(entry, -0.5f, 0.5f, 0.0f).color(255, 255, 255, 255)
                            .texture(u0, 0.0f).overlay(OverlayTexture.DEFAULT_UV)
                            .light(FULL_BRIGHT).normal(entry, 0.0f, 0.0f, 1.0f);
                });
        matrices.pop();
    }

    private void emitSingularityParticle(ClientWorld world, BlockPos linkerPos, Direction direction) {
        if (emitterWorld != world) {
            emitterWorld = world;
            emitterStates.clear();
            orbitVisualStates.clear();
        }
        BlockPos key = linkerPos.toImmutable();
        long now = world.getTime();
        EmitterState emitter = emitterStates.computeIfAbsent(key, ignored ->
                new EmitterState(now + Math.floorMod(key.asLong(), PARTICLE_INTERVAL_TICKS),
                        Math.floorMod(key.hashCode(), PARTICLE_DIRECTIONS)));
        if (now < emitter.nextEmissionTick) return;

        emitter.nextEmissionTick = now + PARTICLE_INTERVAL_TICKS;
        int sample = Math.floorMod(emitter.sequence++, PARTICLE_DIRECTIONS);
        int directionIndex = Math.floorMod(sample + emitter.directionOffset, PARTICLE_DIRECTIONS);
        double vertical = 1.0 - 2.0 * ((directionIndex + 0.5) / PARTICLE_DIRECTIONS);
        double horizontal = Math.sqrt(Math.max(0.0, 1.0 - vertical * vertical));
        double azimuth = directionIndex * GOLDEN_ANGLE + emitter.directionOffset * 0.173;
        double radius = 0.62 + 0.16 * (0.5 + 0.5
                * Math.sin(directionIndex * 1.731 + emitter.directionOffset));
        Vec3d offset = new Vec3d(Math.cos(azimuth) * horizontal * radius,
                vertical * radius, Math.sin(azimuth) * horizontal * radius);
        Vec3d target = Vec3d.of(linkerPos).add(0.5, 0.5, 0.5)
                .add(new Vec3d(direction.getOffsetX(), direction.getOffsetY(), direction.getOffsetZ())
                        .multiply(SINGULARITY_DISTANCE));
        SimpleParticleType type = sample % 3 == 0
                ? TriStorageMod.CONVERGING_END_PARTICLE : TriStorageMod.CONVERGING_PORTAL_PARTICLE;
        world.addParticleClient(type, target.x + offset.x, target.y + offset.y,
                target.z + offset.z, target.x, target.y, target.z);
    }

    private List<LinkerRenderState.OrbitItem> orbitItemsForFrame(LinkerRenderState state) {
        List<LinkerRenderState.OrbitItem> pool = state.orbitPool;
        BlockPos linkerPos = state.pos.toImmutable();
        if (pool.isEmpty()) {
            orbitVisualStates.remove(linkerPos);
            return List.of();
        }

        OrbitVisualState visual = orbitVisualStates.computeIfAbsent(linkerPos,
                ignored -> new OrbitVisualState(linkerPos.asLong()));
        int targetSize = Math.min(MAX_ORBIT_ITEMS, pool.size());
        while (visual.items.size() > targetSize) visual.items.removeLast();
        for (int index = 0; index < visual.items.size(); index++) {
            ItemStack current = visual.items.get(index);
            if (!containsItem(pool, current)) {
                visual.items.set(index, chooseReplacement(pool, visual, index, current));
            }
        }
        while (visual.items.size() < targetSize) {
            int index = visual.items.size();
            visual.items.add(chooseReplacement(pool, visual, index, ItemStack.EMPTY));
            visual.absorptionCycles[index] = Long.MIN_VALUE;
        }

        int absorbedStart = Math.max(1, visual.items.size() / 2);
        for (int index = absorbedStart; index < visual.items.size(); index++) {
            long cycle = (long) Math.floor(state.ticks * 0.012 + index * 0.271);
            long previous = visual.absorptionCycles[index];
            if (previous == Long.MIN_VALUE) visual.absorptionCycles[index] = cycle;
            else if (cycle != previous) {
                visual.items.set(index, chooseReplacement(pool, visual, index,
                        visual.items.get(index)));
                visual.absorptionCycles[index] = cycle;
            }
        }

        List<LinkerRenderState.OrbitItem> rendered = new ArrayList<>(visual.items.size());
        for (ItemStack wanted : visual.items) {
            pool.stream().filter(candidate -> sameItem(candidate.stack(), wanted))
                    .findFirst().ifPresent(rendered::add);
        }
        return rendered;
    }

    private static ItemStack chooseReplacement(List<LinkerRenderState.OrbitItem> pool,
                                               OrbitVisualState state, int slot,
                                               ItemStack previous) {
        long mixed = mix64(state.seed + state.replacementSequence++ * -7046029254386353131L
                + slot * -4417276706812531889L);
        int start = Math.floorMod((int) (mixed ^ mixed >>> 32), pool.size());
        for (int offset = 0; offset < pool.size(); offset++) {
            ItemStack candidate = pool.get((start + offset) % pool.size()).stack();
            if (!sameItem(candidate, previous)
                    && !isAssignedElsewhere(state.items, candidate, slot)) return candidate.copyWithCount(1);
        }
        for (int offset = 0; offset < pool.size(); offset++) {
            ItemStack candidate = pool.get((start + offset) % pool.size()).stack();
            if (!sameItem(candidate, previous)) return candidate.copyWithCount(1);
        }
        return pool.get(start).stack().copyWithCount(1);
    }

    private static boolean containsItem(List<LinkerRenderState.OrbitItem> pool, ItemStack target) {
        return pool.stream().anyMatch(candidate -> sameItem(candidate.stack(), target));
    }

    private static boolean isAssignedElsewhere(List<ItemStack> assigned,
                                               ItemStack candidate, int slot) {
        for (int index = 0; index < assigned.size(); index++) {
            if (index != slot && sameItem(assigned.get(index), candidate)) return true;
        }
        return false;
    }

    private static boolean sameItem(ItemStack left, ItemStack right) {
        return !left.isEmpty() && !right.isEmpty()
                && ItemStack.areItemsAndComponentsEqual(left, right);
    }

    private static double positiveModulo(double value, double modulus) {
        double result = value % modulus;
        return result < 0.0 ? result + modulus : result;
    }

    private static long mix64(long value) {
        value = (value ^ value >>> 30) * -4658895280553007687L;
        value = (value ^ value >>> 27) * -7723592293110705685L;
        return value ^ value >>> 31;
    }

    @Override
    public boolean rendersOutsideBoundingBox() {
        return true;
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
}
