package com.lisheng.futuretinker.spear;

import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;

/**
 * 原版长矛的**戳刺（jab）**规矩，外加三条原版细节：多目标、0.125 碰撞箱放大、不挖方块。
 *
 * <p>戳刺本体还是原版近战（伤害/附魔/属性都走原版），本类补的是原版长矛特有的部分：</p>
 * <ul>
 *   <li><b>不能打太近</b>：目标距离 &lt; {@link #MIN_JAB_RANGE}（原版 2 格）时戳不到。</li>
 *   <li><b>必须满蓄力</b>：原版长矛的攻击冷却不能被打断——不到 100% 就打不出去。</li>
 *   <li><b>不会暴击</b>：{@link CriticalHitEvent} 直接 DENY（暴击粒子也不会出）。</li>
 *   <li><b>没有冲刺击退</b>：结算前取消疾跑状态（原版命中本来也会取消疾跑）。</li>
 *   <li><b>一次戳多个目标 + 0.125 碰撞箱放大</b>：{@link #sweep} 用眼睛射线对「放大后的碰撞箱」
 *       做扫掠，主目标交给原版结算，其余目标补一发同样的伤害。</li>
 *   <li><b>不挖方块</b>：原版长矛左键方块也是戳刺而不是破坏方块，所以取消左键方块并走同一套扫描。</li>
 * </ul>
 *
 * <p>客户端在「左键空气」时原版根本不会把攻击发给服务端，所以加了一个 swing 包
 * （{@link SpearNetwork}）：没有它，碰撞箱放大只对「已经点中了谁」的情况有价值。</p>
 */
public final class SpearAttack {
    /** 原版长矛的戳刺最短距离。 */
    public static final double MIN_JAB_RANGE = 2.0D;
    /** 原版长矛攻击时给目标碰撞箱的放大值。 */
    public static final double HITBOX_MARGIN = 0.125D;

    private SpearAttack() {}

    public static void onAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (!SpearContent.isSpear(player.getMainHandItem()) || player.isSpectator()) {
            return;
        }
        if (player.distanceTo(event.getTarget()) < MIN_JAB_RANGE) {
            event.setCanceled(true);
            return;
        }
        if (player.getAttackStrengthScale(1.0F) < 0.999F) {
            event.setCanceled(true);
            return;
        }
        if (player.isSprinting()) {
            // 原版长矛不会触发「冲刺击退」：在结算前取消疾跑状态即可；
            // 原版普通攻击命中时本来也会 setSprinting(false)，所以玩家感觉不到区别。
            player.setSprinting(false);
        }
        // 主目标由原版结算，这里补其余目标
        sweep(player, event.getTarget());
    }

    public static void onCritical(CriticalHitEvent event) {
        if (SpearContent.isSpear(event.getEntity().getMainHandItem())) {
            // DENY = 这一下不是暴击（既不乘 1.5，也不出暴击粒子）
            event.setResult(Event.Result.DENY);
        }
    }

    /** 拿着长矛左键方块：不挖方块，改成戳刺（原版长矛的行为）。 */
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        if (!SpearContent.isSpear(player.getMainHandItem())) {
            return;
        }
        event.setUseBlock(Event.Result.DENY);
        event.setUseItem(Event.Result.DENY);
        if (!event.getLevel().isClientSide()) {
            sweep(player, null);
        }
    }

    /** 拿着长矛左键空气：客户端把这次挥击发给服务端，服务端才有机会用放大的碰撞箱判定。 */
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        if (event.getLevel().isClientSide()
                && SpearContent.isSpear(event.getEntity().getMainHandItem())) {
            SpearNetwork.sendSwing();
        }
    }

    public static void serverSwing(ServerPlayer player) {
        if (player != null && SpearContent.isSpear(player.getMainHandItem())) {
            sweep(player, null);
        }
    }

    /**
     * 戳刺命中扫描：眼睛射线 vs 放大 {@link #HITBOX_MARGIN} 后的目标碰撞箱，
     * 命中点距离必须在 [{@link #MIN_JAB_RANGE}, 触及] 之间。{@code excluded} 是由原版结算的主目标。
     */
    public static void sweep(Player player, Entity excluded) {
        if (player.getAttackStrengthScale(1.0F) < 0.999F) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        Vec3 eye = player.getEyePosition();
        double reach = player.getEntityReach();
        Vec3 end = eye.add(player.getLookAngle().scale(reach));

        List<LivingEntity> candidates = player.level().getEntitiesOfClass(
                LivingEntity.class,
                player.getBoundingBox().inflate(reach + HITBOX_MARGIN),
                entity -> entity != player && entity.isAlive() && entity != excluded
                        && entity != player.getVehicle());

        boolean hitAnything = false;
        for (LivingEntity target : candidates) {
            AABB box = target.getBoundingBox().inflate(HITBOX_MARGIN);
            var hit = box.clip(eye, end);
            if (hit.isEmpty()) {
                continue;
            }
            double distance = eye.distanceTo(hit.get());
            if (distance < MIN_JAB_RANGE || distance > reach) {
                continue;
            }
            float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE)
                    + EnchantmentHelper.getDamageBonus(stack, target.getMobType());
            if (damage <= 0.0F) {
                continue;
            }
            DamageSource source = player.damageSources().playerAttack(player);
            if (target.hurt(source, damage)) {
                Vec3 push = target.position().subtract(player.position());
                target.knockback(0.4F, push.x, push.z);
                hitAnything = true;
            }
        }
        if (hitAnything) {
            // 自己结算的一击也要走攻击冷却，否则多目标扫描可以无限连
            player.resetAttackStrengthTicker();
        }
    }
}
