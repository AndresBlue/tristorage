package com.andresblue.tristorage.recipe;

import java.util.List;
import java.util.function.Function;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
//? if >=1.21 {
/*import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
*///?} else {
import com.google.gson.JsonObject;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
//?}

/**
 * A shaped recipe with TriStorage behavior on top. Its serializer reads the
 * same JSON as {@code minecraft:crafting_shaped}; subclasses only decide which
 * grids are accepted and how the result is finished.
 */
public abstract class WrappedShapedRecipe extends ShapedRecipe {
    private final Parts parts;

    protected WrappedShapedRecipe(Parts parts) {
        //? if >=1.21 {
        /*super(parts.group(), parts.category(), parts.pattern(), parts.result(),
                parts.showNotification());
        *///?} else {
        super(parts.base().getId(), parts.base().getGroup(), parts.base().category(),
                parts.base().getWidth(), parts.base().getHeight(),
                parts.base().getIngredients(),
                parts.base().getResultItem(RegistryAccess.EMPTY).copy(),
                parts.base().showNotification());
        //?}
        this.parts = parts;
    }

    Parts parts() {
        return parts;
    }

    /** An extra condition on the grid besides the shaped pattern. */
    protected boolean accepts(List<ItemStack> grid) {
        return true;
    }

    /** Adjusts the crafted stack using the grid it was crafted from. */
    protected void finish(ItemStack result, List<ItemStack> grid) {
    }

    //? if >=1.21 {
    /*@Override
    public boolean matches(CraftingInput input, Level world) {
        return super.matches(input, world) && accepts(input.items());
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack result = super.assemble(input, registries);
        finish(result, input.items());
        return result;
    }
    *///?} else {
    @Override
    public boolean matches(CraftingContainer input, Level world) {
        return super.matches(input, world) && accepts(input.getItems());
    }

    @Override
    public ItemStack assemble(CraftingContainer input, RegistryAccess registries) {
        ItemStack result = super.assemble(input, registries);
        finish(result, input.getItems());
        return result;
    }
    //?}

    //? if >=1.21 {
    /*/^* What a shaped recipe is made of; vanilla keeps these fields package-private. ^/
    public record Parts(String group, CraftingBookCategory category, ShapedRecipePattern pattern,
                        ItemStack result, boolean showNotification) {
        static final MapCodec<Parts> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.optionalFieldOf("group", "").forGetter(Parts::group),
                CraftingBookCategory.CODEC.fieldOf("category")
                        .orElse(CraftingBookCategory.MISC).forGetter(Parts::category),
                ShapedRecipePattern.MAP_CODEC.forGetter(Parts::pattern),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(Parts::result),
                Codec.BOOL.optionalFieldOf("show_notification", true)
                        .forGetter(Parts::showNotification)
        ).apply(instance, Parts::new));

        static final StreamCodec<RegistryFriendlyByteBuf, Parts> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, Parts::group,
                        CraftingBookCategory.STREAM_CODEC, Parts::category,
                        ShapedRecipePattern.STREAM_CODEC, Parts::pattern,
                        ItemStack.STREAM_CODEC, Parts::result,
                        ByteBufCodecs.BOOL, Parts::showNotification,
                        Parts::new);
    }
    *///?} else {
    // The vanilla shaped recipe parsed from the same JSON.
    public record Parts(ShapedRecipe base) {
    }
    //?}

    public static class Serializer<T extends WrappedShapedRecipe> implements RecipeSerializer<T> {
        private final Function<Parts, T> factory;
        //? if >=1.21 {
        /*private final MapCodec<T> codec;
        private final StreamCodec<RegistryFriendlyByteBuf, T> streamCodec;
        *///?}

        protected Serializer(Function<Parts, T> factory) {
            this.factory = factory;
            //? if >=1.21 {
            /*codec = Parts.CODEC.xmap(factory, WrappedShapedRecipe::parts);
            streamCodec = Parts.STREAM_CODEC.map(factory, WrappedShapedRecipe::parts);
            *///?}
        }

        //? if >=1.21 {
        /*@Override
        public MapCodec<T> codec() {
            return codec;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, T> streamCodec() {
            return streamCodec;
        }
        *///?} else {
        @Override
        public T fromJson(ResourceLocation id, JsonObject json) {
            return factory.apply(new Parts(RecipeSerializer.SHAPED_RECIPE.fromJson(id, json)));
        }

        @Override
        public T fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            return factory.apply(new Parts(RecipeSerializer.SHAPED_RECIPE.fromNetwork(id, buf)));
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, T recipe) {
            RecipeSerializer.SHAPED_RECIPE.toNetwork(buf, recipe);
        }
        //?}
    }
}
