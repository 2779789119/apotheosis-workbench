package cn.blockforge.apothworkbench.client;

import cn.blockforge.apothworkbench.WbLang;
import cn.blockforge.apothworkbench.data.RarityInfo;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 界面用的中文名。
 * <p>品阶优先用神化语言文件里的 {@code rarity.<id>}（换语言/换版本自动跟随），
 * 没有就退回内置表；词条类型用手写表，未知类型原样显示。</p>
 */
public final class WbNames {

    private static final Map<String, String> RARITY_FALLBACK = new HashMap<>();
    private static final Map<String, String> TYPE_CN = new HashMap<>();
    private static final Map<String, String> CAT_CN = new HashMap<>();
    private static final Map<String, String> rarityCache = new HashMap<>();
    private static final Map<String, String> catCache = new HashMap<>();

    static {
        RARITY_FALLBACK.put("common", "普通");
        RARITY_FALLBACK.put("uncommon", "罕见");
        RARITY_FALLBACK.put("rare", "珍贵");
        RARITY_FALLBACK.put("epic", "史诗");
        RARITY_FALLBACK.put("mythic", "神话");
        RARITY_FALLBACK.put("ancient", "远古");
        RARITY_FALLBACK.put("artifact", "神器");
        RARITY_FALLBACK.put("heirloom", "传家宝");
        RARITY_FALLBACK.put("esoteric", "秘传");

        TYPE_CN.put("attribute", "属性");
        TYPE_CN.put("mob_effect", "药水效果");
        TYPE_CN.put("damage_reduction", "伤害减免");
        TYPE_CN.put("durable", "耐久");
        TYPE_CN.put("catalyzing", "催化");
        TYPE_CN.put("cleaving", "顺劈");
        TYPE_CN.put("enlightened", "启明");
        TYPE_CN.put("executing", "处决");
        TYPE_CN.put("festive", "庆典");
        TYPE_CN.put("magical", "魔法箭");
        TYPE_CN.put("omnetic", "万能");
        TYPE_CN.put("psychic", "心灵");
        TYPE_CN.put("radial", "范围");
        TYPE_CN.put("retreating", "后撤");
        TYPE_CN.put("spectral", "幽影箭");
        TYPE_CN.put("telepathic", "念力");
        TYPE_CN.put("thunderstruck", "雷霆");

        CAT_CN.put("sword", "剑");
        CAT_CN.put("heavy_weapon", "重型武器");
        CAT_CN.put("bow", "弓");
        CAT_CN.put("crossbow", "弩");
        CAT_CN.put("trident", "三叉戟");
        CAT_CN.put("shield", "盾牌");
        CAT_CN.put("pickaxe", "镐");
        CAT_CN.put("shovel", "锹");
        CAT_CN.put("helmet", "头盔");
        CAT_CN.put("chestplate", "胸甲");
        CAT_CN.put("leggings", "护腿");
        CAT_CN.put("boots", "靴子");
        CAT_CN.put("fishing_rod", "钓竿");
        CAT_CN.put("none", "无");
    }

    private WbNames() {}

    public static void clearCache() {
        rarityCache.clear();
        catCache.clear();
    }

    /**
     * 部位表由服务端从神化注册表读出来下发（见 SnapshotPacket#categories），
     * 这里不再硬编码，避免出现"界面能选、神化不认识"的部位。
     */

    /** 品阶中文名 */
    public static String rarity(RarityInfo r) {
        if (r == null) {
            return "";
        }
        String key = "rarity." + r.id();
        String cached = rarityCache.get(key);
        if (cached != null) {
            return cached;
        }
        String s = Component.translatable(key).getString();
        if (s.equals(key)) {
            String mine = "apoth_workbench.rarity." + r.shortId();
            s = WbLang.s(mine);
            if (s.equals(mine)) {
                s = RARITY_FALLBACK.getOrDefault(r.shortId(), r.shortId());
            }
        }
        rarityCache.put(key, s);
        return s;
    }

