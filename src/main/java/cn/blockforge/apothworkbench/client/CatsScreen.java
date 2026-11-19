package cn.blockforge.apothworkbench.client;

import cn.blockforge.apothworkbench.net.EditPacket;
import cn.blockforge.apothworkbench.net.WorkbenchNetwork;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 修改一条词条的适用部位。
 */
public class CatsScreen extends Screen {

    private static final int C_TEXT = 0xFFE8E8F2;
    private static final int C_MUTED = 0xFF8C8CA0;
    private static final int C_ACCENT = 0xFF9B6CFF;
    private static final int C_BORDER = 0xFF33334A;

    private final Screen parent;
    private final String affixId;
    private final List<String> available;
    private final Set<String> chosen = new LinkedHashSet<>();

    private final int pw = 300;
    private final int ph = 170;
    private int px;
    private int py;

    private final List<Chip> chips = new ArrayList<>();
    private int mouseX;
    private int mouseY;
    private List<Component> tip;

    private record Chip(int x, int y, int w, int h, String key, boolean on) {
        boolean hit(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    public CatsScreen(Screen parent, String affixId, String catsCsv, List<String> available) {
        super(Component.literal("修改适用部位"));
        this.parent = parent;
        this.affixId = affixId;
        this.available = List.copyOf(available);
        if (catsCsv != null && !catsCsv.isBlank()) {
            for (String s : catsCsv.split(",")) {
                if (!s.isBlank()) {
                    chosen.add(s.trim());
                }
            }
        }
    }

    @Override
    protected void init() {
        super.init();
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        int half = (pw - 16) / 2 - 2;
        addRenderableWidget(Button.builder(Component.literal("保存"), b -> save())
                .bounds(px + 8, py + ph - 22, half, 16).build());
        addRenderableWidget(Button.builder(Component.literal("取消"), b -> minecraft.setScreen(parent))
                .bounds(px + 8 + half + 4, py + ph - 22, half, 16).build());
    }

    private void save() {
        JsonObject p = new JsonObject();
        JsonArray arr = new JsonArray();
        for (String s : chosen) {
            arr.add(s);
        }
        p.add("cats", arr);
        WorkbenchNetwork.sendToServer(new EditPacket(EditPacket.A_SET_CATS, affixId, p));
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.tip = null;

        g.fill(0, 0, width, height, 0xD00A0A10);
        g.fill(px, py, px + pw, py + ph, 0xFF14141C);
        g.fill(px, py, px + pw, py + 1, C_BORDER);
        g.fill(px, py + ph - 1, px + pw, py + ph, C_BORDER);

        g.drawString(font, "修改适用部位", px + 8, py + 8, C_ACCENT, false);
        g.drawString(font, "点击切换；全不选 = 所有部位都能出", px + 8, py + 22, C_MUTED, false);
        g.drawString(font, "§8" + affixId, px + 8, py + 36, C_TEXT, false);

        chips.clear();
        int cx = px + 8;
        int cy = py + 52;
        int maxX = px + pw - 8;
        for (String key : available) {
            String label = WbNames.cats(key);
            int w = font.width(label) + 14;
            if (cx + w > maxX) {
                cx = px + 8;
                cy += 18;
            }
            boolean on = chosen.contains(key);
            if (on) {
                g.fill(cx, cy, cx + w, cy + 15, 0xFF6C5CE7);
                g.drawString(font, label, cx + 7, cy + 4, 0xFFFFFFFF, false);
            } else {
                g.fill(cx, cy, cx + w, cy + 15, 0xFF1D1D28);
                g.drawString(font, label, cx + 7, cy + 4, C_MUTED, false);
            }
            if (mouseX >= cx && mouseX < cx + w && mouseY >= cy && mouseY < cy + 15) {
                if (tip == null) {
                    tip = List.of(Component.literal("§f" + label + " §8(" + key + ")"),
                            Component.literal(on ? "§7已选中，点击取消" : "§7点击选中"));
                }
            }
            chips.add(new Chip(cx, cy, w, 15, key, on));
            cx += w + 4;
        }

        g.drawString(font, "已选 " + chosen.size() + " 个部位", px + 8, py + ph - 38, C_MUTED, false);

        super.render(g, mouseX, mouseY, partial);
        if (tip != null) {
            g.renderComponentTooltip(font, tip, mouseX, mouseY);
        }
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
