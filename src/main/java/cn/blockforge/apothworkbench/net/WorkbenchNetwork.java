package cn.blockforge.apothworkbench.net;

import cn.blockforge.apothworkbench.WB;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class WorkbenchNetwork {

    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(ResourceLocation.tryBuild(WB.MODID, "main"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .simpleChannel();

    private WorkbenchNetwork() {}

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(SnapshotPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SnapshotPacket::encode)
                .decoder(SnapshotPacket::decode)
                .consumerMainThread(SnapshotPacket::handle)
                .add();
        CHANNEL.messageBuilder(EditPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(EditPacket::encode)
                .decoder(EditPacket::decode)
                .consumerMainThread(EditPacket::handle)
                .add();
        CHANNEL.messageBuilder(DescPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DescPacket::encode)
                .decoder(DescPacket::decode)
                .consumerMainThread(DescPacket::handle)
                .add();
        CHANNEL.messageBuilder(JsonPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(JsonPacket::encode)
                .decoder(JsonPacket::decode)
                .consumerMainThread(JsonPacket::handle)
                .add();
    }

    public static void sendTo(ServerPlayer player, SnapshotPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void send(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToServer(EditPacket packet) {
        CHANNEL.sendToServer(packet);
    }
}
