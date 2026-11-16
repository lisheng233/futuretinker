package com.lisheng.futuretinker.mace;

import com.lisheng.futuretinker.config.FutureTinkerConfig;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.TierSortingRegistry;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.materials.stats.IMaterialStats;
import slimeknights.tconstruct.library.materials.stats.MaterialStatsId;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.tools.stats.HeadMaterialStats;
import slimeknights.tconstruct.tools.stats.HandleMaterialStats;

/**
 * 狼牙棒的战斗逻辑。
 *
 * <p>棒头形态由两条材料统计决定：硬度 {@link HardnessMaterialStats}、韧性
 * {@link ToughnessMaterialStats}。材料数据优先；没有声明时先查内置经典表，再从头材料的
 * 耐久/攻击/挖掘等级推导，避免大部分材料都显示 1.0。</p>
 * <ul>
 *   <li>HARD（硬度高）：钉刺穿甲（额外魔法伤害）、攻速惩罚、横扫角度窄但单目标伤害高；</li>
 *   <li>TOUGH（韧性高）：命中概率挂减速，横扫角度中等；</li>
 *   <li>LIGHT（两样都低）：攻速大幅提升、横扫角度最宽但每目标伤害衰减；</li>
 *   <li>NEUTRAL：默认形态。</li>
 * </ul>
 *
 * <p>钩扯：<b>命中就挂上流血</b>；流血期间目标只要在移动，就会周期性被撕裂掉血
 * （见 {@link BleedEffect}）。和长矛相反，长矛读自己的速度，狼牙棒让目标「动得越多越遭」。</p>
 */
public final class MaceCombat {
    /** 触发流血伤害的默认目标水平速度（格/秒）；实际读配置 {@code mace.bleed_move_speed}。 */
    public static final float BLEED_MOVE_SPEED = 0.05F;
    /** 流血默认持续时间（tick，10 秒）；实际读配置 {@code mace.bleed_duration}。 */
    public static final int BLEED_DURATION = 200;
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

