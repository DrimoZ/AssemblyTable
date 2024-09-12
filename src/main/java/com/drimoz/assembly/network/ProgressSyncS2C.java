package com.drimoz.assembly.network;

import com.drimoz.assembly.core.tileentity.AssemblyTableBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ProgressSyncS2C {
    private final int progress, total;
    private final BlockPos pos;

    public ProgressSyncS2C(int progress, int total, BlockPos pos) {
        this.progress = progress;
        this.total = total;
        this.pos = pos;
    }

    public ProgressSyncS2C(FriendlyByteBuf buf) {
        this.progress = buf.readInt();
        this.total = buf.readInt();
        this.pos = buf.readBlockPos();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(progress);
        buf.writeInt(total);
        buf.writeBlockPos(pos);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            if(Minecraft.getInstance().level.getBlockEntity(pos) instanceof AssemblyTableBlockEntity blockEntity) {
                blockEntity.setClientCurrentProgress(progress);
                blockEntity.setClientTotalProgress(total);
            }
        });
        context.get().setPacketHandled(true);
    }
}
