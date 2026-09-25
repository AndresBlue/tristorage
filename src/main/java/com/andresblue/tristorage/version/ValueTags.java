package com.andresblue.tristorage.version;

//? if >=1.21.6 {
/*import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/^*
 * Since Minecraft 1.21.6 block entities save through ValueOutput and load
 * from ValueInput. TriStorage still builds its data as NBT; these move whole
 * tags across that boundary.
 ^/
public final class ValueTags {
    private ValueTags() {
    }

    public static void write(ValueOutput output, CompoundTag tag) {
        for (String key : tag.keySet()) {
            output.store(key, ExtraCodecs.NBT, tag.get(key));
        }
    }

    /^* The given keys of the input, as a tag; missing keys stay absent. ^/
    public static CompoundTag read(ValueInput input, String... keys) {
        CompoundTag tag = new CompoundTag();
        for (String key : keys) {
            input.read(key, ExtraCodecs.NBT).ifPresent(value -> tag.put(key, value));
        }
        return tag;
    }
}
*///?}
