package com.lisheng.futuretinker.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.lisheng.futuretinker.FutureTinker;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraftforge.common.ForgeConfigSpec;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;

/**
 * 模块开关 + 各模块的公共数值配置。
 *
 * <h2>模块开关为什么「偷偷」先读一遍文件</h2>
 *
 * <p>契约是：<b>关掉的模块不向任何注册表写入内容</b>——对整合包来说它「不存在」，
 * 而不是「存在但数值为 0」（后者会留下刷怪蛋、标签、配方引用这类装不回去的残骸）。</p>
 *
 * <p>这条契约要求开关在注册表填充之前就有值。而这台 Forge 上的实测顺序是
 * （1.20.1 / Forge 47.3.0，见 {@code ModuleRegistry} 的探针记录）：</p>
 *
 * <pre>
 * 模组构造        → 配置未加载
 * 所有 RegisterEvent（sound_event … worldgen/biome） → 配置未加载
 * ModConfigEvent.Loading                        → 配置已加载
 * FMLCommonSetupEvent                           → 配置已加载
 * </pre>
 *
 * <p>也就是说 {@link ForgeConfigSpec#isLoaded()} 在注册阶段永远是 false，
 * 直接读 {@code ConfigValue} 只会抛异常。所以这里在模组构造阶段用 nightconfig
 * 把<b>同一个文件</b>先读一遍（schema 仍然只有 {@link #SPEC} 一份，
 * 早期读取只取值、缺值就回落到 {@code getDefault()}），
 * 之后 Forge 自己加载同一个文件。两条读取路径必须给出同一个答案，
 * {@code ModuleGateTests} 会把这件事当成断言来查。</p>
 *
 * <p><b>数值配置</b>不参与注册表决策：它们在 Forge 加载配置后由
 * {@link #SPEC} 读取（访问器在未加载时回落到默认值）。改数值后生效的时机：
 * 物品属性/攻击/受击下一次读取时；属性修饰符建议重新装备一次或重进世界。</p>
 */
public final class FutureTinkerConfig {
    /** Forge 给 COMMON 配置取的文件名，早期读取必须读同一个文件。 */
    public static final String FILE_NAME = FutureTinker.MOD_ID + "-common.toml";

    // ===== 与工具定义 JSON 对齐的默认基值（属性事件只加「配置值 - 默认值」的差） =====
    public static final double WOLF_ARMOR_DEFAULT_ARMOR_MULTIPLIER = 2.0D;
    public static final double SPEAR_DEFAULT_ATTACK_DAMAGE = 1.0D;
    public static final double SPEAR_DEFAULT_ATTACK_SPEED = 1.0D;
    public static final double MACE_DEFAULT_ATTACK_DAMAGE = 3.0D;
    public static final double MACE_DEFAULT_ATTACK_SPEED = 0.9D;

    public static final ForgeConfigSpec SPEC;

    /** 模块 id -> 开关。顺序即配置文件里的出现顺序。 */
    private static final Map<String, ForgeConfigSpec.BooleanValue> SWITCHES = new LinkedHashMap<>();

    /** 构造阶段从文件里读到的值；配置加载之后不再使用，只用于对照。 */
    private static final Map<String, Boolean> EARLY = new LinkedHashMap<>();

    private static final ForgeConfigSpec.DoubleValue WOLF_ARMOR_MULTIPLIER;
    private static final ForgeConfigSpec.DoubleValue WOLF_TOUGHNESS_MULTIPLIER;
    private static final ForgeConfigSpec.DoubleValue WOLF_ARMOR_BONUS;
    private static final ForgeConfigSpec.DoubleValue WOLF_TOUGHNESS_BONUS;
    private static final ForgeConfigSpec.DoubleValue WOLF_DAMAGE_ABSORPTION;