    /** 中文（英文原名） */
    public static String rarityWithEn(RarityInfo r) {
        if (r == null) {
            return "";
        }
        String cn = rarity(r);
        return cn.equals(r.shortId()) ? cn : cn + " (" + r.shortId() + ")";
    }

    /** 词条类型显示名 */
    public static String type(String typeShort) {
        return WbLang.opt("apoth_workbench.type." + typeShort, TYPE_CN.getOrDefault(typeShort, typeShort));
    }

    public static String typeWithEn(String typeShort) {
        String cn = type(typeShort);
        return cn.equals(typeShort) ? cn : cn + " (" + typeShort + ")";
    }

    /**
     * 单个部位的中文名，按顺序尝试几种可能的翻译键：
     * <ol>
     *   <li>神化官方：{@code text.apotheosis.category.<名字>}</li>
     *   <li>带命名空间的变体：{@code text.apotheosis.category.<ns>.<path>}</li>
     *   <li>Curios / Trinkets 的槽位名：{@code curios.identifier.<path>}（把 artifact_belt 拆成 belt 再试一次）</li>
     *   <li>内置表和 id 美化兜底</li>
     * </ol>
     */
    public static String categoryLabel(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        String cached = catCache.get(name);
        if (cached != null) {
            return cached;
        }
        String result = null;
        for (String key : candidateKeys(name)) {
            String s = Component.translatable(key).getString();
            if (!s.equals(key)) {
                result = s;
                break;
            }
        }
        if (result == null) {
            String mine = "apoth_workbench.cat." + name;
            String s = WbLang.s(mine);
            if (!s.equals(mine)) {
                result = s;
            }
        }
        if (result == null) {
            result = CAT_CN.get(name);
        }
        if (result == null) {
            result = prettify(name);
        }
        catCache.put(name, result);
        return result;
    }

    private static java.util.List<String> candidateKeys(String name) {
        java.util.List<String> keys = new java.util.ArrayList<>(8);
        keys.add("text.apotheosis.category." + name);
        int i = name.indexOf(':');
        String path = name;
        if (i >= 0) {
            String ns = name.substring(0, i);
            path = name.substring(i + 1);
            keys.add("text.apotheosis.category." + ns + "." + path);
            keys.add("text.apotheosis.category." + path);
        }
        keys.add("curios.identifier." + path);
        keys.add("curio.identifier." + path);
        keys.add("trinkets.slot." + path);
        String stripped = stripPrefix(path);
        if (!stripped.equals(path)) {
            keys.add("curios.identifier." + stripped);
            keys.add("trinkets.slot." + stripped);
            keys.add("text.apotheosis.category." + stripped);
        }
        return keys;
    }

    /** artifact_belt -> belt */
    private static String stripPrefix(String path) {
        for (String p : new String[]{"artifact_", "artifacts_", "curio_", "trinket_"}) {
            if (path.startsWith(p) && path.length() > p.length()) {
                return path.substring(p.length());
            }
        }
        return path;
    }

    /** curio:artifact_belt -> Artifact Belt */
    private static String prettify(String name) {
        String path = name;
        int i = name.indexOf(':');
        if (i >= 0) {
            path = name.substring(i + 1);
        }
        StringBuilder sb = new StringBuilder();
        for (String part : path.split("[_/.]")) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1));
            }
        }
        return sb.length() == 0 ? name : sb.toString();
    }

    /** "bow,crossbow" -> "弓 / 弩" */
    public static String cats(String csv) {
        if (csv == null || csv.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String s : csv.split(",")) {
            String k = s.trim();
            if (k.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(" / ");
            }
            sb.append(categoryLabel(k));
        }
        return sb.toString();
    }

    /** "bow,crossbow" -> "弓(bow) / 弩(crossbow)" */
    public static String catsWithEn(String csv) {
        if (csv == null || csv.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String s : csv.split(",")) {
            String k = s.trim();
            if (k.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(" / ");
            }
            String cn = categoryLabel(k);
            sb.append(cn.equals(k) ? k : cn + "(" + k + ")");
        }
        return sb.toString();
    }
}
