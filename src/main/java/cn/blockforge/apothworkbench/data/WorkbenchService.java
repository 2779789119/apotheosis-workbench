package cn.blockforge.apothworkbench.data;

import cn.blockforge.apothworkbench.WB;
import cn.blockforge.apothworkbench.WbLang;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * 工作台的服务端核心：扫描词条、维护编辑状态、生成覆盖数据包。
 */
public final class WorkbenchService {

    private static WorkbenchService INSTANCE;

    /** 神化 7.x 的部位表（读不到注册表时的兜底，不含 none） */
    private static final List<String> FALLBACK_CATEGORIES = List.of(
            "bow", "boots", "chestplate", "crossbow", "heavy_weapon", "helmet",
            "leggings", "pickaxe", "shield", "shovel", "sword", "trident");

    private final MinecraftServer server;
    private final Path stateFile;
    private final Path packDir;
    private final Map<String, AffixEntry> entries = new TreeMap<>();
    private final Map<String, String> descCache = new LinkedHashMap<>();
    private final Set<String> loadFailed = new LinkedHashSet<>();
    private List<RarityInfo> rarities = List.of();
    private boolean needsReload;
    private volatile boolean pendingReload;

    private WorkbenchService(MinecraftServer server) {
        this.server = server;
        Path root = server.getWorldPath(LevelResource.ROOT);
        this.stateFile = root.resolve(WB.PACK_NAME).resolve("state.json");
        this.packDir = root.resolve("datapacks").resolve(WB.PACK_NAME);
        refresh();
    }

    public static WorkbenchService get(MinecraftServer server) {
        if (INSTANCE == null || INSTANCE.server != server) {
            INSTANCE = new WorkbenchService(server);
        }
        return INSTANCE;
    }

    public static void clear() {
        INSTANCE = null;
    }

    /** 不触发创建，用于 tick 里检查是否有挂起操作 */
    public static WorkbenchService peek() {
        return INSTANCE;
    }

    // ---------------------------------------------------------------- 数据装载

    public synchronized void refresh() {
        Map<ResourceLocation, JsonObject> base = DataScanner.scanAffixes(server);
        rarities = DataScanner.scanRarities(server);

        entries.clear();
        base.forEach((id, json) -> entries.put(id.toString(), new AffixEntry(id, json)));

        for (AffixEntry st : loadState()) {
            AffixEntry e = entries.get(st.id.toString());
            if (e == null) {
                if (st.custom && st.current == null) {
                    continue; // 已被删除的自定义词条
                }
                e = new AffixEntry(st.id);
                entries.put(st.id.toString(), e);
            }
            e.status = st.status;
            e.custom = st.custom;
            e.stash = st.stash;
            e.touched = true;
            if (st.name != null && !st.name.isBlank()) {
                e.name = st.name;
            }
            if (st.custom) {
                e.original = null;
                e.current = st.current;
            } else {
                if (st.original != null) {
                    e.original = st.original;
                }
                e.current = st.current != null ? st.current : WB.copy(e.original);
            }
        }
        WB.LOGGER.info("工作台已装载 {} 条词条 / {} 个品阶", entries.size(), rarities.size());
    }

    public List<AffixEntry> list() {
        return new ArrayList<>(entries.values());
    }

    public List<RarityInfo> rarities() {
        return rarities;
    }

    /** 神化支持的部位（直接读神化注册表，避免硬编码写错） */
    public List<String> categories() {
        if (apotheosisLoaded()) {
            try {
                List<String> c = AffixDescriber.categoryNames();
                if (c != null && !c.isEmpty()) {
                    return c;
                }
            } catch (Throwable ignored) {
                // 退回内置表
            }
        }
        return FALLBACK_CATEGORIES;
    }

    public AffixEntry entry(String id) {
        return entries.get(id);
    }

    public RarityInfo rarityByKey(String key) {
        for (RarityInfo r : rarities) {
            if (r.key().equals(key) || r.id().equals(key) || r.shortId().equals(key)) {
                return r;
            }
        }
        return null;
    }

