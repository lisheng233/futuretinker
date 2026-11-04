package com.lisheng.futuretinker.spear;

import com.lisheng.futuretinker.FutureTinker;
import java.util.Objects;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.RegisterEvent;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;

/**
 * 长矛的物品清单。
 *
 * <p>长矛是一件**匠魂工具**：部件、材料、加固与修复全部走匠魂既有流程；本模组只补
 * 「高版本原版长矛」那套战斗机制（见 {@link SpearCharge} 与 {@link SpearCombat}）。</p>
 *
 * <p>部件照抄匠魂自己的 **javelin（标枪）** 蓝本：{@code small_blade} + {@code tool_handle} +
 * {@code bow_limb} + {@code bow_grip}。理由不是偷懒：这套部件组合的模型/贴图是匠魂自己发过的，
 * 材料变体（{@code <贴图>_<材料>}）由匠魂生成，插件只要引用 {@code tconstruct:} 贴图就不会紫黑；
 * 自制部件那条路已经踩过坑（见 wolfarmor 的注释）。</p>
 */
public final class SpearContent {
    public static final String ID = "spear";

    /** 工具定义 id = {@code futuretinker:spear}，数据在 {@code data/futuretinker/tinkering/tool_definitions/spear.json}。 */
    public static final ToolDefinition DEFINITION = ToolDefinition.create(FutureTinker.id(ID));

    private static Item spear;

    private SpearContent() {}

    static void registerItems(RegisterEvent event) {
        if (!Registries.ITEM.equals(event.getRegistryKey())) {
            return;
        }
        event.register(Registries.ITEM, FutureTinker.id(ID),
                () -> spear = new SpearItem(new Item.Properties().stacksTo(1), DEFINITION));
    }

    public static Item spear() {
        return Objects.requireNonNull(spear,
                "Future Tinker: 长矛物品还没注册（模块被关了，或在注册阶段之前访问了它）");
    }

    public static boolean isSpear(ItemStack stack) {
        return !stack.isEmpty() && stack.is(spear());
    }
}
