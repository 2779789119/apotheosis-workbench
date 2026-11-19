package cn.blockforge.apothworkbench.data;

import cn.blockforge.apothworkbench.WB;
import dev.shadowsoffire.apotheosis.adventure.affix.Affix;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixRegistry;
import dev.shadowsoffire.apotheosis.adventure.loot.LootCategory;
import dev.shadowsoffire.apotheosis.adventure.loot.LootRarity;
import dev.shadowsoffire.apotheosis.adventure.loot.RarityRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 调用神化自己的 Affix#getDescription 取词条效果文本。
 * <p>说明：词条描述不是数据包里的静态文本，而是各 Affix 子类在代码里拼出来的
 * （数值取自 values[品阶] 的 StepFunction），所以只能通过神化的 API 拿到。
 * 这个类只在确认神化已加载时才会被加载，避免神化缺失时的 NoClassDefFoundError。</p>
 */
public final class AffixDescriber {

    private AffixDescriber() {}

    /** 词条是否已被神化注册（重载后核对用） */
    public static boolean isRegistered(ResourceLocation id) {
        try {
            return AffixRegistry.INSTANCE.getValue(id) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 是否是神化支持的部位名；null 表示无法判断（没装神化） */
    public static Boolean isValidCategory(String name) {
        try {
            return LootCategory.VALUES.stream().anyMatch(c -> c.getName().equals(name));
        } catch (Throwable t) {
            return null;
        }
    }

    /** 神化真正注册的部位名（不含 none），给界面选单用；拿不到返回 null */
    public static java.util.List<String> categoryNames() {
        try {
            return LootCategory.VALUES.stream()
                    .map(LootCategory::getName)
                    .filter(n -> !"none".equals(n))
                    .sorted()
                    .toList();
        } catch (Throwable t) {
            return null;
        }
    }

    /** 返回词条在指定品阶下的效果描述（保留翻译键与样式，客户端会本地化）；拿不到返回 null */
    public static Component describe(ResourceLocation id, String rarityKey) {
        try {
            Affix affix = AffixRegistry.INSTANCE.getValue(id);
            if (affix == null) {
                return null;
            }
            LootRarity rarity = null;
            if (rarityKey != null && !rarityKey.isEmpty()) {
                rarity = RarityRegistry.INSTANCE.getValue(RarityRegistry.convertId(rarityKey));
            }
            if (rarity == null) {
                return null;
            }
            ItemStack stack = ItemStack.EMPTY;
            Component text = null;
            try {
                text = affix.getDescription(stack, rarity, 1.0F);
            } catch (Exception ignored) {
                // 少数词条依赖具体物品，忽略后走兜底
            }
            if (text == null || text.getString().isBlank()) {
                try {
                    text = affix.getAugmentingText(stack, rarity, 1.0F);
                } catch (Exception ignored) {
                    // 同上
                }
            }
            if (text == null || text.getString().isBlank()) {
                return null;
            }
            return text;
        } catch (Throwable t) {
            WB.LOGGER.debug("读取词条描述失败 {}", id, t);
            return null;
        }
    }
}
