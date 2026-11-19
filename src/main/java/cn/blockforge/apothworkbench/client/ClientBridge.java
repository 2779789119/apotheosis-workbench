package cn.blockforge.apothworkbench.client;

import cn.blockforge.apothworkbench.net.AffixDto;
import cn.blockforge.apothworkbench.net.DescPacket;
import cn.blockforge.apothworkbench.net.JsonPacket;
import cn.blockforge.apothworkbench.net.SnapshotPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 客户端入口，只在物理客户端加载。
 */
public final class ClientBridge {

    private ClientBridge() {}

    public static void handleSnapshot(SnapshotPacket packet) {
        Minecraft mc = Minecraft.getInstance();

        // 先把自定义词条名注入语言表（物品 tooltip 也要用）
        Map<String, String> names = new HashMap<>();
        for (AffixDto a : packet.affixes) {
            if (a.name() != null && !a.name().isBlank()) {
                names.put(a.id(), a.name());
            }
        }
        ClientLang.setNames(names);

        WorkbenchScreen active = WorkbenchScreen.ACTIVE;
        if (active != null) {
            // 即使当前开着「新建词条」子界面，也要把最新快照回填给主界面
            active.update(packet);
            if (packet.open && mc.screen != active) {
                mc.setScreen(active);
            }
            return;
        }
        if (packet.open) {
            mc.setScreen(new WorkbenchScreen(packet));
        }
    }

    public static void handleDesc(DescPacket packet) {
        WorkbenchScreen active = WorkbenchScreen.ACTIVE;
        if (active != null) {
            active.onDesc(packet.id, packet.rarity, packet.json);
        }
    }

    public static void handleJson(JsonPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (packet.json.isEmpty()) {
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal("§c[工作台] 取不到这条词条的 JSON"), false);
            }
            return;
        }
        mc.keyboardHandler.setClipboard(packet.json);
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("§a[工作台] 已复制 " + packet.id + " 的 JSON 到剪贴板"), false);
        }
    }
}
