package cn.blockforge.apothworkbench.client;

import cn.blockforge.apothworkbench.data.RarityInfo;
import cn.blockforge.apothworkbench.net.EditPacket;
import cn.blockforge.apothworkbench.net.WorkbenchNetwork;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 新建词条界面：可从现有词条复制，也可用属性/药水模板从零建。
 */
public class CreateAffixScreen extends Screen {

    private final Screen parent;
    private final String sourceId;
    private final List<RarityInfo> rarities;
    private final List<String> availableCats;
    private String mode;
    private final Set<String> chosen = new LinkedHashSet<>();
    private String operation = "ADDITION";
    private String target = "HURT_SELF";

    private int px;
    private int py;
    private final int pw = 300;
    private final int ph = 250;

    private EditBox editId;
    private EditBox editName;
    private int idW;
    private EditBox editParam;
    private EditBox editCats;
    private EditBox fMin;
    private EditBox fSteps;
    private EditBox fStep;
    private Button btnModeCopy;
    private Button btnModeAttr;
    private Button btnModePotion;
    private Button btnModePaste;
    private Button btnCycle;
    private Button btnPick;
    private Button btnPickCats;
    private com.google.gson.JsonObject pasted;
    private String pasteMsg = "";
    private final List<Chip> chips = new ArrayList<>();
    private Button btnCreate;
    private Button btnCancel;
    private int mouseX;
    private int mouseY;
    private List<Component> tip;

    private record Chip(int x, int y, int w, int h, String key, boolean on) {
        boolean hit(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    public CreateAffixScreen(Screen parent, String sourceId, List<RarityInfo> rarities, List<String> availableCats, String mode) {
        super(Component.literal("新建词条"));
        this.parent = parent;
        this.sourceId = sourceId;
        this.rarities = rarities;
        this.availableCats = availableCats;
        this.mode = mode;
        for (RarityInfo r : rarities) {
            chosen.add(r.key());
        }
    }

    @Override
    protected void init() {
        super.init();
        px = (width - pw) / 2;
        py = Math.max(2, (height - ph) / 2);

        String suggestion = sourceId != null
                ? "apoth_workbench:" + sanitize(sourceId.substring(sourceId.indexOf(':') + 1)) + "_copy"
                : "apoth_workbench:custom/new_affix";
        idW = (pw - 16) * 3 / 5 - 2;
        int nameW = pw - 16 - idW - 4;
        editId = new EditBox(font, px + 8, py + 30, idW, 16, Component.literal("词条 ID"));
        editId.setMaxLength(120);
        editId.setValue(suggestion);
        addRenderableWidget(editId);

        editName = new EditBox(font, px + 8 + idW + 4, py + 30, nameW, 16, Component.literal("名称"));
        editName.setMaxLength(40);
        addRenderableWidget(editName);

        int bw = (pw - 16 - 12) / 4;
        btnModeCopy = addRenderableWidget(Button.builder(Component.literal("复制"), b -> setMode("copy"))
                .bounds(px + 8, py + 50, bw, 16).build());
        btnModeAttr = addRenderableWidget(Button.builder(Component.literal("属性"), b -> setMode("attribute"))
                .bounds(px + 8 + bw + 4, py + 50, bw, 16).build());
        btnModePotion = addRenderableWidget(Button.builder(Component.literal("药水"), b -> setMode("mob_effect"))
                .bounds(px + 8 + (bw + 4) * 2, py + 50, bw, 16).build());
        btnModePaste = addRenderableWidget(Button.builder(Component.literal("粘贴 JSON"), b -> {
            readClipboard();
            setMode("paste");
        }).bounds(px + 8 + (bw + 4) * 3, py + 50, bw, 16).build());

        int paramW = pw - 16 - 26;
        editParam = new EditBox(font, px + 8, py + 79, paramW, 16, Component.literal("参数"));
        editParam.setMaxLength(120);
        addRenderableWidget(editParam);
        btnPick = addRenderableWidget(Button.builder(Component.literal("选…"), b -> pickParam())
                .bounds(px + 8 + paramW + 4, py + 79, 22, 16).build());

        btnCycle = addRenderableWidget(Button.builder(Component.literal("ADDITION"), b -> cycle())
                .bounds(px + 8, py + 99, pw - 16, 16).build());

        int fw = (pw - 16 - 8) / 3;
        fMin = addRenderableWidget(new EditBox(font, px + 8, py + 129, fw, 16, Component.literal("min")));
        fSteps = addRenderableWidget(new EditBox(font, px + 8 + fw + 4, py + 129, fw, 16, Component.literal("steps")));
        fStep = addRenderableWidget(new EditBox(font, px + 8 + (fw + 4) * 2, py + 129, fw, 16, Component.literal("step")));
        fMin.setMaxLength(12);
        fSteps.setMaxLength(12);
        fStep.setMaxLength(12);
        fMin.setValue("0.1");
        fSteps.setValue("1");
        fStep.setValue("0.1");

        int catsW = pw - 16 - 26;
        editCats = new EditBox(font, px + 8, py + 208, catsW, 16, Component.literal("适用部位"));
        editCats.setMaxLength(200);
        editCats.setValue("sword");
        addRenderableWidget(editCats);
        btnPickCats = addRenderableWidget(Button.builder(Component.literal("选…"), b -> pickCats())
                .bounds(px + 8 + catsW + 4, py + 208, 22, 16).build());

        btnCreate = addRenderableWidget(Button.builder(Component.literal("创建"), b -> create())
                .bounds(px + 8, py + ph - 22, (pw - 16) / 2 - 2, 16).build());
        btnCancel = addRenderableWidget(Button.builder(Component.literal("取消"), b -> minecraft.setScreen(parent))
                .bounds(px + 8 + (pw - 16) / 2 + 2, py + ph - 22, (pw - 16) / 2 - 2, 16).build());

        if (sourceId == null && "copy".equals(mode)) {
            mode = "attribute";
        }
        setMode(mode);
    }

    private static String sanitize(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toLowerCase(Locale.ROOT).toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '/' || c == '.') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        String out = sb.toString();
        return out.isEmpty() ? "custom" : out;
    }

