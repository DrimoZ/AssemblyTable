package com.drimoz.assembly.network;

import com.drimoz.assembly.Assembly;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class AssemblyNetwork {
    private static SimpleChannel INSTANCE;

    private static int packetId = 0;
    private static int id() {
        return packetId++;
    }

    public static void init() {
        SimpleChannel net = NetworkRegistry.ChannelBuilder
                .named(new ResourceLocation(Assembly.MOD_ID, "network"))
                .networkProtocolVersion(() -> "1.0")
                .clientAcceptedVersions(s -> true)
                .serverAcceptedVersions(s -> true)
                .simpleChannel();

        INSTANCE = net;

        net.messageBuilder(EnergySyncS2C.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(EnergySyncS2C::new)
                .encoder(EnergySyncS2C::toBytes)
                .consumerMainThread(EnergySyncS2C::handle)
                .add();

        net.messageBuilder(ProgressSyncS2C.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(ProgressSyncS2C::new)
                .encoder(ProgressSyncS2C::toBytes)
                .consumerMainThread(ProgressSyncS2C::handle)
                .add();

        net.messageBuilder(SelectedRecipesSyncS2C.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SelectedRecipesSyncS2C::new)
                .encoder(SelectedRecipesSyncS2C::toBytes)
                .consumerMainThread(SelectedRecipesSyncS2C::handle)
                .add();

    }

    public static <MSG> void sendToServer(MSG message) {
        INSTANCE.sendToServer(message);
    }

    public static <MSG> void sendToPlayer(MSG message, ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static <MSG> void sendToClients(MSG message) {
        INSTANCE.send(PacketDistributor.ALL.noArg(), message);
    }
}
