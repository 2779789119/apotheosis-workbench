package cn.blockforge.apothworkbench.client;

import cn.blockforge.apothworkbench.WB;
import cn.blockforge.apothworkbench.data.RarityInfo;
import cn.blockforge.apothworkbench.net.AffixDto;
import cn.blockforge.apothworkbench.net.EditPacket;
import cn.blockforge.apothworkbench.net.SnapshotPacket;
import cn.blockforge.apothworkbench.net.WorkbenchNetwork;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 神化工作台主界面（纯手绘 + 原版控件混用）。
 */
public class WorkbenchScreen extends Screen {

    public static WorkbenchScreen ACTIVE;

    private static final int C_BG = 0xF00A0A10;
    private static final int C_PANEL = 0xFF14141C;
    private static final int C_PANEL2 = 0xFF1D1D28;
    private static final int C_BORDER = 0xFF33334A;
    private static final int C_TEXT = 0xFFE8E8F2;
    private static final int C_MUTED = 0xFF8C8CA0;
    private static final int C_ACCENT = 0xFF9B6CFF;
    private static final int C_GOOD = 0xFF4FD16A;
    private static final int C_BAD = 0xFFE05252;
    private static final int C_WARN = 0xFFE0A83C;

    private final List<RarityInfo> rarities = new ArrayList<>();
    private final List<String> categories = new ArrayList<>();
    private final List<AffixDto> allAffixes = new ArrayList<>();
    private final List<AffixDto> shown = new ArrayList<>();
    private final Map<String, String> descCache = new LinkedHashMap<>();
    private final Map<String, String> nameCache = new HashMap<>();
    private final Set<String> checked = new LinkedHashSet<>();
    private final Set<String> pendingDesc = new HashSet<>();
    private int[] stats = new int[6];

    private String query = "";
    private String filter = "all";
    private String rarityFilter;
    private String selectedId;
    private String editRarity;
    private String lastCheckedId;
    private String batchRarity;
    private String confirmKey;
    private long confirmUntil;
    private double scroll;
    private boolean draggingBar;
    private int rowH = 28;
    private int[] lastStats = new int[0];
    private int[] lastBtnStats = new int[0];
    private String headerInfo = "";
    private String previewKey = "";
    private String previewText;

    private int hx;
    private int hy;
    private int hw;
    private int hh;
    private int lx;
    private int ly;
    private int lw;
    private int lh;
    private int mx;
    private int my;
    private int mw;
    private int mh;
    private int rx;
    private int ry;
    private int rw;
    private int rh;

    private EditBox search;
    private EditBox fMin;
    private EditBox fSteps;
    private EditBox fStep;
    private Button btnSave;
    private Button btnRefresh;
    private Button btnClose;
    private Button btnStatus;
    private Button btnRevert;
    private Button btnDup;
    private Button btnApply;
    private Button btnSync;
    private Button btnExport;
    private Button btnRename;
    private Button btnCats;
    private Button btnCopyJson;
    private Button btnPasteJson;
    private Button btnBatchRarity;
    private Button bEnable;
    private Button bDisable;
    private Button bDelete;
    private Button bRarityOn;
    private Button bRarityOff;
    private Button bClearSel;
    private Button btnRarity;
    private Button btnDelete;
    private final List<Button> filterButtons = new ArrayList<>();

    private final List<Chip> detailChips = new ArrayList<>();
    private final List<Chip> filterChips = new ArrayList<>();

    private int mouseX;
    private int mouseY;
    private List<Component> tip;

