package cn.blockforge.apothworkbench.client;

import cn.blockforge.apothworkbench.WB;
import cn.blockforge.apothworkbench.WbLang;
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
        super(WbLang.tr("apoth_workbench.title"));
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
        btnClose = addRenderableWidget(Button.builder(WbLang.tr("apoth_workbench.btn.close"), b -> onClose())
                .bounds(bx - 44, hy + 3, 44, 16).build());
        bx -= 48;
        btnRefresh = addRenderableWidget(Button.builder(WbLang.tr("apoth_workbench.btn.refresh"), b ->
                WorkbenchNetwork.sendToServer(new EditPacket(EditPacket.A_REFRESH, "")))
                .bounds(bx - 44, hy + 3, 44, 16).build());
        bx -= 48;
        btnSave = addRenderableWidget(Button.builder(WbLang.tr("apoth_workbench.btn.save"), b -> save())
                .bounds(bx - 90, hy + 3, 90, 16).build());
        bx -= 94;
        btnExport = addRenderableWidget(Button.builder(WbLang.tr("apoth_workbench.btn.export"), b -> export())
                .bounds(bx - 70, hy + 3, 70, 16).build());

        // 左栏筛选
        filterButtons.clear();
        String[][] filters = {{"all", "apoth_workbench.filter.all"}, {"mod", "apoth_workbench.filter.modified"},
                {"disabled", "apoth_workbench.filter.disabled"}, {"deleted", "apoth_workbench.filter.deleted"},
                {"custom", "apoth_workbench.filter.custom"}};
        for (int i = 0; i < filters.length; i++) {
            String key = filters[i][0];
            Button b = addRenderableWidget(Button.builder(WbLang.tr(filters[i][1]), btn -> {
                filter = key;
                scroll = 0;
                rebuild();
            }).bounds(lx, ly + i * 18, lw, 16).build());
            filterButtons.add(b);
        }

        // 新建入口常驻左栏，不依赖是否选中词条
        addRenderableWidget(Button.builder(WbLang.tr("apoth_workbench.btn.new"), b -> newAffix())
                .bounds(lx, ly + filters.length * 18 + 2, lw, 16).build());

        // 底部搜索框
        search = new EditBox(font, pad, height - pad - 16, Math.min(260, width / 2), 16, WbLang.tr("apoth_workbench.hint.search"));
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
        btnRarity = addRenderableWidget(Button.builder(WbLang.tr("apoth_workbench.btn.rarity.dash"), b -> {
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

        btnApply = addRenderableWidget(Button.builder(WbLang.tr("apoth_workbench.btn.apply"), b -> applyValues())
                .bounds(rx + 4, by + 50, half, 16).build());
        btnSync = addRenderableWidget(Button.builder(WbLang.tr("apoth_workbench.btn.sync"), b -> syncValues())
                .bounds(rx + 4 + half + 4, by + 50, half, 16).build());
        btnStatus = addRenderableWidget(Button.builder(WbLang.tr("apoth_workbench.btn.disable"), b -> toggleStatus())
                .bounds(rx + 4, by + 70, half, 16).build());
        btnRevert = addRenderableWidget(Button.builder(WbLang.tr("apoth_workbench.btn.revert"), b -> revert())
                .bounds(rx + 4 + half + 4, by + 70, half, 16).build());
        int third = (rw - 8 - 8) / 3;
        btnDup = addRenderableWidget(Button.builder(WbLang.tr("apoth_workbench.btn.copy"), b -> duplicate())
                .bounds(rx + 4, by + 90, third, 16).build());
        btnRename = addRenderableWidget(Button.builder(WbLang.tr("apoth_workbench.btn.rename"), b -> rename())
                .bounds(rx + 4 + third + 4, by + 90, third, 16).build());
        btnDelete = addRenderableWidget(Button.builder(WbLang.tr("apoth_workbench.btn.delete"), b -> delete())
                .bounds(rx + 4 + (third + 4) * 2, by + 90, third, 16).build());

        // 单条模式：小按钮（改部位 / 复制 JSON / 粘贴 JSON）
        btnCats = addRenderableWidget(Button.builder(WbLang.tr("apoth_workbench.btn.cats"), b -> editCats())
                .bounds(rx + rw - 50, ry + 49, 46, 13).build());
        btnCopyJson = addRenderableWidget(Button.builder(WbLang.tr("apoth_workbench.btn.copy.json"), b -> copyJson())
                .bounds(rx + rw - 50, ry + 13, 22, 13).build());
        btnPasteJson = addRenderableWidget(Button.builder(WbLang.tr("apoth_workbench.btn.paste.json"), b -> pasteJson())
                .bounds(rx + rw - 26, ry + 13, 22, 13).build());

        // 批量模式
        btnBatchRarity = addRenderableWidget(Button.builder(WbLang.tr("apoth_workbench.btn.rarity.dash"), b -> cycleBatchRarity())
                .bounds(rx + 4, by, rw - 8, 16).build());
        bEnable = addRenderableWidget(Button.builder(WbLang.tr("apoth_workbench.btn.batch.enable"), b -> batch("enable"))
                .bounds(rx + 4, by + 20, half, 16).build());
        bDisable = addRenderableWidget(Button.builder(WbLang.tr("apoth_workbench.btn.batch.disable"), b -> batch("disable"))
                .bounds(rx + 4 + half + 4, by + 20, half, 16).build());
        bRarityOn = addRenderableWidget(Button.builder(WbLang.tr("apoth_workbench.btn.batch.rarity.on"), b -> batch("rarity_on"))
                .bounds(rx + 4, by + 40, half, 16).build());
        bRarityOff = addRenderableWidget(Button.builder(WbLang.tr("apoth_workbench.btn.batch.rarity.off"), b -> batch("rarity_off"))
                .bounds(rx + 4 + half + 4, by + 40, half, 16).build());
        bDelete = addRenderableWidget(Button.builder(WbLang.tr("apoth_workbench.btn.batch.delete"), b -> batch("delete"))
                .bounds(rx + 4, by + 60, half, 16).build());
        bClearSel = addRenderableWidget(Button.builder(WbLang.tr("apoth_workbench.btn.batch.clear"), b -> {
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
            btnBatchRarity.setMessage(WbLang.tr("apoth_workbench.btn.batch.rarity",
                    batchRarity == null ? WbLang.tr("apoth_workbench.text.unselected") : Component.literal(rName(batchRarity))));
            boolean delConfirm = "batch-delete".equals(confirmKey) && System.currentTimeMillis() < confirmUntil;
            bDelete.setMessage(WbLang.tr(delConfirm ? "apoth_workbench.btn.confirm.delete" : "apoth_workbench.btn.batch.delete"));
            bRarityOn.active = batchRarity != null;
            bRarityOff.active = batchRarity != null;
            return;
        }
        if (!has) {
            return;
        }
        boolean disabled = a.status() != WB.ST_ACTIVE;
        btnStatus.setMessage(WbLang.tr(disabled ? "apoth_workbench.btn.enable" : "apoth_workbench.btn.disable"));
        boolean delConfirm = ("del:" + a.id()).equals(confirmKey) && System.currentTimeMillis() < confirmUntil;
        btnDelete.setMessage(WbLang.tr(a.has(AffixDto.F_CUSTOM)
                ? (delConfirm ? "apoth_workbench.btn.confirm.short" : "apoth_workbench.btn.delete")
                : (delConfirm ? "apoth_workbench.btn.confirm.short" : "apoth_workbench.btn.block")));
        boolean revConfirm = ("rev:" + a.id()).equals(confirmKey) && System.currentTimeMillis() < confirmUntil;
        btnRevert.setMessage(WbLang.tr(revConfirm ? "apoth_workbench.btn.confirm.revert" : "apoth_workbench.btn.revert"));
        btnRevert.active = a.has(AffixDto.F_HAS_ORIGINAL);

        List<String> keys = a.rarityKeys();
        if (editRarity == null || !keys.contains(editRarity)) {
            editRarity = keys.isEmpty() ? null : keys.get(keys.size() - 1);
        }
        btnRarity.setMessage(editRarity == null
                ? WbLang.tr("apoth_workbench.btn.rarity.none")
                : WbLang.tr("apoth_workbench.btn.rarity.edit", Component.literal(rName(editRarity))));
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

    private void chat(Component msg) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.displayClientMessage(WbLang.tr("apoth_workbench.chat.error", msg), false);
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
            chat(WbLang.tr("apoth_workbench.msg.no_type_field"));
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
            chat(WbLang.tr("apoth_workbench.msg.clipboard_empty"));
            return;
        }
        com.google.gson.JsonElement el;
        try {
            el = com.google.gson.JsonParser.parseString(clip);
        } catch (Exception ex) {
            chat(WbLang.tr("apoth_workbench.msg.clipboard_bad_json"));
            return;
        }
        if (!el.isJsonObject()) {
            chat(WbLang.tr("apoth_workbench.msg.clipboard_not_object"));
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
        StringBuilder title = new StringBuilder(WbLang.s("apoth_workbench.tip.title_prefix")).append(displayName(a));
        if (a.has(AffixDto.F_CUSTOM)) {
            title.append(WbLang.s("apoth_workbench.tip.tag_custom"));
        }
        if (a.has(AffixDto.F_MODIFIED)) {
            title.append(WbLang.s("apoth_workbench.tip.tag_modified"));
        }
        out.add(t(title.toString()));
        out.add(t(WbLang.s("apoth_workbench.tip.id_line", a.id())));
        out.add(t(WbLang.s("apoth_workbench.tip.type_status", WbNames.typeWithEn(a.type()), WB.statusName(a.status()))));
        if (a.has(AffixDto.F_LOAD_FAILED)) {
            out.add(t(WbLang.s("apoth_workbench.tip.load_failed")));
        }
        String cats = a.cats().isEmpty() ? WbLang.s("apoth_workbench.text.all_cats") : WbNames.catsWithEn(a.cats());
        out.add(t(WbLang.s("apoth_workbench.tip.cats", cats)));
        String desc = descText(a);
        if (!desc.isEmpty()) {
            out.add(t(a.descRarity().isEmpty()
                    ? WbLang.s("apoth_workbench.tip.effect")
                    : WbLang.s("apoth_workbench.tip.effect_rarity", rName(a.descRarity()))));
            for (String line : wrapText(desc, 180, 3)) {
                out.add(t(WbLang.s("apoth_workbench.tip.desc_line", line)));
            }
        }
        if (!a.has(AffixDto.F_HAS_VALUES)) {
            out.add(t(WbLang.s("apoth_workbench.tip.rarity_unbounded")));
        } else if (a.rarityKeys().isEmpty()) {
            out.add(t(WbLang.s("apoth_workbench.tip.rarity_none")));
        } else {
            out.add(t(WbLang.s("apoth_workbench.tip.rarity_header", a.rarityKeys().size())));
            for (int i = 0; i < a.rarityKeys().size(); i++) {
                String key = a.rarityKeys().get(i);
                out.add(t(WbLang.s("apoth_workbench.tip.rarity_line", rNameWithEn(key),
                        prettyValue(a.rarityTexts().get(i)))));
            }
        }
        out.add(t(WbLang.s("apoth_workbench.tip.row_hint")));
        return out;
    }

    private void collectWidgetTips() {
        setTipWidget(btnSave, () -> lines(k("tip.save.1"), k("tip.save.2"), k("tip.save.3"),
                k("tip.save.4"), k("tip.save.5")));
        setTipWidget(btnRefresh, () -> lines(k("tip.refresh.1"), k("tip.refresh.2"), k("tip.refresh.3")));
        setTipWidget(btnExport, () -> lines(k("tip.export.1"), k("tip.export.2"), k("tip.export.3"),
                k("tip.export.4"), k("tip.export.5")));
        setTipWidget(btnClose, () -> lines(k("tip.close.1"), k("tip.close.2")));

        for (int i = 0; i < filterButtons.size(); i++) {
            final int idx = i;
            setTipWidget(filterButtons.get(i), () -> filterTip(idx));
        }

        setTipWidget(btnRarity, () -> lines(k("tip.rarity.1"), k("tip.rarity.2"), k("tip.rarity.3"),
                editRarity == null ? k("tip.rarity.none") : t(WbLang.s("apoth_workbench.tip.rarity.current", rNameWithEn(editRarity)))));
        setTipWidget(fMin, () -> lines(k("tip.min.1"), k("tip.min.2"), k("tip.min.3")));
        setTipWidget(fSteps, () -> lines(k("tip.steps.1"), k("tip.steps.2"), k("tip.steps.3"), k("tip.steps.4")));
        setTipWidget(fStep, () -> lines(k("tip.step.1"), k("tip.step.2"), k("tip.step.3")));
        setTipWidget(btnApply, () -> lines(k("tip.apply.1"), k("tip.apply.2")));
        setTipWidget(btnSync, () -> lines(k("tip.sync.1"), k("tip.sync.2"), k("tip.sync.3")));
        setTipWidget(btnStatus, () -> lines(k("tip.status.1"), k("tip.status.2"), k("tip.status.3")));
        setTipWidget(btnRevert, () -> lines(k("tip.revert.1"), k("tip.revert.2"), k("tip.revert.3")));
        setTipWidget(btnDup, () -> lines(k("tip.dup.1"), k("tip.dup.2"), k("tip.dup.3"), k("tip.dup.4")));
        setTipWidget(btnRename, () -> lines(k("tip.rename.1"), k("tip.rename.2"), k("tip.rename.3"), k("tip.rename.4")));
        setTipWidget(btnDelete, () -> lines(k("tip.delete.1"), k("tip.delete.2"), k("tip.delete.3")));
        setTipWidget(search, () -> lines(k("tip.search.1"), k("tip.search.2")));

        setTipWidget(btnCats, () -> lines(k("tip.cats.1"), k("tip.cats.2"), k("tip.cats.3")));
        setTipWidget(btnCopyJson, () -> lines(k("tip.copyjson.1"), k("tip.copyjson.2"), k("tip.copyjson.3")));
        setTipWidget(btnPasteJson, () -> lines(k("tip.pastejson.1"), k("tip.pastejson.2"),
                k("tip.pastejson.3"), k("tip.pastejson.4")));

        setTipWidget(btnBatchRarity, () -> lines(k("tip.batchrarity.1"), k("tip.batchrarity.2")));
        setTipWidget(bEnable, () -> lines(k("tip.batchenable.1"), k("tip.batchenable.2")));
        setTipWidget(bDisable, () -> lines(k("tip.batchdisable.1"), k("tip.batchdisable.2")));
        setTipWidget(bRarityOn, () -> lines(k("tip.batchon.1"), k("tip.batchon.2"), k("tip.batchon.3")));
        setTipWidget(bRarityOff, () -> lines(k("tip.batchoff.1"), k("tip.batchoff.2")));
        setTipWidget(bDelete, () -> lines(k("tip.batchdelete.1"), k("tip.batchdelete.2"), k("tip.batchdelete.3")));
        setTipWidget(bClearSel, () -> lines(k("tip.batchclear.1"), k("tip.batchclear.2")));
    }

    /** tooltip 文案：查表得到当前语言的纯文本 */
    private static Component k(String shortKey, Object... args) {
        return Component.literal(WbLang.s("apoth_workbench." + shortKey, args));
    }

    private List<Component> filterTip(int index) {
        return switch (index) {
            case 1 -> lines(k("tip.filter.modified.1"), k("tip.filter.modified.2"));
            case 2 -> lines(k("tip.filter.disabled.1"), k("tip.filter.disabled.2"));
            case 3 -> lines(k("tip.filter.deleted.1"), k("tip.filter.deleted.2"));
            case 4 -> lines(k("tip.filter.custom.1"), k("tip.filter.custom.2"));
            default -> lines(k("tip.filter.all.1"), k("tip.filter.all.2"));
        };
    }

    private void drawHeader(GuiGraphics g) {
        g.fill(hx, hy, hx + hw, hy + hh, C_PANEL);
        g.fill(hx, hy + hh - 1, hx + hw, hy + hh, C_BORDER);
        g.drawString(font, WbLang.s("apoth_workbench.title"), hx + 6, hy + 6, C_ACCENT, false);
        if (!Arrays.equals(lastStats, stats)) {
            lastStats = stats.clone();
            headerInfo = WbLang.s("apoth_workbench.header.stats",
                    stats[0], stats[1], stats[2], stats[3], stats[4], stats[5]);
        }
        g.drawString(font, headerInfo, hx + 74, hy + 7, C_MUTED, false);
        setTip(hx + 74, hy + 5, font.width(headerInfo), 11, () -> lines(
                k("tip.stats.1"), k("tip.stats.2"), k("tip.stats.3"), k("tip.stats.4"), k("tip.stats.5")));
    }

    private void drawFooter(GuiGraphics g) {
        int y = height - 18;
        int x = search.getX() + search.getWidth() + 8;
        if (stats.length > 6 && stats[6] == 1) {
            g.drawString(font, WbLang.s("apoth_workbench.footer.pending"), x, y, C_WARN, false);
        } else {
            g.drawString(font, WbLang.s("apoth_workbench.footer.hint"), x, y, C_MUTED, false);
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
        g.drawString(font, WbLang.s("apoth_workbench.sidebar.rarity_filter"), lx + 4, cy, C_ACCENT, false);
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
                    k(on ? "tip.rarity.filter_on" : "tip.rarity.filter_off", WbNames.rarityWithEn(r)),
                    k("tip.rarity.meta", r.ordinal(), r.weight()),
                    k("tip.rarity.filter.1"),
                    k("tip.rarity.filter.2")));
            cx += w + 3;
        }
    }

    private static final String[] FILTER_KEYS = {"filter.all", "filter.modified", "filter.disabled",
            "filter.deleted", "filter.custom"};

    private String labelOf(Button b) {
        int i = filterButtons.indexOf(b);
        return i >= 0 && i < FILTER_KEYS.length ? WbLang.s("apoth_workbench." + FILTER_KEYS[i]) : "";
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
                String badge = WbLang.s(a.status() == WB.ST_DELETED
                        ? "apoth_workbench.badge.deleted" : "apoth_workbench.badge.disabled");
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
            String empty = WbLang.s("apoth_workbench.list.empty");
            g.drawString(font, empty, mx + mw / 2 - font.width(empty) / 2, my + mh / 2, C_MUTED, false);
        }
    }

    private void drawDetail(GuiGraphics g) {
        g.fill(rx, ry, rx + rw, ry + rh, C_PANEL);
        g.fill(rx, ry, rx + 1, ry + rh, C_BORDER);
        g.drawString(font, WbLang.s("apoth_workbench.detail.title"), rx + 5, ry + 5, C_ACCENT, false);

        detailChips.clear();
        if (!checked.isEmpty()) {
            drawBatchPanel(g);
            return;
        }
        AffixDto a = selected();
        if (a == null) {
            g.drawString(font, WbLang.s("apoth_workbench.detail.empty.1"), rx + 6, ry + 26, C_MUTED, false);
            g.drawString(font, WbLang.s("apoth_workbench.detail.empty.2"), rx + 6, ry + 40, C_MUTED, false);
            g.drawString(font, WbLang.s("apoth_workbench.detail.empty.3"), rx + 6, ry + 60, C_ACCENT, false);
            return;
        }

        g.drawString(font, clip(a.id(), rw - 58), rx + 5, ry + 18, C_TEXT, false);
        setTip(rx + 5, ry + 17, rw - 10, 10, () -> lines(
                k("tip.id.1", a.id()),
                k("tip.id.2"),
                a.has(AffixDto.F_CUSTOM) ? k("tip.id.custom") : k("tip.id.external")));
        int statusColor = a.status() == WB.ST_ACTIVE ? C_GOOD : (a.status() == WB.ST_DISABLED ? C_WARN : C_BAD);
        String statusText = WbLang.s(a.has(AffixDto.F_LOAD_FAILED)
                ? "apoth_workbench.detail.status_failed" : "apoth_workbench.detail.status", WB.statusName(a.status()));
        g.drawString(font, clip(statusText, rw - 10), rx + 5, ry + 30,
                a.has(AffixDto.F_LOAD_FAILED) ? C_BAD : statusColor, false);
        g.drawString(font, clip(WbLang.s("apoth_workbench.detail.type", WbNames.typeWithEn(a.type())), rw - 10),
                rx + 5, ry + 42, C_MUTED, false);
        String cats = a.cats().isEmpty() ? WbLang.s("apoth_workbench.text.all_cats_short") : WbNames.cats(a.cats());
        g.drawString(font, clip(WbLang.s("apoth_workbench.detail.cats", cats), rw - 60), rx + 5, ry + 54, C_MUTED, false);

        // 效果描述（跟随当前编辑的品阶）
        List<String> descLines = wrapText(descForRarity(a, editRarity), rw - 12, rh >= 280 ? 2 : 1);
        for (int i = 0; i < descLines.size(); i++) {
            g.drawString(font, descLines.get(i), rx + 5, ry + 66 + i * 10, C_TEXT, false);
        }
        int labelY = ry + 66 + descLines.size() * 10 + 4;

        boolean hasValues = a.has(AffixDto.F_HAS_VALUES);
        g.drawString(font, WbLang.s(hasValues
                        ? "apoth_workbench.detail.rarity_editable" : "apoth_workbench.detail.rarity_fixed"),
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
                    tipLines = lines(k("tip.chip.on.1", WbNames.rarityWithEn(r)),
                            k("tip.rarity.meta", r.ordinal(), r.weight()),
                            k("tip.chip.on.2", value),
                            k("tip.chip.on.3"));
                } else {
                    tipLines = lines(k("tip.chip.off.1", WbNames.rarityWithEn(r)),
                            k("tip.rarity.meta", r.ordinal(), r.weight()),
                            k("tip.chip.off.2"),
                            k("tip.chip.off.3"));
                }
                setTip(cx, cy, w, 14, tipLines);
                cx += w + 3;
            }
        } else {
            g.drawString(font, WbLang.s("apoth_workbench.detail.rarity_disabled_hint"), rx + 5, labelY + 14, C_MUTED, false);
        }

        // 数值编辑区说明
        int by = ry + rh - 108;
        g.fill(rx + 5, by - 6, rx + rw - 5, by - 5, C_BORDER);
        g.drawString(font, WbLang.s("apoth_workbench.detail.values_label"), rx + 5, by + 20, C_MUTED, false);
        String preview = stepPreview();
        if (preview != null) {
            int pw = font.width(preview);
            if (rw - 10 - pw > 70) {
                g.drawString(font, preview, rx + rw - 5 - pw, by + 20, previewOk ? C_ACCENT : C_BAD, false);
            }
        }
        setTip(rx + 5, by + 18, rw - 10, 10, () -> lines(
                k("tip.range.1"), k("tip.range.2"), k("tip.range.3"), k("tip.range.4")));
    }

    private void drawBatchPanel(GuiGraphics g) {
        g.drawString(font, WbLang.s("apoth_workbench.batch.title"), rx + 5, ry + 18, C_ACCENT, false);
        g.drawString(font, WbLang.s("apoth_workbench.batch.selected", checked.size()), rx + 5, ry + 32, C_TEXT, false);
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
        g.drawString(font, WbLang.s("apoth_workbench.batch.breakdown", disabled, deleted, custom),
                rx + 5, ry + 44, C_MUTED, false);
        g.drawString(font, WbLang.s("apoth_workbench.batch.hint.1"), rx + 5, ry + 62, C_MUTED, false);
        g.drawString(font, WbLang.s("apoth_workbench.batch.hint.2"), rx + 5, ry + 74, C_MUTED, false);
        g.drawString(font, WbLang.s("apoth_workbench.batch.hint.3"), rx + 5, ry + 86, C_MUTED, false);
        g.drawString(font, WbLang.s("apoth_workbench.batch.hint.4"), rx + 5, ry + 104, C_MUTED, false);
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
                    ? WbLang.s("apoth_workbench.detail.preview", WB.fmt(min), WB.fmt(min + steps * step))
                    : WbLang.s("apoth_workbench.detail.preview_bad");
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
