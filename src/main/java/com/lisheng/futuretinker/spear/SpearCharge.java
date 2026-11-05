package com.lisheng.futuretinker.spear;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.materials.stats.IMaterialStats;
import slimeknights.tconstruct.library.materials.stats.MaterialStatsId;
import slimeknights.tconstruct.library.tools.helper.ToolDamageUtil;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.tools.stats.HandleMaterialStats;
import slimeknights.tconstruct.tools.stats.HeadMaterialStats;

/**
 * 原版长矛的**冲锋**机制（1.21.11 的 Java 版行为，数值来自 wiki 的长矛页面与更新日志）。
 *
 * <p>原版规则，逐条对照（括号里是本类的常量/方法）：</p>
 * <ul>
 *   <li>按住右键把矛压低；松开取消。压低后不是立刻能打，要先过**激活延迟**（{@link Timings#activation()}）。</li>
 *   <li>冲锋伤害 = <b>1 + 伤害倍率 × 双方相对速度（格/秒）</b>。相对速度低于
 *       {@link #DAMAGE_SPEED}（4.6 格/秒）不结算（{@link #canDamage(float)}）。那 +1 是 25w43a
 *       把空手拳伤并进冲锋伤害的结果。</li>
 *   <li>攻击者自己的速度 ≥ {@link #KNOCKBACK_SPEED}（5.1 格/秒）才附带击退（{@link #canKnockback(float)}）。</li>
 *   <li>三个阶段：<b>engaged</b>（伤害 + 击退 + 挑落骑乘）→ <b>tired</b>（伤害 + 击退）→
 *       <b>disengaged</b>（只有伤害）。越高级的矛阶段推进越快、也越早失去挑落能力——
 *       这就是「疲劳」（{@link Stage}、{@link #stageAt}）。</li>
 *   <li>挑落骑乘需要攻击者达到该矛的挑落速度阈值（{@link #dismountSpeed}，原版 9~14 格/秒）；
 *       只有 engaged 阶段才会挑落。</li>
 *   <li>Against one target 的重复连接有 0.5 秒（10 tick）冷却（{@link #HIT_COOLDOWN}）。</li>
 * </ul>
 *
 * <p><b>匠魂的部分：</b>原版的数值是按「材质等级」写的，这里改由**材料**决定——
 * 矛头材料给出倍率、挑落阈值与激活延迟，手柄材料给出疲劳窗口的长度（耐久越高，保持 engaged 越久）。</p>
 */
public final class SpearCharge {
    // ===== 原版常数（Java 1.21.11） =====
    /** 相对速度达到这个值（格/秒）才结算冲锋伤害。 */
    public static final float DAMAGE_SPEED = 4.6F;
    /** 攻击者自身速度达到这个值才附带击退。 */
    public static final float KNOCKBACK_SPEED = 5.1F;
    /** 冲锋的最小命中距离（原版对非玩家是 1 格）。 */
    public static final double MIN_REACH = 1.0D;
    /** 冲锋的最大命中距离（与戳刺一致，4.5 格）。 */
    public static final double MAX_REACH = 4.5D;
    /** 对同一目标的重复连接冷却：0.5 秒。 */
    public static final int HIT_COOLDOWN = 10;

    /** 前向锥体的余弦阈值（约 ±46°）。 */
    private static final double CONE_COS = 0.7D;
    /** 手柄材料的参照：匠魂手柄的 durability 是**比例修正**（-0.35 ~ +0.35），不是绝对值。 */
    private static final MaterialStatsId HANDLE_STATS = HandleMaterialStats.ID;
    private static final MaterialStatsId HEAD_STATS = HeadMaterialStats.ID;

    /** 每个玩家对每个目标的上一次命中的游戏刻。WeakHashMap：玩家下线即可回收。 */
    private static final Map<Player, Map<UUID, Integer>> LAST_HITS = new WeakHashMap<>();

    /**
     * 正在冲锋的玩家 → 已经压低了几个 tick。
     *
     * <p>为什么不用原版的「使用物品」状态：{@code isUsingItem()} 会让
     * {@code LocalPlayer.aiStep} 把移动输入乘 0.2（跑不动），还会触发三叉戟的压低动画。
     * 冲锋要能边跑边压，所以这里自己记时间。</p>
     */
    private static final Map<Player, Integer> CHARGING = new WeakHashMap<>();

