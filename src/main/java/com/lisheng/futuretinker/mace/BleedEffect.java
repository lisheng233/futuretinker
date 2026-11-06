package com.lisheng.futuretinker.mace;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * 「钩扯」造成的流血：每 20 tick 掉一次血，伤害无视护甲（用魔法伤害源），
 * 持续时间由命中时**目标的速度**决定（和长矛读自己速度正好相反）。
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
    }
}
