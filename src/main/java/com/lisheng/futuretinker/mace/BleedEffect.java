package com.lisheng.futuretinker.mace;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * 「钩扯」造成的流血：每 20 tick 掉一次血（魔法伤害，无视护甲），
 * 持续时间由命中时**目标的速度**决定（和长矛读自己速度正好相反）。
 *
 * <p>表现：命中瞬间喷一小片红色伤害粒子 + 一声撕裂音；之后每跳血再落几点粒子。</p>
 */
public final class BleedEffect extends MobEffect {
    public BleedEffect() {
        super(MobEffectCategory.HARMFUL, 0x8B1A1A);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % 20 == 0;
    }

    @Override
    public void applyEffectTick(LivingEntity living, int amplifier) {
        living.hurt(living.damageSources().magic(), 1.0F + amplifier);
        if (living.level() instanceof ServerLevel level) {
            // 每跳血落几点，让「一直在流血」看得见
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
