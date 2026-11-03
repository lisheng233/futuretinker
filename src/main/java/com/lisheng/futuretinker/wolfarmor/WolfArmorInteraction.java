package com.lisheng.futuretinker.wolfarmor;

import java.util.function.Consumer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
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
 * <p><b>必须同时监听两个事件</b>——实测踩到的 bug：真客户端点实体会发 {@code INTERACT_AT}
 * 数据包，Forge 在服务端把它派发成 {@link PlayerInteractEvent.EntityInteractSpecific}；
 * 只监听 {@link PlayerInteractEvent.EntityInteract}（对应 {@code INTERACT} 包）时，
 * 玩家右键狼毫无反应。两条路径走同一套逻辑。</p>
 *
 * <p>取消事件是必须的：驯服的狼默认右键是「坐下/起立」，不取消就会一边穿甲一边坐下。</p>
 */
public final class WolfArmorInteraction {
    private WolfArmorInteraction() {}

    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        handle(event.getTarget(), event.getEntity(), event.getItemStack(),
                event::setCancellationResult, () -> event.setCanceled(true));
    }

    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        handle(event.getTarget(), event.getEntity(), event.getItemStack(),
                event::setCancellationResult, () -> event.setCanceled(true));
    }

    /** 两条事件路径共用的逻辑。 */
    private static void handle(Entity target, Player player, ItemStack held,
            Consumer<InteractionResult> setResult, Runnable cancel) {
        if (!(target instanceof Wolf wolf) || player.level().isClientSide()) {
            return;
        }

        // 1) 手里拿着狼铠：装到狼身上（只认驯服的狼，野狼穿上会跟丢）
        if (WolfArmorContent.isWolfArmor(held) && wolf.isTame()) {
            equip(wolf, player, held);
            setResult.accept(InteractionResult.SUCCESS);
            cancel.run();
            return;
        }

        // 2) 潜行 + 空手：脱下来，还给玩家
        if (held.isEmpty() && player.isShiftKeyDown()
                && WolfArmorContent.isWolfArmor(wolf.getItemBySlot(EquipmentSlot.CHEST))) {
            ItemStack removed = wolf.getItemBySlot(EquipmentSlot.CHEST);
            wolf.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            giveOrDrop(player, removed);
            wolf.playSound(SoundEvents.ARMOR_EQUIP_LEATHER, 1.0F, 1.0F);
            setResult.accept(InteractionResult.SUCCESS);
            cancel.run();
        }
    }

    private static void equip(Wolf wolf, Player player, ItemStack held) {
        ItemStack current = wolf.getItemBySlot(EquipmentSlot.CHEST);
        if (!current.isEmpty()) {
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
