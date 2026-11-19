package cn.blockforge.apothworkbench.data;

import cn.blockforge.apothworkbench.WB;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 直接扫描服务器资源管理器里的神化数据包目录。
 * 这样即使神化以后加新的词条类型，也不需要改这个模组。
 */
public final class DataScanner {

    public static final String AFFIX_DIR = "affixes";
    public static final String RARITY_DIR = "rarities";

    private DataScanner() {}

    /** 词条 id -> 原始 JSON（跳过没有 type 的占位文件） */
    public static Map<ResourceLocation, JsonObject> scanAffixes(MinecraftServer server) {
        Map<ResourceLocation, JsonObject> out = new LinkedHashMap<>();
        ResourceManager rm = server.getResourceManager();
        for (Map.Entry<ResourceLocation, Resource> e : rm.listResources(AFFIX_DIR, p -> p.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation loc = e.getKey();
            String path = loc.getPath();
            String rel = path.substring(AFFIX_DIR.length() + 1, path.length() - ".json".length());
            ResourceLocation id = ResourceLocation.tryBuild(loc.getNamespace(), rel);
            if (id == null) {
                continue;
            }
            JsonObject json = readJson(e.getValue());
            if (json == null || !json.has("type")) {
                continue; // 条件占位/损坏文件，不作为基础数据
            }
            out.put(id, json);
        }
        return out;
    }

    /** 品阶列表，按 ordinal 升序 */
    public static List<RarityInfo> scanRarities(MinecraftServer server) {
        List<RarityInfo> out = new ArrayList<>();
        ResourceManager rm = server.getResourceManager();
        for (Map.Entry<ResourceLocation, Resource> e : rm.listResources(RARITY_DIR, p -> p.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation loc = e.getKey();
            String path = loc.getPath();
            String rel = path.substring(RARITY_DIR.length() + 1, path.length() - ".json".length());
            ResourceLocation id = ResourceLocation.tryBuild(loc.getNamespace(), rel);
            if (id == null) {
                continue;
            }
            JsonObject json = readJson(e.getValue());
            if (json == null) {
                continue;
            }
            int ordinal = json.has("ordinal") && json.get("ordinal").isJsonPrimitive() ? json.get("ordinal").getAsInt() : out.size();
            int weight = json.has("weight") && json.get("weight").isJsonPrimitive() ? json.get("weight").getAsInt() : 0;
            out.add(new RarityInfo(id.toString(), ordinal, parseColor(json), weight));
        }
        out.sort(Comparator.comparingInt(RarityInfo::ordinal));
        return out;
    }

    private static int parseColor(JsonObject json) {
        try {
            if (json.has("color") && json.get("color").isJsonPrimitive()) {
                String s = json.get("color").getAsString();
                if (s.startsWith("#")) {
                    s = s.substring(1);
                }
                if (s.length() == 6) {
                    return 0xFF000000 | Integer.parseInt(s, 16);
                }
                if (s.length() == 8) {
                    return (int) Long.parseLong(s, 16);
                }
            }
        } catch (Exception ignored) {
            // 颜色解析失败就退回白色
        }
        return 0xFFFFFFFF;
    }

    public static JsonObject readJson(Resource resource) {
        try (InputStream in = resource.open()) {
            String s = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JsonElement el = JsonParser.parseString(s);
            return el.isJsonObject() ? el.getAsJsonObject() : null;
        } catch (Exception ex) {
            WB.LOGGER.debug("读取数据包文件失败: {}", resource.sourcePackId(), ex);
            return null;
        }
    }
}
