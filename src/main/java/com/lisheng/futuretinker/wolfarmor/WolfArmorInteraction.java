package com.lisheng.futuretinker.wolfarmor;

import java.util.function.Consumer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

/**
 * 狼铠的**脱甲**路径：与原版一致，用**剪刀**取下。
 *
 * <p>穿甲由 {@link WolfArmorItem} 处理（照原版马铠）；空手时
 * {@code interactLivingEntity} 不会被调用（没有物品），且驯服的狼会先吃掉右键，
 * 所以脱甲由事件兜底。两条事件都要监听：真客户端点实体派发 {@code EntityInteractSpecific}。</p>
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

    private static void handle(Entity target, Player player, ItemStack held,
            Consumer<InteractionResult> setResult, Runnable cancel) {
        if (!(target instanceof Wolf wolf) || player.level().isClientSide()) {
            return;
        }
        // 1) 手上拿着狼铠：穿上（抢在 Wolf#mobInteract 之前，并取消事件，否则狼会坐下）
        if (WolfArmorContent.isWolfArmor(held)) {
            ItemStack current = wolf.getItemBySlot(EquipmentSlot.CHEST);
            if (!current.isEmpty()) {
                wolf.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
                WolfArmorItem.giveOrDrop(player, current);
            }
            ItemStack equipped = held.copy();
            equipped.setCount(1);
            wolf.setItemSlot(EquipmentSlot.CHEST, equipped);
            held.shrink(1);
            wolf.playSound(SoundEvents.ARMOR_EQUIP_LEATHER, 1.0F, 1.0F);
            setResult.accept(InteractionResult.SUCCESS);
            cancel.run();
            return;
        }

        // 2) 剪刀：取下（原版行为）
        if (held.is(Items.SHEARS)
                && WolfArmorContent.isWolfArmor(wolf.getItemBySlot(EquipmentSlot.CHEST))) {
            ItemStack removed = wolf.getItemBySlot(EquipmentSlot.CHEST);
            wolf.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            WolfArmorItem.giveOrDrop(player, removed);
            wolf.playSound(SoundEvents.ARMOR_EQUIP_LEATHER, 1.0F, 1.0F);
            setResult.accept(InteractionResult.SUCCESS);
            cancel.run();
        }
    }
}
