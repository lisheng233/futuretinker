package com.lisheng.futuretinker.spear;

import com.lisheng.futuretinker.config.FutureTinkerConfig;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.materials.stats.IMaterialStats;
import slimeknights.tconstruct.library.materials.stats.MaterialStatsId;
import slimeknights.tconstruct.library.tools.helper.ToolDamageUtil;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.tools.stats.HandleMaterialStats;
import slimeknights.tconstruct.tools.stats.HeadMaterialStats;

/**
 * 原版长矛的**冲锋**机制，逐行对齐 1.21.11 的 {@code KineticWeapon}：
 *
 * <ul>
 *   <li>射线从「眼睛 + 视线 × 2.0」开始，到「眼睛 + 视线 × (4.5 / 创造 6.5 +
 *       一 tick 位移)」结束；命中箱先用精确盒，再用放大 0.125 的盒 + 到中心的视线检测
 *       （{@code ProjectileUtil.getManyEntityHitResult}）。</li>
 *   <li>方块从眼睛开始裁剪：墙比最小射程还近时整个 tick 没有命中；更远的墙把射线截断。</li>
 *   <li>速度按实体分别算（格/秒）：玩家用**位置差**（服务端 {@code deltaMovement} 不可靠），
 *       其它实体优先用 {@code deltaMovement}，等同原版 {@code lastKnownSpeed}。
 *       相对速度 = 攻击者沿视线速度 − 目标沿视线速度。</li>
 *   <li>伤害 = 攻击力属性 + ⌊相对速度 × 倍率⌋；伤害条件只看相对速度 ≥ 4.6 格/秒。</li>
 *   <li>挑落/击退各自独立：挑落要求前向速度 ≥ 材质阈值，击退要求前向速度 ≥ 5.1；
 *       伤害命中本身也会附带击退（原版 {@code stabAttack} 的行为）。</li>
 *   <li>接触冷却按原版顺序：目标一进入射线就先登记 10 tick 冷却，再判断速度/窗口。</li>
 *   <li>激活延迟、挑落/击退/伤害三个窗口对应原版组件的四个字段。</li>
 * </ul>
 *
 * <p><b>匠魂的部分：</b>窗口长度按矛头耐久分档，倍率额外乘矛头的冲刺系数
 * {@code futuretinker:charge}（没声明的材料用 1.0）。</p>
 */
public final class SpearCharge {
    // ===== 原版常数（Java 1.21.11） =====
    /** 相对速度达到这个值（格/秒）才结算冲锋伤害。 */
    public static final float DAMAGE_SPEED = 4.6F;
    /** 原版击退条件的前向速度门槛；即使不满足，伤害命中本身也会击退。 */
    public static final float KNOCKBACK_SPEED = 5.1F;
    /** 原版 AttackRange：2.0 ~ 4.5 格（创造 2.0 ~ 6.5），命中箱放大 0.125。 */
    public static final double MIN_REACH = 2.0D;
    public static final double MAX_REACH = 4.5D;
    public static final double MAX_CREATIVE_REACH = 6.5D;
    public static final double HITBOX_MARGIN = 0.125D;
    /** 对同一目标的接触冷却：0.5 秒。 */
    public static final int HIT_COOLDOWN = 10;

    /** 原版 KineticWeapon 的 forward_movement：第三人称压低时把矛往屏幕里送的幅度。 */
    public static final float FORWARD_MOVEMENT = 0.38F;

    /**
     * 原版 1.21.11 的四个数值（tick）：激活延迟，以及激活后挑落/击退/伤害三个条件的窗口时长。
     * 下标是 {@link #headTier} 的 0~4。原版按材质等级写死；匠魂里换成矛头耐久分档。
     */
    private static final int[] ACTIVATION = {15, 14, 12, 10, 8};
    private static final int[] DISMOUNT = {100, 90, 50, 60, 50};
    // 原版木/石/铁/钻石/下界合金的击退窗口（激活后计时）：10.0 / 9.0 / 6.75 / 6.5 / 5.5 秒
    private static final int[] KNOCKBACK = {200, 180, 135, 130, 110};
    private static final int[] DAMAGE = {300, 275, 225, 200, 175};
    /** 挑落骑乘所需的前向速度（格/秒），原版木/石/铁/钻石/下界合金：14/10/8/7.5/7。 */
    private static final float[] DISMOUNT_SPEED = {14.0F, 10.0F, 8.0F, 7.5F, 7.0F};
    /** 手柄材料的参照：匠魂手柄的 durability 是**比例修正**（-0.35 ~ +0.35），不是绝对值。 */
    private static final MaterialStatsId HANDLE_STATS = HandleMaterialStats.ID;
    private static final MaterialStatsId HEAD_STATS = HeadMaterialStats.ID;