    private void setMode(String m) {
        mode = m;
        btnModeCopy.setMessage(Component.literal(("copy".equals(m) ? "★ " : "") + "复制"));
        btnModeAttr.setMessage(Component.literal(("attribute".equals(m) ? "★ " : "") + "属性"));
        btnModePotion.setMessage(Component.literal(("mob_effect".equals(m) ? "★ " : "") + "药水"));
        btnModePaste.setMessage(Component.literal(("paste".equals(m) ? "★ " : "") + "粘贴"));

        boolean isAttr = "attribute".equals(m);
        boolean isPotion = "mob_effect".equals(m);
        boolean isPaste = "paste".equals(m);
        editParam.visible = isAttr || isPotion;
        btnPick.visible = isAttr || isPotion;
        btnCycle.visible = isAttr || isPotion;
        fMin.visible = isAttr || isPotion;
        fSteps.visible = isAttr || isPotion;
        fStep.visible = isAttr || isPotion;
        editCats.visible = !isPaste;
        btnPickCats.visible = !isPaste;

        if (isAttr) {
            if (editParam.getValue().isEmpty() || editParam.getValue().startsWith("minecraft:speed")) {
                editParam.setValue("minecraft:generic.max_health");
            }
            btnCycle.setMessage(Component.literal("运算: " + operation));
        } else if (isPotion) {
            if (editParam.getValue().isEmpty() || editParam.getValue().startsWith("minecraft:generic")) {
                editParam.setValue("minecraft:speed");
            }
            btnCycle.setMessage(Component.literal("触发: " + target));
        }
    }

    /** 多选适用部位 */
    private void pickCats() {
        Set<String> initial = new LinkedHashSet<>();
        for (String s : editCats.getValue().split(",")) {
            if (!s.isBlank()) {
                initial.add(s.trim());
            }
        }
        minecraft.setScreen(new PickScreen(this, "选择适用部位", availableCats, initial,
                picked -> editCats.setValue(String.join(",", picked)),
                WbNames::cats, true));
    }