    private static final ForgeConfigSpec.DoubleValue SPEAR_BASE_DAMAGE;
    private static final ForgeConfigSpec.DoubleValue SPEAR_BASE_SPEED;
    private static final ForgeConfigSpec.DoubleValue SPEAR_REACH;
    private static final ForgeConfigSpec.DoubleValue SPEAR_CHARGE_SCALE;
    private static final ForgeConfigSpec.DoubleValue SPEAR_FATIGUE_BONUS;

    private static final ForgeConfigSpec.DoubleValue MACE_BASE_DAMAGE;
    private static final ForgeConfigSpec.DoubleValue MACE_BASE_SPEED;
    private static final ForgeConfigSpec.IntValue MACE_BLEED_DURATION;
    private static final ForgeConfigSpec.DoubleValue MACE_BLEED_MOVE_SPEED;
    private static final ForgeConfigSpec.DoubleValue MACE_HARD_DAMAGE_MULTIPLIER;
    private static final ForgeConfigSpec.DoubleValue MACE_HANDLE_PENALTY;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> MACE_OVERRIDES;

    /** 配置列表的内容快照；只有内容变化时才重新解析。 */
    private static volatile List<String> cachedOverrideStrings = List.of();
    private static volatile Map<String, float[]> cachedOverrides = Map.of();

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment(
                "Future Tinker —— 把高版本原版装备用匠魂的材料/部件/强化系统重新实现。",
                "modules.<id>.enabled 是模块总开关：关闭后该模块不注册任何内容（不是数值为 0）。",
                "改动需要重启游戏：开关在注册表填充之前就被读取，热重载不会补注册已经缺失的内容。",
                "下面的数值配置在配置加载后读取；默认值与工具定义 JSON 的 base_stats 对齐。")
                .push("modules");

        defineModule(builder, "wolfarmor", true,
                "匠魂化狼铠：外层甲片（护甲值/韧性）+ 内衬（特殊效果）+ 绑定（可修复性）。",
                "面向原版狼的宠物装备，不是给傀儡穿的铠甲。");
        defineModule(builder, "spear", true,
                "匠魂化长矛：相对速度伤害、击落骑乘、持矛疲劳三件套。",
                "矛头材料决定冲锋系数与击落阈值，手柄材料决定疲劳窗口。");

        defineModule(builder, "mace", true,
                "匠魂化狼牙棒：钩扯流血（读目标速度）、棒头形态决定的横扫、手柄惯性。",
                "棒头读硬度/韧性两条材料统计，手柄读重量（耐久修正）。");
        builder.pop();

        builder.comment(
                        "狼铠的公共基值。",
                        "armor_multiplier 是总护甲倍率，默认 2.0 与 wolf_armor_chestplate.json 的",
                        "multiply_stats 对齐；配置会以倍率修饰符叠在材料护甲之上。",
                        "armor_bonus/toughness_bonus 是平加值，默认 0。",
                        "damage_absorption 是受击时由狼铠耐久吸收的比例（1.0 = 原版 1:1）。")
                .push("wolfarmor");
        WOLF_ARMOR_MULTIPLIER = builder.defineInRange("armor_multiplier", 2.0D, 0.0D, 20.0D);
        WOLF_TOUGHNESS_MULTIPLIER = builder.defineInRange("toughness_multiplier", 1.0D, 0.0D, 20.0D);
        WOLF_ARMOR_BONUS = builder.defineInRange("armor_bonus", 0.0D, -100.0D, 100.0D);
        WOLF_TOUGHNESS_BONUS = builder.defineInRange("toughness_bonus", 0.0D, -100.0D, 100.0D);
        WOLF_DAMAGE_ABSORPTION = builder.defineInRange("damage_absorption", 1.0D, 0.0D, 1.0D);
        builder.pop();

