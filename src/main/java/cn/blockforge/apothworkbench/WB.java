package cn.blockforge.apothworkbench;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 全局常量与小工具。
 */
public final class WB {

    public static final String MODID = "apoth_workbench";
    public static final Logger LOGGER = LogManager.getLogger("ApotheosisWorkbench");

    /** 生成的数据包名称（存档 datapacks 目录下） */
    public static final String PACK_NAME = "apoth_workbench";
    /** 存档数据包在 PackRepository 中的 ID */
    public static final String PACK_ID = "file/" + PACK_NAME;

    /** 词条状态 */
    public static final byte ST_ACTIVE = 0;
    public static final byte ST_DISABLED = 1;
    public static final byte ST_DELETED = 2;

    /** 被禁用/删除词条统一写入的“永不满足”条件 */
    public static final String FALSE_CONDITION_JSON = "{\"conditions\":[{\"type\":\"forge:false\"}]}";

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static final Gson COMPACT = new GsonBuilder().disableHtmlEscaping().create();

    private WB() {}

    public static JsonObject parseObject(String s) {
        JsonElement e = JsonParser.parseString(s);
        if (e == null || !e.isJsonObject()) {
            throw new IllegalArgumentException("不是合法的 JSON 对象");
        }
        return e.getAsJsonObject();
    }

    public static JsonObject copy(JsonObject o) {
        return o == null ? null : o.deepCopy();
    }

    /** 去掉多余小数位的数字文本 */
    public static String fmt(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1.0E9) {
            return Long.toString((long) v);
        }
        String s = String.format(java.util.Locale.ROOT, "%.4f", v);
        while (s.endsWith("0")) {
            s = s.substring(0, s.length() - 1);
        }
        if (s.endsWith(".")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    public static String statusName(byte status) {
        return switch (status) {
            case ST_DISABLED -> "已禁用";
            case ST_DELETED -> "已删除";
            default -> "启用中";
        };
    }
}