    /** 从注册表里挑属性 / 药水效果 */
    private void pickParam() {
        boolean isAttr = "attribute".equals(mode);
        boolean isPotion = "mob_effect".equals(mode);
        if (!isAttr && !isPotion) {
            return;
        }
        List<String> ids = new ArrayList<>();
        if (isAttr) {
            for (var attr : net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES.getValues()) {
                var key = net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES.getKey(attr);
                if (key != null) {
                    ids.add(key.toString());
                }
            }
        } else {
            for (var eff : net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getValues()) {
                var key = net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getKey(eff);
                if (key != null) {
                    ids.add(key.toString());
                }
            }
        }
        ids.sort(String::compareTo);
        minecraft.setScreen(new PickScreen(this, isAttr ? "选择属性" : "选择效果", ids,
                picked -> editParam.setValue(picked),
                rawId -> {
                    var rl = net.minecraft.resources.ResourceLocation.tryParse(rawId);
                    if (rl == null) {
                        return rawId;
                    }
                    if (isAttr) {
                        var a = net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES.getValue(rl);
                        return a == null ? rawId : Component.translatable(a.getDescriptionId()).getString();
                    }
                    var e = net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getValue(rl);
                    return e == null ? rawId : Component.translatable(e.getDescriptionId()).getString();
                }));
    }

    private void cycle() {
        if ("attribute".equals(mode)) {
            operation = switch (operation) {
                case "ADDITION" -> "MULTIPLY_BASE";
                case "MULTIPLY_BASE" -> "MULTIPLY_TOTAL";
                default -> "ADDITION";
            };
            btnCycle.setMessage(Component.literal("运算: " + operation));
        } else {
            target = switch (target) {
                case "HURT_SELF" -> "HURT_ATTACKER";
                default -> "HURT_SELF";
            };
            btnCycle.setMessage(Component.literal("触发: " + target));
        }
    }

    /** 读取剪贴板，作为「粘贴 JSON」模式的词条数据 */
    private void readClipboard() {
        pasted = null;
        pasteMsg = "";
        String clip = minecraft == null ? "" : minecraft.keyboardHandler.getClipboard();
        if (clip == null || clip.isBlank()) {
            pasteMsg = "剪贴板是空的，先把词条 JSON 复制进来";
            return;
        }
        try {
            com.google.gson.JsonElement el = com.google.gson.JsonParser.parseString(clip);
            if (!el.isJsonObject()) {
                pasteMsg = "剪贴板内容不是 JSON 对象";
                return;
            }
            JsonObject obj = el.getAsJsonObject();
            if (!obj.has("type")) {
                pasteMsg = "JSON 里缺少 type 字段（例如 \"apotheosis:attribute\"）";
                return;
            }
            pasted = obj;
            pasteMsg = "已读取 " + obj.get("type").getAsString();
            if (obj.has("values") && obj.get("values").isJsonObject()) {
                pasteMsg += " · " + obj.getAsJsonObject("values").size() + " 个品阶";
            }
        } catch (Exception ex) {
            pasteMsg = "剪贴板内容不是合法 JSON";
        }
    }

    private void create() {
        if ("paste".equals(mode)) {
            if (pasted == null) {
                readClipboard();
                if (pasted == null) {
                    return;
                }
            }
            JsonObject p = new JsonObject();
            p.addProperty("id", editId.getValue().trim());
            p.addProperty("mode", "paste");
            p.addProperty("name", editName.getValue().trim());
            p.add("json", pasted.deepCopy());
            WorkbenchNetwork.sendToServer(new EditPacket(EditPacket.A_CREATE, "", p));
            minecraft.setScreen(parent);
            return;
        }
        JsonObject p = new JsonObject();
        p.addProperty("id", editId.getValue().trim());
        p.addProperty("mode", mode);
        p.addProperty("name", editName.getValue().trim());
        if (sourceId != null) {
            p.addProperty("source", sourceId);
        }
        JsonArray rr = new JsonArray();
        for (RarityInfo r : rarities) {
            if (chosen.contains(r.key())) {
                rr.add(r.key());
            }
        }
        p.add("rarities", rr);

        JsonArray cats = new JsonArray();
        for (String s : editCats.getValue().split(",")) {
            String t = s.trim();
            if (!t.isEmpty() && !"none".equals(t)) {
                cats.add(t);
            }
        }
        p.add("cats", cats);

        p.addProperty("min", parse(fMin.getValue(), 0.1));
        p.addProperty("steps", parse(fSteps.getValue(), 1));
        p.addProperty("step", parse(fStep.getValue(), 0.1));
        p.addProperty("attribute", editParam.getValue().trim());
        p.addProperty("effect", editParam.getValue().trim());
        p.addProperty("operation", operation);
        p.addProperty("target", target);

        WorkbenchNetwork.sendToServer(new EditPacket(EditPacket.A_CREATE, "", p));
        minecraft.setScreen(parent);
    }