        builder.comment(
                        "长矛的公共基值；默认值与 spear.json 的 base_stats 对齐。",
                        "base_attack_damage/base_attack_speed 会以差额加在主手属性上。",
                        "reach_bonus 是额外触及（原版近战 3.0，长矛默认 +1.5 = 4.5）。",
                        "charge_damage_scale 是冲锋伤害的额外全局倍率（叠在材料倍率上）。",
                        "handle_fatigue_bonus_ticks 是手柄耐久修正换算出的窗口 tick 常数，默认 10。")
                .push("spear");
        SPEAR_BASE_DAMAGE = builder.defineInRange("base_attack_damage", SPEAR_DEFAULT_ATTACK_DAMAGE, 0.0D, 100.0D);
        SPEAR_BASE_SPEED = builder.defineInRange("base_attack_speed", SPEAR_DEFAULT_ATTACK_SPEED, 0.0D, 100.0D);
        SPEAR_REACH = builder.defineInRange("reach_bonus", 1.5D, 0.0D, 10.0D);
        SPEAR_CHARGE_SCALE = builder.defineInRange("charge_damage_scale", 1.0D, 0.0D, 10.0D);
        SPEAR_FATIGUE_BONUS = builder.defineInRange("handle_fatigue_bonus_ticks", 10.0D, 0.0D, 100.0D);
        builder.pop();

        builder.comment(
                        "狼牙棒的公共基值；默认值与 mace.json 的 base_stats 对齐。",
                        "bleed_duration/bleed_move_speed 控制钩扯流血的持续与触发速度。",
                        "hard_damage_multiplier 是 HARD 形态额外魔法伤害 = 硬度 × 此值。",
                        "handle_weight_penalty 是重手柄的最大移速惩罚（默认 0.08 = -8%）。")
                .push("mace");
        MACE_BASE_DAMAGE = builder.defineInRange("base_attack_damage", MACE_DEFAULT_ATTACK_DAMAGE, 0.0D, 100.0D);
        MACE_BASE_SPEED = builder.defineInRange("base_attack_speed", MACE_DEFAULT_ATTACK_SPEED, 0.0D, 100.0D);
        MACE_BLEED_DURATION = builder.defineInRange("bleed_duration", 200, 20, 72000);
        MACE_BLEED_MOVE_SPEED = builder.defineInRange("bleed_move_speed", 0.05D, 0.0D, 10.0D);
        MACE_HARD_DAMAGE_MULTIPLIER = builder.defineInRange("hard_damage_multiplier", 1.5D, 0.0D, 10.0D);
        MACE_HANDLE_PENALTY = builder.defineInRange("handle_weight_penalty", 0.08D, 0.0D, 1.0D);
        builder.comment(
                        "棒头硬度/韧性覆盖表，优先级最高（高于材料数据、内置经典表和推导）。",
                        "格式：\"material_id=hardness,toughness\"，例如",
                        "  \"tconstruct:nahuatl=4.0,2.5\"",
                        "可以给本模组未内置的材料补值；同一 ID 重复出现时最后一个生效。")
                .push("material_stats");
        MACE_OVERRIDES = builder.defineList("overrides", List.of(), value -> value instanceof String);
        builder.pop();
        builder.pop();

