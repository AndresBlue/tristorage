package com.andresblue.tristorage.mixin;

//? if >=1.20.5 {
/*import com.mojang.serialization.Dynamic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/^*
 * Minecraft 1.20.5 turns an item's BlockEntityTag into the block_entity_data
 * component, which must name its block entity, and takes that name only from
 * the tag itself. Storage Cores dropped by TriStorage before 2.0 saved none,
 * so an upgraded world would discard them with everything they store. The
 * data fixer reads the item's old tag here; the missing id is added first.
 ^/
@Mixin(targets = "net.minecraft.util.datafix.fixes.ItemStackComponentizationFix$ItemStackData")
abstract class ItemStackDataMixin {
    private static final String CORE_BLOCK_ENTITY = "tristorage:storage_core";

    @Shadow
    @Final
    private String item;

    @Shadow
    Dynamic<?> tag;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void tristorage$nameCoreBlockEntity(CallbackInfo ci) {
        if (item.startsWith("tristorage:") && item.endsWith("_storage_core")) {
            tag = tag.update("BlockEntityTag", data -> data.get("id").result().isPresent()
                    ? data : data.set("id", data.createString(CORE_BLOCK_ENTITY)));
        }
    }
}
*///?}
