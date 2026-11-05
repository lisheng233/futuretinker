package com.lisheng.futuretinker.spear;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;

/**
 * 客户端侧的冲锋输入：本地记开始时间，松手时告诉服务端。
 *
 * <p>为什么要自己记时间：冲锋不走原版「使用物品」，所以没有 {@code getTicksUsingItem()} 可用；
 * 而压低动画（物品模型的 {@code display} 旋转）又需要知道「已经压低了多少 tick」。
 * 本地时间只驱动动画，真正的结算在服务端（{@link SpearCharge}）。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class SpearClientInput {
    private static int startTick = -1;

    private SpearClientInput() {}

    public static void startCharge() {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || startTick >= 0 || !SpearContent.isSpear(player.getMainHandItem())) {
            return;
        }
        startTick = player.tickCount;
        SpearNetwork.sendCharge(true);
    }

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || startTick < 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        boolean stillHolding = player != null && SpearContent.isSpear(player.getMainHandItem());
        if (!stillHolding || !minecraft.options.keyUse.isDown()) {
            startTick = -1;
            SpearNetwork.sendCharge(false);
        }
    }

    /**
     * 压低了多少 tick；-1 = 没在冲锋。
     *
     * <p>直接喂给物品模型的 predicate（{@code futuretinker:spear_charge}）：
     * 模型 override 用它切成「还没压 / 压低 1 / 2 / 3」几档，档位之间换的是
     * {@code display} 的旋转与位移，于是就有了旋转+下压的过程。</p>
     */
    public static float chargeTicks() {
        Minecraft minecraft = Minecraft.getInstance();
        if (startTick < 0 || minecraft.player == null) {
            return -1.0F;
        }
        return (float) (minecraft.player.tickCount - startTick);
    }
}
