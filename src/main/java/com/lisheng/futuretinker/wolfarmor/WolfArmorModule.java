package com.lisheng.futuretinker.wolfarmor;

import com.lisheng.futuretinker.FutureTinker;
import com.lisheng.futuretinker.module.FtModule;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.RegisterEvent;

/**
 * 匠魂化狼铠（模块一）。
 *
 * <p>M1 只做到「模块可独立启停」：启用时注册一个自己的创造模式标签页作为存在证据，
 * 部件（外层甲片 / 内衬 / 绑定）与装配流程属于 M2、M3。</p>
 *
 * <p>标签页不可省：它让「模块关掉 = 什么都没注册」这条契约在游戏内可被观测
 * （{@code ModuleGateTests} 直接查 {@code BuiltInRegistries.CREATIVE_MODE_TAB}）。</p>
 */
public final class WolfArmorModule implements FtModule {
    public static final String ID = "wolfarmor";

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
                .icon(() -> new ItemStack(Items.WOLF_SPAWN_EGG))
                .build());
    }
}
