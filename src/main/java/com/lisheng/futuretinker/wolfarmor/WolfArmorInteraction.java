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
 * 狼铠的穿/脱交互（事件路径）。
 *
 * <p>为什么必须走事件而不是只靠物品的 {@code interactLivingEntity}：驯服的狼在
 * {@code Wolf#mobInteract} 里会**自己吃掉右键**（坐下/起立并返回 SUCCESS），
 * 而 Forge 的物品交互钩子只在实体交互返回 PASS 之后才会被调用——所以对驯服的狼，
 * 物品钩子永远收不到这次点击（这是实测现象「右键没反应，狼只是坐下了」的解释）。
 * 事件在 {@code mobInteract} 之前派发，取消它就能抢占。</p>
 *
 * <p>两条事件都要监听：真客户端点实体派发 {@code EntityInteractSpecific}，
 * 服务端交互派发 {@code EntityInteract}。</p>
 *
 * <p>每一条都打日志（{@code latest.log} 里搜 {@code [wolfarmor]}）：玩家实测时
 * 日志会直接告诉我们是哪条路径跑到了、狼是否驯服、手上拿的是什么。</p>
 */
public final class WolfArmorInteraction {
    private WolfArmorInteraction() {}

    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        handle("EntityInteract", event.getTarget(), event.getEntity(), event.getItemStack(),
                event::setCancellationResult, () -> event.setCanceled(true));
    }

    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        handle("EntityInteractSpecific", event.getTarget(), event.getEntity(), event.getItemStack(),
                event::setCancellationResult, () -> event.setCanceled(true));
    }

    private static void handle(String path, Entity target, Player player, ItemStack held,
            Consumer<InteractionResult> setResult, Runnable cancel) {
        if (!(target instanceof Wolf wolf)) {
            return;
        }
        if (player.level().isClientSide()) {
            return;
        }
        com.lisheng.futuretinker.FutureTinker.LOGGER.info(
                "[wolfarmor] 交互 path={} wolfTamed={} held={} 狼当前铠甲={}",
                path, wolf.isTame(), held.getItem(), wolf.getItemBySlot(EquipmentSlot.CHEST));

        // 1) 手上拿着狼铠：穿上（原版马铠也不要求先驯服，这里不设门槛）
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

        // 2) 潜行 + 空手：脱下
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
