package com.andresblue.tristorage.client;

import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;

public final class LinkerRenderState extends BlockEntityRenderState {
    public Direction antennaDirection;
    public float ticks;
    public List<OrbitItem> orbitPool = new ArrayList<>();

    public record OrbitItem(ItemStack stack, ItemRenderState renderState) {
    }
}
