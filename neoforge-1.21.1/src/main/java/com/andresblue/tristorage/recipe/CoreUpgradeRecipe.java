package com.andresblue.tristorage.recipe;

import com.andresblue.tristorage.TriStorageMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Shaped core upgrade that carries the old core's portable storage component into the new tier. */
public final class CoreUpgradeRecipe extends ShapedRecipe {
    private final String recipeGroup;
    private final CraftingBookCategory recipeCategory;
    private final ShapedRecipePattern recipePattern;
    private final ItemStack recipeResult;
    private final boolean notification;
    public CoreUpgradeRecipe(String group, CraftingBookCategory category, ShapedRecipePattern pattern, ItemStack result, boolean notification) {
        super(group, category, pattern, result, notification); this.recipeGroup=group; this.recipeCategory=category; this.recipePattern=pattern; this.recipeResult=result; this.notification=notification;
    }
    @Override public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack crafted = super.assemble(input, registries);
        for (int i=0;i<input.size();i++) { ItemStack source=input.getItem(i); if (source.is(TriStorageMod.IRON_CORE.get().asItem()) || source.is(TriStorageMod.DIAMOND_CORE.get().asItem()) || source.is(TriStorageMod.BLAZE_CORE.get().asItem())) { CustomData data=source.get(DataComponents.CUSTOM_DATA); if(data!=null) crafted.set(DataComponents.CUSTOM_DATA, data); break; } }
        return crafted;
    }
    @Override public RecipeSerializer<?> getSerializer() { return TriStorageMod.CORE_UPGRADE_RECIPE_SERIALIZER.get(); }
    public static final class Serializer implements RecipeSerializer<CoreUpgradeRecipe> {
        private static final MapCodec<CoreUpgradeRecipe> CODEC=RecordCodecBuilder.mapCodec(i->i.group(Codec.STRING.optionalFieldOf("group","").forGetter(r->r.recipeGroup),CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(r->r.recipeCategory),ShapedRecipePattern.MAP_CODEC.forGetter(r->r.recipePattern),ItemStack.STRICT_CODEC.fieldOf("result").forGetter(r->r.recipeResult),Codec.BOOL.optionalFieldOf("show_notification",true).forGetter(r->r.notification)).apply(i,CoreUpgradeRecipe::new));
        private static final StreamCodec<RegistryFriendlyByteBuf,CoreUpgradeRecipe> STREAM=StreamCodec.of((buf,r)->{buf.writeUtf(r.recipeGroup);buf.writeEnum(r.recipeCategory);ShapedRecipePattern.STREAM_CODEC.encode(buf,r.recipePattern);ItemStack.STREAM_CODEC.encode(buf,r.recipeResult);buf.writeBoolean(r.notification);},buf->new CoreUpgradeRecipe(buf.readUtf(),buf.readEnum(CraftingBookCategory.class),ShapedRecipePattern.STREAM_CODEC.decode(buf),ItemStack.STREAM_CODEC.decode(buf),buf.readBoolean()));
        @Override public MapCodec<CoreUpgradeRecipe> codec(){return CODEC;} @Override public StreamCodec<RegistryFriendlyByteBuf,CoreUpgradeRecipe> streamCodec(){return STREAM;}
    }
}
