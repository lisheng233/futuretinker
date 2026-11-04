package com.lisheng.futuretinker.spear;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.eventbus.api.Event;

/**
 * 原版长矛的**戳刺（jab）**规矩。
 *
 * <p>戳刺本体就是原版近战（伤害/附魔/属性都走原版），这里只补三条原版长矛特有的限制：</p>
 * <ul>
 *   <li><b>不能打太近</b>：目标距离 &lt; {@link #MIN_JAB_RANGE}（原版 2 格）时戳不到。</li>
 *   <li><b>必须满蓄力</b>：原版长矛的攻击冷却不能被打断——不到 100% 就打不出去
 *       （普通武器是伤害按蓄力比例打折，不是禁止攻击）。</li>
 *   <li><b>不会暴击、也没有冲刺击退</b>：暴击用 Forge 的 {@link CriticalHitEvent} 直接 DENY；
 *       冲刺击退通过在结算前取消疾跑状态来规避（原版命中本身也会取消疾跑，所以没有行为损失）。</li>
 * </ul>
 *
 * <p>没做的两条（写在这里别装作做了）：原版「一次戳多个目标」和 0.125 的目标碰撞箱放大。</p>
 */
public final class SpearCombat {
    /** 原版长矛的戳刺最短距离。 */
    public static final double MIN_JAB_RANGE = 2.0D;

    private SpearCombat() {}

    public static void onAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        ItemStack stack = player.getMainHandItem();
        if (!SpearContent.isSpear(stack) || player.isSpectator()) {
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
    }

    public static void onCritical(CriticalHitEvent event) {
        Player player = event.getEntity();
        if (SpearContent.isSpear(player.getMainHandItem())) {
            // DENY = 这一下不是暴击（既不乘 1.5，也不出暴击粒子）
            event.setResult(Event.Result.DENY);
        }
    }
}
