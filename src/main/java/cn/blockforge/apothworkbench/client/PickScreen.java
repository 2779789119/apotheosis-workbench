package cn.blockforge.apothworkbench.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 通用的一列可选列表，带搜索和可拖动滚动条。
 * <p>单选：点一项立即返回；多选：点一项切换勾选，底部点确定。</p>
 */
public class PickScreen extends Screen {

    private static final int C_BG = 0xD00A0A10;
    private static final int C_PANEL = 0xFF14141C;
    private static final int C_BORDER = 0xFF33334A;
    private static final int C_TEXT = 0xFFE8E8F2;
    private static final int C_MUTED = 0xFF8C8CA0;
    private static final int C_ACCENT = 0xFF9B6CFF;

    private static final int ROW = 18;

    private final Screen parent;
    private final String heading;
    private final List<String> all;
    private final List<String> shown = new ArrayList<>();
    private final Function<String, String> labeler;
    private final boolean multi;
    private final Consumer<List<String>> onConfirm;
    private final Set<String> selected = new LinkedHashSet<>();
    private final java.util.Map<String, String> labelCache = new java.util.HashMap<>();

    private final int pw = 320;
    private final int ph = 230;
    private int px;
    private int py;

    private EditBox search;
    private String query = "";
    private double scroll;
    private boolean draggingBar;
    private int mouseX;
    private int mouseY;

    /** 单选 */
    public PickScreen(Screen parent, String heading, List<String> ids, Consumer<String> onPick,
                      Function<String, String> labeler) {
        this(parent, heading, ids, Set.of(), picked -> onPick.accept(picked.isEmpty() ? "" : picked.get(0)), labeler, false);
    }

    /** 多选 */
    public PickScreen(Screen parent, String heading, List<String> ids, Set<String> initial,
                      Consumer<List<String>> onConfirm, Function<String, String> labeler, boolean multi) {
        super(Component.literal(heading));
        this.parent = parent;
        this.heading = heading;
        this.all = ids;
        this.labeler = labeler;
        this.multi = multi;
        this.onConfirm = onConfirm;
        this.selected.addAll(initial);
    }

    @Override
    protected void init() {
        super.init();
        px = (width - pw) / 2;
        py = (height - ph) / 2;

        search = new EditBox(font, px + 8, py + 24, pw - 16, 16, Component.literal("搜索"));
        search.setMaxLength(60);
        search.setValue(query);
        search.setResponder(s -> {
            query = s;
            rebuild();
        });
        addRenderableWidget(search);
        setInitialFocus(search);
        rebuild();

        if (multi) {
            int half = (pw - 16) / 2 - 2;
            addRenderableWidget(Button.builder(Component.literal("确定"), b -> {
                onConfirm.accept(new ArrayList<>(selected));
                minecraft.setScreen(parent);
            }).bounds(px + 8, py + ph - 24, half, 16).build());
            addRenderableWidget(Button.builder(Component.literal("取消"), b -> minecraft.setScreen(parent))
                    .bounds(px + 8 + half + 4, py + ph - 24, half, 16).build());
        }
    }

    private void rebuild() {
        shown.clear();
        String q = query.trim().toLowerCase(Locale.ROOT);
        for (String id : all) {
            if (q.isEmpty()) {
                shown.add(id);
                continue;
            }
            String label = label(id);
            if (id.toLowerCase(Locale.ROOT).contains(q) || label.toLowerCase(Locale.ROOT).contains(q)) {
                shown.add(id);
            }
        }
        double max = Math.max(0, shown.size() * ROW - listHeight());
        if (scroll > max) {
            scroll = max;
        }
        if (scroll < 0) {
            scroll = 0;
        }
    }

    private int listTop() {
        return py + 46;
    }

    private int listHeight() {
        return ph - 66 - (multi ? 26 : 0);
    }

    private int barX() {
        return px + pw - 10;
    }

