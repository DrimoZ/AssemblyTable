package com.drimoz.assembly.network;

import com.drimoz.assembly.core.tileentity.AssemblyTableBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class EnergySyncS2C {
    private final int energy;
    private final BlockPos pos;

    public EnergySyncS2C(int energy, BlockPos pos) {
        this.energy = energy;
        this.pos = pos;
    }

    public EnergySyncS2C(FriendlyByteBuf buf) {
        this.energy = buf.readInt();
        this.pos = buf.readBlockPos();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(energy);
        buf.writeBlockPos(pos);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            if(Minecraft.getInstance().level.getBlockEntity(pos) instanceof AssemblyTableBlockEntity blockEntity) {
                blockEntity.setClientCurrentEnergy(energy);
            }
        });
        context.get().setPacketHandled(true);
    }
}
