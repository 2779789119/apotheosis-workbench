package cn.blockforge.apothworkbench.net;

import cn.blockforge.apothworkbench.client.ClientBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 -> 客户端：某个词条在某个品阶下的效果描述（Component JSON）。
 */
public class DescPacket {

    public final String id;
    public final String rarity;
    public final String json;

    public DescPacket(String id, String rarity, String json) {
        this.id = id == null ? "" : id;
        this.rarity = rarity == null ? "" : rarity;
        this.json = json == null ? "" : json;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(id);
        buf.writeUtf(rarity);
        buf.writeUtf(json, 32767);
    }

    public static DescPacket decode(FriendlyByteBuf buf) {
        return new DescPacket(buf.readUtf(), buf.readUtf(), buf.readUtf(32767));
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientBridge.handleDesc(this)));
        ctx.setPacketHandled(true);
    }
}
