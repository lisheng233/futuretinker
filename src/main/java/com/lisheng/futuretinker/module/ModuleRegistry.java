package com.lisheng.futuretinker.module;

import com.lisheng.futuretinker.FutureTinker;
import com.lisheng.futuretinker.config.FutureTinkerConfig;
import com.lisheng.futuretinker.mace.MaceModule;
import com.lisheng.futuretinker.spear.SpearModule;
import com.lisheng.futuretinker.wolfarmor.WolfArmorModule;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.RegisterEvent;

/**
 * 模块表与开关闸门。
 *
 * <p>闸门只有一处：{@link #onRegisterRegistries(RegisterEvent)}。它会读
 * {@link FutureTinkerConfig#isModuleEnabled(String)}——配置此时还没被 Forge 加载，
 * 读到的是 {@code FutureTinkerConfig} 在构造阶段提前读出来的值（这一点有实测依据，
 * 见该类注释）。每次注册阶段都重读一遍并要求两次一致，
 * 记录值存进 {@link #gatedState(String)}，这是 GameTest 唯一的证据来源。</p>
 */
public final class ModuleRegistry {
    private static final List<FtModule> MODULES = List.of(new WolfArmorModule(), new SpearModule(), new MaceModule());

    /** 模块 id -> 注册阶段读到的开关值。空 = 注册阶段还没跑到。 */
    private static final Map<String, Boolean> GATED = new LinkedHashMap<>();

    private static boolean announced;

    private ModuleRegistry() {}

    public static List<FtModule> modules() {
        return MODULES;
    }

    /**
     * 注册阶段读到的开关值；empty 表示这个模块的注册阶段还没跑到
     * （模块表为空，或 RegisterEvent 监听器根本没被调用）。
     */
    public static Optional<Boolean> gatedState(String moduleId) {
        return Optional.ofNullable(GATED.get(moduleId));
    }

    /** 模组构造阶段调用一次。 */
    public static void init(IEventBus modBus) {
        validateIds();
        modBus.addListener(ModuleRegistry::onRegisterRegistries);
        modBus.addListener(ModuleRegistry::onCommonSetup);
        for (FtModule module : MODULES) {
            module.attach(modBus);
        }
        FutureTinker.LOGGER.info("Future Tinker: 声明了 {} 个模块 {}", MODULES.size(), ids());
    }

    private static void onRegisterRegistries(RegisterEvent event) {
        List<String> enabled = new ArrayList<>();
        List<String> disabled = new ArrayList<>();
        for (FtModule module : MODULES) {
            boolean on = FutureTinkerConfig.isModuleEnabled(module.id());
            GATED.put(module.id(), on);
            if (on) {
                enabled.add(module.id());
                module.registerRegistries(event);
            } else {
                disabled.add(module.id());
            }
        }
        if (!announced) {
            announced = true;
            FutureTinker.LOGGER.info("Future Tinker 模块闸门（注册阶段）: 启用={} 关闭={}", enabled, disabled);
        }
        // 唯一的创造模式标签页：物品都注册完之后才建，里面只会有启用的模块登记过的东西
        CreativeTab.register(event);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        for (FtModule module : MODULES) {
            if (FutureTinkerConfig.isModuleEnabled(module.id())) {
                event.enqueueWork(module::commonSetup);
            }
        }
    }

    /**
     * 模块 id 的静态契约：非空、小写、唯一，并且每个模块都必须有配置开关。
     * 违反就是模组作者的 bug，启动即失败比留到玩家存档里爆好。
     */
    private static void validateIds() {
        Set<String> seen = new HashSet<>();
        for (FtModule module : MODULES) {
            String id = module.id();
            if (id == null || id.isBlank()) {
                throw new IllegalStateException("Future Tinker: 有模块没有 id（" + module.getClass().getName() + "）");
            }
            if (!id.equals(id.toLowerCase(Locale.ROOT)) || !id.equals(id.trim())) {
                throw new IllegalStateException("Future Tinker: 模块 id 必须是小写且无空白: '" + id + "'");
            }
            if (!seen.add(id)) {
                throw new IllegalStateException("Future Tinker: 模块 id 重复: '" + id + "'");
            }
            if (!FutureTinkerConfig.declaredModules().contains(id)) {
                throw new IllegalStateException("Future Tinker: 模块 '" + id + "' 没有配置开关，"
                        + "关不掉的内容不符合「模块可独立启停」。已声明: " + FutureTinkerConfig.declaredModules());
            }
        }
    }

    private static List<String> ids() {
        List<String> list = new ArrayList<>(MODULES.size());
        for (FtModule module : MODULES) {
            list.add(module.id());
        }
        return Collections.unmodifiableList(list);
    }
}
