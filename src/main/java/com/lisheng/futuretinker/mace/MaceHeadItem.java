package com.lisheng.futuretinker.mace;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.part.IMaterialItem;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;
import slimeknights.tconstruct.tools.stats.HeadMaterialStats;

/**
 * 狼牙棒头部件：在匠魂普通的 head 统计之外，把本模组自定义的
 * {@link HardnessMaterialStats} / {@link ToughnessMaterialStats} 也写进 tooltip。
 */
public class MaceHeadItem extends ToolPartItem {
    public MaceHeadItem(Properties properties) {
        super(properties, HeadMaterialStats.ID);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
            TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        MaterialVariantId material = IMaterialItem.getMaterialFromStack(stack);
        if (material != null) {
            tooltip.add(Component.translatable("material.futuretinker.hardness",
                    String.format("%.2f", MaceCombat.hardnessOf(material))));
            tooltip.add(Component.translatable("material.futuretinker.toughness",
                    String.format("%.2f", MaceCombat.toughnessOf(material))));
        }
    }
}
