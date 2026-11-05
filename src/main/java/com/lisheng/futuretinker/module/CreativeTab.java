package com.lisheng.futuretinker.module;

import com.lisheng.futuretinker.FutureTinker;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.RegisterEvent;

/**
 * 唯一的创造模式标签页「未来匠魂」。
 *
 * <p>说明白「一个标签页 + 多个可独立开关的模块」怎么不打架：</p>
 * <ul>
 *   <li>标签页本身不属于任何模块，由 {@link ModuleRegistry} 在物品注册完之后统一注册；</li>
 *   <li>模块只有在**自己被启用**时（也就是它的 {@code registerRegistries} 被调用时）才把物品
 *       登记进来，所以关掉一个模块，标签页里就不会有它的东西；</li>
 *   <li>所有模块都关掉时没有任何物品，标签页也就不注册。</li>
 * </ul>
 */
public final class CreativeTab {
    public static final String ID = "main";

    private static final List<Supplier<? extends Item>> ITEMS = new ArrayList<>();
    private static boolean registered;

    private CreativeTab() {}

    /** 模块注册物品时调用（只在模块启用时才会被调用）。 */
    public static void add(Supplier<? extends Item> item) {
        ITEMS.add(item);
    }

    public static List<Supplier<? extends Item>> items() {
        return List.copyOf(ITEMS);
    }

    static void register(RegisterEvent event) {
        if (registered || ITEMS.isEmpty() || !Registries.CREATIVE_MODE_TAB.equals(event.getRegistryKey())) {
            return;
        }
        registered = true;
        event.register(Registries.CREATIVE_MODE_TAB, FutureTinker.id(ID), () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup." + FutureTinker.MOD_ID + "." + ID))
                .icon(() -> new ItemStack(ITEMS.get(0).get()))
                .displayItems((parameters, output) -> ITEMS.forEach(item -> output.accept(item.get())))
                .build());
    }
}
