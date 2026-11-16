package com.lisheng.futuretinker.spear;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 客户端看到的冲锋状态。
 *
 * <p>本地玩家由 {@link SpearClientInput} 自己计时（输入延迟最小）；其他玩家由服务端在
 * {@link SpearCharge#start} / {@link SpearCharge#stop} 时广播一个包，客户端只记下
 * 「开始时的 tickCount」，动画进度按实体自己的 tickCount 推。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class SpearClientState {
    private static final Map<Integer, Integer> START_TICKS = new HashMap<>();

    private SpearClientState() {}

    /** 服务端广播：某玩家开始/结束冲锋。 */
    public static void onChargeState(int entityId, boolean start) {
        if (!start) {
            START_TICKS.remove(entityId);
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(entityId);
        if (entity != null) {
            START_TICKS.put(entityId, entity.tickCount);
        }
    }

    public static boolean isCharging(Player player) {
        if (player == Minecraft.getInstance().player) {
            return SpearClientInput.isCharging();
        }
        return START_TICKS.containsKey(player.getId());
    }

    /** 已经冲锋了多少 tick。未在冲锋时返回 0。 */
    public static int chargeTicks(Player player) {
        if (player == Minecraft.getInstance().player) {
            return SpearClientInput.isCharging() ? (int) SpearClientInput.chargeTicks() : 0;
        }
        Integer start = START_TICKS.get(player.getId());
        return start == null ? 0 : Math.max(0, player.tickCount - start);
    }

    /** 带 partialTicks 的版本，给渲染用。 */
    public static float chargeTicks(Player player, float partialTicks) {
        return chargeTicks(player) + partialTicks;
    }

    public static void clear() {
        START_TICKS.clear();
    }
}
