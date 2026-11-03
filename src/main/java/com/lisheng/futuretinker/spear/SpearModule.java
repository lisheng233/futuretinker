package com.lisheng.futuretinker.spear;

import com.lisheng.futuretinker.FutureTinker;
import com.lisheng.futuretinker.module.FtModule;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.RegisterEvent;

/**
 * 匠魂化长矛（模块二）。
 *
 * <p>M1 同样只到「模块可独立启停」。真正的差异化机制在 M5：
 * 相对速度伤害、击落骑乘、持矛疲劳三件套，材质选择改变武器定位。
 * 注册名必须带 {@code futuretinker:} 前缀，与 Tinkers' Tools / Dreamtinker 共存。</p>
 */
public final class SpearModule implements FtModule {
    public static final String ID = "spear";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void registerRegistries(RegisterEvent event) {
        if (!Registries.CREATIVE_MODE_TAB.equals(event.getRegistryKey())) {
            return;
        }
        event.register(Registries.CREATIVE_MODE_TAB, FutureTinker.id(ID), () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup." + FutureTinker.MOD_ID + "." + ID))
                .icon(() -> new ItemStack(Items.TRIDENT))
                .build());
    }
}
