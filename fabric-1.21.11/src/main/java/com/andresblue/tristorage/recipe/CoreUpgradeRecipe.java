package com.andresblue.tristorage.recipe;

import com.andresblue.tristorage.TriStorageMod;
import com.andresblue.tristorage.storage.CoreStorageData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.recipe.RawShapedRecipe;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper;

/** A shaped upgrade recipe that carries the previous core's storage component forward. */
public final class CoreUpgradeRecipe extends ShapedRecipe {
    private final String recipeGroup;
    private final CraftingRecipeCategory recipeCategory;
    private final RawShapedRecipe rawRecipe;
    private final ItemStack output;
    private final boolean notification;

    public CoreUpgradeRecipe(String group, CraftingRecipeCategory category,
                             RawShapedRecipe raw, ItemStack result, boolean showNotification) {
        super(group, category, raw, result, showNotification);
        this.recipeGroup = group;
        this.recipeCategory = category;
        this.rawRecipe = raw;
        this.output = result;
        this.notification = showNotification;
    }

    @Override
    public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup registries) {
        ItemStack crafted = super.craft(input, registries);
        for (int index = 0; index < input.size(); index++) {
            CoreStorageData data = input.getStackInSlot(index).get(TriStorageMod.CORE_STORAGE);
            if (data != null) {
                crafted.set(TriStorageMod.CORE_STORAGE, data);
                break;
            }
        }
        return crafted;
    }

    @Override
    public RecipeSerializer<? extends ShapedRecipe> getSerializer() {
        return TriStorageMod.CORE_UPGRADE_RECIPE_SERIALIZER;
    }

    public static final class Serializer implements RecipeSerializer<CoreUpgradeRecipe> {
        private static final MapCodec<CoreUpgradeRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.optionalFieldOf("group", "").forGetter(recipe -> recipe.recipeGroup),
                CraftingRecipeCategory.CODEC.optionalFieldOf("category", CraftingRecipeCategory.MISC)
                        .forGetter(recipe -> recipe.recipeCategory),
                RawShapedRecipe.CODEC.forGetter(recipe -> recipe.rawRecipe),
                ItemStack.VALIDATED_CODEC.fieldOf("result").forGetter(recipe -> recipe.output),
                Codec.BOOL.optionalFieldOf("show_notification", true).forGetter(recipe -> recipe.notification)
        ).apply(instance, CoreUpgradeRecipe::new));
        private static final PacketCodec<RegistryByteBuf, CoreUpgradeRecipe> PACKET_CODEC =
                PacketCodecs.registryCodec(CODEC.codec());

        @Override
        public MapCodec<CoreUpgradeRecipe> codec() {
            return CODEC;
        }

        @Override
        public PacketCodec<RegistryByteBuf, CoreUpgradeRecipe> packetCodec() {
            return PACKET_CODEC;
        }
    }
}
