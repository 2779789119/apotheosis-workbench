package cn.blockforge.apothworkbench.client;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

import java.util.HashMap;
import java.util.Map;

/**
 * 把工作台里的自定义词条名塞进客户端翻译层。
 * <p>注意：{@code Language.getInstance().getLanguageData()} 返回的是 ImmutableMap，直接 put 会抛异常，
 * 所以这里改用「包装一个 Language 再 Language.inject」的方式：查表时先看自定义名，没有再交给原版。</p>
 */
public final class ClientLang {

    private static final Map<String, String> names = new HashMap<>();
    private static Language wrapper;

    private ClientLang() {}

    public static void setNames(Map<String, String> src) {
        if (names.equals(src)) {
            return;
        }
        names.clear();
        names.putAll(src);
        wrapper = null; // 强制重建包装
        apply();
    }

    /** 语言实例被资源重载换掉后，会重新包装一次 */
    public static void tick() {
        apply();
    }

    private static void apply() {
        try {
            Language current = Language.getInstance();

            Map<String, String> custom = new HashMap<>();
            for (Map.Entry<String, String> e : names.entrySet()) {
                String id = e.getKey();
                String value = e.getValue();
                if (value == null || value.isBlank()) {
                    continue;
                }
                custom.put("affix." + id, value);
                custom.put("affix." + id + ".suffix", value);
            }

            if (custom.isEmpty()) {
                // 没有自定义名了：如果当前是我们的包装，退回原版
                if (current instanceof CustomLang cl) {
                    Language.inject(cl.base);
                    wrapper = null;
                }
                return;
            }
            if (current == wrapper) {
                return;
            }
            Language base = current instanceof CustomLang cl ? cl.base : current;
            wrapper = new CustomLang(base, custom);
            Language.inject(wrapper);
        } catch (Throwable ignored) {
            // 翻译层异常不应该影响游戏
        }
    }

    /** 只覆盖查表，其它全部委托给原版 Language */
    private static final class CustomLang extends Language {

        private final Language base;
        private final Map<String, String> custom;

        private CustomLang(Language base, Map<String, String> custom) {
            this.base = base;
            this.custom = custom;
        }

        @Override
        public String getOrDefault(String key, String def) {
            String v = custom.get(key);
            return v != null ? v : base.getOrDefault(key, def);
        }

        @Override
        public boolean has(String key) {
            return custom.containsKey(key) || base.has(key);
        }

        @Override
        public boolean isDefaultRightToLeft() {
            return base.isDefaultRightToLeft();
        }

        @Override
        public FormattedCharSequence getVisualOrder(FormattedText text) {
            return base.getVisualOrder(text);
        }

        @Override
        public Map<String, String> getLanguageData() {
            return base.getLanguageData();
        }
    }
}
