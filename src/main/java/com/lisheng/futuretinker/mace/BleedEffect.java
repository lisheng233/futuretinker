package com.lisheng.futuretinker.mace;

import com.lisheng.futuretinker.config.FutureTinkerConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * 「钩扯」造成的流血：命中就挂上；<b>只有目标在移动时</b>才会每 10 tick 被撕裂掉一次血
 * （魔法伤害，无视护甲）。站着不动的目标不会掉血。
 *
 * <p>表现：命中瞬间喷一小片红色伤害粒子 + 一声撕裂音（在 {@link MaceCombat#onAttack}）；
 * 之后每次移动触发再落几点粒子。</p>
 */
public final class BleedEffect extends MobEffect {
    public BleedEffect() {
        super(MobEffectCategory.HARMFUL, 0x8B1A1A);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        // 每 0.5 秒检查一次；是否真的掉血由 applyEffectTick 里的移动判定决定
        return duration % 10 == 0;
    }

    @Override
    public void applyEffectTick(LivingEntity living, int amplifier) {
        if (MaceCombat.horizontalSpeed(living) < FutureTinkerConfig.maceBleedMoveSpeed()) {
            return;
        }
        living.hurt(living.damageSources().magic(), 1.0F + amplifier);
        if (living.level() instanceof ServerLevel level) {
            // 每跳血落几点，让「一动就撕裂」看得见
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                    living.getX(), living.getY() + living.getBbHeight() * 0.6D, living.getZ(),
                    2, 0.2D, 0.2D, 0.2D, 0.0D);
            if (living.getRandom().nextFloat() < 0.35F) {
                level.playSound(null, living.getX(), living.getY(), living.getZ(),
                        SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.35F, 0.7F);
            }
        }
    }
}