    /** 每个玩家对每个目标的上一次接触的游戏时间。WeakHashMap：玩家下线即可回收。 */
    private static final Map<Player, Map<UUID, Long>> LAST_HITS = new WeakHashMap<>();

    /**
     * 正在冲锋的玩家 → 已经压低了几个 tick。
     *
     * <p>为什么不用原版的「使用物品」状态：{@code isUsingItem()} 会让
     * {@code LocalPlayer.aiStep} 把移动输入乘 0.2（跑不动），还会触发三叉戟的压低动画。
     * 冲锋要能边跑边压，所以这里自己记时间。</p>
     */
    private static final Map<Player, Integer> CHARGING = new WeakHashMap<>();

    /** 一次冲锋的时间表（单位 tick，20 tick = 1 秒）。 */
    public record Timings(int activation, int engaged, int tired, int disengaged) {
        public int total() {
            return activation + engaged + tired + disengaged;
        }
    }

    private SpearCharge() {}

    // ===== 数值：由材料算出来 =====

    /**
     * 冲锋窗口。矛头耐久分档决定基础窗口（等级越高启动越快、疲劳越早）；
     * 手柄材料的 durability 是比例修正（女王史莱姆 +0.35 窗口最长，琥珀金 -0.35 最短），
     * 给三个窗口各加 ±N tick；N 默认 10，由配置 {@code spear.handle_fatigue_bonus_ticks} 控制。
     */
    public static Timings timings(IToolStackView tool) {
        int tier = headTier(tool);
        int activation = ACTIVATION[tier];
        // 手柄的比例耐久修正给窗口 ±N tick：女王史莱姆更长，琥珀金更短。
        // 默认 N=10（*3*10 = 原来的 *30）；N 由配置 handle_fatigue_bonus_ticks 控制。
        int bonus = Math.round(handleDurabilityBonus(tool) * 3.0F
                * (float) FutureTinkerConfig.spearHandleFatigueBonusTicks());
        int dismount = Math.max(20, DISMOUNT[tier] + bonus);
        int knockback = Math.max(dismount + 20, KNOCKBACK[tier] + bonus);
        int damage = Math.max(knockback + 20, DAMAGE[tier] + bonus);
        // 窗口长度直接对应原版的三个条件上限；engagedEnd = 挑落结束，
        // tiredEnd = 击退结束，total = 伤害结束（都含激活延迟）。
        return new Timings(activation, dismount, knockback - dismount, damage - knockback);
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
        float configScale = (float) FutureTinkerConfig.spearChargeDamageScale();
        return Mth.clamp(base * chargeCoefficient(tool) * configScale, 0.0F, 4.0F);
    }

    /** 矛头材料的冲刺系数；没有这条统计的材料走默认值。 */
    public static float chargeCoefficient(IToolStackView tool) {
        return stats(tool, 0, ChargeMaterialStats.ID, ChargeMaterialStats.class)
                .map(ChargeMaterialStats::charge)
                .filter(value -> value > 0.0F)
                .orElse(ChargeMaterialStats.DEFAULT_CHARGE);
    }

    /** 把骑手挑下坐骑所需的速度（格/秒）。 */
    public static float dismountSpeed(IToolStackView tool) {
        return DISMOUNT_SPEED[headTier(tool)];
    }

    /** 原版冲锋伤害：攻击力 + ⌊相对速度 × 倍率⌋。 */
    public static float chargeDamage(float attackDamage, float closingSpeed, float multiplier) {
        return attackDamage + Mth.floor(closingSpeed * multiplier);
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
        // 第三人称动作：让其他客户端也知道这个玩家开始压低了
        SpearNetwork.sendChargeState(player, true);
        SpearModule.playLowerSound(player);
    }

