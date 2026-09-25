package com.andresblue.tristorage.recipe;

import com.andresblue.tristorage.TriStorageMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** The crafting-terminal upgrade refuses a terminal carrying a serialized block entity. */
public final class CleanTerminalUpgradeRecipe extends ShapedRecipe {
    private final String recipeGroup; private final CraftingBookCategory recipeCategory; private final ShapedRecipePattern recipePattern; private final ItemStack recipeResult; private final boolean notification;
    public CleanTerminalUpgradeRecipe(String group,CraftingBookCategory category,ShapedRecipePattern pattern,ItemStack result,boolean notification){super(group,category,pattern,result,notification);recipeGroup=group;recipeCategory=category;recipePattern=pattern;recipeResult=result;this.notification=notification;}
    @Override public boolean matches(CraftingInput input, Level level){if(!super.matches(input,level))return false;for(int i=0;i<input.size();i++){ItemStack s=input.getItem(i);if(s.is(TriStorageMod.TERMINAL.get().asItem())&&(s.has(DataComponents.BLOCK_ENTITY_DATA)||s.has(DataComponents.CUSTOM_DATA)))return false;}return true;}
    @Override public RecipeSerializer<?> getSerializer(){return TriStorageMod.CLEAN_TERMINAL_UPGRADE_RECIPE_SERIALIZER.get();}
    public static final class Serializer implements RecipeSerializer<CleanTerminalUpgradeRecipe>{
        private static final MapCodec<CleanTerminalUpgradeRecipe> CODEC=RecordCodecBuilder.mapCodec(i->i.group(Codec.STRING.optionalFieldOf("group","").forGetter(r->r.recipeGroup),CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(r->r.recipeCategory),ShapedRecipePattern.MAP_CODEC.forGetter(r->r.recipePattern),ItemStack.STRICT_CODEC.fieldOf("result").forGetter(r->r.recipeResult),Codec.BOOL.optionalFieldOf("show_notification",true).forGetter(r->r.notification)).apply(i,CleanTerminalUpgradeRecipe::new));
        private static final StreamCodec<RegistryFriendlyByteBuf,CleanTerminalUpgradeRecipe> STREAM=StreamCodec.of((buf,r)->{buf.writeUtf(r.recipeGroup);buf.writeEnum(r.recipeCategory);ShapedRecipePattern.STREAM_CODEC.encode(buf,r.recipePattern);ItemStack.STREAM_CODEC.encode(buf,r.recipeResult);buf.writeBoolean(r.notification);},buf->new CleanTerminalUpgradeRecipe(buf.readUtf(),buf.readEnum(CraftingBookCategory.class),ShapedRecipePattern.STREAM_CODEC.decode(buf),ItemStack.STREAM_CODEC.decode(buf),buf.readBoolean()));
        @Override public MapCodec<CleanTerminalUpgradeRecipe> codec(){return CODEC;} @Override public StreamCodec<RegistryFriendlyByteBuf,CleanTerminalUpgradeRecipe> streamCodec(){return STREAM;}
    }
}