    /** 冲锋的三个阶段，外加「还没激活」。 */
    public enum Stage {
        /** 没压低、或压低没到激活延迟。 */
        IDLE,
        /** 伤害 + 击退 + 挑落。 */
        ENGAGED,
        /** 伤害 + 击退。 */
        TIRED,
        /** 只有伤害。 */
        DISENGAGED
    }

    /** 一次冲锋的时间表（单位 tick，20 tick = 1 秒）。 */
    public record Timings(int activation, int engaged, int tired, int disengaged) {
        public int total() {
            return activation + engaged + tired + disengaged;
        }
    }

    private SpearCharge() {}

    // ===== 数值：由材料算出来 =====

    /**
     * 疲劳窗口。手柄材料决定倍率（匠魂手柄的 durability 是比例修正：女王史莱姆 +0.35 窗口最长，
     * 琥珀金 -0.35 窗口最短）。激活延迟由矛头等级决定（原版也是等级越高启动越快）。
     */
    public static Timings timings(IToolStackView tool) {
        int tier = headTier(tool);
        // 阶段长短由**攻击速度**决定：越快越早结束（原版矛的节奏也是跟攻速走的）
        float factor = Mth.clamp(attackSpeed(tool), 0.5F, 2.0F);
        return new Timings(
                Math.max(1, Math.round((8.0F - tier) / factor)),
                Math.round(50.0F / factor),
                Math.round(70.0F / factor),
                Math.round(90.0F / factor));
    }

    /**
     * 伤害倍率 = 基准值（矛头等级 + 攻击力）× 矛头的**冲刺系数**。
     *
     * <p>基准值对原版五种材质正好是 0.7（木）~1.2（下界合金）；冲刺系数来自材料数据
     * {@code futuretinker:charge}，其它模组添加的材料没声明这条统计时用默认值 1.0，
     * 于是「新材料也能用、只是倍率是基准」。</p>
     */
    public static float multiplier(IToolStackView tool) {
        float base = Mth.clamp(0.65F + 0.1F * headTier(tool) + 0.03F * headAttack(tool), 0.7F, 1.2F);
        return Mth.clamp(base * chargeCoefficient(tool), 0.2F, 4.0F);
    }

    /** 矛头材料的冲刺系数；没有这条统计的材料走默认值。 */
    public static float chargeCoefficient(IToolStackView tool) {
        return stats(tool, 0, ChargeMaterialStats.ID, ChargeMaterialStats.class)
                .map(ChargeMaterialStats::charge)
                .filter(value -> value > 0.0F)
                .orElse(ChargeMaterialStats.DEFAULT_CHARGE);
    }

    /** 把骑手挑下坐骑所需的速度：原版 9（下界合金）~14（木）。 */
    public static float dismountSpeed(IToolStackView tool) {
        return Mth.clamp(14.0F - headTier(tool), 9.0F, 14.0F);
    }

    public static boolean canDamage(float relativeSpeed) {
        return relativeSpeed >= DAMAGE_SPEED;
    }

    public static boolean canKnockback(float attackerSpeed) {
        return attackerSpeed >= KNOCKBACK_SPEED;
    }

    /**
     * 冲锋伤害 = 1（空手拳伤）+ 倍率 × 水平相对速度 + 0.25 × 矛头攻击力。
     *
     * <p>最后那项是「攻击力作为次要变量」：原版只看速度，但整件工具的攻击力完全不影响冲锋
     * 说不过去，所以给一个很小的权重（铁矛头约 +0.5~1 点）。</p>
     */
    public static float damage(float multiplier, float headAttack, float relativeSpeed) {
        return 1.0F + 0.25F * headAttack + multiplier * relativeSpeed;
    }

