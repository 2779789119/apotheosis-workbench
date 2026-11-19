package cn.blockforge.apothworkbench.net;

import cn.blockforge.apothworkbench.WB;
import cn.blockforge.apothworkbench.client.ClientBridge;
import cn.blockforge.apothworkbench.data.AffixEntry;
import cn.blockforge.apothworkbench.data.RarityInfo;
import cn.blockforge.apothworkbench.data.WorkbenchService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 服务端 -> 客户端：完整的工作台快照。
 */
public class SnapshotPacket {

    public final boolean open;
    public final List<RarityInfo> rarities;
    public final List<String> categories;
    public final List<AffixDto> affixes;
    public final int[] stats;

    public SnapshotPacket(boolean open, List<RarityInfo> rarities, List<String> categories,
                          List<AffixDto> affixes, int[] stats) {
        this.open = open;
        this.rarities = rarities;
        this.categories = categories;
        this.affixes = affixes;
        this.stats = stats;
    }

    public static SnapshotPacket of(WorkbenchService svc, boolean open) {
        synchronized (svc) {
            return build(svc, open);
        }
    }

    private static SnapshotPacket build(WorkbenchService svc, boolean open) {
        List<AffixDto> list = new ArrayList<>();
        for (AffixEntry e : svc.list()) {
            byte flags = 0;
            if (e.custom) {
                flags |= AffixDto.F_CUSTOM;
            }
            if (e.modified()) {
                flags |= AffixDto.F_MODIFIED;
            }
            if (e.hasValues()) {
                flags |= AffixDto.F_HAS_VALUES;
            }
            if (e.original != null) {
                flags |= AffixDto.F_HAS_ORIGINAL;
            }
            if (svc.loadFailed().contains(e.id.toString())) {
                flags |= AffixDto.F_LOAD_FAILED;
            }
            if (e.payload() != null && e.payload().has("types")) {
                flags |= AffixDto.F_CATS_EDITABLE;
            }
            List<String> keys = new ArrayList<>();
            List<String> texts = new ArrayList<>();
            var values = e.values();
            if (values != null) {
                // 按品阶顺序排列
                for (RarityInfo r : svc.rarities()) {
                    if (values.has(r.key())) {
                        keys.add(r.key());
                        texts.add(e.valueText(r.key()));
                    }
                }
                for (String k : values.keySet()) {
                    if (!keys.contains(k)) {
                        keys.add(k);
                        texts.add(e.valueText(k));
                    }
                }
            }
            // 描述不再随快照下发：592 条词条一次性算描述会让打开界面卡一下，
            // 改成客户端需要时用 A_DESC 单独要（结果按 词条+品阶 缓存）。
            String descRarity = keys.isEmpty() ? "" : keys.get(keys.size() - 1);
            list.add(new AffixDto(e.id.toString(), e.typeShort(), e.summary(),
                    String.join(",", e.categories()), e.status, flags, keys, texts,
                    "", descRarity, e.name == null ? "" : e.name));
        }
        Map<String, Integer> st = svc.stats();
        int[] stats = new int[]{
                st.getOrDefault("total", 0), st.getOrDefault("active", 0), st.getOrDefault("disabled", 0),
                st.getOrDefault("deleted", 0), st.getOrDefault("custom", 0), st.getOrDefault("modified", 0),
                st.getOrDefault("needsReload", 0)};
        return new SnapshotPacket(open, new ArrayList<>(svc.rarities()), new ArrayList<>(svc.categories()), list, stats);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(open);
        buf.writeVarInt(rarities.size());
        for (RarityInfo r : rarities) {
            buf.writeUtf(r.id());
            buf.writeVarInt(r.ordinal());
            buf.writeInt(r.color());
            buf.writeVarInt(r.weight());
        }
        buf.writeVarInt(categories.size());
        for (String c : categories) {
            buf.writeUtf(c);
        }
        buf.writeVarInt(affixes.size());
        for (AffixDto a : affixes) {
            buf.writeUtf(a.id());
            buf.writeUtf(a.type());
            buf.writeUtf(a.summary());
            buf.writeUtf(a.cats());
            buf.writeByte(a.status());
            buf.writeByte(a.flags());
            buf.writeVarInt(a.rarityKeys().size());
            for (int i = 0; i < a.rarityKeys().size(); i++) {
                buf.writeUtf(a.rarityKeys().get(i));
                buf.writeUtf(a.rarityTexts().get(i));
            }
            buf.writeUtf(a.descJson(), 32767);
            buf.writeUtf(a.descRarity(), 128);
            buf.writeUtf(a.name(), 256);
        }
        for (int s : stats) {
            buf.writeVarInt(s);
        }
    }

    public static SnapshotPacket decode(FriendlyByteBuf buf) {
        boolean open = buf.readBoolean();
        int rc = buf.readVarInt();
        List<RarityInfo> rarities = new ArrayList<>(rc);
        for (int i = 0; i < rc; i++) {
            String rid = buf.readUtf();
            int ordinal = buf.readVarInt();
            int color = buf.readInt();
            int weight = buf.readVarInt();
            rarities.add(new RarityInfo(rid, ordinal, color, weight));
        }
        int cc = buf.readVarInt();
        List<String> categories = new ArrayList<>(cc);
        for (int i = 0; i < cc; i++) {
            categories.add(buf.readUtf());
        }
        int ac = buf.readVarInt();
        List<AffixDto> affixes = new ArrayList<>(ac);
        for (int i = 0; i < ac; i++) {
            String id = buf.readUtf();
            String type = buf.readUtf();
            String summary = buf.readUtf();
            String cats = buf.readUtf();
            byte status = buf.readByte();
            byte flags = buf.readByte();
            int kc = buf.readVarInt();
            List<String> keys = new ArrayList<>(kc);
            List<String> texts = new ArrayList<>(kc);
            for (int k = 0; k < kc; k++) {
                keys.add(buf.readUtf());
                texts.add(buf.readUtf());
            }
            String descJson = buf.readUtf(32767);
            String descRarity = buf.readUtf(128);
            String name = buf.readUtf(256);
            affixes.add(new AffixDto(id, type, summary, cats, status, flags, keys, texts, descJson, descRarity, name));
        }
        int[] stats = new int[7];
        for (int i = 0; i < 7; i++) {
            stats[i] = buf.readVarInt();
        }
        return new SnapshotPacket(open, rarities, categories, affixes, stats);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientBridge.handleSnapshot(this)));
        ctx.setPacketHandled(true);
    }
}