    private record Chip(int x, int y, int w, int h, String key, boolean on) {
        boolean hit(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    public WorkbenchScreen(SnapshotPacket packet) {
        super(Component.literal("神化工作台"));
        ACTIVE = this;
        apply(packet);
    }

    private void apply(SnapshotPacket packet) {
        rarities.clear();
        rarities.addAll(packet.rarities);
        categories.clear();
        categories.addAll(packet.categories);
        allAffixes.clear();
        allAffixes.addAll(packet.affixes);
        descCache.clear();
        pendingDesc.clear();
        nameCache.clear();
        stats = packet.stats;
        checked.removeIf(id -> find(id) == null);
        rebuild();
        if (selectedId != null && find(selectedId) == null) {
            selectedId = null;
        }
    }

    public void update(SnapshotPacket packet) {
        apply(packet);
        syncDetail();
    }

    @Override
    protected void init() {
        super.init();
        WbNames.clearCache();
        int pad = 6;
        int headerH = 22;
        int footerH = 22;

        hx = pad;
        hy = pad;
        hw = width - pad * 2;
        hh = headerH;

        ly = hy + hh + 4;
        lh = height - ly - footerH - pad;
        lx = pad;
        lw = Math.min(130, Math.max(92, width / 5));

        rx = width - pad - Math.min(230, Math.max(160, width / 3));
        rw = width - pad - rx;
        ry = ly;
        rh = lh;

        mx = lx + lw + 4;
        mw = rx - 4 - mx;
        my = ly;
        mh = lh;

        rowH = 28;

        // 顶栏
        int bx = width - pad;
        btnClose = addRenderableWidget(Button.builder(Component.literal("关闭"), b -> onClose())
                .bounds(bx - 44, hy + 3, 44, 16).build());
        bx -= 48;
        btnRefresh = addRenderableWidget(Button.builder(Component.literal("刷新"), b ->
                WorkbenchNetwork.sendToServer(new EditPacket(EditPacket.A_REFRESH, "")))
                .bounds(bx - 44, hy + 3, 44, 16).build());
        bx -= 48;
        btnSave = addRenderableWidget(Button.builder(Component.literal("保存并重载"), b -> save())
                .bounds(bx - 90, hy + 3, 90, 16).build());
        bx -= 94;
        btnExport = addRenderableWidget(Button.builder(Component.literal("导出脚本"), b -> export())
                .bounds(bx - 70, hy + 3, 70, 16).build());

        // 左栏筛选
        filterButtons.clear();
        String[][] filters = {{"all", "全部词条"}, {"mod", "已修改"}, {"disabled", "已禁用"}, {"deleted", "已删除"}, {"custom", "自定义"}};
        for (int i = 0; i < filters.length; i++) {
            String key = filters[i][0];
            Button b = addRenderableWidget(Button.builder(Component.literal(filters[i][1]), btn -> {
                filter = key;
                scroll = 0;
                rebuild();
            }).bounds(lx, ly + i * 18, lw, 16).build());
            filterButtons.add(b);
        }

        // 新建入口常驻左栏，不依赖是否选中词条
        addRenderableWidget(Button.builder(Component.literal("＋ 新建词条"), b -> newAffix())
                .bounds(lx, ly + filters.length * 18 + 2, lw, 16).build());

        // 底部搜索框
        search = new EditBox(font, pad, height - pad - 16, Math.min(260, width / 2), 16, Component.literal("搜索"));
        search.setMaxLength(80);
        search.setValue(query);
        search.setResponder(s -> {
            query = s;
            scroll = 0;
            rebuild();
        });
        addRenderableWidget(search);

        // 右栏控件
        int by = ry + rh - 108;
        int half = (rw - 8) / 2 - 2;
        btnRarity = addRenderableWidget(Button.builder(Component.literal("品阶: -"), b -> {
            AffixDto a = selected();
            if (a == null || a.rarityKeys().isEmpty()) {
                return;
            }
            int idx = a.rarityKeys().indexOf(editRarity);
            idx = (idx + 1) % a.rarityKeys().size();
            editRarity = a.rarityKeys().get(idx);
            syncDetail();
        }).bounds(rx + 4, by, rw - 8, 16).build());

        int fw = (rw - 8 - 8) / 3;
        fMin = addRenderableWidget(new EditBox(font, rx + 4, by + 30, fw, 16, Component.literal("min")));
        fSteps = addRenderableWidget(new EditBox(font, rx + 4 + fw + 4, by + 30, fw, 16, Component.literal("steps")));
        fStep = addRenderableWidget(new EditBox(font, rx + 4 + (fw + 4) * 2, by + 30, fw, 16, Component.literal("step")));
        fMin.setMaxLength(12);
        fSteps.setMaxLength(12);
        fStep.setMaxLength(12);

        btnApply = addRenderableWidget(Button.builder(Component.literal("保存数值"), b -> applyValues())
                .bounds(rx + 4, by + 50, half, 16).build());
        btnSync = addRenderableWidget(Button.builder(Component.literal("同步全部"), b -> syncValues())
                .bounds(rx + 4 + half + 4, by + 50, half, 16).build());
        btnStatus = addRenderableWidget(Button.builder(Component.literal("禁用"), b -> toggleStatus())
                .bounds(rx + 4, by + 70, half, 16).build());
        btnRevert = addRenderableWidget(Button.builder(Component.literal("恢复原样"), b -> revert())
                .bounds(rx + 4 + half + 4, by + 70, half, 16).build());
        int third = (rw - 8 - 8) / 3;
        btnDup = addRenderableWidget(Button.builder(Component.literal("复制"), b -> duplicate())
                .bounds(rx + 4, by + 90, third, 16).build());
        btnRename = addRenderableWidget(Button.builder(Component.literal("改名"), b -> rename())
                .bounds(rx + 4 + third + 4, by + 90, third, 16).build());
        btnDelete = addRenderableWidget(Button.builder(Component.literal("删除"), b -> delete())
                .bounds(rx + 4 + (third + 4) * 2, by + 90, third, 16).build());

        // 单条模式：小按钮（改部位 / 复制 JSON / 粘贴 JSON）
        btnCats = addRenderableWidget(Button.builder(Component.literal("改部位"), b -> editCats())
                .bounds(rx + rw - 50, ry + 49, 46, 13).build());
        btnCopyJson = addRenderableWidget(Button.builder(Component.literal("拷"), b -> copyJson())
                .bounds(rx + rw - 50, ry + 13, 22, 13).build());
        btnPasteJson = addRenderableWidget(Button.builder(Component.literal("贴"), b -> pasteJson())
                .bounds(rx + rw - 26, ry + 13, 22, 13).build());

        // 批量模式
        btnBatchRarity = addRenderableWidget(Button.builder(Component.literal("品阶: -"), b -> cycleBatchRarity())
                .bounds(rx + 4, by, rw - 8, 16).build());
        bEnable = addRenderableWidget(Button.builder(Component.literal("批量启用"), b -> batch("enable"))
                .bounds(rx + 4, by + 20, half, 16).build());
        bDisable = addRenderableWidget(Button.builder(Component.literal("批量禁用"), b -> batch("disable"))
                .bounds(rx + 4 + half + 4, by + 20, half, 16).build());
        bRarityOn = addRenderableWidget(Button.builder(Component.literal("加入品阶"), b -> batch("rarity_on"))
                .bounds(rx + 4, by + 40, half, 16).build());
        bRarityOff = addRenderableWidget(Button.builder(Component.literal("移出品阶"), b -> batch("rarity_off"))
                .bounds(rx + 4 + half + 4, by + 40, half, 16).build());
        bDelete = addRenderableWidget(Button.builder(Component.literal("批量删除"), b -> batch("delete"))
                .bounds(rx + 4, by + 60, half, 16).build());
        bClearSel = addRenderableWidget(Button.builder(Component.literal("清空选择"), b -> {
            checked.clear();
            syncDetail();
        }).bounds(rx + 4 + half + 4, by + 60, half, 16).build());

        rebuild();
        syncDetail();
        setInitialFocus(search);
    }

    @Override
    public void onClose() {
        if (ACTIVE == this) {
            ACTIVE = null;
        }
        super.onClose();
    }

    // ------------------------------------------------------------------ 数据

    private AffixDto find(String id) {
        for (AffixDto a : allAffixes) {
            if (a.id().equals(id)) {
                return a;
            }
        }
        return null;
    }

    private AffixDto selected() {
        return selectedId == null ? null : find(selectedId);
    }

    private void rebuild() {
        shown.clear();
        String q = query.trim().toLowerCase(Locale.ROOT);
        for (AffixDto a : allAffixes) {
            if (!matchFilter(a)) {
                continue;
            }
            if (rarityFilter != null && !a.rarityKeys().contains(rarityFilter)) {
                continue;
            }
            if (!q.isEmpty()) {
                StringBuilder hay = new StringBuilder();
                hay.append(a.id()).append(' ').append(a.type()).append(' ').append(WbNames.type(a.type()))
                        .append(' ').append(a.summary()).append(' ').append(a.cats()).append(' ').append(displayName(a));
                for (String k : a.rarityKeys()) {
                    hay.append(' ').append(rName(k));
                }
                if (!hay.toString().toLowerCase(Locale.ROOT).contains(q)) {
                    continue;
                }
            }
            shown.add(a);
        }
        clampScroll();
    }

    private boolean matchFilter(AffixDto a) {
        return switch (filter) {
            case "mod" -> a.has(AffixDto.F_MODIFIED);
            case "disabled" -> a.status() == WB.ST_DISABLED;
            case "deleted" -> a.status() == WB.ST_DELETED;
            case "custom" -> a.has(AffixDto.F_CUSTOM);
            default -> true;
        };
    }

    private void clampScroll() {
        double max = Math.max(0, shown.size() * rowH - mh + 6);
        if (scroll > max) {
            scroll = max;
        }
        if (scroll < 0) {
            scroll = 0;
        }
    }

    private String displayName(AffixDto a) {
        if (a.name() != null && !a.name().isBlank()) {
            return a.name();
        }
        String cached = nameCache.get(a.id());
        if (cached != null) {
            return cached;
        }
        String key = "affix." + a.id();
        String s = Component.translatable(key).getString();
        if (s.equals(key)) {
            int i = a.id().indexOf(':');
            s = i >= 0 ? a.id().substring(i + 1) : a.id();
        }
        nameCache.put(a.id(), s);
        return s;
    }

    private void syncDetail() {
        if (btnStatus == null) {
            return; // 界面还没 init 完，等 init 里再同步
        }
        AffixDto a = selected();
        boolean has = a != null;
        boolean batchMode = !checked.isEmpty();

        // 单条控件
        boolean single = has && !batchMode;
        btnStatus.visible = single;
        btnRevert.visible = single;
        btnDup.visible = single;
        btnDelete.visible = single;
        btnApply.visible = single;
        btnSync.visible = single;
        btnRarity.visible = single;
        fMin.visible = single;
        fSteps.visible = single;
        fStep.visible = single;
        btnCopyJson.visible = single;
        btnPasteJson.visible = single;
        btnRename.visible = single;
        btnCats.visible = single && a.has(AffixDto.F_CATS_EDITABLE);

        // 批量控件
        btnBatchRarity.visible = batchMode;
        bEnable.visible = batchMode;
        bDisable.visible = batchMode;
        bRarityOn.visible = batchMode;
        bRarityOff.visible = batchMode;
        bDelete.visible = batchMode;
        bClearSel.visible = batchMode;

        if (batchMode) {
            if (batchRarity == null && !rarities.isEmpty()) {
                batchRarity = rarities.get(rarities.size() - 1).key();
            }
            btnBatchRarity.setMessage(Component.literal("批量品阶: " + (batchRarity == null ? "未选" : rName(batchRarity))));
            boolean delConfirm = "batch-delete".equals(confirmKey) && System.currentTimeMillis() < confirmUntil;
            bDelete.setMessage(Component.literal(delConfirm ? "确认删除？" : "批量删除"));
            bRarityOn.active = batchRarity != null;
            bRarityOff.active = batchRarity != null;
            return;
        }
        if (!has) {
            return;
        }
        boolean disabled = a.status() != WB.ST_ACTIVE;
        btnStatus.setMessage(Component.literal(disabled ? "启用词条" : "禁用词条"));
        boolean delConfirm = ("del:" + a.id()).equals(confirmKey) && System.currentTimeMillis() < confirmUntil;
        btnDelete.setMessage(Component.literal(a.has(AffixDto.F_CUSTOM)
                ? (delConfirm ? "确认？" : "删除")
                : (delConfirm ? "确认？" : "屏蔽")));
        boolean revConfirm = ("rev:" + a.id()).equals(confirmKey) && System.currentTimeMillis() < confirmUntil;
        btnRevert.setMessage(Component.literal(revConfirm ? "确认恢复？" : "恢复原样"));
        btnRevert.active = a.has(AffixDto.F_HAS_ORIGINAL);

        List<String> keys = a.rarityKeys();
        if (editRarity == null || !keys.contains(editRarity)) {
            editRarity = keys.isEmpty() ? null : keys.get(keys.size() - 1);
        }
        btnRarity.setMessage(Component.literal(editRarity == null ? "品阶: 无（点击启用品阶）" : "编辑品阶: " + rName(editRarity)));
        btnRarity.active = !keys.isEmpty();

        boolean editable = false;
        if (editRarity != null) {
            String text = a.rarityTexts().get(keys.indexOf(editRarity));
            editable = text.indexOf('|') >= 0;
            if (editable) {
                String[] parts = text.split("\\|", -1);
                if (parts.length >= 3) {
                    fMin.setValue(parts[0]);
                    fSteps.setValue(parts[1]);
                    fStep.setValue(parts[2]);
                }
            }
        }
        fMin.setEditable(editable);
        fSteps.setEditable(editable);
        fStep.setEditable(editable);
        btnApply.active = editable;
    }

    // ------------------------------------------------------------------ 操作

    private void save() {
        WorkbenchNetwork.sendToServer(new EditPacket(EditPacket.A_SAVE, ""));
    }

    private void export() {
        boolean all = hasShiftDown();
        WorkbenchNetwork.sendToServer(new EditPacket(all ? EditPacket.A_EXPORT_ALL : EditPacket.A_EXPORT, ""));
    }

    /** 危险操作二次确认：第一次点击记下，3.5 秒内再点才执行 */
    private boolean confirm(String key) {
        long now = System.currentTimeMillis();
        if (key.equals(confirmKey) && now < confirmUntil) {
            confirmKey = null;
            return true;
        }
        confirmKey = key;
        confirmUntil = now + 3500;
        return false;
    }

    private void chat(String msg) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal("§c[工作台] " + msg), false);
        }
    }

    private void toggleStatus() {
        AffixDto a = selected();
        if (a == null) {
            return;
        }
        JsonObject p = new JsonObject();
        p.addProperty("status", a.status() == WB.ST_ACTIVE ? WB.ST_DISABLED : WB.ST_ACTIVE);
        WorkbenchNetwork.sendToServer(new EditPacket(EditPacket.A_SET_STATUS, a.id(), p));
    }

    private void revert() {
        AffixDto a = selected();
        if (a == null || !a.has(AffixDto.F_HAS_ORIGINAL)) {
            return;
        }
        if (!confirm("rev:" + a.id())) {
            syncDetail();
            return;
        }
        WorkbenchNetwork.sendToServer(new EditPacket(EditPacket.A_REVERT, a.id()));
    }

    private void delete() {
        AffixDto a = selected();
        if (a == null) {
            return;
        }
        if (!confirm("del:" + a.id())) {
            syncDetail();
            return;
        }
        if (a.has(AffixDto.F_CUSTOM)) {
            selectedId = null;
        }
        JsonObject p = new JsonObject();
        p.addProperty("status", WB.ST_DELETED);
        WorkbenchNetwork.sendToServer(new EditPacket(EditPacket.A_SET_STATUS, a.id(), p));
    }

    private void editCats() {
        AffixDto a = selected();
        if (a == null) {
            return;
        }
        if (!a.has(AffixDto.F_CATS_EDITABLE)) {
            chat("该词条类型没有部位字段，无法修改");
            return;
        }
        minecraft.setScreen(new CatsScreen(this, a.id(), a.cats(), categories));
    }

    private void rename() {
        AffixDto a = selected();
        if (a == null) {
            return;
        }
        minecraft.setScreen(new RenameScreen(this, a.id(), a.name()));
    }

    private void copyJson() {
        AffixDto a = selected();
        if (a == null) {
            return;
        }
        WorkbenchNetwork.sendToServer(new EditPacket(EditPacket.A_REQUEST_JSON, a.id()));
    }

    private void pasteJson() {
        AffixDto a = selected();
        if (a == null) {
            return;
        }
        String clip = minecraft == null ? "" : minecraft.keyboardHandler.getClipboard();
        if (clip == null || clip.isBlank()) {
            chat("剪贴板是空的");
            return;
        }
        com.google.gson.JsonElement el;
        try {
            el = com.google.gson.JsonParser.parseString(clip);
        } catch (Exception ex) {
            chat("剪贴板内容不是合法 JSON");
            return;
        }
        if (!el.isJsonObject()) {
            chat("剪贴板内容必须是 JSON 对象");
            return;
        }
        JsonObject p = new JsonObject();
        p.add("json", el);
        WorkbenchNetwork.sendToServer(new EditPacket(EditPacket.A_SET_JSON, a.id(), p));
        descCache.clear();
    }

    private void cycleBatchRarity() {
        if (rarities.isEmpty()) {
            return;
        }
        int idx = -1;
        for (int i = 0; i < rarities.size(); i++) {
            if (rarities.get(i).key().equals(batchRarity)) {
                idx = i;
                break;
            }
        }
        idx = (idx + 1) % rarities.size();
        batchRarity = rarities.get(idx).key();
        syncDetail();
    }

    private void batch(String op) {
        if (checked.isEmpty()) {
            return;
        }
        if ("delete".equals(op) && !confirm("batch-delete")) {
            syncDetail();
            return;
        }
        JsonObject p = new JsonObject();
        com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
        for (String id : checked) {
            arr.add(id);
        }
        p.add("ids", arr);
        p.addProperty("op", op);
        if (batchRarity != null) {
            p.addProperty("rarity", batchRarity);
        }
        WorkbenchNetwork.sendToServer(new EditPacket(EditPacket.A_BATCH, "", p));
        confirmKey = null;
    }

    private void duplicate() {
        AffixDto a = selected();
        if (a == null) {
            return;
        }
        minecraft.setScreen(new CreateAffixScreen(this, a.id(), rarities, categories, "copy"));
    }

    /** 左栏「＋ 新建词条」：有选中就默认拿它当模板，没选中就从属性模板开始 */
    private void newAffix() {
        AffixDto a = checked.isEmpty() ? selected() : null;
        String src = a == null ? null : a.id();
        minecraft.setScreen(new CreateAffixScreen(this, src, rarities, categories, src == null ? "attribute" : "copy"));
    }

    private void applyValues() {
        AffixDto a = selected();
        if (a == null || editRarity == null) {
            return;
        }
        JsonObject p = new JsonObject();
        p.addProperty("rarity", editRarity);
        p.addProperty("min", parse(fMin.getValue(), 0.1));
        p.addProperty("steps", parse(fSteps.getValue(), 1));
        p.addProperty("step", parse(fStep.getValue(), 0.1));
        WorkbenchNetwork.sendToServer(new EditPacket(EditPacket.A_SET_VALUES, a.id(), p));
    }

    private void syncValues() {
        AffixDto a = selected();
        if (a == null) {
            return;
        }
        JsonObject p = new JsonObject();
        p.addProperty("min", parse(fMin.getValue(), 0.1));
        p.addProperty("steps", parse(fSteps.getValue(), 1));
        p.addProperty("step", parse(fStep.getValue(), 0.1));
        WorkbenchNetwork.sendToServer(new EditPacket(EditPacket.A_SYNC_VALUES, a.id(), p));
    }

    private static double parse(String s, double def) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception ex) {
            return def;
        }
    }

    private void toggleRarity(String key, boolean on) {
        AffixDto a = selected();
        if (a == null) {
            return;
        }
        JsonObject p = new JsonObject();
        p.addProperty("rarity", key);
        p.addProperty("on", on);
        WorkbenchNetwork.sendToServer(new EditPacket(EditPacket.A_TOGGLE_RARITY, a.id(), p));
        editRarity = on ? key : editRarity;
    }

    // ------------------------------------------------------------------ 交互

    @Override
    public boolean mouseClicked(double px, double py, int button) {
        if (super.mouseClicked(px, py, button)) {
            return true;
        }
        for (Chip c : detailChips) {
            if (c.hit(px, py)) {
                toggleRarity(c.key(), !c.on());
                return true;
            }
        }
        for (Chip c : filterChips) {
            if (c.hit(px, py)) {
                rarityFilter = c.key().equals(rarityFilter) ? null : c.key();
                scroll = 0;
                rebuild();
                return true;
            }
        }
        if (px >= mx + mw - 7 && px < mx + mw && py >= my && py < my + mh) {
            draggingBar = true;
            scrollTo(py);
            return true;
        }
        if (inside(px, py, mx, my, mw, mh)) {
            int idx = (int) Math.floor((py - my - 2 + scroll) / rowH);
            if (idx >= 0 && idx < shown.size()) {
                AffixDto a = shown.get(idx);
                boolean inCheckbox = px < mx + 15;
                if (inCheckbox || hasControlDown()) {
                    toggleChecked(a.id(), hasShiftDown());
                    return true;
                }
                if (hasShiftDown() && lastCheckedId != null) {
                    rangeCheck(lastCheckedId, a.id());
                    return true;
                }
                if (!a.id().equals(selectedId)) {
                    selectedId = a.id();
                    editRarity = null;
                    syncDetail();
                }
                lastCheckedId = a.id();
                return true;
            }
        }
        return false;
    }

    private void toggleChecked(String id, boolean shiftRange) {
        if (shiftRange && lastCheckedId != null) {
            rangeCheck(lastCheckedId, id);
            return;
        }
        if (!checked.remove(id)) {
            checked.add(id);
        }
        lastCheckedId = id;
        syncDetail();
    }

    private void rangeCheck(String fromId, String toId) {
        int a = indexOfShown(fromId);
        int b = indexOfShown(toId);
        if (a < 0 || b < 0) {
            return;
        }
        int lo = Math.min(a, b);
        int hi = Math.max(a, b);
        for (int i = lo; i <= hi; i++) {
            checked.add(shown.get(i).id());
        }
        syncDetail();
    }

    private int indexOfShown(String id) {
        for (int i = 0; i < shown.size(); i++) {
            if (shown.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private void scrollTo(double py) {
        double frac = (py - my) / Math.max(1, mh);
        double max = Math.max(0, shown.size() * rowH - mh + 6);
        scroll = Math.max(0, Math.min(max, frac * (shown.size() * rowH) - mh / 2.0));
    }

    @Override
    public boolean mouseDragged(double px, double py, int button, double dx, double dy) {
        if (draggingBar) {
            scrollTo(py);
            return true;
        }
        return super.mouseDragged(px, py, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double px, double py, int button) {
        draggingBar = false;
        return super.mouseReleased(px, py, button);
    }

    @Override
    public boolean mouseScrolled(double px, double py, double delta) {
        if (inside(px, py, mx, my, mw, mh)) {
            scroll -= delta * rowH;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(px, py, delta);
    }

    private static boolean inside(double px, double py, int x, int y, int w, int h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    @Override
    public boolean isPauseScreen() {
        // 单人游戏打开工作台时暂停世界，避免一边改数据一边被怪追
        return true;
    }

    // ------------------------------------------------------------------ 绘制

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.tip = null;

        g.fill(0, 0, width, height, C_BG);
        drawHeader(g);
        drawSidebar(g);
        drawList(g);
        drawDetail(g);

        super.render(g, mouseX, mouseY, partial);

        drawFooter(g);
        collectWidgetTips();
        if (tip != null && !tip.isEmpty()) {
            g.renderComponentTooltip(font, tip, mouseX, mouseY);
        }
    }

    // ------------------------------------------------------------------ 悬浮提示

    /** 提示内容延迟构造：只有真的悬停时才拼字符串，避免每帧为几十个按钮创建 Component */
    private void setTip(int x, int y, int w, int h, Supplier<List<Component>> supplier) {
        if (tip == null && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h) {
            tip = supplier.get();
        }
    }

    private void setTip(int x, int y, int w, int h, List<Component> lines) {
        setTip(x, y, w, h, () -> lines);
    }

    private void setTipWidget(AbstractWidget widget, Supplier<List<Component>> supplier) {
        if (tip == null && widget != null && widget.visible && widget.isMouseOver(mouseX, mouseY)) {
            tip = supplier.get();
        }
    }

    private static List<Component> lines(Component... parts) {
        return List.of(parts);
    }

    private static Component t(String s) {
        return Component.literal(s);
    }

    /** 词条效果描述（客户端按自己的语言解析，含具体数值） */
    private String descText(AffixDto a) {
        return descForRarity(a, a == null ? null : a.descRarity());
    }

    private String parseDesc(String json) {
        if (json == null || json.isEmpty()) {
            return "";
        }
        try {
            Component comp = Component.Serializer.fromJson(json);
            return comp == null ? "" : comp.getString().trim();
        } catch (Exception ex) {
            return "";
        }
    }

    /** 指定品阶的描述；没缓存就向服务端要一次 */
    private String descForRarity(AffixDto a, String rarity) {
        if (a == null || rarity == null || rarity.isEmpty()) {
            return "";
        }
        String key = a.id() + "|" + rarity;
        String cached = descCache.get(key);
        if (cached != null) {
            return cached;
        }
        // 少数情况下快照里自带了描述（目前不带），有就先用
        if (rarity.equals(a.descRarity()) && a.descJson() != null && !a.descJson().isEmpty()) {
            String text = parseDesc(a.descJson());
            descCache.put(key, text);
            return text;
        }
        if (pendingDesc.size() < 8 && pendingDesc.add(key)) {
            JsonObject p = new JsonObject();
            p.addProperty("rarity", rarity);
            WorkbenchNetwork.sendToServer(new EditPacket(EditPacket.A_DESC, a.id(), p));
        }
        return "";
    }

    /** 服务端回的按品阶描述 */
    public void onDesc(String id, String rarity, String json) {
        pendingDesc.remove(id + "|" + rarity);
        descCache.put(id + "|" + rarity, parseDesc(json));
    }

    /** 按像素宽度折行，最多 maxLines 行 */
    private List<String> wrapText(String text, int maxWidth, int maxLines) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        for (String para : text.split("\n")) {
            String remain = para.trim();
            while (!remain.isEmpty()) {
                if (font.width(remain) <= maxWidth) {
                    out.add(remain);
                    break;
                }
                String cut = font.plainSubstrByWidth(remain, maxWidth);
                if (cut.isEmpty()) {
                    break;
                }
                out.add(cut);
                remain = remain.substring(cut.length()).trim();
                if (out.size() >= maxLines) {
                    return out;
                }
            }
            if (out.size() >= maxLines) {
                return out;
            }
        }
        return out;
    }

    /** "0.2|2|0.05" -> "0.2 +2×0.05" */
    private static String prettyValue(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "—";
        }
        int i = raw.indexOf('|');
        if (i >= 0) {
            String[] p = raw.split("\\|", -1);
            if (p.length >= 3) {
                return p[0] + " +" + p[1] + "×" + p[2];
            }
        }
        return raw;
    }

    private List<Component> rowTip(AffixDto a) {
        List<Component> out = new ArrayList<>();
        StringBuilder title = new StringBuilder("§f").append(displayName(a));
        if (a.has(AffixDto.F_CUSTOM)) {
            title.append(" §d[自定义]");
        }
        if (a.has(AffixDto.F_MODIFIED)) {
            title.append(" §6[已修改]");
        }
        out.add(t(title.toString()));
        out.add(t("§7ID §f" + a.id()));
        out.add(t("§7类型 §f" + WbNames.typeWithEn(a.type()) + "   §7状态 §f" + WB.statusName(a.status())));
        if (a.has(AffixDto.F_LOAD_FAILED)) {
            out.add(t("§c重载后没有注册成功，请查看 latest.log"));
        }
        String cats = a.cats().isEmpty() ? "（全部部位）" : WbNames.catsWithEn(a.cats());
        out.add(t("§7部位 §f" + cats));
        String desc = descText(a);
        if (!desc.isEmpty()) {
            out.add(t("§7效果" + (a.descRarity().isEmpty() ? "" : "§8（" + rName(a.descRarity()) + " 品阶）")));
            for (String line : wrapText(desc, 180, 3)) {
                out.add(t("§f" + line));
            }
        }
        if (!a.has(AffixDto.F_HAS_VALUES)) {
            out.add(t("§7品阶 §8不受品阶限制"));
        } else if (a.rarityKeys().isEmpty()) {
            out.add(t("§c品阶 §c未启用任何品阶，不会出现在战利品中"));
        } else {
            out.add(t("§7品阶（" + a.rarityKeys().size() + "）"));
            for (int i = 0; i < a.rarityKeys().size(); i++) {
                String key = a.rarityKeys().get(i);
                out.add(t(" §8· §f" + rNameWithEn(key) + " §7" + prettyValue(a.rarityTexts().get(i))));
            }
        }
        out.add(t("§8左键选中，右侧可开关品阶 / 禁用 / 删除"));
        return out;
    }

    private void collectWidgetTips() {
        setTipWidget(btnSave, () -> lines(t("§f保存并重载"), t("§7把改动写进存档数据包并重载游戏内数据"),
                t("§7客户端会同步到新的词条数据"), t("§8单人暂停时会在关闭界面后自动应用"),
                t("§8不点这个按钮，改动只在下次开服后生效")));
        setTipWidget(btnRefresh, () -> lines(t("§f刷新"), t("§7重新扫描数据包里的词条与品阶"),
                t("§8未保存的编辑会保留")));
        setTipWidget(btnExport, () -> lines(t("§f导出为 KubeJS 脚本"),
                t("§7把当前改动写成 ServerEvents.highPriorityData(...)"),
                t("§7保存到 kubejs/server_scripts/apoth_workbench_affixes.js"),
                t("§7按住 Shift 点击 = 导出全部词条（让 KubeJS 完全接管）"),
                t("§8与「保存并重载」的覆盖数据包作用相同，二选一，别同时启用")));
        setTipWidget(btnClose, () -> lines(t("§f关闭"), t("§8改动已自动存盘，但仍需「保存并重载」才在游戏内生效")));

        for (int i = 0; i < filterButtons.size(); i++) {
            final int idx = i;
            setTipWidget(filterButtons.get(i), () -> filterTip(idx));
        }

        setTipWidget(btnRarity, () -> lines(t("§f编辑品阶"), t("§7在已启用的品阶之间循环"),
                t("§7下面的三个数字只作用于当前显示的品阶"),
                editRarity == null ? t("§8当前没有已启用的品阶") : t("§8当前：" + rNameWithEn(editRarity))));
        setTipWidget(fMin, () -> lines(t("§f保底数值"), t("§7这件装备最差也有这么多"),
                t("§8最好能到多少，看另外两个数")));
        setTipWidget(fSteps, () -> lines(t("§f分成几档"), t("§7词条数值不固定，会被随机分成几档"),
                t("§7档数越多，两件装备的差距越小"),
                t("§c不能填 0，最少填 1")));
        setTipWidget(fStep, () -> lines(t("§f每档加多少"), t("§7从保底往上，每升一档加这么多"),
                t("§8最好 = 保底 + 档数 × 每档加")));
        setTipWidget(btnApply, () -> lines(t("§f保存数值"), t("§7只改上面「编辑品阶」选中的那个品阶")));
        setTipWidget(btnSync, () -> lines(t("§f同步全部"), t("§7把这三个数套到这条词条的所有品阶"),
                t("§c注意：所有品阶会变成同一个范围")));
        setTipWidget(btnStatus, () -> lines(t("§f禁用 / 启用"),
                t("§7禁用：词条不再加载、不再出现在战利品里，数据保留"),
                t("§7启用：恢复加载（保留之前的编辑）")));
        setTipWidget(btnRevert, () -> lines(t("§f恢复原样"), t("§7丢弃这条词条的全部编辑与禁用/删除状态"),
                t("§7回到它出厂时的数据")));
        setTipWidget(btnDup, () -> lines(t("§f复制"), t("§7以当前词条为模板新建一条"),
                t("§7会进新建界面，可改 ID、品阶、部位"),
                t("§8从零造词条用左边「＋ 新建词条」")));
        setTipWidget(btnRename, () -> lines(t("§f改名"), t("§7给这条词条设一个中文名"),
                t("§7物品上直接显示，立即生效"),
                t("§8留空 = 恢复原来的名字")));
        setTipWidget(btnDelete, () -> lines(t("§f删除 / 屏蔽"),
                t("§7内置词条：写入假条件让神化忽略它，可「恢复原样」找回"),
                t("§c自定义词条：直接删除数据")));
        setTipWidget(search, () -> lines(t("§f搜索"), t("§7匹配词条 ID、中文名、类型、效果、部位")));

        setTipWidget(btnCats, () -> lines(t("§f改部位"), t("§7修改这条词条能出现在哪些物品上"),
                t("§8全不选 = 所有部位都能出")));
        setTipWidget(btnCopyJson, () -> lines(t("§f复制 JSON"), t("§7把整条词条的 JSON 复制到剪贴板"),
                t("§8可以用外部编辑器改，再用「贴」贴回来")));
        setTipWidget(btnPasteJson, () -> lines(t("§f粘贴 JSON"), t("§7用剪贴板里的 JSON 整体替换这条词条"),
                t("§7会先校验：type/属性/效果/部位/品阶/数值"),
                t("§8校验不过会直接拒绝并说明原因")));

        setTipWidget(btnBatchRarity, () -> lines(t("§f批量品阶"), t("§7切换「加入品阶 / 移出品阶」要操作的品阶")));
        setTipWidget(bEnable, () -> lines(t("§f批量启用"), t("§7把选中的词条全部恢复加载")));
        setTipWidget(bDisable, () -> lines(t("§f批量禁用"), t("§7选中的词条全部停用，数据保留")));
        setTipWidget(bRarityOn, () -> lines(t("§f加入品阶"), t("§7给选中的所有词条加一个品阶"),
                t("§8没有该品阶数值的会沿用各自最高品阶的数值")));
        setTipWidget(bRarityOff, () -> lines(t("§f移出品阶"), t("§7把该品阶从选中的所有词条里去掉")));
        setTipWidget(bDelete, () -> lines(t("§f批量删除"), t("§c需要连点两次确认"),
                t("§7内置词条=屏蔽，自定义词条=彻底删除")));
        setTipWidget(bClearSel, () -> lines(t("§f清空选择"), t("§8回到单条编辑模式")));
    }

    private List<Component> filterTip(int index) {
        return switch (index) {
            case 1 -> lines(t("§f已修改"), t("§7改动过、或处于禁用/删除状态的词条"));
            case 2 -> lines(t("§f已禁用"), t("§7保留数据但当前不加载的词条"));
            case 3 -> lines(t("§f已删除"), t("§7被屏蔽/删除、当前不在游戏里的词条"));
            case 4 -> lines(t("§f自定义"), t("§7通过工作台新建的词条"));
            default -> lines(t("§f全部词条"), t("§7神化数据包里所有已注册的词条"));
        };
    }

    private void drawHeader(GuiGraphics g) {
        g.fill(hx, hy, hx + hw, hy + hh, C_PANEL);
        g.fill(hx, hy + hh - 1, hx + hw, hy + hh, C_BORDER);
        g.drawString(font, "神化工作台", hx + 6, hy + 6, C_ACCENT, false);
        if (!Arrays.equals(lastStats, stats)) {
            lastStats = stats.clone();
            headerInfo = String.format(Locale.ROOT, "共 %d 条 · 正常 %d · 已禁用 %d · 已删 %d · 自建 %d · 改过 %d",
                    stats[0], stats[1], stats[2], stats[3], stats[4], stats[5]);
        }
        g.drawString(font, headerInfo, hx + 74, hy + 7, C_MUTED, false);
        setTip(hx + 74, hy + 5, font.width(headerInfo), 11, () -> lines(
                t("§f这些数字是什么意思"),
                t("§7共 = 扫描到的词条总数"),
                t("§7正常 / 已禁用 / 已删 = 每条词条现在的状态"),
                t("§7自建 = 用工作台新建出来的词条"),
                t("§7改过 = 和原版数据不一样的数量")));
    }

    private void drawFooter(GuiGraphics g) {
        int y = height - 18;
        int x = search.getX() + search.getWidth() + 8;
        if (stats.length > 6 && stats[6] == 1) {
            g.drawString(font, "● 改动已写入文件，点「保存并重载」生效（世界暂停时会在关闭界面后应用）", x, y, C_WARN, false);
        } else {
            g.drawString(font, "滚轮翻页 · 点击品阶开关调整归属 · 选中词条可禁用/删除/复制",
                    x, y, C_MUTED, false);
        }
    }

    private void drawSidebar(GuiGraphics g) {
        g.fill(lx, ly, lx + lw, ly + lh, C_PANEL);
        g.fill(lx + lw - 1, ly, lx + lw, ly + lh, C_BORDER);

        // 只有统计变化时才重建按钮文字，避免每帧创建一堆 Component
        if (!Arrays.equals(lastBtnStats, stats)) {
            lastBtnStats = stats.clone();
            for (Button b : filterButtons) {
                b.setMessage(Component.literal(labelOf(b) + countSuffix(b)));
            }
        }

        int cy = ly + filterButtons.size() * 18 + 24;
        g.drawString(font, "品阶筛选", lx + 4, cy, C_ACCENT, false);
        filterChips.clear();
        int cx = lx + 4;
        int cyy = cy + 12;
        int maxX = lx + lw - 4;
        for (RarityInfo r : rarities) {
            String label = WbNames.rarity(r);
            int w = font.width(label) + 10;
            if (cx + w > maxX) {
                cx = lx + 4;
                cyy += 16;
            }
            boolean on = r.key().equals(rarityFilter);
            drawChip(g, cx, cyy, w, 13, label, on, r.color());
            filterChips.add(new Chip(cx, cyy, w, 13, r.key(), on));
            setTip(cx, cyy, w, 13, lines(
                    t("§f品阶 " + WbNames.rarityWithEn(r) + (on ? " §a（筛选中）" : "")),
                    t("§8序号 " + r.ordinal() + " · 权重 " + r.weight()),
                    t("§7只看属于这个品阶的词条"),
                    t("§8再点一次取消筛选")));
            cx += w + 3;
        }
    }

    private String labelOf(Button b) {
        for (int i = 0; i < filterButtons.size(); i++) {
            if (filterButtons.get(i) == b) {
                return switch (i) {
                    case 0 -> "全部词条";
                    case 1 -> "已修改";
                    case 2 -> "已禁用";
                    case 3 -> "已删除";
                    default -> "自定义";
                };
            }
        }
        return "";
    }

    private String countSuffix(Button b) {
        int i = filterButtons.indexOf(b);
        int n = switch (i) {
            case 1 -> stats[5];
            case 2 -> stats[2];
            case 3 -> stats[3];
            case 4 -> stats[4];
            default -> stats[0];
        };
        return " (" + n + ")";
    }

    private void drawList(GuiGraphics g) {
        g.fill(mx, my, mx + mw, my + mh, C_PANEL);
        g.fill(mx, my, mx + 1, my + mh, C_BORDER);

        g.enableScissor(mx, my, mx + mw, my + mh);
        int start = (int) Math.max(0, Math.floor(scroll / rowH) - 1);
        int end = Math.min(shown.size(), start + mh / rowH + 3);
        for (int i = start; i < end; i++) {
            AffixDto a = shown.get(i);
            int y = my + 2 + i * rowH - (int) scroll;
            boolean sel = a.id().equals(selectedId);
            boolean hover = inside(mouseX, mouseY, mx, my, mw, mh)
                    && mouseY >= y && mouseY < y + rowH - 2;
            int rowBg = sel ? 0xFF2B2545 : (hover ? C_PANEL2 : 0);
            if (rowBg != 0) {
                g.fill(mx + 3, y, mx + mw - 8, y + rowH - 2, rowBg);
            }

            // 多选框
            boolean isChecked = checked.contains(a.id());
            int cbx = mx + 4;
            int cby = y + 9;
            g.fill(cbx, cby, cbx + 9, cby + 9, isChecked ? C_ACCENT : C_PANEL2);
            g.fill(cbx, cby, cbx + 9, cby + 1, C_BORDER);
            g.fill(cbx, cby + 8, cbx + 9, cby + 9, C_BORDER);
            g.fill(cbx, cby, cbx + 1, cby + 9, C_BORDER);
            g.fill(cbx + 8, cby, cbx + 9, cby + 9, C_BORDER);
            if (isChecked) {
                g.fill(cbx + 2, cby + 2, cbx + 7, cby + 7, 0xFFFFFFFF);
            }

            int textX = mx + 18;
            int nameColor = a.status() == WB.ST_DELETED ? 0xFF7A7A8A
                    : (a.status() == WB.ST_DISABLED ? C_WARN : C_TEXT);
            if (a.has(AffixDto.F_LOAD_FAILED)) {
                nameColor = C_BAD;
            }
            String name = clip(displayName(a), mw - 78);
            g.drawString(font, name, textX, y + 3, nameColor, false);
            String sub = WbNames.type(a.type()) + " · " + a.summary();
            g.drawString(font, clip(sub, mw - 100), textX, y + 14, C_MUTED, false);
            if (a.has(AffixDto.F_MODIFIED)) {
                g.drawString(font, "*", textX + font.width(name) + 2, y + 3, C_ACCENT, false);
            }
            if (a.has(AffixDto.F_LOAD_FAILED)) {
                g.drawString(font, "!", mx + mw - 28, y + 12, C_BAD, false);
            }

            // 品阶色点
            int dotX = mx + mw - 12;
            for (int k = a.rarityKeys().size() - 1; k >= 0; k--) {
                int color = colorOf(a.rarityKeys().get(k));
                g.fill(dotX - 3, y + 12, dotX + 1, y + 16, color);
                dotX -= 5;
            }
            if (a.status() != WB.ST_ACTIVE) {
                String badge = a.status() == WB.ST_DELETED ? "删" : "禁";
                g.drawString(font, badge, mx + mw - 18, y + 3,
                        a.status() == WB.ST_DELETED ? C_BAD : C_WARN, false);
            }
            if (hover) {
                setTip(mx + 3, y, mw - 8, rowH - 2, () -> rowTip(a));
            }
        }
        g.disableScissor();

        // 滚动条
        int max = (int) Math.max(0, shown.size() * rowH - mh + 6);
        g.fill(mx + mw - 6, my, mx + mw - 2, my + mh, 0x40000000);
        if (max > 0) {
            int barH = Math.max(16, (int) ((double) mh * mh / (shown.size() * rowH)));
            int barY = my + (int) ((double) (mh - barH) * scroll / max);
            g.fill(mx + mw - 6, barY, mx + mw - 2, barY + barH, C_ACCENT);
        }

        if (shown.isEmpty()) {
            g.drawString(font, "没有符合条件的词条", mx + mw / 2 - font.width("没有符合条件的词条") / 2, my + mh / 2, C_MUTED, false);
        }
    }

    private void drawDetail(GuiGraphics g) {
        g.fill(rx, ry, rx + rw, ry + rh, C_PANEL);
        g.fill(rx, ry, rx + 1, ry + rh, C_BORDER);
        g.drawString(font, "词条详情", rx + 5, ry + 5, C_ACCENT, false);

        detailChips.clear();
        if (!checked.isEmpty()) {
            drawBatchPanel(g);
            return;
        }
        AffixDto a = selected();
        if (a == null) {
            g.drawString(font, "← 从中间列表里选择一条词条", rx + 6, ry + 26, C_MUTED, false);
            g.drawString(font, "点行左侧方块可多选、批量处理", rx + 6, ry + 40, C_MUTED, false);
            g.drawString(font, "想自己造一条就点左边「＋ 新建词条」", rx + 6, ry + 60, C_ACCENT, false);
            return;
        }

        g.drawString(font, clip(a.id(), rw - 58), rx + 5, ry + 18, C_TEXT, false);
        setTip(rx + 5, ry + 17, rw - 10, 10, () -> lines(
                t("§f" + a.id()),
                t("§7这是词条的数据包 ID，也是生成文件的名字"),
                a.has(AffixDto.F_CUSTOM) ? t("§d工作台新建的词条") : t("§8来自神化或其它数据包")));
        int statusColor = a.status() == WB.ST_ACTIVE ? C_GOOD : (a.status() == WB.ST_DISABLED ? C_WARN : C_BAD);
        String statusText = "状态: " + WB.statusName(a.status()) + (a.has(AffixDto.F_LOAD_FAILED) ? " · 未注册成功" : "");
        g.drawString(font, clip(statusText, rw - 10), rx + 5, ry + 30,
                a.has(AffixDto.F_LOAD_FAILED) ? C_BAD : statusColor, false);
        g.drawString(font, clip("类型: " + WbNames.typeWithEn(a.type()), rw - 10), rx + 5, ry + 42, C_MUTED, false);
        String cats = a.cats().isEmpty() ? "（全部）" : WbNames.cats(a.cats());
        g.drawString(font, clip("部位: " + cats, rw - 60), rx + 5, ry + 54, C_MUTED, false);

        // 效果描述（跟随当前编辑的品阶）
        List<String> descLines = wrapText(descForRarity(a, editRarity), rw - 12, rh >= 280 ? 2 : 1);
        for (int i = 0; i < descLines.size(); i++) {
            g.drawString(font, descLines.get(i), rx + 5, ry + 66 + i * 10, C_TEXT, false);
        }
        int labelY = ry + 66 + descLines.size() * 10 + 4;

        boolean hasValues = a.has(AffixDto.F_HAS_VALUES);
        g.drawString(font, hasValues ? "能在哪些品阶出现（点一下开关）" : "这条词条所有品阶都会出",
                rx + 5, labelY, C_ACCENT, false);

        if (hasValues) {
            int cx = rx + 5;
            int cy = labelY + 12;
            int maxX = rx + rw - 5;
            for (RarityInfo r : rarities) {
                String label = WbNames.rarity(r);
                int w = font.width(label) + 12;
                if (cx + w > maxX) {
                    cx = rx + 5;
                    cy += 17;
                }
                boolean on = a.rarityKeys().contains(r.key());
                drawChip(g, cx, cy, w, 14, label, on, r.color());
                detailChips.add(new Chip(cx, cy, w, 14, r.key(), on));
                List<Component> tipLines;
                if (on) {
                    int idx = a.rarityKeys().indexOf(r.key());
                    String value = idx >= 0 ? prettyValue(a.rarityTexts().get(idx)) : "—";
                    tipLines = lines(t("§f品阶 " + WbNames.rarityWithEn(r) + " §a（已启用）"),
                            t("§8序号 " + r.ordinal() + " · 权重 " + r.weight()),
                            t("§7数值 §f" + value),
                            t("§8点击关闭：该品阶不再产出这条词条"));
                } else {
                    tipLines = lines(t("§f品阶 " + WbNames.rarityWithEn(r) + " §8（未启用）"),
                            t("§8序号 " + r.ordinal() + " · 权重 " + r.weight()),
                            t("§7点击启用：让这条词条也能在这个品阶出现"),
                            t("§8数值会沿用该词条最高品阶的数值"));
                }
                setTip(cx, cy, w, 14, tipLines);
                cx += w + 3;
            }
        } else {
            g.drawString(font, "（这种词条没法单独设品阶，只能整体禁用）", rx + 5, labelY + 14, C_MUTED, false);
        }

        // 数值编辑区说明
        int by = ry + rh - 108;
        g.fill(rx + 5, by - 6, rx + rw - 5, by - 5, C_BORDER);
        g.drawString(font, "保底 / 档数 / 每档加", rx + 5, by + 20, C_MUTED, false);
        String preview = stepPreview();
        if (preview != null) {
            int pw = font.width(preview);
            if (rw - 10 - pw > 70) {
                g.drawString(font, preview, rx + rw - 5 - pw, by + 20, previewOk ? C_ACCENT : C_BAD, false);
            }
        }
        setTip(rx + 5, by + 18, rw - 10, 10, () -> lines(
                t("§f这条词条能刷到的范围"),
                t("§7装备生成时随机取一档，所以每件都不一样"),
                t("§8最差 = 保底，最好 = 保底 + 档数 × 每档加"),
                t("§8例：0.3 / 20 / 0.02 → 最差 0.3，最好 0.7")));
    }

    private void drawBatchPanel(GuiGraphics g) {
        g.drawString(font, "批量操作", rx + 5, ry + 18, C_ACCENT, false);
        g.drawString(font, "已选 " + checked.size() + " 条词条", rx + 5, ry + 32, C_TEXT, false);
        int disabled = 0;
        int deleted = 0;
        int custom = 0;
        for (String id : checked) {
            AffixDto d = find(id);
            if (d == null) {
                continue;
            }
            if (d.status() == WB.ST_DISABLED) {
                disabled++;
            } else if (d.status() == WB.ST_DELETED) {
                deleted++;
            }
            if (d.has(AffixDto.F_CUSTOM)) {
                custom++;
            }
        }
        g.drawString(font, "其中 禁用 " + disabled + " · 删除 " + deleted + " · 自定义 " + custom,
                rx + 5, ry + 44, C_MUTED, false);
        g.drawString(font, "勾选：点行左侧方块", rx + 5, ry + 62, C_MUTED, false);
        g.drawString(font, "连选：Shift+点行", rx + 5, ry + 74, C_MUTED, false);
        g.drawString(font, "切换：Ctrl+点行", rx + 5, ry + 86, C_MUTED, false);
        g.drawString(font, "批量只改状态/品阶，不动数值与部位", rx + 5, ry + 104, C_MUTED, false);
    }

    private int colorOf(String rarityKey) {
        for (RarityInfo r : rarities) {
            if (r.key().equals(rarityKey) || r.id().equals(rarityKey)) {
                return r.color();
            }
        }
        return C_MUTED;
    }

    private boolean previewOk;

    /** 把三个输入框算成 "能刷到 0.3 ~ 0.7"，非法返回提示；输入没变则复用上一次结果 */
    private String stepPreview() {
        String key = fMin.getValue() + "|" + fSteps.getValue() + "|" + fStep.getValue();
        if (key.equals(previewKey)) {
            return previewText;
        }
        previewKey = key;
        previewOk = false;
        try {
            double min = Double.parseDouble(fMin.getValue().trim());
            double steps = Double.parseDouble(fSteps.getValue().trim());
            double step = Double.parseDouble(fStep.getValue().trim());
            previewOk = steps >= 1 && steps == Math.rint(steps) && step >= 0
                    && Double.isFinite(min) && Double.isFinite(step);
            previewText = previewOk
                    ? "能刷到 " + WB.fmt(min) + " ~ " + WB.fmt(min + steps * step)
                    : "档数要 ≥1 的整数";
            return previewText;
        } catch (Exception ex) {
            previewText = null;
            return null;
        }
    }

    private String rName(String key) {
        for (RarityInfo r : rarities) {
            if (r.key().equals(key) || r.id().equals(key)) {
                return WbNames.rarity(r);
            }
        }
        return key;
    }

    private String rNameWithEn(String key) {
        for (RarityInfo r : rarities) {
            if (r.key().equals(key) || r.id().equals(key)) {
                return WbNames.rarityWithEn(r);
            }
        }
        return key;
    }

    private void drawChip(GuiGraphics g, int x, int y, int w, int h, String label, boolean on, int rarityColor) {
        if (on) {
            g.fill(x, y, x + w, y + h, 0xFF000000 | (rarityColor & 0xFFFFFF));
            g.drawString(font, label, x + 6, y + (h - 8) / 2, 0xFF101018, false);
        } else {
            g.fill(x, y, x + w, y + h, C_PANEL2);
            g.fill(x, y, x + w, y + 1, C_BORDER);
            g.fill(x, y + h - 1, x + w, y + h, C_BORDER);
            g.drawString(font, label, x + 6, y + (h - 8) / 2, C_MUTED, false);
        }
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
}
