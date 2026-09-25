package com.andresblue.tristorage.screen;

import com.andresblue.tristorage.storage.ItemKey;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.ShapedRecipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure recipe allocation logic shared by JEI and EMI transfers. It solves
 * ingredient alternatives as a small constrained assignment problem, avoiding
 * the classic greedy failure where a broad tag consumes the only item accepted
 * by a narrower ingredient.
 */
public final class RecipeTransferPlanner {
    public static final int MAX_TRANSFER = Integer.MAX_VALUE;
    private static final int GRID_SIZE = 9;
    private static final int MAX_GRID_STACK = 64;

    private RecipeTransferPlanner() {
    }

    public static Plan plan(CraftingRecipe recipe, List<Resource> input,
                            int requestedCrafts) {
        if (recipe == null || !recipe.fits(3, 3)) {
            return null;
        }
        List<Requirement> requirements = requirements(recipe);
        if (requirements.isEmpty() || requirements.size() > GRID_SIZE) {
            return null;
        }

        List<Resource> resources = mergeResources(input);
        if (resources.isEmpty()) {
            return null;
        }

        int crafts;
        int[] assignment;
        if (requestedCrafts == MAX_TRANSFER) {
            int low = 1;
            int high = MAX_GRID_STACK;
            crafts = 0;
            assignment = null;
            while (low <= high) {
                int middle = (low + high) >>> 1;
                int[] candidate = solve(requirements, resources, middle);
                if (candidate != null) {
                    crafts = middle;
                    assignment = candidate;
                    low = middle + 1;
                } else {
                    high = middle - 1;
                }
            }
            if (crafts == 0) {
                return null;
            }
            // Binary search may have retained a valid assignment for the best
            // amount already, but recomputing keeps this invariant explicit.
            assignment = solve(requirements, resources, crafts);
        } else {
            crafts = Math.max(1, Math.min(MAX_GRID_STACK, requestedCrafts));
            assignment = solve(requirements, resources, crafts);
        }
        if (assignment == null) {
            return null;
        }

        List<ItemStack> grid = new ArrayList<>(GRID_SIZE);
        for (int slot = 0; slot < GRID_SIZE; slot++) {
            grid.add(ItemStack.EMPTY);
        }
        Map<ItemKey, Integer> consumption = new LinkedHashMap<>();
        for (int index = 0; index < requirements.size(); index++) {
            Resource resource = resources.get(assignment[index]);
            ItemStack placed = resource.template.copy();
            placed.setCount(crafts);
            grid.set(requirements.get(index).slot, placed);
            consumption.merge(ItemKey.frozen(resource.template), crafts, Integer::sum);
        }
        return new Plan(crafts, List.copyOf(grid), Map.copyOf(consumption));
    }

    /**
     * Computes a best-effort ingredient availability mask without mutating any
     * inventory. Bits are aligned with {@link CraftingRecipe#getIngredients()},
     * which lets recipe viewers highlight the exact missing inputs.
     *
     * <p>The partial solver is a capacitated bipartite matcher. It reports a
     * maximum feasible ingredient subset in polynomial time, including recipes
     * with overlapping tags, without enumerating up to 512 subsets.</p>
     */
    public static Availability availability(CraftingRecipe recipe,
                                            List<Resource> input) {
        if (recipe == null || !recipe.fits(3, 3)) {
            return Availability.EMPTY;
        }
        List<Requirement> requirements = requirements(recipe);
        if (requirements.isEmpty() || requirements.size() > GRID_SIZE) {
            return Availability.EMPTY;
        }
        int requiredMask = 0;
        for (Requirement requirement : requirements) {
            requiredMask |= 1 << requirement.inputIndex;
        }

        List<Resource> resources = mergeResources(input);
        if (resources.isEmpty()) {
            return new Availability(requiredMask, 0);
        }
        int[] assignment = solvePartial(requirements, resources, 1);
        int availableMask = 0;
        for (int index = 0; index < requirements.size(); index++) {
            if (assignment[index] >= 0) {
                availableMask |= 1 << requirements.get(index).inputIndex;
            }
        }
        return new Availability(requiredMask, availableMask);
    }

    private static List<Requirement> requirements(CraftingRecipe recipe) {
        List<Ingredient> ingredients = recipe.getIngredients();
        List<Requirement> requirements = new ArrayList<>();
        if (recipe instanceof ShapedRecipe shaped) {
            int width = shaped.getWidth();
            int height = shaped.getHeight();
            if (width > 3 || height > 3 || ingredients.size() < width * height) {
                return List.of();
            }
            for (int row = 0; row < height; row++) {
                for (int column = 0; column < width; column++) {
                    Ingredient ingredient = ingredients.get(column + row * width);
                    if (!ingredient.isEmpty()) {
                        requirements.add(new Requirement(column + row * width,
                                column + row * 3, ingredient));
                    }
                }
            }
        } else {
            int slot = 0;
            for (int inputIndex = 0; inputIndex < ingredients.size(); inputIndex++) {
                Ingredient ingredient = ingredients.get(inputIndex);
                if (!ingredient.isEmpty()) {
                    if (slot >= GRID_SIZE) {
                        return List.of();
                    }
                    requirements.add(new Requirement(inputIndex, slot++, ingredient));
                }
            }
        }
        return requirements;
    }

