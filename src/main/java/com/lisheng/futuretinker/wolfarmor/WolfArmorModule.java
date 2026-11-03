package com.lisheng.futuretinker.wolfarmor;

import com.lisheng.futuretinker.FutureTinker;
import com.lisheng.futuretinker.module.FtModule;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegisterEvent;

/**
 * 匠魂化狼铠（模块一）。
 *
 * <p>定位是**护甲的变种**：一件 {@code ModifiableArmorItem}，材料强度来自胸甲板材统计，
 * 装/脱靠狼的护甲槽，装配与修复走匠魂既有流程。部件与护甲的定义、数据形状见
 * {@link WolfArmorContent}。</p>
 */
public final class WolfArmorModule implements FtModule {
    public static final String ID = "wolfarmor";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void attach(IEventBus modBus) {
        // 交互是 forge 总线上的事件；模块被关掉时这个方法根本不会被调用，所以不用再判开关。
        // 两个事件都监听：真客户端点实体派发 EntityInteractSpecific，服务端交互派发 EntityInteract。
        MinecraftForge.EVENT_BUS.addListener(WolfArmorInteraction::onEntityInteract);
        MinecraftForge.EVENT_BUS.addListener(WolfArmorInteraction::onEntityInteractSpecific);
        // 死亡掉落：实测默认情况下铠甲会直接消失（见 WolfArmorDrops 注释）。
        MinecraftForge.EVENT_BUS.addListener(WolfArmorDrops::onLivingDrops);
        FutureTinker.LOGGER.info("Future Tinker: 狼铠护甲定义 id = {}（对应 data/futuretinker/tinkering/tool_definitions/*.json）",
                WolfArmorContent.armorDefinitionId());
    }

    @Override
    public void registerRegistries(RegisterEvent event) {
        // 物品在 RegisterEvent 里创建（Item 构造函数会写注册表，构造阶段注册表已冻结）。
        WolfArmorContent.registerItems(event);
        if (Registries.CREATIVE_MODE_TAB.equals(event.getRegistryKey())) {
            event.register(Registries.CREATIVE_MODE_TAB, FutureTinker.id(ID), () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + FutureTinker.MOD_ID + "." + ID))
                    .icon(() -> new ItemStack(WolfArmorContent.wolfArmor()))
                    .displayItems((parameters, output) -> {
                        output.accept(WolfArmorContent.plating());
                        output.accept(WolfArmorContent.lining());
                        output.accept(WolfArmorContent.binding());
                        output.accept(WolfArmorContent.wolfArmor());
                    })
                    .build());
        }
    }
}
