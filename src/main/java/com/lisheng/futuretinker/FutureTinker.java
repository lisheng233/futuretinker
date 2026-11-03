package com.lisheng.futuretinker;

import com.lisheng.futuretinker.config.FutureTinkerConfig;
import com.lisheng.futuretinker.module.ModuleRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Future Tinker —— 用匠魂的材料、部件与强化系统重做高版本原版装备。
 *
 * <p>模组本身不做任何内容注册：所有内容属于某个模块，模块是否启用由
 * {@code config/futuretinker-common.toml} 里的 {@code modules.<id>.enabled} 决定。</p>
 *
 * <p>构造阶段的三件事，顺序不能变：</p>
 * <ol>
 *   <li>注册 {@link ForgeConfigSpec}（让 Forge 知道有这个文件）；</li>
 *   <li>{@link FutureTinkerConfig#bootstrap} 提前把开关读出来——注册表事件之后 Forge 才加载配置，
 *       见 {@link FutureTinkerConfig} 里的实测顺序；</li>
 *   <li>接上模块表与总线监听器。</li>
 * </ol>
 */
@Mod(FutureTinker.MOD_ID)
public final class FutureTinker {
    public static final String MOD_ID = "futuretinker";
    public static final Logger LOGGER = LoggerFactory.getLogger("FutureTinker");

    public FutureTinker() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, FutureTinkerConfig.SPEC);
        FutureTinkerConfig.bootstrap(FMLPaths.CONFIGDIR.get());
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModuleRegistry.init(modBus);
    }

    /** 本模组命名空间下的资源 id。注册名一律加 {@code futuretinker:} 前缀，避免与其它匠魂附属撞名。 */
    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
