package com.andresblue.tristorage;

import com.andresblue.tristorage.version.Stacks;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
//? if >=1.21 {
/*import com.mojang.serialization.JsonOps;
import java.util.Optional;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
*///?} else {
import com.andresblue.tristorage.version.Ids;
//?}

/** Builds stacks and recipes the same way on every Minecraft version under test. */
public final class TestStacks {
    private TestStacks() {
    }

    /** Changes the stack's custom data in place. */
    public static void edit(ItemStack stack, Consumer<CompoundTag> change) {
        CompoundTag tag = Stacks.customData(stack);
        change.accept(tag);
        Stacks.setCustomData(stack, tag);
    }

    public static CompoundTag data(ItemStack stack) {
        return Stacks.customData(stack);
    }

    /** The built-in registries, enough to save stacks without a server. */
    public static HolderLookup.Provider registries() {
        return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    /**
     * Parses a crafting_shaped recipe written in the 1.20.1 data pack format,
     * converted the way the build converts the mod's own recipes.
     */
    public static ShapedRecipe shapedRecipe(String path, String json) {
        JsonObject object = JsonParser.parseString(json).getAsJsonObject();
        //? if >=1.21 {
        /*JsonObject result = object.getAsJsonObject("result");
        if (result.has("item")) {
            result.add("id", result.remove("item"));
        }
        //? if >=1.21.2 {
        /^JsonObject key = object.getAsJsonObject("key");
        for (Map.Entry<String, JsonElement> entry : List.copyOf(key.entrySet())) {
            JsonObject ingredient = entry.getValue().getAsJsonObject();
            key.add(entry.getKey(), new JsonPrimitive(ingredient.get("item").getAsString()));
        }
        ^///?}
        // Ingredients resolve items through the registries, as the game does.
        return RecipeSerializer.SHAPED_RECIPE.codec().codec()
                .parse(registries().createSerializationContext(JsonOps.INSTANCE), object)
                .getOrThrow();
        *///?} else {
        return RecipeSerializer.SHAPED_RECIPE.fromJson(Ids.of("tristorage", path), object);
        //?}
    }

    /** A shaped recipe from its pattern row by row; null or missing entries are empty. */
    public static ShapedRecipe shapedRecipe(int width, int height,
                                            List<Ingredient> ingredients, ItemStack result) {
        //? if >=1.21.2 {
        /*List<Optional<Ingredient>> pattern = new ArrayList<>();
        for (int index = 0; index < width * height; index++) {
            pattern.add(Optional.ofNullable(index < ingredients.size() ? ingredients.get(index) : null));
        }
        return new ShapedRecipe("", CraftingBookCategory.MISC,
                new ShapedRecipePattern(width, height, pattern, Optional.empty()), result, true);
        *///?} elif >=1.21 {
        /*return new ShapedRecipe("", CraftingBookCategory.MISC, new ShapedRecipePattern(
                width, height, grid(width, height, ingredients), Optional.empty()), result, true);
        *///?} else {
        return new ShapedRecipe(Ids.of("tristorage", "planner_test"), "",
                CraftingBookCategory.MISC, width, height, grid(width, height, ingredients),
                result, true);
        //?}
    }

    //? if <1.21.2 {
    private static NonNullList<Ingredient> grid(int width, int height,
                                                List<Ingredient> ingredients) {
        NonNullList<Ingredient> pattern = NonNullList.withSize(width * height, Ingredient.EMPTY);
        for (int index = 0; index < ingredients.size(); index++) {
            if (ingredients.get(index) != null) {
                pattern.set(index, ingredients.get(index));
            }
        }
        return pattern;
    }
    //?}
}
