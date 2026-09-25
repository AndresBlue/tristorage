package com.andresblue.tristorage.recipe;

import com.andresblue.tristorage.TriStorageMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.recipe.RawShapedRecipe;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.world.World;

/** Prevents a terminal carrying block-entity data from being consumed by an upgrade recipe. */
public final class CleanTerminalUpgradeRecipe extends ShapedRecipe {
    private final String recipeGroup;
    private final CraftingRecipeCategory recipeCategory;
    private final RawShapedRecipe rawRecipe;
    private final ItemStack output;
    private final boolean notification;

    public CleanTerminalUpgradeRecipe(String group, CraftingRecipeCategory category,
                                      RawShapedRecipe raw, ItemStack result, boolean showNotification) {
        super(group, category, raw, result, showNotification);
        this.recipeGroup = group;
        this.recipeCategory = category;
        this.rawRecipe = raw;
        this.output = result;
        this.notification = showNotification;
    }

    @Override
    public boolean matches(CraftingRecipeInput input, World world) {
        if (!super.matches(input, world)) {
            return false;
        }

        for (int index = 0; index < input.size(); index++) {
            ItemStack stack = input.getStackInSlot(index);
            if (stack.isOf(TriStorageMod.TERMINAL.asItem())
                    && (stack.contains(DataComponentTypes.BLOCK_ENTITY_DATA)
                    || stack.contains(DataComponentTypes.CUSTOM_DATA))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public RecipeSerializer<? extends ShapedRecipe> getSerializer() {
        return TriStorageMod.CLEAN_TERMINAL_UPGRADE_RECIPE_SERIALIZER;
    }

    public static final class Serializer implements RecipeSerializer<CleanTerminalUpgradeRecipe> {
        private static final MapCodec<CleanTerminalUpgradeRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.optionalFieldOf("group", "").forGetter(recipe -> recipe.recipeGroup),
                CraftingRecipeCategory.CODEC.optionalFieldOf("category", CraftingRecipeCategory.MISC)
                        .forGetter(recipe -> recipe.recipeCategory),
                RawShapedRecipe.CODEC.forGetter(recipe -> recipe.rawRecipe),
                ItemStack.VALIDATED_CODEC.fieldOf("result").forGetter(recipe -> recipe.output),
                Codec.BOOL.optionalFieldOf("show_notification", true).forGetter(recipe -> recipe.notification)
        ).apply(instance, CleanTerminalUpgradeRecipe::new));
        private static final PacketCodec<RegistryByteBuf, CleanTerminalUpgradeRecipe> PACKET_CODEC =
                PacketCodecs.registryCodec(CODEC.codec());

        @Override
        public MapCodec<CleanTerminalUpgradeRecipe> codec() {
            return CODEC;
        }

        @Override
        public PacketCodec<RegistryByteBuf, CleanTerminalUpgradeRecipe> packetCodec() {
            return PACKET_CODEC;
        }
    }
}
