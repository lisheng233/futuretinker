package com.lisheng.futuretinker.wolfarmor;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDropsEvent;

/**
 * 狼死亡时把狼铠吐出来。
 *
 * <p>实测：默认情况下狼死了铠甲**直接消失**（`Mob.armorDropChances` 只有 8.5% 的概率，
 * 而且匠魂的 `ModifierEvents.onLivingDrops` 也没有把它吐出来）。辛苦造出来的护甲，
 * 死一次就没，这是设计缺陷，所以这里显式掉落。</p>
 *
 * <p>为什么要查「是不是已经有一份」：原版那条 8.5% 的概率路径在我们之后仍可能命中，
 * 直接 add 会变成「有时候掉两份」。这里只补缺失的那一份，结果恒为 1 份——
 * {@code WolfArmorDropTests} 断言的就是这个 1。</p>
 */
public final class WolfArmorDrops {
    private WolfArmorDrops() {}

    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Wolf wolf)) {
            return;
        }
        ItemStack armor = wolf.getItemBySlot(EquipmentSlot.CHEST);
        if (!WolfArmorContent.isWolfArmor(armor)) {
            return;
        }
        boolean alreadyDropped = event.getDrops().stream()
                .anyMatch(drop -> WolfArmorContent.isWolfArmor(drop.getItem()));
        if (alreadyDropped) {
            return;
        }
        event.getDrops().add(new ItemEntity(wolf.level(), wolf.getX(), wolf.getY() + 0.5D, wolf.getZ(),
                armor.copy()));
    }
}
