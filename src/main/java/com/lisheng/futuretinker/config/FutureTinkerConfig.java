package com.lisheng.futuretinker.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.lisheng.futuretinker.FutureTinker;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 模块开关配置。
 *
 * <h2>为什么这个类要「偷偷」先读一遍文件</h2>
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
 */
public final class FutureTinkerConfig {
    /** Forge 给 COMMON 配置取的文件名，早期读取必须读同一个文件。 */
    public static final String FILE_NAME = FutureTinker.MOD_ID + "-common.toml";

    public static final ForgeConfigSpec SPEC;

    /** 模块 id -> 开关。顺序即配置文件里的出现顺序。 */
    private static final Map<String, ForgeConfigSpec.BooleanValue> SWITCHES = new LinkedHashMap<>();

    /** 构造阶段从文件里读到的值；配置加载之后不再使用，只用于对照。 */
    private static final Map<String, Boolean> EARLY = new LinkedHashMap<>();

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment(
                "Future Tinker —— 把高版本原版装备用匠魂的材料/部件/强化系统重新实现。",
                "modules.<id>.enabled 是模块总开关：关闭后该模块不注册任何内容（不是数值为 0）。",
                "改动需要重启游戏：开关在注册表填充之前就被读取，热重载不会补注册已经缺失的内容。")
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
        SPEC = builder.build();
    }

    private FutureTinkerConfig() {}

    private static void defineModule(ForgeConfigSpec.Builder builder, String moduleId,
            boolean defaultEnabled, String... comments) {
        builder.comment(comments).push(moduleId);
        SWITCHES.put(moduleId, builder.comment("是否启用该模块。").define("enabled", defaultEnabled));
    }

    /**
     * 模组构造阶段调用一次：把模块开关提前定下来。
     *
     * <p>文件不存在（首次启动）或解析失败时按 {@link ForgeConfigSpec.ConfigValue#getDefault()} 处理，
     * 与 Forge 之后写入/修正的结果一致。</p>
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
}