        SPEC = builder.build();
    }

    private FutureTinkerConfig() {}

    private static void defineModule(ForgeConfigSpec.Builder builder, String moduleId,
            boolean defaultEnabled, String... comments) {
        builder.comment(comments).push(moduleId);
        SWITCHES.put(moduleId, builder.comment("是否启用该模块。").define("enabled", defaultEnabled));
        // 必须弹回 modules：少了这一行，后面的模块会嵌进前一个模块的节里，
        // 生成 modules.wolfarmor.spear.mace.enabled 这种路径，文档里的开关就不存在了。
        builder.pop();
    }

    /**
     * 模组构造阶段调用一次：把模块开关提前定下来。
     *
     * <p>文件不存在（首次启动）或解析失败时按 {@link ForgeConfigSpec.ConfigValue#getDefault()} 处理，
     * 与 Forge 之后写入/修正的结果一致。数值配置不在这里读——它们不参与注册决策。</p>
     */
    public static void bootstrap(Path configDir) {
        Path file = configDir.resolve(FILE_NAME);
        CommentedConfig parsed = null;
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                // nightconfig 3.6.x 的 TomlParser 只有 parse(Reader)；Forge 写这个文件时用的是 UTF-8
                // （实测文件头字节是 e2 80 94 这样的 UTF-8 序列），所以这里也显式按 UTF-8 读。
                parsed = new TomlParser().parse(reader);
            } catch (IOException | RuntimeException e) {
                FutureTinker.LOGGER.warn("Future Tinker: 启动期读取 {} 失败，模块开关按默认值处理；"
                        + "Forge 稍后会自行加载并修正该文件: {}", file, e.toString());
            }
        }

        int fromFile = 0;
        for (Map.Entry<String, ForgeConfigSpec.BooleanValue> entry : SWITCHES.entrySet()) {
            Boolean value = null;
            if (parsed != null) {
                Object raw = parsed.get(List.of("modules", entry.getKey(), "enabled"));
                if (raw instanceof Boolean bool) {
                    value = bool;
                    fromFile++;
                }
            }
            EARLY.put(entry.getKey(), value != null ? value : entry.getValue().getDefault());
        }
        FutureTinker.LOGGER.info("Future Tinker 模块开关（注册表填充前，从 {} 读到 {} 项）: {}",
                FILE_NAME, fromFile, EARLY);
    }

    /**
     * 读某个模块的开关。配置已加载时走 {@link ForgeConfigSpec}，否则走 {@link #bootstrap(Path)} 的早期值。
     *
     * @throws IllegalArgumentException 模块没有声明开关（新增模块忘了加配置，属于必须修的 bug）
     */
    public static boolean isModuleEnabled(String moduleId) {
        ForgeConfigSpec.BooleanValue value = SWITCHES.get(moduleId);
        if (value == null) {
            throw new IllegalArgumentException("Future Tinker: 模块 '" + moduleId + "' 没有配置开关，"
                    + "已声明的模块是 " + SWITCHES.keySet());
        }
        if (SPEC.isLoaded()) {
            return value.get();
        }
        return EARLY.getOrDefault(moduleId, value.getDefault());
    }

    /** 构造阶段读到的开关快照（只读）。用于和 Forge 自己读到的值对照。 */
    public static Map<String, Boolean> earlySwitches() {
        return Collections.unmodifiableMap(EARLY);
    }

    /** 已声明开关的模块 id（只读）。 */
    public static Set<String> declaredModules() {
        return Collections.unmodifiableSet(SWITCHES.keySet());
    }

    // ===== 数值访问器：配置未加载时回落到 schema 默认值 =====

    public static double wolfArmorMultiplier() {
        return doubleValue(WOLF_ARMOR_MULTIPLIER);
    }

    public static double wolfArmorToughnessMultiplier() {
        return doubleValue(WOLF_TOUGHNESS_MULTIPLIER);
    }

    public static double wolfArmorBonus() {
        return doubleValue(WOLF_ARMOR_BONUS);
    }

    public static double wolfArmorToughnessBonus() {
        return doubleValue(WOLF_TOUGHNESS_BONUS);
    }

    public static double wolfArmorDamageAbsorption() {
        return doubleValue(WOLF_DAMAGE_ABSORPTION);
    }

    public static double spearBaseAttackDamage() {
        return doubleValue(SPEAR_BASE_DAMAGE);
    }

    public static double spearBaseAttackSpeed() {
        return doubleValue(SPEAR_BASE_SPEED);
    }

    public static double spearReachBonus() {
        return doubleValue(SPEAR_REACH);
    }

    public static double spearChargeDamageScale() {
        return doubleValue(SPEAR_CHARGE_SCALE);
    }

    public static double spearHandleFatigueBonusTicks() {
        return doubleValue(SPEAR_FATIGUE_BONUS);
    }

    public static double maceBaseAttackDamage() {
        return doubleValue(MACE_BASE_DAMAGE);
    }

    public static double maceBaseAttackSpeed() {
        return doubleValue(MACE_BASE_SPEED);
    }

    public static int maceBleedDuration() {
        return SPEC.isLoaded() ? MACE_BLEED_DURATION.get() : MACE_BLEED_DURATION.getDefault();
    }

    public static double maceBleedMoveSpeed() {
        return doubleValue(MACE_BLEED_MOVE_SPEED);
    }

    public static double maceHardDamageMultiplier() {
        return doubleValue(MACE_HARD_DAMAGE_MULTIPLIER);
    }

    public static double maceHandleWeightPenalty() {
        return doubleValue(MACE_HANDLE_PENALTY);
    }

    /**
     * 棒头硬/韧性的配置覆盖；没有条目返回 null。
     * 先按完整 {@link MaterialVariantId}（可能带 {@code #variant}）匹配，再按基础材料 id 匹配。
     */
    @Nullable
    public static float[] maceMaterialOverride(@Nullable MaterialVariantId material) {
        if (material == null) {
            return null;
        }
        Map<String, float[]> overrides = overrides();
        float[] hit = overrides.get(material.toString());
        if (hit == null) {
            hit = overrides.get(material.getId().toString());
        }
        return hit;
    }

    /**
     * 解析 {@code material_id=hardness,toughness} 列表。无效条目跳过并告警；同一 id 后者覆盖前者。
     * public 供 GameTest 直接验证解析规则。
     */
    public static Map<String, float[]> parseMaceOverrides(List<? extends String> entries) {
        Map<String, float[]> result = new LinkedHashMap<>();
        for (String entry : entries) {
            if (entry == null) {
                continue;
            }
            String line = entry.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int equals = line.indexOf('=');
            if (equals <= 0 || equals == line.length() - 1) {
                FutureTinker.LOGGER.warn("Future Tinker: 忽略无效的狼牙棒材料覆盖 '{}'（缺少 id=hardness,toughness）", entry);
                continue;
            }
            String id = line.substring(0, equals).trim();
            String[] values = line.substring(equals + 1).trim().split("[,/\\s]+");
            if (values.length != 2) {
                FutureTinker.LOGGER.warn("Future Tinker: 忽略无效的狼牙棒材料覆盖 '{}'（需要两个数值）", entry);
                continue;
            }
            try {
                float hardness = Float.parseFloat(values[0]);
                float toughness = Float.parseFloat(values[1]);
                if (!Float.isFinite(hardness) || !Float.isFinite(toughness)) {
                    throw new NumberFormatException("not finite");
                }
                result.put(id, new float[] {
                        clamp(hardness, 0.01F, 100.0F),
                        clamp(toughness, 0.01F, 100.0F)});
            } catch (NumberFormatException e) {
                FutureTinker.LOGGER.warn("Future Tinker: 忽略无效的狼牙棒材料覆盖 '{}'（数值无法解析）", entry);
            }
        }
        return result;
    }

    private static Map<String, float[]> overrides() {
        if (!SPEC.isLoaded()) {
            return Map.of();
        }
        List<? extends String> raw = MACE_OVERRIDES.get();
        List<String> snapshot = new ArrayList<>(raw == null ? 0 : raw.size());
        if (raw != null) {
            snapshot.addAll(raw);
        }
        if (!snapshot.equals(cachedOverrideStrings)) {
            // 先写解析结果，再写快照：读者最多多解析一次，不会读到旧表配新快照
            cachedOverrides = Collections.unmodifiableMap(parseMaceOverrides(snapshot));
            cachedOverrideStrings = snapshot;
        }
        return cachedOverrides;
    }

    private static double doubleValue(ForgeConfigSpec.DoubleValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