    /** 松手 / 掉手 / 死亡：结束冲锋。 */
    public static void stop(Player player) {
        if (player != null && CHARGING.remove(player) != null) {
            SpearNetwork.sendChargeState(player, false);
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
            // 伤害窗口走完 = 这一轮冲锋结束，必须松手重来
            stop(player);
            return;
        }
        int elapsed = heldTicks - timings.activation();
        if (elapsed < 0) {
            return;
        }

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 attackerVelocity = velocity(player);
        float forwardSpeed = (float) look.dot(attackerVelocity);

        Vec3 start = eye.add(look.scale(MIN_REACH));
        // 原版把射程末端沿视线补上已知的一 tick 位移，高速冲锋时不会差最后一点距离
        double reach = player.isCreative() ? MAX_CREATIVE_REACH : MAX_REACH;
        Vec3 end = eye.add(look.scale(reach + Math.max(0.0D, forwardSpeed / 20.0D)));

        BlockHitResult blockHit = player.level().clip(new ClipContext(
                eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (blockHit.getType() != HitResult.Type.MISS) {
            Vec3 blockLocation = blockHit.getLocation();
            if (blockLocation.distanceToSqr(eye) < start.distanceToSqr(eye)) {
                // 方块比最小射程还近：原版直接返回方块命中，这一 tick 没有任何实体命中
                return;
            }
            end = blockLocation;
        }

        AABB area = AABB.ofSize(start, HITBOX_MARGIN, HITBOX_MARGIN, HITBOX_MARGIN)
                .expandTowards(end.subtract(start))
                .inflate(1.0D);
        List<LivingEntity> candidates = player.level().getEntitiesOfClass(
                LivingEntity.class, area, entity -> canHit(player, entity));
        if (candidates.isEmpty()) {
            return;
        }

        // 先快照所有候选的相对速度：前面的目标被击退后 deltaMovement 会立刻改变，
        // 不能让同 tick 的后续结算（尤其是骑手取根载具速度）读到击退后的值。
        Map<LivingEntity, Float> closingSpeeds = new IdentityHashMap<>(candidates.size());
        for (LivingEntity candidate : candidates) {
            Vec3 targetVelocity = velocity(candidate);
            closingSpeeds.put(candidate, (float) Math.max(0.0D,
                    look.dot(attackerVelocity) - look.dot(targetVelocity)));
        }

        float multiplier = multiplier(tool);
        float dismountSpeed = dismountSpeed(tool);
        long now = player.level().getGameTime();
        int damageWindow = timings.engaged() + timings.tired() + timings.disengaged();
        int knockbackWindow = timings.engaged() + timings.tired();

        for (LivingEntity target : candidates) {
            Vec3 hitPoint = clipTarget(player, target, start, end);
            if (hitPoint == null) {
                continue;
            }
            // 原版顺序：射线命中后先登记接触冷却，再判断速度/窗口；不满足条件也占掉这 10 tick
            if (wasRecentlyStabbed(player, target, now)) {
                continue;
            }
            rememberStabbed(player, target, now);

            float closingSpeed = closingSpeeds.get(target);

            // 三个条件各自独立：伤害只看相对速度，挑落/击退只看前向速度
            boolean canDismount = elapsed <= timings.engaged()
                    && forwardSpeed >= dismountSpeed;
            boolean canKnockback = elapsed <= knockbackWindow
                    && forwardSpeed >= KNOCKBACK_SPEED;
            boolean canDamage = elapsed <= damageWindow
                    && closingSpeed >= DAMAGE_SPEED;
            if (!canDismount && !canKnockback && !canDamage) {
                continue;
            }

            boolean damaged = false;
            if (canDamage) {
                float attackDamage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE)
                        + EnchantmentHelper.getDamageBonus(stack, target.getMobType());
                damaged = target.hurt(player.damageSources().playerAttack(player),
                        chargeDamage(attackDamage, closingSpeed, multiplier));
            }

            // 原版 stabAttack：伤害命中本身也会击退；击退窗口额外允许「无伤害击退」
            if (canKnockback || damaged) {
                float yaw = player.getYRot() * ((float) Math.PI / 180.0F);
                float strength = 0.4F + ((float) player.getAttributeValue(Attributes.ATTACK_KNOCKBACK)
                        + EnchantmentHelper.getKnockbackBonus(player)) * 0.5F;
                target.knockback(strength, Mth.sin(yaw), -Mth.cos(yaw));
                player.setDeltaMovement(player.getDeltaMovement().multiply(0.6D, 1.0D, 0.6D));
            }
            boolean dismounted = canDismount && target.isPassenger();
            if (dismounted) {
                target.stopRiding();
            }

            if (damaged || canKnockback || dismounted) {
                player.setLastHurtMob(target);
                player.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.8F, 1.0F);
            }
            // 原版对每个满足条件的 LivingEntity 目标调用 hurtEnemy（无论伤害是否真的落地）
            ToolDamageUtil.damage(tool, 1, player, stack);
        }
    }

