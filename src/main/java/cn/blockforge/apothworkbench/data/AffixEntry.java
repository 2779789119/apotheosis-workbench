package cn.blockforge.apothworkbench.data;

import cn.blockforge.apothworkbench.WB;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一条词条在工作台中的完整模型。
 * <p>original = 词条“出厂”数据（自定义词条为 null）；current = 当前编辑结果。
 * stash 用于记住被移除的品阶数值块，重新勾选时能原样恢复。</p>
 */
public final class AffixEntry {

    public final ResourceLocation id;
    public JsonObject original;
    public JsonObject current;
    public byte status = WB.ST_ACTIVE;
    public boolean custom;
    public boolean touched;
    /** 自定义显示名（存在 state.json，客户端会注入语言表） */
    public String name;
    public Map<String, JsonElement> stash = new LinkedHashMap<>();

    public AffixEntry(ResourceLocation id) {
        this.id = id;
    }

    public AffixEntry(ResourceLocation id, JsonObject json) {
        this.id = id;
        this.original = json;
        // 必须深拷贝：否则 current 与 original 是同一个对象，任何编辑都会被判断成“未改动”
        this.current = json == null ? null : json.deepCopy();
    }

    /** 当前生效的数据 */
    public JsonObject payload() {
        return current != null ? current : original;
    }

    public String typeId() {
        JsonObject p = payload();
        if (p != null && p.has("type") && p.get("type").isJsonPrimitive()) {
            return p.get("type").getAsString();
        }
        return "unknown";
    }

    public String typeShort() {
        String t = typeId();
        int i = t.indexOf(':');
        return i >= 0 ? t.substring(i + 1) : t;
    }

    public List<String> categories() {
        List<String> out = new ArrayList<>();
        JsonObject p = payload();
        if (p != null && p.has("types") && p.get("types").isJsonArray()) {
            p.getAsJsonArray("types").forEach(e -> {
                if (e.isJsonPrimitive()) {
                    out.add(e.getAsString());
                }
            });
        }
        return out;
    }

    /** values 对象；没有则返回 null（说明该词条不受品阶限制） */
    public JsonObject values() {
        JsonObject p = payload();
        if (p != null && p.has("values") && p.get("values").isJsonObject()) {
            return p.getAsJsonObject("values");
        }
        return null;
    }

    public boolean hasValues() {
        return values() != null;
    }

    public boolean modified() {
        if (custom) {
            return true;
        }
        if (status != WB.ST_ACTIVE) {
            return true;
        }
        return current != null && original != null && !current.equals(original);
    }

    /** 详情面板里显示的简短说明 */
    public String summary() {
        JsonObject p = payload();
        if (p == null) {
            return "（无数据）";
        }
        String t = typeShort();
        try {
            if ("attribute".equals(t) && p.has("attribute")) {
                return p.get("attribute").getAsString() + " · " + cnOperation(str(p, "operation", "ADDITION"));
            }
            if ("mob_effect".equals(t) && p.has("mob_effect")) {
                return p.get("mob_effect").getAsString() + " · " + cnTarget(str(p, "target", "HURT_SELF"));
            }
            if ("damage_reduction".equals(t)) {
                return "伤害减免";
            }
        } catch (Exception ignored) {
            // 数据不规范时不要炸界面
        }
        return t;
    }

    private static String str(JsonObject o, String key, String def) {
        if (o.has(key) && o.get(key).isJsonPrimitive()) {
            return o.get(key).getAsString();
        }
        return def;
    }

    /** 品阶 -> 展示/编辑文本；带 '|' 表示是可编辑的 min|steps|step */
    public String valueText(String rarityKey) {
        JsonObject v = values();
        if (v == null || !v.has(rarityKey)) {
            return "";
        }
        return describe(v.get(rarityKey));
    }

    public static String describe(JsonElement el) {
        if (el == null || el.isJsonNull()) {
            return "";
        }
        if (!el.isJsonObject()) {
            return el.toString();
        }
        JsonObject o = el.getAsJsonObject();
        if (isStepBlock(o)) {
            return WB.fmt(o.get("min").getAsDouble()) + "|" + WB.fmt(o.get("steps").getAsDouble()) + "|"
                    + WB.fmt(o.get("step").getAsDouble());
        }
        // 复合结构（药水类）：逐项翻成中文，避免给出 "duration:+3" 这种看不懂的东西
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(cnField(e.getKey())).append(' ');
            JsonElement v = e.getValue();
            if (v.isJsonObject() && isStepBlock(v.getAsJsonObject())) {
                JsonObject b = v.getAsJsonObject();
                sb.append(WB.fmt(b.get("min").getAsDouble())).append('+')
                        .append(WB.fmt(b.get("steps").getAsDouble())).append('×')
                        .append(WB.fmt(b.get("step").getAsDouble()));
            } else if (v.isJsonPrimitive()) {
                sb.append(v.getAsString());
            } else if (v.isJsonObject()) {
                sb.append(v.getAsJsonObject().size()).append(" 项");
            } else {
                sb.append(v);
            }
        }
        String s = sb.toString();
        return s.length() > 60 ? s.substring(0, 60) + "…" : s;
    }

    private static boolean isStepBlock(JsonObject o) {
        return o.has("min") && o.has("steps") && o.has("step")
                && o.get("min").isJsonPrimitive() && o.get("steps").isJsonPrimitive() && o.get("step").isJsonPrimitive();
    }

    private static String cnField(String key) {
        return switch (key) {
            case "duration" -> "持续(刻)";
            case "amplifier" -> "等级";
            case "cooldown" -> "冷却(刻)";
            case "min" -> "最小";
            case "steps" -> "步数";
            case "step" -> "每步";
            default -> key;
        };
    }

    private static String cnOperation(String op) {
        return switch (op) {
            case "ADDITION" -> "加算";
            case "MULTIPLY_BASE" -> "乘算·基础";
            case "MULTIPLY_TOTAL" -> "乘算·总计";
            default -> op;
        };
    }

    private static String cnTarget(String target) {
        return switch (target) {
            case "HURT_SELF" -> "自己受伤时";
            case "HURT_ATTACKER" -> "攻击命中时";
            default -> target;
        };
    }
}
