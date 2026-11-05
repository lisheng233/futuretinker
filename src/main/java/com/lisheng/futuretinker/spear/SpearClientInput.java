package com.lisheng.futuretinker.spear;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * 客户端侧的冲锋输入：本地记开始时间，松手时告诉服务端。
 *
 * <p>为什么要自己记时间：冲锋不走原版「使用物品」，所以没有 {@code getTicksUsingItem()} 可用；
 * 而压低动画又需要知道「已经压低了多少 tick」。本地时间只驱动动画，真正的结算在服务端
 * （{@link SpearCharge}）。</p>
 *
 * <p>三条结束条件（和服务端对齐）：</p>
 * <ol>
 *   <li>压满一轮（{@link SpearCharge#timings} 的总和）——原版长矛压太久会自己收起；</li>
 *   <li>松开右键（连续 2 tick 都没按住才认，避免按键抖动把状态来回清掉——那就是「矛来回抖」）；</li>
 *   <li>手里换掉了矛。</li>
 * </ol>
 */
@OnlyIn(Dist.CLIENT)
public final class SpearClientInput {
    private static int startTick = -1;
    private static int releasedTicks;
    /** 时长走满结束之后，必须松手再按才能开始下一次（原版就是这样）。 */
    private static boolean requireRelease;

    private SpearClientInput() {}

    public static void startCharge() {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || startTick >= 0 || requireRelease
                || !SpearContent.isSpear(player.getMainHandItem())) {
            return;
        }
        startTick = player.tickCount;
        releasedTicks = 0;
        SpearNetwork.sendCharge(true);
    }

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (startTick < 0) {
            // 松手之后才允许下一次冲锋
            if (!minecraft.options.keyUse.isDown()) {
                requireRelease = false;
            }
            return;
        }
        Player player = minecraft.player;
        if (player == null) {
            stopCharge();
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!SpearContent.isSpear(stack)) {
            stopCharge();
            return;
        }
        if (player.tickCount - startTick >= SpearCharge.timings(ToolStack.from(stack)).total()) {
            // 压满一轮：原版长矛这时会自己收起，而且要松手才能再来一次
            requireRelease = true;
            stopCharge();
            return;
        }
        if (minecraft.options.keyUse.isDown()) {
            releasedTicks = 0;
        } else if (++releasedTicks >= 2) {
            stopCharge();
        }
    }

    private static void stopCharge() {
        startTick = -1;
        releasedTicks = 0;
        SpearNetwork.sendCharge(false);
    }

    public static boolean isCharging() {
        return startTick >= 0;
    }

    /** 压低进度 0~1（一遍冲锋的时长走满）。 */
    public static float chargeProgress(float partialTicks) {
        if (startTick < 0) {
            return 0.0F;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) {
            return 0.0F;
        }
        int total = SpearCharge.timings(ToolStack.from(player.getMainHandItem())).total();
        if (total <= 0) {
            return 0.0F;
        }
        float ticks = player.tickCount - startTick + partialTicks;
        return Math.max(0.0F, Math.min(1.0F, ticks / (float) total));
    }

    /** 已经压低了多少 tick；-1 = 没在冲锋。 */
    public static float chargeTicks() {
        if (startTick < 0) {
            return -1.0F;
        }
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null ? -1.0F : (float) (minecraft.player.tickCount - startTick);
    }
}