    private static List<Resource> mergeResources(List<Resource> input) {
        Map<ItemKey, MutableResource> merged = new LinkedHashMap<>();
        for (Resource resource : input) {
            if (resource == null || resource.template.isEmpty() || resource.available <= 0) {
                continue;
            }
            ItemKey key = ItemKey.frozen(resource.template);
            MutableResource current = merged.get(key);
            if (current == null) {
                ItemStack template = resource.template.copy();
                template.setCount(1);
                merged.put(key, new MutableResource(template, resource.available));
            } else {
                current.available = saturatedAdd(current.available, resource.available);
            }
        }
        List<Resource> result = new ArrayList<>(merged.size());
        for (MutableResource resource : merged.values()) {
            result.add(new Resource(resource.template, resource.available));
        }
        return result;
    }

    private static int[] solve(List<Requirement> requirements, List<Resource> resources,
                               int crafts) {
        int[] assignment = solvePartial(requirements, resources, crafts);
        for (int resource : assignment) {
            if (resource < 0) {
                return null;
            }
        }
        return assignment;
    }

    private static int[] solvePartial(List<Requirement> requirements,
                                      List<Resource> resources, int crafts) {
        List<CandidateRequirement> candidatesByRequirement = new ArrayList<>(
                requirements.size());
        for (int index = 0; index < requirements.size(); index++) {
            Requirement requirement = requirements.get(index);
            List<Integer> candidates = new ArrayList<>();
            for (int resource = 0; resource < resources.size(); resource++) {
                ItemStack stack = resources.get(resource).template;
                if (stack.getMaxCount() >= crafts && requirement.ingredient.test(stack)) {
                    candidates.add(resource);
                }
            }
            candidates.sort(Comparator.comparingLong(
                    resource -> -resources.get(resource).available));
            candidatesByRequirement.add(new CandidateRequirement(index, candidates));
        }
        List<CandidateRequirement> ordered = new ArrayList<>(candidatesByRequirement);
        ordered.sort(Comparator.comparingInt(value -> value.candidates.size()));
        int[] assignment = new int[requirements.size()];
        Arrays.fill(assignment, -1);
        int[] offsets = new int[resources.size() + 1];
        for (int resource = 0; resource < resources.size(); resource++) {
            int capacity = (int) Math.min(requirements.size(),
                    resources.get(resource).available / crafts);
            offsets[resource + 1] = offsets[resource] + capacity;
        }
        int[] matchedRequirement = new int[offsets[resources.size()]];
        Arrays.fill(matchedRequirement, -1);
        for (CandidateRequirement requirement : ordered) {
            assign(requirement.originalIndex, candidatesByRequirement, offsets,
                    matchedRequirement, assignment,
                    new boolean[matchedRequirement.length]);
        }
        return assignment;
    }

    private static boolean assign(int requirement,
                                  List<CandidateRequirement> candidates,
                                  int[] offsets,
                                  int[] matchedRequirement,
                                  int[] assignment,
                                  boolean[] visitedCapacitySlots) {
        for (int resource : candidates.get(requirement).candidates) {
            for (int capacitySlot = offsets[resource];
                 capacitySlot < offsets[resource + 1]; capacitySlot++) {
                if (visitedCapacitySlots[capacitySlot]) {
                    continue;
                }
                visitedCapacitySlots[capacitySlot] = true;
                int displaced = matchedRequirement[capacitySlot];
                if (displaced < 0 || assign(displaced, candidates, offsets,
                        matchedRequirement, assignment, visitedCapacitySlots)) {
                    matchedRequirement[capacitySlot] = requirement;
                    assignment[requirement] = resource;
                    return true;
                }
            }
        }
        return false;
    }

    private static long saturatedAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    public record Resource(ItemStack template, long available) {
    }

    public record Plan(int crafts, List<ItemStack> grid,
                       Map<ItemKey, Integer> consumption) {
    }

    public record Availability(int requiredMask, int availableMask) {
        private static final Availability EMPTY = new Availability(0, 0);

        public boolean canCraft() {
            return requiredMask != 0
                    && (availableMask & requiredMask) == requiredMask;
        }

        public boolean isAvailable(int inputIndex) {
            int bit = 1 << inputIndex;
            return (requiredMask & bit) == 0 || (availableMask & bit) != 0;
        }
    }

    private record Requirement(int inputIndex, int slot, Ingredient ingredient) {
    }

    private record CandidateRequirement(int originalIndex, List<Integer> candidates) {
    }

    private static final class MutableResource {
        private final ItemStack template;
        private long available;

        private MutableResource(ItemStack template, long available) {
            this.template = template;
            this.available = available;
        }
    }
}
