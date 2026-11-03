package com.lisheng.futuretinker.wolfarmor;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

/**
 * 给狼穿/脱铠甲。
 *
 * <p>靠的是 {@code Mob} 自带的护甲槽（`EquipmentSlot.CHEST`）：放进去的物品会参与
 * `Attributes.ARMOR`/`ARMOR_TOUGHNESS`，并且跟着狼一起存进存档。
 * 这条前提已经用 GameTest 验证过（{@code WolfEquipmentTests}），不是推测。</p>
 *
 * <p>取消事件是必须的：驯服的狼默认右键是「坐下/起立」，不取消就会一边穿甲一边坐下。</p>
 */
public final class WolfArmorInteraction {
    private WolfArmorInteraction() {}

    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Wolf wolf)) {
            return;
        }
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        ItemStack held = event.getItemStack();

        if (WolfArmorContent.isWolfArmor(held)) {
            // 只给驯服的狼穿：野狼穿上会跟丢，属于白送。
            if (!wolf.isTame()) {
                return;
            }
            equip(wolf, player, held);
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }

        // 潜行 + 空手 = 脱下来
        if (held.isEmpty() && player.isShiftKeyDown() && WolfArmorContent.isWolfArmor(wolf.getItemBySlot(EquipmentSlot.CHEST))) {
            ItemStack removed = wolf.getItemBySlot(EquipmentSlot.CHEST);
            wolf.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            giveOrDrop(player, removed);
            wolf.playSound(SoundEvents.ARMOR_EQUIP_LEATHER, 1.0F, 1.0F);
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    private static void equip(Wolf wolf, Player player, ItemStack held) {
        ItemStack current = wolf.getItemBySlot(EquipmentSlot.CHEST);
        if (!current.isEmpty()) {
            // 已经有东西（本模组的狼铠或别的东西）→ 换下来还给玩家
            wolf.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            giveOrDrop(player, current);
        }
        ItemStack equipped = held.copy();
        equipped.setCount(1);
        wolf.setItemSlot(EquipmentSlot.CHEST, equipped);
        held.shrink(1);
        wolf.playSound(SoundEvents.ARMOR_EQUIP_LEATHER, 1.0F, 1.0F);
    }

    private static void giveOrDrop(Player player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (!player.getInventory().add(stack.copy())) {
            player.drop(stack.copy(), false);
        }
    }
}
