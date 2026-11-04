package com.lisheng.futuretinker.spear;

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
 * 匠魂化长矛（模块二）。
 *
 * <p>复刻高版本原版长矛的三条机制：</p>
 * <ul>
 *   <li><b>戳刺</b>：4.5 格触及、2 格内打不到、必须满蓄力、没有暴击与冲刺击退（{@link SpearCombat}）；</li>
 *   <li><b>冲锋</b>：按住右键压低，伤害 = 1 + 倍率 × 相对速度，够快则击退、挑落骑乘（{@link SpearCharge}）；</li>
 *   <li><b>疲劳</b>：engaged → tired → disengaged 三个阶段，越往后越少效果；窗口长度由手柄材料决定。</li>
 * </ul>
 *
 * <p>注册名带 {@code futuretinker:} 前缀，与 Tinkers' Tools / Dreamtinker 的长矛共存。</p>
 */
public final class SpearModule implements FtModule {
    public static final String ID = "spear";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void attach(IEventBus modBus) {
        // 戳刺的规矩是 forge 总线上的玩家事件；模块被关掉时这个方法根本不会被调用
        MinecraftForge.EVENT_BUS.addListener(SpearCombat::onAttack);
        MinecraftForge.EVENT_BUS.addListener(SpearCombat::onCritical);
    }

    @Override
    public void registerRegistries(RegisterEvent event) {
        SpearContent.registerItems(event);
        if (!Registries.CREATIVE_MODE_TAB.equals(event.getRegistryKey())) {
            return;
        }
        event.register(Registries.CREATIVE_MODE_TAB, FutureTinker.id(ID), () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup." + FutureTinker.MOD_ID + "." + ID))
                .icon(() -> new ItemStack(SpearContent.spear()))
                .displayItems((parameters, output) -> output.accept(SpearContent.spear()))
                .build());
    }
}
