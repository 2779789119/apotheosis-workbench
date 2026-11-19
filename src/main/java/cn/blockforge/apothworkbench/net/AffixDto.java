package cn.blockforge.apothworkbench.net;

import java.util.List;

/**
 * 传给客户端的单条词条摘要。
 */
public record AffixDto(String id, String type, String summary, String cats, byte status, byte flags,
                       List<String> rarityKeys, List<String> rarityTexts,
                       String descJson, String descRarity, String name) {

    public static final byte F_CUSTOM = 1;
    public static final byte F_MODIFIED = 2;
    public static final byte F_HAS_VALUES = 4;
    public static final byte F_HAS_ORIGINAL = 8;
    public static final byte F_LOAD_FAILED = 16;
    public static final byte F_CATS_EDITABLE = 32;

    public boolean has(byte flag) {
        return (flags & flag) != 0;
    }
}
