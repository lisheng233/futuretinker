package com.lisheng.futuretinker.spear;

import com.lisheng.futuretinker.FutureTinker;
import java.util.Objects;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.RegisterEvent;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;
import slimeknights.tconstruct.tools.stats.HeadMaterialStats;

/**
 * 长矛的物品清单：**长矛本体 + 一个自制矛头部件**。
 *
 * <p>部件组合是「矛头 + 坚韧绑定结 + 坚韧手柄」：</p>
 * <ul>
 *   <li>{@code futuretinker:spear_head}（新）——本模组自己的部件，用 {@link HeadMaterialStats}
 *       （矛头的耐久/攻击力就是长矛的伤害与「等级」来源）；</li>
 *   <li>{@code tconstruct:tough_binding} / {@code tconstruct:tough_handle}——匠魂现成的部件，
 *       绑定结是无统计的 {@code binding}，手柄给 {@code handle} 统计（疲劳窗口的来源）。</li>
 * </ul>
 *
 * <p>矛头是**普通部件物品**（{@link ToolPartItem}），配方走 {@code tconstruct:part_builder}，
 * 所以它能在部件台里用空白模板 + 材料做出来；图标是灰度遮罩 + 材料上色（见 {@link SpearClient}），
 * 不走匠魂的 material 贴图模型——那条路要求每个材料都有一张 {@code <贴图>_<材料>} 变体，
 * 插件贴上不去（狼铠那轮的紫黑就是这么来的）。</p>
 */
public final class SpearContent {
    public static final String ID = "spear";
    public static final String HEAD_ID = "spear_head";

    /** 工具定义 id = {@code futuretinker:spear}，数据在 {@code data/futuretinker/tinkering/tool_definitions/spear.json}。 */
    public static final ToolDefinition DEFINITION = ToolDefinition.create(FutureTinker.id(ID));

    private static Item spear;
    private static Item spearHead;

    private SpearContent() {}

    static void registerItems(RegisterEvent event) {
        if (!Registries.ITEM.equals(event.getRegistryKey())) {
            return;
        }
        event.register(Registries.ITEM, FutureTinker.id(ID),
                () -> spear = new SpearItem(new Item.Properties().stacksTo(1), DEFINITION));
        event.register(Registries.ITEM, FutureTinker.id(HEAD_ID),
                () -> spearHead = new ToolPartItem(new Item.Properties(), HeadMaterialStats.ID));
    }

    public static Item spear() {
        return Objects.requireNonNull(spear,
                "Future Tinker: 长矛物品还没注册（模块被关了，或在注册阶段之前访问了它）");
    }

    public static Item spearHead() {
        return Objects.requireNonNull(spearHead,
                "Future Tinker: 矛头部件还没注册（模块被关了，或在注册阶段之前访问了它）");
    }

    public static boolean isSpear(ItemStack stack) {
        return !stack.isEmpty() && stack.is(spear());
    }
}
