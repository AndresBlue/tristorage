package com.andresblue.tristorage;

import com.andresblue.tristorage.version.Stacks;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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

    /** Parses a crafting_shaped recipe written in the 1.20.1 data pack format. */
    public static ShapedRecipe shapedRecipe(String path, String json) {
        JsonObject object = JsonParser.parseString(json).getAsJsonObject();
        //? if >=1.21 {
        /*JsonObject result = object.getAsJsonObject("result");
        if (result.has("item")) {
            result.add("id", result.remove("item"));
        }
        return RecipeSerializer.SHAPED_RECIPE.codec().codec()
                .parse(JsonOps.INSTANCE, object).getOrThrow();
        *///?} else {
        return RecipeSerializer.SHAPED_RECIPE.fromJson(Ids.of("tristorage", path), object);
        //?}
    }

    public static ShapedRecipe shapedRecipe(int width, int height,
                                            NonNullList<Ingredient> pattern, ItemStack result) {
        //? if >=1.21 {
        /*return new ShapedRecipe("", CraftingBookCategory.MISC,
                new ShapedRecipePattern(width, height, pattern, Optional.empty()), result, true);
        *///?} else {
        return new ShapedRecipe(Ids.of("tristorage", "planner_test"), "",
                CraftingBookCategory.MISC, width, height, pattern, result, true);
        //?}
    }
}
