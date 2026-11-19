package cn.blockforge.apothworkbench.net;

import cn.blockforge.apothworkbench.WB;
import cn.blockforge.apothworkbench.data.WorkbenchService;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 客户端 -> 服务端：一次编辑操作。payload 是 JSON 文本，便于后续扩展。
 */
public class EditPacket {

    public static final int A_SET_STATUS = 0;
    public static final int A_TOGGLE_RARITY = 1;
    public static final int A_SET_VALUES = 2;
    public static final int A_SYNC_VALUES = 3;
    public static final int A_REVERT = 4;
    public static final int A_SAVE = 5;
    public static final int A_CREATE = 6;
    public static final int A_REFRESH = 7;
    public static final int A_EXPORT = 8;
    public static final int A_SET_CATS = 9;
    public static final int A_BATCH = 10;
    public static final int A_SET_JSON = 11;
    public static final int A_REQUEST_JSON = 12;
    public static final int A_DESC = 13;
    public static final int A_EXPORT_ALL = 14;
    public static final int A_SET_NAME = 15;

    public final int action;
    public final String id;
    public final String payload;

    public EditPacket(int action, String id, JsonObject payload) {
        this.action = action;
        this.id = id == null ? "" : id;
        this.payload = payload == null ? "{}" : WB.COMPACT.toJson(payload);
    }

    public EditPacket(int action, String id) {
        this(action, id, null);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(action);
        buf.writeUtf(id);
        buf.writeUtf(payload, 262144);
    }

    public static EditPacket decode(FriendlyByteBuf buf) {
        int action = buf.readVarInt();
        String id = buf.readUtf();
        String payload = buf.readUtf(262144);
        return new EditPacket(action, id, WB.parseObject(payload));
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.setPacketHandled(true);

        // 注意：这里故意不用 enqueueWork。
        // 打开工作台会暂停世界（单人时集成服务器不再 tick），任务队列里的东西要等到恢复才执行，
        // 会让界面看起来“点了没反应”。工作台的编辑只改内存模型和文件、不碰世界，
        // 所以在网络线程里整体加锁处理是安全的；真正需要主线程的数据包重载会被挂起，等恢复 tick 再做。
        ServerPlayer player = ctx.getSender();
        if (player == null) {
            return;
        }
        if (!player.hasPermissions(2)) {
            player.sendSystemMessage(Component.literal("§c[工作台] 你没有权限使用词条工作台"));
            return;
        }
        var server = player.getServer();
        if (server == null) {
            return;
        }
        JsonObject p;
        try {
            p = WB.parseObject(payload);
        } catch (Exception ex) {
            return;
        }

        WorkbenchService svc = WorkbenchService.get(server);
        String err;
        SnapshotPacket snapshot;
        synchronized (svc) {
            // 两种"取数据"的请求不需要回快照
            if (action == A_REQUEST_JSON) {
                WorkbenchNetwork.send(player, new JsonPacket(id, svc.jsonOf(svc.entry(id))));
                return;
            }
            if (action == A_DESC) {
                String rarity = p.has("rarity") ? p.get("rarity").getAsString() : "";
                String json = svc.describeJson(svc.entry(id), rarity);
                WorkbenchNetwork.send(player, new DescPacket(id, rarity, json));
                return;
            }
            try {
                err = apply(svc, p);
            } catch (Exception ex) {
                WB.LOGGER.error("工作台编辑失败", ex);
                err = "操作失败：" + ex.getMessage();
            }
            // 把改动立即落盘（不改游戏内数据包），避免中途关服丢失
            if (action != A_SAVE && action != A_REFRESH && action != A_EXPORT && action != A_EXPORT_ALL) {
                svc.markDirty();
            }
            snapshot = SnapshotPacket.of(svc, false);
        }

        if (err != null && err.startsWith("\u0000")) {
            player.sendSystemMessage(Component.literal("§a[工作台] " + err.substring(1)));
        } else if (err != null && !err.isEmpty()) {
            player.sendSystemMessage(Component.literal("§c[工作台] " + err));
        }
        WorkbenchNetwork.sendTo(player, snapshot);
    }

    private String apply(WorkbenchService svc, JsonObject p) {
        switch (action) {
            case A_SET_STATUS:
                return svc.setStatus(svc.entry(id), (byte) (p.has("status") ? p.get("status").getAsInt() : 0));
            case A_TOGGLE_RARITY:
                return svc.toggleRarity(svc.entry(id), p.get("rarity").getAsString(), p.get("on").getAsBoolean());
            case A_SET_VALUES:
                return svc.setValues(svc.entry(id), p.get("rarity").getAsString(),
                        p.get("min").getAsDouble(), p.get("steps").getAsDouble(), p.get("step").getAsDouble());
            case A_SYNC_VALUES:
                return svc.syncValues(svc.entry(id),
                        p.get("min").getAsDouble(), p.get("steps").getAsDouble(), p.get("step").getAsDouble());
            case A_REVERT:
                return svc.revert(svc.entry(id));
            case A_CREATE:
                return svc.create(p);
            case A_SAVE: {
                String msg = svc.saveAndReload();
                return "\u0000" + msg; // 以特殊前缀标记为普通提示
            }
            case A_REFRESH:
                svc.reloadFromDisk();
                return null;
            case A_EXPORT:
                return "\u0000" + svc.exportKubeJs();
            case A_EXPORT_ALL:
                return "\u0000" + svc.exportKubeJs(true);
            case A_SET_CATS: {
                List<String> cats = new ArrayList<>();
                if (p.has("cats") && p.get("cats").isJsonArray()) {
                    for (JsonElement el : p.getAsJsonArray("cats")) {
                        cats.add(el.getAsString());
                    }
                }
                return svc.setCategories(svc.entry(id), cats);
            }
            case A_BATCH: {
                List<String> ids = new ArrayList<>();
                if (p.has("ids") && p.get("ids").isJsonArray()) {
                    for (JsonElement el : p.getAsJsonArray("ids")) {
                        ids.add(el.getAsString());
                    }
                }
                String op = p.has("op") ? p.get("op").getAsString() : "";
                String rarity = p.has("rarity") ? p.get("rarity").getAsString() : "";
                String msg = svc.batch(ids, op, rarity);
                return msg.startsWith("批量") ? "\u0000" + msg : msg;
            }
            case A_SET_NAME:
                return svc.setName(svc.entry(id), p.has("name") ? p.get("name").getAsString() : "");
            case A_SET_JSON: {
                JsonObject obj = p.has("json") && p.get("json").isJsonObject() ? p.getAsJsonObject("json") : null;
                if (obj == null) {
                    return "粘贴的内容不是合法的 JSON 对象";
                }
                return svc.setJson(svc.entry(id), obj);
            }
            default:
                return "未知操作";
        }
    }
}
