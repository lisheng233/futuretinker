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
 * 狼铠的**脱甲**路径。
 *
 * <p>穿甲由物品自己处理（{@link WolfArmorItem#interactLivingEntity}，照原版马铠的做法）。
 * 但「潜行 + 空手右键」时玩家手上没有物品，{@code interactLivingEntity} 根本不会被调用，
 * 所以脱甲只能由事件兜底。两条事件都要监听：真客户端点实体派发
 * {@code EntityInteractSpecific}，服务端交互派发 {@code EntityInteract}。</p>
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
        // 潜行 + 空手：脱下来，还给玩家
        if (held.isEmpty() && player.isShiftKeyDown()
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
