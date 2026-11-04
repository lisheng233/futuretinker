package com.lisheng.futuretinker.wolfarmor;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import slimeknights.tconstruct.library.tools.helper.ToolDamageUtil;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * 狼铠的受击行为：**挨打要掉匠魂耐久**，不是只当原版护甲点数用。
 *
 * <p>用户实测指出：狼穿着狼铠被打，狼铠耐久一点不掉，只有原版护甲减免。
 * 匠魂自己的护甲是「两点都算」：护甲值参与原版减免，同时每次受击扣工具耐久
 * （{@link ToolDamageUtil}）。玩家身上的护甲由匠魂自己的事件处理，狼不在那条路径上，
 * 所以这里替狼补上。</p>
 *
 * <p>耐久消耗按伤害的 1/4（至少 1 点），与匠魂护甲的量级一致；护甲的
 * {@code unbreakable}/{@code reinforced} 等强化由 {@link ToolDamageUtil} 自己处理。</p>
 */
public final class WolfArmorCombat {
    private WolfArmorCombat() {}

    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Wolf wolf)) {
            return;
        }
        if (wolf.level().isClientSide()) {
            return;
        }
        ItemStack armor = wolf.getItemBySlot(EquipmentSlot.CHEST);
        if (!WolfArmorContent.isWolfArmor(armor)) {
            return;
        }
        ToolStack tool = ToolStack.from(armor);
        if (tool.isBroken()) {
            return;
        }
        int durabilityDamage = Math.max(1, (int) Math.ceil(event.getAmount() / 4.0F));
        ToolDamageUtil.damage(tool, durabilityDamage, wolf, armor);
    }
}