    private List<AffixEntry> loadState() {
        List<AffixEntry> out = new ArrayList<>();
        if (!Files.isRegularFile(stateFile)) {
            return out;
        }
        try {
            JsonObject root = WB.parseObject(Files.readString(stateFile, StandardCharsets.UTF_8));
            if (!root.has("entries") || !root.get("entries").isJsonArray()) {
                return out;
            }
            for (JsonElement el : root.getAsJsonArray("entries")) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject o = el.getAsJsonObject();
                if (!o.has("id")) {
                    continue;
                }
                ResourceLocation id = ResourceLocation.tryParse(o.get("id").getAsString());
                if (id == null) {
                    continue;
                }
                AffixEntry e = new AffixEntry(id);
                e.status = (byte) (o.has("status") ? o.get("status").getAsInt() : 0);
                e.custom = o.has("custom") && o.get("custom").getAsBoolean();
                if (o.has("original") && o.get("original").isJsonObject()) {
                    e.original = o.getAsJsonObject("original");
                }
                if (o.has("current") && o.get("current").isJsonObject()) {
                    e.current = o.getAsJsonObject("current");
                }
                if (o.has("stash") && o.get("stash").isJsonObject()) {
                    for (Map.Entry<String, JsonElement> s : o.getAsJsonObject("stash").entrySet()) {
                        e.stash.put(s.getKey(), s.getValue());
                    }
                }
                if (o.has("name") && o.get("name").isJsonPrimitive()) {
                    String n = o.get("name").getAsString();
                    e.name = n.isBlank() ? null : n;
                }
                e.touched = true;
                out.add(e);
            }
        } catch (Exception ex) {
            WB.LOGGER.error("读取工作台状态失败: {}", stateFile, ex);
        }
        return out;
    }

    private void saveState() throws IOException {
        // 覆盖前留一份上一版，误操作能手动捞回来
        if (Files.isRegularFile(stateFile)) {
            try {
                Files.copy(stateFile, stateFile.resolveSibling("state.json.bak"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // 备份失败不影响主流程
            }
        }
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonArray arr = new JsonArray();
        for (AffixEntry e : entries.values()) {
            if (!e.touched) {
                continue;
            }
            JsonObject o = new JsonObject();
            o.addProperty("id", e.id.toString());
            o.addProperty("status", e.status);
            o.addProperty("custom", e.custom);
            if (e.name != null && !e.name.isBlank()) {
                o.addProperty("name", e.name);
            }
            if (e.original != null) {
                o.add("original", e.original);
            }
            if (e.current != null) {
                o.add("current", e.current);
            }
            if (!e.stash.isEmpty()) {
                JsonObject s = new JsonObject();
                e.stash.forEach(s::add);
                o.add("stash", s);
            }
            arr.add(o);
        }
        root.add("entries", arr);
        Files.createDirectories(stateFile.getParent());
        Files.writeString(stateFile, WB.GSON.toJson(root), StandardCharsets.UTF_8);
    }

    // ---------------------------------------------------------------- 编辑操作

    /** 保证 current 是可安全修改的副本（删除状态下 current 为 null，不能直接改 original） */
    private void ensureEditable(AffixEntry e) {
        if (e.current == null && e.original != null) {
            e.current = WB.copy(e.original);
        }
    }

    private void touch(AffixEntry e) {
        if (!e.custom && e.original == null) {
            e.original = WB.copy(e.current);
        }
        e.touched = true;
    }

    /** 恢复成“与原版完全一致”时就不再写进状态文件 */
    private void normalize(AffixEntry e) {
        if (e.custom) {
            return;
        }
        if (e.status == WB.ST_ACTIVE && e.current != null && e.original != null
                && e.current.equals(e.original) && e.stash.isEmpty()
                && (e.name == null || e.name.isBlank())) {
            e.touched = false;
            e.current = e.original;
        }
    }

    public Component setStatus(AffixEntry e, byte status) {
        if (e == null) {
            return WbLang.err("msg.affix_not_found");
        }
        if (e.custom && status == WB.ST_DELETED) {
            entries.remove(e.id.toString());
            return null;
        }
        e.status = status;
        if (status == WB.ST_ACTIVE) {
            e.current = e.current != null ? e.current : WB.copy(e.original);
        } else if (status == WB.ST_DELETED) {
            // 删除（屏蔽）会丢弃此前的编辑，恢复时回到词条出厂数据
            e.current = null;
            e.stash.clear();
        }
        touch(e);
        normalize(e);
        return null;
    }

    public Component toggleRarity(AffixEntry e, String rarityKey, boolean on) {
        if (e == null) {
            return WbLang.err("msg.affix_not_found");
        }
        ensureEditable(e);
        JsonObject values = e.values();
        if (values == null) {
            return WbLang.err("msg.no_values_field");
        }
        if (on) {
            if (values.has(rarityKey)) {
                return null;
            }
            touch(e);
            JsonElement block = e.stash.remove(rarityKey);
            if (block == null) {
                block = defaultBlock(values);
            }
            values.add(rarityKey, block);
            normalize(e);
            return null;
        }
        if (!values.has(rarityKey)) {
            return null;
        }
        touch(e);
        JsonElement removed = values.remove(rarityKey);
        if (removed != null) {
            e.stash.put(rarityKey, removed);
        }
        if (values.size() == 0) {
            normalize(e);
            return WbLang.warn("msg.no_rarity_left");
        }
        normalize(e);
        return null;
    }

    private JsonElement defaultBlock(JsonObject values) {
        String best = null;
        int bestOrd = Integer.MIN_VALUE;
        for (String k : values.keySet()) {
            RarityInfo r = rarityByKey(k);
            int ord = r == null ? -1 : r.ordinal();
            if (ord > bestOrd) {
                bestOrd = ord;
                best = k;
            }
        }
        if (best != null) {
            return values.get(best).deepCopy();
        }
        return flatBlock(0.1, 1, 0.1);
    }

    private static JsonObject flatBlock(double min, double steps, double step) {
        JsonObject o = new JsonObject();
        o.addProperty("min", min);
        o.addProperty("steps", steps);
        o.addProperty("step", step);
        return o;
    }

    public Component setValues(AffixEntry e, String rarityKey, double min, double steps, double step) {
        if (e == null) {
            return WbLang.err("msg.affix_not_found");
        }
        Component bad = checkStepInput(min, steps, step);
        if (bad != null) {
            return bad;
        }
        ensureEditable(e);
        JsonObject values = e.values();
        if (values == null || !values.has(rarityKey)) {
            return WbLang.err("msg.rarity_not_enabled");
        }
        JsonElement old = values.get(rarityKey);
        if (old.isJsonObject()) {
            JsonObject o = old.getAsJsonObject();
            if (!(o.has("min") && o.has("steps") && o.has("step"))) {
                return WbLang.err("msg.complex_block");
            }
        }
        touch(e);
        values.add(rarityKey, flatBlock(min, steps, step));
        normalize(e);
        return null;
    }

    public Component syncValues(AffixEntry e, double min, double steps, double step) {
        if (e == null) {
            return WbLang.err("msg.affix_not_found");
        }
        Component bad = checkStepInput(min, steps, step);
        if (bad != null) {
            return bad;
        }
        ensureEditable(e);
        JsonObject values = e.values();
        if (values == null || values.size() == 0) {
            return WbLang.err("msg.no_usable_rarity");
        }
        List<String> keys = new ArrayList<>(values.keySet());
        int applied = 0;
        touch(e);
        for (String k : keys) {
            JsonElement el = values.get(k);
            if (el.isJsonObject()) {
                JsonObject o = el.getAsJsonObject();
                if (!(o.has("min") && o.has("steps") && o.has("step"))) {
                    continue;
                }
            }
            values.add(k, flatBlock(min, steps, step));
            applied++;
        }
        normalize(e);
        return applied == 0 ? WbLang.err("msg.nothing_to_sync") : null;
    }

    public Component revert(AffixEntry e) {
        if (e == null) {
            return WbLang.err("msg.affix_not_found");
        }
        if (e.custom) {
            entries.remove(e.id.toString());
            return WbLang.ok("msg.custom_deleted");
        }
        e.current = WB.copy(e.original);
        e.status = WB.ST_ACTIVE;
        e.stash.clear();
        e.touched = false;
        return null;
    }

    /** 当前词条的 JSON 文本（用于复制到剪贴板） */
    public synchronized String jsonOf(AffixEntry e) {
        if (e == null || e.payload() == null) {
            return "";
        }
        return WB.GSON.toJson(e.payload());
    }

    /** 用粘贴进来的 JSON 覆盖整条词条（先校验副本，不合格就不动原数据） */
    public Component setJson(AffixEntry e, JsonObject json) {
        if (e == null) {
            return WbLang.err("msg.affix_not_found");
        }
        if (json == null || !json.has("type")) {
            return WbLang.err("msg.json_missing_type");
        }
        AffixEntry probe = new AffixEntry(e.id);
        probe.custom = true; // 让 validate 走 payload()=current
        probe.current = json.deepCopy();
        List<Component> errs = validate(probe);
        if (!errs.isEmpty()) {
            return WbLang.err("msg.validate_failed", join(errs));
        }
        ensureEditable(e);
        e.current = json.deepCopy();
        touch(e);
        normalize(e);
        return null;
    }

    /** 设置自定义显示名（空 = 清除，回到语言文件里的原名字） */
    public Component setName(AffixEntry e, String name) {
        if (e == null) {
            return WbLang.err("msg.affix_not_found");
        }
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.length() > 40) {
            return WbLang.err("msg.name_too_long");
        }
        e.name = trimmed.isEmpty() ? null : trimmed;
        e.touched = true;
        descCache.clear();
        if (e.name == null) {
            // 名字清空且没有其它改动时，不必在 state.json 里留一条空记录
            normalize(e);
        }
        return null;
    }

    /** 修改适用部位（types）；词条类型本身没有 types 字段时不给改 */
    public Component setCategories(AffixEntry e, List<String> cats) {
        if (e == null) {
            return WbLang.err("msg.affix_not_found");
        }
        ensureEditable(e);
        JsonObject p = e.payload();
        if (p == null) {
            return WbLang.err("msg.no_data");
        }
        if (!p.has("types")) {
            return WbLang.err("msg.no_type_field");
        }
        JsonArray arr = new JsonArray();
        for (String c : cats) {
            if (c != null && !c.isBlank()) {
                arr.add(c.trim());
            }
        }
        // 先在副本上试一遍，避免把非法部位写进去
        JsonObject probeJson = p.deepCopy();
        probeJson.add("types", arr.deepCopy());
        AffixEntry probe = new AffixEntry(e.id);
        probe.custom = true;
        probe.current = probeJson;
        List<Component> errs = validate(probe);
        if (!errs.isEmpty()) {
            return WbLang.err("msg.not_modified", join(errs));
        }
        touch(e);
        p.add("types", arr);
        normalize(e);
        return null;
    }

    /** 批量操作：enable / disable / delete / rarity_on / rarity_off */
    public Component batch(List<String> ids, String op, String rarity) {
        if (ids == null || ids.isEmpty()) {
            return WbLang.err("msg.nothing_selected");
        }
        if (("rarity_on".equals(op) || "rarity_off".equals(op)) && (rarity == null || rarity.isBlank())) {
            return WbLang.err("msg.pick_rarity_first");
        }
        int ok = 0;
        int skip = 0;
        for (String idStr : ids) {
            AffixEntry e = entries.get(idStr);
            if (e == null) {
                skip++;
                continue;
            }
            switch (op) {
                case "enable" -> setStatus(e, WB.ST_ACTIVE);
                case "disable" -> setStatus(e, WB.ST_DISABLED);
                case "delete" -> setStatus(e, WB.ST_DELETED);
                case "rarity_on" -> {
                    if (!e.hasValues()) {
                        skip++;
                        continue;
                    }
                    ensureEditable(e);
                    toggleRarity(e, rarity, true);
                }
                case "rarity_off" -> {
                    if (!e.hasValues()) {
                        skip++;
                        continue;
                    }
                    ensureEditable(e);
                    toggleRarity(e, rarity, false);
                }
                default -> {
                    return WbLang.err("msg.batch_unknown", op);
                }
            }
            ok++;
        }
        MutableComponent action = switch (op) {
            case "enable" -> WbLang.tr("batch.op.enable");
            case "disable" -> WbLang.tr("batch.op.disable");
            case "delete" -> WbLang.tr("batch.op.delete");
            case "rarity_on" -> WbLang.tr("batch.op.rarity_on", rarity);
            case "rarity_off" -> WbLang.tr("batch.op.rarity_off", rarity);
            default -> Component.literal(op);
        };
        return skip > 0
                ? WbLang.ok("msg.batch_done_skip", action, ok, skip)
                : WbLang.ok("msg.batch_done", action, ok);
    }

    // ---------------------------------------------------------------- 校验

    /**
     * 校验一条词条的数据，返回错误列表（空 = 通过）。
     * 目的是拦住"神化会静默跳过、但界面上看不出来"的坏数据。
     */
    public List<Component> validate(AffixEntry e) {
        List<Component> errs = new ArrayList<>();
        JsonObject p = e.payload();
        if (p == null) {
            errs.add(WbLang.tr("validate.no_data"));
            return errs;
        }
        if (!p.has("type") || !p.get("type").isJsonPrimitive()) {
            errs.add(WbLang.tr("validate.missing_type"));
            return errs;
        }
        String shortType = e.typeShort();

        boolean needTypes = "attribute".equals(shortType) || "mob_effect".equals(shortType);
        if (!p.has("types")) {
            if (needTypes) {
                errs.add(WbLang.tr("validate.types_required"));
            }
        } else if (!p.get("types").isJsonArray()) {
            errs.add(WbLang.tr("validate.types_not_array"));
        } else {
            for (com.google.gson.JsonElement c : p.getAsJsonArray("types")) {
                String s = c.isJsonPrimitive() ? c.getAsString() : "";
                Boolean ok = AffixDescriber.isValidCategory(s);
                if (ok != null && !ok) {
                    errs.add(WbLang.tr("validate.bad_category", s));
                }
            }
        }

        if ("attribute".equals(shortType)) {
            if (!p.has("attribute")) {
                errs.add(WbLang.tr("validate.missing_attribute"));
            } else {
                ResourceLocation aid = ResourceLocation.tryParse(p.get("attribute").getAsString());
                if (aid == null || !ForgeRegistries.ATTRIBUTES.containsKey(aid)) {
                    errs.add(WbLang.tr("validate.attribute_missing", p.get("attribute").getAsString()));
                }
            }
            String op = p.has("operation") && p.get("operation").isJsonPrimitive() ? p.get("operation").getAsString() : "";
            if (!List.of("ADDITION", "MULTIPLY_BASE", "MULTIPLY_TOTAL").contains(op)) {
                errs.add(WbLang.tr("validate.bad_operation", op));
            }
        } else if ("mob_effect".equals(shortType)) {
            String mid = p.has("mob_effect") && p.get("mob_effect").isJsonPrimitive() ? p.get("mob_effect").getAsString() : "";
            ResourceLocation id = ResourceLocation.tryParse(mid);
            if (id == null || !ForgeRegistries.MOB_EFFECTS.containsKey(id)) {
                errs.add(WbLang.tr("validate.effect_missing", mid));
            }
        }

        JsonObject values = e.values();
        if (values != null) {
            for (String key : values.keySet()) {
                if (rarityByKey(key) == null) {
                    errs.add(WbLang.tr("validate.rarity_missing", key));
                    continue;
                }
                JsonElement blk = values.get(key);
                if (blk.isJsonObject()) {
                    checkStep(blk.getAsJsonObject(), errs, key);
                }
            }
        }
        return errs;
    }

    /** 界面输入的三元组先做一次检查，避免把神化解析不了的数据写进去 */
    private Component checkStepInput(double min, double steps, double step) {
        if (!Double.isFinite(min) || !Double.isFinite(steps) || !Double.isFinite(step)) {
            return WbLang.err("msg.bad_number");
        }
        if (steps < 1) {
            return WbLang.err("msg.steps_min");
        }
        if (steps != Math.rint(steps)) {
            return WbLang.err("msg.steps_int");
        }
        if (step < 0) {
            return WbLang.err("msg.step_negative");
        }
        return null;
    }

    private void checkStep(JsonObject o, List<Component> errs, String where) {
        if (!(o.has("min") && o.has("steps") && o.has("step"))) {
            return;
        }
        try {
            double min = o.get("min").getAsDouble();
            double steps = o.get("steps").getAsDouble();
            double step = o.get("step").getAsDouble();
            if (!Double.isFinite(min) || !Double.isFinite(steps) || !Double.isFinite(step)) {
                errs.add(WbLang.tr("validate.step_number", where));
            } else if (steps < 1) {
                errs.add(WbLang.tr("validate.step_min", where));
            } else if (step < 0) {
                errs.add(WbLang.tr("validate.step_negative", where));
            } else if (steps != Math.rint(steps)) {
                errs.add(WbLang.tr("validate.step_int", where));
            }
        } catch (Exception ex) {
            errs.add(WbLang.tr("validate.step_format", where));
        }
    }

    /** 拼接多条错误，分隔符也走语言表 */
    private static Component join(List<Component> parts) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                out.append(WbLang.tr("text.sep"));
            }
            out.append(parts.get(i));
        }
        return out;
    }

    /** 校验所有"我们生成过"的词条，返回可读错误（最多 3 条） */
    public Component validateTouched() {
        List<Component> all = new ArrayList<>();
        for (AffixEntry e : entries.values()) {
            if (!e.touched) {
                continue;
            }
            for (Component err : validate(e)) {
                all.add(WbLang.tr("validate.entry", e.id.toString(), err));
            }
        }
        if (all.isEmpty()) {
            return null;
        }
        MutableComponent out = WbLang.tr("validate.summary");
        for (int i = 0; i < Math.min(3, all.size()); i++) {
            out.append(WbLang.tr("validate.summary_item", all.get(i)));
        }
        if (all.size() > 3) {
            out.append(WbLang.tr("validate.summary_more", all.size()));
        }
        return out;
    }

    /** 重载后核对词条是否真的被神化注册了（没注册大概率是数据有问题） */
    private void probeLoaded() {
        if (!apotheosisLoaded()) {
            return;
        }
        Set<String> failed = new LinkedHashSet<>();
        for (AffixEntry e : entries.values()) {
            if (e.status != WB.ST_ACTIVE || (!e.touched && !e.custom)) {
                continue;
            }
            if (!AffixDescriber.isRegistered(e.id)) {
                failed.add(e.id.toString());
            }
        }
        loadFailed.clear();
        loadFailed.addAll(failed);
        if (failed.isEmpty() || server.getPlayerList() == null) {
            return;
        }
        MutableComponent msg = WbLang.tr("chat.warn", WbLang.tr("msg.load_failed", failed.size()));
        int i = 0;
        for (String id : failed) {
            if (i++ >= 3) {
                msg.append(WbLang.tr("msg.load_failed_more"));
                break;
            }
            msg.append(Component.literal(" " + id));
        }
        for (var p : server.getPlayerList().getPlayers()) {
            if (p.hasPermissions(2)) {
                p.sendSystemMessage(msg);
            }
        }
    }

    public Set<String> loadFailed() {
        return loadFailed;
    }

    // ---------------------------------------------------------------- 新增词条

    public Component create(JsonObject payload) {
        String idStr = payload.has("id") ? payload.get("id").getAsString() : "";
        ResourceLocation id = ResourceLocation.tryParse(idStr);
        if (id == null) {
            return WbLang.err("msg.bad_id");
        }
        if (entries.containsKey(id.toString())) {
            return WbLang.err("msg.id_exists", id.toString());
        }
        JsonObject json;
        boolean paste = payload.has("mode") && "paste".equals(payload.get("mode").getAsString());
        if (paste) {
            if (!payload.has("json") || !payload.get("json").isJsonObject()) {
                return WbLang.err("msg.clipboard_not_object");
            }
            json = payload.getAsJsonObject("json").deepCopy();
        } else {
            try {
                json = buildCreated(payload);
            } catch (Exception ex) {
                return WbLang.err("msg.build_failed", String.valueOf(ex.getMessage()));
            }
        }
        if (json == null || !json.has("type")) {
            return WbLang.err("msg.built_no_type");
        }
        AffixEntry e = new AffixEntry(id);
        e.custom = true;
        e.original = null;
        e.current = json;
        e.status = WB.ST_ACTIVE;
        e.touched = true;
        String name = payload.has("name") ? payload.get("name").getAsString().trim() : "";
        e.name = name.isEmpty() ? null : name;
        List<Component> errs = validate(e);
        if (!errs.isEmpty()) {
            return WbLang.err("msg.create_failed", join(errs));
        }
        entries.put(id.toString(), e);
        return null;
    }

    private JsonObject buildCreated(JsonObject p) {
        String mode = p.has("mode") ? p.get("mode").getAsString() : "copy";
        JsonArray rarityArr = p.has("rarities") && p.get("rarities").isJsonArray() ? p.getAsJsonArray("rarities") : new JsonArray();
        JsonArray catArr = p.has("cats") && p.get("cats").isJsonArray() ? p.getAsJsonArray("cats") : new JsonArray();
        double min = p.has("min") ? p.get("min").getAsDouble() : 0.1;
        double steps = p.has("steps") ? p.get("steps").getAsDouble() : 1;
        double step = p.has("step") ? p.get("step").getAsDouble() : 0.1;

        JsonObject values = new JsonObject();
        for (JsonElement el : rarityArr) {
            String key = el.getAsString();
            values.add(key, flatBlock(min, steps, step));
        }

        JsonObject out;
        if ("attribute".equals(mode) || "mob_effect".equals(mode)) {
            out = new JsonObject();
            out.addProperty("type", "attribute".equals(mode) ? "apotheosis:attribute" : "apotheosis:mob_effect");
            if ("attribute".equals(mode)) {
                out.addProperty("attribute", p.has("attribute") ? p.get("attribute").getAsString() : "minecraft:generic.max_health");
                out.addProperty("operation", p.has("operation") ? p.get("operation").getAsString() : "ADDITION");
                out.add("values", values);
            } else {
                out.addProperty("mob_effect", p.has("effect") ? p.get("effect").getAsString() : "minecraft:speed");
                out.addProperty("target", p.has("target") ? p.get("target").getAsString() : "HURT_SELF");
                JsonObject potionValues = new JsonObject();
                for (String key : values.keySet()) {
                    JsonObject block = new JsonObject();
                    JsonObject duration = new JsonObject();
                    duration.addProperty("min", min);
                    duration.addProperty("steps", steps);
                    duration.addProperty("step", step);
                    block.add("duration", duration);
                    block.addProperty("amplifier", 0);
                    block.addProperty("cooldown", 200);
                    potionValues.add(key, block);
                }
                out.add("values", potionValues);
            }
        } else {
            String source = p.has("source") ? p.get("source").getAsString() : null;
            AffixEntry src = source == null ? null : entries.get(source);
            if (src == null || src.payload() == null) {
                throw new IllegalArgumentException(WbLang.s("msg.template_missing"));
            }
            out = src.payload().deepCopy();
            JsonObject srcValues = src.values();
            JsonObject newValues = new JsonObject();
            for (String key : values.keySet()) {
                JsonElement block = null;
                if (srcValues != null) {
                    if (srcValues.has(key)) {
                        block = srcValues.get(key).deepCopy();
                    } else {
                        block = defaultBlock(srcValues);
                    }
                }
                if (block == null) {
                    block = flatBlock(min, steps, step);
                }
                newValues.add(key, block);
            }
            out.add("values", newValues);
        }

        // attribute / mob_effect 的 codec 要求 types 字段必须存在（空数组=所有部位）
        if (catArr.size() > 0 || "attribute".equals(mode) || "mob_effect".equals(mode)) {
            out.add("types", catArr.deepCopy());
        }
        return out;
    }

    // ---------------------------------------------------------------- 保存与重载

    public synchronized Component saveAndReload() {
        try {
            Component problems = validateTouched();
            if (problems != null) {
                return problems;
            }
            int written = writePack();
            saveState();
            descCache.clear();
            // 世界暂停时（单人打开界面时集成服务器不 tick），重载只能等恢复后再做
            if (server.isSameThread()) {
                needsReload = false;
                applyReload();
                return written > 0 ? WbLang.ok("msg.saved", written) : WbLang.ok("msg.saved_none");
            }
            pendingReload = true;
            return written > 0 ? WbLang.ok("msg.saved_pending", written) : WbLang.ok("msg.saved_none_pending");
        } catch (Exception ex) {
            WB.LOGGER.error("保存工作台失败", ex);
            return WbLang.err("msg.save_failed", String.valueOf(ex.getMessage()));
        }
    }

    public boolean hasPending() {
        return pendingReload;
    }

    /** 暂停结束后由服务端 tick 调用，把挂起的重载补上 */
    public synchronized void processPending() {
        if (!pendingReload) {
            return;
        }
        pendingReload = false;
        needsReload = false;
        applyReload();
    }

    private void applyReload() {
        try {
            PackRepository repo = server.getPackRepository();
            repo.reload();
            String packId = resolvePackId(repo);
            List<String> selected = new ArrayList<>(repo.getSelectedIds());
            if (Files.isDirectory(packDir.resolve("data")) && packId != null) {
                if (!selected.contains(packId)) {
                    selected.add(packId);
                }
            } else {
                selected.removeIf(id -> id.equals(packId) || id.endsWith("/" + WB.PACK_NAME));
            }
            repo.setSelected(selected);
            server.reloadResources(selected)
                    .thenAccept(v -> server.execute(this::probeLoaded))
                    .exceptionally(t -> {
                        WB.LOGGER.error("重新加载数据包失败", t);
                        return null;
                    });
        } catch (Exception ex) {
            WB.LOGGER.error("重载工作台数据包失败", ex);
        }
    }

    public boolean needsReload() {
        return needsReload;
    }

    private static boolean apotheosisLoaded() {
        try {
            return ModList.get().isLoaded("apotheosis");
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 词条在该品阶下的效果描述（Component JSON，客户端负责本地化）。
     * 描述由神化代码动态生成，所以这里直接调它的 API，并做进程内缓存。
     */
    public synchronized String describeJson(AffixEntry e, String rarityKey) {
        if (e == null || rarityKey == null || rarityKey.isEmpty() || !apotheosisLoaded()) {
            return "";
        }
        String key = e.id + "|" + rarityKey;
        String cached = descCache.get(key);
        if (cached != null) {
            return cached;
        }
        String json = "";
        try {
            Component desc = AffixDescriber.describe(e.id, rarityKey);
            if (desc != null && !desc.getString().isBlank()) {
                json = Component.Serializer.toJson(desc);
            }
        } catch (Throwable t) {
            WB.LOGGER.debug("生成词条描述失败 {}", e.id, t);
        }
        descCache.put(key, json);
        return json;
    }

    /**
     * 每次编辑后立即把改动落到磁盘（状态 + 数据包文件），但不动游戏内的数据包，
     * 这样即使中途关服也不会丢编辑；点「保存并重载」才真正生效。
     */
    public synchronized void markDirty() {
        needsReload = true;
        descCache.clear();
        try {
            writePack();
            saveState();
        } catch (Exception ex) {
            WB.LOGGER.error("写入工作台数据包失败", ex);
        }
    }

    /** 重新扫描数据包，同时保留当前未保存的编辑 */
    public synchronized void reloadFromDisk() {
        try {
            saveState();
        } catch (IOException ex) {
            WB.LOGGER.error("暂存工作台状态失败", ex);
        }
        refresh();
    }

    /** 把当前编辑状态写成存档数据包，返回写入的文件数 */
    public int writePack() throws IOException {
        Path dataDir = packDir.resolve("data");
        deleteRecursively(dataDir);
        Files.createDirectories(packDir);

        int written = 0;
        for (AffixEntry e : entries.values()) {
            JsonObject out = null;
            if (e.status == WB.ST_ACTIVE) {
                if (e.custom) {
                    out = e.current;
                } else if (e.current != null && e.original != null && !e.current.equals(e.original)) {
                    out = e.current;
                }
            } else {
                out = WB.parseObject(WB.FALSE_CONDITION_JSON);
            }
            if (out == null) {
                continue;
            }
            Path f = dataDir.resolve(e.id.getNamespace()).resolve(DataScanner.AFFIX_DIR).resolve(e.id.getPath() + ".json");
            Files.createDirectories(f.getParent());
            Files.writeString(f, WB.GSON.toJson(out), StandardCharsets.UTF_8);
            written++;
        }

        if (written == 0) {
            deleteRecursively(packDir);
            return 0;
        }

        String meta = "{\"pack\":{\"pack_format\":15,\"description\":\"Apotheosis Workbench (generated, do not edit by hand)\"}}";
        Files.writeString(packDir.resolve("pack.mcmeta"), meta, StandardCharsets.UTF_8);
        return written;
    }

    // ---------------------------------------------------------------- 导出 KubeJS

    /**
     * 把当前改动导出成 KubeJS 脚本（ServerEvents.highPriorityData 注入数据包）。
     * 和覆盖数据包是两套等效做法，二选一。
     */
    public synchronized Component exportKubeJs() {
        return exportKubeJs(false);
    }

    public synchronized Component exportKubeJs(boolean all) {
        List<AffixEntry> targets = new ArrayList<>();
        for (AffixEntry e : entries.values()) {
            if (all) {
                // 全量导出：把每条词条当前的样子都写出来，方便 KubeJS 完全接管
                if (e.status == WB.ST_ACTIVE || e.touched) {
                    targets.add(e);
                }
            } else if (e.status == WB.ST_ACTIVE) {
                if (e.custom || (e.current != null && e.original != null && !e.current.equals(e.original))) {
                    targets.add(e);
                }
            } else if (e.touched) {
                targets.add(e);
            }
        }
        if (targets.isEmpty()) {
            return all ? WbLang.err("msg.export_empty_all") : WbLang.err("msg.export_empty");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("// 由「神化工作台」Apotheosis Workbench 自动生成\n");
        sb.append("// 生成时间：").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).append('\n');
        sb.append(all ? "// 导出条数（全量）：" : "// 改动条数：").append(targets.size()).append('\n');
        sb.append("// 用法：放进 kubejs/server_scripts/ 后执行 /reload\n");
        sb.append("// 说明：这份脚本与工作台的覆盖数据包作用相同（都是往数据包里塞 affixes JSON），\n");
        sb.append("//       用了脚本就不要同时启用数据包，否则两套覆盖会互相打架。\n");
        sb.append("// 禁用的词条写的是 {\"conditions\":[{\"type\":\"forge:false\"}]}，神化会直接忽略它。\n");
        sb.append("ServerEvents.highPriorityData(event => {\n");
        for (AffixEntry e : targets) {
            JsonObject json;
            if (e.status == WB.ST_ACTIVE) {
                json = e.current;
                if (json == null) {
                    continue;
                }
            } else {
                json = WB.parseObject(WB.FALSE_CONDITION_JSON);
            }
            String id = e.id.getNamespace() + ":affixes/" + e.id.getPath();
            sb.append("\n\t// ").append(e.id).append(" · ").append(WB.statusName(e.status));
            if (e.custom) {
                sb.append(" · 自定义");
            }
            sb.append('\n');
            sb.append("\tevent.addJson('").append(id).append("', ")
                    .append(indentJson(WB.GSON.toJson(json), "\t"))
                    .append(");\n");
        }
        sb.append("});\n");

        try {
            Path gameDir = FMLPaths.GAMEDIR.get();
            boolean hasKubeJs = Files.isDirectory(gameDir.resolve("kubejs"));
            String fileName = all ? "apoth_workbench_affixes_all.js" : "apoth_workbench_affixes.js";
            Path target = hasKubeJs
                    ? gameDir.resolve("kubejs").resolve("server_scripts").resolve(fileName)
                    : gameDir.resolve("apoth_workbench_export").resolve(fileName);
            Files.createDirectories(target.getParent());
            Files.writeString(target, sb.toString(), StandardCharsets.UTF_8);
            String shown = gameDir.relativize(target).toString().replace('\\', '/');
            return WbLang.ok(hasKubeJs ? "msg.exported_kubejs" : "msg.exported_file", targets.size(), shown);
        } catch (Exception ex) {
            WB.LOGGER.error("导出 KubeJS 脚本失败", ex);
            return WbLang.err("msg.export_failed", String.valueOf(ex.getMessage()));
        }
    }

    private static String indentJson(String json, String pad) {
        String[] lines = json.split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                sb.append('\n').append(pad);
            }
            sb.append(lines[i]);
        }
        return sb.toString();
    }

    /** 存档数据包在 PackRepository 里的真实 ID（不同版本前缀可能不同，这里做兼容匹配） */
    private String resolvePackId(PackRepository repo) {
        for (String id : repo.getAvailableIds()) {
            if (id.equals(WB.PACK_ID) || id.endsWith("/" + WB.PACK_NAME)) {
                return id;
            }
        }
        return null;
    }

    /** 服务器启动时确保存档数据包处于启用状态 */
    public synchronized void ensurePackOnStart() {
        try {
            if (!Files.isDirectory(packDir.resolve("data"))) {
                return;
            }
            PackRepository repo = server.getPackRepository();
            repo.reload();
            String packId = resolvePackId(repo);
            if (packId == null) {
                WB.LOGGER.warn("未能在数据包列表中找到工作台数据包（{}）", WB.PACK_ID);
                return;
            }
            List<String> selected = new ArrayList<>(repo.getSelectedIds());
            if (selected.contains(packId)) {
                return;
            }
            selected.add(packId);
            repo.setSelected(selected);
            WB.LOGGER.info("已自动启用工作台覆盖数据包 {}", packId);
            server.reloadResources(selected).exceptionally(t -> {
                WB.LOGGER.error("启用工作台数据包失败", t);
                return null;
            });
        } catch (Exception ex) {
            WB.LOGGER.error("检查工作台数据包失败", ex);
        }
    }

    public static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path p : paths) {
                Files.deleteIfExists(p);
            }
        }
    }

    /** 供界面显示用的统计 */
    public Map<String, Integer> stats() {
        Map<String, Integer> m = new LinkedHashMap<>();
        int active = 0, disabled = 0, deleted = 0, custom = 0, modified = 0;
        for (AffixEntry e : entries.values()) {
            switch (e.status) {
                case WB.ST_DISABLED -> disabled++;
                case WB.ST_DELETED -> deleted++;
                default -> active++;
            }
            if (e.custom) {
                custom++;
            }
            if (e.modified()) {
                modified++;
            }
        }
        m.put("total", entries.size());
        m.put("active", active);
        m.put("disabled", disabled);
        m.put("deleted", deleted);
        m.put("custom", custom);
        m.put("modified", modified);
        m.put("needsReload", needsReload ? 1 : 0);
        return m;
    }
}
