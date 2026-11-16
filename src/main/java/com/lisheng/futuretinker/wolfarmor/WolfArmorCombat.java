package com.lisheng.futuretinker.wolfarmor;

import com.lisheng.futuretinker.config.FutureTinkerConfig;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import slimeknights.tconstruct.library.tools.helper.ToolDamageUtil;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * 狼铠的受击行为：**用自身耐久顶伤害**（与原版 1.20.5+ 的狼铠一致）。
 *
 * <p>原版行为：狼铠给狼 11 点护甲值，并消耗自身耐久抵消大多数非魔法伤害。所以这里在
 * {@link LivingDamageEvent}（护甲减免之后的最终伤害）里：</p>
 * <ol>
 *   <li>魔法伤害、绕过护甲的伤害不管（原版也不吃）；</li>
 *   <li>按 1:1 用狼铠耐久吃掉这段伤害（耐久不够就吃到碎，剩余伤害照常落到狼身上）；</li>
 *   <li>吃掉多少就把事件伤害减掉多少——全吃掉时事件伤害归零。</li>
 * </ol>
 *
 * <p>选 {@code LivingDamageEvent} 而不是 {@code LivingHurtEvent}：后者在护甲减免之前，
 * 拿到的不是「经过护甲削减的物理伤害」，按它扣耐久会扣多。</p>
 */
public final class WolfArmorCombat {
    private WolfArmorCombat() {}

    public static void onLivingDamage(LivingDamageEvent event) {
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
        DamageSource source = event.getSource();
        // 非魔法伤害才由铠甲承担（原版狼铠同样不挡魔法/绕过护甲的伤害）
        if (source.is(DamageTypeTags.BYPASSES_ARMOR) || source.is(DamageTypeTags.WITCH_RESISTANT_TO)) {
            return;
        }
        ToolStack tool = ToolStack.from(armor);
        if (tool.isBroken()) {
            return;
        }

        double absorption = FutureTinkerConfig.wolfArmorDamageAbsorption();
        int damage = (int) Math.ceil(event.getAmount() * absorption);
        if (damage <= 0) {
            return;
        }
        int remainingDurability = Math.max(0, armor.getMaxDamage() - armor.getDamageValue());
        int absorbed = Math.min(damage, remainingDurability);
        if (absorbed <= 0) {
            return;
        }
        ToolDamageUtil.damage(tool, absorbed, wolf, armor);
        event.setAmount(event.getAmount() - absorbed);
    }
}