    private void scrollToMouse(double my) {
        double max = Math.max(0, shown.size() * ROW - listHeight());
        if (max <= 0) {
            scroll = 0;
            return;
        }
        double frac = (my - listTop()) / (double) Math.max(1, listHeight());
        double target = frac * (shown.size() * ROW) - listHeight() / 2.0;
        scroll = Math.max(0, Math.min(max, target));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;

        // 兜底：万一 responder 没触发，也保证过滤结果和输入框一致
        if (search != null && !search.getValue().equals(query)) {
            query = search.getValue();
            rebuild();
        }

        g.fill(0, 0, width, height, C_BG);
        g.fill(px, py, px + pw, py + ph, C_PANEL);
        g.fill(px, py, px + pw, py + 1, C_BORDER);
        g.fill(px, py + ph - 1, px + pw, py + ph, C_BORDER);

        String title = heading + "（" + shown.size() + " 项"
                + (multi ? " · 已选 " + selected.size() : "") + "）";
        g.drawString(font, title, px + 8, py + 10, C_ACCENT, false);

        int top = listTop();
        int h = listHeight();
        int x1 = px + 6;
        int x2 = barX();

        g.enableScissor(x1, top, x2 + 4, top + h);
        int start = (int) Math.max(0, Math.floor(scroll / ROW) - 1);
        int end = Math.min(shown.size(), start + h / ROW + 3);
        for (int i = start; i < end; i++) {
            String id = shown.get(i);
            int y = top + i * ROW - (int) scroll;
            boolean hover = mouseX >= x1 && mouseX < x2 && mouseY >= y && mouseY < y + ROW - 2;
            boolean on = multi && selected.contains(id);
            if (on) {
                g.fill(x1, y, x2, y + ROW - 2, 0xFF3A2F63);
            } else if (hover) {
                g.fill(x1, y, x2, y + ROW - 2, 0xFF2B2545);
            }
            int textX = x1 + 4;
            if (multi) {
                int cbx = x1 + 4;
                g.fill(cbx, y + 5, cbx + 9, y + 14, on ? C_ACCENT : C_PANEL);
                g.fill(cbx, y + 5, cbx + 9, y + 6, C_BORDER);
                g.fill(cbx, y + 13, cbx + 9, y + 14, C_BORDER);
                g.fill(cbx, y + 5, cbx + 1, y + 14, C_BORDER);
                g.fill(cbx + 8, y + 5, cbx + 9, y + 14, C_BORDER);
                if (on) {
                    g.fill(cbx + 2, y + 7, cbx + 7, y + 12, 0xFFFFFFFF);
                }
                textX = cbx + 13;
            }
            String label = label(id);
            boolean same = label.equals(id);
            g.drawString(font, clip(label, x2 - textX - 10), textX, y + 4, C_TEXT, false);
            if (!same) {
                int w = font.width(id);
                if (w < (x2 - textX) / 2) {
                    g.drawString(font, id, x2 - 4 - w, y + 4, C_MUTED, false);
                }
            }
        }
        g.disableScissor();

        int max = (int) Math.max(0, shown.size() * ROW - h);
        g.fill(x2 + 2, top, x2 + 6, top + h, 0x40000000);
        if (max > 0) {
            int barH = Math.max(16, (int) ((double) h * h / (shown.size() * ROW)));
            int barY = top + (int) ((double) (h - barH) * scroll / max);
            g.fill(x2 + 2, barY, x2 + 6, barY + barH, C_ACCENT);
        }

        if (shown.isEmpty()) {
            g.drawString(font, "没有匹配项", px + pw / 2 - font.width("没有匹配项") / 2, top + h / 2, C_MUTED, false);
        }

        super.render(g, mouseX, mouseY, partial);
        if (!multi) {
            g.drawString(font, "§8点一项即可选中 · Esc 返回", px + 8, py + ph - 10, C_MUTED, false);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        int top = listTop();
        int h = listHeight();
        if (mx >= barX() && mx < barX() + 8 && my >= top && my < top + h) {
            draggingBar = true;
            scrollToMouse(my);
            return true;
        }
        if (mx >= px + 6 && mx < barX() && my >= top && my < top + h) {
            int idx = (int) Math.floor((my - top + scroll) / ROW);
            if (idx >= 0 && idx < shown.size()) {
                String id = shown.get(idx);
                if (multi) {
                    if (!selected.remove(id)) {
                        selected.add(id);
                    }
                    return true;
                }
                onConfirm.accept(List.of(id));
                minecraft.setScreen(parent);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingBar) {
            scrollToMouse(my);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        draggingBar = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx >= px + 6 && mx < barX() + 8 && my >= listTop() && my < listTop() + listHeight()) {
            scroll -= delta * ROW;
            double max = Math.max(0, shown.size() * ROW - listHeight());
            scroll = Math.max(0, Math.min(max, scroll));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    /** 翻译结果按 id 缓存，避免每帧每行都去构造 Component 查表 */
    private String label(String id) {
        String v = labelCache.get(id);
        if (v == null) {
            v = labeler.apply(id);
            labelCache.put(id, v);
        }
        return v;
    }

    private String clip(String s, int maxWidth) {
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

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
