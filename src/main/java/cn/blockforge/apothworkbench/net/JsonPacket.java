package cn.blockforge.apothworkbench.net;

import cn.blockforge.apothworkbench.client.ClientBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 -> 客户端：词条的完整 JSON 文本（用于复制到剪贴板）。
 */
public class JsonPacket {

    public final String id;
    public final String json;

    public JsonPacket(String id, String json) {
        this.id = id == null ? "" : id;
        this.json = json == null ? "" : json;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(id);
        buf.writeUtf(json, 262144);
    }

    public static JsonPacket decode(FriendlyByteBuf buf) {
        return new JsonPacket(buf.readUtf(), buf.readUtf(262144));
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientBridge.handleJson(this)));
        ctx.setPacketHandled(true);
    }
}
