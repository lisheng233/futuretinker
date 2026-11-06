package com.lisheng.futuretinker.mace;

import com.lisheng.futuretinker.FutureTinker;
import com.lisheng.futuretinker.module.CreativeTab;
import com.lisheng.futuretinker.module.FtModule;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegisterEvent;
import slimeknights.tconstruct.library.materials.MaterialRegistry;

/**
 * 狼牙棒（模块三）。
 *
 * <p>两个部件：棒头（新部件，读硬度/韧性两条材料统计）+ 坚韧手柄（读重量）。</p>
 * <p>核心机制：钩扯流血（读**目标**速度，和长矛读自己速度相反）、棒头形态决定的横扫角度、
 * 手柄重量决定的击退与持有时移速。</p>
 */
public final class MaceModule implements FtModule {
    public static final String ID = "mace";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void attach(IEventBus modBus) {
        MinecraftForge.EVENT_BUS.addListener(MaceCombat::onAttack);
        // 客户端上色（服务器端不加载这个类）
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.lisheng.futuretinker.mace.MaceClient.register(modBus));
        FutureTinker.LOGGER.info("Future Tinker: 狼牙棒已接入（钩扯流血 + 形态横扫 + 手柄惯性）");
    }

    @Override
    public void commonSetup() {
        // 材料统计类型要在材料数据加载之前注册；模块关掉就不注册
        MaterialRegistry.getInstance().registerStatType(HardnessMaterialStats.TYPE);
        MaterialRegistry.getInstance().registerStatType(ToughnessMaterialStats.TYPE);
    }

    @Override
    public void registerRegistries(RegisterEvent event) {
        MaceContent.registerItems(event);
        MaceContent.registerEffects(event);
        if (Registries.ITEM.equals(event.getRegistryKey())) {
            CreativeTab.add(MaceContent::mace);
            CreativeTab.add(MaceContent::maceHead);
        }
    }

    @Override
    public List<ResourceLocation> contentItems() {
        return List.of(FutureTinker.id(MaceContent.ID), FutureTinker.id(MaceContent.HEAD_ID));
    }
}