    /**
     * 原版 {@code ProjectileUtil.getManyEntityHitResult} 的单目标版本：
     * 先精确命中箱；没中再用放大 0.125 的命中箱，并要求从放大命中点到命中箱中心的
     * 连线不被方块挡住。返回值只用于判断是否接触，距离由 start/end 控制。
     */
    private static Vec3 clipTarget(Player player, LivingEntity target, Vec3 start, Vec3 end) {
        AABB box = target.getBoundingBox();
        if (box.contains(start)) {
            return start;
        }
        Optional<Vec3> exact = box.clip(start, end);
        if (exact.isPresent()) {
            return exact.get();
        }
        Optional<Vec3> inflated = box.inflate(HITBOX_MARGIN).clip(start, end);
        if (inflated.isEmpty()) {
            return null;
        }
        Vec3 inflatedHit = inflated.get();
        Vec3 center = box.getCenter();
        BlockHitResult lineOfSight = player.level().clip(new ClipContext(
                inflatedHit, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 lineOfSightEnd = lineOfSight.getType() == HitResult.Type.MISS
                ? center : lineOfSight.getLocation();
        return box.clip(inflatedHit, lineOfSightEnd).orElse(null);
    }

    /** 原版 {@code PiercingWeapon.canHit} 的实体过滤。 */
    private static boolean canHit(Player player, LivingEntity target) {
        if (target == player || target.isSpectator() || target.isInvulnerable()
                || !target.isAlive() || !target.isPickable()) {
            return false;
        }
        if (target instanceof Player other && !player.canHarmPlayer(other)) {
            return false;
        }
        return !player.isAlliedTo(target);
    }

    // ===== 工具方法 =====

    /** 实体上一 tick 的位置；速度用位置差算——服务端玩家的 {@code deltaMovement} 并不可靠。 */
    private record Tracked(Vec3 position, int tick) {}

    private static final Map<Entity, Tracked> LAST_POSITIONS = new WeakHashMap<>();

    /**
     * 实体速度，格/秒，等价于 1.21.11 的 {@code Entity.lastKnownSpeed * 20}。
     *
     * <p>服务端玩家的 {@code deltaMovement} 只反映客户端上报的输入、不可靠，所以玩家走
     * 位置差；其它实体（尤其是被 AI 移动的怪）用 {@code deltaMovement} 更接近原版的
     * {@code lastKnownSpeed}，目标第一次进入射线时也能拿到速度。两者都拿不到时退回位置差。</p>
     */
    private static Vec3 velocity(Entity entity) {
        // 原版：非玩家乘客的速度取根载具（骑手的 deltaMovement 往往是 0）
        if (!(entity instanceof Player) && entity.isPassenger()) {
            entity = entity.getRootVehicle();
        }
        if (!(entity instanceof Player)) {
            Vec3 movement = entity.getDeltaMovement();
            if (movement.lengthSqr() > 1.0E-8D) {
                return movement.scale(20.0D);
            }
        }
        Vec3 position = entity.position();
        int now = entity.tickCount;
        Tracked previous = LAST_POSITIONS.put(entity, new Tracked(position, now));
        if (previous == null) {
            return Vec3.ZERO;
        }
        int ticks = Math.max(1, now - previous.tick());
        return position.subtract(previous.position()).scale(20.0D / ticks);
    }

    private static boolean wasRecentlyStabbed(Player player, LivingEntity target, long now) {
        Map<UUID, Long> hits = LAST_HITS.get(player);
        if (hits == null) {
            return false;
        }
        Long last = hits.get(target.getUUID());
        return last != null && now - last < HIT_COOLDOWN;
    }

    private static void rememberStabbed(Player player, LivingEntity target, long now) {
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