    private static double parse(String s, double def) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception ex) {
            return def;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.tip = null;

        g.fill(0, 0, width, height, 0xD00A0A10);
        g.fill(px, py, px + pw, py + ph, 0xFF14141C);
        g.fill(px, py, px + pw, py + 1, 0xFF33334A);
        g.fill(px, py + ph - 1, px + pw, py + ph, 0xFF33334A);

        g.drawString(font, "新建词条", px + 8, py + 8, 0xFF9B6CFF, false);
        g.drawString(font, "词条 ID", px + 8, py + 20, 0xFF8C8CA0, false);
        g.drawString(font, "名称（可留空）", px + 8 + idW + 4, py + 20, 0xFF9B6CFF, false);

        if ("attribute".equals(mode)) {
            g.drawString(font, "属性 ID", px + 8, py + 68, 0xFF8C8CA0, false);
        } else if ("mob_effect".equals(mode)) {
            g.drawString(font, "效果 ID", px + 8, py + 68, 0xFF8C8CA0, false);
        } else if ("paste".equals(mode)) {
            g.drawString(font, "从剪贴板读取：", px + 8, py + 68, 0xFF8C8CA0, false);
            g.drawString(font, clipText(pasteMsg, pw - 16), px + 8, py + 79,
                    pasted == null ? 0xFFE05252 : 0xFF4FD16A, false);
        } else {
            g.drawString(font, clipText("将从「" + sourceId + "」复制，品阶数值沿用原词条", pw - 16),
                    px + 8, py + 79, 0xFF8C8CA0, false);
        }

        if ("attribute".equals(mode) || "mob_effect".equals(mode)) {
            g.drawString(font, "数值 保底 / 档数 / 每档加", px + 8, py + 119, 0xFF8C8CA0, false);
        }

        chips.clear();
        if ("paste".equals(mode)) {
            g.drawString(font, "品阶、部位、数值都以 JSON 里的内容为准", px + 8, py + 153, 0xFF9B6CFF, false);
            g.drawString(font, "（values 决定品阶，types 决定部位）", px + 8, py + 167, 0xFF8C8CA0, false);
        } else {
            g.drawString(font, "品阶", px + 8, py + 153, 0xFF9B6CFF, false);
            drawRarityChips(g);
        }

        if (!"paste".equals(mode)) {
            g.drawString(font, "适用部位（逗号分隔：sword/helmet/chestplate/bow…）", px + 8, py + 198, 0xFF8C8CA0, false);
        }

        super.render(g, mouseX, mouseY, partial);

        collectTips();
        if (tip != null && !tip.isEmpty()) {
            g.renderComponentTooltip(font, tip, mouseX, mouseY);
        }
    }

    private void drawRarityChips(GuiGraphics g) {
        int cx = px + 8;
        int cy = py + 164;
        int maxX = px + pw - 8;
        for (RarityInfo r : rarities) {
            String label = WbNames.rarity(r);
            int w = font.width(label) + 12;
            if (cx + w > maxX) {
                cx = px + 8;
                cy += 17;
            }
            boolean on = chosen.contains(r.key());
            if (on) {
                g.fill(cx, cy, cx + w, cy + 14, 0xFF000000 | (r.color() & 0xFFFFFF));
                g.drawString(font, label, cx + 6, cy + 3, 0xFF101018, false);
            } else {
                g.fill(cx, cy, cx + w, cy + 14, 0xFF1D1D28);
                g.drawString(font, label, cx + 6, cy + 3, 0xFF8C8CA0, false);
            }
            chips.add(new Chip(cx, cy, w, 14, r.key(), on));
            setTip(cx, cy, w, 14, List.of(
                    Component.literal("§f品阶 " + WbNames.rarityWithEn(r) + (on ? " §a（已选）" : " §8（未选）")),
                    Component.literal("§7勾选的品阶都会写入词条的 values"),
                    Component.literal("§8取消勾选 = 这个词阶不会出这条词条")));
            cx += w + 3;
        }
    }

    private String clipText(String s, int maxWidth) {
        if (s == null) {
            return "";
        }
        if (font.width(s) <= maxWidth) {
            return s;
        }
        String dots = "…";
        while (s.length() > 1 && font.width(s + dots) > maxWidth) {
            s = s.substring(0, s.length() - 1);
        }
        return s + dots;
    }

    private void setTip(int x, int y, int w, int h, List<Component> lines) {
        if (tip == null && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h) {
            tip = lines;
        }
    }