    /** 重手柄的移速惩罚：0 ~ -8%（默认，配置可调）。 */
    public static double movementSpeedModifier(IToolStackView tool) {
        return -FutureTinkerConfig.maceHandleWeightPenalty() * handleWeight(tool);
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

        // 钩扯：命中就挂上流血；之后目标移动时才会掉血
        int amplifier = form == Form.HARD ? 1 : 0;
        target.addEffect(new MobEffectInstance(MaceContent.bleed(),
                FutureTinkerConfig.maceBleedDuration(), amplifier, false, true));
        // 命中瞬间的表现：喷一小片血粒子 + 一声撕裂
        if (player.level() instanceof net.minecraft.server.level.ServerLevel level) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.DAMAGE_INDICATOR,
                    target.getX(), target.getY() + target.getBbHeight() * 0.6D, target.getZ(),
                    8, 0.3D, 0.3D, 0.3D, 0.05D);
            level.playSound(null, target.getX(), target.getY(), target.getZ(),
                    net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_CRIT,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.7F, 0.8F);
            level.playSound(null, target.getX(), target.getY(), target.getZ(),
                    net.minecraft.sounds.SoundEvents.SLIME_SQUISH,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.5F, 1.3F);
        }

        switch (form) {
            case HARD -> {
                // 钉刺穿甲：额外一段无视护甲的伤害
                target.hurt(player.damageSources().magic(),
                        (float) FutureTinkerConfig.maceHardDamageMultiplier() * hardness(tool));
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
                // 横扫命中的每个目标同样挂流血
                target.addEffect(new MobEffectInstance(MaceContent.bleed(),
                        FutureTinkerConfig.maceBleedDuration(),
                        form == Form.HARD ? 1 : 0, false, true));
                Vec3 push = target.position().subtract(player.position());
                target.knockback(0.4F, push.x, push.z);
            }
        }
    }

    // ===== 材料读取 =====

    /**
     * 内置的经典材料表。
     *
     * <p>优先顺序：配置覆盖（{@code mace.material_stats.overrides}）> 材料数据 >
     * 这张表 > 从头材料统计推导。配置覆盖让整合包作者可以给任意材料（包括本模组没
     * 内置的材料）直接指定硬/韧；推导保证其它匠魂/模组材料也有不同数值，而不是全部 1.0。</p>
     */
    private static final java.util.Map<String, float[]> KNOWN = java.util.Map.ofEntries(
            java.util.Map.entry("tconstruct:manyullyn", new float[] {8.0F, 2.0F}),      // 玛玉灵：硬
            java.util.Map.entry("tconstruct:cobalt", new float[] {6.5F, 1.5F}),         // 钴：硬
            java.util.Map.entry("tconstruct:hepatizon", new float[] {7.0F, 2.0F}),      // 铁钴合金：硬
            java.util.Map.entry("tconstruct:netherite", new float[] {7.0F, 3.0F}),      // 下界合金：硬
            java.util.Map.entry("tconstruct:iron", new float[] {1.5F, 4.0F}),           // 铁：韧
            java.util.Map.entry("tconstruct:slimesteel", new float[] {2.0F, 3.5F}),     // 黏钢：韧
            java.util.Map.entry("tconstruct:pig_iron", new float[] {2.2F, 3.2F}),       // 生铁：韧
            java.util.Map.entry("tconstruct:rose_gold", new float[] {2.5F, 2.0F}),      // 玫瑰金：中性偏韧
            java.util.Map.entry("tconstruct:amethyst_bronze", new float[] {3.0F, 2.5F}), // 紫水晶青铜：中性
            java.util.Map.entry("tconstruct:stone", new float[] {1.0F, 1.0F}),          // 石头：中性
            java.util.Map.entry("tconstruct:flint", new float[] {0.5F, 0.5F}),          // 燧石：轻
            java.util.Map.entry("tconstruct:wood", new float[] {0.4F, 0.4F}),           // 木：轻
            java.util.Map.entry("tconstruct:bone", new float[] {0.6F, 0.6F}));          // 骨头：轻

    public static float hardness(IToolStackView tool) {
        float[] override = configured(tool);
        if (override != null) {
            return override[0];
        }
        return stats(tool, 0, HardnessMaterialStats.ID, HardnessMaterialStats.class)
                .map(HardnessMaterialStats::hardness)
                .filter(value -> value > 0.0F)
                .orElseGet(() -> defaultStat(tool, 0));
    }

    public static float toughness(IToolStackView tool) {
        float[] override = configured(tool);
        if (override != null) {
            return override[1];
        }
        return stats(tool, 0, ToughnessMaterialStats.ID, ToughnessMaterialStats.class)
                .map(ToughnessMaterialStats::toughness)
                .filter(value -> value > 0.0F)
                .orElseGet(() -> defaultStat(tool, 1));
    }

    /** 单个材料（部件 tooltip 用）：配置覆盖 > 材料数据 > 内置经典表 > 推导。 */
    public static float hardnessOf(MaterialVariantId material) {
        float[] override = FutureTinkerConfig.maceMaterialOverride(material);
        if (override != null) {
            return override[0];
        }
        return MaterialRegistry.getInstance().getMaterialStats(material.getId(), HardnessMaterialStats.ID)
                .filter(HardnessMaterialStats.class::isInstance)
                .map(HardnessMaterialStats.class::cast)
                .map(HardnessMaterialStats::hardness)
                .filter(value -> value > 0.0F)
                .orElseGet(() -> defaultStat(material, 0));
    }

    public static float toughnessOf(MaterialVariantId material) {
        float[] override = FutureTinkerConfig.maceMaterialOverride(material);
        if (override != null) {
            return override[1];
        }
        return MaterialRegistry.getInstance().getMaterialStats(material.getId(), ToughnessMaterialStats.ID)
                .filter(ToughnessMaterialStats.class::isInstance)
                .map(ToughnessMaterialStats.class::cast)
                .map(ToughnessMaterialStats::toughness)
                .filter(value -> value > 0.0F)
                .orElseGet(() -> defaultStat(material, 1));
    }

    @Nullable
    private static float[] configured(IToolStackView tool) {
        MaterialNBT materials = tool.getMaterials();
        if (materials.size() == 0) {
            return null;
        }
        return FutureTinkerConfig.maceMaterialOverride(materials.get(0).getVariant());
    }

    private static float defaultStat(IToolStackView tool, int column) {
        MaterialNBT materials = tool.getMaterials();
        if (materials.size() == 0) {
            return HardnessMaterialStats.DEFAULT;
        }
        return defaultStat(materials.get(0).getVariant(), column);
    }

    /** 配置覆盖 > 内置经典表 > 从头材料的耐久/攻击/等级推导。 */
    private static float defaultStat(MaterialVariantId material, int column) {
        float[] configured = FutureTinkerConfig.maceMaterialOverride(material);
        if (configured != null) {
            return configured[column];
        }
        float[] entry = KNOWN.get(material.getId().toString());
        if (entry != null) {
            return entry[column];
        }
        HeadMaterialStats head = MaterialRegistry.getInstance()
                .getMaterialStats(material.getId(), HeadMaterialStats.ID)
                .filter(HeadMaterialStats.class::isInstance)
                .map(HeadMaterialStats.class::cast)
                .orElse(null);
        if (head == null) {
            return HardnessMaterialStats.DEFAULT;
        }
        return column == 0 ? derivedHardness(head) : derivedToughness(head);
    }

    /** 头材料的挖掘等级（排序表下标 0~4；未知等级退回按耐久分档）。 */
    private static int tierLevel(HeadMaterialStats head) {
        int index = TierSortingRegistry.getSortedTiers().indexOf(head.tier());
        if (index >= 0) {
            return Mth.clamp(index, 0, 4);
        }
        int durability = head.durability();
        if (durability < 100) {
            return 0;
        }
        if (durability < 200) {
            return 1;
        }
        if (durability < 600) {
            return 2;
        }
        return durability < 1600 ? 3 : 4;
    }

    private static float derivedHardness(HeadMaterialStats head) {
        return Mth.clamp(0.4F + 0.55F * head.attack() + 0.7F * tierLevel(head), 0.4F, 8.0F);
    }

    private static float derivedToughness(HeadMaterialStats head) {
        int tier = tierLevel(head);
        float attack = head.attack();
        if (tier <= 1) {
            return Mth.clamp(0.4F + 0.2F * attack + 0.3F * tier, 0.4F, 2.0F);
        }
        float value = 0.4F + 0.5F * tier
                + 1.8F * (1.0F - Math.min(1.0F, attack / 4.0F))
                + Math.min(1.0F, head.durability() / 1200.0F);
        return Mth.clamp(value, 0.4F, 6.0F);
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
}
