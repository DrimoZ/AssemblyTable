package com.drimoz.assembly.network;

import com.drimoz.assembly.core.tileentity.AssemblyTableBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

public class SelectedRecipesSyncS2C {
    private final List<Integer> selectedRecipeSlots;
    private final BlockPos pos;

    public SelectedRecipesSyncS2C(List<Integer> selectedRecipeSlots, BlockPos pos) {
        this.selectedRecipeSlots = selectedRecipeSlots;
        this.pos = pos;
    }

    public SelectedRecipesSyncS2C(FriendlyByteBuf buf) {
        this.selectedRecipeSlots = buf.readList(FriendlyByteBuf::readVarInt);
        this.pos = buf.readBlockPos();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeCollection(selectedRecipeSlots, FriendlyByteBuf::writeVarInt);
        buf.writeBlockPos(pos);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            if(Minecraft.getInstance().level.getBlockEntity(pos) instanceof AssemblyTableBlockEntity blockEntity) {
                blockEntity.setClientSelectedRecipeSlots(selectedRecipeSlots);
            }
        });
        context.get().setPacketHandled(true);
    }
}
