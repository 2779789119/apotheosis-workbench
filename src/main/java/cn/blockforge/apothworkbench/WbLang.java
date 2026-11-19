package cn.blockforge.apothworkbench;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * 语言表快捷入口。
 * <p>所有界面/聊天文案都走 {@code apoth_workbench.*} 翻译键，
 * 服务端下发的消息也用 {@link Component#translatable} 包装，客户端会按自己的语言显示。</p>
 */
public final class WbLang {

    public static final String P = "apoth_workbench.";

    private WbLang() {}

    /** 可翻译组件；key 可以省略 {@code apoth_workbench.} 前缀，args 会填入 %s 占位符 */
    public static MutableComponent tr(String key, Object... args) {
        return Component.translatable(full(key), args);
    }

    /** 纯文本形式（用于 drawString / 搜索匹配） */
    public static String s(String key, Object... args) {
        return Component.translatable(full(key), args).getString();
    }

    private static String full(String key) {
        return key.startsWith(P) ? key : P + key;
    }

    /** 未命中翻译键时返回原文；用于「有就翻译，没有就用原名」的场合 */
    public static String opt(String key, String fallback) {
        String v = s(key);
        return v.equals(key) ? fallback : v;
    }

    /** 成功提示：§a[工作台] xxx */
    public static MutableComponent ok(String key, Object... args) {
        return tr("chat.ok", tr(key, args));
    }

    /** 失败提示：§c[工作台] xxx */
    public static MutableComponent err(String key, Object... args) {
        return tr("chat.error", tr(key, args));
    }

    /** 需要注意的提示：§e[工作台] xxx */
    public static MutableComponent warn(String key, Object... args) {
        return tr("chat.warn", tr(key, args));
    }
}