    /** 压在阶段表上：没激活 → engaged → tired → disengaged → 结束。 */
    public static Stage stageAt(Timings timings, int heldTicks) {
        if (heldTicks < timings.activation()) {
            return Stage.IDLE;
        }
        int engagedEnd = timings.activation() + timings.engaged();
        int tiredEnd = engagedEnd + timings.tired();
        int disengagedEnd = tiredEnd + timings.disengaged();
        if (heldTicks < engagedEnd) {
            return Stage.ENGAGED;
        }
        if (heldTicks < tiredEnd) {
            return Stage.TIRED;
        }
        if (heldTicks < disengagedEnd) {
            return Stage.DISENGAGED;
        }
        return Stage.IDLE;
    }

    // ===== 冲锋的开始 / 结束 / 每 tick 推进（只在服务端调用） =====

    /** 右键：开始压低（已经在冲锋就忽略）。 */
    public static void start(Player player) {
        if (player == null || player.isSpectator()) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!SpearContent.isSpear(stack) || ToolStack.from(stack).isBroken()) {
            return;
        }
        if (CHARGING.containsKey(player)) {
            return;
        }
        CHARGING.put(player, 0);
        SpearModule.playLowerSound(player);
    }

    /** 松手 / 掉手 / 死亡：结束冲锋。 */
    public static void stop(Player player) {
        if (player != null) {
            CHARGING.remove(player);
        }
    }

    public static boolean isCharging(Player player) {
        return player != null && CHARGING.containsKey(player);
    }

    /** 已经压低了多少 tick（不在冲锋时返回 0）。 */
    public static int heldTicks(Player player) {
        Integer ticks = CHARGING.get(player);
        return ticks == null ? 0 : ticks;
    }

    /** 服务端每 tick 推进所有正在冲锋的玩家。 */
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || CHARGING.isEmpty()) {
            return;
        }
        for (Player player : List.copyOf(CHARGING.keySet())) {
            ItemStack stack = player.getMainHandItem();
            if (!player.isAlive() || !SpearContent.isSpear(stack)) {
                stop(player);
                continue;
            }
            int ticks = heldTicks(player) + 1;
            CHARGING.put(player, ticks);
            tick(player, stack, ticks);
        }
    }

    public static void tick(Player player, ItemStack stack, int heldTicks) {
        ToolStack tool = ToolStack.from(stack);
        if (tool.isBroken()) {
            stop(player);
            return;
        }
        Timings timings = timings(tool);
        if (heldTicks >= timings.total()) {
            // 三个阶段走完 = 这一轮冲锋结束，必须松手重来
            stop(player);
            return;
        }
        Stage stage = stageAt(timings, heldTicks);
        if (stage == Stage.IDLE) {
            return;
        }

        Vec3 eye = player.getEyePosition();
        Vec3 look = horizontal(player.getLookAngle());
        AABB area = player.getBoundingBox().inflate(MAX_REACH, 4.0D, MAX_REACH);
        List<LivingEntity> candidates = player.level().getEntitiesOfClass(
                LivingEntity.class, area, entity -> entity != player && entity.isAlive());
        if (candidates.isEmpty()) {
            return;
        }

        float attackerSpeed = horizontalSpeed(player);
        float multiplier = multiplier(tool);
        float dismount = dismountSpeed(tool);
        int now = (int) player.level().getGameTime();

        for (LivingEntity target : candidates) {
            // 水平判定：不要求 y 坐标一致（飞在高处也能捅到），只看水平距离与朝向锥
            Vec3 offset = horizontal(target.getBoundingBox().getCenter().subtract(eye));
            double distance = offset.length();
            if (distance < MIN_REACH || distance > MAX_REACH) {
                continue;
            }
            if (offset.lengthSqr() < 1.0E-4D) {
                continue;
            }
            if (look.dot(offset.normalize()) < CONE_COS) {
                continue;
            }
            if (Math.abs(target.getY() - player.getY()) > 3.0D) {
                continue;
            }
            if (isOnCooldown(player, target, now)) {
                continue;
            }
            float relativeSpeed = relativeSpeed(player, target);
            if (!canDamage(relativeSpeed)) {
                continue;
            }
            if (!target.hurt(player.damageSources().playerAttack(player),
                    damage(multiplier, headAttack(tool), relativeSpeed))) {
                continue;
            }
            rememberHit(player, target, now);

            if (stage != Stage.DISENGAGED && canKnockback(attackerSpeed)) {
                target.knockback(0.5F, look.x, look.z);
            }
            if (stage == Stage.ENGAGED && attackerSpeed >= dismount && target.isPassenger()) {
                target.stopRiding();
            }
            player.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.8F, 1.0F);
            ToolDamageUtil.damage(tool, 1, player, stack);
        }
    }

    // ===== 工具方法 =====

    private static Vec3 horizontal(Vec3 vector) {
        return new Vec3(vector.x, 0.0D, vector.z);
    }

    /** 水平速度，格/秒（{@code getDeltaMovement()} 是格/tick）。原版算冲锋速度时不看 y 分量。 */
    private static float horizontalSpeed(LivingEntity entity) {
        Vec3 movement = entity.getDeltaMovement();
        return (float) Math.sqrt(movement.x * movement.x + movement.z * movement.z) * 20.0F;
    }

    /** 双方的水平相对速度，格/秒。 */
    private static float relativeSpeed(LivingEntity attacker, LivingEntity target) {
        Vec3 delta = attacker.getDeltaMovement().subtract(target.getDeltaMovement());
        return (float) Math.sqrt(delta.x * delta.x + delta.z * delta.z) * 20.0F;
    }

    /** 工具的攻击速度（阶段时长由它决定）。 */
    private static float attackSpeed(IToolStackView tool) {
        Object value = tool.getStats().get(ToolStats.ATTACK_SPEED);
        return value instanceof Number number ? number.floatValue() : 1.0F;
    }

    private static boolean isOnCooldown(Player player, LivingEntity target, int now) {
        Map<UUID, Integer> hits = LAST_HITS.get(player);
        if (hits == null) {
            return false;
        }
        Integer last = hits.get(target.getUUID());
        return last != null && now - last < HIT_COOLDOWN;
    }

    private static void rememberHit(Player player, LivingEntity target, int now) {
        LAST_HITS.computeIfAbsent(player, key -> new WeakHashMap<>()).put(target.getUUID(), now);
    }

    // ===== 材料读取 =====

    /**
     * 「矛头等级」，0~4：原版的木/石/铁/钻石/下界合金正好是 0~4。
     *
     * <p>不从 {@code Tier#getLevel()} 取：Forge 已经把它标成过时（等级排序要用
     * {@code TierSortingRegistry}）。改用矛头耐久做分档，对原版五种材质能还原出同样的级别，
     * 对匠魂材料也能平滑扩展。</p>
     */
    private static int headTier(IToolStackView tool) {
        int durability = stats(tool, 0, HEAD_STATS, HeadMaterialStats.class)
                .map(HeadMaterialStats::durability)
                .orElse(250);
        if (durability < 100) {
            return 0;
        }
        if (durability < 200) {
            return 1;
        }
        if (durability < 600) {
            return 2;
        }
        if (durability < 1600) {
            return 3;
        }
        return 4;
    }

    private static float headAttack(IToolStackView tool) {
        return stats(tool, 0, HEAD_STATS, HeadMaterialStats.class)
                .map(HeadMaterialStats::attack)
                .orElse(2.0F);
    }

    private static float handleDurabilityBonus(IToolStackView tool) {
        return stats(tool, 1, HANDLE_STATS, HandleMaterialStats.class)
                .map(HandleMaterialStats::durability)
                .orElse(0.0F);
    }

    /**
     * 材料表第 {@code index} 项的材料统计。长矛的部件顺序是
     * {@code small_blade(0) / tool_handle(1) / bow_limb(2) / bow_grip(3)}。
     */
    private static <T extends IMaterialStats> Optional<T> stats(
            IToolStackView tool, int index, MaterialStatsId statType, Class<T> type) {
        MaterialNBT materials = tool.getMaterials();
        if (materials.size() <= index) {
            return Optional.empty();
        }
        MaterialVariant variant = materials.get(index);
        Optional<IMaterialStats> stats =
                MaterialRegistry.getInstance().getMaterialStats(variant.getVariant().getId(), statType);
        return stats.filter(type::isInstance).map(type::cast);
    }
}