    private void setTipWidget(AbstractWidget widget, List<Component> lines) {
        if (tip == null && widget != null && widget.visible && widget.isMouseOver(mouseX, mouseY)) {
            tip = lines;
        }
    }

    private static Component t(String s) {
        return Component.literal(s);
    }

    private void collectTips() {
        setTipWidget(editId, List.of(
                t("§f词条 ID"),
                t("§7格式：命名空间:路径，例如 apoth_workbench:custom/my_affix"),
                t("§8只能用 小写字母 / 数字 / _ - / ."),
                t("§f为什么自建词条显示英文"),
                t("§7词条名字/描述在客户端语言文件里，数据包写不进去"),
                t("§7物品上会显示 affix.<词条ID>，可以用 KubeJS 补："),
                t("§8ClientEvents.lang('zh_cn', event => {"),
                t("§8  event.add('affix.<词条ID>', '名字')"),
                t("§8  event.add('affix.<词条ID>.suffix', '· 后缀')"),
                t("§8})")));
        setTipWidget(btnModeCopy, List.of(t("§f复制模板"), t("§7完整复制选中词条，只换 ID"),
                t("§8品阶、部位、数值都沿用原词条")));
        setTipWidget(btnModeAttr, List.of(t("§f属性模板"), t("§7从零建一条属性词条"),
                t("§7需要填属性 ID、运算方式、数值、部位")));
        setTipWidget(btnModePotion, List.of(t("§f药水模板"), t("§7从零建一条药水效果词条"),
                t("§7需要填效果 ID、触发时机、数值、部位")));
        setTipWidget(btnModePaste, List.of(t("§f粘贴 JSON"),
                t("§7从剪贴板读一条完整词条 JSON 来新建"),
                t("§7做顺劈/庆典/处决这类 special 词条就靠它"),
                t("§8JSON 必须含 type 字段，创建前会校验")));
        setTipWidget(btnPick, List.of(t("§f从列表里选"),
                t("§7列出游戏里所有已注册的属性 / 效果"),
                t("§7可以搜中文名，也能搜英文 id"),
                t("§8点一项就填进左边的框")));
        setTipWidget(editName, List.of(
                t("§f名称"),
                t("§7物品上直接显示的名字，例如「霜冻」"),
                t("§7新建后立即生效（会注入客户端语言表）"),
                t("§8留空 = 显示 affix.<词条ID>")));
        setTipWidget(editParam, List.of(
                t("§f" + ("mob_effect".equals(mode) ? "效果 ID" : "属性 ID")),
                t("§7例如 " + ("mob_effect".equals(mode) ? "minecraft:speed" : "minecraft:generic.max_health")),
                t("§8必须是游戏里已注册的 ID")));
        setTipWidget(btnCycle, "attribute".equals(mode)
                ? List.of(t("§f运算方式"), t("§7ADDITION 加算 / MULTIPLY_BASE 乘基础 / MULTIPLY_TOTAL 乘总"))
                : List.of(t("§f触发时机"), t("§7HURT_SELF 自己受伤时 / HURT_ATTACKER 打人时")));
        setTipWidget(fMin, List.of(t("§f保底数值"), t("§7这件装备最差也有这么多")));
        setTipWidget(fSteps, List.of(t("§f分成几档"), t("§7档数越多差距越小"), t("§c不能填 0")));
        setTipWidget(fStep, List.of(t("§f每档加多少"),
                t("§8最好 = 保底 + 档数 × 每档加")));
        setTipWidget(editCats, List.of(
                t("§f适用部位"),
                t("§7英文 id，逗号分隔；留空 = 所有部位"),
                t("§7共 " + availableCats.size() + " 个可选，点右边「选…」直接挑")));
        setTipWidget(btnPickCats, List.of(
                t("§f挑选适用部位"),
                t("§7列出神化当前支持的全部部位（" + availableCats.size() + " 个）"),
                t("§7点一项勾选/取消，多选后点确定")));
        setTipWidget(btnCreate, List.of(t("§f创建"), t("§7生成新词条并写入工作台数据"),
                t("§8回主界面后记得点「保存并重载」")));
        setTipWidget(btnCancel, List.of(t("§f取消"), t("§8返回工作台，不创建")));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        for (Chip c : chips) {
            if (c.hit(mouseX, mouseY)) {
                if (chosen.contains(c.key())) {
                    chosen.remove(c.key());
                } else {
                    chosen.add(c.key());
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
