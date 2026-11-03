package com.lisheng.futuretinker.module;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegisterEvent;

/**
 * 一个内容模块（狼铠、长矛……）。
 *
 * <p>生命周期由 {@link ModuleRegistry} 统一驱动，模块自己不订阅总线：</p>
 * <ol>
 *   <li>{@link #attach(IEventBus)} —— 模组构造阶段，只挂监听器，<b>不能读配置</b>，也不能注册内容。</li>
 *   <li>{@link #registerRegistries(RegisterEvent)} —— 只有模块被启用时才会被调用，
 *       配置此时一定已加载。注册名一律走 {@code futuretinker:} 前缀。</li>
 *   <li>{@link #commonSetup(FMLCommonSetupEvent)} —— 只有模块被启用时才会被调用。</li>
 * </ol>
 */
public interface FtModule {
    /** 模块 id，同时是配置节名、注册名后缀与创造模式标签页路径。必须是小写、非空。 */
    String id();

    /** 模组构造阶段调用（无论模块是否启用）。只允许挂监听器。 */
    default void attach(IEventBus modBus) {}

    /** 模块启用时的注册表写入阶段。 */
    default void registerRegistries(RegisterEvent event) {}

    /** 模块启用时的通用初始化阶段（由 {@link ModuleRegistry} 放进 enqueueWork 里跑）。 */
    default void commonSetup() {}
}
