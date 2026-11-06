package com.lisheng.futuretinker.mace;

import java.util.Optional;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.materials.stats.IMaterialStats;
import slimeknights.tconstruct.library.materials.stats.MaterialStatsId;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.tools.stats.HandleMaterialStats;

/**
 * 狼牙棒的战斗逻辑。
 *
 * <p>和长矛相反：长矛读**自己的**速度（奖励你冲得比目标快），狼牙棒读**目标的**速度
 * （惩罚目标在你面前乱跑）——命中时目标在动，就挂一段流血。</p>
 *
 * <p>棒头形态由两条材料统计决定（默认值 1.0，其它模组的材料没数据也能用）：
 * 硬度 {@link HardnessMaterialStats}、韧性 {@link ToughnessMaterialStats}。</p>
 * <ul>
 *   <li>HARD（硬度高）：钉刺穿甲（额外魔法伤害）、攻速惩罚、横扫角度窄但单目标伤害高；</li>
 *   <li>TOUGH（韧性高）：命中概率挂减速，横扫角度中等；</li>
 *   <li>LIGHT（两样都低）：攻速大幅提升、横扫角度最宽但每目标伤害衰减；</li>
 *   <li>NEUTRAL：默认形态。</li>
 * </ul>
 */
public final class MaceCombat {
    /** 钩扯的触发速度（格/秒）：目标在动才算。 */
    public static final float HOOK_MIN_SPEED = 2.0F;
    public static final float HOOK_STRONG_SPEED = 6.0F;
    public static final double SWEEP_MARGIN = 0.125D;

    public enum Form {
        HARD, TOUGH, LIGHT, NEUTRAL
    }

    private MaceCombat() {}

    public static Form form(IToolStackView tool) {
        float hardness = hardness(tool);
        float toughness = toughness(tool);
        if (hardness >= toughness * 1.3F) {
            return Form.HARD;
        }
        if (toughness >= hardness * 1.3F) {
            return Form.TOUGH;
        }
        if (hardness < 1.0F && toughness < 1.0F) {
            return Form.LIGHT;
        }
        return Form.NEUTRAL;
    }

    /** 横扫角度（度）：硬度越高越窄，轻质越宽。 */
    public static double sweepAngle(Form form) {
        return switch (form) {
            case HARD -> 30.0D;
            case TOUGH -> 70.0D;
            case LIGHT -> 110.0D;
            case NEUTRAL -> 70.0D;
        };
    }

    /** 横扫对每个额外目标的伤害系数。 */
    public static float sweepDamageScale(Form form) {
        return switch (form) {
            case HARD -> 1.0F;
            case TOUGH -> 0.8F;
            case LIGHT -> 0.45F;
            case NEUTRAL -> 0.8F;
        };
    }

    /** 攻速修正（倍率）：轻质更快，高硬度更慢。 */
    public static double attackSpeedModifier(Form form) {
        return switch (form) {
            case LIGHT -> 0.30D;
            case HARD -> -0.20D;
            default -> 0.0D;
        };
    }

    /** 手柄「重量」0~1（由手柄材料的耐久修正推出来）。 */
    public static double handleWeight(IToolStackView tool) {
        return stats(tool, 1, HandleMaterialStats.ID, HandleMaterialStats.class)
                .map(stats -> (double) Mth.clamp((stats.durability() + 0.35F) / 0.7F, 0.0F, 1.0F))
                .orElse(0.5D);
    }

    /** 重手柄的移速惩罚：0 ~ -8%。 */
    public static double movementSpeedModifier(IToolStackView tool) {
        return -0.08D * handleWeight(tool);
    }

    public static void onAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        ItemStack stack = player.getMainHandItem();
        if (!MaceContent.isMace(stack) || player.isSpectator()) {
            return;
        }
        ToolStack tool = ToolStack.from(stack);
        if (!(event.getTarget() instanceof LivingEntity target)) {
            return;
        }
        Form form = form(tool);

