package cn.blockforge.apothworkbench.client;

import cn.blockforge.apothworkbench.WbLang;
import cn.blockforge.apothworkbench.net.EditPacket;
import cn.blockforge.apothworkbench.net.WorkbenchNetwork;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 给一条词条起中文名（会注入客户端语言表，物品上直接显示）。
 */
public class RenameScreen extends Screen {

    private final Screen parent;
    private final String affixId;
    private final String initial;

    private final int pw = 250;
    private final int ph = 100;
    private int px;
    private int py;

    private EditBox box;
    private int mouseX;
    private int mouseY;
    private java.util.List<Component> tip;

    public RenameScreen(Screen parent, String affixId, String currentName) {
        super(WbLang.tr("apoth_workbench.rename.title"));
        this.parent = parent;
        this.affixId = affixId;
        this.initial = currentName == null ? "" : currentName;
    }

    @Override
    protected void init() {
        super.init();
        px = (width - pw) / 2;
        py = (height - ph) / 2;

        box = new EditBox(font, px + 8, py + 44, pw - 16, 16, WbLang.tr("apoth_workbench.rename.hint"));
        box.setMaxLength(40);
        box.setValue(initial);
        addRenderableWidget(box);
        setInitialFocus(box);

        addRenderableWidget(Button.builder(WbLang.tr("apoth_workbench.btn.save_short"), b -> save())
                .bounds(px + 8, py + ph - 24, (pw - 16) / 2 - 2, 16).build());
        addRenderableWidget(Button.builder(WbLang.tr("apoth_workbench.btn.cancel"), b -> minecraft.setScreen(parent))
                .bounds(px + 8 + (pw - 16) / 2 + 2, py + ph - 24, (pw - 16) / 2 - 2, 16).build());
    }

    private void save() {
        JsonObject p = new JsonObject();
        p.addProperty("name", box.getValue().trim());
        WorkbenchNetwork.sendToServer(new EditPacket(EditPacket.A_SET_NAME, affixId, p));
        minecraft.setScreen(parent);
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

        g.drawString(font, WbLang.s("apoth_workbench.rename.title"), px + 8, py + 8, 0xFF9B6CFF, false);
        g.drawString(font, clip("§8" + affixId, pw - 16), px + 8, py + 22, 0xFF8C8CA0, false);
        g.drawString(font, WbLang.s("apoth_workbench.rename.subtitle"), px + 8, py + 34, 0xFF8C8CA0, false);

        super.render(g, mouseX, mouseY, partial);

        String hint = WbLang.s("apoth_workbench.rename.empty_hint");
        g.drawString(font, clip(hint, pw - 16), px + 8, py + 64, 0xFF8C8CA0, false);
        if (mouseX >= px + 8 && mouseX < px + pw - 8 && mouseY >= py + 62 && mouseY < py + 74) {
            tip = java.util.List.of(
                    Component.literal(WbLang.s("apoth_workbench.rename.tip.1")),
                    Component.literal(WbLang.s("apoth_workbench.rename.tip.2")),
                    Component.literal(WbLang.s("apoth_workbench.rename.tip.3")));
        }
        if (tip != null) {
            g.renderComponentTooltip(font, tip, mouseX, mouseY);
        }
    }

    private String clip(String s, int maxWidth) {
        if (font.width(s) <= maxWidth) {
            return s;
        }
        String dots = "…";
        while (s.length() > 1 && font.width(s + dots) > maxWidth) {
            s = s.substring(0, s.length() - 1);
        }
        return s + dots;
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
