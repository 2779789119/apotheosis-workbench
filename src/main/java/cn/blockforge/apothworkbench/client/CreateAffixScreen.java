package cn.blockforge.apothworkbench.client;

import cn.blockforge.apothworkbench.WbLang;
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
        super(WbLang.tr("create.title"));
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
        editId = new EditBox(font, px + 8, py + 30, idW, 16, WbLang.tr("create.label.id"));
        editId.setMaxLength(120);
        editId.setValue(suggestion);
        addRenderableWidget(editId);

        editName = new EditBox(font, px + 8 + idW + 4, py + 30, nameW, 16, WbLang.tr("create.name_hint"));
        editName.setMaxLength(40);
        addRenderableWidget(editName);

        int bw = (pw - 16 - 12) / 4;
        btnModeCopy = addRenderableWidget(Button.builder(WbLang.tr("create.mode.copy"), b -> setMode("copy"))
                .bounds(px + 8, py + 50, bw, 16).build());
        btnModeAttr = addRenderableWidget(Button.builder(WbLang.tr("create.mode.attr"), b -> setMode("attribute"))
                .bounds(px + 8 + bw + 4, py + 50, bw, 16).build());
        btnModePotion = addRenderableWidget(Button.builder(WbLang.tr("create.mode.potion"), b -> setMode("mob_effect"))
                .bounds(px + 8 + (bw + 4) * 2, py + 50, bw, 16).build());
        btnModePaste = addRenderableWidget(Button.builder(WbLang.tr("create.btn.paste"), b -> {
            readClipboard();
            setMode("paste");
        }).bounds(px + 8 + (bw + 4) * 3, py + 50, bw, 16).build());

        int paramW = pw - 16 - 26;
        editParam = new EditBox(font, px + 8, py + 79, paramW, 16, WbLang.tr("create.param_hint"));
        editParam.setMaxLength(120);
        addRenderableWidget(editParam);
        btnPick = addRenderableWidget(Button.builder(WbLang.tr("create.btn.pick"), b -> pickParam())
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
        editCats = new EditBox(font, px + 8, py + 208, catsW, 16, WbLang.tr("create.label.cats_hint"));
        editCats.setMaxLength(200);
        editCats.setValue("sword");
        addRenderableWidget(editCats);
        btnPickCats = addRenderableWidget(Button.builder(WbLang.tr("create.btn.pick"), b -> pickCats())
                .bounds(px + 8 + catsW + 4, py + 208, 22, 16).build());

        btnCreate = addRenderableWidget(Button.builder(WbLang.tr("create.btn.create"), b -> create())
                .bounds(px + 8, py + ph - 22, (pw - 16) / 2 - 2, 16).build());
        btnCancel = addRenderableWidget(Button.builder(WbLang.tr("btn.cancel"), b -> minecraft.setScreen(parent))
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
        btnModeCopy.setMessage(modeLabel("copy".equals(m), "create.mode.copy"));
        btnModeAttr.setMessage(modeLabel("attribute".equals(m), "create.mode.attr"));
        btnModePotion.setMessage(modeLabel("mob_effect".equals(m), "create.mode.potion"));
        btnModePaste.setMessage(modeLabel("paste".equals(m), "create.mode.paste"));

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
            btnCycle.setMessage(WbLang.tr("create.operation", operation));
        } else if (isPotion) {
            if (editParam.getValue().isEmpty() || editParam.getValue().startsWith("minecraft:generic")) {
                editParam.setValue("minecraft:speed");
            }
            btnCycle.setMessage(WbLang.tr("create.target", target));
        }
    }

    /** 模式按钮文字，选中的加 ★ 前缀 */
    private static Component modeLabel(boolean active, String key) {
        return active ? WbLang.tr("create.mode_label_on", WbLang.tr(key)) : WbLang.tr(key);
    }

    /** 多选适用部位 */
    private void pickCats() {
        Set<String> initial = new LinkedHashSet<>();
        for (String s : editCats.getValue().split(",")) {
            if (!s.isBlank()) {
                initial.add(s.trim());
            }
        }
        minecraft.setScreen(new PickScreen(this, WbLang.s("create.pick.cats_title"), availableCats, initial,
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
        minecraft.setScreen(new PickScreen(this, WbLang.s(isAttr ? "create.pick.attr_title" : "create.pick.effect_title"), ids,
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
            btnCycle.setMessage(WbLang.tr("create.operation", operation));
        } else {
            target = switch (target) {
                case "HURT_SELF" -> "HURT_ATTACKER";
                default -> "HURT_SELF";
            };
            btnCycle.setMessage(WbLang.tr("create.target", target));
        }
    }

    /** 读取剪贴板，作为「粘贴 JSON」模式的词条数据 */
    private void readClipboard() {
        pasted = null;
        pasteMsg = "";
        String clip = minecraft == null ? "" : minecraft.keyboardHandler.getClipboard();
        if (clip == null || clip.isBlank()) {
            pasteMsg = WbLang.s("create.paste_msg.empty");
            return;
        }
        try {
            com.google.gson.JsonElement el = com.google.gson.JsonParser.parseString(clip);
            if (!el.isJsonObject()) {
                pasteMsg = WbLang.s("create.paste_msg.not_object");
                return;
            }
            JsonObject obj = el.getAsJsonObject();
            if (!obj.has("type")) {
                pasteMsg = WbLang.s("create.paste_msg.no_type");
                return;
            }
            pasted = obj;
            pasteMsg = WbLang.s("create.paste_msg.ok", obj.get("type").getAsString());
            if (obj.has("values") && obj.get("values").isJsonObject()) {
                pasteMsg += WbLang.s("create.paste_msg.rarities", obj.getAsJsonObject("values").size());
            }
        } catch (Exception ex) {
            pasteMsg = WbLang.s("create.paste_msg.bad_json");
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

        g.drawString(font, WbLang.s("create.title"), px + 8, py + 8, 0xFF9B6CFF, false);
        g.drawString(font, WbLang.s("create.label.id"), px + 8, py + 20, 0xFF8C8CA0, false);
        g.drawString(font, WbLang.s("create.label.name"), px + 8 + idW + 4, py + 20, 0xFF9B6CFF, false);

        if ("attribute".equals(mode)) {
            g.drawString(font, WbLang.s("create.label.attr_id"), px + 8, py + 68, 0xFF8C8CA0, false);
        } else if ("mob_effect".equals(mode)) {
            g.drawString(font, WbLang.s("create.label.effect_id"), px + 8, py + 68, 0xFF8C8CA0, false);
        } else if ("paste".equals(mode)) {
            g.drawString(font, WbLang.s("create.label.read_clipboard"), px + 8, py + 68, 0xFF8C8CA0, false);
            g.drawString(font, clipText(pasteMsg, pw - 16), px + 8, py + 79,
                    pasted == null ? 0xFFE05252 : 0xFF4FD16A, false);
        } else {
            g.drawString(font, clipText(WbLang.s("create.copy_from", String.valueOf(sourceId)), pw - 16),
                    px + 8, py + 79, 0xFF8C8CA0, false);
        }

        if ("attribute".equals(mode) || "mob_effect".equals(mode)) {
            g.drawString(font, WbLang.s("create.values_label"), px + 8, py + 119, 0xFF8C8CA0, false);
        }

        chips.clear();
        if ("paste".equals(mode)) {
            g.drawString(font, WbLang.s("create.paste.1"), px + 8, py + 153, 0xFF9B6CFF, false);
            g.drawString(font, WbLang.s("create.paste.2"), px + 8, py + 167, 0xFF8C8CA0, false);
        } else {
            g.drawString(font, WbLang.s("create.label.rarity"), px + 8, py + 153, 0xFF9B6CFF, false);
            drawRarityChips(g);
        }

        if (!"paste".equals(mode)) {
            g.drawString(font, WbLang.s("create.label.cats"), px + 8, py + 198, 0xFF8C8CA0, false);
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
                    WbLang.tr(on ? "create.chip.on" : "create.chip.off", WbNames.rarityWithEn(r)),
                    WbLang.tr("create.chip.1"),
                    WbLang.tr("create.chip.2")));
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

    /** tooltip 文案：查表得到当前语言的纯文本 */
    private static Component k(String shortKey, Object... args) {
        return Component.literal(WbLang.s(shortKey, args));
    }

    private void collectTips() {
        setTipWidget(editId, List.of(
                k("tip.create.id.1"),
                k("tip.create.id.2"),
                k("tip.create.id.3"),
                k("tip.create.id.4"),
                k("tip.create.id.5"),
                k("tip.create.id.6"),
                k("tip.create.id.7"),
                k("tip.create.id.8"),
                k("tip.create.id.9"),
                k("tip.create.id.10")));
        setTipWidget(btnModeCopy, List.of(k("tip.create.copy.1"), k("tip.create.copy.2"), k("tip.create.copy.3")));
        setTipWidget(btnModeAttr, List.of(k("tip.create.attr.1"), k("tip.create.attr.2"), k("tip.create.attr.3")));
        setTipWidget(btnModePotion, List.of(k("tip.create.potion.1"), k("tip.create.potion.2"), k("tip.create.potion.3")));
        setTipWidget(btnModePaste, List.of(k("tip.create.paste.1"), k("tip.create.paste.2"),
                k("tip.create.paste.3"), k("tip.create.paste.4")));
        setTipWidget(btnPick, List.of(k("tip.create.pick.1"), k("tip.create.pick.2"),
                k("tip.create.pick.3"), k("tip.create.pick.4")));
        setTipWidget(editName, List.of(
                k("tip.create.name.1"),
                k("tip.create.name.2"),
                k("tip.create.name.3"),
                k("tip.create.name.4")));
        setTipWidget(editParam, List.of(
                k("mob_effect".equals(mode) ? "tip.create.param_effect" : "tip.create.param_attr"),
                k("tip.create.param_example",
                        "mob_effect".equals(mode) ? "minecraft:speed" : "minecraft:generic.max_health"),
                k("tip.create.param.3")));
        setTipWidget(btnCycle, "attribute".equals(mode)
                ? List.of(k("tip.create.cycle.op.1"), k("tip.create.cycle.op.2"))
                : List.of(k("tip.create.cycle.target.1"), k("tip.create.cycle.target.2")));
        setTipWidget(fMin, List.of(k("tip.create.min.1"), k("tip.create.min.2")));
        setTipWidget(fSteps, List.of(k("tip.create.steps.1"), k("tip.create.steps.2"), k("tip.create.steps.3")));
        setTipWidget(fStep, List.of(k("tip.create.step.1"), k("tip.create.step.2")));
        setTipWidget(editCats, List.of(
                k("tip.create.cats.1"),
                k("tip.create.cats.2"),
                k("tip.create.cats.3", availableCats.size())));
        setTipWidget(btnPickCats, List.of(
                k("tip.create.pickcats.1"),
                k("tip.create.pickcats.2", availableCats.size()),
                k("tip.create.pickcats.3")));
        setTipWidget(btnCreate, List.of(k("tip.create.create.1"), k("tip.create.create.2"), k("tip.create.create.3")));
        setTipWidget(btnCancel, List.of(k("tip.create.cancel.1"), k("tip.create.cancel.2")));
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