        // 钩扯：目标在动 → 流血（速度越快越久、越疼）
        float targetSpeed = horizontalSpeed(target);
        if (targetSpeed >= HOOK_MIN_SPEED) {
            int amplifier = targetSpeed >= HOOK_STRONG_SPEED ? 2 : 1;
            int duration = 60 + Math.round(targetSpeed * 10.0F);
            target.addEffect(new MobEffectInstance(MaceContent.bleed(), duration, amplifier, false, false));
        }

        switch (form) {
            case HARD -> {
                // 钉刺穿甲：额外一段无视护甲的伤害
                target.hurt(player.damageSources().magic(), 1.5F * hardness(tool));
            }
            case TOUGH -> {
                if (player.getRandom().nextFloat() < 0.5F) {
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1, false, false));
                }
            }
            default -> {
            }
        }

        if (player.getAttackStrengthScale(1.0F) >= 0.999F) {
            sweep(player, target, (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE), form);
        }
    }

    /** 冷却满时的横扫：角度与伤害由棒头形态决定，主目标排除。 */
    public static void sweep(Player player, LivingEntity primary, float baseDamage, Form form) {
        double halfAngle = Math.cos(Math.toRadians(sweepAngle(form) / 2.0D));
        float scale = sweepDamageScale(form);
        Vec3 eye = player.getEyePosition();
        Vec3 look = horizontal(player.getLookAngle());
        double reach = player.getEntityReach();
        var candidates = player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(reach + SWEEP_MARGIN, 3.0D, reach + SWEEP_MARGIN),
                entity -> entity != player && entity.isAlive() && entity != primary
                        && entity != player.getVehicle());
        for (LivingEntity target : candidates) {
            Vec3 offset = horizontal(target.getBoundingBox().getCenter().subtract(eye));
            double distance = offset.length();
            if (distance < 0.5D || distance > reach || offset.lengthSqr() < 1.0E-4D) {
                continue;
            }
            if (look.dot(offset.normalize()) < halfAngle) {
                continue;
            }
            float damage = baseDamage * scale;
            if (damage <= 0.0F) {
                continue;
            }
            if (target.hurt(player.damageSources().playerAttack(player), damage)) {
                Vec3 push = target.position().subtract(player.position());
                target.knockback(0.4F, push.x, push.z);
            }
        }
    }

    // ===== 材料读取 =====

    public static float hardness(IToolStackView tool) {
        return stats(tool, 0, HardnessMaterialStats.ID, HardnessMaterialStats.class)
                .map(HardnessMaterialStats::hardness)
                .filter(value -> value > 0.0F)
                .orElse(HardnessMaterialStats.DEFAULT);
    }

    public static float toughness(IToolStackView tool) {
        return stats(tool, 0, ToughnessMaterialStats.ID, ToughnessMaterialStats.class)
                .map(ToughnessMaterialStats::toughness)
                .filter(value -> value > 0.0F)
                .orElse(ToughnessMaterialStats.DEFAULT);
    }

    static <T extends IMaterialStats> Optional<T> stats(
            IToolStackView tool, int index, MaterialStatsId statType, Class<T> type) {
        MaterialNBT materials = tool.getMaterials();
        if (materials.size() <= index) {
            return Optional.empty();
        }
        MaterialVariant variant = materials.get(index);
        return MaterialRegistry.getInstance().getMaterialStats(variant.getVariant().getId(), statType)
                .filter(type::isInstance)
                .map(type::cast);
    }

    static Vec3 horizontal(Vec3 vector) {
        return new Vec3(vector.x, 0.0D, vector.z);
    }

    /** 目标水平速度，格/秒。 */
    public static float horizontalSpeed(LivingEntity entity) {
        Vec3 movement = entity.getDeltaMovement();
        return (float) Math.sqrt(movement.x * movement.x + movement.z * movement.z) * 20.0F;
    }

    /** 流血持续 tick（给测试用）。 */
    public static int bleedDuration(float targetSpeed) {
        return 60 + Math.round(targetSpeed * 10.0F);
    }
}
