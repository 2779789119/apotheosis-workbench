package cn.blockforge.apothworkbench.data;

/**
 * 品阶（稀有度）信息，完全来自神化的 rarities 数据包，不硬编码。
 */
public record RarityInfo(String id, int ordinal, int color, int weight) {

    /** 写入词条 values 时使用的键：神化自己的品阶用短名，其它命名空间用全名 */
    public String key() {
        int i = id.indexOf(':');
        if (i > 0) {
            String ns = id.substring(0, i);
            if ("apotheosis".equals(ns)) {
                return id.substring(i + 1);
            }
        }
        return id;
    }

    public String shortId() {
        int i = id.indexOf(':');
        return i > 0 ? id.substring(i + 1) : id;
    }
}
